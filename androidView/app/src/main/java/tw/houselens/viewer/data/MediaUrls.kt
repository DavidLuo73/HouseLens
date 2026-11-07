package tw.houselens.viewer.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import java.net.URI
import java.util.Locale

private fun webUri(raw: String?, httpsOnly: Boolean): URI? {
    if (raw.isNullOrBlank()) return null
    val uri = try { URI(raw) } catch (_: Exception) { return null }
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    if (scheme != "https" && (httpsOnly || scheme != "http")) return null
    if (uri.host.isNullOrBlank() || uri.rawUserInfo != null || uri.port !in -1..65535) return null
    return uri
}
fun mediaUrl(raw: String?): String? {
    val uri = webUri(raw, true) ?: return null
    if (uri.path.lowercase(Locale.ROOT).substringAfterLast('.') in setOf("mp4","webm","m3u8","mov","avi","m4v")) return null
    return uri.toASCIIString()
}
fun externalListingUrl(raw: String?): String? = webUri(raw, false)?.toASCIIString()
fun imageReferer(raw: String?): String? {
    val uri = mediaUrl(raw)?.let(::URI) ?: return null
    return if (uri.host.equals("res.sinyi.com.tw", ignoreCase = true) && uri.port in listOf(-1,443))
        "https://www.sinyi.com.tw/" else null
}

fun createImageClient(engine: HttpClientEngine? = null): HttpClient = HttpClient(engine ?: OkHttp.create()) {
    followRedirects = false
    install(HttpTimeout) {
        requestTimeoutMillis = 15_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 15_000
    }
    install(createClientPlugin("ViewerImages") {
        onRequest { request, _ ->
            val url = request.url.build().toString()
            require(mediaUrl(url) != null) { "Unsupported image URL" }
            request.headers.remove("Authorization")
            request.headers.remove("apikey")
            request.headers.remove("Referer")
            imageReferer(url)?.let { request.headers.append("Referer", it) }
        }
    })
}
