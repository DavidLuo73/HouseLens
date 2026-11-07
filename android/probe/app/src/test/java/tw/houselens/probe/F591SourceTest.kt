package tw.houselens.probe

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tw.houselens.probe.sources.F591Source

class F591SourceTest {
    @get:Rule val temp = TemporaryFolder()
    private val query = testQuery(Site.F591)
    private val api = "https://sale.591.com.tw/test/list?regionid=3&section=38&firstRow=0"
    private fun raw(body: String = fixtureText("fixtures/591/search-page1.json"), url: String = api,
        index: Int = 0, q: ProbeQuery = query) =
        RawCapture(F591Source.request(q, index).url, 200, null, listOf(CapturedJson(url, body)), 1, false)
    private fun parse(body: String) = F591Source.parse(raw(body), query, 0)

    @Test fun invalidPagesIdentifyTheRejectedStageWithoutReturningRawContent() {
        val html = captureFixture(F591Source, query, "fixtures/591/fallback.html")
        val cases = listOf(
            raw().copy(documentUrl = "https://other.example/?secret=private") to "F591_DOCUMENT_URL",
            raw().copy(documentUrl = raw().documentUrl.replace("/?", "/mobile?")) to "F591_DOCUMENT_PATH",
            raw().copy(documentUrl = raw().documentUrl.replace("section=38", "section=37")) to "F591_DOCUMENT_QUERY",
            raw().copy(documentUrl = "https://sale.591.com.tw/") to "F591_DOCUMENT_QUERY",
            raw().copy(statusCode = 500) to "F591_HTTP_STATUS",
            raw(url = api.replace("Row=0", "Row=30")) to "F591_JSON_UNCORRELATED",
            raw().copy(json = raw().json + CapturedJson(api, "{\"total\":0}")) to "F591_JSON_CONFLICT",
            raw().copy(json = emptyList()) to "F591_NO_CONTENT",
            raw("{private-invalid") to "F591_JSON_FORMAT",
            raw("[]") to "F591_JSON_FORMAT",
            raw("{\"total\":-1}") to "F591_JSON_TOTAL",
            raw("{}") to "F591_JSON_LIST",
            raw("{\"total\":1,\"house_list\":[]}") to "F591_JSON_COUNT",
            raw("""{"house_list":[{"price":750}]}""") to "F591_JSON_CARD",
            raw("""{"house_list":[{"id":1,"price":750},{"id":1,"price":750}]}""") to "F591_JSON_DUPLICATE",
            html.copy(html = "<main>private mobile page</main>") to "F591_HTML_NO_CARDS",
            html.copy(html = html.html!!.replace("798.00", "private price")) to "F591_HTML_CARD",
        )
        for ((capture, expected) in cases) {
            val result = F591Source.parse(capture, query, 0)
            assertEquals(expected, PageKind.INVALID, result.kind)
            assertEquals(expected, result.evidence)
            assertTrue(result.rawKeys.isEmpty() && result.listings.isEmpty())
        }
    }

    @Test fun requestPreservesSearchFiltersAndUsesThirtyRowOffsets() {
        val q = query.copy(maxTotalPrice = BigDecimal("1000"), maxAgeYears = 30,
            minSizePing = BigDecimal("20"), rooms = "2,3,4,5~", parkingCodes = "PF,PM")
        val first = F591Source.request(q, 0)
        assertEquals("https://sale.591.com.tw/?shType=list&type=2&regionid=3&section=38&price=\$_\$1000&houseage=\$_\$30&parking=1,2,3&area=\$20_\$&pattern=2,3,4,5", first.url)
        assertTrue(first.browser && first.captureJson)
        assertEquals("https://sale.591.com.tw/", first.warmupUrl)
        assertTrue(F591Source.request(q, 1).url.endsWith("&firstRow=30"))
        assertTrue(F591Source.request(q, 2).url.endsWith("&firstRow=60"))
        assertTrue(F591Source.request(query.copy(district = "永和區"), 0).url.contains("section=37&"))
        assertTrue(F591Source.request(query.copy(district = "新店區"), 0).url.contains("section=34&"))
        assertFalse(F591Source.request(query.copy(maxTotalPrice = BigDecimal.ZERO), 0).url.contains("price="))
    }

    @Test fun unsupportedQueriesFailBeforeNavigation() {
        for (q in listOf(query.copy(site = Site.Rakuya), query.copy(parkingCodes = "XX"),
            query.copy(rooms = "abc"), query.copy(typeCodes = "R9"), query.copy(useCode = "9"))) {
            assertThrows(IllegalArgumentException::class.java) { F591Source.request(q, 0) }
        }
        for (index in listOf(-1, 40)) assertThrows(IllegalArgumentException::class.java) { F591Source.request(query, index) }
    }

    @Test fun parsesExactAmountsAndDifferentPagesWithoutInventingUnknownValues() {
        val first = F591Source.parse(raw(), query, 0)
        assertEquals(30, first.rawKeys.size)
        assertEquals("100001", first.rawKeys.first())
        val item = first.listings.first()
        assertEquals(BigDecimal("750.00"), item.totalPrice)
        assertEquals(BigDecimal("28.50"), item.areaPing)
        assertEquals(BigDecimal("26.32"), item.unitPrice)
        assertEquals(12, item.ageYears)
        assertTrue(item.hasParking)
        assertEquals("https://img.591.com.tw/synthetic.jpg", item.imageUrl)
        assertEquals(NextPage.MORE, first.next)
        val second = F591Source.parse(raw(fixtureText("fixtures/591/page2.json"), api.replace("Row=0", "Row=30"), 1), query, 1)
        assertEquals(listOf("100002"), second.rawKeys)
        assertEquals(BigDecimal("800"), second.listings.single().totalPrice)
        assertEquals(BigDecimal("30.00"), second.listings.single().areaPing)
        assertNull(second.listings.single().ageYears)
        assertTrue("hasParking" in second.listings.single().unknownFields)
        assertEquals(NextPage.END, second.next)
        assertFalse(raw().networkObserved)
    }

    @Test fun zeroTotalRejectsRecommendations() {
        val capture = captureFixture(F591Source, query, "fixtures/591/zero-with-recommendations.json", api)
        val result = F591Source.parse(capture, query, 0)
        assertEquals(PageKind.EMPTY, result.kind)
        assertEquals(NextPage.END, result.next)
        assertTrue(result.rawKeys.isEmpty() && result.listings.isEmpty())
        assertEquals("zero_total", result.evidence)
    }

    @Test fun rejectsStaleWrongDistrictUncorrelatedAndUntrustedResponses() {
        for (url in listOf(api.replace("section=38", "section=380"), api.replace("regionid=3", "regionid=30"),
            api.replace("Row=0", "Row=30"), api.substringBefore("&firstRow"),
            api.replace("sale.591.com.tw", "sale.591.com.tw.evil.invalid"), api.replace("https:", "http:"),
            "$api&section=38", api.replace("test/list", "recommendations"), "$api&bad=%ZZ")) {
            assertEquals(url, PageKind.INVALID, F591Source.parse(raw(url = url), query, 0).kind)
        }
        assertEquals(PageKind.INVALID, F591Source.parse(raw().copy(documentUrl = F591Source.request(query, 1).url), query, 0).kind)
    }

    @Test fun unrelatedBodiesCannotSupplyTotalsOrListings() {
        val stale = CapturedJson(api.replace("Row=0", "Row=30"), fixtureText("fixtures/591/zero-with-recommendations.json"))
        val result = F591Source.parse(raw().copy(json = listOf(stale) + raw().json), query, 0)
        assertEquals(30, result.rawKeys.size)
        assertEquals("100001", result.rawKeys.first())
        assertEquals(NextPage.MORE, result.next)
        val conflicting = CapturedJson(api, fixtureText("fixtures/591/zero-with-recommendations.json"))
        assertEquals(PageKind.INVALID, F591Source.parse(raw().copy(json = raw().json + conflicting), query, 0).kind)
    }

    @Test fun thirdPageResponseMustHaveOffsetSixty() {
        val body = """{"total":61,"house_list":[{"id":100004,"price":900,"section_name":"中和區"}]}"""
        val result = F591Source.parse(raw(body, api.replace("Row=0", "Row=60"), 2), query, 2)
        assertEquals(listOf("100004"), result.rawKeys)
        assertEquals(NextPage.END, result.next)
        assertEquals(PageKind.INVALID, F591Source.parse(raw(body, api.replace("Row=0", "Row=30"), 2), query, 2).kind)
    }

    @Test fun fullyExcludedPageStillContinuesFromRawEvidence() {
        val q = query.copy(maxTotalPrice = BigDecimal.ONE)
        val result = F591Source.parse(raw(q = q), q, 0)
        assertEquals(30, result.rawKeys.size)
        assertTrue(result.listings.isEmpty())
        assertEquals(30, result.excluded.size)
        assertTrue(result.excluded.values.all { it == "over_price" })
        assertEquals(PageDecision.CONTINUE, nextDecision(result, emptySet(), 0, 40))
    }

    @Test fun malformedSearchCardsNeverBecomeSuccessfulEmptyOrPartialPages() {
        for (body in listOf("{broken", "{}", """{"data":{"total":1}}""",
            """{"data":{"total":1,"house_list":[]}}""",
            """{"data":{"total":1,"house_list":[{"price":750}]}}""",
            """{"data":{"total":1,"house_list":[{"id":1,"price":"面議"}]}}""",
            """{"data":{"total":-1,"house_list":[]}}""",
            fixtureText("fixtures/591/search-page1.json").replace("750.00", "0"))) {
            assertEquals(body, PageKind.INVALID, parse(body).kind)
        }
    }

    @Test fun supportsExistingListPathsButMissingTotalCannotProveEnd() {
        val card = """[{"id":"123","price":"1,000.00","section_name":"中和區"}]"""
        for (body in listOf("{\"data\":{\"items\":$card}}",
            "{\"data\":{\"rows\":$card}}", "{\"rows\":$card}", "{\"items\":$card}")) {
            val result = parse(body)
            assertEquals(BigDecimal("1000.00"), result.listings.single().totalPrice)
            assertEquals(NextPage.UNKNOWN, result.next)
        }
    }

    @Test fun knownWrongLocationAndParkingAreExcludedWhileMissingParkingStaysUnknown() {
        val body = fixtureText("fixtures/591/search-page1.json")
        assertEquals("wrong_district", parse(body.replace("中和區", "板橋區")).excluded["100001"])
        val q = query.copy(parkingCodes = "PF")
        val result = F591Source.parse(raw(body.replace("\"parking\":1", "\"parking\":0"), q = q), q, 0)
        assertEquals("no_parking", result.excluded["100001"])
    }

    @Test fun htmlFallbackIsDistinctAndCannotProveCompleteBffCapture() {
        val capture = captureFixture(F591Source, query, "fixtures/591/fallback.html")
        val result = F591Source.parse(capture, query, 0)
        assertEquals(listOf("100003"), result.rawKeys)
        assertEquals(BigDecimal("798.00"), result.listings.single().totalPrice)
        assertEquals(NextPage.UNKNOWN, result.next)
        assertEquals("html_fallback", result.evidence)
        assertTrue("hasParking" in result.listings.single().unknownFields)
        assertEquals(PageKind.INVALID, F591Source.parse(capture.copy(html = "<html>未知頁</html>"), query, 0).kind)
        assertEquals(PageKind.INVALID, F591Source.parse(capture.copy(html = capture.html!!.replace("798.00", "面議")), query, 0).kind)
    }

    @Test fun challengeAndHttpErrorsDoNotReturnSearchSuccess() {
        val capture = raw().copy(html = "<html><h1>請完成驗證</h1><iframe src='https://challenges.cloudflare.com/'></iframe></html>")
        assertEquals(PageKind.CHALLENGE, F591Source.parse(capture, query, 0).kind)
        assertEquals(PageKind.INVALID, F591Source.parse(raw().copy(statusCode = 500), query, 0).kind)
    }

    @Test fun sourceEvidenceAndUnknownFieldsCanBeSavedAndExported() {
        val runId = "11111111-1111-4111-8111-111111111111"
        val process = "22222222-2222-4222-8222-222222222222"
        val store = ProbeStore(temp.root, process, utcNow = { "2026-11-04T00:00:00Z" }, elapsedNow = { 0L })
        store.create(RunRecord(runId, process, query, RunMode.FOREGROUND, RunState.RUNNING,
            DeviceSnapshot(null,null,null,null,null,null,null,null,null,null,null,null), EvidenceSource.SIMULATED))
        val page = F591Source.parse(captureFixture(F591Source, query, "fixtures/591/fallback.html"), query, 0)
        store.savePage(runId, 0, page)
        assertEquals(page, store.loadPage(runId, 0))
        store.export(runId, java.io.ByteArrayOutputStream())
    }

    @Test fun booleanIdsAndMalformedTotalsCannotMasqueradeAsSearchData() {
        val body = fixtureText("fixtures/591/search-page1.json")
        for (bad in listOf(body.replace("\"id\":100001", "\"id\":true"),
            body.replace("\"total\":31", "\"total\":\"3,1\""),
            body.replace("\"total\":31", "\"total\":31.5"))) {
            assertEquals(PageKind.INVALID, parse(bad).kind)
        }
    }

    @Test fun matchingQueryOnNonSearchDocumentIsNotCurrentSearchEvidence() {
        val capture = raw().copy(documentUrl = raw().documentUrl.replace("/?", "/unrelated?"))
        assertEquals(PageKind.INVALID, F591Source.parse(capture, query, 0).kind)
    }

    @Test fun unknownConstraintsAndUnsafeImagesRemainExplicitlyUnverified() {
        val q = query.copy(minSizePing = BigDecimal("100"), rooms = "2")
        val body = fixtureText("fixtures/591/search-page1.json").replace("img.591.com.tw", "evil.invalid")
        val item = F591Source.parse(raw(body, q = q), q, 0).listings.first()
        assertTrue(item.unknownFields.containsAll(setOf("areaBasis", "typeCodes", "useCode", "rooms", "imageUrl")))
        assertNull(item.imageUrl)
        assertEquals("unsupported_type", parse(body.replace("\"id\":100001", "\"is_newhouse\":true,\"id\":100001")).excluded["100001"])
    }

    @Test fun shortOrOversizedPagesCannotProveCompleteSearch() {
        val card = """{"id":123,"price":750,"section_name":"中和區"}"""
        assertEquals(PageKind.INVALID, parse("""{"total":20,"house_list":[$card]}""").kind)
        assertEquals(PageKind.INVALID, parse("""{"total":31,"house_list":[$card]}""").kind)
        assertEquals(PageKind.INVALID, parse("""{"total":1,"house_list":[$card,$card]}""").kind)
        assertEquals(NextPage.END, parse("""{"total":1,"house_list":[$card]}""").next)
    }

    @Test fun responsesForDifferentExplicitFiltersCannotEndThisSearch() {
        val body = fixtureText("fixtures/591/zero-with-recommendations.json")
        for (suffix in listOf("&price=\$_\$1", "&parking=1", "&pattern=2", "&area=\$20_\$", "&houseage=\$_\$10", "&type=1")) {
            assertEquals(PageKind.INVALID, F591Source.parse(raw(body, api + suffix), query, 0).kind)
        }
    }

    @Test fun parkingPresenceDoesNotProveRequestedParkingType() {
        val q = query.copy(parkingCodes = "PF")
        val item = F591Source.parse(raw(q = q), q, 0).listings.first()
        assertTrue(item.hasParking)
        assertTrue("parkingCodes" in item.unknownFields)
    }
}
