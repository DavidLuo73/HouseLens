package tw.houselens.probe

import java.io.IOException
import java.net.URI
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random
import kotlinx.serialization.json.*

interface PageTransport {
    suspend fun capture(request: PageRequest, navigationId: Long): RawCapture
    fun cancel()
}

interface SourceAdapter {
    val site: Site
    val maxPages: Int
    fun request(query: ProbeQuery, pageIndex: Int): PageRequest
    fun parse(capture: RawCapture, query: ProbeQuery, pageIndex: Int): PageResult
}

class TransportException(val reason: ProbeReason, val status: Int? = null) : IOException(reason.name)

internal val pageHosts = setOf("sale.591.com.tw", "www.rakuya.com.tw", "buy.yungching.com.tw",
    "www.hbhousing.com.tw", "www.sinyi.com.tw", "buy.cthouse.com.tw", "www.twhg.com.tw")
internal const val SELF_CHECK_HOST = "appassets.androidplatform.net"
internal const val BRIDGE_LIMIT = 2 * 1024 * 1024
internal const val BODY_LIMIT = 8 * 1024 * 1024

internal fun secureUri(url: String): URI? = try {
    URI(url).takeIf { it.scheme == "https" && it.host != null && it.rawUserInfo == null &&
        (it.port == -1 || it.port == 443) && it.rawFragment == null }
} catch (_: Exception) { null }

internal fun originOf(url: String): String? = secureUri(url)?.let { "https://${it.host.lowercase()}" }
fun allowedPageUrl(url: String): Boolean = secureUri(url)?.host?.lowercase() in pageHosts
fun allowedImageUrl(site: Site, url: String): Boolean {
    val hosts = when (site) {
        Site.F591 -> setOf("s.591.com.tw", "img.591.com.tw")
        Site.Rakuya -> setOf("static.rakuya.com.tw", "www.rakuya.com.tw")
        Site.Yungching -> setOf("yccdn.yungching.com.tw")
        Site.HBHousing -> setOf("img.hbhousing.com.tw")
        Site.Sinyi -> setOf("res.sinyi.com.tw", "www.sinyi.com.tw")
        Site.CtHouse -> setOf("media.cthouse.com.tw")
        Site.TwHouse -> setOf("img.twhg.com.tw")
    }
    return secureUri(url)?.host?.lowercase() in hosts
}

fun acceptCapture(origin: String, expectedOrigin: String, mainFrame: Boolean,
    receivedId: Long, expectedId: Long, bytes: Int): Boolean =
    mainFrame && receivedId == expectedId && bytes in 0..BRIDGE_LIMIT &&
        originOf(origin) != null && originOf(origin) == originOf(expectedOrigin)

internal data class CaptureMessage(val kind: String, val url: String, val body: String,
    val networkObserved: Boolean, val ready: Boolean)

internal fun decodeCapture(message: String, origin: String, mainFrame: Boolean, id: Long,
    documentUrl: String, selfCheck: Boolean): CaptureMessage? {
    if (message.length > BRIDGE_LIMIT || message.toByteArray().size > BRIDGE_LIMIT) return null
    return try {
        val obj = Json.parseToJsonElement(message) as? JsonObject ?: return null
        fun string(key: String) = (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val receivedId = (obj["navigationId"] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull ?: return null
        if (!acceptCapture(origin, documentUrl, mainFrame, receivedId, id, message.toByteArray().size) ||
            string("documentUrl") != documentUrl) return null
        val kind = string("kind") ?: return null
        val url = string("url") ?: return null
        val uri = secureUri(url) ?: return null
        val body = string("body") ?: return null
        when (kind) {
            "json" -> {
                val allowed = if (selfCheck) uri.host == SELF_CHECK_HOST && uri.path.startsWith("/probe/") else
                    uri.host == "sale.591.com.tw" && Regex("/(search|house|sale|list)(/|$|_)").containsMatchIn(uri.path)
                val status = (obj["status"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull ?: return null
                if (!allowed || status !in 200..299 ||
                    !Regex("application/(?:[\\w.+-]*\\+)?json(?:;.*)?", RegexOption.IGNORE_CASE).matches(string("contentType") ?: "")) return null
            }
            "dom", "oversize" -> if (url != documentUrl) return null
            else -> return null
        }
        CaptureMessage(kind, url, body, !selfCheck && obj["networkObserved"] == JsonPrimitive(true), obj["ready"] == JsonPrimitive(true))
    } catch (_: Exception) { null }
}

// One instance is shared by HTTP, robots, images and app-initiated WebView navigations.
class RequestPacer(
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    private val jitter: () -> Int = { Random.nextInt(2000) },
) {
    private val mutex = Mutex()
    private var lastStart: Long? = null
    suspend fun awaitTurn() = mutex.withLock {
        val gap = 3000L + jitter().coerceIn(0, 1999)
        delay((gap - (lastStart?.let { now() - it } ?: 0)).coerceAtLeast(0))
        lastStart = now()
    }
}

internal fun robotsAllowed(text: String, path: String): Boolean? {
    var relevant = false
    var sawAgent = false
    var unsupported = false
    var previousWasAgent = false
    for (raw in text.lineSequence()) {
        val line = raw.substringBefore('#').trim()
        if (line.isEmpty()) continue
        val key = line.substringBefore(':').lowercase()
        val value = line.substringAfter(':', "").trim()
        when (key) {
            "user-agent" -> {
                sawAgent = true
                val matches = value == "*" || value.contains("HouseLens", true)
                relevant = if (previousWasAgent) relevant || matches else matches
            }
            "disallow" -> if (relevant && value.isNotEmpty()) {
                if ('*' in value || '$' in value) unsupported = true
                else if (path.startsWith(value)) return false
            }
            "allow", "crawl-delay" -> if (relevant) unsupported = true
        }
        previousWasAgent = key == "user-agent"
    }
    // shortcut: only the backend's literal Disallow subset; expand with fixtures if real robots requires more.
    return if (unsupported || (!sawAgent && text.isNotBlank())) null else true
}
