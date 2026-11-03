package tw.houselens.probe

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class HttpPageTransport(
    private val userAgent: String,
    private val pacer: RequestPacer,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val open: (String) -> HttpURLConnection = { URL(it).openConnection() as HttpsURLConnection },
) : PageTransport {
    private val busy = Mutex()
    @Volatile private var activeJob: Job? = null
    private val robots = mutableMapOf<String, String>()
    init { require(userAgent.isNotBlank() && '\r' !in userAgent && '\n' !in userAgent) }

    override suspend fun capture(request: PageRequest, navigationId: Long): RawCapture = operation {
        if (request.browser || request.warmupUrl != null) throw TransportException(ProbeReason.UNSUPPORTED)
        val response = fetch(request, ::allowedPageUrl)
        val text = response.bytes.toString(Charsets.UTF_8)
        RawCapture(response.url, response.status, if (request.captureJson) null else text,
            if (request.captureJson) listOf(CapturedJson(response.url, text)) else emptyList(), navigationId, true)
    }

    suspend fun loadImage(site: Site, url: String): ByteArray = operation {
        val response = fetch(PageRequest(url), { allowedImageUrl(site, it) },
            if (site == Site.Sinyi) "https://www.sinyi.com.tw/" else null)
        if (!response.contentType.orEmpty().startsWith("image/", true)) throw TransportException(ProbeReason.UNSUPPORTED)
        response.bytes
    }

    suspend fun checkRobots(url: String): Boolean? = operation {
        if (!allowedPageUrl(url)) throw TransportException(ProbeReason.UNSUPPORTED)
        val origin = originOf(url)!!
        val content = robots[origin] ?: try {
            fetch(PageRequest("$origin/robots.txt"), { allowedPageUrl(it) && originOf(it) == origin })
                .bytes.toString(Charsets.UTF_8).also { robots[origin] = it }
        } catch (e: TransportException) {
            if (e.reason == ProbeReason.RATE_LIMITED) throw e
            return@operation null
        }
        robotsAllowed(content, URI(url).rawPath.ifEmpty { "/" } + (URI(url).rawQuery?.let { "?$it" } ?: ""))
    }

    override fun cancel() { activeJob?.cancel() }

    private suspend fun <T> operation(block: suspend () -> T): T = coroutineScope {
        check(busy.tryLock()) { "transport_busy" }
        activeJob = currentCoroutineContext()[Job]
        try { block() } finally { activeJob = null; busy.unlock() }
    }

    private data class Response(val url: String, val status: Int, val bytes: ByteArray,
        val location: String?, val contentType: String?)

    private suspend fun fetch(request: PageRequest, allowed: (String) -> Boolean, referer: String? = null): Response {
        if (request.method !in setOf("GET", "POST") || (request.method == "GET" && request.body != null) ||
            (request.body?.toByteArray()?.size ?: 0) > BODY_LIMIT) throw TransportException(ProbeReason.UNSUPPORTED)
        repeat(3) { attempt ->
            try {
                return withTimeout(30_000) {
                    var url = request.url
                    var method = request.method
                    var body = request.body
                    repeat(6) { redirect ->
                        if (!allowed(url)) throw TransportException(ProbeReason.UNSUPPORTED)
                        pacer.awaitTurn()
                        val response = exchange(url, method, body, referer)
                        if (response.status == 429) throw TransportException(ProbeReason.RATE_LIMITED, 429)
                        if (response.status in setOf(301, 302, 303, 307, 308)) {
                            if (redirect == 5 || response.location == null) throw TransportException(ProbeReason.HTTP_ERROR, response.status)
                            val target = try { URI(url).resolve(response.location).toString() }
                                catch (_: Exception) { throw TransportException(ProbeReason.UNSUPPORTED) }
                            // Do not forward a POST body across origins, even to another allowed site.
                            if (body != null && originOf(target) != originOf(url)) throw TransportException(ProbeReason.UNSUPPORTED)
                            url = target
                            if (response.status == 303 || (method == "POST" && response.status in setOf(301, 302))) { method = "GET"; body = null }
                        } else {
                            if (response.status !in 200..299) throw TransportException(ProbeReason.HTTP_ERROR, response.status)
                            return@withTimeout response
                        }
                    }
                    throw TransportException(ProbeReason.HTTP_ERROR)
                }
            } catch (e: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                throw TransportException(ProbeReason.TIMEOUT)
            } catch (e: CancellationException) { throw e
            } catch (e: IOException) {
                currentCoroutineContext().ensureActive()
                val failure = e as? TransportException ?: TransportException(ProbeReason.NETWORK_ERROR)
                val retry = failure.reason == ProbeReason.NETWORK_ERROR || failure.status in 500..599
                if (!retry || attempt == 2) throw failure
                delay(2000L shl attempt)
            }
        }
        error("unreachable")
    }

    private suspend fun exchange(url: String, method: String, body: String?, referer: String?): Response {
        try {
            return withTimeout(30_000) {
                suspendCancellableCoroutine { continuation ->
                    val lock = Any()
                    var connection: HttpURLConnection? = null
                    continuation.invokeOnCancellation { synchronized(lock) { connection?.disconnect() } }
                    CoroutineScope(continuation.context).launch(io) {
                        var current: HttpURLConnection? = null
                        try {
                            currentCoroutineContext().ensureActive()
                            current = open(url)
                            synchronized(lock) { connection = current; if (!continuation.isActive) current.disconnect() }
                            currentCoroutineContext().ensureActive()
                            current.instanceFollowRedirects = false
                            current.useCaches = false
                            current.connectTimeout = 15_000
                            current.readTimeout = 30_000
                            current.requestMethod = method
                            current.setRequestProperty("User-Agent", userAgent)
                            current.setRequestProperty("Accept-Encoding", "identity")
                            current.setRequestProperty("Cache-Control", "no-cache, no-store")
                            if (referer != null) current.setRequestProperty("Referer", referer)
                            if (body != null) {
                                current.doOutput = true
                                current.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                                current.outputStream.use { it.write(body.toByteArray()) }
                            }
                            val status = current.responseCode
                            val bytes = if (status in 200..299) {
                                if (current.getHeaderField("Content-Encoding")?.let { !it.equals("identity", true) } == true)
                                    throw TransportException(ProbeReason.UNSUPPORTED)
                                current.inputStream.use { input ->
                                    val output = ByteArrayOutputStream()
                                    val buffer = ByteArray(8192)
                                    while (true) {
                                        currentCoroutineContext().ensureActive()
                                        val size = input.read(buffer)
                                        if (size < 0) break
                                        if (output.size() + size > BODY_LIMIT) throw TransportException(ProbeReason.UNSUPPORTED)
                                        output.write(buffer, 0, size)
                                    }
                                    output.toByteArray()
                                }
                            } else byteArrayOf()
                            continuation.resume(Response(url, status, bytes, current.getHeaderField("Location"), current.getHeaderField("Content-Type")))
                        } catch (e: CancellationException) { continuation.cancel(e)
                        } catch (e: Exception) {
                            // Only fixed reason/status escape the transport; URLs and response bodies stay private.
                            continuation.resumeWithException(e as? TransportException ?: TransportException(ProbeReason.NETWORK_ERROR))
                        } finally { synchronized(lock) { current?.disconnect(); connection = null } }
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            throw TransportException(ProbeReason.TIMEOUT)
        }
    }
}
