package tw.houselens.probe

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.SslError
import android.os.Looper
import android.view.ViewGroup
import android.webkit.*
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Owned by the service; the Activity only lends a container. All lifecycle calls are on main. */
@SuppressLint("SetJavaScriptEnabled")
class WebViewSession(context: Context, private val pacer: RequestPacer, private val selfCheck: Boolean = false,
    private val onRendererGone: () -> Unit = {}) : PageTransport {
    private val app = context.applicationContext
    private val view: WebView
    private var script: ScriptHandler? = null
    private var activeJob: Job? = null
    private var pending: CompletableDeferred<Unit>? = null
    private var evaluation: CancellableContinuation<String>? = null
    private var failure: TransportException? = null
    private var destroyed = false
    val isAvailable: Boolean get() { mainThread(); return !destroyed }
    private var navigationId = -1L
    private var documentUrl = ""
    private var statusCode: Int? = null
    private val captured = linkedMapOf<String, CaptureMessage>()
    private var capturedBytes = 0
    private val bridgeSource = app.assets.open("probe-bridge.js").bufferedReader().use { it.readText() }
    private val documentScriptSupported = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    val jsonBridgeSupported: Boolean = documentScriptSupported &&
        WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
    val userAgent: String get() { mainThread(); return view.settings.userAgentString }

    private val assets = if (selfCheck) WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(app))
        .addPathHandler("/probe/", WebViewAssetLoader.PathHandler { path ->
            when (path) {
                "fetch", "xhr" -> localResponse("application/json", "{\"source\":\"local\",\"case\":\"$path\"}")
                "late" -> localResponse("application/json", "{\"source\":\"local\",\"case\":\"late\"}").apply {
                    data = object : java.io.FilterInputStream(data) {
                        private var delayed = false
                        private fun waitOnce() { if (!delayed) { delayed = true; Thread.sleep(1500) } }
                        override fun read(): Int { waitOnce(); return super.read() }
                        override fun read(buffer: ByteArray, offset: Int, length: Int): Int { waitOnce(); return super.read(buffer, offset, length) }
                    }
                }
                "frame" -> localResponse("text/html", "<script>const p=JSON.parse(parent.__houseLensSnapshot()); if(window.HouseLensBridge) { HouseLensBridge.postMessage(JSON.stringify({kind:'json',url:location.origin+'/probe/frame',documentUrl:p.documentUrl,navigationId:p.navigationId,status:200,contentType:'application/json',body:'{\"frame\":true}',networkObserved:false})); parent.document.documentElement.dataset.frameSent='true'; }</script>")
                else -> localResponse("text/plain", "missing", 404)
            }
        }).build() else null

    init {
        mainThread()
        view = WebView(app)
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            setSupportMultipleWindows(false)
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, false)
        ServiceWorkerController.getInstance().serviceWorkerWebSettings.apply {
            cacheMode = WebSettings.LOAD_NO_CACHE
            allowFileAccess = false
            allowContentAccess = false
        }
        if (selfCheck) ServiceWorkerController.getInstance().setServiceWorkerClient(object : ServiceWorkerClient() {
            override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse = localResponse("text/plain", "blocked", 403)
        })
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            val origins = if (selfCheck) setOf("https://$SELF_CHECK_HOST") else pageHosts.map { "https://$it" }.toSet()
            WebViewCompat.addWebMessageListener(view, "HouseLensBridge", origins) { _, message, origin, mainFrame, _ ->
                if (activeJob?.isActive != true || message.type != androidx.webkit.WebMessageCompat.TYPE_STRING) return@addWebMessageListener
                val data = decodeCapture(message.data ?: return@addWebMessageListener, origin.toString(), mainFrame,
                    navigationId, documentUrl, selfCheck) ?: return@addWebMessageListener
                if (data.kind == "json" && data.url !in captured) {
                    val bytes = data.body.toByteArray().size
                    if (capturedBytes + bytes > BODY_LIMIT) { fail(ProbeReason.UNSUPPORTED); return@addWebMessageListener }
                    capturedBytes += bytes
                    captured[data.url] = data
                }
            }
        }
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return request.url.scheme != "https"
                val url = request.url.toString()
                val allowed = permitted(url) && (activeJob == null || originOf(url) == originOf(documentUrl))
                if (!allowed) fail(ProbeReason.UNSUPPORTED)
                else if (activeJob != null && documentUrl != url) {
                    documentUrl = url; captured.clear(); capturedBytes = 0
                }
                return !allowed
            }
            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                if (activeJob != null && url == documentUrl) statusCode = null
            }
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (!selfCheck) return null
                return if (request.url.host == SELF_CHECK_HOST && request.url.scheme == "https")
                    assets?.shouldInterceptRequest(request.url) ?: localResponse("text/plain", "missing", 404)
                else localResponse("text/plain", "blocked", 403)
            }
            override fun onPageFinished(view: WebView, url: String) {
                if (url == documentUrl && url == view.url) {
                    pending?.complete(Unit)
                }
            }
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                if (error.url == documentUrl && view.url == documentUrl) fail(ProbeReason.NETWORK_ERROR)
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame && request.url.toString() == documentUrl && view.url == documentUrl) fail(ProbeReason.NETWORK_ERROR)
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame && request.url.toString() == documentUrl && view.url == documentUrl) {
                    statusCode = response.statusCode
                    if (response.statusCode == 429) fail(ProbeReason.RATE_LIMITED)
                }
            }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                rendererGone()
                return true
            }
        }
    }

    override suspend fun capture(request: PageRequest, navigationId: Long): RawCapture = withContext(Dispatchers.Main.immediate) {
        check(!destroyed && activeJob == null) { "session_unavailable" }
        if (!request.browser || request.method != "GET" || request.body != null || !permitted(request.url)) throw TransportException(ProbeReason.UNSUPPORTED)
        if (!documentScriptSupported || (request.captureJson && !jsonBridgeSupported)) throw TransportException(ProbeReason.UNSUPPORTED)
        activeJob = currentCoroutineContext()[Job]
        try {
            request.warmupUrl?.let {
                if (!permitted(it) || originOf(it) != originOf(request.url)) throw TransportException(ProbeReason.UNSUPPORTED)
                navigate(it, -navigationId - 1, false)
            }
            navigate(request.url, navigationId, request.captureJson)
        } finally {
            if (!destroyed) view.stopLoading()
            pending = null; activeJob = null; captured.clear(); capturedBytes = 0
            if (!destroyed && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) script?.remove()
            script = null
        }
    }

    private suspend fun navigate(url: String, id: Long, wantsJson: Boolean): RawCapture {
        pacer.awaitTurn()
        if (destroyed) throw failure ?: TransportException(ProbeReason.RENDERER_GONE)
        view.stopLoading()
        navigationId = id; documentUrl = url; failure = null; statusCode = null
        captured.clear(); capturedBytes = 0
        pending = CompletableDeferred()
        val source = bridgeSource.replace("__PROBE_CONFIG__", "{\"navigationId\":$id,\"selfCheck\":$selfCheck}")
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            script?.remove()
            script = WebViewCompat.addDocumentStartJavaScript(view, source, setOf(originOf(url)!!))
        } else throw TransportException(ProbeReason.UNSUPPORTED)
        try {
            view.loadUrl(url)
            withTimeout(30_000) { pending!!.await() }
            failure?.let { throw it }
            var previous: String? = null
            var stable = 0
            var last: CaptureMessage? = null
            val ready = withTimeoutOrNull(5000) { while (isActive) {
                delay(250)
                failure?.let { throw it }
                val encoded = withTimeout(1000) { evaluate("window.__houseLensSnapshot ? window.__houseLensSnapshot() : null") }
                val message = try { (Json.parseToJsonElement(encoded) as? JsonPrimitive)?.contentOrNull } catch (_: Exception) { null }
                val data = message?.let { decodeCapture(it, originOf(documentUrl)!!, true, id, documentUrl, selfCheck) }
                if (data?.kind == "oversize") throw TransportException(ProbeReason.UNSUPPORTED)
                if (data?.kind == "dom" && data.ready && data.body.isNotBlank()) {
                    stable = if (previous == data.body) stable + 1 else 0
                    previous = data.body; last = data
                    if (stable >= 2 && (!wantsJson || captured.isNotEmpty())) return@withTimeoutOrNull data
                }
            }; null }
            failure?.let { throw it }
            if (ready != null) return result(ready, wantsJson)
            if (stable >= 2 && last != null) return result(last!!, wantsJson)
            throw TransportException(ProbeReason.TIMEOUT)
        } catch (e: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            throw TransportException(ProbeReason.TIMEOUT)
        }
    }

    private fun result(dom: CaptureMessage, wantsJson: Boolean) = RawCapture(documentUrl, statusCode, dom.body,
        captured.values.map { CapturedJson(it.url, it.body) }, navigationId,
        !selfCheck && jsonBridgeSupported && if (wantsJson) captured.isNotEmpty() && captured.values.all { it.networkObserved } else dom.networkObserved)

    private suspend fun evaluate(js: String): String = suspendCancellableCoroutine { continuation ->
        evaluation = continuation
        view.evaluateJavascript(js) {
            if (evaluation === continuation) evaluation = null
            if (continuation.isActive) continuation.resume(it ?: "null")
        }
    }

    internal suspend fun checkInjectedFailure(reason: ProbeReason): Boolean = supervisorScope {
        check(selfCheck && reason in setOf(ProbeReason.TIMEOUT, ProbeReason.RENDERER_GONE))
        val job = async { capture(PageRequest("https://$SELF_CHECK_HOST/assets/selfcheck.html?neverReady=1", browser = true), 3) }
        try {
            withTimeout(10_000) {
                while (pending == null) delay(10)
                if (reason == ProbeReason.RENDERER_GONE) rendererGone() else fail(reason)
                try { job.await(); false } catch (e: TransportException) {
                    e.reason == reason && (reason != ProbeReason.RENDERER_GONE || !isAvailable)
                }
            }
        } finally { job.cancelAndJoin() }
    }

    private fun rendererGone() {
        fail(ProbeReason.RENDERER_GONE)
        if (selfCheck) ServiceWorkerController.getInstance().setServiceWorkerClient(null)
        detach(); view.destroy(); destroyed = true
        onRendererGone()
    }

    override fun cancel() { mainThread(); activeJob?.cancel(); if (!destroyed) view.stopLoading() }
    fun attach(container: ViewGroup) {
        mainThread(); check(!destroyed)
        if (view.parent !== container) { detach(); container.addView(view, ViewGroup.LayoutParams(-1, -1)) }
    }
    fun detach() { mainThread(); (view.parent as? ViewGroup)?.removeView(view) }
    fun detach(container: ViewGroup) { mainThread(); if (view.parent === container) detach() }
    fun clearSession() { mainThread(); check(activeJob == null && !destroyed); view.clearCache(true); view.clearHistory() }
    fun destroy() {
        mainThread(); if (destroyed) return
        cancel(); detach()
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) script?.remove()
        script = null
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) WebViewCompat.removeWebMessageListener(view, "HouseLensBridge")
        if (selfCheck) ServiceWorkerController.getInstance().setServiceWorkerClient(null)
        view.destroy(); destroyed = true
    }
    private fun fail(reason: ProbeReason) {
        failure = TransportException(reason)
        pending?.completeExceptionally(failure!!)
        evaluation?.takeIf { it.isActive }?.resumeWithException(failure!!)
        evaluation = null
    }
    private fun permitted(url: String) = if (selfCheck) secureUri(url)?.host == SELF_CHECK_HOST else allowedPageUrl(url)
    private fun mainThread() { check(Looper.myLooper() == Looper.getMainLooper()) { "main_thread_required" } }
    private fun localResponse(type: String, body: String, code: Int = 200) = WebResourceResponse(type, "UTF-8", code,
        if (code == 200) "OK" else "Blocked", mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(body.toByteArray()))
}
