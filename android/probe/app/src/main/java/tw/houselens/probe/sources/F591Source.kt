package tw.houselens.probe.sources

import java.math.BigDecimal
import java.math.MathContext
import java.net.URLDecoder
import kotlinx.serialization.json.*
import org.jsoup.Jsoup
import tw.houselens.probe.*

object F591Source : SourceAdapter {
    override val site = Site.F591
    override val maxPages = 40
    private val sections = mapOf("中和區" to "38", "永和區" to "37", "新店區" to "34")
    private val idPattern = Regex("[A-Za-z0-9_-]{1,80}")
    private val numberPattern = Regex("(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]+)?")

    override fun request(query: ProbeQuery, pageIndex: Int): PageRequest {
        validateQuery(query)
        require(query.site == site && pageIndex in 0 until maxPages) { "invalid_source_page" }
        require(query.typeCodes in setOf("", "R1,R2") && query.useCode in setOf("", "1")) { "unsupported_query_codes" }
        val parking = query.parkingCodes.split(',').filter(String::isNotEmpty).map {
            when (it.uppercase()) { "PF", "1" -> 1; "PM", "2" -> 2; "3" -> 3
                else -> throw IllegalArgumentException("unsupported_parking") }
        }.toSortedSet()
        if (1 in parking && 2 in parking) parking.add(3)
        val rooms = query.rooms.split(',').filter(String::isNotEmpty).map {
            require(Regex("[1-9][0-9]*~?").matches(it)) { "unsupported_rooms" }
            (it.removeSuffix("~").toIntOrNull() ?: throw IllegalArgumentException("unsupported_rooms")).coerceAtMost(5)
        }.toSortedSet()
        val url = buildString {
            append("https://sale.591.com.tw/?shType=list&type=2&regionid=3&section=${sections.getValue(query.district)}")
            if (query.maxTotalPrice.signum() > 0) append("&price=\$_\$${query.maxTotalPrice.stripTrailingZeros().toPlainString()}")
            if (query.maxAgeYears > 0) append("&houseage=\$_\$${query.maxAgeYears}")
            if (parking.isNotEmpty()) append("&parking=${parking.joinToString(",")}")
            if (query.minSizePing.signum() > 0) append("&area=\$${query.minSizePing.stripTrailingZeros().toPlainString()}_\$")
            if (rooms.isNotEmpty()) append("&pattern=${rooms.joinToString(",")}")
            if (pageIndex > 0) append("&firstRow=${pageIndex * 30}")
        }
        return PageRequest(url, browser = true, warmupUrl = "https://sale.591.com.tw/", captureJson = true)
    }

    override fun parse(capture: RawCapture, query: ProbeQuery, pageIndex: Int): PageResult {
        val expected = params(request(query, pageIndex).url)!!
        val documentUri = secureUri(capture.documentUrl)
        if (documentUri?.host != "sale.591.com.tw") return invalid(ProbeReason.F591_DOCUMENT_URL)
        if (documentUri.path != "/") return invalid(ProbeReason.F591_DOCUMENT_PATH)
        val document = params(capture.documentUrl) ?: return invalid(ProbeReason.F591_DOCUMENT_QUERY)
        if (document != expected) return invalid(ProbeReason.F591_DOCUMENT_QUERY)
        val dom = capture.html?.let(Jsoup::parse)
        if (dom != null && (dom.select("iframe[src*=challenges.cloudflare.com], #challenge-form, .g-recaptcha").isNotEmpty() ||
                Regex("請完成驗證|驗證您是人類|人機驗證|verify you are human", RegexOption.IGNORE_CASE).containsMatchIn(dom.text()))) {
            return PageResult(PageKind.CHALLENGE, emptyList(), emptyList(), emptyMap(), NextPage.UNKNOWN, "challenge")
        }
        if (capture.statusCode != null && capture.statusCode !in 200..299) return invalid(ProbeReason.F591_HTTP_STATUS)
        val matching = capture.json.filter {
            val uri = secureUri(it.url)
            val p = params(it.url)
            uri != null && Regex("/(search|house|sale|list)(/|$|_)").containsMatchIn(uri.path) &&
                p != null && p["regionid"] == "3" && p["section"] == sections[query.district] &&
                p["firstRow"] == (pageIndex * 30).toString() &&
                listOf("type", "price", "houseage", "parking", "area", "pattern").all { key ->
                    key !in p || p[key] == expected[key]
                }
        }.map { it.body }.distinct()
        if (matching.size > 1) return invalid(ProbeReason.F591_JSON_CONFLICT)
        if (matching.isEmpty()) {
            if (capture.json.isNotEmpty()) return invalid(ProbeReason.F591_JSON_UNCORRELATED)
            if (dom == null) return invalid(ProbeReason.F591_NO_CONTENT)
            return parseHtml(dom, query)
        }
        return try { parseJson(Json.parseToJsonElement(matching.single()) as? JsonObject ?: return invalid(ProbeReason.F591_JSON_FORMAT), query, pageIndex) }
        catch (_: IllegalArgumentException) { invalid(ProbeReason.F591_JSON_FORMAT) }
    }

    private fun params(url: String): Map<String, String>? {
        val uri = secureUri(url)?.takeIf { it.host == "sale.591.com.tw" } ?: return null
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

    private fun parseJson(root: JsonObject, query: ProbeQuery, index: Int): PageResult {
        val totalElement = path(root, "data.total", "data.data.total", "total")
        val total = totalElement?.let {
            try { decimal(scalar(it))?.intValueExact() } catch (_: ArithmeticException) { null }
        }
        if (totalElement != null && (total == null || total < 0)) return invalid(ProbeReason.F591_JSON_TOTAL)
        if (total == 0) return PageResult(PageKind.EMPTY, emptyList(), emptyList(), emptyMap(), NextPage.END, "zero_total")
        val cards = path(root, "data.house_list", "data.data.house_list", "house_list", "data.items", "data.rows", "rows", "items") as? JsonArray
            ?: return invalid(ProbeReason.F591_JSON_LIST)
        if (cards.isEmpty() || cards.size > 30 ||
            (total != null && cards.size != minOf(30, total - index * 30))) return invalid(ProbeReason.F591_JSON_COUNT)
        val listings = mutableListOf<ProbeListing>()
        val rawKeys = mutableListOf<String>()
        val excluded = linkedMapOf<String, String>()
        for (element in cards) {
            val card = element as? JsonObject ?: return invalid(ProbeReason.F591_JSON_CARD)
            val item = listing(card, query) ?: return invalid(ProbeReason.F591_JSON_CARD)
            if (item.sourceListingKey in rawKeys) return invalid(ProbeReason.F591_JSON_DUPLICATE)
            rawKeys.add(item.sourceListingKey)
            val reason = if (text(card, "is_newhouse") in setOf("1", "true")) "unsupported_type" else exclusionReason(item, query)
            if (reason == null) listings.add(item) else excluded[item.sourceListingKey] = reason
        }
        val next = when { total == null -> NextPage.UNKNOWN; (index + 1) * 30 >= total -> NextPage.END; else -> NextPage.MORE }
        return PageResult(PageKind.RESULTS, rawKeys, listings, excluded, next, if (total == null) "unknown" else "total_count")
    }

    private fun listing(card: JsonObject, query: ProbeQuery): ProbeListing? {
        val idValue = listOf("id", "house_id", "houseid").firstNotNullOfOrNull { key ->
            card[key]?.takeUnless { it == JsonNull || it == JsonPrimitive("") }
        } as? JsonPrimitive ?: return null
        if (!idValue.isString && !Regex("[0-9]+").matches(idValue.content)) return null
        val id = idValue.content.takeIf(idPattern::matches) ?: return null
        val price = decimal(text(card, "price", "total_price", "totalPrice"))?.takeIf { it.signum() > 0 } ?: return null
        val address = text(card, "address", "addr", "location", "street_name")
        val district = text(card, "section_name", "section", "district")?.takeIf { it.endsWith("區") }
            ?: address?.let(::districtFromAddress) ?: ""
        val city = text(card, "city", "city_name", "region_name")?.takeIf { it.endsWith("市") || it.endsWith("縣") }
            ?: address?.let { Regex("^(.{2,3}[市縣])").find(it)?.value } ?: query.city
        val area = decimal(text(card, "area", "showarea", "building_size")?.removeSuffix("坪")?.trim())?.takeIf { it.signum() > 0 }
        val floor = text(card, "floor", "floorStr", "floor_str")
        val age = decimal(text(card, "houseage", "age", "ageText", "age_str")?.removeSuffix("年")?.trim())?.let {
            try { it.intValueExact() } catch (_: ArithmeticException) { null }
        }
        val parking = when (text(card, "hasParking", "parking", "has_parking")?.lowercase()) {
            "true", "1", "2", "3" -> true; "false", "0" -> false; else -> null
        }
        val title = text(card, "house_title", "title", "name")
        val unitPrice = decimal(text(card, "unitprice"))?.takeIf { it.signum() > 0 }
            ?: area?.let { price.divide(it, MathContext.DECIMAL128) }
        val image = imageUrl(card)
        val rawUrl = text(card, "detail_url", "url")
        val url = rawUrl?.let { safeDetailUrl(it) }
            ?: if (Regex("[0-9]+").matches(id)) "https://sale.591.com.tw/home/house/detail/2/$id.html" else ""
        val unknown = mutableSetOf("postedDate", "areaBasis")
        if (query.typeCodes.isNotEmpty()) unknown.add("typeCodes")
        if (query.useCode.isNotEmpty()) unknown.add("useCode")
        if (query.rooms.isNotEmpty()) unknown.add("rooms")
        if (query.parkingCodes.isNotEmpty()) unknown.add("parkingCodes")
        if (address == null) unknown.add("address")
        if (district.isEmpty()) unknown.add("district")
        if (area == null) unknown.add("areaPing")
        if (floor == null) unknown.add("floor")
        if (age == null) unknown.add("ageYears")
        if (parking == null) unknown.add("hasParking")
        if (unitPrice == null) unknown.add("unitPrice")
        if (title == null) unknown.add("title")
        if (image == null) unknown.add("imageUrl")
        if (url.isEmpty()) unknown.add("url")
        return ProbeListing(city, district, address, area ?: BigDecimal.ZERO, floor, age, parking ?: false,
            price, unitPrice, site, id, title ?: "", url, null, image, unknown)
    }

    private fun parseHtml(dom: org.jsoup.nodes.Document, query: ProbeQuery): PageResult {
        val cards = listOf("ul.houseList > li.clear", "li.houseList-item", "div.house-item", "div.list-item")
            .map { dom.select(it) }.firstOrNull { it.isNotEmpty() } ?: return invalid(ProbeReason.F591_HTML_NO_CARDS)
        val items = mutableListOf<ProbeListing>()
        val keys = mutableListOf<String>()
        val excluded = linkedMapOf<String, String>()
        for (card in cards) {
            val href = card.select("a[href]").map { it.attr("href") }.firstOrNull {
                Regex("/(?:home/house/)?detail/2/[0-9]+\\.html$").containsMatchIn(it)
            } ?: return invalid(ProbeReason.F591_HTML_CARD)
            val url = safeDetailUrl(href) ?: return invalid(ProbeReason.F591_HTML_CARD)
            val id = url.substringAfterLast('/').removeSuffix(".html")
            val title = card.selectFirst("a.houseLeft h3, h3, h2")?.text() ?: return invalid(ProbeReason.F591_HTML_CARD)
            val price = card.selectFirst("b.priceNum, .price b, .price")?.text() ?: return invalid(ProbeReason.F591_HTML_CARD)
            val address = card.selectFirst(".add, .address")?.text()
            val details = card.select("li").map { it.ownText() }
            val obj = buildJsonObject {
                put("id", id); put("title", title); put("price", price); put("url", url)
                if (address != null) put("address", address)
                details.firstOrNull { it.endsWith("坪") }?.let { put("showarea", it) }
                details.firstOrNull { it.endsWith("年") }?.let { put("houseage", it) }
                details.firstOrNull { it.contains("樓") }?.let { put("floor", it) }
            }
            val item = listing(obj, query) ?: return invalid(ProbeReason.F591_HTML_CARD)
            if (id in keys) continue
            keys.add(id)
            val reason = exclusionReason(item, query)
            if (reason == null) items.add(item) else excluded[id] = reason
        }
        // shortcut: DOM cards do not prove search completeness; require correlated BFF before declaring END.
        return PageResult(PageKind.RESULTS, keys, items, excluded, NextPage.UNKNOWN, "html_fallback")
    }

    private fun safeDetailUrl(raw: String): String? {
        val url = if (raw.startsWith('/')) "https://sale.591.com.tw$raw" else raw
        return url.takeIf { secureUri(it)?.host == "sale.591.com.tw" }
    }

    private fun imageUrl(card: JsonObject): String? {
        fun safe(value: String?) = value?.let { if (it.startsWith("//")) "https:$it" else it }
            ?.takeIf { allowedImageUrl(site, it) }
        for (key in listOf("imgs", "img_list", "photos")) {
            for (image in card[key] as? JsonArray ?: continue) {
                val value = if (image is JsonObject) {
                    if (text(image, "type", "media_type") == "video") text(image, "thumbnail", "poster", "cover", "img", "thumb")
                    else text(image, "src", "url", "path")
                } else scalar(image)
                safe(value)?.let { return it }
            }
        }
        return safe(text(card, "photo_url", "cover_img", "img", "main_img", "photo_path", "thumbnail_url", "cover", "thumb_url"))
    }

    private fun districtFromAddress(address: String): String? = Regex("^(.{2,3}[市縣])?([\\p{IsHan}]{2,4}區)")
        .find(address)?.groupValues?.get(2)
    private fun decimal(value: String?): BigDecimal? = value?.trim()?.takeIf(numberPattern::matches)?.replace(",", "")?.toBigDecimalOrNull()
    private fun scalar(value: JsonElement?): String? = (value as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    private fun text(obj: JsonObject, vararg keys: String): String? = keys.firstNotNullOfOrNull { scalar(obj[it]) }
    private fun path(obj: JsonObject, vararg paths: String): JsonElement? = paths.firstNotNullOfOrNull { path ->
        path.split('.').fold(obj as JsonElement?) { value, key -> (value as? JsonObject)?.get(key) }
    }
    private fun invalid(reason: ProbeReason) = PageResult(PageKind.INVALID,
        emptyList(), emptyList(), emptyMap(), NextPage.UNKNOWN, reason.name)
}
