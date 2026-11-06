package tw.houselens.probe

import android.Manifest
import android.app.NotificationManager
import android.content.*
import android.graphics.BitmapFactory
import android.os.*
import android.provider.Settings
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var binder: ProbeService.LocalBinder? = null
    private var bound = false
    private var visible = false
    private var collector: Job? = null
    private var detailJob: Job? = null
    private var localJob: Job? = null
    private var container: FrameLayout? = null
    private var selected by mutableStateOf<RunRecord?>(null)
    private var busy by mutableStateOf(true)
    private var message by mutableStateOf("正在連接本機服務")
    private var listings by mutableStateOf<List<ProbeListing>>(emptyList())
    private var history by mutableStateOf<List<String>>(emptyList())
    private var selfChecks by mutableStateOf<List<Pair<String, Boolean>>>(emptyList())
    private var thumbnail by mutableStateOf<ImageBitmap?>(null)
    private var imageMessage by mutableStateOf("")
    private var notifications by mutableStateOf(false)
    private var pendingExport: String? = null
    private var exportUri: android.net.Uri? = null
    private var interruptedLocalWork = false
    private val store get() = ProbeService.store(applicationContext)
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifications = getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    }
    private val exportDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        exportUri = uri
        if (uri != null && pendingExport != null) finishExport()
        else { pendingExport = null; message = "已取消匯出，未寫入檔案。" }
    }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val connected = service as ProbeService.LocalBinder
            binder = connected; busy = connected.busy
            message = if (interruptedLocalWork) "前一次本機作業可能中斷；若正在匯出，請檢查目的檔，刪除不完整 ZIP 後重試。" else "服務已連接"
            interruptedLocalWork = false
            collector?.cancel()
            collector = scope.launch {
                launch { connected.busyState.collect { busy = it } }
                connected.state.collect { record ->
                    if (record != null) { selected = record; loadDetails(record) }
                    if (visible) container?.let { connected.attach(it) }
                }
            }
            refreshHistory()
            finishExport()
        }
        override fun onServiceDisconnected(name: ComponentName) {
            binder = null; busy = true; message = "服務中斷，請重開 App 並檢查歷史紀錄。"
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingExport = savedInstanceState?.getString("exportRun")
        exportUri = savedInstanceState?.getString("exportUri")?.let(android.net.Uri::parse)
        interruptedLocalWork = savedInstanceState?.getBoolean("localWork") == true
        enableEdgeToEdge()
        val d = readDeviceSnapshot(applicationContext)
        val device = "裝置 ${d.model ?: "未取得"}｜Android ${d.androidVersion ?: "未取得"}\nWebView ${d.webViewPackage ?: "未取得"} ${d.webViewVersion ?: ""}\n電量 ${d.batteryPercent ?: "未知"}%｜省電 ${d.powerSaveMode ?: "未知"}"
        setContent {
            MaterialTheme { Surface(Modifier.fillMaxSize()) {
                ProbeScreen(selected, { q, m -> start(q, m, false) }, ::cancel, { id ->
                    if (binder?.resume(id) != true) message = "無可續用的網站工作階段，請取消後重新開始。"
                }, { id ->
                    if (!busy && pendingExport == null) {
                        pendingExport = id
                        try { exportDocument.launch("HouseLens-$id.zip") }
                        catch (_: RuntimeException) { pendingExport = null; message = "無法開啟檔案選擇器。" }
                    }
                }, busy, message, listings, history, ::loadHistory, ::refreshHistory,
                    { q, m -> start(q, m, true) }, ::selfCheck, selfChecks,
                    notifications, ::requestNotifications, ::loadImage, thumbnail, imageMessage, device) {
                    AndroidView(factory = { FrameLayout(it).also { view -> container = view; if (visible) binder?.attach(view) } },
                        modifier = Modifier.fillMaxWidth().height(340.dp),
                        onRelease = { binder?.detach(it); if (container === it) container = null },
                        update = { if (visible) binder?.attach(it) })
                }
            } }
        }
        bound = bindService(Intent(this, ProbeService::class.java), connection, BIND_AUTO_CREATE)
        if (!bound) message = "無法連接服務，請重新開啟 App。"
    }
    override fun onStart() {
        super.onStart(); visible = true; ProbeService.activityStarted()
        notifications = getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        container?.let { binder?.attach(it) }
    }
    override fun onStop() {
        visible = false; container?.let { binder?.detach(it) }
        ProbeService.activityStopped(); super.onStop()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("exportRun", pendingExport); outState.putString("exportUri", exportUri?.toString())
        outState.putBoolean("localWork", localJob?.isActive == true || interruptedLocalWork)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        container?.let { binder?.detach(it) }; scope.cancel()
        if (bound) unbindService(connection)
        super.onDestroy()
    }
    private fun start(query: ProbeQuery, mode: RunMode, collection: Boolean) {
        if (pendingExport != null) { message = "匯出選擇尚未完成，請稍後重試。"; return }
        try {
            val accepted = if (collection) binder?.startCollection(query, mode) else binder?.start(query, mode)
            message = if (accepted == true) "已開始；每頁成功落盤後才增加保存頁數。" else "忙碌或服務未就緒，請稍後手動重試。"
            if (accepted == true) { thumbnail = null; imageMessage = "" }
        } catch (_: IllegalArgumentException) { message = "此來源不支援輸入條件。" }
    }
    private fun cancel() {
        if (localJob?.isActive == true) { localJob?.cancel(); message = "正在取消本機作業" }
        else { binder?.cancel(); message = "正在取消採集；等待收尾後才能開始下一作業。" }
    }
    private fun loadDetails(record: RunRecord) {
        detailJob?.cancel()
        detailJob = scope.launch {
            try {
                listings = withContext(Dispatchers.IO) {
                    record.savedPageIndices.sorted().flatMap { store.loadPage(record.runId, it).listings }
                        .distinctBy { it.sourceSite to it.sourceListingKey }
                }
                if (record.state !in setOf(RunState.RUNNING, RunState.WAITING_FOR_USER)) refreshHistory()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { listings = emptyList(); message = "結果讀取失敗；勿視為已完整保存。" }
        }
    }
    private fun refreshHistory() {
        scope.launch {
            try { history = withContext(Dispatchers.IO) { store.runIds() } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = "歷史清單讀取失敗。" }
        }
    }
    private fun loadHistory(id: String) = localWork {
        selected = withContext(Dispatchers.IO) { store.load(id) }
        loadDetails(selected!!); thumbnail = null; imageMessage = ""
        message = "已讀取歷史；中斷作業不會自動續跑。"
    }
    private fun localWork(action: suspend () -> Unit) {
        val service = binder ?: run { message = "服務未就緒，請稍後手動重試。"; return }
        val owner = Any()
        if (!service.reserveLocal(owner)) { message = "忙碌中，請稍後手動重試。"; return }
        localJob = scope.launch {
            try { action() }
            catch (e: CancellationException) { message = "本機作業已取消；未完成的匯出檔請刪除。"; throw e }
            catch (_: Exception) { message = "本機作業失敗；未完成的匯出檔請刪除，原始紀錄仍保留。" }
            finally { service.releaseLocal(owner) }
        }
    }
    private fun finishExport() {
        val uri = exportUri ?: return
        val id = pendingExport ?: return
        val service = binder ?: return
        exportUri = null; pendingExport = null
        if (service.busy) { message = "匯出時服務忙碌，請刪除空檔後手動重試。"; return }
        localWork {
            withContext(Dispatchers.IO) {
                (contentResolver.openOutputStream(uri, "w") ?: throw java.io.IOException("export_unavailable")).use { store.export(id, it) }
            }
            message = "ZIP 已匯出；請由你決定是否分享。"
        }
    }
    private fun selfCheck() = localWork {
        selfChecks = emptyList(); message = "本機自檢中；請保持 App 可見，可隨時取消。"
        selfChecks = ProbeSelfCheck(applicationContext).run()
        refreshHistory()
        message = "自檢結束；LOCAL_SELF_CHECK／SIMULATED 不代表實站或背景成功。"
    }
    private fun loadImage(item: ProbeListing) = localWork {
        thumbnail = null; imageMessage = "${item.sourceListingKey}：圖片載入中"
        val transport = HttpPageTransport(android.webkit.WebSettings.getDefaultUserAgent(applicationContext), RequestPacer())
        try {
            val bytes = transport.loadImage(item.sourceSite, item.imageUrl ?: error("missing_image"))
            thumbnail = withContext(Dispatchers.Default) {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                options.inSampleSize = thumbnailSampleSize(options.outWidth, options.outHeight)
                options.inJustDecodeBounds = false
                (BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: error("invalid_image")).asImageBitmap()
            }
            imageMessage = "${item.sourceListingKey}：圖片成功（只預覽此張）"
        } catch (e: CancellationException) { imageMessage = "${item.sourceListingKey}：圖片已取消"; throw e }
        catch (_: Exception) { imageMessage = "${item.sourceListingKey}：圖片失敗；房源仍可讀取與匯出。" }
        finally { transport.cancel() }
    }
    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }
}
