package tw.houselens.probe

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import android.view.ViewGroup
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import tw.houselens.probe.sources.*

class ProbeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val binder = LocalBinder()
    private lateinit var runner: ProbeRunner
    private lateinit var power: PowerManager
    private lateinit var lock: PowerManager.WakeLock
    private var session: WebViewSession? = null
    private var http: HttpPageTransport? = null
    private var receiverRegistered = false
    private val remaining = ArrayDeque<ProbeQuery>()
    private var collectionMode = RunMode.FULL
    private var localOwner: Any? = null
    private val occupied = kotlinx.coroutines.flow.MutableStateFlow(false)
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_ON || intent.action == Intent.ACTION_SCREEN_OFF) updateEnvironment()
        }
    }

    inner class LocalBinder : Binder() {
        val state get() = runner.state
        val busy get() = runner.busy || remaining.isNotEmpty() || localOwner != null
        val busyState: kotlinx.coroutines.flow.StateFlow<Boolean> get() = occupied
        fun reserveLocal(owner: Any): Boolean {
            if (busy) return false
            localOwner = owner; occupied.value = true
            return true
        }
        fun releaseLocal(owner: Any) {
            if (localOwner === owner) { localOwner = null; occupied.value = runner.busy || remaining.isNotEmpty() }
        }
        fun start(query: ProbeQuery, mode: RunMode): Boolean {
            if (busy || visibleActivities == 0) return false
            return startVisible(query, mode)
        }
        fun startCollection(query: ProbeQuery, mode: RunMode): Boolean {
            if (busy || visibleActivities == 0) return false
            val queries = siteOrder.map { query.copy(site = it) }
            queries.forEach { sources.getValue(it.site).request(it, 0) }
            collectionMode = mode
            remaining.addAll(queries.drop(1))
            return startVisible(queries.first(), mode)
        }
        fun resume(runId: String): Boolean = visibleActivities > 0 && runner.resume(runId)
        fun cancel() { remaining.clear(); runner.cancel() }
        fun attach(container: ViewGroup): Boolean {
            val current = session?.takeIf { it.isAvailable } ?: return false
            current.attach(container)
            return true
        }
        fun detach(container: ViewGroup) { session?.detach(container) }
    }

    override fun onCreate() {
        super.onCreate()
        current = this
        power = getSystemService(PowerManager::class.java)
        lock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HouseLens:probe").apply { setReferenceCounted(false) }
        val httpTransport = object : PageTransport {
            override suspend fun capture(request: PageRequest, navigationId: Long) = http!!.capture(request, navigationId)
            override fun cancel() { http?.cancel() }
        }
        val browserTransport = object : PageTransport {
            override suspend fun capture(request: PageRequest, navigationId: Long) = session!!.capture(request, navigationId)
            override fun cancel() { session?.cancel() }
        }
        runner = ProbeRunner(store(applicationContext), processId, sources, httpTransport, browserTransport, scope,
            snapshot = { readDeviceSnapshot(applicationContext) }, checkRobots = { http!!.checkRobots(it) },
            sessionAlive = { session?.isAvailable == true }, prepareSession = {
                val pacer = RequestPacer()
                session = WebViewSession(applicationContext, pacer, onRendererGone = { runner.stop(ProbeReason.RENDERER_GONE) })
                session!!.clearSession()
                http = HttpPageTransport(session!!.userAgent, pacer)
            }, wakeLock = { held, timeout -> if (held) lock.acquire(timeout) else releaseLock() },
            evidenceSource = EvidenceSource.LIVE, finished = ::onRunFinished)
        val filter = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
        else registerReceiver(screenReceiver, filter)
        receiverRegistered = true
    }

    override fun onBind(intent: Intent): IBinder = binder

    private fun startVisible(query: ProbeQuery, mode: RunMode): Boolean {
        validateQuery(query)
        sources.getValue(query.site).request(query, 0)
        updateEnvironment()
        occupied.value = true
        try {
            startForegroundService(Intent(this, ProbeService::class.java))
            promote()
        } catch (_: RuntimeException) {
            remaining.clear()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return runner.startDenied(query, mode)
        }
        return runner.start(query, mode)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == CANCEL) binder.cancel()
        if (!runner.busy) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(startId) }
        return START_NOT_STICKY
    }

    private fun promote() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "房源驗證進度", NotificationManager.IMPORTANCE_LOW))
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val back = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), flags)
        val cancel = PendingIntent.getService(this, 1, Intent(this, ProbeService::class.java).setAction(CANCEL), flags)
        val notification = Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_probe)
            .setContentTitle("HouseLens 房源驗證").setContentText("驗證進行中；返回 App 查看進度或處理網站驗證")
            .setContentIntent(back).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "取消", cancel).build()).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(1, notification)
    }

    private fun updateEnvironment() = runner.environment(visibleActivities > 0, power.isInteractive)

    private fun onRunFinished() {
        releaseLock()
        session?.destroy(); session = null; http = null
        val state = runner.state.value?.state
        if (remaining.isNotEmpty() && state in setOf(RunState.COMPLETED, RunState.TRUNCATED, RunState.INCONCLUSIVE)) {
            updateEnvironment()
            runner.start(remaining.removeFirst(), collectionMode)
        } else {
            remaining.clear()
            occupied.value = localOwner != null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        remaining.clear()
        runner.stop(ProbeReason.TIMEOUT)
        releaseLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        remaining.clear()
        runner.stop(ProbeReason.PROCESS_LOST)
        releaseLock()
        if (receiverRegistered) unregisterReceiver(screenReceiver)
        session?.destroy(); session = null
        scope.cancel()
        if (current === this) current = null
        super.onDestroy()
    }

    private fun releaseLock() { if (lock.isHeld) lock.release() }

    companion object {
        private const val CHANNEL = "probe_progress"
        private const val CANCEL = "tw.houselens.probe.CANCEL"
        private val processId = UUID.randomUUID().toString()
        private var sharedStore: ProbeStore? = null
        private var current: ProbeService? = null
        private var visibleActivities = 0
        val siteOrder = listOf(Site.F591, Site.Rakuya, Site.Yungching, Site.HBHousing, Site.Sinyi, Site.CtHouse, Site.TwHouse)
        private val sources = probeSources
        @Synchronized fun store(context: Context): ProbeStore = sharedStore ?: ProbeStore(File(context.applicationContext.filesDir, "runs"), processId)
            .also { sharedStore = it }
        internal fun selfCheckRecord(context: Context) = RunRecord(UUID.randomUUID().toString(), processId,
            ProbeInput(siteCode = Site.CtHouse.code).query(), RunMode.FOREGROUND, RunState.RUNNING,
            readDeviceSnapshot(context), EvidenceSource.LOCAL_SELF_CHECK, appVersion = BuildConfig.VERSION_NAME)
        fun activityStarted() { visibleActivities++; current?.updateEnvironment() }
        fun activityStopped() {
            visibleActivities = (visibleActivities - 1).coerceAtLeast(0)
            if (visibleActivities == 0) current?.session?.detach()
            current?.updateEnvironment()
        }
    }
}
