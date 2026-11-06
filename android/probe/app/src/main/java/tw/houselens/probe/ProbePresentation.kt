package tw.houselens.probe

internal fun thumbnailSampleSize(width: Int, height: Int): Int {
    require(width > 0 && height > 0 && width.toLong() * height <= 50_000_000) { "image_dimensions" }
    var sample = 1
    while (maxOf(width, height) / sample > 512) sample *= 2
    return sample
}

internal fun backgroundSavedPages(events: List<ProbeEvent>): Int {
    val first = events.firstOrNull() ?: return 0
    if (events.any { it.evidenceSource != EvidenceSource.LIVE || it.runId != first.runId ||
            it.processId != first.processId || it.site != first.site || it.kind == ProbeEventKind.EVIDENCE_INCOMPLETE ||
            it.reason == ProbeReason.EVIDENCE_LOST } ||
        events.zipWithNext().any { (a, b) -> a.elapsedMs > b.elapsedMs }) return 0
    val stages = listOf(ProbeEventKind.REQUEST_STARTED, ProbeEventKind.RESPONSE_RECEIVED,
        ProbeEventKind.PAGE_PARSED, ProbeEventKind.PAGE_SAVED)
    val requests = events.filter { it.kind == ProbeEventKind.REQUEST_STARTED }
    return events.withIndex().filter { it.value.kind in stages }.groupBy { it.value.pageIndex to it.value.navigationId }
        .values.filter { group ->
            group.map { it.value.kind } == stages && group[1].value.networkObserved == true &&
                requests.count { it.navigationId == group.first().value.navigationId } == 1 &&
                requests.count { it.pageIndex == group.first().value.pageIndex } == 1 &&
                (group.last().value.newRawCount ?: 0) > 0 &&
                events.take(group.first().index).lastOrNull { it.kind in setOf(ProbeEventKind.APP_BACKGROUND, ProbeEventKind.APP_FOREGROUND) }?.kind == ProbeEventKind.APP_BACKGROUND &&
                events.subList(group.first().index, group.last().index + 1).none { it.kind in setOf(
                    ProbeEventKind.APP_FOREGROUND, ProbeEventKind.WAITING_FOR_USER, ProbeEventKind.RESUMED) }
        }.map { it.last().value.pageIndex }.distinct().size
}

internal fun localCaptureChecks(capture: RawCapture): List<Pair<String, Boolean>> {
    val base = "https://$SELF_CHECK_HOST"
    val dom = org.jsoup.Jsoup.parse(capture.html.orEmpty())
    fun has(kind: String) = capture.json.any { it.url == "$base/probe/$kind" &&
        it.body == "{\"source\":\"local\",\"case\":\"$kind\"}" }
    return listOf(
        "LOCAL_SELF_CHECK：fetch" to has("fetch"), "LOCAL_SELF_CHECK：XHR" to has("xhr"),
        "LOCAL_SELF_CHECK：iframe 訊息確實送出並拒絕" to (dom.select("[data-frame-sent=true]").isNotEmpty() && capture.json.none { it.url.endsWith("/probe/frame") }),
        "LOCAL_SELF_CHECK：延遲舊 ID 重放並拒絕" to (dom.select("[data-stale-sent=true]").isNotEmpty() && capture.json.none { it.url.endsWith("/probe/stale") }),
        "LOCAL_SELF_CHECK：不列為實站網路證據" to !capture.networkObserved,
    )
}
