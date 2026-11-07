package tw.houselens.viewer.data

import org.junit.Assert.*
import org.junit.Test
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest

class MediaUrlsTest {
    @Test fun image_requests_never_send_credentials_or_follow_redirects() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val client = createImageClient(MockEngine { r ->
            requests += r
            respond("", HttpStatusCode.Found, headersOf("Location","https://evil.test/image.jpg"))
        })
        try {
            client.get("https://res.sinyi.com.tw/a.jpg") {
                header("Authorization","must-not-leak"); header("apikey","must-not-leak")
            }
            client.get("https://res.sinyi.com.tw.evil/a.jpg")
            assertEquals(2, requests.size)
            assertTrue(requests.all { it.headers["Authorization"] == null && it.headers["apikey"] == null })
            assertEquals("https://www.sinyi.com.tw/",requests.first().headers["Referer"])
            assertNull(requests.last().headers["Referer"])
        } finally { client.close() }
    }
    @Test fun unsafe_media_urls_are_rejected() {
        listOf("http://img.example/a.jpg","javascript:alert(1)","file:///etc/test",
            "https://user:pass@res.sinyi.com.tw/a.jpg","https://x.test/a.MP4?q=1",
            "https://x.test/a.m3u8").forEach { assertNull(it, mediaUrl(it)) }
        assertEquals("https://img.example/a.jpg", mediaUrl("https://img.example/a.jpg"))
    }
    @Test fun external_links_allow_http_but_reject_credentials_and_schemes() {
        assertEquals("http://house.example/a", externalListingUrl("http://house.example/a"))
        assertNull(externalListingUrl("intent://x"))
        assertNull(externalListingUrl("https://user@house.example"))
        assertNull(externalListingUrl("https:///broken"))
    }
    @Test fun sinyi_referer_requires_exact_host() {
        assertEquals("https://www.sinyi.com.tw/", imageReferer("https://res.sinyi.com.tw/a.jpg"))
        assertNull(imageReferer("https://res.sinyi.com.tw.evil/a.jpg"))
        assertNull(imageReferer("https://res.sinyi.com.tw@evil/a.jpg"))
        assertNull(imageReferer("https://other.example/a.jpg"))
    }
}
