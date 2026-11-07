package tw.houselens.viewer.data

import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.cancellation.CancellationException

class ApiContractTest {
    private val requests = mutableListOf<HttpRequestData>()
    private fun api(
        version: () -> Long = { 0 },
        forbidden: suspend () -> Unit = {},
        reply: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ): SupabaseHouseLensData {
        val client = createViewerClient("https://project.supabase.co", "sb_publishable_test", MockEngine { r ->
            requests += r
            assertEquals(HttpMethod.Get, r.method)
            assertEquals("houselens", r.headers["Accept-Profile"])
            if (r.url.encodedPath.endsWith("/ViewerMembers")) json("""[{"UserId":"member"}]""")
            else reply(r)
        })
        return SupabaseHouseLensData(client, {}, forbidden, version)
    }
    private fun MockRequestHandleScope.json(body: String, total: Int=0) =
        respond(body, HttpStatusCode.OK, headersOf("Content-Type" to listOf("application/json"), "Content-Range" to listOf("0-19/$total")))

    @Test fun list_encodes_filters_both_price_bounds_and_stable_paging() = runTest {
        val data = api { json("""[{"id":"p","currentTotalPrice":9007199254740993.01}]""", 41) }
        val page = data.properties(PropertyQuery(districts=listOf("中正區","大安區"),sourceSites=listOf(0,2),
            minPrice=BigDecimal("1000"),maxPrice=BigDecimal("2000"),hasParking=false,priceDropped=true),2)
        val r=requests.last()
        assertEquals("20", r.url.parameters["limit"])
        assertEquals("20", r.url.parameters["offset"])
        assertTrue(r.headers["Prefer"]!!.contains("count=exact"))
        assertEquals("(currentTotalPrice.gte.1000,currentTotalPrice.lte.2000)", r.url.parameters["and"])
        assertEquals("ov.{0,2}", r.url.parameters["sourceSites"])
        assertTrue(r.url.parameters["district"]!!.contains("中正區"))
        assertEquals("eq.false", r.url.parameters["hasParking"])
        assertEquals("eq.true", r.url.parameters["hasPriceDrop"])
        assertEquals("score.desc.nullslast,currentTotalPrice.asc.nullslast,id.asc.nullslast", r.url.parameters["order"])
        assertEquals(41, page.total)
        assertEquals(BigDecimal("9007199254740993.01"),page.items.single().currentTotalPrice)
    }
    @Test fun all_sorts_and_invalid_page() = runTest {
        val data=api { json("[]") }
        mapOf("price" to "currentTotalPrice.asc", "unitPrice" to "currentUnitPrice.asc.nullsfirst", "newest" to "firstSeenAt.desc").forEach { (sort, expected) ->
            data.properties(PropertyQuery(sortBy=sort),1)
            assertTrue(requests.last().url.parameters["order"]!!.startsWith(expected))
        }
        val before=requests.size
        try { data.properties(PropertyQuery(),0); fail() } catch (_: IllegalArgumentException) {}
        assertEquals(before,requests.size)
    }
    @Test fun detail_paginates_history_even_when_server_caps_below_500() = runTest {
        val data=api { r ->
            when(r.url.pathSegments.last()) {
                "viewer_properties" -> json("""[{"id":"p","currentTotalPrice":100}]""")
                "Listings" -> json("""[{"SourceSite":2,"Url":"https://house.test/p","Title":"房屋"}]""")
                "PriceHistoryEntries" -> {
                    val offset=r.url.parameters["offset"]!!.toInt()
                    assertEquals("500",r.url.parameters["limit"])
                    assertEquals("CapturedAt.desc.nullslast,Id.desc.nullslast",r.url.parameters["order"])
                    val rows=(offset until minOf(offset+200,1101)).joinToString(",") {
                        """{"Id":"$it","CapturedAt":"2026-11-07T00:00:00Z","TotalPrice":100,"ChangeFlag":0}"""
                    }
                    json("[$rows]",1101)
                }
                else -> error("Unexpected endpoint")
            }
        }
        val detail=data.detail("p")!!
        assertEquals(1101,detail.priceHistory.size)
        assertEquals(1101,detail.priceHistory.map { it.id }.toSet().size)
        assertEquals(2,detail.sources.single().sourceSite)
        assertFalse(requests.first { it.url.encodedPath.endsWith("viewer_properties") }.url.toString().contains("PriceHistoryEntries"))
    }
    @Test fun empty_property_and_latest_run_are_null() = runTest {
        val data=api { json("[]") }
        assertNull(data.property("missing"))
        assertNull(data.detail("missing"))
        assertNull(data.latestRun())
    }
    @Test fun drops_stats_top_and_run_decode_their_own_contracts() = runTest {
        val data=api { r ->
            when(r.url.pathSegments.last()) {
                "viewer_big_drops" -> {
                    assertEquals("eq.中正區",r.url.parameters["district"])
                    assertEquals("dropPercent.desc.nullslast,id.asc.nullslast",r.url.parameters["order"])
                    json("""[{"id":"p","newPrice":800,"originalPrice":1000,"dropPercent":0.2,"capturedAt":"2026-01-01T00:00:00Z","status":1}]""",21)
                }
                "viewer_top_properties" -> json("""[{"district":"中正區","rank":1,"id":"p","totalPrice":800,"score":0.9}]""")
                "viewer_district_stats" -> json("""[{"district":"中正區","propertyCount":0,"avgUnitPrice":null,"priceBuckets":[{"range":"0-1000","count":0}],"trend":[{"date":"2026-11-07","avgUnitPrice":12.3}],"insufficientData":true}]""")
                "DistrictConfigs" -> json("""[{"City":"臺北市","District":"中正區","IsEnabled":false}]""")
                "CrawlRuns" -> json("""[{"Id":"r","StartedAt":"2026-11-07T00:00:00Z","Status":1,"NewCount":1,"DelistedCount":0,"BigDropCount":0}]""")
                "SourceRunResults" -> {
                    assertFalse(r.url.parameters["select"]!!.contains("ErrorMessage"))
                    json("""[{"SourceSite":2,"Success":false,"FetchedCount":0}]""")
                }
                else -> error("Unexpected endpoint")
            }
        }
        val drops=data.bigDrops(1,"中正區")
        assertEquals(21,drops.total)
        assertEquals(1,drops.items.single().status)
        assertEquals("2026-01-01T00:00:00Z",drops.items.single().capturedAt)
        assertEquals("p",data.topRated().single().items.single().id)
        assertNull(data.districtStats().single().avgUnitPrice)
        assertEquals("2026-11-07",data.districtStats().single().trend.single().date)
        assertFalse(data.districtOptions().single().isEnabled)
        assertFalse(data.latestRun()!!.sources.single().success)
    }
    @Test fun missing_membership_denies_before_loading_data() = runTest {
        var denied=0
        val client=createViewerClient("https://project.supabase.co","sb_publishable_test",MockEngine {
            assertTrue(it.url.encodedPath.endsWith("ViewerMembers"))
            respond("[]",HttpStatusCode.OK,headersOf("Content-Type","application/json"))
        })
        val data=SupabaseHouseLensData(client,{}, { denied++ },{0})
        try { data.properties(PropertyQuery(),1); fail() } catch(e: ApiException) { assertEquals(403,e.status) }
        assertEquals(1,denied)
    }
    @Test fun non_json_errors_preserve_status_and_retry_after() = runTest {
        for ((status,body,retry) in listOf(Triple(429,"", "12"),Triple(500,"<html>oops</html>",null))) {
            val data=api { respond(body,HttpStatusCode.fromValue(status),headersOf("Retry-After",retry ?: "")) }
            try { data.property("p"); fail() } catch(e: ApiException) {
                assertEquals(status,e.status)
                assertEquals(if(status==429) 12L else null,e.retryAfterSeconds)
                assertFalse(e.message!!.contains("html"))
            }
        }
        val date=ZonedDateTime.now(java.time.ZoneOffset.UTC).plusSeconds(60).format(DateTimeFormatter.RFC_1123_DATE_TIME)
        val data=api { respond("",HttpStatusCode.TooManyRequests,headersOf("Retry-After",date)) }
        try { data.property("p"); fail() } catch(e: ApiException) { assertTrue(e.retryAfterSeconds!! in 58..60) }
    }
    @Test fun stale_session_result_is_cancelled() = runTest {
        var version=0L
        val data=api(version={version}) { version++; json("""[{"id":"p","currentTotalPrice":1}]""") }
        try { data.property("p"); fail() } catch (_: CancellationException) {}
    }
    @Test fun redirects_are_not_followed_with_credentials() = runTest {
        val data=api { respond("",HttpStatusCode.Found,headersOf("Location","https://evil.test/steal")) }
        try { data.property("p") } catch(_: ApiException) {}
        assertTrue(requests.all { it.url.host=="project.supabase.co" })
    }
}
