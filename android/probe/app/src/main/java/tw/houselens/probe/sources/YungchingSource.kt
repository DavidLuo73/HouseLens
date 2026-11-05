package tw.houselens.probe.sources

import java.net.URI
import java.net.URLEncoder
import java.math.BigDecimal
import java.math.RoundingMode
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import tw.houselens.probe.*

object YungchingSource : SourceAdapter {
    override val site = Site.Yungching
    override val maxPages = 30
    private const val baseUrl = "https://buy.yungching.com.tw"
    private val types = linkedMapOf("電梯大廈" to "住宅大樓", "華廈" to "華廈", "無電梯公寓" to "公寓")
    private val typeCodes = mapOf("R1" to listOf("無電梯公寓"), "R2" to listOf("電梯大廈", "華廈"))
    private val parks = listOf("坡道平面", "坡道機械", "昇降平面", "昇降機械", "庭院", "y")
    private val parkingCodes = mapOf("PF" to listOf("坡道平面", "昇降平面"), "PM" to listOf("坡道機械", "昇降機械"))
    private val number = Regex("(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]+)?")

    override fun request(query: ProbeQuery, pageIndex: Int): PageRequest {
        validateQuery(query)
        require(query.site == site && pageIndex in 0 until maxPages) { "invalid_source_page" }
        require(query.useCode in setOf("", "1") && (query.rooms.isEmpty() ||
            query.rooms.split(',').all { Regex("[1-9][0-9]*~?").matches(it) })) { "unsupported_query_codes" }
        val selectedTypes = resolve(query.typeCodes, types.keys.toList(), typeCodes, types.keys.toList())
        val selectedParks = resolve(query.parkingCodes, parks, parkingCodes, emptyList())
        val segments = mutableListOf("${encode(query.city)}-${encode(query.district)}_c")
        if (query.maxTotalPrice.signum() > 0) segments.add("-${query.maxTotalPrice.stripTrailingZeros().toPlainString()}_price")
        segments.add(selectedTypes.joinToString(",", transform = ::encode) + "_type")
        if (query.minSizePing.signum() > 0) segments.add("${query.minSizePing.stripTrailingZeros().toPlainString()}-_pin")
        segments.add("${encode("住宅")}_p")
        if (selectedParks.isNotEmpty()) segments.add(selectedParks.joinToString(",", transform = ::encode) + "_park")
        if (query.maxAgeYears > 0) segments.add("-${query.maxAgeYears}_age")
        val url = "$baseUrl/list/${segments.joinToString("/")}" + if (pageIndex == 0) "" else "?pg=${pageIndex + 1}"
        return PageRequest(url, browser = true, warmupUrl = baseUrl)
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
        val emptyScope = dom.body().clone()
        emptyScope.select("li.search-result-list-item, script, style, template, noscript, [hidden], [aria-hidden=true]").remove()
        val hiddenStyle = Regex("(?:^|;)\\s*(?:display\\s*:\\s*none|visibility\\s*:\\s*(?:hidden|collapse))\\s*(?:!important\\s*)?(?:;|$)", RegexOption.IGNORE_CASE)
        emptyScope.select("[style]").filter { hiddenStyle.containsMatchIn(it.attr("style")) }.forEach { it.remove() }
        if (emptyScope.allElements.any { it.ownText() == "暫無符合物件" }) {
            return PageResult(PageKind.EMPTY, emptyList(), emptyList(), emptyMap(), NextPage.END, "empty_marker")
        }
        val cards = dom.select("li.search-result-list-item")
        if (cards.isEmpty()) return invalid()
        val allowed = resolve(query.typeCodes, types.keys.toList(), typeCodes, types.keys.toList()).map { types.getValue(it) }
        val rawKeys = linkedSetOf<String>()
        val listings = mutableListOf<ProbeListing>()
        val excluded = linkedMapOf<String, String>()
        for (container in cards) {
            val card = container.selectFirst("a.link") ?: return invalid("parse_error")
            val item = listing(card, query) ?: return invalid("parse_error")
            if (!rawKeys.add(item.sourceListingKey)) continue
            val caseType = text(card, ".caseType")
            val reason = when {
                caseType != null && caseType !in types.values -> "non_residential"
                caseType != null && caseType !in allowed -> "unsupported_type"
                else -> exclusionReason(item, query)
            }
            if (reason == null) listings.add(item) else excluded[item.sourceListingKey] = reason
        }
        // shortcut: nonempty fixtures have no reliable last-page marker; add only after live evidence.
        return PageResult(PageKind.RESULTS, rawKeys.toList(), listings, excluded, NextPage.UNKNOWN, "unknown")
    }

    private fun listing(card: Element, query: ProbeQuery): ProbeListing? {
        val href = card.attr("href")
        val link = try { secureUri(URI("$baseUrl/").resolve(href).toString()) } catch (_: IllegalArgumentException) { null }
        if (link?.host != "buy.yungching.com.tw") return null
        val id = Regex("/house/([0-9]{1,80})").matchEntire(link.path)?.groupValues?.get(1) ?: return null
        val price = decimal(text(card, ".price"))?.takeIf { it.signum() > 0 } ?: return null
        val title = text(card, ".caseName")
        val address = text(card, ".address")
        val city = address?.let { Regex("^([\\p{IsHan}]{2,3}[市縣])").find(it)?.value }
        val district = address?.removePrefix(city ?: "")?.trim()?.let { Regex("^([\\p{IsHan}]{2,4}區)").find(it)?.value }
        val area = text(card, ".regArea")?.let { Regex("建坪\\s*([0-9]+(?:\\.[0-9]+)?)").matchEntire(it)?.groupValues?.get(1) }
            ?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
        val floor = text(card, ".floor")
        val age = card.select(".case-info span").firstNotNullOfOrNull {
            val value = Regex("([0-9]+(?:\\.[0-9]+)?)\\s*年").matchEntire(it.text())?.groupValues?.get(1)
            try { value?.toBigDecimalOrNull()?.setScale(0, RoundingMode.HALF_EVEN)?.intValueExact() }
            catch (_: ArithmeticException) { null }
        }
        val room = text(card, ".room")
        val hasParking = room != null && !Regex("(?:無|沒有|未含|不含)\\s*車位").containsMatchIn(room) &&
            room.split(Regex("\\s+")).any { it == "車位" }
        val image = card.selectFirst(".img-wrapper img[src]")?.attr("src")?.takeIf { allowedImageUrl(site, it) }
        val unitPrice = area?.let { price.divide(it, 2, RoundingMode.HALF_EVEN) }
        val unknown = mutableSetOf("postedDate", "useCode", "areaBasis")
        if (text(card, ".caseType") == null) unknown.add("typeCodes")
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
            floor, age, hasParking, price, unitPrice, site, id, title ?: "", "$baseUrl/house/$id", null, image, unknown)
    }

    private fun resolve(codes: String, names: List<String>, aliases: Map<String, List<String>>, defaults: List<String>): List<String> {
        if (codes.isEmpty()) return defaults
        val selected = codes.split(',').flatMap { code ->
            names.firstOrNull { it.equals(code, ignoreCase = true) }?.let { listOf(it) }
                ?: aliases[code.uppercase()] ?: throw IllegalArgumentException("unsupported_query_codes")
        }
        return names.filter { it in selected }
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun text(card: Element, selector: String) = card.selectFirst(selector)?.text()?.takeIf { it.isNotBlank() }
    private fun decimal(value: String?) = value?.takeIf(number::matches)?.replace(",", "")?.toBigDecimalOrNull()
    private fun invalid(evidence: String = "invalid_structure") =
        PageResult(PageKind.INVALID, emptyList(), emptyList(), emptyMap(), NextPage.UNKNOWN, evidence)
}
