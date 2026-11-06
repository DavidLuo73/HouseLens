package tw.houselens.probe

import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Calls and scope are confined to main; disk work is serialized on io. */
class ProbeRunner(
    private val store: ProbeStore,
    private val processId: String,
    private val sources: Map<Site, SourceAdapter>,
    private val http: PageTransport,
    private val browser: PageTransport,
    private val scope: CoroutineScope,
    private val snapshot: () -> DeviceSnapshot,
    private val checkRobots: suspend (String) -> Boolean?,
    private val sessionAlive: () -> Boolean,
    private val prepareSession: () -> Unit,
    private val wakeLock: (Boolean, Long) -> Unit,
    private val elapsedNow: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    private val utcNow: () -> String = { Instant.now().toString() },
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val evidenceSource: EvidenceSource,
    private val finished: () -> Unit = {},
) {
    private val mutableState = MutableStateFlow<RunRecord?>(null)
    val state: StateFlow<RunRecord?> = mutableState.asStateFlow()
    val busy: Boolean get() = active != null
    private val disk = Mutex()
    private var active: Work? = null
    private var navigation = 0L
    private var foreground = true
    private var interactive = true
    private val environmentChanged = MutableStateFlow(0L)

    private class Work(val record: RunRecord, val deadline: Long) {
        var job: Job? = null
        var stop: ProbeReason? = null
        var resume: CompletableDeferred<Unit>? = null
        var waitDeadline: Long? = null
        var browserChallenge = false
        var held = false
        var closing = false
        var backgroundStart: Long? = null
        val lifecycleJobs = mutableListOf<Job>()
        var lifecycleFailure: ProbeReason? = null
        var savingPage = false
    }

    fun start(query: ProbeQuery, mode: RunMode): Boolean = begin(query, mode, null)

    fun startDenied(query: ProbeQuery, mode: RunMode): Boolean = begin(query, mode, ProbeReason.SERVICE_START_DENIED)

    private fun begin(query: ProbeQuery, mode: RunMode, denied: ProbeReason?): Boolean {
        if (busy) return false
        validateQuery(query)
        sources.getValue(query.site).request(query, 0)
        val record = RunRecord(UUID.randomUUID().toString(), processId, query, mode, RunState.RUNNING, snapshot(), evidenceSource,
            appVersion = BuildConfig.VERSION_NAME)
        val work = Work(record, elapsedNow() + if (mode.isTimedProbe()) 900_000 else 5_400_000)
        active = work
        mutableState.value = record
        work.job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var outcome = RunState.FAILED
            var reason: ProbeReason? = null
            try {
                withContext(NonCancellable) { disk { store.create(record) } }
                currentCoroutineContext().ensureActive()
                emit(work, ProbeEventKind.RUN_STARTED)
                emit(work, if (foreground) ProbeEventKind.APP_FOREGROUND else ProbeEventKind.APP_BACKGROUND)
                emit(work, if (interactive) ProbeEventKind.SCREEN_ON else ProbeEventKind.SCREEN_OFF)
                if (eligible(work) && work.backgroundStart == null) work.backgroundStart = state.value!!.events.last().elapsedMs
                if (denied != null) throw TransportException(denied)
                withTimeout((work.deadline - elapsedNow()).coerceAtLeast(1)) {
                    checkActive(work)
                    prepareSession()
                    hold(work, true)
                    outcome = pages(work)
                }
            } catch (_: TimeoutCancellationException) {
                outcome = RunState.TRUNCATED; reason = ProbeReason.TIMEOUT
            } catch (_: CancellationException) {
                reason = work.stop ?: ProbeReason.PROCESS_LOST
                outcome = stopState(reason)
            } catch (error: TransportException) {
                reason = error.reason; outcome = stopState(reason)
            } catch (_: IOException) {
                reason = ProbeReason.STORAGE_ERROR
            } catch (_: Exception) {
                reason = ProbeReason.PARSE_ERROR
            } finally {
                work.closing = true
                hold(work, false)
                withContext(NonCancellable) {
                    work.lifecycleJobs.joinAll()
                    work.lifecycleFailure?.let { outcome = RunState.FAILED; reason = it }
                    val battery = runCatching { snapshot().batteryPercent }.getOrNull()
                    try {
                        disk {
                            store.finish(record.runId, outcome, reason, battery)
                            mutableState.value = store.load(record.runId)
                        }
                    } catch (_: Exception) {
                        val failure = event(work, ProbeEventKind.RUN_FAILED, reason = ProbeReason.STORAGE_ERROR)
                        mutableState.value = (mutableState.value ?: record).copy(state = RunState.FAILED,
                            events = (mutableState.value?.events ?: emptyList()) + failure)
                    } finally {
                        active = null
                        finished()
                    }
                }
            }
        }
        return true
    }

    fun resume(runId: String): Boolean {
        val work = active ?: return false
        val signal = work.resume ?: return false
        if (work.waitDeadline?.let { elapsedNow() >= it } == true) {
            stop(ProbeReason.CHALLENGE_WAIT_TIMEOUT)
            return false
        }
        if (work.closing || work.stop != null || work.record.runId != runId || !work.browserChallenge ||
            !sessionAlive() || elapsedNow() >= work.deadline || signal.isCompleted) return false
        return signal.complete(Unit)
    }

    fun cancel() = stop(ProbeReason.USER_CANCELLED)

    fun stop(reason: ProbeReason) {
        val work = active ?: return
        if (work.closing || work.stop != null) return
        work.stop = reason
        hold(work, false)
        work.job?.cancel()
        http.cancel(); browser.cancel()
    }

    fun environment(foreground: Boolean, interactive: Boolean) {
        val foregroundChanged = this.foreground != foreground
        val screenChanged = this.interactive != interactive
        this.foreground = foreground; this.interactive = interactive
        val work = active
        if (work != null && !work.closing && work.stop == null) {
            if (eligible(work) && work.backgroundStart == null) work.backgroundStart = elapsedNow()
            val events = buildList {
                if (foregroundChanged) add(event(work, if (foreground) ProbeEventKind.APP_FOREGROUND else ProbeEventKind.APP_BACKGROUND))
                if (screenChanged) add(event(work, if (interactive) ProbeEventKind.SCREEN_ON else ProbeEventKind.SCREEN_OFF))
            }
            if (work.savingPage && events.isNotEmpty()) {
                work.lifecycleFailure = ProbeReason.EVIDENCE_LOST
                stop(ProbeReason.EVIDENCE_LOST)
            }
            if (events.isNotEmpty()) work.lifecycleJobs += scope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    disk { for (event in events) store.append(event) }
                } catch (error: Exception) {
                    // A lifecycle timestamp that races a disk commit must not become optimistic background evidence.
                    work.lifecycleFailure = if (error is IOException) ProbeReason.STORAGE_ERROR else ProbeReason.EVIDENCE_LOST
                    if (active === work) stop(work.lifecycleFailure!!)
                }
            }
            if (work.waitDeadline?.let { elapsedNow() >= it } == true) stop(ProbeReason.CHALLENGE_WAIT_TIMEOUT)
        }
        environmentChanged.value++
    }

    private suspend fun pages(work: Work): RunState {
        val record = work.record
        val source = sources.getValue(record.query.site)
        val timed = record.mode.isTimedProbe()
        val limit = if (record.mode == RunMode.FOREGROUND) minOf(3, source.maxPages) else source.maxPages
        val seen = mutableSetOf<String>()
        var index = 0
        while (true) {
            if (timed && index > 0) {
                environmentChanged.first { work.backgroundStart != null }
                val start = work.backgroundStart!!
                delay((start + listOf(60_000L, 300_000L, 540_000L)[index - 1] - elapsedNow()).coerceAtLeast(0))
                if (!eligible(work)) return RunState.INCONCLUSIVE
            }
            checkActive(work)
            val request = source.request(record.query, index)
            for (url in listOfNotNull(request.warmupUrl, request.url).distinct()) {
                checkActive(work)
                when (checkRobots(url)) {
                    false -> throw TransportException(ProbeReason.ROBOTS_DISALLOWED)
                    null -> emit(work, ProbeEventKind.HEARTBEAT, reason = ProbeReason.ROBOTS_UNKNOWN)
                    true -> Unit
                }
            }
            checkActive(work)
            val id = ++navigation
            emit(work, ProbeEventKind.REQUEST_STARTED, index, id)
            val capture = (if (request.browser) browser else http).capture(request, id)
            checkActive(work)
            if (capture.navigationId != id) throw TransportException(ProbeReason.EVIDENCE_LOST)
            emit(work, ProbeEventKind.RESPONSE_RECEIVED, index, id, network = capture.networkObserved)
            val page = withContext(io) { source.parse(capture, record.query, index) }
            checkActive(work)
            emit(work, ProbeEventKind.PAGE_PARSED, index, id, page = page)
            val decision = nextDecision(page, seen, index, limit)
            if (decision == PageDecision.WAIT_FOR_USER) {
                work.browserChallenge = request.browser
                work.resume = CompletableDeferred()
                val waitDeadline = elapsedNow() + 300_000
                work.waitDeadline = waitDeadline
                emit(work, ProbeEventKind.WAITING_FOR_USER)
                hold(work, false)
                val resumed = withTimeoutOrNull((waitDeadline - elapsedNow()).coerceAtLeast(1)) { work.resume!!.await(); true } ?: false
                work.resume = null
                work.waitDeadline = null
                if (!resumed || elapsedNow() >= waitDeadline) throw TransportException(ProbeReason.CHALLENGE_WAIT_TIMEOUT)
                if (!sessionAlive()) throw TransportException(ProbeReason.RENDERER_GONE)
                checkActive(work)
                emit(work, ProbeEventKind.RESUMED)
                hold(work, true)
                continue
            }
            if (decision == PageDecision.FAIL) throw TransportException(ProbeReason.PARSE_ERROR)
            work.savingPage = true
            try {
                disk {
                    store.savePage(record.runId, index, page)
                    mutableState.value = store.load(record.runId)
                }
            } finally { work.savingPage = false }
            seen.addAll(page.rawKeys)
            checkActive(work)
            if (decision == PageDecision.INCONCLUSIVE) return RunState.INCONCLUSIVE
            if (timed && index == 3) {
                delay((work.backgroundStart!! + 600_000 - elapsedNow()).coerceAtLeast(0))
                checkActive(work)
                emit(work, ProbeEventKind.HEARTBEAT)
                return if (backgroundVerdict(state.value!!.events, record.mode) == BackgroundVerdict.PASSED)
                    RunState.COMPLETED else RunState.INCONCLUSIVE
            }
            if (decision == PageDecision.COMPLETE) return if (timed) RunState.INCONCLUSIVE else RunState.COMPLETED
            if (decision == PageDecision.TRUNCATE) return RunState.TRUNCATED
            index++
        }
    }

    private fun eligible(work: Work) = !foreground && when (work.record.mode) {
        RunMode.BACKGROUND_10M -> interactive
        RunMode.LOCKED_10M -> !interactive
        else -> false
    }

    private fun hold(work: Work, held: Boolean) {
        if (work.held == held) return
        wakeLock(held, (work.deadline - elapsedNow()).coerceAtLeast(1))
        work.held = held
    }

    private suspend fun checkActive(work: Work) {
        currentCoroutineContext().ensureActive()
        if (elapsedNow() >= work.deadline) throw TransportException(ProbeReason.TIMEOUT)
    }

    private fun event(work: Work, kind: ProbeEventKind, index: Int? = null, id: Long? = null,
        page: PageResult? = null, network: Boolean? = null, reason: ProbeReason? = null) =
        ProbeEvent(work.record.runId, processId, work.record.query.site, index, kind, utcNow(), elapsedNow(), id,
            page?.rawKeys?.size, page?.listings?.size, network, reason, evidenceSource)

    private suspend fun emit(work: Work, kind: ProbeEventKind, index: Int? = null, id: Long? = null,
        page: PageResult? = null, network: Boolean? = null, reason: ProbeReason? = null) {
        val event = event(work, kind, index, id, page, network, reason)
        disk {
            store.append(event)
            mutableState.value = store.load(work.record.runId)
        }
        checkActive(work)
    }

    private suspend fun <T> disk(action: () -> T): T = disk.withLock { withContext(io) { action() } }

    private fun stopState(reason: ProbeReason) = when (reason) {
        ProbeReason.USER_CANCELLED -> RunState.CANCELLED
        ProbeReason.TIMEOUT -> RunState.TRUNCATED
        ProbeReason.PROCESS_LOST, ProbeReason.CHALLENGE_WAIT_TIMEOUT -> RunState.INTERRUPTED
        else -> RunState.FAILED
    }
}

private fun RunMode.isTimedProbe() = this == RunMode.BACKGROUND_10M || this == RunMode.LOCKED_10M
