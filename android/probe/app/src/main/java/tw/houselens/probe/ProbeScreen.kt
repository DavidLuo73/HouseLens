package tw.houselens.probe

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

internal fun Site.label() = when (this) {
    Site.F591 -> "591"; Site.Rakuya -> "樂屋"; Site.Yungching -> "永慶"; Site.HBHousing -> "住商"
    Site.Sinyi -> "信義"; Site.CtHouse -> "中信"; Site.TwHouse -> "台灣房屋"
}
internal fun RunMode.label() = when (this) {
    RunMode.FOREGROUND -> "前景（最多 3 頁）"; RunMode.BACKGROUND_10M -> "B1 切換 App 10 分鐘"
    RunMode.LOCKED_10M -> "B2 鎖屏 10 分鐘"; RunMode.FULL -> "完整輪次（有頁數上限）"
}

@Composable
fun ProbeScreen(
    state: RunRecord?, onStart: (ProbeQuery, RunMode) -> Unit, onCancel: () -> Unit,
    onResume: (String) -> Unit, onExport: (String) -> Unit,
    busy: Boolean, message: String, listings: List<ProbeListing>, history: List<String>,
    onHistory: (String) -> Unit, onRefresh: () -> Unit, onCollection: (ProbeQuery, RunMode) -> Unit,
    onSelfCheck: () -> Unit, selfChecks: List<Pair<String, Boolean>>,
    notificationsEnabled: Boolean, onNotification: () -> Unit,
    onImage: (ProbeListing) -> Unit, image: ImageBitmap?, imageMessage: String,
    device: String, browser: @Composable () -> Unit,
) {
    var siteCode by rememberSaveable { mutableIntStateOf(0) }
    var district by rememberSaveable { mutableStateOf("中和區") }
    var price by rememberSaveable { mutableStateOf("2000") }
    var size by rememberSaveable { mutableStateOf("20") }
    var rooms by rememberSaveable { mutableStateOf("") }
    var types by rememberSaveable { mutableStateOf("R1,R2") }
    var age by rememberSaveable { mutableStateOf("0") }
    var parking by rememberSaveable { mutableStateOf("") }
    var modeName by rememberSaveable { mutableStateOf(RunMode.FOREGROUND.name) }
    var inputError by remember { mutableStateOf("") }
    fun start(collection: Boolean) {
        val query = try { ProbeInput(siteCode, district, price, size, rooms, types, age, parking).query(collection) }
        catch (_: IllegalArgumentException) { inputError = "條件無效或此來源不支援代碼，請修正後再開始。"; return }
        inputError = ""
        if (collection) onCollection(query, RunMode.valueOf(modeName)) else onStart(query, RunMode.valueOf(modeName))
    }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(stringResource(R.string.probe_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.headlineSmall)
            Text(device)
            Text("資料只存本機；網站、背景與鎖屏能力須逐站實測。")
        }
        item {
            Choice("來源", probeSources.keys.map { it.code.toString() to it.label() }, siteCode.toString(), !busy) { siteCode = it.toInt() }
            Choice("新北市行政區", listOf("中和區", "永和區", "新店區").map { it to it }, district, !busy) { district = it }
            Input("最高總價（萬，0 不限）", price, !busy) { price = it }
            Input("最小坪數（0 不限）", size, !busy) { size = it }
            Input("房數代碼（空白不限；例如 2,3 或 3~）", rooms, !busy) { rooms = it }
            Input("型態代碼（預設 R1,R2；依站支援）", types, !busy) { types = it }
            Text("用途：住宅 1；未知欄位不代表已符合條件。")
            Input("最大屋齡（年，0 不限）", age, !busy) { age = it }
            Input("車位代碼（空白不限；PF／PM，以逗號分隔）", parking, !busy) { parking = it }
            Choice("模式", RunMode.entries.map { it.name to it.label() }, modeName, !busy) { modeName = it }
            Text("條件：${Site.entries.first { it.code == siteCode }.label()}／$district／最高 $price 萬／最小 $size 坪")
            Text("B1／B2：第 1 頁保存後切換 App／鎖屏，保持 10 分鐘；提早結束可能是證據不足。")
            if (inputError.isNotEmpty()) Text(inputError, color = MaterialTheme.colorScheme.error)
            Button(onClick = { start(false) }, enabled = !busy) { Text("開始單站") }
            OutlinedButton(onClick = { start(true) }, enabled = !busy) { Text("七站依序開始") }
            if (busy) Text("忙碌中；衝突作業不排隊，請完成後手動重試。")
            Button(onClick = onCancel, enabled = busy) { Text("取消目前作業") }
        }
        item {
            if (!notificationsEnabled) {
                Text("通知未開啟，通知列可能沒有進度與取消按鈕；可回到 App 取消。")
                OutlinedButton(onClick = onNotification) { Text("設定通知權限") }
            }
            if (message.isNotBlank()) Text(message)
            OutlinedButton(onClick = onSelfCheck, enabled = !busy) { Text("執行本機自檢（不連網站）") }
            selfChecks.forEach { (name, passed) -> Text("${if (passed) "PASS" else "FAIL"}：$name") }
        }
        item {
            Text("網站驗證區", style = MaterialTheme.typography.titleMedium)
            Text("需要人工驗證時在下方完成，再按繼續。HTTP 來源無可續用網頁時請取消重跑。")
            browser()
            if (state?.state == RunState.WAITING_FOR_USER) {
                Button(onClick = { onResume(state.runId) }) { Text("已完成驗證，繼續同一作業") }
            }
        }
        item {
            Text("結果", style = MaterialTheme.typography.titleMedium)
            if (state == null) Text(stringResource(R.string.not_started)) else {
                Text("runId：${state.runId}\n執行版本：${state.appVersion ?: "未知（舊紀錄）"}")
                Text("${state.query.site.label()}／${state.query.district}／${state.mode.label()}\n${state.evidenceSource}／${state.state}")
                Text("已保存頁：${state.savedPageIndices.size}／有效筆數：${listings.size}")
                Text("背景內新頁：${backgroundSavedPages(state.events)}／背景判讀：${backgroundVerdict(state.events, state.mode)}")
                Text("停止／事件原因：${state.events.lastOrNull { it.reason != null }?.reason ?: "無"}")
                Text("最後保存時間：${state.events.lastOrNull { it.kind == ProbeEventKind.PAGE_SAVED }?.utc ?: "尚無"}")
                Text("完成僅代表本次模式已結束；完整性、未知欄位及背景判讀請分開核對。")
                OutlinedButton(onClick = { onExport(state.runId) }, enabled = !busy) { Text("匯出此筆 ZIP") }
            }
        }
        if (imageMessage.isNotEmpty()) item {
            Text(imageMessage)
            image?.let { Image(it, "所選房源縮圖", Modifier.fillMaxWidth().height(180.dp)) }
        }
        items(listings, key = { "${it.sourceSite}-${it.sourceListingKey}" }) { listing ->
            Column {
                Text("${listing.sourceListingKey}｜${listing.totalPrice.toPlainString()} 萬｜${listing.areaPing.toPlainString()} 坪")
                Text("${listing.city}${listing.district}｜屋齡 ${listing.ageYears ?: "未知"}｜樓層 ${listing.floor ?: "未知"}")
                Text("未知／未核實：${listing.unknownFields.sorted().joinToString().ifEmpty { "無註記" }}")
                OutlinedButton(onClick = { onImage(listing) }, enabled = !busy && listing.imageUrl != null) { Text("預覽此張縮圖") }
            }
        }
        item {
            Text("本機歷史", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = onRefresh, enabled = !busy) { Text("重新讀取歷史") }
        }
        items(history, key = { it }) { id -> OutlinedButton(onClick = { onHistory(id) }, enabled = !busy) { Text(id) } }
    }
}

@Composable private fun Input(label: String, value: String, enabled: Boolean, changed: (String) -> Unit) {
    OutlinedTextField(value, { if (it.length <= 129) changed(it) }, label = { Text(label) },
        enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth())
}

@Composable private fun Choice(label: String, options: List<Pair<String, String>>, value: String, enabled: Boolean, changed: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled) { Text("$label：${options.first { it.first == value }.second}") }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (key, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { changed(key); expanded = false }) }
        }
    }
}
