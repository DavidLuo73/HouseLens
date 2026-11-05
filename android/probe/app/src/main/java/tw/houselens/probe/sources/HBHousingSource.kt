package tw.houselens.probe.sources

import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI
import java.net.URLEncoder
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import tw.houselens.probe.*

object HBHousingSource : SourceAdapter {
    override val site = Site.HBHousing
    override val maxPages = 100
    private const val baseUrl = "https://www.hbhousing.com.tw"
    private val districts = mapOf("中和區" to "235", "永和區" to "234", "新店區" to "231")
    private val types = linkedMapOf("noelevator" to "公寓", "elevator" to "大樓", "mansion" to "華廈")
    private val aliases = mapOf("r1" to listOf("noelevator"), "r2" to listOf("elevator", "mansion"))
    private val residentialTypes = types.values.toSet() + setOf("透天/別墅", "別墅", "透天厝", "農舍")
    private val number = Regex("(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]+)?")

    override fun request(query: ProbeQuery, pageIndex: Int): PageRequest {
        validateQuery(query)
        require(query.site == site && pageIndex in 0 until maxPages) { "invalid_source_page" }
        require(query.useCode in setOf("", "1") && (query.parkingCodes.isEmpty() ||
            query.parkingCodes.split(',').all { it in setOf("PF", "PM") }) && (query.rooms.isEmpty() ||
            query.rooms.split(',').all { Regex("[1-9][0-9]*~?").matches(it) })) { "unsupported_query_codes" }
        val segments = mutableListOf(URLEncoder.encode(query.city, "UTF-8"), districts.getValue(query.district),
            resolveTypes(query.typeCodes).joinToString("-") + "-style")
        if (query.maxTotalPrice.signum() > 0) segments.add("${query.maxTotalPrice.stripTrailingZeros().toPlainString()}-down-price")
        segments.add("apartment-type")
        if (query.minSizePing.signum() > 0) segments.add("area-${query.minSizePing.stripTrailingZeros().toPlainString()}-up-area")
        if (query.maxAgeYears > 0) segments.add("${query.maxAgeYears}-down-age")
        if (query.rooms.isNotEmpty()) {
            val minRooms = query.rooms.split(',').minOf { it.trimEnd('~').toBigInteger() }
            segments.add("${minRooms}_up_room-pattern")
        }
        if (query.parkingCodes.isNotEmpty()) segments.add("parking-tag")
        if (pageIndex > 0) segments.add("${pageIndex + 1}-page")
        return PageRequest("$baseUrl/buyhouse/${segments.joinToString("/")}", browser = true, warmupUrl = baseUrl)
    }

    override fun parse(capture: RawCapture, query: ProbeQuery, pageIndex: Int): PageResult {
        val expected = URI(request(query, pageIndex).url)
        val actual = secureUri(capture.documentUrl) ?: return invalid()
        if (actual.host != expected.host || actual.path != expected.path || actual.rawQuery != expected.rawQuery) return invalid()
        val dom = Jsoup.parse(capture.html ?: return invalid())
        if (dom.select("iframe[src*=challenges.cloudflare.com], #challenge-form, .g-recaptcha").isNotEmpty() ||
            Regex("請完成驗證|驗證您是人類|人機驗證|verify you are human", RegexOption.IGNORE_CASE).containsMatchIn(dom.text())) {
            return PageResult(PageKind.CHALLENGE, emptyList(), emptyList(), emptyMap(), NextPage.UNKNOWN, "challenge")
        }
        if (capture.statusCode != null && capture.statusCode !in 200..299) return invalid()
        val cards = dom.select("section[class*='@container']")
        // shortcut: no verified empty-state marker exists in the fixtures; add one after live evidence.
        if (cards.isEmpty()) return invalid()
        val allowed = if (query.typeCodes.isEmpty()) residentialTypes else resolveTypes(query.typeCodes).map { types.getValue(it) }.toSet()
        val rawKeys = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        val listings = mutableListOf<ProbeListing>()
        val excluded = linkedMapOf<String, String>()
        for (card in cards) {
            val parts = precedingSpan(card.select("button").firstOrNull { "格局" in it.text() })
                ?.split('|')?.map(String::trim).orEmpty()
            val type = parts.firstOrNull()?.takeIf { it.isNotBlank() &&
                !Regex("^(?:[0-9]|B[0-9]|地下.*樓|建坪$|[-—]+$)").containsMatchIn(it) }
            val item = listing(card, parts, type, query) ?: return invalid("parse_error")
            rawKeys.add(item.sourceListingKey)
            if (!seen.add(item.sourceListingKey)) continue
            val reason = when {
                type != null && type !in residentialTypes -> "non_residential"
                type != null && type !in allowed -> "unsupported_type"
                else -> exclusionReason(item, query)
            }
            if (reason == null) listings.add(item) else excluded[item.sourceListingKey] = reason
        }
        val next = when (cards.size) {
            in 1..9 -> NextPage.END
            10 -> NextPage.MORE
            else -> NextPage.UNKNOWN
        }
        val evidence = when (next) {
            NextPage.END -> "last_page"
            NextPage.MORE -> "more_pages"
            NextPage.UNKNOWN -> "unknown"
        }
        return PageResult(PageKind.RESULTS, rawKeys, listings, excluded, next, evidence)
    }

    private fun listing(card: Element, parts: List<String>, type: String?, query: ProbeQuery): ProbeListing? {
        val titleLink = card.select("h3 a[href]").firstOrNull { '#' !in it.attr("href") } ?: return null
        val link = try { secureUri(URI("$baseUrl/").resolve(titleLink.attr("href")).toString()) }
            catch (_: IllegalArgumentException) { null }
        if (link?.host != "www.hbhousing.com.tw" || link.path != "/detail") return null
        val id = Regex("sn=([A-Za-z0-9]{1,80})").matchEntire(link.rawQuery ?: "")?.groupValues?.get(1) ?: return null
        val priceText = clean(card.selectFirst("span[class*='text-error']")?.text())?.removeSuffix("萬")?.trim()
        val price = priceText?.takeIf(number::matches)?.replace(",", "")?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: return null
        val address = precedingSpan(card.selectFirst("a[href*='#life']"))
        val city = address?.let { Regex("^([\\p{IsHan}]{2,3}[市縣])").find(it)?.value }
        val district = address?.removePrefix(city ?: "")?.trim()?.let { Regex("^([\\p{IsHan}]{2,4}區)").find(it)?.value }
        val age = parts.firstNotNullOfOrNull {
            val value = Regex("([0-9]+(?:\\.[0-9]+)?)\\s*年").matchEntire(it)?.groupValues?.get(1)
            try { value?.toBigDecimalOrNull()?.setScale(0, RoundingMode.HALF_EVEN)?.intValueExact() }
            catch (_: ArithmeticException) { null }
        }
        val floor = parts.firstOrNull { '樓' in it && '/' in it }
        val areaText = parts.indexOf("建坪").takeIf { it >= 0 }?.let { parts.getOrNull(it + 1) }
        val area = areaText?.let { Regex("([0-9]+(?:\\.[0-9]+)?)\\s*坪").matchEntire(it)?.groupValues?.get(1) }
            ?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
        val hasParking = card.select("div.tag").any {
            val label = clean(it.text()).orEmpty()
            Regex("(?:有)?\\s*車位").matches(label)
        }
        val title = clean(titleLink.text())
        val image = card.select("img[src]").map { it.attr("src") }.firstOrNull { allowedImageUrl(site, it) }
        val unitPrice = area?.let { price.divide(it, 2, RoundingMode.HALF_EVEN) }
        val unknown = mutableSetOf("postedDate", "useCode", "areaBasis")
        if (type == null) unknown.add("typeCodes")
        if (query.rooms.isNotEmpty()) unknown.add("rooms")
        if (query.parkingCodes.isNotEmpty()) unknown.add("parkingCodes")
        if (city == null) unknown.add("city")
        if (district == null) unknown.add("district")
        if (address == null) unknown.add("address")
        if (title == null) unknown.add("title")
        if (floor == null) unknown.add("floor")
        if (age == null) unknown.add("ageYears")
        if (area == null) unknown.add("areaPing")
        if (unitPrice == null) unknown.add("unitPrice")
        if (!hasParking) unknown.add("hasParking")
        if (image == null) unknown.add("imageUrl")
        return ProbeListing(city ?: query.city, district ?: query.district, address, area ?: BigDecimal.ZERO,
            floor, age, hasParking, price, unitPrice, site, id, title ?: "", "$baseUrl/detail?sn=$id", null, image, unknown)
    }

    private fun resolveTypes(codes: String): List<String> {
        if (codes.isEmpty()) return types.keys.toList()
        val selected = codes.split(',').flatMap { code ->
            val normalized = code.lowercase()
            if (normalized in types) listOf(normalized)
            else aliases[normalized] ?: throw IllegalArgumentException("unsupported_query_codes")
        }
        return types.keys.filter { it in selected }
    }

    private fun precedingSpan(element: Element?) = clean(generateSequence(element?.previousElementSibling()) {
        it.previousElementSibling()
    }.firstOrNull { it.tagName() == "span" }?.text())
    private fun clean(text: String?) = text?.replace("\u200B", "")?.replace(Regex("[\\s\\u00a0]+"), " ")?.trim()?.takeIf { it.isNotEmpty() }
    private fun invalid(evidence: String = "invalid_structure") =
        PageResult(PageKind.INVALID, emptyList(), emptyList(), emptyMap(), NextPage.UNKNOWN, evidence)
}
