package tw.houselens.probe.sources

import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import tw.houselens.probe.*

object TwHouseSource : SourceAdapter {
    override val site = Site.TwHouse
    override val maxPages = 30
    private const val baseUrl = "https://www.twhg.com.tw"
    private val kinds = listOf("apartment", "midrise", "condo")
    private val aliases = mapOf("R1" to listOf("apartment"), "R2" to listOf("midrise", "condo"))
    private val nonResidential = listOf("辦公室", "店面", "廠房", "廠辦", "倉庫", "工廠", "土地", "建地", "農地")
    private val number = "(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]+)?"

    override fun request(query: ProbeQuery, pageIndex: Int): PageRequest {
        validateQuery(query)
        require(query.site == site && pageIndex in 0 until maxPages) { "invalid_source_page" }
        require(query.useCode in setOf("", "1") && (query.parkingCodes.isEmpty() ||
            query.parkingCodes.split(',').all { it.uppercase() in setOf("PF", "PM") })) { "unsupported_query_codes" }
        val rooms = if (query.rooms.isEmpty()) emptyList() else query.rooms.split(',').map {
            require(Regex("[1-9][0-9]*~?").matches(it)) { "unsupported_query_codes" }
            it.removeSuffix("~").toIntOrNull()?.takeIf { n -> n > 0 }
                ?: throw IllegalArgumentException("unsupported_query_codes")
        }
        val selected = if (query.typeCodes.isEmpty()) kinds else query.typeCodes.split(',').flatMap { code ->
            kinds.firstOrNull { it.equals(code, ignoreCase = true) }?.let { listOf(it) }
                ?: aliases[code.uppercase()] ?: throw IllegalArgumentException("unsupported_query_codes")
        }
        val zip = mapOf("中和區" to "235", "永和區" to "234", "新店區" to "231").getValue(query.district)
        val segments = mutableListOf("newTaipei-city", "$zip-zips", kinds.filter { it in selected }.joinToString("-") + "-kinds")
        if (query.maxTotalPrice.signum() > 0) segments.add("${query.maxTotalPrice.stripTrailingZeros().toPlainString()}down-price")
        if (query.minSizePing.signum() > 0) {
            segments.add("${query.minSizePing.stripTrailingZeros().toPlainString()}up-ping")
            segments.add("fullBuildingPing-ping_type")
        }
        if (rooms.isNotEmpty()) segments.add("${rooms.min()}up-bedrooms")
        if (query.maxAgeYears > 0) segments.add("${query.maxAgeYears}down-house_year")
        if (query.parkingCodes.isNotEmpty()) segments.add("1up-park_count")
        segments.add("recomended-desc")
        return PageRequest("$baseUrl/buy/list/${segments.joinToString("/")}?page=${pageIndex + 1}")
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
        emptyScope.select("a, script, style, template, noscript, [hidden], [aria-hidden=true]").remove()
        val hiddenStyle = Regex("(?:^|;)\\s*(?:display\\s*:\\s*none|visibility\\s*:\\s*(?:hidden|collapse))\\s*(?:!important\\s*)?(?:;|$)", RegexOption.IGNORE_CASE)
        emptyScope.select("[style]").filter { hiddenStyle.containsMatchIn(it.attr("style")) }.forEach { it.remove() }
        if (emptyScope.allElements.any { it.ownText() == "暫無查詢物件" }) {
            return PageResult(PageKind.EMPTY, emptyList(), emptyList(), emptyMap(), NextPage.END, "empty_marker")
        }
        val cards = dom.select("a").filter { card ->
            val href = card.attr("href")
            (card.parents().any { it.tagName() == "li" } && card.selectFirst("h2, h3") != null &&
                Regex("建坪|地坪|萬").containsMatchIn(card.text())) ||
                (href.contains("/buy/") && !href.contains("/buy/list") && !href.endsWith("/buy/"))
        }
        if (cards.isEmpty()) return invalid()
        val rawKeys = mutableListOf<String>()
        val seen = mutableMapOf<String, Pair<ProbeListing, String?>>()
        val listings = mutableListOf<ProbeListing>()
        val excluded = linkedMapOf<String, String>()
        for (card in cards) {
            val item = listing(card, query) ?: return invalid("parse_error")
            rawKeys.add(item.sourceListingKey)
            val reason = if (nonResidential.any { card.text().contains(it) }) "non_residential" else exclusionReason(item, query)
            val previous = seen.putIfAbsent(item.sourceListingKey, item to reason)
            if (previous != null) {
                if (previous != (item to reason)) return invalid("parse_error")
                continue
            }
            if (reason == null) listings.add(item) else excluded[item.sourceListingKey] = reason
        }
        // shortcut: only the empty marker proves the end; add other pagination evidence after live validation.
        return PageResult(PageKind.RESULTS, rawKeys, listings, excluded, NextPage.UNKNOWN, "unknown")
    }

    private fun listing(card: Element, query: ProbeQuery): ProbeListing? {
        val link = try { secureUri(URI("$baseUrl/").resolve(card.attr("href")).toString()) } catch (_: IllegalArgumentException) { null }
        if (link?.host != "www.twhg.com.tw" || link.rawQuery != null) return null
        val id = Regex("/buy/([A-Z][A-Z0-9]{1,79})").matchEntire(link.path)?.groupValues?.get(1) ?: return null
        val title = (card.selectFirst("h2") ?: card.selectFirst("h3"))?.text()?.removeSuffix(id)?.trim()?.takeIf { it.isNotEmpty() }
        val details = card.clone()
        details.select("h2, h3, script, style, template").remove()
        if (details.text().contains("面議")) return null
        // shortcut: known layouts use mt-auto or the final direct field; recheck the selector if live markup changes.
        val priceField = details.select(".mt-auto").lastOrNull() ?: details.children().lastOrNull() ?: return null
        val amount = Regex("($number)\\s*萬").matchEntire(priceField.text())?.groupValues?.get(1) ?: return null
        val price = amount.replace(",", "").toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: return null
        val text = details.text()
        val area = Regex("建坪\\s*([0-9]+(?:\\.[0-9]+)?)\\s*坪").find(text)?.groupValues?.get(1)
            ?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
        val ageValue = Regex("(?:^|\\s)([0-9]+(?:\\.[0-9]+)?)\\s*年(?:$|\\s)").find(text)?.groupValues?.get(1)?.toBigDecimalOrNull()
        val age = try { ageValue?.setScale(0, RoundingMode.HALF_EVEN)?.intValueExact() } catch (_: ArithmeticException) { null }
        val location = Regex("(?:^|[\\s\\p{Zs}])([\\p{IsHan}]{2,3}[市縣])").find(text)
        val city = location?.groupValues?.get(1)
        val district = location?.let {
            Regex("^[\\s\\p{Zs}]*([\\p{IsHan}]{2,4}[區鎮市鄉])")
                .find(text.substring(it.range.last + 1))?.groupValues?.get(1)
        }
        val background = card.select("[style]").asSequence().mapNotNull {
            Regex("background-image\\s*:\\s*url\\(\\s*['\"]?([^'\"()\\s]+)['\"]?\\s*\\)", RegexOption.IGNORE_CASE)
                .find(it.attr("style"))?.groupValues?.get(1)
        }
        val image = (background + card.select("img[src]").asSequence().map { it.attr("src") })
            .firstOrNull { allowedImageUrl(site, it) }
        val unitPrice = area?.let { price.divide(it, 2, RoundingMode.HALF_EVEN) }
        // shortcut: list HTML does not verify detail fields or exact filter semantics; recheck with live evidence.
        val unknown = mutableSetOf("address", "floor", "hasParking", "postedDate", "typeCodes", "useCode", "areaBasis")
        if (query.rooms.isNotEmpty()) unknown.add("rooms")
        if (query.parkingCodes.isNotEmpty()) unknown.add("parkingCodes")
        if (city == null) unknown.add("city")
        if (district == null) unknown.add("district")
        if (title == null) unknown.add("title")
        if (area == null) unknown.add("areaPing")
        if (unitPrice == null) unknown.add("unitPrice")
        if (age == null) unknown.add("ageYears")
        if (image == null) unknown.add("imageUrl")
        return ProbeListing(city ?: query.city, district ?: query.district, null, area ?: BigDecimal.ZERO,
            null, age, false, price, unitPrice, site, id, title ?: "", "$baseUrl/buy/$id", null, image, unknown)
    }

    private fun invalid(evidence: String = "invalid_structure") =
        PageResult(PageKind.INVALID, emptyList(), emptyList(), emptyMap(), NextPage.UNKNOWN, evidence)
}
