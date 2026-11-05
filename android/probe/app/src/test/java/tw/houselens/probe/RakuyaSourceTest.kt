package tw.houselens.probe

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tw.houselens.probe.sources.RakuyaSource

class RakuyaSourceTest {
    @get:Rule val temp = TemporaryFolder()
    private val query = testQuery(Site.Rakuya, "新店區")
    private val apartment get() = fixtureText("fixtures/rakuya/apartment.html")
    private val house get() = fixtureText("fixtures/rakuya/house.html")
    private fun raw(html: String = apartment, q: ProbeQuery = query, index: Int = 0) =
        RawCapture(RakuyaSource.request(q, index).url, 200, html, emptyList(), 1, false)
    private fun parse(html: String = apartment, q: ProbeQuery = query, index: Int = 0) =
        RakuyaSource.parse(raw(html, q, index), q, index)

    @Test fun requestMapsFiltersAndOneBasedPages() {
        val q = query.copy(district = "中和區", maxTotalPrice = BigDecimal("1000"),
            minSizePing = BigDecimal("20"), rooms = "2,3,4,5~", maxAgeYears = 30, parkingCodes = "PF,PM")
        val first = RakuyaSource.request(q, 0)
        assertEquals("https://www.rakuya.com.tw/sell/result?zipcode=235&usecode=1&typecode=R1,R2&price=~1000&size=20~&room=2,3,4,5~&age=~30&other=PF,PM&page=1", first.url)
        assertTrue(first.browser)
        assertFalse(first.captureJson)
        assertEquals("https://www.rakuya.com.tw", first.warmupUrl)
        assertTrue(RakuyaSource.request(q, 1).url.endsWith("&page=2"))
        assertTrue(RakuyaSource.request(q, 29).url.endsWith("&page=30"))
        assertTrue(RakuyaSource.request(query.copy(district = "永和區"), 0).url.contains("zipcode=234&"))
        assertTrue(RakuyaSource.request(query, 0).url.contains("zipcode=231&"))
        val defaults = RakuyaSource.request(query.copy(typeCodes = "", useCode = "", maxTotalPrice = BigDecimal.ZERO), 0)
        assertEquals("https://www.rakuya.com.tw/sell/result?zipcode=231&usecode=1&typecode=R1,R2&page=1", defaults.url)
        val precise = RakuyaSource.request(query.copy(maxTotalPrice = BigDecimal("999.50"), minSizePing = BigDecimal("26.985")), 0)
        assertTrue(precise.url.contains("price=~999.5&size=26.985~"))
    }

    @Test fun unsupportedQueriesFailBeforeNavigation() {
        for (q in listOf(query.copy(site = Site.F591), query.copy(typeCodes = "R9"),
            query.copy(useCode = "2"), query.copy(parkingCodes = "XX"), query.copy(rooms = "abc"))) {
            assertThrows(IllegalArgumentException::class.java) { RakuyaSource.request(q, 0) }
        }
        for (i in listOf(-1, 30)) assertThrows(IllegalArgumentException::class.java) { RakuyaSource.request(query, i) }
        assertTrue(RakuyaSource.request(query.copy(typeCodes = "R1,R2,R3,R4,R5,R6"), 0).url.contains("R1,R2,R3,R4,R5,R6"))
    }

    @Test fun apartmentPreservesFieldsAndMainArea() {
        val page = parse()
        val item = page.listings.single()
        assertEquals(listOf("0ca48612345678a"), page.rawKeys)
        assertEquals(Site.Rakuya, item.sourceSite)
        assertEquals("新北市", item.city)
        assertEquals("新店區", item.district)
        assertEquals("美河市", item.address)
        assertEquals("美麗華電梯大廈三房含車位", item.title)
        assertEquals(BigDecimal("2088"), item.totalPrice)
        assertEquals(BigDecimal("26.98"), item.areaPing)
        assertEquals(BigDecimal("77.39"), item.unitPrice)
        assertEquals(13, item.ageYears)
        assertEquals("10/16樓", item.floor)
        assertTrue(item.hasParking)
        assertEquals("https://www.rakuya.com.tw/sell_item/info?ehid=0ca48612345678a", item.url)
        assertEquals("https://static.rakuya.com.tw/r1/n354/99/8b/12345678_1_c.jpeg", item.imageUrl)
        assertEquals(NextPage.UNKNOWN, page.next)
        assertFalse(raw().networkObserved)
    }

    @Test fun houseUsesTotalAreaAndHalfEvenAgeWithoutInventingParkingAbsence() {
        val item = parse(house).listings.single()
        assertEquals("050b83313052929", item.sourceListingKey)
        assertEquals(BigDecimal("1580"), item.totalPrice)
        assertEquals(BigDecimal("45"), item.areaPing)
        assertEquals(BigDecimal("35.11"), item.unitPrice)
        assertEquals(44, item.ageYears)
        assertEquals("1~2/2樓", item.floor)
        assertFalse(item.hasParking)
        assertTrue("hasParking" in item.unknownFields)
        assertEquals(46, parse(house.replace("44.5年", "45.5年")).listings.single().ageYears)
    }

    @Test fun duplicateCardsProduceOneRawKeyAndOneListing() {
        val page = parse(apartment + apartment)
        assertEquals(1, page.rawKeys.size)
        assertEquals(1, page.listings.size)
        assertEquals(PageDecision.INCONCLUSIVE, nextDecision(page, page.rawKeys.toSet(), 1, 30))
    }

    @Test fun fullyExcludedFirstPageDoesNotHideValidSecondPage() {
        val q = query.copy(maxTotalPrice = BigDecimal("900"))
        val first = parse(fixtureText("fixtures/rakuya/filtered-page.html"), q)
        assertEquals(PageKind.RESULTS, first.kind)
        assertEquals(2, first.rawKeys.size)
        assertTrue(first.listings.isEmpty())
        assertEquals(setOf("non_residential", "over_price"), first.excluded.values.toSet())
        assertEquals(PageDecision.CONTINUE, nextDecision(first, emptySet(), 0, 30))
        val second = parse(apartment.replace("2,088", "800"), q, 1)
        assertEquals("0ca48612345678a", second.listings.single().sourceListingKey)
        assertEquals(PageDecision.CONTINUE, nextDecision(second, first.rawKeys.toSet(), 1, 30))
        assertEquals(PageDecision.TRUNCATE, nextDecision(second, emptySet(), 29, 30))
    }

    @Test fun noCardsOrUnscopedTotalsCannotProveEmptyOrLastPage() {
        for (html in listOf("", "<div class='result-list'></div>", "<p>共 0 筆</p>", "<p>暫無符合物件</p>")) {
            assertEquals(PageKind.INVALID, parse(html).kind)
        }
        assertEquals(NextPage.UNKNOWN, parse("<p>共 1 筆</p>$apartment").next)
        assertEquals(NextPage.UNKNOWN, parse("<p>共 0 筆</p>$apartment").next)
    }

    @Test fun requiredCardFailuresInvalidateWholePageEvenWhenCardWouldBeFiltered() {
        for (html in listOf(apartment.replace("data-ehid=\"0ca48612345678a\"", ""),
            apartment.replace("0ca48612345678a", "bad/id"), apartment.replace("2,088", "面議"),
            apartment.replace("2,088", "2,08"), apartment.replace("2,088", "0"),
            apartment + house.replace("1,580", "錯誤"),
            apartment.replace("電梯大廈</li>", "商辦</li>").replace("2,088", "面議"))) {
            assertEquals(PageKind.INVALID, parse(html).kind)
        }
    }

    @Test fun knownLocationPriceAndAgeAreFilteredButMissingParkingStaysUnknown() {
        assertEquals("wrong_district", parse(apartment.replace("新店區", "板橋區")).excluded.values.single())
        assertEquals("wrong_city", parse(apartment.replace(">新店區<", ">桃園市新店區<")).excluded.values.single())
        assertEquals("over_price", parse(q = query.copy(maxTotalPrice = BigDecimal("2000"))).excluded.values.single())
        assertEquals("over_age", parse(q = query.copy(maxAgeYears = 12)).excluded.values.single())
        assertEquals(1, parse(house, query.copy(parkingCodes = "PF")).listings.size)
        val missing = parse(apartment.replace("13年", "未知").replace("平面車位", "近公園"),
            query.copy(maxAgeYears = 1, parkingCodes = "PF")).listings.single()
        assertNull(missing.ageYears)
        assertTrue(missing.unknownFields.containsAll(setOf("ageYears", "hasParking", "parkingCodes")))
    }

    @Test fun unknownFieldsAndMixedAreaBasisRemainUnverified() {
        val q = query.copy(rooms = "2", minSizePing = BigDecimal("40"), parkingCodes = "PM")
        val item = parse(q = q).listings.single()
        assertTrue(item.unknownFields.containsAll(setOf("typeCodes", "useCode", "rooms", "parkingCodes", "areaBasis", "postedDate")))
        val missing = parse(apartment.replace("static.rakuya.com.tw", "evil.invalid")
            .replace("主建26.98坪", "主建未知坪").replace("13年", "未知")).listings.single()
        assertEquals(BigDecimal("50.8"), missing.areaPing)
        assertNull(missing.imageUrl)
        assertTrue("imageUrl" in missing.unknownFields)
    }

    @Test fun stalePageOtherQueryAndUnsafeDocumentsAreRejected() {
        val capture = raw()
        for (url in listOf(capture.documentUrl.replace("page=1", "page=2"),
            capture.documentUrl.replace("zipcode=231", "zipcode=235"),
            capture.documentUrl.replace("price=~100000", "price=~1"),
            capture.documentUrl.replace("/sell/result", "/unrelated"),
            capture.documentUrl.replace("www.rakuya.com.tw", "www.rakuya.com.tw.evil.invalid"),
            capture.documentUrl.replace("https:", "http:"), capture.documentUrl + "&page=1")) {
            assertEquals(url, PageKind.INVALID, RakuyaSource.parse(capture.copy(documentUrl = url), query, 0).kind)
        }
    }

    @Test fun challengeAndHttpErrorsNeverBecomeSearchSuccess() {
        val capture = raw("<form id='challenge-form'>請完成驗證</form>$apartment").copy(statusCode = 403)
        assertEquals(PageKind.CHALLENGE, RakuyaSource.parse(capture, query, 0).kind)
        assertEquals(PageKind.INVALID, RakuyaSource.parse(raw().copy(statusCode = 500), query, 0).kind)
    }

    @Test fun cityDistrictWhitespaceCannotTurnAnExplicitOtherDistrictIntoTheQueryDistrict() {
        for (location in listOf("新北市 板橋區", "新北市　板橋區", "新北市&nbsp;板橋區")) {
            val page = parse(apartment.replace(">新店區<", ">$location<"))
            assertTrue(location, page.listings.isEmpty())
            assertEquals(location, "wrong_district", page.excluded["0ca48612345678a"])
        }
    }

    @Test fun sourcePagesCanBeStoredAndExportedAsSimulatedEvidence() {
        val runId = "11111111-1111-4111-8111-111111111111"
        val process = "22222222-2222-4222-8222-222222222222"
        val store = ProbeStore(temp.root, process, utcNow = { "2026-11-05T00:00:00Z" }, elapsedNow = { 0L })
        store.create(RunRecord(runId, process, query, RunMode.FOREGROUND, RunState.RUNNING,
            DeviceSnapshot(null,null,null,null,null,null,null,null,null,null,null,null), EvidenceSource.SIMULATED))
        val page = parse()
        store.savePage(runId, 0, page)
        assertEquals(page, store.loadPage(runId, 0))
        store.export(runId, java.io.ByteArrayOutputStream())
    }

    @Test fun missingBuildingTypeDoesNotTurnRoomsAgeOrFloorIntoNonResidentialEvidence() {
        var html = apartment.replace("<li>電梯大廈</li>", "")
        for (remove in listOf("", "<li>3房2廳2衛</li>", "<li>13年</li>")) {
            html = html.replace(remove, "")
            val page = parse(html)
            assertEquals(1, page.listings.size)
            assertTrue("typeCodes" in page.listings.single().unknownFields)
            assertTrue(page.excluded.isEmpty())
        }
    }
}
