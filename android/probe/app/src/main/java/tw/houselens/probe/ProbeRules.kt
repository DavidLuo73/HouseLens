package tw.houselens.probe

private val queryCodeFormat = Regex("[\\p{L}\\p{N}_~\\-]+(?:,[\\p{L}\\p{N}_~\\-]+)*")

fun validateQuery(query: ProbeQuery) {
    require(query.city == "新北市" && query.district in setOf("中和區", "永和區", "新店區")) {
        "unsupported_location"
    }
    require(query.maxTotalPrice.signum() >= 0) { "invalid_max_price" }
    require(query.minSizePing.signum() >= 0) { "invalid_min_size" }
    require(query.maxAgeYears >= 0) { "invalid_max_age" }
    // Source adapters validate the meanings of their own codes before constructing requests.
    for (codes in listOf(query.rooms, query.typeCodes, query.useCode, query.parkingCodes)) {
        require(codes.length <= 128 && (codes.isEmpty() || queryCodeFormat.matches(codes))) {
            "invalid_query_codes"
        }
    }
}

// null means no proven exclusion; unknown fields remain unverified, not confirmed matches.
fun exclusionReason(item: ProbeListing, query: ProbeQuery): String? {
    if (item.sourceListingKey.isBlank()) return "missing_id"
    if (item.sourceSite != query.site) return "wrong_source"
    if ("totalPrice" in item.unknownFields) return "unknown_price"
    if (item.totalPrice.signum() <= 0) return "invalid_price"
    if (item.city != query.city) return "wrong_city"
    if (item.district != query.district) return "wrong_district"
    if (query.maxTotalPrice.signum() > 0 && item.totalPrice > query.maxTotalPrice) return "over_price"
    // Area definitions differ across sites (e.g. main vs total); minSizePing stays source-specific.
    if (query.maxAgeYears > 0 && "ageYears" !in item.unknownFields &&
        item.ageYears != null && item.ageYears > query.maxAgeYears
    ) return "over_age"
    if (query.parkingCodes.isNotEmpty() && "hasParking" !in item.unknownFields && !item.hasParking) {
        return "no_parking"
    }
    return null
}

fun nextDecision(
    page: PageResult,
    previousRawKeys: Set<String>,
    pageIndex: Int,
    pageLimit: Int,
): PageDecision {
    require(pageLimit > 0 && pageIndex >= 0 && pageIndex < pageLimit) { "invalid_page_bounds" }
    if (page.kind == PageKind.CHALLENGE) return PageDecision.WAIT_FOR_USER
    if (page.kind == PageKind.INVALID) return PageDecision.FAIL
    if (page.rawKeys.any { it.isBlank() } ||
        (page.kind == PageKind.RESULTS && page.rawKeys.isEmpty()) ||
        (page.kind == PageKind.EMPTY && (page.rawKeys.isNotEmpty() || page.listings.isNotEmpty()))
    ) return PageDecision.FAIL
    if (page.next == NextPage.END) {
        return if (page.evidence.isNotBlank()) PageDecision.COMPLETE else PageDecision.INCONCLUSIVE
    }
    if (pageIndex == pageLimit - 1) return PageDecision.TRUNCATE
    if (page.rawKeys.isEmpty() || page.rawKeys.all { it in previousRawKeys }) return PageDecision.INCONCLUSIVE
    return PageDecision.CONTINUE
}
