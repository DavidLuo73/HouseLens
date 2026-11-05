package tw.houselens.probe.sources

import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLDecoder
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import tw.houselens.probe.*

object RakuyaSource : SourceAdapter {
    override val site = Site.Rakuya
    override val maxPages = 30
    private const val baseUrl = "https://www.rakuya.com.tw"
    private val zipcodes = mapOf("中和區" to "235", "永和區" to "234", "新店區" to "231")
    private val residentialTypes = setOf("公寓", "電梯大廈", "住宅大樓", "華廈", "透天厝", "別墅",
        "透天別墅", "住宅", "樓中樓", "套房")
    private val idPattern = Regex("[A-Za-z0-9_-]{1,80}")
    private val numberPattern = Regex("(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]+)?")

    override fun request(query: ProbeQuery, pageIndex: Int): PageRequest {
        validateQuery(query)
        require(query.site == site && pageIndex in 0 until maxPages) { "invalid_source_page" }
        require(query.useCode in setOf("", "1") &&
            (query.typeCodes.isEmpty() || query.typeCodes.split(',').all { it in setOf("R1", "R2", "R3", "R4", "R5", "R6") }) &&
            (query.parkingCodes.isEmpty() || query.parkingCodes.split(',').all { it in setOf("PF", "PM") }) &&
            (query.rooms.isEmpty() || query.rooms.split(',').all { Regex("[1-9][0-9]*~?").matches(it) })) {
            "unsupported_query_codes"
        }
        val url = buildString {
            append("$baseUrl/sell/result?zipcode=${zipcodes.getValue(query.district)}")
            append("&usecode=${query.useCode.ifEmpty { "1" }}&typecode=${query.typeCodes.ifEmpty { "R1,R2" }}")
            if (query.maxTotalPrice.signum() > 0) append("&price=~${query.maxTotalPrice.stripTrailingZeros().toPlainString()}")
            if (query.minSizePing.signum() > 0) append("&size=${query.minSizePing.stripTrailingZeros().toPlainString()}~")
            if (query.rooms.isNotEmpty()) append("&room=${query.rooms}")
            if (query.maxAgeYears > 0) append("&age=~${query.maxAgeYears}")
            if (query.parkingCodes.isNotEmpty()) append("&other=${query.parkingCodes}")
            append("&page=${pageIndex + 1}")
        }
        return PageRequest(url, browser = true, warmupUrl = baseUrl)
    }

    override fun parse(capture: RawCapture, query: ProbeQuery, pageIndex: Int): PageResult {
        val expected = params(request(query, pageIndex).url)!!
        if (params(capture.documentUrl) != expected) return invalid()
        val dom = Jsoup.parse(capture.html ?: return invalid())
        if (dom.select("iframe[src*=challenges.cloudflare.com], #challenge-form, .g-recaptcha").isNotEmpty() ||
            Regex("請完成驗證|驗證您是人類|人機驗證|verify you are human", RegexOption.IGNORE_CASE).containsMatchIn(dom.text())) {
            return PageResult(PageKind.CHALLENGE, emptyList(), emptyList(), emptyMap(), NextPage.UNKNOWN, "challenge")
        }
        if (capture.statusCode != null && capture.statusCode !in 200..299) return invalid()
        val cards = dom.select("section.search-obj")
        if (cards.isEmpty()) return invalid()
        val rawKeys = mutableListOf<String>()
        val listings = mutableListOf<ProbeListing>()
        val excluded = linkedMapOf<String, String>()
        for (card in cards) {
            val item = listing(card, query) ?: return invalid("parse_error")
            if (item.sourceListingKey in rawKeys) continue
            rawKeys.add(item.sourceListingKey)
            val typeNode = card.selectFirst(".info__detail-info ul li:first-child")
            val type = typeNode?.text()?.takeIf { it.isNotBlank() && !typeNode.hasClass("info__floor") &&
                !Regex("(?:[0-9]+房(?:[0-9]+廳)?(?:[0-9]+衛)?|[0-9]+(?:\\.[0-9]+)?\\s*年)").matches(it) }
            val reason = if (type != null && type !in residentialTypes) "non_residential" else exclusionReason(item, query)
            if (reason == null) listings.add(item) else excluded[item.sourceListingKey] = reason
        }
        // shortcut: fixtures have no reliable pagination/empty marker; add only after live evidence.
        return PageResult(PageKind.RESULTS, rawKeys, listings, excluded, NextPage.UNKNOWN, "unknown")
    }

    private fun params(url: String): Map<String, String>? {
        val uri = secureUri(url)?.takeIf { it.host == "www.rakuya.com.tw" && it.path == "/sell/result" } ?: return null
        return try {
            val values = linkedMapOf<String, String>()
            for (part in (uri.rawQuery ?: return null).split('&')) {
                val key = URLDecoder.decode(part.substringBefore('='), "UTF-8")
                val value = URLDecoder.decode(part.substringAfter('=', ""), "UTF-8")
                if (values.put(key, value) != null) return null
            }
            values
        } catch (_: IllegalArgumentException) { null }
    }

    private fun listing(card: Element, query: ProbeQuery): ProbeListing? {
        val id = card.attr("data-ehid").trim().takeIf(idPattern::matches) ?: return null
        val price = decimal(text(card, ".info__price--total b"))?.takeIf { it.signum() > 0 } ?: return null
        val title = text(card, ".card__head h2")
        val location = text(card, ".info__geo--area")
        val address = text(card, ".info__geo--community") ?: location
        val city = location?.let { Regex("^([\\p{IsHan}]{2,3}[市縣])").find(it)?.value }
        val district = location?.removePrefix(city ?: "")?.trim()?.let {
            Regex("^([\\p{IsHan}]{2,4}區)").find(it)?.value
        }
        val floor = text(card, ".info__floor")
        val age = card.select(".info__detail-info ul li").firstNotNullOfOrNull {
            val value = Regex("^([0-9]+(?:\\.[0-9]+)?)\\s*年$").matchEntire(it.text())?.groupValues?.get(1)
            try { decimal(value)?.setScale(0, RoundingMode.HALF_EVEN)?.intValueExact() }
            catch (_: ArithmeticException) { null }
        }
        val spaces = card.select(".info__spaceList li").map { it.text() }
        fun area(label: String) = spaces.firstNotNullOfOrNull {
            Regex("^$label\\s*([0-9]+(?:\\.[0-9]+)?)\\s*坪$").matchEntire(it)?.groupValues?.get(1)
                ?.toBigDecimalOrNull()?.takeIf { amount -> amount.signum() > 0 }
        }
        val area = area("主建") ?: area("總建")
        val hasParking = card.select(".group__tags span.is--feature").any { it.text() in setOf("平面車位", "機械車位", "車位") }
        val image = card.selectFirst(".card__photo img[src]")?.attr("src")?.substringBefore('?')
            ?.takeIf { allowedImageUrl(site, it) }
        val unitPrice = area?.let { price.divide(it, 2, RoundingMode.HALF_EVEN) }
        val unknown = mutableSetOf("postedDate", "typeCodes", "useCode", "areaBasis")
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
            floor, age, hasParking, price, unitPrice, site, id, title ?: "", "$baseUrl/sell_item/info?ehid=$id",
            null, image, unknown)
    }

    private fun text(card: Element, selector: String) = card.selectFirst(selector)?.text()?.takeIf { it.isNotBlank() }
    private fun decimal(value: String?) = value?.takeIf(numberPattern::matches)?.replace(",", "")?.toBigDecimalOrNull()
    private fun invalid(evidence: String = "invalid_structure") =
        PageResult(PageKind.INVALID, emptyList(), emptyList(), emptyMap(), NextPage.UNKNOWN, evidence)
}
