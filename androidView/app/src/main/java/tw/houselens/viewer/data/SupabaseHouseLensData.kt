package tw.houselens.viewer.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.minimalConfig
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.*
import io.github.jan.supabase.postgrest.query.*
import io.github.jan.supabase.postgrest.result.PostgrestResult
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.Url
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.Duration
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

@OptIn(SupabaseInternal::class)
fun createViewerClient(url: String, key: String, engine: HttpClientEngine? = null): SupabaseClient {
    val origin = Url(url)
    require(origin.protocol.name == "https" && origin.host.isNotBlank() &&
        origin.user == null && origin.password == null &&
        origin.encodedPath in listOf("", "/") && origin.parameters.isEmpty() && origin.fragment.isEmpty())
    return createSupabaseClient(url.trimEnd('/'), key) {
        httpEngine = engine ?: OkHttp.create()
        defaultSerializer = KotlinXSerializer(viewerJson)
        requestTimeout = 15.seconds
        httpConfig {
            followRedirects = false
            expectSuccess = false
            install(createClientPlugin("SupabaseOrigin") {
                onRequest { request, _ ->
                    val target = request.url.build()
                    require(target.protocol == origin.protocol && target.host == origin.host &&
                        target.port == origin.port && target.user == null && target.password == null) {
                        "Untrusted Supabase origin"
                    }
                }
            })
        }
        install(Auth) {
            // B2 replaces memory-only storage before enabling the login UI.
            minimalConfig()
            flowType = FlowType.PKCE
            scheme = "tw.houselens.viewer"
            host = "auth"
            defaultRedirectUrl = "tw.houselens.viewer://auth/callback"
        }
        install(Postgrest) {
            defaultSchema = "houselens"
            propertyConversionMethod = PropertyConversionMethod.SERIAL_NAME
            timeout = 15.seconds
        }
    }
}

class SupabaseHouseLensData(
    private val client: SupabaseClient,
    private val refreshSession: suspend () -> Unit,
    private val onForbidden: suspend () -> Unit,
    private val sessionVersion: () -> Long
) : HouseLensData {
    private suspend fun <T> read(block: suspend () -> T): T {
        val version = sessionVersion()
        fun ensureCurrent() {
            if (version != sessionVersion()) throw CancellationException("Session changed")
        }
        try {
            val members = client.from("ViewerMembers").select(Columns.list("UserId")) { limit(1) }
                .decodeList<kotlinx.serialization.json.JsonObject>()
            ensureCurrent()
            if (members.isEmpty()) throw ApiException(403, null, "此帳號尚未獲得使用權限")
            return block().also { ensureCurrent() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RestException) {
            ensureCurrent()
            if (e.statusCode == 403) onForbidden()
            throw ApiException(e.statusCode, retryAfter(e.response.headers["Retry-After"]), "資料讀取失敗（HTTP ${e.statusCode}），請重試")
        } catch (e: ApiException) {
            ensureCurrent()
            if (e.status == 403) onForbidden()
            throw e
        } catch (e: Exception) {
            ensureCurrent()
            throw ApiException(null, null, "暫時無法取得資料，請檢查連線後重試")
        }
    }

    override suspend fun properties(query: PropertyQuery, page: Int): PropertyPage {
        require(page > 0) { "Page must be positive" }
        require(validateQuery(query) == null) { validateQuery(query).orEmpty() }
        return read {
            val result = client.from("viewer_properties").select {
                count(Count.EXACT)
                filter {
                    eq("status", query.status)
                    if (query.districts.isNotEmpty()) isIn("district", query.districts)
                    if (query.sourceSites.isNotEmpty()) overlaps("sourceSites", query.sourceSites)
                    query.hasParking?.let { eq("hasParking", it) }
                    if (query.priceDropped) eq("hasPriceDrop", true)
                    // SDK 3.5 collapses duplicate parameter keys; AND preserves both bounds.
                    if (query.minPrice != null || query.maxPrice != null) and {
                        query.minPrice?.let { gte("currentTotalPrice", it.toPlainString()) }
                        query.maxPrice?.let { lte("currentTotalPrice", it.toPlainString()) }
                    }
                }
                when (query.sortBy) {
                    "score" -> {
                        order("score", Order.DESCENDING, nullsFirst = false)
                        order("currentTotalPrice", Order.ASCENDING)
                    }
                    "price" -> order("currentTotalPrice", Order.ASCENDING)
                    "unitPrice" -> order("currentUnitPrice", Order.ASCENDING, nullsFirst = true)
                    "newest" -> order("firstSeenAt", Order.DESCENDING)
                }
                order("id", Order.ASCENDING)
                range((page - 1L) * 20, page * 20L - 1)
            }
            PropertyPage(result.total(), page, 20, result.decodeList())
        }
    }
    private suspend fun summary(id: String) = client.from("viewer_properties").select {
        filter { eq("id", id) }; limit(1)
    }.decodeSingleOrNull<PropertyDto>()

    override suspend fun property(id: String): PropertyDto? = read { summary(id) }

    override suspend fun detail(id: String): PropertyDetails? = read {
        val property = summary(id) ?: return@read null
        val sources = client.from("Listings").select(Columns.list("SourceSite","Title","Url","ImageUrl","PostedDate")) {
            filter { eq("PropertyId", id) }; order("Id", Order.ASCENDING)
        }.decodeList<SourceDto>()
        val history = mutableListOf<PriceEntryDto>()
        do {
            val result = client.from("PriceHistoryEntries").select(
                Columns.list("Id","CapturedAt","TotalPrice","UnitPrice","SourceSite","ChangeFlag","ChangePercent","IsBigDrop")
            ) {
                count(Count.EXACT)
                filter { eq("PropertyId", id) }
                order("CapturedAt", Order.DESCENDING)
                order("Id", Order.DESCENDING)
                range(history.size.toLong(), history.size + 499L)
            }
            val batch = result.decodeList<PriceEntryDto>()
            val total = result.total()
            if (batch.isEmpty() && history.size < total) throw ApiException(null, null, "價格紀錄不完整，請重新載入")
            history += batch
        } while (history.size < total)
        PropertyDetails(property, sources, history)
    }

    override suspend fun topRated(): List<TopGroupDto> = read {
        client.from("viewer_top_properties").select {
            order("district", Order.ASCENDING); order("rank", Order.ASCENDING)
        }.decodeList<TopItemDto>().groupBy { it.district }.map { (district, items) -> TopGroupDto(district, items) }
    }
    override suspend fun bigDrops(page: Int, district: String?): DropPage {
        require(page > 0) { "Page must be positive" }
        return read {
            val result = client.from("viewer_big_drops").select {
                count(Count.EXACT)
                filter { district?.let { eq("district", it) } }
                order("dropPercent", Order.DESCENDING); order("id", Order.ASCENDING)
                range((page - 1L) * 20, page * 20L - 1)
            }
            DropPage(result.total(), result.decodeList())
        }
    }
    override suspend fun districtStats(): List<DistrictStatsDto> = read {
        client.from("viewer_district_stats").select { order("district", Order.ASCENDING) }.decodeList()
    }
    override suspend fun districtOptions(): List<DistrictOptionDto> = read {
        client.from("DistrictConfigs").select(Columns.list("City","District","IsEnabled")) {
            order("District", Order.ASCENDING); order("City", Order.ASCENDING)
        }.decodeList()
    }
    override suspend fun latestRun(): CrawlRunDto? = read {
        val run = client.from("CrawlRuns").select(Columns.list("Id","StartedAt","FinishedAt","Status","NewCount","DelistedCount","BigDropCount")) {
            order("StartedAt", Order.DESCENDING); order("Id", Order.DESCENDING); limit(1)
        }.decodeSingleOrNull<CrawlRunDto>() ?: return@read null
        val sources = client.from("SourceRunResults").select(Columns.list("SourceSite","Success","FetchedCount")) {
            filter { eq("CrawlRunId", run.id) }; order("SourceSite", Order.ASCENDING)
        }.decodeList<SourceResultDto>()
        run.copy(sources = sources)
    }
}
private fun PostgrestResult.total(): Int {
    val total = countOrNull()
    if (total == null || total !in 0..Int.MAX_VALUE.toLong()) throw ApiException(null, null, "無法確認資料筆數，請重試")
    return total.toInt()
}
private fun retryAfter(raw: String?): Long? {
    if (raw == null) return null
    raw.toLongOrNull()?.let { return it.coerceAtLeast(0) }
    return try {
        Duration.between(java.time.Instant.now(), ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant())
            .seconds.coerceAtLeast(0)
    } catch (e: Exception) { null }
}
