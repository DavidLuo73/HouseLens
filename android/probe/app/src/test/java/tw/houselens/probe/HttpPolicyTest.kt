package tw.houselens.probe

import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HttpPolicyTest {
    @Test fun robotsGroupedAgentsCannotLoseWildcardDisallow() {
        assertNotEquals(true, robotsAllowed("User-agent: *\nUser-agent: Other\nDisallow: /private", "/private/1"))
    }
    private open class Reply(url: String, val code: Int = 200, val text: String = "ok", val headers: Map<String, String> = emptyMap()) : HttpURLConnection(URL(url)) {
        var disconnected = false
        override fun connect() { }
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getResponseCode() = code
        override fun getInputStream() = ByteArrayInputStream(text.toByteArray())
        override fun getHeaderField(name: String) = headers[name]
    }

    @Test fun redirectChainSharesOneThirtySecondDeadline() = runTest {
        var calls = 0
        val transport = HttpPageTransport("test UA", RequestPacer({ testScheduler.currentTime }, { 0 }), StandardTestDispatcher(testScheduler)) {
            calls++
            object : Reply(it, if (calls == 1) 302 else 200, headers = mapOf("Location" to "/next")) {
                override fun getResponseCode(): Int { testScheduler.advanceTimeBy(20_000); return code }
            }
        }
        try { transport.capture(PageRequest("https://www.sinyi.com.tw/buy/"), 1); fail("redirect chain exceeded total deadline") }
        catch (e: TransportException) { assertEquals(ProbeReason.TIMEOUT, e.reason) }
    }

    @Test fun cancelDisconnectsActiveConnectionBeforeReadingBody() = runTest {
        lateinit var transport: HttpPageTransport
        var reply: Reply? = null
        transport = HttpPageTransport("test UA", RequestPacer({ testScheduler.currentTime }, { 0 }), StandardTestDispatcher(testScheduler)) {
            object : Reply(it) {
                override fun getResponseCode(): Int {
                    transport.cancel()
                    assertTrue(disconnected)
                    return 200
                }
            }.also { reply = it }
        }
        val work = launch { transport.capture(PageRequest("https://www.sinyi.com.tw/"), 1) }
        advanceUntilIdle()
        assertTrue(work.isCancelled)
        assertTrue(reply!!.disconnected)
    }

    @Test fun postAndImageHeadersStayRestricted() = runTest {
        val replies = mutableListOf<Reply>()
        val sent = java.io.ByteArrayOutputStream()
        val transport = HttpPageTransport("actual WebView UA", RequestPacer({ testScheduler.currentTime }, { 0 }), StandardTestDispatcher(testScheduler)) {
            object : Reply(it, text = "image", headers = mapOf("Content-Type" to "image/jpeg")) {
                override fun getOutputStream() = sent
            }.also(replies::add)
        }
        transport.capture(PageRequest("https://buy.cthouse.com.tw/api/house_list.ashx", "POST", "{\"page\":2}", captureJson = true), 2)
        assertEquals("{\"page\":2}", sent.toString("UTF-8"))
        assertEquals("POST", replies[0].requestMethod)
        assertNull(replies[0].getRequestProperty("Referer"))
        transport.loadImage(Site.Sinyi, "https://res.sinyi.com.tw/a.jpg")
        transport.loadImage(Site.F591, "https://img.591.com.tw/a.jpg")
        assertEquals("https://www.sinyi.com.tw/", replies[1].getRequestProperty("Referer"))
        assertNull(replies[2].getRequestProperty("Referer"))
        assertTrue(replies.all { it.getRequestProperty("Authorization") == null && it.getRequestProperty("Cookie") == null })
        assertEquals("actual WebView UA", replies[1].getRequestProperty("User-Agent"))
    }

    @Test fun retriesAtMostThreeTimesWithBackoffAndSharedSpacing() = runTest {
        val starts = mutableListOf<Long>()
        val transport = HttpPageTransport("test WebView UA", RequestPacer({ testScheduler.currentTime }, { 0 }), StandardTestDispatcher(testScheduler)) {
            starts += testScheduler.currentTime
            Reply(it, 503)
        }
        try { transport.capture(PageRequest("https://www.sinyi.com.tw/buy/"), 1); fail() }
        catch (e: TransportException) { assertEquals(ProbeReason.HTTP_ERROR, e.reason) }
        assertEquals(listOf(3000L, 6000L, 10000L), starts)
    }

    @Test fun rateLimitDoesNotRetryOrLeakResponse() = runTest {
        var calls = 0
        val transport = HttpPageTransport("test UA", RequestPacer({ testScheduler.currentTime }, { 0 }), StandardTestDispatcher(testScheduler)) {
            calls++; Reply(it, 429, "secret token")
        }
        try { transport.capture(PageRequest("https://www.sinyi.com.tw/buy/"), 1); fail() }
        catch (e: TransportException) {
            assertEquals(ProbeReason.RATE_LIMITED, e.reason)
            assertFalse(e.toString().contains("secret"))
        }
        assertEquals(1, calls)
    }

    @Test fun cancelDuringRetryWaitPreventsFurtherRequests() = runTest {
        var calls = 0
        val transport = HttpPageTransport("test UA", RequestPacer({ testScheduler.currentTime }, { 0 }), StandardTestDispatcher(testScheduler)) {
            calls++; throw IOException("private url")
        }
        val work = launch { transport.capture(PageRequest("https://www.sinyi.com.tw/buy/"), 1) }
        advanceTimeBy(3001); runCurrent()
        transport.cancel(); advanceUntilIdle()
        assertTrue(work.isCancelled)
        assertEquals(1, calls)
    }

    @Test fun robotsCachesTextButEvaluatesEachPathAndUnknownStaysUnknown() = runTest {
        var calls = 0
        val transport = HttpPageTransport("test UA", RequestPacer({ testScheduler.currentTime }, { 0 }), StandardTestDispatcher(testScheduler)) {
            calls++
            if (it.contains("sinyi")) Reply(it, 200, "User-agent: *\nDisallow: /private # comment\nDisallow:\nUser-agent: Other\nDisallow: /")
            else Reply(it, 404)
        }
        assertEquals(true, transport.checkRobots("https://www.sinyi.com.tw/public"))
        assertEquals(false, transport.checkRobots("https://www.sinyi.com.tw/private/1"))
        assertEquals(true, transport.checkRobots("https://www.sinyi.com.tw/buy/"))
        assertEquals(1, calls)
        assertNull(transport.checkRobots("https://www.rakuya.com.tw/buy"))
    }

    @Test fun redirectIsRevalidatedAndPostJsonIsKeptSeparateFromHtml() = runTest {
        var calls = 0
        val transport = HttpPageTransport("test UA", RequestPacer({ testScheduler.currentTime }, { 0 }), StandardTestDispatcher(testScheduler)) {
            calls++
            if (it.contains("sinyi")) Reply(it, 302, headers = mapOf("Location" to "https://evil.test/private"))
            else Reply(it, 200, "{\"RS\":\"OK\"}")
        }
        try { transport.capture(PageRequest("https://www.sinyi.com.tw/"), 1); fail() }
        catch (e: TransportException) { assertEquals(ProbeReason.UNSUPPORTED, e.reason) }
        assertEquals(1, calls)
        val raw = transport.capture(PageRequest("https://buy.cthouse.com.tw/api/house_list.ashx", captureJson = true), 2)
        assertNull(raw.html)
        assertEquals("{\"RS\":\"OK\"}", raw.json.single().body)
        assertTrue(raw.networkObserved)
    }

    @Test fun oversizedBodyFailsAndConnectionsAlwaysDisconnect() = runTest {
        val reply = Reply("https://www.sinyi.com.tw/", text = "x".repeat(8 * 1024 * 1024 + 1))
        val transport = HttpPageTransport("test UA", RequestPacer({ testScheduler.currentTime }, { 0 }), StandardTestDispatcher(testScheduler)) { reply }
        try { transport.capture(PageRequest(reply.url.toString()), 1); fail() }
        catch (e: TransportException) { assertEquals(ProbeReason.UNSUPPORTED, e.reason) }
        assertTrue(reply.disconnected)
    }
}
