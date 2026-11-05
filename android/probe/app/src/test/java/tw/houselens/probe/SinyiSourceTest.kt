package tw.houselens.probe

import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tw.houselens.probe.sources.SinyiSource

@OptIn(ExperimentalCoroutinesApi::class)
class SinyiSourceTest {
    @get:Rule val temp = TemporaryFolder()
    private val query = testQuery(Site.Sinyi)
    private val search = fixtureText("fixtures/sinyi/search.html")
    private fun raw(html: String = search, q: ProbeQuery = query, index: Int = 0) =
        RawCapture(SinyiSource.request(q, index).url, 200, html, emptyList(), 1, false)
    private fun parse(html: String = search, q: ProbeQuery = query, index: Int = 0) =
        SinyiSource.parse(raw(html, q, index), q, index)

    @Test fun semanticSsrCardKeepsPriceAreaAndLocation() {
        val capture = captureFixture(SinyiSource, query, "fixtures/sinyi/search.html")
        assertFalse(capture.networkObserved)
        val page = SinyiSource.parse(capture, query, 0)
        val item = page.listings.single()
        assertEquals(listOf("3087NK"), page.rawKeys)
        assertEquals(BigDecimal("750"), item.totalPrice)
        assertEquals(BigDecimal("28.50"), item.areaPing)
        assertEquals(BigDecimal("26.32"), item.unitPrice)
        assertEquals(28, item.ageYears)
        assertEquals("11樓/11樓", item.floor)
        assertEquals("新北市", item.city)
        assertEquals("中和區", item.district)
        assertEquals("中和住宅測試", item.title)
        assertEquals("https://www.sinyi.com.tw/buy/house/3087NK", item.url)
        assertEquals("https://res.sinyi.com.tw/buy/3087NK/smallimg/A.JPG", item.imageUrl)
        assertEquals("中和住宅測試", parse(search.replace("__fixture", "__changed")).listings.single().title)
    }

    @Test fun requestUsesHttpAndOrderedFiltersWithExactDecimals() {
        val q = query.copy(maxTotalPrice = BigDecimal("1000.25"), minSizePing = BigDecimal("20.5"),
            rooms = "3,4,5~", maxAgeYears = 30, parkingCodes = "PF,PM")
        val request = SinyiSource.request(q, 0)
        assertEquals("https://www.sinyi.com.tw/buy/list/0-1000.25-price/apartment-dalou-huaxia-type/" +
            "plane-auto-mix-mechanical-firstfloor-tower-yesparking/20.5-up-area/0-30-year/" +
            "3-up-roomtotal/NewTaipei-city/235-zip/default-desc/1", request.url)
        assertFalse(request.browser)
        assertFalse(request.captureJson)
        assertNull(request.warmupUrl)
        assertEquals("GET", request.method)
        assertTrue(SinyiSource.request(q, 4).url.endsWith("/5"))
        assertTrue(SinyiSource.request(query.copy(district = "永和區", parkingCodes = "mechanical,plane"), 0).url
            .contains("/plane-mechanical-yesparking/NewTaipei-city/234-zip/"))
        assertTrue(SinyiSource.request(query.copy(typeCodes = "R5,R4,DALOU", parkingCodes = "yesparking"), 0).url
            .contains("/dalou-townhouse-villa-type/yesparking/"))
        assertEquals("https://www.sinyi.com.tw/buy/list/apartment-townhouse-villa-dalou-huaxia-type/NewTaipei-city/231-zip/default-desc/2",
            SinyiSource.request(query.copy(district = "新店區", typeCodes = "", useCode = "", maxTotalPrice = BigDecimal.ZERO), 1).url)
    }

    @Test fun rejectsUnknownCodesAndPageBounds() {
        for (q in listOf(query.copy(site = Site.F591), query.copy(district = "板橋區"), query.copy(typeCodes = "bogus"),
            query.copy(typeCodes = "R3"), query.copy(parkingCodes = "bogus"), query.copy(useCode = "2"), query.copy(rooms = "0"), query.copy(rooms = "abc")))
            assertThrows(IllegalArgumentException::class.java) { SinyiSource.request(q, 0) }
        for (index in listOf(-1, 5)) assertThrows(IllegalArgumentException::class.java) { SinyiSource.request(query, index) }
    }

    @Test fun globalNextDataAndUnknownEmptyPagesNeverBecomeResults() {
        val capture = captureFixture(SinyiSource, query, "fixtures/sinyi/next-data-only.html")
        val page = SinyiSource.parse(capture, query, 0)
        assertTrue(page.listings.isEmpty())
        assertEquals(PageKind.INVALID, page.kind)
        for (html in listOf("", "<main>共0筆</main>", "<div class='not-buy-list-item'>750萬</div>"))
            assertEquals(PageKind.INVALID, parse(html).kind)
        assertEquals(listOf("3087NK"), parse(search + capture.html).rawKeys)
    }

    @Test fun rawTwentyCardsContinueEvenWhenAllFiltered() {
        val q = query.copy(maxTotalPrice = BigDecimal.ONE)
        val html = (1..20).joinToString("") { search.replace("3087NK", "ID$it") }
        val page = parse(html, q)
        assertEquals(20, page.rawKeys.size)
        assertTrue(page.listings.isEmpty())
        assertEquals(20, page.excluded.size)
        assertEquals(NextPage.MORE, page.next)
        assertEquals(PageDecision.CONTINUE, nextDecision(page, emptySet(), 0, 5))
        assertEquals(listOf("3087NK"), parse(search.replace("750萬", "1萬"), q, 1).listings.map { it.sourceListingKey })
        assertEquals(PageDecision.TRUNCATE, nextDecision(page, emptySet(), 4, 5))
    }

    @Test fun rawCountBeforeDeduplicationControlsPageBoundary() {
        val page = parse(search.repeat(20))
        assertEquals(List(20) { "3087NK" }, page.rawKeys)
        assertEquals(1, page.listings.size)
        assertEquals(NextPage.MORE, page.next)
        assertEquals(PageDecision.INCONCLUSIVE, nextDecision(page, setOf("3087NK"), 1, 5))
        for (count in 1..19) {
            val short = parse(search.repeat(count), query.copy(maxTotalPrice = BigDecimal.ONE))
            assertEquals(NextPage.END, short.next)
            assertEquals("last_page", short.evidence)
            assertEquals(PageDecision.COMPLETE, nextDecision(short, emptySet(), 0, 5))
        }
        assertEquals(NextPage.UNKNOWN, parse(search.repeat(21)).next)
    }

    @Test fun brokenCardsInvalidateBeforeFilteringOrDeduplication() {
        for (html in listOf(search.replace("/buy/house/3087NK?", "/buy/house/bad!?"),
            search.replace("/buy/house/", "https://evil.invalid/buy/house/"),
            search.replace("href=", "data-href="), search.replace("750萬", "面議"),
            search.replace("750萬", "0萬"), search.replace("750萬", "-750萬"), search.replace("750萬", "7,50萬"),
            search + search.replace("750萬", "面議"))) {
            assertEquals(PageKind.INVALID, parse(html, query.copy(maxTotalPrice = BigDecimal.ONE)).kind)
            assertEquals("parse_error", parse(html).evidence)
        }
    }

    @Test fun ambiguousPricesCannotSilentlyChooseOldPrice() {
        assertEquals(PageKind.INVALID, parse(search.replace("750萬", "原價900萬 現價750萬")).kind)
        assertEquals(BigDecimal("750.25"), parse(search.replace("750萬", "750.25萬")).listings.single().totalPrice)
        assertEquals(BigDecimal("1750"), parse(search.replace("750萬", "1,750萬")).listings.single().totalPrice)
    }

    @Test fun malformedPriceTokensCannotBecomeValidSuffixes() {
        for (amount in listOf("1, 750萬", "7.5e2萬", "−750萬", "7/50萬", "７50萬")) {
            val page = parse(search.replace("750萬", amount))
            assertEquals(amount, PageKind.INVALID, page.kind)
            assertEquals("parse_error", page.evidence)
        }
    }

    @Test fun unsupportedFloorsRemainUnknownInsteadOfLosingTheirPrefix() {
        for (floor in listOf("B1樓/11樓", "地下1樓/11樓", "1~3樓/11樓")) {
            val item = parse(search.replace("11樓/11樓", floor)).listings.single()
            assertNull(floor, item.floor)
            assertTrue("floor" in item.unknownFields)
        }
    }

    @Test fun challengeHttpFailureAndMismatchedDocumentsAreRejected() {
        assertEquals(PageKind.CHALLENGE, parse("<form id='challenge-form'></form>" + search).kind)
        assertEquals(PageKind.INVALID, SinyiSource.parse(raw().copy(statusCode = 500), query, 0).kind)
        for (url in listOf(raw().documentUrl.dropLast(1) + "2", raw().documentUrl + "?page=2",
            raw().documentUrl.replace("100000", "1"), raw().documentUrl.replace("www.sinyi.com.tw", "www.sinyi.com.tw.evil.invalid"),
            raw().documentUrl.replace("https:", "http:")))
            assertEquals(PageKind.INVALID, SinyiSource.parse(raw().copy(documentUrl = url), query, 0).kind)
    }

    @Test fun knownLocationPriceAndAgeAreFiltered() {
        for (address in listOf("新北市板橋區", "新北市 板橋區", "新北市&nbsp;板橋區", "新北市　板橋區"))
            assertEquals("wrong_district", parse(search.replace("新北市中和區", address)).excluded["3087NK"])
        assertEquals("wrong_city", parse(search.replace("新北市中和區", "桃園市中壢區")).excluded["3087NK"])
        assertEquals("over_price", parse(q = query.copy(maxTotalPrice = BigDecimal("749.99"))).excluded["3087NK"])
        assertEquals("over_age", parse(q = query.copy(maxAgeYears = 27)).excluded["3087NK"])
        assertEquals(28, parse(search.replace("28.4年", "28.5年")).listings.single().ageYears)
    }

    @Test fun missingDetailsAndUnverifiedConditionsStayUnknown() {
        val html = search.replace("新北市中和區中和路", "").replace("建坪 28.50", "").replace("28.4年", "").replace("11樓/11樓", "")
        val item = parse(html, query.copy(rooms = "3,4,5~", parkingCodes = "PF")).listings.single()
        assertTrue(item.unknownFields.containsAll(setOf("city", "district", "address", "areaPing", "unitPrice", "ageYears", "floor",
            "typeCodes", "useCode", "rooms", "parkingCodes", "hasParking", "areaBasis", "postedDate")))
        assertNull(item.ageYears)
        assertEquals(BigDecimal.ZERO, item.areaPing)
        assertEquals(1, parse(q = query.copy(minSizePing = BigDecimal("40"))).listings.size)
        for (title in listOf("有車位", "無車位", "車位另租")) {
            val unknown = parse(search.replace("中和住宅測試", title)).listings.single()
            assertFalse(unknown.hasParking)
            assertTrue("hasParking" in unknown.unknownFields)
        }
    }

    @Test fun videoFirstIsSkippedAfterNextImageDecoding() {
        val html = fixtureText("fixtures/sinyi/video-first.html")
        assertEquals("https://res.sinyi.com.tw/buy/3087NK/smallimg/B.JPG?v=1", parse(html).listings.single().imageUrl)
        for (ext in listOf("M3U8", "WEBM", "MOV"))
            assertTrue(parse(html.replace("MP4", ext)).listings.single().imageUrl!!.contains("/B.JPG"))
    }

    @Test fun lazyAndMissingImagesUseExistingSourceFallback() {
        val noImage = search.substringBefore("  <img") + "</div>"
        val fallback = parse(noImage).listings.single()
        assertEquals("https://res.sinyi.com.tw/buy/3087NK/smallimg/A.JPG", fallback.imageUrl)
        assertTrue("imageUrl" in fallback.unknownFields)
        assertEquals("https://res.sinyi.com.tw/lazy.jpg", parse(noImage.replace("</div>",
            "<img data-src='https://res.sinyi.com.tw/lazy.jpg'></div>")).listings.single().imageUrl)
    }

    @Test fun unsafeAndAmbiguousNextImagesCannotEscapeAllowlist() {
        for (src in listOf("https://evil.invalid/a.jpg", "http://res.sinyi.com.tw/a.jpg", "https://res.sinyi.com.tw.evil.invalid/a.jpg",
            "https://res.sinyi.com.tw/a.mp4?v=1", "/_next/image?url=%ZZ", "/_next/image?url=https%3A%2F%2Fevil.invalid%2Fa.jpg",
            "/_next/image?url=https%3A%2F%2Fres.sinyi.com.tw%2Fb.jpg&amp;url=https%3A%2F%2Fevil.invalid%2Fa.jpg")) {
            val html = search.substringBefore("  <img") + "<img src='$src'></div>"
            val item = parse(html).listings.single()
            assertEquals(src, "https://res.sinyi.com.tw/buy/3087NK/smallimg/A.JPG", item.imageUrl)
            assertTrue(src, "imageUrl" in item.unknownFields)
        }
    }

    @Test fun imageFailureLeavesSavedListIntactAndHeadersStayScoped() = runTest {
        val page = parse()
        val store = ProbeStore(temp.root, "22222222-2222-4222-8222-222222222222", utcNow = { "2026-11-06T00:00:00Z" }, elapsedNow = { 0L })
        val id = "11111111-1111-4111-8111-111111111111"
        store.create(RunRecord(id, "22222222-2222-4222-8222-222222222222", query, RunMode.FOREGROUND, RunState.RUNNING,
            DeviceSnapshot(null,null,null,null,null,null,null,null,null,null,null,null), EvidenceSource.SIMULATED))
        store.savePage(id, 0, page)
        val replies = mutableListOf<HttpURLConnection>()
        val transport = HttpPageTransport("test UA", RequestPacer({ testScheduler.currentTime }, { 0 }), StandardTestDispatcher(testScheduler)) {
            object : HttpURLConnection(URL(it)) {
                override fun connect() { }
                override fun disconnect() { }
                override fun usingProxy() = false
                override fun getResponseCode() = if (url.host == "res.sinyi.com.tw") 404 else 200
                override fun getInputStream() = ByteArrayInputStream("image".toByteArray())
                override fun getHeaderField(name: String) = if (name == "Content-Type") "image/jpeg" else null
            }.also(replies::add)
        }
        try { transport.loadImage(Site.Sinyi, page.listings.single().imageUrl!!); fail() }
        catch (e: TransportException) { assertEquals(ProbeReason.HTTP_ERROR, e.reason); assertEquals(404, e.status) }
        transport.loadImage(Site.F591, "https://img.591.com.tw/a.jpg")
        assertEquals("https://www.sinyi.com.tw/", replies[0].getRequestProperty("Referer"))
        assertNull(replies[1].getRequestProperty("Referer"))
        assertTrue(replies.all { it.requestProperties.keys.none { key -> key.lowercase() in setOf("authorization", "apikey", "cookie") } })
        assertEquals(page, store.loadPage(id, 0))
        store.export(id, java.io.ByteArrayOutputStream())
    }
}
