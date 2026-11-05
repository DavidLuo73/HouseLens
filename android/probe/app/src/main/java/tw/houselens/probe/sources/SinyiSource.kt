package tw.houselens.probe.sources

import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI
import java.net.URLDecoder
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import tw.houselens.probe.*

object SinyiSource : SourceAdapter {
    override val site = Site.Sinyi
    override val maxPages = 5
    private const val baseUrl = "https://www.sinyi.com.tw"
    private val districts = mapOf("中和區" to "235", "永和區" to "234", "新店區" to "231")
    private val types = listOf("apartment", "dalou", "huaxia", "townhouse", "villa")
    private val typeAliases = mapOf("r1" to listOf("apartment"), "r2" to listOf("dalou", "huaxia"),
        "r4" to listOf("villa"), "r5" to listOf("townhouse"))
    private val parking = listOf("plane", "auto", "mix", "mechanical", "firstfloor", "tower", "other", "yesparking")
    private val parkingAliases = mapOf("pf" to listOf("plane", "auto", "firstfloor"), "pm" to listOf("mix", "mechanical", "tower"))
    private val number = Regex("(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]+)?")

    override fun request(query: ProbeQuery, pageIndex: Int): PageRequest {
        validateQuery(query)
        require(query.site == site && pageIndex in 0 until maxPages) { "invalid_source_page" }
        require(query.useCode in setOf("", "1") && (query.rooms.isEmpty() ||
            query.rooms.split(',').all { Regex("[1-9][0-9]*~?").matches(it) })) { "unsupported_query_codes" }
        val segments = mutableListOf<String>()
        if (query.maxTotalPrice.signum() > 0) segments.add("0-${query.maxTotalPrice.stripTrailingZeros().toPlainString()}-price")
        segments.add(if (query.typeCodes.isEmpty()) "apartment-townhouse-villa-dalou-huaxia-type"
            else resolveCodes(query.typeCodes, types, typeAliases).joinToString("-") + "-type")
        if (query.parkingCodes.isNotEmpty()) {
            val selected = resolveCodes(query.parkingCodes, parking, parkingAliases).toSet() + "yesparking"
            segments.add(parking.filter { it in selected }.joinToString("-"))
        }
        if (query.minSizePing.signum() > 0) segments.add("${query.minSizePing.stripTrailingZeros().toPlainString()}-up-area")
        if (query.maxAgeYears > 0) segments.add("0-${query.maxAgeYears}-year")
        if (query.rooms.isNotEmpty()) segments.add("${query.rooms.split(',').minOf { it.trimEnd('~').toBigInteger() }}-up-roomtotal")
        segments.addAll(listOf("NewTaipei-city", "${districts.getValue(query.district)}-zip", "default-desc", "${pageIndex + 1}"))
        return PageRequest("$baseUrl/buy/list/${segments.joinToString("/")}")
    }

    override fun parse(capture: RawCapture, query: ProbeQuery, pageIndex: Int): PageResult {
        val expected = URI(request(query, pageIndex).url)
        val actual = secureUri(capture.documentUrl) ?: return invalid()
        if (actual.host != expected.host || actual.path != expected.path || actual.rawQuery != expected.rawQuery) return invalid()
        val dom = Jsoup.parse(capture.html ?: return invalid())
        if (dom.select("iframe[src*=challenges.cloudflare.com], #challenge-form, .g-recaptcha").isNotEmpty() ||
            Regex("請完成驗證|驗證您是人類|人機驗證|verify you are human", RegexOption.IGNORE_CASE).containsMatchIn(dom.text()))
            return PageResult(PageKind.CHALLENGE, emptyList(), emptyList(), emptyMap(), NextPage.UNKNOWN, "challenge")
        if (capture.statusCode != null && capture.statusCode !in 200..299) return invalid()
        val cards = dom.select("div.buy-list-item")
        // shortcut: no verified empty marker; add one when live evidence establishes it.
        if (cards.isEmpty()) return invalid()
        val rawKeys = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        val listings = mutableListOf<ProbeListing>()
        val excluded = linkedMapOf<String, String>()
        for (card in cards) {
            val item = listing(card, query) ?: return invalid("parse_error")
            rawKeys.add(item.sourceListingKey)
            if (!seen.add(item.sourceListingKey)) continue
            val reason = exclusionReason(item, query)
            if (reason == null) listings.add(item) else excluded[item.sourceListingKey] = reason
        }
        val next = when (cards.size) {
            in 1..19 -> NextPage.END
            20 -> NextPage.MORE
            else -> NextPage.UNKNOWN
        }
        val evidence = when (next) {
            NextPage.END -> "last_page"
            NextPage.MORE -> "more_pages"
            NextPage.UNKNOWN -> "unknown"
        }
        return PageResult(PageKind.RESULTS, rawKeys, listings, excluded, next, evidence)
    }

    private fun listing(card: Element, query: ProbeQuery): ProbeListing? {
        val links = card.select("a[href]").mapNotNull {
            try { secureUri(URI("$baseUrl/").resolve(it.attr("href")).toString()) }
            catch (_: IllegalArgumentException) { null }
        }.filter { it.host == "www.sinyi.com.tw" && Regex("/buy/house/[A-Za-z0-9]{1,80}").matches(it.path) }
        val id = links.map { it.path.substringAfterLast('/') }.distinct().singleOrNull() ?: return null
        val title = clean(card.selectFirst("div[class*='LongInfoCard_Type_Name']")?.text())
        val address = clean(card.selectFirst("div[class*='LongInfoCard_Type_Address'] span")?.text())
        val city = address?.let { Regex("^([\\p{IsHan}]{2,3}[市縣])").find(it)?.value }
        val district = address?.removePrefix(city ?: "")?.trim()?.let { Regex("^([\\p{IsHan}]{2,4}區)").find(it)?.value }
        val details = card.clone()
        details.select("div[class*='LongInfoCard_Type_Name'], div[class*='LongInfoCard_Type_Address'], script, style, template").remove()
        val text = clean(details.text()).orEmpty()
        val prices = Regex("([+−\\p{Pd}\\p{N}A-Za-z.,/~]+(?:\\s+[+−\\p{Pd}\\p{N}A-Za-z.,/~]+)*)\\s*萬")
            .findAll(text).map { it.groupValues[1] }.toList()
        // shortcut: ambiguous amounts are invalid; narrow to current price when a live selector is verified.
        if (prices.isEmpty() || prices.any { !number.matches(it) }) return null
        val price = prices.map { it.replace(",", "").toBigDecimal() }.distinctBy { it.stripTrailingZeros() }.singleOrNull()
            ?.takeIf { it.signum() > 0 } ?: return null
        val area = Regex("建坪\\s*([0-9]+(?:\\.[0-9]+)?)(?![0-9.,])").find(text)?.groupValues?.get(1)
            ?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
        val age = try { Regex("(?<![0-9.,+-])([0-9]+(?:\\.[0-9]+)?)\\s*年").find(text)?.groupValues?.get(1)
            ?.toBigDecimalOrNull()?.setScale(0, RoundingMode.HALF_EVEN)?.intValueExact() }
            catch (_: ArithmeticException) { null }
        val floor = Regex("(?<![\\p{L}\\p{N}~./-])[0-9]+樓\\s*/\\s*[0-9]+樓(?![\\p{L}\\p{N}~./-])")
            .find(text)?.value?.replace(" ", "")
        val image = card.select("img").firstNotNullOfOrNull { resolveImage(it) }
        val unitPrice = area?.let { price.divide(it, 2, RoundingMode.HALF_EVEN) }
        // shortcut: type/use/parking stay unknown until live field selectors are verified; titles are not evidence.
        val unknown = mutableSetOf("postedDate", "typeCodes", "useCode", "hasParking", "areaBasis")
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
        if (image == null) unknown.add("imageUrl")
        return ProbeListing(city ?: query.city, district ?: query.district, address, area ?: BigDecimal.ZERO,
            floor, age, false, price, unitPrice, site, id, title ?: "", "$baseUrl/buy/house/$id", null,
            image ?: "https://res.sinyi.com.tw/buy/$id/smallimg/A.JPG", unknown)
    }

    private fun resolveImage(img: Element): String? {
        for (attr in listOf("src", "data-src")) {
            val src = img.attr(attr)
            val resolved = try {
                val uri = URI("$baseUrl/").resolve(src)
                if (uri.host == "www.sinyi.com.tw" && uri.path == "/_next/image") {
                    val values = uri.rawQuery.orEmpty().split('&').filter { it.substringBefore('=') == "url" }
                    values.singleOrNull()?.substringAfter('=', "")?.let { URLDecoder.decode(it, "UTF-8") }
                } else src
            } catch (_: IllegalArgumentException) { null }
            if (resolved == null || !allowedImageUrl(site, resolved)) continue
            val path = URI(resolved).path.lowercase()
            if (Regex("\\.(mp4|m3u8|webm|mov)$").containsMatchIn(path) || "/video/" in path || "_video" in path) continue
            return resolved
        }
        return null
    }

    private fun resolveCodes(codes: String, ordered: List<String>, aliases: Map<String, List<String>>): List<String> {
        val selected = codes.split(',').flatMap { code ->
            val normalized = code.lowercase()
            if (normalized in ordered) listOf(normalized)
            else aliases[normalized] ?: throw IllegalArgumentException("unsupported_query_codes")
        }
        return ordered.filter { it in selected }
    }

    private fun clean(text: String?) = text?.replace("\u200B", "")?.replace(Regex("[\\s\\u00a0]+"), " ")?.trim()?.takeIf { it.isNotEmpty() }
    private fun invalid(evidence: String = "invalid_structure") =
        PageResult(PageKind.INVALID, emptyList(), emptyList(), emptyMap(), NextPage.UNKNOWN, evidence)
}
