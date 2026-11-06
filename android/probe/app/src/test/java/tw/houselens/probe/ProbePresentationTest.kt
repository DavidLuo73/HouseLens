package tw.houselens.probe

import org.junit.Assert.*
import org.junit.Test

class ProbePresentationTest {
    private fun event(kind: ProbeEventKind, time: Long) = ProbeEvent("run", "process", Site.F591,
        0, kind, "2026-11-06T00:00:00Z", time, 1, 2, 1, true, null, EvidenceSource.LIVE, newRawCount = 2)
    private val events = listOf(event(ProbeEventKind.APP_BACKGROUND, 0),
        event(ProbeEventKind.REQUEST_STARTED, 1), event(ProbeEventKind.RESPONSE_RECEIVED, 2),
        event(ProbeEventKind.PAGE_PARSED, 3), event(ProbeEventKind.PAGE_SAVED, 4))
    @Test fun backgroundPageCountRequiresWholeLiveNewPageSequence() {
        assertEquals(1, backgroundSavedPages(events))
        assertEquals(0, backgroundSavedPages(events.map { it.copy(evidenceSource = EvidenceSource.LOCAL_SELF_CHECK) }))
        assertEquals(0, backgroundSavedPages(events.drop(2)))
        assertEquals(0, backgroundSavedPages(events.take(3) + event(ProbeEventKind.APP_FOREGROUND, 3) + events.drop(3)))
        assertEquals(0, backgroundSavedPages(events.map { it.copy(networkObserved = false) }))
        assertEquals(0, backgroundSavedPages(events.map { it.copy(newRawCount = 0) }))
    }
    @Test fun thumbnailSamplingIsBoundedAndRejectsInvalidDimensions() {
        assertEquals(1, thumbnailSampleSize(300, 200))
        assertEquals(8, thumbnailSampleSize(4000, 3000))
        assertThrows(IllegalArgumentException::class.java) { thumbnailSampleSize(0, 100) }
        assertThrows(IllegalArgumentException::class.java) { thumbnailSampleSize(100000, 100000) }
    }
    @Test fun reusedNavigationOrLostEvidenceCannotInflateBackgroundCount() {
        val reused = events + events.drop(1).map { it.copy(pageIndex = 1, elapsedMs = it.elapsedMs + 10) }
        assertEquals(0, backgroundSavedPages(reused))
        assertEquals(0, backgroundSavedPages(events + event(ProbeEventKind.RUN_FAILED, 5).copy(reason = ProbeReason.EVIDENCE_LOST)))
    }
    @Test fun selfCheckRequiresAttemptMarkersAndExactLocalResponses() {
        val base = "https://$SELF_CHECK_HOST"
        val capture = RawCapture("$base/assets/selfcheck.html", 200,
            "<html data-frame-sent=\"true\" data-stale-sent=\"true\">", listOf(
                CapturedJson("$base/probe/fetch", "{\"source\":\"local\",\"case\":\"fetch\"}"),
                CapturedJson("$base/probe/xhr", "{\"source\":\"local\",\"case\":\"xhr\"}")), 2, false)
        assertTrue(localCaptureChecks(capture).all { it.second })
        assertFalse(localCaptureChecks(capture.copy(html = "<html>" )).all { it.second })
        assertFalse(localCaptureChecks(capture.copy(json = capture.json + CapturedJson("$base/probe/stale", "{}"))).all { it.second })
        assertFalse(localCaptureChecks(capture.copy(networkObserved = true)).all { it.second })
    }
}
