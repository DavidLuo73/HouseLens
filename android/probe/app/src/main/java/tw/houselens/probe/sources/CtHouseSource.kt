package tw.houselens.probe.sources

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import tw.houselens.probe.*

object CtHouseSource : SourceAdapter {
    override val site = Site.CtHouse
    override val maxPages = 100
    private const val baseUrl = "https://buy.cthouse.com.tw"
    private const val endpoint = "$baseUrl/api/house_list.ashx"
    private val types = linkedMapOf("電梯大樓" to 2, "公寓" to 1, "套房" to 3, "透天" to 6)
    private val aliases = mapOf("R1" to "公寓", "R2" to "電梯大樓", "R3" to "套房", "R5" to "透天")
    private val number = Regex("(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]+)?")

    override fun request(query: ProbeQuery, pageIndex: Int): PageRequest {
        validateQuery(query)
        require(query.site == site && pageIndex in 0 until maxPages) { "invalid_source_page" }
        require(query.useCode in setOf("", "1") && (query.rooms.isEmpty() ||
            query.rooms.split(',').all { Regex("[1-9][0-9]*~?").matches(it) }) &&
            (query.parkingCodes.isEmpty() || query.parkingCodes.split(',').all { it.uppercase() in setOf("PF", "PM") })) {
            "unsupported_query_codes"
        }
        val segments = mutableListOf("${query.city}-city", "${query.district}-town")
        if (query.maxTotalPrice.signum() > 0) segments.add("0-${query.maxTotalPrice.stripTrailingZeros().toPlainString()}-price")
        if (query.minSizePing.signum() > 0) segments.add("${query.minSizePing.stripTrailingZeros().toPlainString()}-up-area")
        segments.add(resolveTypes(query.typeCodes).joinToString("-") + "-type")
        if (query.maxAgeYears > 0) segments.add("0-${query.maxAgeYears}-year")
        if (query.rooms.isNotEmpty()) segments.add("${query.rooms.split(',').minOf { it.trimEnd('~').toBigInteger() }}-up-room")
        if (query.parkingCodes.isNotEmpty()) segments.add("1-parking")
        val body = buildJsonObject {
            put("arg", segments.joinToString("/") + "/")
            put("page", pageIndex + 1)
        }
        return PageRequest(endpoint, method = "POST", body = body.toString(), captureJson = true)
    }

    override fun parse(capture: RawCapture, query: ProbeQuery, pageIndex: Int): PageResult {
        request(query, pageIndex)
        if (!isEndpoint(capture.documentUrl) || (capture.statusCode != null && capture.statusCode !in 200..299)) return invalid()
        val response = capture.json.singleOrNull() ?: return invalid()
        if (!isEndpoint(response.url)) return invalid()
        if (response.body.trimStart().startsWith('<')) {
            val dom = Jsoup.parse(response.body)
            if (dom.select("iframe[src*=challenges.cloudflare.com], #challenge-form, .g-recaptcha").isNotEmpty() ||
                Regex("請完成驗證|驗證您是人類|人機驗證|verify you are human", RegexOption.IGNORE_CASE).containsMatchIn(dom.text()))
                return PageResult(PageKind.CHALLENGE, emptyList(), emptyList(), emptyMap(), NextPage.UNKNOWN, "challenge")
            return invalid()
        }
        val root = try { Json.parseToJsonElement(response.body) as? JsonObject } catch (_: IllegalArgumentException) { null }
            ?: return invalid()
        if (root["RS"] != JsonPrimitive("OK")) return invalid()
        val totalPages = (root["totalpage"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
            ?.takeIf { it >= 0 } ?: return invalid()
        val houses = root["houses"] as? JsonArray ?: return invalid()
        if (houses.isEmpty()) {
            // shortcut: zero-page conventions are synthetic; verify 0/1 on a live empty search.
            return if (pageIndex == 0 && totalPages in 0..1)
                PageResult(PageKind.EMPTY, emptyList(), emptyList(), emptyMap(), NextPage.END, "zero_total")
            else invalid()
        }
        if (totalPages == 0 || pageIndex >= totalPages) return invalid()
        val rawKeys = mutableListOf<String>()
        val seen = mutableMapOf<String, JsonObject>()
        val listings = mutableListOf<ProbeListing>()
        val excluded = linkedMapOf<String, String>()
        val allowedClasses = resolveTypes(query.typeCodes).map { types.getValue(it) }
        for (element in houses) {
            val obj = element as? JsonObject ?: return invalid("parse_error")
            val item = listing(obj, query) ?: return invalid("parse_error")
            val id = item.sourceListingKey
            rawKeys.add(id)
            val previous = seen.put(id, obj)
            if (previous != null) {
                if (previous != obj) return invalid("parse_error")
                continue
            }
            val usage = integer(obj, "house_type_usage")
            val type = integer(obj, "house_type_class")
            val reason = when {
                usage != null && usage != 1 -> "non_residential"
                type != null && type !in allowedClasses -> "unsupported_type"
                else -> exclusionReason(item, query)
            }
            if (reason == null) listings.add(item) else excluded[id] = reason
        }
        return PageResult(PageKind.RESULTS, rawKeys, listings, excluded,
            if (pageIndex + 1 < totalPages) NextPage.MORE else NextPage.END, "total_pages")
    }

    private fun listing(obj: JsonObject, query: ProbeQuery): ProbeListing? {
        val id = (obj["id"] as? JsonPrimitive)?.let {
            if (it.isString) it.content.takeIf { value -> Regex("[A-Za-z0-9]{1,80}").matches(value) }
            else it.content.takeIf { value -> Regex("[0-9]{1,80}").matches(value) }
        } ?: return null
        val price = decimal(obj, "sell_price")?.takeIf { it.signum() > 0 } ?: return null
        val address = string(obj, "address")
        val city = address?.let { Regex("^([\\p{IsHan}]{2,3}[市縣])").find(it)?.value }
        val district = address?.removePrefix(city ?: "")?.trim()?.let { Regex("^([\\p{IsHan}]{2,4}區)").find(it)?.value }
        val area = sequenceOf("house_area", "size_val").mapNotNull { decimal(obj, it) }.firstOrNull { it.signum() > 0 }
        val unit = decimal(obj, "unit_price")?.takeIf { it.signum() > 0 } ?: area?.let { price.divide(it, 2, RoundingMode.HALF_EVEN) }
        val age = integer(obj, "age")?.takeIf { it >= 0 }
        val floor = string(obj, "floor_val")
        val parking = integer(obj, "parking_lot_belong")?.takeIf { it in 0..1 }
        val title = string(obj, "case_name")
        val image = (obj["imgs"] as? JsonArray)?.firstNotNullOfOrNull {
            val path = (it as? JsonPrimitive)?.takeIf { value -> value.isString }?.content ?: return@firstNotNullOfOrNull null
            val url = if (path.startsWith('/') && !path.startsWith("//")) "https://media.cthouse.com.tw/photo$path" else path
            url.takeIf { value -> allowedImageUrl(site, value) }
        }
        val posted = string(obj, "created_date")?.takeIf {
            runCatching { OffsetDateTime.parse(it) }.isSuccess || runCatching { LocalDateTime.parse(it) }.isSuccess ||
                runCatching { LocalDate.parse(it) }.isSuccess
        }
        // shortcut: the query's area basis and exact rooms/parking type need live field evidence.
        val unknown = mutableSetOf("areaBasis")
        if (query.rooms.isNotEmpty()) unknown.add("rooms")
        if (query.parkingCodes.isNotEmpty()) unknown.add("parkingCodes")
        if (integer(obj, "house_type_usage") == null) unknown.add("useCode")
        if (integer(obj, "house_type_class") == null) unknown.add("typeCodes")
        if (city == null) unknown.add("city")
        if (district == null) unknown.add("district")
        if (address == null) unknown.add("address")
        if (title == null) unknown.add("title")
        if (area == null) unknown.add("areaPing")
        if (unit == null) unknown.add("unitPrice")
        if (age == null) unknown.add("ageYears")
        if (floor == null) unknown.add("floor")
        if (parking == null) unknown.add("hasParking")
        if (image == null) unknown.add("imageUrl")
        if (posted == null) unknown.add("postedDate")
        return ProbeListing(city ?: query.city, district ?: query.district, address, area ?: BigDecimal.ZERO,
            floor, age, parking == 1, price, unit, site, id, title ?: "", "$baseUrl/house/$id.html", posted, image, unknown)
    }

    private fun resolveTypes(codes: String): List<String> {
        if (codes.isEmpty()) return listOf("電梯大樓", "公寓")
        val selected = codes.split(',').map { if (it in types) it else aliases[it.uppercase()]
            ?: throw IllegalArgumentException("unsupported_query_codes") }
        return types.keys.filter { it in selected }
    }

    private fun string(obj: JsonObject, key: String) = (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        ?.replace(Regex("[\\s\\u00a0\\u3000]+"), " ")?.trim()?.takeIf { it.isNotEmpty() }
    private fun integer(obj: JsonObject, key: String) = (obj[key] as? JsonPrimitive)?.content?.toIntOrNull()
    private fun decimal(obj: JsonObject, key: String): BigDecimal? = (obj[key] as? JsonPrimitive)?.content
        ?.takeIf { number.matches(it) }?.replace(",", "")?.toBigDecimalOrNull()
    private fun isEndpoint(url: String) = secureUri(url)?.let {
        it.host == "buy.cthouse.com.tw" && it.rawPath == "/api/house_list.ashx" && it.rawQuery == null
    } == true
    private fun invalid(evidence: String = "invalid_structure") =
        PageResult(PageKind.INVALID, emptyList(), emptyList(), emptyMap(), NextPage.UNKNOWN, evidence)
}
