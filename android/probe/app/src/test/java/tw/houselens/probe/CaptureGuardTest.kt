package tw.houselens.probe

import org.junit.Assert.*
import org.junit.Test

class CaptureGuardTest {
    @Test fun messageMustMatchDocumentEndpointStatusAndType() {
        val doc = "https://sale.591.com.tw/?section=38"
        val message = """{"kind":"json","url":"https://sale.591.com.tw/api/search","documentUrl":"$doc","navigationId":9,"status":200,"contentType":"application/json","body":"{}","networkObserved":false}"""
        assertNotNull(decodeCapture(message, "https://sale.591.com.tw", true, 9, doc, false))
        for (bad in listOf(message.replace("/api/search", "/account"), message.replace("200", "403"), message.replace("application/json", "text/html"), message.replace("section=38", "section=37"), message.replace("navigationId\":9", "navigationId\":8"))) {
            assertNull(decodeCapture(bad, "https://sale.591.com.tw", true, 9, doc, false))
        }
        assertNull(decodeCapture(message, "https://sale.591.com.tw", false, 9, doc, false))
        assertNull(decodeCapture("[]", "https://sale.591.com.tw", true, 9, doc, false))
    }
    @Test fun acceptsOnlyExactHttpsMainFrameAndCurrentNavigationWithinLimit() {
        val origin = "https://sale.591.com.tw"
        assertTrue(acceptCapture(origin, origin, true, 8, 8, 2 * 1024 * 1024))
        for (bad in listOf("https://sale.591.com.tw.evil.test", "http://sale.591.com.tw", "https://sale.591.com.tw:444", "null", "https://evil.test")) {
            assertFalse(acceptCapture(bad, origin, true, 8, 8, 10))
        }
        assertFalse(acceptCapture(origin, origin, false, 8, 8, 10))
        assertFalse(acceptCapture(origin, origin, true, 7, 8, 10))
        assertFalse(acceptCapture(origin, origin, true, 8, 8, 2097153))
        assertFalse(acceptCapture(origin, origin, true, 8, 8, -1))
    }

    @Test fun urlsRejectCredentialsPortsLookalikesAndNonHttps() {
        assertTrue(allowedPageUrl("https://sale.591.com.tw/?section=38"))
        for (url in listOf("file:///secret", "http://sale.591.com.tw/", "https://sale.591.com.tw.evil.test/", "https://secret@sale.591.com.tw/", "https://sale.591.com.tw:444/")) {
            assertFalse(allowedPageUrl(url))
        }
        assertTrue(allowedImageUrl(Site.Sinyi, "https://res.sinyi.com.tw/buy/A/smallimg/A.JPG"))
        assertFalse(allowedImageUrl(Site.Sinyi, "https://res.sinyi.com.tw.evil.test/a.jpg"))
        assertFalse(allowedImageUrl(Site.F591, "https://res.sinyi.com.tw/a.jpg"))
    }
}
