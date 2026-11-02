package tw.houselens.probe

import org.junit.Assert.assertEquals
import org.junit.Test

class ProbeReportTest {
    private val runId = "11111111-1111-4111-8111-111111111111"
    private val processId = "22222222-2222-4222-8222-222222222222"

    private fun event(kind: ProbeEventKind, time: Long, page: Int? = null) = ProbeEvent(
        runId, processId, Site.F591, page, kind, "2026-11-03T00:00:00Z", time,
        page?.toLong(), page?.let { 10 }, page?.let { 0 },
        if (kind == ProbeEventKind.RESPONSE_RECEIVED) true else null,
        null, EvidenceSource.LIVE, if (kind == ProbeEventKind.PAGE_SAVED) 10 else null,
    )

    private fun page(index: Int, time: Long) = listOf(
        event(ProbeEventKind.REQUEST_STARTED, time, index),
        event(ProbeEventKind.RESPONSE_RECEIVED, time + 100, index),
        event(ProbeEventKind.PAGE_PARSED, time + 200, index),
        event(ProbeEventKind.PAGE_SAVED, time + 300, index),
    )

    private fun timeline(locked: Boolean = false): List<ProbeEvent> = buildList {
        add(event(ProbeEventKind.APP_FOREGROUND, 0))
        add(event(ProbeEventKind.SCREEN_ON, 0))
        add(event(ProbeEventKind.APP_BACKGROUND, 1000))
        if (locked) add(event(ProbeEventKind.SCREEN_OFF, 1000))
        addAll(page(1, 61_000))
        addAll(page(2, 301_000))
        addAll(page(3, 541_000))
        add(event(ProbeEventKind.HEARTBEAT, 601_000))
        add(event(ProbeEventKind.RUN_COMPLETED, 601_000))
    }

    @Test fun foregroundDoesNotClaimBackgroundEvidence() {
        assertEquals(BackgroundVerdict.NOT_TESTED, backgroundVerdict(emptyList(), RunMode.FOREGROUND))
    }

    @Test fun threeNewPagesPassEvenWhenEveryListingWasFiltered() {
        assertEquals(BackgroundVerdict.PASSED, backgroundVerdict(timeline(), RunMode.BACKGROUND_10M))
        assertEquals(BackgroundVerdict.PASSED, backgroundVerdict(timeline(true), RunMode.LOCKED_10M))
    }

    @Test fun heartbeatCacheAndRepeatedDataAreNotSuccess() {
        val events = timeline()
        for (candidate in listOf(
            events.filter { it.pageIndex == null },
            events.map { if (it.kind == ProbeEventKind.RESPONSE_RECEIVED) it.copy(networkObserved = false) else it },
            events.map { if (it.kind == ProbeEventKind.PAGE_SAVED) it.copy(newRawCount = 0) else it },
            events.filter { it.pageIndex != 2 },
        )) assertEquals(BackgroundVerdict.INCONCLUSIVE, backgroundVerdict(candidate, RunMode.BACKGROUND_10M))
    }

    @Test fun foregroundOnlyParsingOrSavingFails() {
        for (kind in listOf(ProbeEventKind.RESPONSE_RECEIVED, ProbeEventKind.PAGE_PARSED, ProbeEventKind.PAGE_SAVED)) {
            val events = timeline().toMutableList()
            val index = events.indexOfFirst { it.pageIndex == 3 && it.kind == kind }
            events.add(index, event(ProbeEventKind.APP_FOREGROUND, events[index].elapsedMs))
            assertEquals(BackgroundVerdict.FAILED, backgroundVerdict(events, RunMode.BACKGROUND_10M))
            assertEquals(BackgroundVerdict.FAILED, backgroundVerdict(events, RunMode.FULL))
        }
    }

    @Test fun screenMismatchAndShortOrSplitIntervalsAreInconclusive() {
        assertEquals(BackgroundVerdict.INCONCLUSIVE, backgroundVerdict(timeline(), RunMode.LOCKED_10M))
        assertEquals(BackgroundVerdict.INCONCLUSIVE, backgroundVerdict(timeline(true), RunMode.BACKGROUND_10M))
        assertEquals(BackgroundVerdict.INCONCLUSIVE, backgroundVerdict(timeline().map {
            if (it.elapsedMs == 601_000L) it.copy(elapsedMs = 600_999) else it
        }, RunMode.BACKGROUND_10M))
        val split = timeline().toMutableList()
        split.add(7, event(ProbeEventKind.SCREEN_OFF, 100_000))
        split.add(8, event(ProbeEventKind.SCREEN_ON, 200_000))
        assertEquals(BackgroundVerdict.INCONCLUSIVE, backgroundVerdict(split, RunMode.BACKGROUND_10M))
    }

    @Test fun mixedIdentityClockAndEvidenceCannotPass() {
        val events = timeline()
        for (changed in listOf(
            events[5].copy(processId = "33333333-3333-4333-8333-333333333333"),
            events[5].copy(runId = "33333333-3333-4333-8333-333333333333"),
            events[5].copy(site = Site.Rakuya), events[5].copy(elapsedMs = 0),
            events[5].copy(evidenceSource = EvidenceSource.LOCAL_SELF_CHECK),
            events[5].copy(evidenceSource = EvidenceSource.SIMULATED),
        )) {
            assertEquals(BackgroundVerdict.INCONCLUSIVE, backgroundVerdict(events.toMutableList().apply { this[5] = changed }, RunMode.BACKGROUND_10M))
        }
        assertEquals(BackgroundVerdict.INCONCLUSIVE, backgroundVerdict(events.filter { it.kind != ProbeEventKind.SCREEN_ON }, RunMode.BACKGROUND_10M))
        assertEquals(BackgroundVerdict.INCONCLUSIVE, backgroundVerdict(events + event(ProbeEventKind.EVIDENCE_INCOMPLETE, 601_000), RunMode.BACKGROUND_10M))
    }

    @Test fun stagesCannotBeStitchedAcrossNavigationsOrReusedPages() {
        val events = timeline()
        assertEquals(BackgroundVerdict.INCONCLUSIVE, backgroundVerdict(events.map {
            if (it.pageIndex == 2 && it.kind == ProbeEventKind.PAGE_SAVED) it.copy(navigationId = 99) else it
        }, RunMode.BACKGROUND_10M))
        assertEquals(BackgroundVerdict.INCONCLUSIVE, backgroundVerdict(events.map {
            if (it.pageIndex == 2) it.copy(pageIndex = 1, navigationId = 1) else it
        }, RunMode.BACKGROUND_10M))
        assertEquals(BackgroundVerdict.INCONCLUSIVE, backgroundVerdict(events.map {
            if (it.pageIndex == 2) it.copy(navigationId = 1) else it
        }, RunMode.BACKGROUND_10M))
        assertEquals(BackgroundVerdict.INCONCLUSIVE, backgroundVerdict(events.filter { it.kind != ProbeEventKind.PAGE_SAVED }, RunMode.BACKGROUND_10M))
    }

    @Test fun failureCancellationAndHumanInterventionCannotBecomePasses() {
        for (kind in listOf(ProbeEventKind.RUN_FAILED, ProbeEventKind.RUN_CANCELLED, ProbeEventKind.RUN_INTERRUPTED, ProbeEventKind.WAITING_FOR_USER, ProbeEventKind.RESUMED)) {
            assertEquals(BackgroundVerdict.FAILED, backgroundVerdict(timeline() + event(kind, 601_000), RunMode.BACKGROUND_10M))
        }
    }

    @Test fun lateSavesAndLaterReplacementIntervalsCannotPass() {
        val late = timeline().map {
            if (it.pageIndex == 3 && it.kind == ProbeEventKind.PAGE_SAVED) it.copy(elapsedMs = 601_001)
            else if (it.elapsedMs == 601_000L) it.copy(elapsedMs = 602_000) else it
        }
        assertEquals(BackgroundVerdict.INCONCLUSIVE, backgroundVerdict(late, RunMode.BACKGROUND_10M))
        val replaced = timeline().take(7) + event(ProbeEventKind.APP_FOREGROUND, 100_000) +
            event(ProbeEventKind.APP_BACKGROUND, 200_000) + page(4, 260_000) + page(5, 500_000) + page(6, 740_000) +
            event(ProbeEventKind.RUN_COMPLETED, 800_000)
        assertEquals(BackgroundVerdict.INCONCLUSIVE, backgroundVerdict(replaced, RunMode.BACKGROUND_10M))
    }

    @Test fun fullRequiresCompletionAndAnEntireNewPageInBackground() {
        val events = timeline().take(7)
        assertEquals(BackgroundVerdict.INCONCLUSIVE, backgroundVerdict(events, RunMode.FULL))
        assertEquals(BackgroundVerdict.PASSED, backgroundVerdict(events + event(ProbeEventKind.RUN_COMPLETED, 62_000), RunMode.FULL))
        assertEquals(BackgroundVerdict.INCONCLUSIVE, backgroundVerdict(events + event(ProbeEventKind.RUN_TRUNCATED, 62_000), RunMode.FULL))
        assertEquals(BackgroundVerdict.INCONCLUSIVE, backgroundVerdict(timeline().filter { it.kind != ProbeEventKind.APP_BACKGROUND }, RunMode.FULL))
    }
}
