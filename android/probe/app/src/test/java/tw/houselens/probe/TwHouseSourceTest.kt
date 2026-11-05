package tw.houselens.probe

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tw.houselens.probe.sources.TwHouseSource

class TwHouseSourceTest {
    @get:Rule val temp = TemporaryFolder()
    private val query = testQuery(Site.TwHouse)
    private val residential get() = fixtureText("fixtures/twhouse/residential.html")
    private val discounted get() = fixtureText("fixtures/twhouse/discounted.html")
    private val empty get() = fixtureText("fixtures/twhouse/empty.html")
    private fun raw(html: String = residential, q: ProbeQuery = query, index: Int = 0) =
        RawCapture(TwHouseSource.request(q, index).url, 200, html, emptyList(), 1, false)
    private fun parse(html: String = residential, q: ProbeQuery = query, index: Int = 0) =
        TwHouseSource.parse(raw(html, q, index), q, index)

    @Test fun requestPreservesBuildingAreaFiltersAndOneBasedPages() {
        val q = query.copy(maxTotalPrice = BigDecimal("1000"), minSizePing = BigDecimal("25"),
            rooms = "2,3,5~", maxAgeYears = 30, parkingCodes = "PF,PM")
        val first = TwHouseSource.request(q, 0)
        assertEquals("https://www.twhg.com.tw/buy/list/newTaipei-city/235-zips/apartment-midrise-condo-kinds/1000down-price/25up-ping/fullBuildingPing-ping_type/2up-bedrooms/30down-house_year/1up-park_count/recomended-desc?page=1", first.url)
        assertEquals("GET", first.method)
        assertNull(first.body)
        assertNull(first.warmupUrl)
        assertFalse(first.browser)
        assertFalse(first.captureJson)
        assertTrue(TwHouseSource.request(q, 29).url.endsWith("?page=30"))
        assertEquals(30, TwHouseSource.maxPages)
        assertTrue(TwHouseSource.request(q.copy(district = "永和區", typeCodes = "R1"), 1).url.contains("/234-zips/apartment-kinds/"))
        assertTrue(TwHouseSource.request(q.copy(district = "新店區", typeCodes = "R2"), 1).url.contains("/231-zips/midrise-condo-kinds/"))
        assertEquals(first, TwHouseSource.request(q.copy(typeCodes = "CONDO,apartment,midrise"), 0))
    }

    @Test fun defaultsDecimalsAndUnsupportedCodesAreHandledExplicitly() {
        assertEquals("https://www.twhg.com.tw/buy/list/newTaipei-city/235-zips/apartment-midrise-condo-kinds/recomended-desc?page=1",
            TwHouseSource.request(query.copy(typeCodes = "", useCode = "", maxTotalPrice = BigDecimal.ZERO), 0).url)
        val url = TwHouseSource.request(query.copy(maxTotalPrice = BigDecimal("799.50"), minSizePing = BigDecimal("25.125")), 0).url
        assertTrue(url.contains("/799.5down-price/25.125up-ping/fullBuildingPing-ping_type/"))
        for (q in listOf(query.copy(site = Site.F591), query.copy(typeCodes = "R3"), query.copy(typeCodes = "R1,typo"),
            query.copy(parkingCodes = "XX"), query.copy(useCode = "2"), query.copy(rooms = "abc")))
            assertThrows(IllegalArgumentException::class.java) { TwHouseSource.request(q, 0) }
        for (index in listOf(-1, 30)) assertThrows(IllegalArgumentException::class.java) { TwHouseSource.request(query, index) }
    }

    @Test fun residentialFieldsAndUnknownDetailFieldsArePreserved() {
        val page = parse()
        val item = page.listings.single()
        assertEquals(listOf("A123456789"), page.rawKeys)
        assertEquals("中和三房美廈", item.title)
        assertEquals("新北市", item.city)
        assertEquals("中和區", item.district)
        assertEquals(BigDecimal("760"), item.totalPrice)
        assertEquals(BigDecimal("34.56"), item.areaPing)
        assertEquals(BigDecimal("21.99"), item.unitPrice)
        assertEquals(25, item.ageYears)
        assertEquals(24, parse(residential.replace("25.3年", "24.5年")).listings.single().ageYears)
        assertEquals("https://www.twhg.com.tw/buy/A123456789", item.url)
        assertEquals("https://img.twhg.com.tw/admin/images/OB01/AAAA/A123456789.jpg", item.imageUrl)
        assertNull(item.address)
        assertNull(item.floor)
        assertNull(item.postedDate)
        assertFalse(item.hasParking)
        assertTrue(item.unknownFields.containsAll(setOf("address", "floor", "hasParking", "postedDate", "typeCodes", "useCode")))
        assertFalse(raw().networkObserved)
    }

    @Test fun discountedPriceIsCurrentPriceAndKeepsDecimalPrecision() {
        val q = query.copy(district = "永和區", maxTotalPrice = BigDecimal("1500"))
        val item = parse(discounted, q).listings.single()
        assertEquals("TF02157997", item.sourceListingKey)
        assertEquals("永和降價美廈", item.title)
        assertEquals(BigDecimal("1480"), item.totalPrice)
        assertEquals(BigDecimal("32.00"), item.areaPing)
        assertEquals(15, item.ageYears)
        assertEquals(BigDecimal("1480.50"), parse(discounted.replace("1,480萬", "1,480.50萬"), q).listings.single().totalPrice)
    }

    @Test fun landAreaCannotBecomeBuildingArea() {
        val html = residential.replace("建坪 34.56 坪", "地坪 80.00 坪")
        val item = parse(html).listings.single()
        assertEquals(BigDecimal.ZERO, item.areaPing)
        assertNull(item.unitPrice)
        assertTrue(item.unknownFields.containsAll(setOf("areaPing", "unitPrice")))
        assertEquals(BigDecimal("34.56"), parse(residential.replace("建坪 34.56 坪", "地坪 80.00 坪 建坪 34.56 坪")).listings.single().areaPing)
    }

    @Test fun nonResidentialKnownPriceAgeAndLocationAreExcluded() {
        for (keyword in listOf("辦公室", "店面", "廠房", "土地", "農地"))
            assertEquals("non_residential", parse(residential.replace("中和三房美廈", keyword)).excluded["A123456789"])
        assertEquals("over_price", parse(q = query.copy(maxTotalPrice = BigDecimal("759.99"))).excluded["A123456789"])
        assertEquals("over_age", parse(q = query.copy(maxAgeYears = 24)).excluded["A123456789"])
        for (location in listOf("新北市板橋區", "新北市 板橋區", "新北市&nbsp;板橋區", "新北市　板橋區"))
            assertEquals("wrong_district", parse(residential.replace("新北市中和區", location)).excluded["A123456789"])
        assertEquals("wrong_city", parse(residential.replace("新北市中和區", "桃園市中壢區")).excluded["A123456789"])
        assertEquals("wrong_district", parse(discounted).excluded["TF02157997"])
    }

    @Test fun fullyFilteredPageContinuesToValidSecondPage() {
        val q = query.copy(maxTotalPrice = BigDecimal("800"))
        val first = parse(residential.replace("760萬", "900萬"), q)
        assertEquals(PageKind.RESULTS, first.kind)
        assertEquals(listOf("A123456789"), first.rawKeys)
        assertTrue(first.listings.isEmpty())
        assertEquals(NextPage.UNKNOWN, first.next)
        assertEquals(PageDecision.CONTINUE, nextDecision(first, emptySet(), 0, 30))
        val second = parse(residential.replace("A123456789", "B987654321"), q, 1)
        assertEquals(1, second.listings.size)
        assertEquals(PageDecision.CONTINUE, nextDecision(second, first.rawKeys.toSet(), 1, 30))
        assertEquals(PageDecision.TRUNCATE, nextDecision(second, emptySet(), 29, 30))
    }

    @Test fun emptyMarkerProvesEndAndIgnoresFeaturedCards() {
        val page = parse(empty + residential)
        assertEquals(PageKind.EMPTY, page.kind)
        assertTrue(page.rawKeys.isEmpty())
        assertTrue(page.listings.isEmpty())
        assertEquals(NextPage.END, page.next)
        assertEquals("empty_marker", page.evidence)
        assertEquals(PageDecision.COMPLETE, nextDecision(page, emptySet(), 0, 30))
    }

    @Test fun unscopedAndHiddenMarkersCannotProveEmpty() {
        for (html in listOf("", "<p>共0筆</p>", "<script>const msg='暫無查詢物件'</script>",
            "<template><p>暫無查詢物件</p></template>", "<p hidden>暫無查詢物件</p>"))
            assertEquals(PageKind.INVALID, parse(html).kind)
        assertEquals(PageKind.RESULTS, parse(residential.replace("中和三房美廈", "暫無查詢物件")).kind)
        for (style in listOf("display:none", "DISPLAY: none !important;", "visibility:hidden", "visibility:collapse"))
            assertEquals(PageKind.RESULTS, parse("<div style='$style'><p>暫無查詢物件</p></div>$residential").kind)
    }

    @Test fun challengeAndHttpErrorsCannotBecomeEmpty() {
        val page = parse("<form id='challenge-form'>請完成驗證</form>$empty")
        assertEquals(PageKind.CHALLENGE, page.kind)
        assertEquals(PageDecision.WAIT_FOR_USER, nextDecision(page, emptySet(), 0, 30))
        assertEquals(PageKind.INVALID, TwHouseSource.parse(raw(empty).copy(statusCode = 500), query, 0).kind)
    }

    @Test fun navigationLinksAreNotCardsButMalformedCardsFail() {
        val nav = "<nav><a href='/buy/'>買屋</a><a href='/buy/list/newTaipei-city'>搜尋</a></nav>"
        assertEquals(listOf("A123456789"), parse(nav + residential).rawKeys)
        assertEquals(PageKind.INVALID, parse(nav).kind)
        for (href in listOf("/buy/invalid", "https://evil.invalid/buy/A123456789", "/buy/A123456789/extra", ""))
            assertEquals(href, PageKind.INVALID, parse(residential.replace("/buy/A123456789", href)).kind)
    }

    @Test fun malformedCurrentPricesCannotFallBackOrParseATrailingNumber() {
        for (price in listOf("0萬", "-760萬", "−760萬", "7,60萬", "1, 760萬", "7.6e2萬", "７60萬", "面議")) {
            assertEquals(price, PageKind.INVALID, parse(residential.replace("760萬", price)).kind)
            assertEquals(price, PageKind.INVALID, parse(residential + residential.replace("760萬", price)).kind)
        }
        assertEquals(PageKind.INVALID, parse(discounted.replace("1,480萬", "−1,480萬"), query.copy(district = "永和區")).kind)
        assertEquals(PageKind.INVALID, parse(residential.replace("中和三房美廈", "辦公室").replace("760萬", "面議")).kind)
    }

    @Test fun duplicatesKeepRawKeysAndCannotProveEnd() {
        val page = parse(residential + residential)
        assertEquals(listOf("A123456789", "A123456789"), page.rawKeys)
        assertEquals(1, page.listings.size)
        assertEquals(PageDecision.INCONCLUSIVE, nextDecision(page, page.rawKeys.toSet(), 1, 30))
        assertEquals(PageKind.INVALID, parse(residential + residential.replace("760萬", "761萬")).kind)
    }

    @Test fun missingFieldsAndParkingFiltersRemainUnknown() {
        val html = residential.replace("新北市中和區中山路二段", "").replace("25.3年", "未知")
            .replace("img.twhg.com.tw", "evil.invalid")
        val item = parse(html, query.copy(parkingCodes = "PF", rooms = "2,3")).listings.single()
        assertEquals("新北市", item.city)
        assertEquals("中和區", item.district)
        assertNull(item.ageYears)
        assertNull(item.imageUrl)
        assertTrue(item.unknownFields.containsAll(setOf("city", "district", "ageYears", "imageUrl", "rooms", "parkingCodes", "hasParking")))
    }

    @Test fun staleOrUnsafeDocumentUrlsAreRejected() {
        val capture = raw()
        for (url in listOf(capture.documentUrl.replace("page=1", "page=2"), capture.documentUrl + "&page=1",
            capture.documentUrl.replace("100000down", "1down"), capture.documentUrl.replace("https:", "http:"),
            capture.documentUrl.replace("www.twhg.com.tw", "www.twhg.com.tw.evil.invalid")))
            assertEquals(PageKind.INVALID, TwHouseSource.parse(capture.copy(documentUrl = url), query, 0).kind)
    }

    @Test fun resultAndEmptyPagesRoundTripThroughStoreAndExport() {
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

    @Test fun addressContainingWanIsNotAPriceField() {
        val html = residential.replace("新北市中和區中山路二段", "新北市新店區萬壽路")
        assertEquals(BigDecimal("760"), parse(html, query.copy(district = "新店區")).listings.single().totalPrice)
    }

    @Test fun missingCurrentPriceNeverFallsBackToOldPrice() {
        for (current in listOf("價格待更新", "", "面議"))
            assertEquals(PageKind.INVALID, parse(discounted.replace("1,480萬", current), query.copy(district = "永和區")).kind)
        assertEquals(BigDecimal("1480"), parse(discounted.replace("1,480萬", "<b>1,480</b>萬"), query.copy(district = "永和區")).listings.single().totalPrice)
    }

    @Test fun duplicateResidentialConflictCannotDependOnOrder() {
        val office = residential.replace("3房2廳2衛", "辦公室")
        for (html in listOf(residential + office, office + residential))
            assertEquals(PageKind.INVALID, parse(html).kind)
    }

    @Test fun headingsInUnrelatedNavigationAreNotSearchCards() {
        val nav = "<nav><a href='/news'><h2>最新消息</h2></a><a href='/buy/list'><h3>搜尋</h3></a></nav>"
        assertEquals(listOf("A123456789"), parse(nav + residential).rawKeys)
    }

    @Test fun missingDistrictDoesNotOverwriteKnownCity() {
        assertEquals("wrong_city", parse(residential.replace("新北市中和區中山路二段", "桃園市")).excluded["A123456789"])
        val item = parse(residential.replace("新北市中和區中山路二段", "新北市")).listings.single()
        assertFalse("city" in item.unknownFields)
        assertTrue("district" in item.unknownFields)
    }

    @Test fun wholeCurrentPriceFieldMustBeValidAcrossNestedTags() {
        for (value in listOf("−<b>1,480萬</b>", "-<b>1,480萬</b>", "<b>1,480萬</b>價格待更新", "<b>1,480</b>元"))
            assertEquals(value, PageKind.INVALID, parse(discounted.replace("1,480萬", value), query.copy(district = "永和區")).kind)
        assertEquals(PageKind.INVALID, parse(residential.replace("760萬", "−<b>760萬</b>")).kind)
    }
}
