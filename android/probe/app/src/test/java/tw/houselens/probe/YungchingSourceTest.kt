package tw.houselens.probe

import java.math.BigDecimal
import java.net.URI
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tw.houselens.probe.sources.YungchingSource

class YungchingSourceTest {
    @get:Rule val temp = TemporaryFolder()
    private val query = testQuery(Site.Yungching)
    private val residential get() = fixtureText("fixtures/yungching/residential.html")
    private val parking get() = fixtureText("fixtures/yungching/parking.html")
    private val empty get() = fixtureText("fixtures/yungching/empty-featured.html")
    private fun raw(html: String = residential, q: ProbeQuery = query, index: Int = 0) =
        RawCapture(YungchingSource.request(q, index).url, 200, html, emptyList(), 1, false)
    private fun parse(html: String = residential, q: ProbeQuery = query, index: Int = 0) =
        YungchingSource.parse(raw(html, q, index), q, index)

    @Test fun requestMapsNativeAndFallbackCodesAndOneBasedPages() {
        val q = query.copy(maxTotalPrice = BigDecimal("800"), minSizePing = BigDecimal("20"),
            maxAgeYears = 30, parkingCodes = "PF,PM")
        val first = YungchingSource.request(q, 0)
        assertEquals("/list/新北市-中和區_c/-800_price/電梯大廈,華廈,無電梯公寓_type/20-_pin/住宅_p/坡道平面,坡道機械,昇降平面,昇降機械_park/-30_age", URI(first.url).path)
        assertNull(URI(first.url).query)
        assertTrue(first.browser)
        assertFalse(first.captureJson)
        assertEquals("https://buy.yungching.com.tw", first.warmupUrl)
        assertEquals("pg=2", URI(YungchingSource.request(q, 1).url).query)
        assertEquals("pg=30", URI(YungchingSource.request(q, 29).url).query)
        assertEquals(30, YungchingSource.maxPages)
        val native = q.copy(typeCodes = "無電梯公寓,華廈,電梯大廈", parkingCodes = "昇降機械,坡道平面")
        assertTrue(URI(YungchingSource.request(native, 0).url).path.contains("/坡道平面,昇降機械_park/"))
        assertTrue(URI(YungchingSource.request(q.copy(parkingCodes = "Y"), 0).url).path.contains("/y_park/"))
        for (district in listOf("永和區", "新店區"))
            assertTrue(URI(YungchingSource.request(q.copy(district = district), 0).url).path.contains("新北市-${district}_c"))
    }

    @Test fun optionalFiltersDefaultsPrecisionAndUnsupportedCodes() {
        val default = URI(YungchingSource.request(query.copy(typeCodes = "", useCode = "", maxTotalPrice = BigDecimal.ZERO), 0).url).path
        assertEquals("/list/新北市-中和區_c/電梯大廈,華廈,無電梯公寓_type/住宅_p", default)
        val precise = URI(YungchingSource.request(query.copy(maxTotalPrice = BigDecimal("799.50"), minSizePing = BigDecimal("28.505")), 0).url).path
        assertTrue(precise.contains("/-799.5_price/"))
        assertTrue(precise.contains("/28.505-_pin/"))
        for (q in listOf(query.copy(site = Site.F591), query.copy(typeCodes = "R3"), query.copy(typeCodes = "R1,typo"),
            query.copy(useCode = "2"), query.copy(parkingCodes = "XX"), query.copy(rooms = "abc")))
            assertThrows(IllegalArgumentException::class.java) { YungchingSource.request(q, 0) }
        for (index in listOf(-1, 30)) assertThrows(IllegalArgumentException::class.java) { YungchingSource.request(query, index) }
    }

    @Test fun residentialPreservesFieldsWithHalfEvenAgeAndUnitPrice() {
        val page = parse()
        val item = page.listings.single()
        assertEquals(listOf("1234567"), page.rawKeys)
        assertEquals(Site.Yungching, item.sourceSite)
        assertEquals("新北市", item.city)
        assertEquals("中和區", item.district)
        assertEquals("新北市中和區中和路", item.address)
        assertEquals("中和優質公寓", item.title)
        assertEquals(BigDecimal("750"), item.totalPrice)
        assertEquals(BigDecimal("28.50"), item.areaPing)
        assertEquals(BigDecimal("26.32"), item.unitPrice)
        assertEquals(12, item.ageYears)
        assertEquals(14, parse(residential.replace("12.5年", "13.5年")).listings.single().ageYears)
        assertEquals("5/12樓", item.floor)
        assertEquals("https://buy.yungching.com.tw/house/1234567", item.url)
        assertTrue(item.imageUrl!!.contains("width=480&height=0"))
        assertFalse(item.hasParking)
        assertTrue("hasParking" in item.unknownFields)
        assertFalse("typeCodes" in item.unknownFields)
        assertFalse(raw().networkObserved)
    }

    @Test fun buildingWithParkingAndTypeFilters() {
        val q = query.copy(district = "永和區")
        val item = parse(parking, q).listings.single()
        assertEquals("9876543", item.sourceListingKey)
        assertEquals(BigDecimal("1280"), item.totalPrice)
        assertEquals(BigDecimal("35.00"), item.areaPing)
        assertEquals(8, item.ageYears)
        assertTrue(item.hasParking)
        assertFalse("hasParking" in item.unknownFields)
        assertEquals("non_residential", parse(residential.replace("公寓</span>", "辦公商業大樓</span>")).excluded["1234567"])
        assertEquals("unsupported_type", parse(q = query.copy(typeCodes = "R2")).excluded["1234567"])
        assertEquals(1, parse(parking, q.copy(typeCodes = "電梯大廈")).listings.size)
        assertEquals(1, parse(q = query.copy(typeCodes = "R1")).listings.size)
    }

    @Test fun emptyMarkerOverridesFeaturedCardsAndProvesEnd() {
        val page = parse(empty)
        assertEquals(PageKind.EMPTY, page.kind)
        assertTrue(page.rawKeys.isEmpty())
        assertTrue(page.listings.isEmpty())
        assertTrue(page.excluded.isEmpty())
        assertEquals(NextPage.END, page.next)
        assertEquals("empty_marker", page.evidence)
        assertEquals(PageDecision.COMPLETE, nextDecision(page, emptySet(), 0, 30))
    }

    @Test fun challengeTakesPriorityOverEmptyAndCards() {
        val capture = raw("<form id='challenge-form'>請完成驗證</form>$empty").copy(statusCode = 403)
        val page = YungchingSource.parse(capture, query, 0)
        assertEquals(PageKind.CHALLENGE, page.kind)
        assertTrue(page.rawKeys.isEmpty())
        assertEquals(PageDecision.WAIT_FOR_USER, nextDecision(page, emptySet(), 0, 30))
        assertEquals(PageKind.CHALLENGE, parse("<p>Verify you are human</p>$empty").kind)
    }

    @Test fun fullyFilteredPageDoesNotStopBeforeValidSecondPage() {
        val q = query.copy(maxTotalPrice = BigDecimal("800"))
        val cards = (1..20).joinToString("") { residential.replace("1234567", "${2000000 + it}").replace(">750<", ">900<") }
        val first = parse(cards, q)
        assertEquals(PageKind.RESULTS, first.kind)
        assertEquals(20, first.rawKeys.size)
        assertEquals(20, first.excluded.size)
        assertTrue(first.listings.isEmpty())
        assertEquals(NextPage.UNKNOWN, first.next)
        assertEquals(PageDecision.CONTINUE, nextDecision(first, emptySet(), 0, 30))
        val second = parse(residential, q, 1)
        assertEquals("1234567", second.listings.single().sourceListingKey)
        assertEquals(PageDecision.CONTINUE, nextDecision(second, first.rawKeys.toSet(), 1, 30))
        assertEquals(PageDecision.TRUNCATE, nextDecision(second, emptySet(), 29, 30))
    }

    @Test fun duplicatesAreNotEvidenceOfCompletion() {
        val page = parse(residential + residential)
        assertEquals(1, page.rawKeys.size)
        assertEquals(1, page.listings.size)
        assertEquals(PageDecision.INCONCLUSIVE, nextDecision(page, page.rawKeys.toSet(), 1, 30))
    }

    @Test fun missingOrBadIdAndPriceInvalidateEvenExcludedCards() {
        for (html in listOf(residential.replace("house/1234567", "house/bad"),
            residential.replace("house/1234567", "house/1234567evil"),
            residential.replace("house/1234567", "https://evil.invalid/house/1234567"),
            residential.replace(">750<", ">0<"), residential.replace(">750<", ">7,50<"),
            residential.replace(">750<", ">面議<"),
            residential + residential.replace(">750<", ">壞資料<"),
            residential.replace("公寓</span>", "店面</span>").replace(">750<", ">面議<"))) {
            assertEquals(PageKind.INVALID, parse(html).kind)
        }
    }

    @Test fun noCardsAndUnscopedTextCannotProveEmpty() {
        for (html in listOf("", "<p>本週精選</p>", "<p>共0筆</p>",
            "<script>const msg = '暫無符合物件';</script>", "<template><p>暫無符合物件</p></template>",
            "<div hidden>暫無符合物件</div>")) assertEquals(PageKind.INVALID, parse(html).kind)
        assertEquals(PageKind.RESULTS, parse(residential.replace("中和優質公寓", "暫無符合物件")).kind)
        assertEquals(PageKind.INVALID, YungchingSource.parse(raw(empty).copy(statusCode = 500), query, 0).kind)
    }

    @Test fun knownLocationPriceAndAgeAreFiltered() {
        for (location in listOf("新北市板橋區", "新北市 板橋區", "新北市　板橋區", "新北市&nbsp;板橋區"))
            assertEquals(location, "wrong_district", parse(residential.replace("新北市中和區", location)).excluded["1234567"])
        assertEquals("wrong_city", parse(residential.replace("新北市中和區", "桃園市中壢區")).excluded["1234567"])
        assertEquals("over_price", parse(q = query.copy(maxTotalPrice = BigDecimal("749.99"))).excluded["1234567"])
        assertEquals("over_age", parse(q = query.copy(maxAgeYears = 11)).excluded["1234567"])
    }

    @Test fun missingFieldsStayUnknownWithoutInventingParkingOrMatchingFilters() {
        val html = residential.replace("新北市中和區中和路", "").replace("公寓</span>", "</span>")
            .replace("12.5年", "未知").replace("建坪28.50", "建坪未知").replace("yccdn.yungching.com.tw", "evil.invalid")
        val item = parse(html, query.copy(rooms = "2,3,4,5~", parkingCodes = "PF", minSizePing = BigDecimal("40"))).listings.single()
        assertTrue(item.unknownFields.containsAll(setOf("city", "district", "address", "typeCodes", "useCode", "rooms",
            "parkingCodes", "areaBasis", "areaPing", "unitPrice", "ageYears", "hasParking", "imageUrl", "postedDate")))
        assertEquals(BigDecimal.ZERO, item.areaPing)
        assertNull(item.unitPrice)
        assertNull(item.ageYears)
        assertNull(item.imageUrl)
    }

    @Test fun staleDifferentQueryAndUnsafeDocumentsAreRejected() {
        val capture = raw()
        for (url in listOf(capture.documentUrl + "?pg=2", capture.documentUrl + "?pg=1&pg=1",
            capture.documentUrl.replace("100000_price", "1_price"),
            capture.documentUrl.replace("/list/", "/other/"),
            capture.documentUrl.replace("buy.yungching.com.tw", "buy.yungching.com.tw.evil.invalid"),
            capture.documentUrl.replace("https:", "http:")))
            assertEquals(url, PageKind.INVALID, YungchingSource.parse(capture.copy(documentUrl = url), query, 0).kind)
    }

    @Test fun resultAndEmptyPagesCanBeStoredAndExportedAsSimulatedEvidence() {
        val runId = "11111111-1111-4111-8111-111111111111"
        val process = "22222222-2222-4222-8222-222222222222"
        val store = ProbeStore(temp.root, process, utcNow = { "2026-11-06T00:00:00Z" }, elapsedNow = { 0L })
        store.create(RunRecord(runId, process, query, RunMode.FOREGROUND, RunState.RUNNING,
            DeviceSnapshot(null,null,null,null,null,null,null,null,null,null,null,null), EvidenceSource.SIMULATED))
        for ((index, page) in listOf(parse(), parse(empty)).withIndex()) {
            store.savePage(runId, index, page)
            assertEquals(page, store.loadPage(runId, index))
        }
        store.export(runId, java.io.ByteArrayOutputStream())
    }

    @Test fun inlineHiddenEmptyMarkersCannotDiscardSearchResults() {
        for (style in listOf("display:none", "DISPLAY: none !important;", "visibility: hidden", "visibility:collapse")) {
            val page = parse("<div style='$style'><p>暫無符合物件</p></div>$residential")
            assertEquals(style, PageKind.RESULTS, page.kind)
            assertEquals(listOf("1234567"), page.rawKeys)
            assertEquals(NextPage.UNKNOWN, page.next)
        }
    }

    @Test fun negatedParkingTextDoesNotBecomeConfirmedParking() {
        val q = query.copy(district = "永和區", parkingCodes = "PF")
        for (label in listOf("無車位", "無 車位", "無&nbsp;車位", "<span>無</span> <span>車位</span>", "未含 車位", "不含 車位")) {
            val item = parse(parking.replace("3房(室)2廳2衛 車位", "3房(室)2廳2衛 $label"), q).listings.single()
            assertFalse(label, item.hasParking)
            assertTrue(label, "hasParking" in item.unknownFields)
        }
        assertTrue(parse(parking, q).listings.single().hasParking)
    }
}
