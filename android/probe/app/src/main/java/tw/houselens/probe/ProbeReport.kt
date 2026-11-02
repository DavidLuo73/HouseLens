package tw.houselens.probe

private val pageStages = listOf(ProbeEventKind.REQUEST_STARTED, ProbeEventKind.RESPONSE_RECEIVED,
    ProbeEventKind.PAGE_PARSED, ProbeEventKind.PAGE_SAVED)

private data class BackgroundWindow(val start: Int, val end: Int, val startMs: Long, val endMs: Long) {
    fun contains(index: Int) = index in start until end
}

fun backgroundVerdict(events: List<ProbeEvent>, mode: RunMode): BackgroundVerdict {
    if (mode == RunMode.FOREGROUND) return BackgroundVerdict.NOT_TESTED
    val first = events.firstOrNull() ?: return BackgroundVerdict.INCONCLUSIVE
    if (events.any { it.evidenceSource != EvidenceSource.LIVE || it.runId != first.runId ||
            it.processId != first.processId || it.site != first.site || it.elapsedMs < 0 ||
            it.kind == ProbeEventKind.EVIDENCE_INCOMPLETE } ||
        events.zipWithNext().any { (a, b) -> a.elapsedMs > b.elapsedMs }
    ) return BackgroundVerdict.INCONCLUSIVE
    if (events.any { it.kind in setOf(ProbeEventKind.WAITING_FOR_USER, ProbeEventKind.RESUMED, ProbeEventKind.RUN_CANCELLED,
            ProbeEventKind.RUN_FAILED, ProbeEventKind.RUN_INTERRUPTED) }
    ) return BackgroundVerdict.FAILED
    if (events.any { it.kind in setOf(ProbeEventKind.RUN_TRUNCATED, ProbeEventKind.RUN_INCONCLUSIVE) }) {
        return BackgroundVerdict.INCONCLUSIVE
    }

    var foreground: Boolean? = null
    var screenOn: Boolean? = null
    var start: Int? = null
    val windows = mutableListOf<BackgroundWindow>()
    events.forEachIndexed { index, event ->
        when (event.kind) {
            ProbeEventKind.APP_FOREGROUND -> foreground = true
            ProbeEventKind.APP_BACKGROUND -> foreground = false
            ProbeEventKind.SCREEN_ON -> screenOn = true
            ProbeEventKind.SCREEN_OFF -> screenOn = false
            else -> Unit
        }
        val eligible = foreground == false && when (mode) {
            RunMode.BACKGROUND_10M -> screenOn == true
            RunMode.LOCKED_10M -> screenOn == false
            else -> true
        }
        if (eligible && start == null) start = index
        if (!eligible && start != null) {
            val began = start!!
            windows += BackgroundWindow(began, index, events[began].elapsedMs, event.elapsedMs)
            start = null
        }
    }
    start?.let { windows += BackgroundWindow(it, events.size, events[it].elapsedMs, events.last().elapsedMs) }

    val groups = events.indices.filter { events[it].kind in pageStages && events[it].pageIndex != null && events[it].navigationId != null }
        .groupBy { events[it].pageIndex to events[it].navigationId }
    for (indices in groups.values) {
        val request = indices.singleOrNull { events[it].kind == ProbeEventKind.REQUEST_STARTED } ?: continue
        val response = indices.singleOrNull { events[it].kind == ProbeEventKind.RESPONSE_RECEIVED } ?: continue
        if (response <= request || events[response].networkObserved != true) continue
        val window = windows.firstOrNull { it.contains(request) } ?: continue
        if (indices.any { index -> events[index].kind in listOf(ProbeEventKind.PAGE_PARSED, ProbeEventKind.PAGE_SAVED) &&
                index > response && !window.contains(index) &&
                (request + 1..index).any { events[it].kind == ProbeEventKind.APP_FOREGROUND } }
        ) return BackgroundVerdict.FAILED
    }

    val successful = groups.values.filter { indices ->
        if (indices.map { events[it].kind } != pageStages) return@filter false
        val response = events[indices[1]]
        val parsed = events[indices[2]]
        val saved = events[indices[3]]
        response.networkObserved == true && (saved.newRawCount ?: 0) > 0 &&
            (saved.rawCount ?: 0) >= saved.newRawCount!! && parsed.rawCount == saved.rawCount &&
            parsed.acceptedCount == saved.acceptedCount && saved.acceptedCount != null &&
            saved.acceptedCount in 0..saved.rawCount!! && saved.pageIndex!! >= 0 && saved.navigationId!! >= 0
    }.groupBy { events[it.first()].pageIndex }.values.filter { it.size == 1 }.map { it.single() }
        .groupBy { events[it.first()].navigationId }.values.filter { it.size == 1 }.map { it.single() }

    if (mode == RunMode.FULL) {
        val completed = events.indexOfLast { it.kind == ProbeEventKind.RUN_COMPLETED }
        return if (completed >= 0 && successful.any { stages -> stages.last() < completed &&
                windows.any { window -> stages.all(window::contains) } }) BackgroundVerdict.PASSED
        else BackgroundVerdict.INCONCLUSIVE
    }
    val window = windows.firstOrNull() ?: return BackgroundVerdict.INCONCLUSIVE
    if (window.endMs - window.startMs < 600_000) return BackgroundVerdict.INCONCLUSIVE
    val inWindow = successful.filter { stages -> stages.all(window::contains) &&
        events[stages.last()].elapsedMs - window.startMs <= 600_000 }
    val slots = listOf(60_000L until 300_000L, 300_000L until 540_000L, 540_000L until 600_000L)
    return if (slots.all { slot -> inWindow.any { events[it.first()].elapsedMs - window.startMs in slot } }) {
        BackgroundVerdict.PASSED
    } else BackgroundVerdict.INCONCLUSIVE
}
