package tw.houselens.probe

import java.math.BigDecimal
import java.net.URI
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tw.houselens.probe.sources.HBHousingSource

class HBHousingSourceTest {
    @get:Rule val temp = TemporaryFolder()
    private val query = testQuery(Site.HBHousing)
    private val residential = fixtureText("fixtures/hbhousing/residential.html")
    private val parking = fixtureText("fixtures/hbhousing/parking.html")
    private val ten = fixtureText("fixtures/hbhousing/ten-filtered-cards.html")
    private fun raw(html: String = residential, q: ProbeQuery = query, index: Int = 0) =
        RawCapture(HBHousingSource.request(q, index).url, 200, html, emptyList(), 1, false)
    private fun parse(html: String = residential, q: ProbeQuery = query, index: Int = 0) =
        HBHousingSource.parse(raw(html, q, index), q, index)

    @Test fun residentialCardKeepsCurrentPriceAndArea() {
        val capture = captureFixture(HBHousingSource, query, "fixtures/hbhousing/residential.html")
        assertFalse(capture.networkObserved)
        val page = HBHousingSource.parse(capture, query, 0)
        val item = page.listings.single()
        assertEquals(listOf("YS202291"), page.rawKeys)
        assertEquals(BigDecimal("750"), item.totalPrice)
        assertEquals(BigDecimal("29.05"), item.areaPing)
        assertEquals(BigDecimal("25.82"), item.unitPrice)
        assertEquals(31, item.ageYears)
        assertEquals("5樓/5樓", item.floor)
        assertEquals("新北市", item.city)
        assertEquals("中和區", item.district)
        assertEquals("https://www.hbhousing.com.tw/detail?sn=YS202291", item.url)
        assertEquals("https://img.hbhousing.com.tw/pictures/A271%2fA271YS202291a.jpg?1782581465", item.imageUrl)
        assertFalse(item.hasParking)
        assertTrue("hasParking" in item.unknownFields)
    }

    @Test fun buildingWithParkingAndHalfEvenAge() {
        val q = query.copy(district = "永和區", parkingCodes = "PF")
        val item = parse(parking, q).listings.single()
        assertEquals("ZS203105", item.sourceListingKey)
        assertEquals(BigDecimal("1280"), item.totalPrice)
        assertEquals(BigDecimal("35.00"), item.areaPing)
        assertEquals(BigDecimal("36.57"), item.unitPrice)
        assertTrue(item.hasParking)
        assertFalse("hasParking" in item.unknownFields)
        assertTrue("parkingCodes" in item.unknownFields)
        assertEquals(8, item.ageYears)
        assertEquals(12, parse(residential.replace("30.7年", "12.5年")).listings.single().ageYears)
    }

    @Test fun requestUsesScopedCodesAndOneBasedPathPagination() {
        val q = query.copy(maxTotalPrice = BigDecimal("2000.25"), minSizePing = BigDecimal("20.5"),
            rooms = "3,2,5~", maxAgeYears = 30, parkingCodes = "PF,PM")
        val request = HBHousingSource.request(q, 0)
        assertEquals("/buyhouse/新北市/235/noelevator-elevator-mansion-style/2000.25-down-price/apartment-type/area-20.5-up-area/30-down-age/2_up_room-pattern/parking-tag", URI(request.url).path)
        assertEquals(request.url + "/2-page", HBHousingSource.request(q, 1).url)
        assertTrue(HBHousingSource.request(q, 99).url.endsWith("/100-page"))
        assertTrue(request.browser)
        assertFalse(request.captureJson)
        assertEquals("https://www.hbhousing.com.tw", request.warmupUrl)
        assertTrue(URI(HBHousingSource.request(query.copy(district = "永和區", typeCodes = "MANSION,R1"), 0).url).path.contains("/234/noelevator-mansion-style/"))
        assertEquals("/buyhouse/新北市/231/noelevator-elevator-mansion-style/apartment-type",
            URI(HBHousingSource.request(query.copy(district = "新店區", typeCodes = "", useCode = "", maxTotalPrice = BigDecimal.ZERO), 0).url).path)
    }

    @Test fun rejectsUnknownCodesAndInvalidBounds() {
        for (q in listOf(query.copy(site = Site.F591), query.copy(district = "板橋區"), query.copy(typeCodes = "bogus"),
            query.copy(parkingCodes = "bogus"), query.copy(useCode = "2"), query.copy(rooms = "0"), query.copy(rooms = "abc"))) {
            assertThrows(IllegalArgumentException::class.java) { HBHousingSource.request(q, 0) }
        }
        for (index in listOf(-1, 100)) assertThrows(IllegalArgumentException::class.java) { HBHousingSource.request(query, index) }
    }

    @Test fun tenFilteredCardsStillContinueAndNextPageCanContainResults() {
        val q = query.copy(maxTotalPrice = BigDecimal("1"))
        val page = parse(ten, q)
        assertEquals(10, page.rawKeys.size)
        assertEquals(10, page.rawKeys.toSet().size)
        assertTrue(page.listings.isEmpty())
        assertEquals(10, page.excluded.size)
        assertEquals(NextPage.MORE, page.next)
        assertEquals(PageDecision.CONTINUE, nextDecision(page, emptySet(), 0, 100))
        val second = parse(residential.replace("750 萬", "1 萬"), q, 1)
        assertEquals("YS202291", second.listings.single().sourceListingKey)
        assertEquals(PageDecision.COMPLETE, nextDecision(second, page.rawKeys.toSet(), 1, 100))
    }

    @Test fun rawCountPrecedesTypePriceAndIdDeduplication() {
        val html = residential + parking + ten
        val page = parse(html)
        assertEquals(12, page.rawKeys.size)
        assertEquals(listOf("YS202291"), page.listings.map { it.sourceListingKey })
        assertEquals("wrong_district", page.excluded["ZS203105"])
        assertEquals("non_residential", page.excluded["OS000001"])
        assertEquals(NextPage.UNKNOWN, page.next)
        val repeated = parse(residential.repeat(10))
        assertEquals(List(10) { "YS202291" }, repeated.rawKeys)
        assertEquals(1, repeated.listings.size)
        assertEquals(NextPage.MORE, repeated.next)
        assertEquals(PageDecision.INCONCLUSIVE, nextDecision(repeated, setOf("YS202291"), 1, 100))
        assertEquals(PageDecision.TRUNCATE, nextDecision(repeated, emptySet(), 99, 100))
    }

    @Test fun oneToNineValidRawCardsProveLastPageEvenWhenExcluded() {
        for (count in 1..9) {
            val html = (1..count).joinToString("") { residential.replace("YS202291", "S$it") }
            val page = parse(html, query.copy(maxTotalPrice = BigDecimal.ONE))
            assertEquals(count, page.rawKeys.size)
            assertTrue(page.listings.isEmpty())
            assertEquals(NextPage.END, page.next)
            assertEquals("last_page", page.evidence)
            assertEquals(PageDecision.COMPLETE, nextDecision(page, emptySet(), 0, 100))
        }
    }

    @Test fun fragmentLinksDoNotAddCardsOrOverrideTitleLink() {
        val html = residential.replace("<h3 class=", "<h3><a href='/detail?sn=BAD#life'>地圖</a></h3><h3 class=")
        assertEquals(listOf("YS202291"), parse(html).rawKeys)
    }

    @Test fun brokenRawCardsInvalidateEvenFilteredOrDuplicateItems() {
        for (html in listOf(residential.replace("YS202291", "bad!"),
            residential.replace("/detail?sn=YS202291", "https://evil.invalid/detail?sn=YS202291"),
            residential.replace("/detail?sn=YS202291", "/detail?sn=YS202291&sn=OTHER"),
            residential.replace("750 萬", "0 萬"), residential.replace("750 萬", "7,50 萬"),
            residential.replace("750 萬", "面議"), residential.replace("<h3 ", "<div ").replace("</h3>", "</div>"),
            ten.replaceFirst("750 萬", "面議"), residential + residential.replace("750 萬", "面議"))) {
            assertEquals(PageKind.INVALID, parse(html).kind)
            assertEquals("parse_error", parse(html).evidence)
        }
    }

    @Test fun noCardsCannotProveEmptyAndChallengeTakesPriority() {
        for (html in listOf("", "<main>共0筆</main>", "<p>查無物件</p>", "<p>熱門推薦</p>"))
            assertEquals(PageKind.INVALID, parse(html).kind)
        val challenge = parse("<form id='challenge-form'></form>" + residential)
        assertEquals(PageKind.CHALLENGE, challenge.kind)
        assertEquals(PageDecision.WAIT_FOR_USER, nextDecision(challenge, emptySet(), 0, 100))
        assertEquals(PageKind.INVALID, HBHousingSource.parse(raw().copy(statusCode = 500), query, 0).kind)
    }

    @Test fun staleDifferentQueryAndUnsafeDocumentsAreRejected() {
        val capture = raw()
        for (url in listOf(capture.documentUrl + "/2-page", capture.documentUrl + "?page=2",
            capture.documentUrl.replace("100000-down-price", "1-down-price"),
            capture.documentUrl.replace("/buyhouse/", "/other/"),
            capture.documentUrl.replace("www.hbhousing.com.tw", "www.hbhousing.com.tw.evil.invalid"),
            capture.documentUrl.replace("https:", "http:")))
            assertEquals(url, PageKind.INVALID, HBHousingSource.parse(capture.copy(documentUrl = url), query, 0).kind)
    }

    @Test fun knownTypeLocationPriceAndAgeAreFiltered() {
        for (location in listOf("新北市板橋區", "新北市 板橋區", "新北市　板橋區", "新北市&nbsp;板橋區", "新北市\u200B板橋區"))
            assertEquals("wrong_district", parse(residential.replace("新北市中和區", location)).excluded["YS202291"])
        assertEquals("wrong_city", parse(residential.replace("新北市中和區", "桃園市中壢區")).excluded["YS202291"])
        assertEquals("over_price", parse(q = query.copy(maxTotalPrice = BigDecimal("749.99"))).excluded["YS202291"])
        assertEquals("over_age", parse(q = query.copy(maxAgeYears = 30)).excluded["YS202291"])
        assertEquals("unsupported_type", parse(q = query.copy(typeCodes = "R2")).excluded["YS202291"])
    }

    @Test fun buildingTypeIsNotMistakenForFloorAndCommercialTypesStayExcluded() {
        val q = query.copy(district = "永和區")
        val item = parse(parking, q).listings.single()
        assertFalse("typeCodes" in item.unknownFields)
        assertEquals("unsupported_type", parse(parking, q.copy(typeCodes = "R1")).excluded["ZS203105"])
        for (type in listOf("辦公大樓", "廠房"))
            assertEquals(type, "non_residential", parse(residential.replace("公寓 |", "$type |")).excluded["YS202291"])
    }

    @Test fun missingDetailsRemainUnknownAndAreaBasisIsNotAssumed() {
        val html = residential.replace("新北市中和區中和路", "").replace("公寓 | ", "")
            .replace("30.7年", "未知").replace("29.05坪", "未知坪").replace("img.hbhousing.com.tw", "evil.invalid")
        val item = parse(html, query.copy(rooms = "2,3,5~", parkingCodes = "PF", minSizePing = BigDecimal("40"))).listings.single()
        assertTrue(item.unknownFields.containsAll(setOf("city", "district", "address", "typeCodes", "useCode", "rooms",
            "parkingCodes", "areaBasis", "areaPing", "unitPrice", "ageYears", "hasParking", "imageUrl", "postedDate")))
        assertEquals(BigDecimal.ZERO, item.areaPing)
        assertNull(item.unitPrice)
        assertNull(item.ageYears)
        assertNull(item.imageUrl)
        assertEquals("5樓/5樓", item.floor)
        assertEquals(1, parse(q = query.copy(minSizePing = BigDecimal("40"))).listings.size)
    }

    @Test fun negatedParkingTextDoesNotBecomeConfirmedParking() {
        val q = query.copy(district = "永和區", parkingCodes = "PF")
        for (label in listOf("無車位", "無 車位", "無&nbsp;車位", "<span>無</span> <span>車位</span>", "未含 車位", "不含 車位")) {
            val item = parse(parking.replace("有車位</div>", "$label</div>"), q).listings.single()
            assertFalse(label, item.hasParking)
            assertTrue(label, "hasParking" in item.unknownFields)
        }
    }

    @Test fun onlyExplicitPositiveParkingTagsConfirmParking() {
        val q = query.copy(district = "永和區", parkingCodes = "PF")
        for (label in listOf("車位：無", "無固定車位", "車位另租", "車位洽詢")) {
            val item = parse(parking.replace("有車位</div>", "$label</div>"), q).listings.single()
            assertFalse(label, item.hasParking)
            assertTrue(label, "hasParking" in item.unknownFields)
        }
        for (label in listOf("車位", "有 車位", "有&nbsp;車位", "<span>有</span> <span>車位</span>")) {
            val item = parse(parking.replace("有車位</div>", "$label</div>"), q).listings.single()
            assertTrue(label, item.hasParking)
            assertFalse(label, "hasParking" in item.unknownFields)
        }
    }

    @Test fun rawCountsAndBoundaryEvidenceSurviveStoreAndExport() {
        val runId = "11111111-1111-4111-8111-111111111111"
        val process = "22222222-2222-4222-8222-222222222222"
        val store = ProbeStore(temp.root, process, utcNow = { "2026-11-06T00:00:00Z" }, elapsedNow = { 0L })
        store.create(RunRecord(runId, process, query, RunMode.FOREGROUND, RunState.RUNNING,
            DeviceSnapshot(null,null,null,null,null,null,null,null,null,null,null,null), EvidenceSource.SIMULATED))
        for ((index, page) in listOf(parse(residential.repeat(10)), parse(ten), parse()).withIndex()) {
            store.savePage(runId, index, page)
            assertEquals(page, store.loadPage(runId, index))
        }
        store.export(runId, java.io.ByteArrayOutputStream())
    }
}
