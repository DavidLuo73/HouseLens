package tw.houselens.probe

import java.io.File
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ProbeRunnerTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun rejected591PageKeepsFixedReasonInEventsAndExportWithoutSavingRawPage() = runTest {
        val h = RunnerHarness(this, temp.root)
        val source = tw.houselens.probe.sources.F591Source
        val rejected = source.parse(RawCapture(source.request(h.query, 0).url, 200,
            "<main>private-cookie-and-page-content</main>", emptyList(), 1, false), h.query, 0)
        h.pages = listOf(rejected)
        h.runner.start(h.query, RunMode.FOREGROUND); advanceUntilIdle()
        val record = h.store.load(h.runner.state.value!!.runId)
        assertEquals(RunState.FAILED, record.state)
        assertEquals("F591_HTML_NO_CARDS", record.events.single { it.kind == ProbeEventKind.PAGE_PARSED }.reason?.name)
        assertEquals("F591_HTML_NO_CARDS", record.events.last().reason?.name)
        assertTrue(record.savedPageIndices.isEmpty())
        assertFalse(record.events.any { it.kind == ProbeEventKind.PAGE_SAVED })
        val output = ByteArrayOutputStream().also { h.store.export(record.runId, it) }
        val entries = mutableMapOf<String, String>()
        ZipInputStream(output.toByteArray().inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertTrue(entries.getValue("events.jsonl").contains("F591_HTML_NO_CARDS"))
        assertEquals("{\"pages\":[]}", entries.getValue("sample.json"))
        assertFalse(entries.values.any { it.contains("private-cookie-and-page-content") })
    }

    @Test fun unrecognizedParseEvidenceCannotBecomeAnExportedReason() = runTest {
        val h = RunnerHarness(this, temp.root)
        h.pages = listOf(h.end.copy(kind = PageKind.INVALID, evidence = "F591_private-cookie-value"))
        h.runner.start(h.query, RunMode.FOREGROUND); advanceUntilIdle()
        val record = h.store.load(h.runner.state.value!!.runId)
        assertEquals(RunState.FAILED, record.state)
        assertEquals(ProbeReason.PARSE_ERROR, record.events.last().reason)
        assertNull(record.events.single { it.kind == ProbeEventKind.PAGE_PARSED }.reason)
        assertTrue(record.savedPageIndices.isEmpty())
    }

    @Test fun filteredThenValidThenEndUsesRawProgressAndRejectsSecondStart() = runTest {
        val h = RunnerHarness(this, temp.root)
        h.pages = listOf(h.page("A", filtered = true), h.page("B"), h.end)
        assertTrue(h.runner.start(h.query, RunMode.FULL))
        assertFalse(h.runner.start(h.query, RunMode.FULL))
        advanceUntilIdle()
        val record = h.runner.state.value!!
        assertEquals(RunState.COMPLETED, record.state)
        assertEquals(setOf(0, 1, 2), record.savedPageIndices)
        assertEquals(listOf("B"), record.savedPageIndices.flatMap { h.store.loadPage(record.runId, it).listings }.map { it.sourceListingKey })
        assertEquals(1, temp.root.listFiles()!!.size)
        assertEquals(listOf(true, false), h.wake)
    }

    @Test fun foregroundStopsAfterThreePagesWithoutClaimingComplete() = runTest {
        val h = RunnerHarness(this, temp.root)
        h.runner.start(h.query, RunMode.FOREGROUND)
        advanceUntilIdle()
        assertEquals(RunState.TRUNCATED, h.runner.state.value!!.state)
        assertEquals(3, h.requests.size)
    }

    @Test fun cancelRejectsLateResponseAndKeepsSlotUntilUnwind() = runTest {
        val h = RunnerHarness(this, temp.root)
        h.capture = { _, id -> withContext(NonCancellable) { delay(1000); h.raw(id) } }
        h.runner.start(h.query, RunMode.FULL)
        runCurrent()
        h.runner.cancel()
        assertFalse(h.runner.start(h.query, RunMode.FULL))
        advanceUntilIdle()
        assertEquals(RunState.CANCELLED, h.runner.state.value!!.state)
        assertTrue(h.runner.state.value!!.savedPageIndices.isEmpty())
        assertEquals(1, h.requests.size)
        h.capture = { _, id -> h.raw(id) }; h.pages = listOf(h.end)
        assertTrue(h.runner.start(h.query, RunMode.FULL))
        advanceUntilIdle()
        assertEquals(RunState.COMPLETED, h.runner.state.value!!.state)
    }

    @Test fun storageFailureStopsAndPersistsFixedReason() = runTest {
        val h = RunnerHarness(this, temp.root, failPage = true)
        h.runner.start(h.query, RunMode.FULL)
        advanceUntilIdle()
        assertEquals(RunState.FAILED, h.runner.state.value!!.state)
        assertEquals(ProbeReason.STORAGE_ERROR, h.runner.state.value!!.events.last().reason)
        assertEquals(1, h.requests.size)
        assertEquals(false, h.wake.last())
    }

    @Test fun challengeWaitsForExplicitResumeAndKeepsSamePageWithNewNavigation() = runTest {
        val h = RunnerHarness(this, temp.root)
        h.browser = true; h.pages = listOf(h.challenge)
        h.runner.start(h.query, RunMode.FULL); runCurrent()
        val id = h.runner.state.value!!.runId
        assertEquals(RunState.WAITING_FOR_USER, h.runner.state.value!!.state)
        assertEquals(listOf(true, false), h.wake)
        advanceTimeBy(200_000); runCurrent()
        assertEquals(1, h.requests.size)
        h.pages = listOf(h.end)
        assertTrue(h.runner.resume(id)); assertFalse(h.runner.resume(id))
        advanceUntilIdle()
        assertEquals(RunState.COMPLETED, h.runner.state.value!!.state)
        assertEquals(listOf(0, 0), h.requests.map { it.first })
        assertEquals(2, h.requests.map { it.second }.toSet().size)
        assertEquals(BackgroundVerdict.FAILED, backgroundVerdict(h.runner.state.value!!.events, RunMode.FULL))
    }

    @Test fun challengeExpiresAtFiveMinutesAndLostSessionCannotResume() = runTest {
        val h = RunnerHarness(this, temp.root)
        h.browser = true; h.pages = listOf(h.challenge)
        h.runner.start(h.query, RunMode.FULL); runCurrent()
        h.alive = false
        assertFalse(h.runner.resume(h.runner.state.value!!.runId))
        advanceUntilIdle()
        assertEquals(300_000L, testScheduler.currentTime)
        assertEquals(RunState.INTERRUPTED, h.runner.state.value!!.state)
        assertEquals(ProbeReason.CHALLENGE_WAIT_TIMEOUT, h.runner.state.value!!.events.last().reason)
        assertEquals(false, h.wake.last())
    }

    @Test fun backgroundSamplesAtThreeSlotsAndWaitsUntilTenMinutes() = runTest {
        val h = RunnerHarness(this, temp.root)
        h.runner.start(h.query, RunMode.BACKGROUND_10M); runCurrent()
        assertEquals(1, h.requests.size)
        h.runner.environment(foreground = false, interactive = true)
        advanceTimeBy(599_999); runCurrent()
        assertEquals(listOf(0L, 60_000L, 300_000L, 540_000L), h.requestTimes)
        assertEquals(RunState.RUNNING, h.runner.state.value!!.state)
        advanceTimeBy(1); runCurrent()
        val record = h.runner.state.value!!
        assertEquals(RunState.COMPLETED, record.state)
        assertEquals(BackgroundVerdict.PASSED, backgroundVerdict(record.events, record.mode))
    }

    @Test fun lockedModeWaitsForScreenOffAndInsufficientPagesAreInconclusive() = runTest {
        val h = RunnerHarness(this, temp.root)
        h.pages = listOf(h.page("A"), h.end)
        h.runner.start(h.query, RunMode.LOCKED_10M); runCurrent()
        h.runner.environment(false, true)
        advanceTimeBy(30_000); runCurrent()
        assertEquals(1, h.requests.size)
        h.runner.environment(false, false)
        advanceUntilIdle()
        assertEquals(listOf(0L, 90_000L), h.requestTimes)
        assertEquals(RunState.INCONCLUSIVE, h.runner.state.value!!.state)
    }

    @Test fun cancelDuringBackgroundWaitDoesNotRequestAnotherPage() = runTest {
        val h = RunnerHarness(this, temp.root)
        h.runner.start(h.query, RunMode.BACKGROUND_10M); runCurrent()
        h.runner.environment(false, true); runCurrent()
        h.runner.cancel(); advanceUntilIdle()
        assertEquals(1, h.requests.size)
        assertEquals(RunState.CANCELLED, h.runner.state.value!!.state)
        assertEquals(false, h.wake.last())
    }

    @Test fun overallDeadlineIncludesPreparationAndStopsWithoutBackgroundEvent() = runTest {
        val h = RunnerHarness(this, temp.root)
        h.runner.start(h.query, RunMode.BACKGROUND_10M)
        advanceUntilIdle()
        assertEquals(900_000L, testScheduler.currentTime)
        assertEquals(RunState.TRUNCATED, h.runner.state.value!!.state)
        assertEquals(ProbeReason.TIMEOUT, h.runner.state.value!!.events.last().reason)
        assertEquals(false, h.wake.last())
    }

    @Test fun rendererFailureAndSystemTimeoutNeverComplete() = runTest {
        val h = RunnerHarness(this, temp.root)
        h.capture = { _, _ -> throw TransportException(ProbeReason.RENDERER_GONE) }
        h.runner.start(h.query, RunMode.FULL); advanceUntilIdle()
        assertEquals(ProbeReason.RENDERER_GONE, h.runner.state.value!!.events.last().reason)
        h.capture = { _, id -> delay(10_000); h.raw(id) }
        h.runner.start(h.query, RunMode.FULL); runCurrent()
        h.runner.stop(ProbeReason.TIMEOUT); advanceUntilIdle()
        assertEquals(RunState.TRUNCATED, h.runner.state.value!!.state)
        assertEquals(false, h.wake.last())
    }

    @Test fun robotsCheckedBeforeWarmupAndBothTransportsAndUnknownStaysUnknown() = runTest {
        val h = RunnerHarness(this, temp.root)
        h.robots = null; h.pages = listOf(h.end)
        h.runner.start(h.query, RunMode.FULL); advanceUntilIdle()
        assertTrue(h.runner.state.value!!.events.any { it.reason == ProbeReason.ROBOTS_UNKNOWN })
        assertEquals(listOf("https://sale.591.com.tw/?page=0"), h.robotUrls)
        h.browser = true; h.warmup = "https://sale.591.com.tw/"
        h.robots = false
        h.runner.start(h.query, RunMode.FULL); advanceUntilIdle()
        assertEquals(1, h.requests.size)
        assertEquals(ProbeReason.ROBOTS_DISALLOWED, h.runner.state.value!!.events.last().reason)
        assertEquals(h.warmup, h.robotUrls.last())
    }

    @Test fun mismatchedNavigationIsRejectedBeforeParsing() = runTest {
        val h = RunnerHarness(this, temp.root)
        h.capture = { _, id -> h.raw(id + 1) }
        h.runner.start(h.query, RunMode.FULL); advanceUntilIdle()
        assertEquals(RunState.FAILED, h.runner.state.value!!.state)
        assertTrue(h.runner.state.value!!.savedPageIndices.isEmpty())
    }

    @Test fun immediateCancelStillReleasesReservationAndRecordsCancellation() = runTest {
        val h = RunnerHarness(this, temp.root)
        h.runner.start(h.query, RunMode.FULL)
        h.runner.cancel()
        advanceUntilIdle()
        assertFalse(h.runner.busy)
        assertEquals(RunState.CANCELLED, h.runner.state.value!!.state)
        assertTrue(h.requests.isEmpty())
    }

    @Test fun alreadyBackgroundAtCollectionStartCanObserveNextSite() = runTest {
        val h = RunnerHarness(this, temp.root)
        h.runner.environment(false, false)
        h.runner.start(h.query, RunMode.LOCKED_10M)
        advanceUntilIdle()
        assertEquals(listOf(0L, 60_000L, 300_000L, 540_000L), h.requestTimes)
        assertEquals(BackgroundVerdict.PASSED, backgroundVerdict(h.runner.state.value!!.events, RunMode.LOCKED_10M))
    }

    @Test fun emptyFinalBackgroundSampleIsInconclusive() = runTest {
        val h = RunnerHarness(this, temp.root)
        h.pages = listOf(h.page("A"), h.page("B"), h.page("C"), h.end)
        h.runner.start(h.query, RunMode.BACKGROUND_10M); runCurrent()
        h.runner.environment(false, true)
        advanceUntilIdle()
        assertEquals(RunState.INCONCLUSIVE, h.runner.state.value!!.state)
    }

    @Test fun foregroundReturnDuringFinalWaitCannotCompleteObservation() = runTest {
        val h = RunnerHarness(this, temp.root)
        h.runner.start(h.query, RunMode.BACKGROUND_10M); runCurrent()
        h.runner.environment(false, true)
        advanceTimeBy(550_000); runCurrent()
        h.runner.environment(true, true)
        advanceUntilIdle()
        assertEquals(RunState.INCONCLUSIVE, h.runner.state.value!!.state)
    }

    @Test fun startDeniedWritesFailureWithoutNetworkOrWakeLock() = runTest {
        val h = RunnerHarness(this, temp.root)
        assertTrue(h.runner.startDenied(h.query, RunMode.FULL)); advanceUntilIdle()
        val record = h.store.load(h.runner.state.value!!.runId)
        assertEquals(RunState.FAILED, record.state)
        assertEquals(ProbeReason.SERVICE_START_DENIED, record.events.last().reason)
        assertTrue(h.requests.isEmpty()); assertTrue(h.wake.isEmpty())
    }

    @Test fun rendererGoneDuringChallengeEndsWithoutWaitingForFiveMinutes() = runTest {
        val h = RunnerHarness(this, temp.root)
        h.browser = true; h.pages = listOf(h.challenge)
        h.runner.start(h.query, RunMode.FULL); runCurrent()
        h.runner.stop(ProbeReason.RENDERER_GONE); advanceUntilIdle()
        assertEquals(0L, testScheduler.currentTime)
        assertEquals(ProbeReason.RENDERER_GONE, h.runner.state.value!!.events.last().reason)
        assertFalse(h.runner.resume(h.runner.state.value!!.runId))
    }

    @Test fun elapsedDeadlineIsCheckedAfterSuspensionEvenIfSchedulerHasNotTimedOut() = runTest {
        val h = RunnerHarness(this, temp.root)
        h.pages = listOf(h.end)
        h.capture = { _, id -> h.elapsedOffset = 5_400_001; h.raw(id) }
        h.runner.start(h.query, RunMode.FULL); advanceUntilIdle()
        assertEquals(RunState.TRUNCATED, h.runner.state.value!!.state)
        assertEquals(ProbeReason.TIMEOUT, h.runner.state.value!!.events.last().reason)
        assertTrue(h.runner.state.value!!.savedPageIndices.isEmpty())
    }

    @Test fun fullStopsAtSourceCapWhileRawResultsContinue() = runTest {
        val h = RunnerHarness(this, temp.root)
        h.maxPages = 5
        h.runner.start(h.query, RunMode.FULL); advanceUntilIdle()
        assertEquals(RunState.TRUNCATED, h.runner.state.value!!.state)
        assertEquals(5, h.requests.size)
    }

    @Test fun lifecycleDuringFinalCommitCannotBeDroppedToProduceBackgroundPass() = runTest {
        val h = RunnerHarness(this, temp.root)
        h.pages = listOf(h.page("A").copy(next = NextPage.END, evidence = "last_page"))
        h.runner.environment(false, true)
        h.onCommit = { file -> if (file.name == "page-0.json") {
            h.runner.environment(true, true)
            h.elapsedOffset += 10
        } }
        h.runner.start(h.query, RunMode.FULL); advanceUntilIdle()
        val record = h.store.load(h.runner.state.value!!.runId)
        assertNotEquals(BackgroundVerdict.PASSED, backgroundVerdict(record.events, RunMode.FULL))
        assertEquals(ProbeReason.EVIDENCE_LOST, record.events.last().reason)
    }

    @Test fun batteryAtEndSurvivesExportForConsumptionComparison() = runTest {
        val h = RunnerHarness(this, temp.root)
        h.pages = listOf(h.end)
        h.capture = { _, id -> h.battery = 77; h.raw(id) }
        h.runner.start(h.query, RunMode.FULL); advanceUntilIdle()
        val output = ByteArrayOutputStream()
        h.store.export(h.runner.state.value!!.runId, output)
        val report = ZipInputStream(output.toByteArray().inputStream()).use { zip ->
            zip.nextEntry
            zip.readBytes().toString(Charsets.UTF_8)
        }
        assertTrue(report.contains("batteryStartPercent: 80"))
        assertTrue(report.contains("batteryEndPercent: 77"))
    }

    @Test fun lifecycleDuringCommitWithSameClockTickStillCannotPass() = runTest {
        val h = RunnerHarness(this, temp.root)
        h.pages = listOf(h.page("A").copy(next = NextPage.END, evidence = "last_page"))
        h.runner.environment(false, true)
        h.onCommit = { file -> if (file.name == "page-0.json") h.runner.environment(true, true) }
        h.runner.start(h.query, RunMode.FULL); advanceUntilIdle()
        assertNotEquals(BackgroundVerdict.PASSED, backgroundVerdict(h.runner.state.value!!.events, RunMode.FULL))
    }

    @Test fun deadlineCrossedWhileSavingRequestEventDoesNotStartNetwork() = runTest {
        val h = RunnerHarness(this, temp.root)
        h.afterDisk = {
            val log = temp.root.listFiles()?.singleOrNull()?.resolve("events.jsonl")
            if (log?.exists() == true && log.readText().contains("REQUEST_STARTED")) h.elapsedOffset = 5_400_001
        }
        h.runner.start(h.query, RunMode.FULL); advanceUntilIdle()
        assertEquals(ProbeReason.TIMEOUT, h.runner.state.value!!.events.last().reason)
        assertTrue(h.requests.isEmpty())
    }

    @Test fun challengeResumeAfterElapsedDeadlineIsRejectedEvenBeforeTimerFires() = runTest {
        val h = RunnerHarness(this, temp.root)
        h.browser = true; h.pages = listOf(h.challenge)
        h.runner.start(h.query, RunMode.FULL); runCurrent()
        h.elapsedOffset = 300_001
        assertFalse(h.runner.resume(h.runner.state.value!!.runId))
        advanceUntilIdle()
        assertEquals(ProbeReason.CHALLENGE_WAIT_TIMEOUT, h.runner.state.value!!.events.last().reason)
    }

    @Test fun deadlineCrossedDuringWarmupRobotsDoesNotFetchSecondRobots() = runTest {
        val h = RunnerHarness(this, temp.root)
        h.browser = true; h.warmup = "https://sale.591.com.tw/"
        h.onRobots = { h.elapsedOffset = 5_400_001 }
        h.runner.start(h.query, RunMode.FULL); advanceUntilIdle()
        assertEquals(1, h.robotUrls.size)
        assertTrue(h.requests.isEmpty())
        assertEquals(ProbeReason.TIMEOUT, h.runner.state.value!!.events.last().reason)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class RunnerHarness(val scope: TestScope, root: File, failPage: Boolean = false) {
    val process = "22222222-2222-4222-8222-222222222222"
    val query = testQuery(Site.F591)
    val device = DeviceSnapshot(null, null, null, null, null, null, null, null, 80, false, false, null)
    val wake = mutableListOf<Boolean>()
    val requests = mutableListOf<Pair<Int, Long>>()
    val requestTimes = mutableListOf<Long>()
    val robotUrls = mutableListOf<String>()
    var browser = false
    var warmup: String? = null
    var alive = true
    var battery = 80
    var elapsedOffset = 0L
    var maxPages = 100
    var onCommit: (File) -> Unit = {}
    var afterDisk: () -> Unit = {}
    var robots: Boolean? = true
    var onRobots: () -> Unit = {}
    var pages = emptyList<PageResult>()
    val end = PageResult(PageKind.EMPTY, emptyList(), emptyList(), emptyMap(), NextPage.END, "empty_marker")
    val challenge = end.copy(kind = PageKind.CHALLENGE, next = NextPage.UNKNOWN, evidence = "challenge")
    val store = ProbeStore(root, process, utcNow = { "2026-11-06T00:00:00Z" }, elapsedNow = { scope.testScheduler.currentTime + elapsedOffset },
        commit = { from, to ->
            if (failPage && to.name.startsWith("page-")) throw IOException("disk full")
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE)
            onCommit(to)
        })
    fun page(key: String, filtered: Boolean = false) = PageResult(PageKind.RESULTS, listOf(key),
        if (filtered) emptyList() else listOf(ProbeListing("新北市", "中和區", null, BigDecimal.TEN,
            null, null, false, BigDecimal("798.00"), null, Site.F591, key, "", "", null)),
        if (filtered) mapOf(key to "over_price") else emptyMap(), NextPage.MORE, "more_pages")
    fun raw(id: Long) = RawCapture("https://sale.591.com.tw/", 200, "", emptyList(), id, true)
    var capture: suspend (PageRequest, Long) -> RawCapture = { _, id -> raw(id) }
    private val transport = object : PageTransport {
        override suspend fun capture(request: PageRequest, navigationId: Long): RawCapture {
            requests += request.url.substringAfter("page=").toInt() to navigationId
            requestTimes += scope.testScheduler.currentTime
            return capture.invoke(request, navigationId)
        }
        override fun cancel() = Unit
    }
    private val source = object : SourceAdapter {
        override val site = Site.F591
        override val maxPages get() = this@RunnerHarness.maxPages
        override fun request(query: ProbeQuery, pageIndex: Int) = PageRequest("https://sale.591.com.tw/?page=$pageIndex", browser = browser, warmupUrl = warmup)
        override fun parse(capture: RawCapture, query: ProbeQuery, pageIndex: Int) = pages.getOrNull(pageIndex) ?: page("A$pageIndex")
    }
    val runner = ProbeRunner(store, process, mapOf(Site.F591 to source), transport, transport, scope,
        snapshot = { device.copy(batteryPercent = battery) }, checkRobots = { robotUrls += it; onRobots(); robots },
        sessionAlive = { alive }, prepareSession = {}, wakeLock = { held, _ -> wake += held },
        elapsedNow = { scope.testScheduler.currentTime + elapsedOffset }, utcNow = { "2026-11-06T00:00:00Z" },
        io = object : CoroutineDispatcher() {
            private val dispatcher = StandardTestDispatcher(scope.testScheduler)
            override fun dispatch(context: CoroutineContext, block: Runnable) = dispatcher.dispatch(context) {
                block.run(); afterDisk()
            }
        }, evidenceSource = EvidenceSource.LIVE)
}
