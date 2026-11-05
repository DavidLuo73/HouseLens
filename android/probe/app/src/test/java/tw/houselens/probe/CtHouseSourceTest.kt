package tw.houselens.probe

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tw.houselens.probe.sources.CtHouseSource

@OptIn(ExperimentalCoroutinesApi::class)
class CtHouseSourceTest {
    @get:Rule val temp = TemporaryFolder()
    private val query = testQuery(Site.CtHouse)
    private val endpoint = "https://buy.cthouse.com.tw/api/house_list.ashx"
    private val sample = fixtureText("fixtures/cthouse/results.json")
    private val item = Json.parseToJsonElement(sample).jsonObject["houses"]!!.jsonArray.single().jsonObject
    private fun envelope(items: List<JsonElement> = listOf(item), pages: JsonElement = JsonPrimitive(1)) =
        buildJsonObject { put("RS", "OK"); put("totalpage", pages); put("houses", JsonArray(items)) }.toString()
    private fun changed(vararg values: Pair<String, JsonElement?>) =
        JsonObject(item.toMutableMap().apply { for ((key, value) in values) if (value == null) remove(key) else put(key, value) })
    private fun raw(body: String = sample) = RawCapture(endpoint, 200, null, listOf(CapturedJson(endpoint, body)), 1, false)
    private fun parse(body: String = sample, q: ProbeQuery = query, page: Int = 0) = CtHouseSource.parse(raw(body), q, page)

    @Test fun postBodyPreservesConditionsAndOneBasedPage() {
        val q = query.copy(maxTotalPrice = BigDecimal("1000.25"), minSizePing = BigDecimal("20.50"),
            rooms = "3,2,5~", maxAgeYears = 30, parkingCodes = "PF,PM")
        val request = CtHouseSource.request(q, 1)
        assertEquals(endpoint, request.url)
        assertEquals("POST", request.method)
        assertFalse(request.browser)
        assertNull(request.warmupUrl)
        assertTrue(request.captureJson)
        val body = Json.parseToJsonElement(request.body!!).jsonObject
        assertEquals(JsonPrimitive(2), body["page"])
        assertEquals("新北市-city/中和區-town/0-1000.25-price/20.5-up-area/電梯大樓-公寓-type/0-30-year/2-up-room/1-parking/", body["arg"]!!.jsonPrimitive.content)
        val minimal = CtHouseSource.request(query.copy(district = "永和區", maxTotalPrice = BigDecimal.ZERO, typeCodes = ""), 0)
        assertEquals("新北市-city/永和區-town/電梯大樓-公寓-type/", Json.parseToJsonElement(minimal.body!!).jsonObject["arg"]!!.jsonPrimitive.content)
        val aliases = CtHouseSource.request(query.copy(district = "新店區", typeCodes = "R5,R3,R2,R1"), 0)
        assertTrue(aliases.body!!.contains("電梯大樓-公寓-套房-透天-type/"))
    }

    @Test fun unsupportedCodesAndPageBoundsAreRejected() {
        for (q in listOf(query.copy(typeCodes = "R4"), query.copy(typeCodes = "R1,wrong"), query.copy(useCode = "2"),
            query.copy(parkingCodes = "wrong"), query.copy(rooms = "0"), query.copy(site = Site.Sinyi))) {
            try { CtHouseSource.request(q, 0); fail("accepted $q") } catch (_: IllegalArgumentException) { }
        }
        for (page in listOf(-1, 100)) {
            try { CtHouseSource.request(query, page); fail() } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun residentialFieldsAndUnitsMatchBackendFixture() {
        val page = parse()
        assertEquals(PageKind.RESULTS, page.kind)
        assertEquals(NextPage.END, page.next)
        val listing = page.listings.single()
        assertEquals("2097688", listing.sourceListingKey)
        assertEquals(Site.CtHouse, listing.sourceSite)
        assertEquals(BigDecimal("788"), listing.totalPrice)
        assertEquals(BigDecimal("8.18"), listing.areaPing)
        assertEquals(BigDecimal("96.33"), listing.unitPrice)
        assertEquals(10, listing.ageYears)
        assertEquals("4樓/共9樓", listing.floor)
        assertEquals("新北市中和區華順街", listing.address)
        assertFalse(listing.hasParking)
        assertFalse("hasParking" in listing.unknownFields)
        assertEquals("https://buy.cthouse.com.tw/house/2097688.html", listing.url)
        assertEquals("https://media.cthouse.com.tw/photo/project2/house_photo/202603/2097688-1_new.jpg", listing.imageUrl)
        assertFalse(raw().networkObserved)
    }

    @Test fun discountedPriceNeverFallsBackToOriginalPrice() {
        val discounted = fixtureText("fixtures/cthouse/discounted.json")
        val listing = parse(discounted, query.copy(maxTotalPrice = BigDecimal("800"))).listings.single()
        assertEquals(BigDecimal("798.00"), listing.totalPrice)
        assertTrue(listing.hasParking)
        for (value in listOf("null", "true", "\"bad\"", "0", "-1", "{}"))
            assertEquals(PageKind.INVALID, parse(discounted.replace("\"798.00\"", value)).kind)
        assertEquals(PageKind.INVALID, parse(discounted.replace("\"sell_price\": \"798.00\",", "")).kind)
        assertEquals(BigDecimal("788.125"), parse(envelope(listOf(changed("sell_price" to JsonPrimitive(BigDecimal("788.125")))))).listings.single().totalPrice)
    }

    @Test fun malformedEnvelopesCannotBecomeSuccessfulEmptyResults() {
        for (body in listOf("[]", "null", "{", "{}", fixtureText("fixtures/cthouse/error.json"),
            """{"RS":"OK","totalpage":1}""", """{"RS":"OK","totalpage":1,"houses":{}}""",
            """{"RS":"OK","houses":[]}""", """{"totalpage":1,"houses":[]}"""))
            assertEquals(body, PageKind.INVALID, parse(body).kind)
        for (pages in listOf(JsonNull, JsonPrimitive(-1), JsonPrimitive(1.5), JsonPrimitive(true), JsonPrimitive("1"), JsonPrimitive(2147483648L)))
            assertEquals(pages.toString(), PageKind.INVALID, parse(envelope(pages = pages)).kind)
        assertEquals(PageKind.INVALID, parse(envelope(pages = JsonPrimitive(0))).kind)
        assertEquals(PageKind.INVALID, parse(page = 1).kind)
    }

    @Test fun onlyConsistentFirstPageZeroResultsCanEndAnEmptySearch() {
        for (pages in listOf(0, 1)) {
            val page = parse(envelope(emptyList(), JsonPrimitive(pages)))
            assertEquals(PageKind.EMPTY, page.kind)
            assertEquals(NextPage.END, page.next)
            assertTrue(page.rawKeys.isEmpty())
            assertEquals(PageDecision.COMPLETE, nextDecision(page, emptySet(), 0, 100))
        }
        assertEquals(PageKind.INVALID, parse(envelope(emptyList(), JsonPrimitive(2))).kind)
        assertEquals(PageKind.INVALID, parse(envelope(emptyList(), JsonPrimitive(2)), page = 1).kind)
    }

    @Test fun successStatusMustBeExactTypedOk() {
        for (status in listOf(JsonPrimitive(" OK "), JsonPrimitive("ok"), JsonPrimitive("OK\n"), JsonNull, JsonPrimitive(true))) {
            val body = buildJsonObject { put("RS", status); put("totalpage", 0); put("houses", JsonArray(emptyList())) }
            assertEquals(body.toString(), PageKind.INVALID, parse(body.toString()).kind)
        }
    }

    @Test fun htmlChallengeWaitsForUserButJsonTextCannotSpoofIt() {
        for (html in listOf("<html><form id='challenge-form'>請完成驗證</form></html>",
            "<html><div class='g-recaptcha'></div></html>", "<html><p>verify you are human</p></html>")) {
            val page = parse(html)
            assertEquals(PageKind.CHALLENGE, page.kind)
            assertEquals(PageDecision.WAIT_FOR_USER, nextDecision(page, emptySet(), 0, 100))
        }
        assertEquals(PageKind.INVALID, parse("<html>maintenance</html>").kind)
        val error = buildJsonObject { put("RS", "ERROR"); put("message", "<form id='challenge-form'>請完成驗證</form>") }
        assertEquals(PageKind.INVALID, parse(error.toString()).kind)
        assertEquals(PageKind.RESULTS, parse(envelope(listOf(changed("case_name" to JsonPrimitive("請完成驗證"))))).kind)
    }

    @Test fun allFilteredFirstPageStillContinuesToValidSecondPage() {
        val first = parse(envelope(listOf(changed("house_type_usage" to JsonPrimitive(2))), JsonPrimitive(2)))
        assertTrue(first.listings.isEmpty())
        assertEquals(listOf("2097688"), first.rawKeys)
        assertEquals("non_residential", first.excluded["2097688"])
        assertEquals(NextPage.MORE, first.next)
        assertEquals(PageDecision.CONTINUE, nextDecision(first, emptySet(), 0, 100))
        val second = parse(envelope(listOf(changed("id" to JsonPrimitive(2097689))), JsonPrimitive(2)), page = 1)
        assertEquals("2097689", second.listings.single().sourceListingKey)
        assertEquals(PageDecision.COMPLETE, nextDecision(second, first.rawKeys.toSet(), 1, 100))
        val capped = parse(envelope(pages = JsonPrimitive(101)), page = 99)
        assertEquals(PageDecision.TRUNCATE, nextDecision(capped, emptySet(), 99, 100))
        assertEquals(PageDecision.INCONCLUSIVE, nextDecision(first, first.rawKeys.toSet(), 0, 100))
    }

    @Test fun duplicatesKeepRawKeysButInvalidOrConflictingCardsFailWholePage() {
        val duplicate = parse(envelope(listOf(item, item)))
        assertEquals(listOf("2097688", "2097688"), duplicate.rawKeys)
        assertEquals(1, duplicate.listings.size)
        for (bad in listOf(changed("id" to null), changed("id" to JsonPrimitive(true)), changed("id" to JsonPrimitive("../a")),
            changed("sell_price" to JsonNull), changed("sell_price" to JsonPrimitive("799")), JsonNull))
            assertEquals(PageKind.INVALID, parse(envelope(listOf(item, bad))).kind)
        assertEquals(PageKind.INVALID, parse(envelope(listOf(changed("house_type_usage" to JsonPrimitive(2), "sell_price" to JsonNull)))).kind)
    }

    @Test fun knownFiltersRejectCrossDistrictTypePriceAgeAndParking() {
        for (separator in listOf("", " ", "\u00a0", "　"))
            assertEquals("wrong_district", parse(envelope(listOf(changed("address" to JsonPrimitive("新北市${separator}板橋區文化路"))))).excluded["2097688"])
        assertEquals("wrong_city", parse(envelope(listOf(changed("address" to JsonPrimitive("桃園市中壢區榮民南路"))))).excluded["2097688"])
        assertEquals("unsupported_type", parse(envelope(listOf(changed("house_type_class" to JsonPrimitive(10))))).excluded["2097688"])
        assertEquals("over_price", parse(q = query.copy(maxTotalPrice = BigDecimal("787"))).excluded["2097688"])
        assertEquals("over_age", parse(q = query.copy(maxAgeYears = 9)).excluded["2097688"])
        assertEquals("no_parking", parse(q = query.copy(parkingCodes = "PF")).excluded["2097688"])
        assertEquals(1, parse(q = query.copy(typeCodes = "電梯大樓")).listings.size)
    }

    @Test fun absentAndMalformedOptionalFieldsRemainUnknown() {
        val missing = changed(*listOf("address", "case_name", "house_area", "size_val", "unit_price", "age", "floor_val",
            "parking_lot_belong", "house_type_usage", "house_type_class", "imgs").map { it to null }.toTypedArray())
        val listing = parse(envelope(listOf(missing)), query.copy(rooms = "2", parkingCodes = "PF")).listings.single()
        assertTrue(listing.unknownFields.containsAll(setOf("city", "district", "address", "title", "areaPing", "unitPrice", "ageYears", "floor",
            "hasParking", "typeCodes", "useCode", "imageUrl", "postedDate", "rooms", "parkingCodes", "areaBasis")))
        assertFalse(listing.hasParking)
        assertNull(listing.ageYears)
        for (value in listOf(JsonNull, JsonPrimitive(true), JsonPrimitive(2), JsonPrimitive("bad")))
            assertTrue("hasParking" in parse(envelope(listOf(changed("parking_lot_belong" to value)))).listings.single().unknownFields)
        assertEquals(1, parse(q = query.copy(minSizePing = BigDecimal("20"))).listings.size)
    }

    @Test fun numericFallbackAndSourceDatePreservePrecision() {
        val data = changed("house_area" to JsonPrimitive("bad"), "size_val" to JsonPrimitive("32"), "sell_price" to JsonPrimitive("1"),
            "unit_price" to JsonNull, "age" to JsonPrimitive("12"), "created_date" to JsonPrimitive("2026-11-06T08:30:00+08:00"))
        val listing = parse(envelope(listOf(data))).listings.single()
        assertEquals(BigDecimal("32"), listing.areaPing)
        assertEquals(BigDecimal("0.03"), listing.unitPrice)
        assertEquals(12, listing.ageYears)
        assertEquals("2026-11-06T08:30:00+08:00", listing.postedDate)
    }

    @Test fun captureEndpointAndHttpStatusAreValidated() {
        for (url in listOf("http://buy.cthouse.com.tw/api/house_list.ashx", "$endpoint?page=1", "$endpoint/", "https://evil.invalid/api/house_list.ashx")) {
            assertEquals(PageKind.INVALID, CtHouseSource.parse(raw().copy(documentUrl = url), query, 0).kind)
            assertEquals(PageKind.INVALID, CtHouseSource.parse(raw().copy(json = listOf(CapturedJson(url, sample))), query, 0).kind)
        }
        assertEquals(PageKind.INVALID, CtHouseSource.parse(raw().copy(statusCode = 500), query, 0).kind)
        assertEquals(PageKind.INVALID, CtHouseSource.parse(raw().copy(json = emptyList(), html = sample), query, 0).kind)
        assertEquals(PageKind.INVALID, CtHouseSource.parse(raw().copy(json = raw().json + raw().json), query, 0).kind)
    }

    @Test fun externalListingAndImageUrlsCannotEscapeSource() {
        val data = changed("url" to JsonPrimitive("https://evil.invalid/"), "imgs" to JsonArray(listOf(JsonPrimitive("https://evil.invalid/a.jpg"))))
        val listing = parse(envelope(listOf(data))).listings.single()
        assertEquals("https://buy.cthouse.com.tw/house/2097688.html", listing.url)
        assertNull(listing.imageUrl)
        assertTrue("imageUrl" in listing.unknownFields)
    }

    @Test fun postTransportFeedsJsonParserAndStoreExportsCurrentPrice() = runTest {
        val sent = ByteArrayOutputStream()
        lateinit var connection: HttpURLConnection
        val transport = HttpPageTransport("test UA", RequestPacer({ testScheduler.currentTime }, { 0 }), StandardTestDispatcher(testScheduler)) {
            object : HttpURLConnection(URL(it)) {
                override fun connect() { }
                override fun disconnect() { }
                override fun usingProxy() = false
                override fun getResponseCode() = 200
                override fun getOutputStream() = sent
                override fun getInputStream() = ByteArrayInputStream(fixtureText("fixtures/cthouse/discounted.json").toByteArray())
                override fun getHeaderField(name: String) = if (name == "Content-Type") "application/json" else null
            }.also { connection = it }
        }
        val capture = transport.capture(CtHouseSource.request(query, 0), 12)
        assertEquals("POST", connection.requestMethod)
        assertEquals("application/json; charset=utf-8", connection.getRequestProperty("Content-Type"))
        assertEquals(JsonPrimitive(1), Json.parseToJsonElement(sent.toString("UTF-8")).jsonObject["page"])
        assertNull(capture.html)
        assertEquals(12L, capture.navigationId)
        val page = CtHouseSource.parse(capture, query, 0)
        val id = "11111111-1111-4111-8111-111111111111"
        val process = "22222222-2222-4222-8222-222222222222"
        val store = ProbeStore(temp.root, process, utcNow = { "2026-11-06T00:00:00Z" }, elapsedNow = { 0L })
        store.create(RunRecord(id, process, query, RunMode.FOREGROUND, RunState.RUNNING,
            DeviceSnapshot(null,null,null,null,null,null,null,null,null,null,null,null), EvidenceSource.SIMULATED))
        store.savePage(id, 0, page)
        assertEquals(page, store.loadPage(id, 0))
        assertEquals(BigDecimal("798.00"), store.loadPage(id, 0).listings.single().totalPrice)
        store.export(id, ByteArrayOutputStream())
    }
}
