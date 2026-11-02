package tw.houselens.probe

import kotlinx.serialization.json.*

internal fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.let {
    require(it.isString); it.content
}
private fun JsonObject.optionalText(key: String) = if (getValue(key) == JsonNull) null else text(key)
private fun JsonObject.number(key: String) = getValue(key).jsonPrimitive.int
private fun JsonObject.optionalNumber(key: String) = if (getValue(key) == JsonNull) null else number(key)
private fun JsonObject.optionalLong(key: String) = if (getValue(key) == JsonNull) null else getValue(key).jsonPrimitive.long
private fun JsonObject.flag(key: String) = if (getValue(key) == JsonNull) null else getValue(key).jsonPrimitive.boolean
private fun strings(values: Iterable<String>) = JsonArray(values.map(::JsonPrimitive))
private fun JsonObject.stringList(key: String) = getValue(key).jsonArray.map { it.jsonPrimitive.content }

internal fun queryJson(q: ProbeQuery) = buildJsonObject {
    put("site", q.site.name); put("city", q.city); put("district", q.district)
    put("maxTotalPrice", q.maxTotalPrice.toString()); put("minSizePing", q.minSizePing.toString())
    put("rooms", q.rooms); put("typeCodes", q.typeCodes); put("useCode", q.useCode)
    put("maxAgeYears", q.maxAgeYears); put("parkingCodes", q.parkingCodes)
}

private fun queryFromJson(o: JsonObject) = ProbeQuery(Site.valueOf(o.text("site")), o.text("city"), o.text("district"),
    o.text("maxTotalPrice").toBigDecimal(), o.text("minSizePing").toBigDecimal(), o.text("rooms"),
    o.text("typeCodes"), o.text("useCode"), o.number("maxAgeYears"), o.text("parkingCodes"))

internal fun deviceJson(d: DeviceSnapshot) = buildJsonObject {
    put("model", d.model); put("androidVersion", d.androidVersion); put("sdkInt", d.sdkInt)
    put("buildId", d.buildId); put("securityPatch", d.securityPatch)
    put("webViewPackage", d.webViewPackage); put("webViewVersion", d.webViewVersion)
    put("network", d.network); put("batteryPercent", d.batteryPercent); put("powerSaveMode", d.powerSaveMode)
    put("batteryOptimizationExempt", d.batteryOptimizationExempt); put("thermalStatus", d.thermalStatus)
}

private fun deviceFromJson(o: JsonObject) = DeviceSnapshot(o.optionalText("model"), o.optionalText("androidVersion"),
    o.optionalNumber("sdkInt"), o.optionalText("buildId"), o.optionalText("securityPatch"),
    o.optionalText("webViewPackage"), o.optionalText("webViewVersion"), o.optionalText("network"),
    o.optionalNumber("batteryPercent"), o.flag("powerSaveMode"), o.flag("batteryOptimizationExempt"), o.optionalNumber("thermalStatus"))

internal fun recordJson(r: RunRecord) = buildJsonObject {
    put("schemaVersion", 1); put("runId", r.runId); put("processId", r.processId)
    put("query", queryJson(r.query)); put("mode", r.mode.name); put("state", r.state.name)
    put("device", deviceJson(r.deviceSnapshot)); put("evidenceSource", r.evidenceSource.name)
}

internal fun recordFromJson(o: JsonObject): RunRecord {
    require(o.number("schemaVersion") == 1)
    return RunRecord(o.text("runId"), o.text("processId"), queryFromJson(o.getValue("query").jsonObject),
        RunMode.valueOf(o.text("mode")), RunState.valueOf(o.text("state")), deviceFromJson(o.getValue("device").jsonObject),
        EvidenceSource.valueOf(o.text("evidenceSource")))
}

private fun listingJson(p: ProbeListing) = buildJsonObject {
    put("city", p.city); put("district", p.district); put("address", p.address)
    put("areaPing", p.areaPing.toString()); put("floor", p.floor); put("ageYears", p.ageYears)
    put("hasParking", p.hasParking); put("totalPrice", p.totalPrice.toString()); put("unitPrice", p.unitPrice?.toString())
    put("sourceSite", p.sourceSite.name); put("sourceListingKey", p.sourceListingKey)
    put("title", p.title); put("url", p.url); put("postedDate", p.postedDate); put("imageUrl", p.imageUrl)
    put("unknownFields", strings(p.unknownFields.sorted()))
}

private fun listingFromJson(o: JsonObject) = ProbeListing(o.text("city"), o.text("district"), o.optionalText("address"),
    o.text("areaPing").toBigDecimal(), o.optionalText("floor"), o.optionalNumber("ageYears"),
    o.getValue("hasParking").jsonPrimitive.boolean, o.text("totalPrice").toBigDecimal(), o.optionalText("unitPrice")?.toBigDecimal(),
    Site.valueOf(o.text("sourceSite")), o.text("sourceListingKey"), o.text("title"), o.text("url"),
    o.optionalText("postedDate"), o.optionalText("imageUrl"), o.stringList("unknownFields").toSet())

internal fun pageJson(p: PageResult) = buildJsonObject {
    put("kind", p.kind.name); put("rawKeys", strings(p.rawKeys)); put("listings", JsonArray(p.listings.map(::listingJson)))
    put("excluded", JsonObject(p.excluded.mapValues { JsonPrimitive(it.value) }))
    put("next", p.next.name); put("evidence", p.evidence)
}

internal fun pageFromJson(o: JsonObject) = PageResult(PageKind.valueOf(o.text("kind")), o.stringList("rawKeys"),
    o.getValue("listings").jsonArray.map { listingFromJson(it.jsonObject) },
    o.getValue("excluded").jsonObject.mapValues { it.value.jsonPrimitive.content },
    NextPage.valueOf(o.text("next")), o.text("evidence"))

internal fun eventJson(e: ProbeEvent) = buildJsonObject {
    put("runId", e.runId); put("processId", e.processId); put("site", e.site.name); put("pageIndex", e.pageIndex)
    put("kind", e.kind.name); put("utc", e.utc); put("elapsedMs", e.elapsedMs); put("navigationId", e.navigationId)
    put("rawCount", e.rawCount); put("acceptedCount", e.acceptedCount); put("networkObserved", e.networkObserved)
    put("reason", e.reason?.name); put("evidenceSource", e.evidenceSource.name); put("newRawCount", e.newRawCount)
}

internal fun eventFromJson(o: JsonObject) = ProbeEvent(o.text("runId"), o.text("processId"), Site.valueOf(o.text("site")),
    o.optionalNumber("pageIndex"), ProbeEventKind.valueOf(o.text("kind")), o.text("utc"), o.getValue("elapsedMs").jsonPrimitive.long,
    o.optionalLong("navigationId"), o.optionalNumber("rawCount"), o.optionalNumber("acceptedCount"), o.flag("networkObserved"),
    o.optionalText("reason")?.let(ProbeReason::valueOf), EvidenceSource.valueOf(o.text("evidenceSource")), o.optionalNumber("newRawCount"))

internal fun sampleJson(pages: Map<Int, PageResult>) = buildJsonObject {
    put("pages", JsonArray(pages.map { (index, page) -> buildJsonObject {
        put("pageIndex", index); put("rawKeys", strings(page.rawKeys)); put("evidence", page.evidence)
        put("excluded", JsonObject(page.excluded.mapValues { JsonPrimitive(it.value) }))
        put("listings", JsonArray(page.listings.map { p -> buildJsonObject {
            put("sourceSite", p.sourceSite.name); put("sourceListingKey", p.sourceListingKey)
            put("city", p.city); put("district", p.district)
            put("totalPrice", p.totalPrice.toString()); put("areaPing", p.areaPing.toString())
            put("unknownFields", strings(p.unknownFields.sorted()))
        } }))
    } }))
}
