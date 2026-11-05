# HouseLens 七站驗證 APK Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **使用者最新選擇：Native。** 使用 `superpowers:executing-plans`，主代理依序實作；每個 Task 結束後暫停並 review、直接覆寫固定的 [交接文件](../handoff/handoff.md)，等待使用者繼續。所有交接一律寫入 `F:\SourceCode\HouseLens\android\handoff\handoff.md`，不另建編號或日期版本。Context 使用超過60%時也暫停並更新摘要；若環境沒有可靠比例指標，須明說而不捏造讀數。全部實作完成後另做獨立審查。此要求優先於上方通用模板的建議與任何批次執行預設。

**Goal:** 交付使用者可自行安裝的測試 APK，驗證七站採集、切換 App／鎖屏進度及中斷行為，產生可人工核對的逐站證據。

**Architecture:** 獨立 `android/probe/` Gradle 工程、單一 `app` module。純 Kotlin 的來源轉接器、執行器與證據判讀共用資料契約；Android 層提供 HTTP／WebView、前景服務、Compose 操作頁與使用者主動匯出。資料只在 App 私有目錄，不接 Supabase；網站支援能力是試驗結果，不預設成功。

**Tech Stack:** Kotlin、Compose Material 3、Coroutines、Android WebView／AndroidX WebKit、HTTPS 標準 API、jsoup、kotlinx.serialization JSON、JUnit。具體版本與建置驗證見 Task 1。

**Spec:** [S25+ 驗證方案](2026-11-03-s25plus-validation.md)、[七站驗證規格](../03-crawler-feasibility.md)；正式產品方向參照 [完整移植設計](../01-android-design.md)，本計畫只實作前兩份文件的獨立驗證 APK。

## Global Constraints

- 更新：2026-11-06；狀態：使用者已選 Native 與逐 Task 暫停／review，Task1–7已完成實作、測試與review，停點等待繼續Task8；Task8–14未開始，尚無七站真機成功證據。
- 所有 Android plan、tasks、驗證紀錄都放在 `F:\SourceCode\HouseLens\android\` 下；不修改 Web／後端程式或正式資料庫。
- 裝置：Samsung S25+、One UI 9.0、Android 17。使用者可安裝及操作，**不開 USB 偵錯**；不可把 adb／connectedAndroidTest 列成使用者驗收前提。
- Kotlin＋Jetpack Compose；`minSdk=26`、`compileSdk/targetSdk=37`。API 26 是工程基線，不表示已驗證 Android 8；真機結論限本 S25+ 組合。
- 七站均保留；測試順序 591、樂屋、永慶、住商、信義、中信、台灣房屋。每次單站單區，另提供七站循序模式，不平行爬取。
- 只有一個正式 Supabase 專案；本 APK 無 Supabase SDK、連線設定、Google 登入或正式房源寫入。
- 抓取／清除衝突採提示忙碌、稍後手動重試，不自動排隊。本 APK 只實作裝置內單一作業；跨 Web／手機互斥留待資料共用計畫。
- 普通 APK、無 root、無電腦代跑、無 Playwright runtime、無自動破解網站驗證；背景可靠性由真機報告判定。
- B1 切換 App 10 分鐘、B2 鎖屏 10 分鐘，各至少兩次；省電設定、完整輪次與未驗證系統事件另列。未測、失敗、證據不足均不得算通過。
- 不自動上傳日誌，不匯出 cookie、token、帳號、電話、裝置序號、原始整頁或原始 BFF 本文；APK 不含服務端密鑰。
- 實作期間的下載、安裝與實站請求遵守當時工具權限；Task1已依工具權限下載並建置；實站請求尚未開始。

## Review Focus

1. 過濾後零筆／推薦頁／重複頁不能假報完整掃描；以原始資料與結束證據判斷（Task 2、5–11）。
2. 前一頁的晚到回應、第三方 frame 或過大訊息不得污染當頁／洩漏日誌（Task 4、5）。
3. 心跳、快取與回前景才補抓不能當背景成功；使用單調時間與同一程序區間判讀（Task 3、12）。
4. 取消、磁碟失敗、部分落盤或程序死亡不得變成成功／重複計數（Task 3、12、13）。
5. 驗證畫面、通知拒絕、系統 timeout／renderer 終止必須有可回報結果，模擬事件不能冒充真機證據（Task 4、12–14）。

---

## 檔案與責任

Task1工程與模型已建立，其餘為未來檔案；`P = android/probe`、`M = P/app/src/main/java/tw/houselens/probe`、`T = P/app/src/test/java/tw/houselens/probe`、`F = P/app/src/test/resources/fixtures`。這些縮寫只減少本文重複，實際路徑不含 `P/M/T/F`。

| 檔案／目錄 | 責任 |
| --- | --- |
| `P/settings.gradle.kts`、`P/build.gradle.kts`、`P/gradle.properties`、`P/gradle/libs.versions.toml`、`P/gradle/wrapper/gradle-wrapper.properties`、`P/gradle/wrapper/gradle-wrapper.jar`、`P/gradlew`、`P/gradlew.bat` | 單 module 建置與鎖定版本，wrapper 取官方發行檔並驗 checksum |
| `P/app/build.gradle.kts`、`P/.gitignore`、`P/app/src/main/AndroidManifest.xml`、`P/app/src/main/res/values/strings.xml`、`P/app/src/main/res/values/themes.xml`、`P/app/src/main/res/drawable/ic_probe.xml` | App 配置、權限、繁中名稱與通知小圖示 |
| `M/ProbeModels.kt`、`M/ProbeRules.kt` | 查詢／頁面／事件資料及過濾、分頁決策 |
| `M/ProbeStore.kt`、`M/ProbeReport.kt` | 每頁原子落盤、恢復檢查、白名單匯出與證據判讀 |
| `M/PageTransport.kt`、`M/HttpPageTransport.kt`、`M/WebViewSession.kt` | 採集介面、HTTP、單一 WebView 生命週期與受限訊息橋 |
| `M/sources/F591Source.kt`、`RakuyaSource.kt`、`YungchingSource.kt`、`HBHousingSource.kt`、`SinyiSource.kt`、`CtHouseSource.kt`、`TwHouseSource.kt`（後六個也在 `M/sources/`） | 七站 URL／body／解析／空頁與分頁證據 |
| `M/ProbeRunner.kt`、`M/ProbeService.kt`、`M/DeviceSnapshot.kt` | 單作業流程、通知／背景／取消、裝置資訊 |
| `M/MainActivity.kt`、`M/ProbeScreen.kt`、`M/ProbeSelfCheck.kt` | 操作、結果、WebView 掛載與無 adb 本機檢查 |
| `P/app/src/main/assets/probe-bridge.js`、`P/app/src/main/assets/selfcheck.html` | 591 回應觀察候選及固定本機測試頁；不含登入憑證 |
| `T/*Test.kt`、`T/Fixtures.kt`、`F/各站樣本` | JVM 邏輯測試與從既有 C# 測試移入／新增的合成樣本 |
| `android/validation/README.md`、`android/validation/summary.md`、`android/validation/builds/<version>/` | 安裝回報步驟、逐站結論、APK／hash／建置摘要 |

實作不引入 DI framework、Room、WorkManager、完整導航或離線同步。試驗用 JSON 檔足以保存有限頁面；正式 App 是否沿用另行決定。

## 共用契約（Task 1／2 建立；後續任務使用相同名稱）

`ProbeModels.kt`：

- `Site`：F591=0、Rakuya=1、Sinyi=2、Yungching=3、TwHouse=4、HBHousing=5、CtHouse=6，沿用既有來源編碼。
- `ProbeQuery(site: Site, city: String, district: String, maxTotalPrice: BigDecimal, minSizePing: BigDecimal, rooms: String, typeCodes: String, useCode: String, maxAgeYears: Int, parkingCodes: String)`；語意沿用 `backend/src/HouseLens.Application/Crawling/ISourceScraper.cs` 的 `DistrictCriteria`。
- `ProbeListing`：沿用 `PropertyDto.cs` 的同名 camelCase 欄位、nullable 與單位；價格／坪數用 BigDecimal。`postedDate` 保存來源可提供的 ISO 日期字串或 null，不假造時區；額外 `unknownFields: Set<String>` 註記舊契約以 false／0 表示的未知值。
- `PageRequest(url: String, method: String = "GET", body: String? = null, browser: Boolean = false, warmupUrl: String? = null, captureJson: Boolean = false)`；網址只由來源程式建立，UI 不接受任意網址。
- `CapturedJson(url: String, body: String)` 與 `RawCapture(documentUrl: String, statusCode: Int?, html: String?, json: List<CapturedJson>, navigationId: Long, networkObserved: Boolean)`：只在記憶體用於解析，不直接序列化到匯出檔。
- `NextPage` = `MORE/END/UNKNOWN`；`PageKind` = `RESULTS/EMPTY/CHALLENGE/INVALID`；`PageResult(kind: PageKind, rawKeys: List<String>, listings: List<ProbeListing>, excluded: Map<String,String>, next: NextPage, evidence: String)`。`excluded` 記不含個資的固定理由代碼；`evidence` 也是固定代碼，不能塞原始 HTML。
- `RunMode` = `FOREGROUND/BACKGROUND_10M/LOCKED_10M/FULL`；`RunState` = `RUNNING/WAITING_FOR_USER/COMPLETED/CANCELLED/INTERRUPTED/FAILED/TRUNCATED/INCONCLUSIVE`。
- `ProbeEvent(runId: String, processId: String, site: Site, pageIndex: Int?, kind: ProbeEventKind, utc: String, elapsedMs: Long, navigationId: Long?, rawCount: Int?, acceptedCount: Int?, networkObserved: Boolean?, reason: ProbeReason?, evidenceSource: EvidenceSource, newRawCount: Int? = null)`。processId 是每次程序啟動的隨機值，不是裝置識別碼；事件另以 APP_BACKGROUND／APP_FOREGROUND／SCREEN_OFF／SCREEN_ON 表示狀態區間。kind/reason使用enum，只接受程式定義的代碼，拒絕任意網頁文字；PAGE_SAVED與終止事件由Store產生，newRawCount由已存頁rawKeys計算。
- `EvidenceSource` = `LIVE/LOCAL_SELF_CHECK/SIMULATED`，RunRecord 與 ProbeEvent 必須明確提供 evidenceSource，與 RunMode 分開；Task3 判讀拒絕非 LIVE 證據。`RunRecord` 保存 runId、processId、query、mode、state、開始時裝置快照、已落盤頁索引及時間事件；`BackgroundVerdict` = `PASSED/FAILED/INCONCLUSIVE/NOT_TESTED`。

`PageTransport.kt` 與來源介面：

```kotlin
interface PageTransport {
    suspend fun capture(request: PageRequest, navigationId: Long): RawCapture
    fun cancel()
}
interface SourceAdapter {
    val site: Site
    val maxPages: Int
    fun request(query: ProbeQuery, pageIndex: Int): PageRequest
    fun parse(capture: RawCapture, query: ProbeQuery, pageIndex: Int): PageResult
}
```

pageIndex 一律從 0 起；來源轉 URL 時才換成網站頁碼。七個Source均為無狀態Kotlin `object`；parse先取得原始keys、套用站別型態條件與共同exclusionReason，再回傳有效listings。原始搜尋卡缺ID／價格解析失敗必須記解析異常，不可靜默丟掉後當完整頁。WebViewSession 與 HttpPageTransport 各實作 PageTransport；Runner 按 request.browser 選取，不建立泛用爬蟲框架。

`T/Fixtures.kt` 只供測試：`testQuery(site: Site, district: String = "中和區")` 回傳新北市、最高100000萬、最小0坪、R1,R2／住宅1，其餘不限；`captureFixture(source: SourceAdapter, query: ProbeQuery, path: String, jsonUrl: String? = null): RawCapture` 從resources讀取樣本，documentUrl=source.request(query,0).url，jsonUrl非null才包成CapturedJson，否則放html；navigationId=1、statusCode=200、networkObserved=false。fixture永遠不能當作LIVE網路證據。

## 執行順序與檢查規則

主代理依 1→2→3→4→5→6→7→8→9→10→11→12→13→14 順序實作；每 Task 做完停止啟動下一 Task，先 review 並回報，等使用者繼續。可委派唯讀研究／審查，不分派實作。首次可安裝 APK 在 Task 1，具採集能力的交付在 Task 13；使用者真機結果屬 Task 14，不能靠開發機測試勾選。

以下 Gradle 命令工作目錄一律是 `F:\SourceCode\HouseLens\android\probe`。每個 RED 步驟必須是預期斷言失敗或該任務待建 API 尚缺，不把 SDK／下載失敗算成有效 RED。每個任務通過後只 stage 該任務的明列檔案並提交；執行前檢查工作樹，不能 `git add .` 把既有未追蹤檔一起提交。

### Task 1: 可建置 APK 與資料契約

**Files:** Create 上述全部 Gradle／manifest／resources 檔、`M/ProbeModels.kt`、最小 `M/MainActivity.kt`；Test `T/ProbeModelsTest.kt`。測試helper在Task5首次需要時建立。

**Interfaces:** Produces 共用模型與 `tw.houselens.probe` applicationId；首次畫面只顯示版本與「尚未開始驗證」，不執行網站請求。

- [x] **Step 1 — 準備可重現工具鏈。** 唯讀盤點已發現 PATH 上是 Microsoft JDK 11.0.16，預設 Android SDK 只有 API／Build Tools 31；尚無工程。實作時安裝或選用 JDK 17、SDK platform 37、Build Tools 36.0.0、command-line tools；使用官方下載與 checksum，未取得必要權限前不安裝。JDK patch、下載 URL/hash 寫入 build-info，不覆蓋全域 Java 設定。
- [x] **Step 2 — 建最小工程與紅測試。** 固定 AGP 9.1.1／Gradle 9.3.1，AGP 內建 Kotlin 2.2.10，Compose compiler plugin 2.2.10；不再套 `org.jetbrains.kotlin.android`。依賴起始鎖版：Compose BOM 2026.09.00（UI／Material3）、activity-compose 1.10.1、webkit 1.14.0、coroutines-android/test 1.10.2、serialization-json 1.9.0、jsoup 1.23.2、JUnit 4.13.2；不用 serialization compiler plugin，JSON 透過 JsonElement 明確轉換。建立 `ProbeModelsTest.sourceCodesMatchExistingDatabase` 驗證七站契約碼；精度往返測試放Task3，不替標準BigDecimal寫鏡像測試。
- [x] **Step 3 — 跑 RED。** `./gradlew.bat :app:testDebugUnitTest --tests '*ProbeModelsTest'`；期望模型缺少／斷言失敗。先修復依賴解析不相容再重跑，不降低 targetSdk。版本組合目前只查文件，若解析需要調整，更新 version catalog 與 build-info，再跑本任務；禁止動態 `+` 版本。
- [x] **Step 4 — 實作模型與基礎配置。** java/kotlin JVM target 17；關閉 App backup、禁止明文流量。P/.gitignore 忽略 `.gradle/`、`**/build/`、`local.properties`、私有簽署資料；不修改根目錄忽略檔。此時只宣告 INTERNET。
- [x] **Step 5 — 驗證與提交。** `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 全部 PASS；`app/build/outputs/apk/debug/app-debug.apk` 存在。模型測試至少包括：

```kotlin
assertEquals(listOf(0, 1, 2, 3, 4, 5, 6),
    listOf(Site.F591, Site.Rakuya, Site.Sinyi, Site.Yungching, Site.TwHouse, Site.HBHousing, Site.CtHouse).map { it.code })
```

負價格與不支援的行政區等輸入驗證已於Task2完成。提交訊息 `build(android): bootstrap isolated probe app`。

### Task 2: 分頁與篩選決策，不複製舊漏抓條件

**Files:** Create `M/ProbeRules.kt`；Test `T/ProbeRulesTest.kt`；Modify `M/ProbeModels.kt`（加入 PageDecision）。

**Interfaces:** Produces `validateQuery(query: ProbeQuery): Unit`、`exclusionReason(item: ProbeListing, query: ProbeQuery): String?`、`nextDecision(page: PageResult, previousRawKeys: Set<String>, pageIndex: Int, pageLimit: Int): PageDecision`；PageDecision=`CONTINUE/COMPLETE/WAIT_FOR_USER/FAIL/TRUNCATE/INCONCLUSIVE`。

- [x] **Step 1 — 寫測試。** 原始10卡全被篩掉、next=MORE仍 CONTINUE；EMPTY+END才 COMPLETE；CHALLENGE為 WAIT；無 selector／未知結構為 FAIL；到上限且 MORE／UNKNOWN為 TRUNCATE；整頁原始 ID 重複但無可靠末頁證據為 INCONCLUSIVE。中信「板橋區」不得混入「中和區」有效資料，nullable 不被擅自改成真值。
- [x] **Step 2 — 跑 RED。** `./gradlew.bat :app:testDebugUnitTest --tests '*ProbeRulesTest'`。
- [x] **Step 3 — 實作上述三函式。** 共同檢查來源 ID、正價格、城市／行政區與現有可判定條件；站別型態／房數代碼留來源處理。缺少欄位只能標未知，不能捏造「已符合」；記錄排除理由。有效筆數不參與判斷原始頁是否已盡。
- [x] **Step 4 — 跑 GREEN。** 同上；重要斷言使用具體輸入：

```kotlin
val filteredPage = PageResult(PageKind.RESULTS, listOf("A"), emptyList(), mapOf("A" to "over_price"), NextPage.MORE, "next_link")
assertEquals(PageDecision.CONTINUE, nextDecision(filteredPage, emptySet(), 0, 30))
assertEquals(PageDecision.TRUNCATE, nextDecision(filteredPage, emptySet(), 29, 30))
```

- [x] **Step 5 — 提交。** `fix(android): distinguish filtered pages from end of results`。


Task2實作決定詳見 [執行紀錄](../tasks/task2.md)：probe僅接受新北中和／永和／新店；代碼先驗長度與格式，語意由來源／Task13驗證。共同篩選不直接套用minSizePing，因現有來源的主建／總建基準不同。exclusionReason回null只代表無可證排除，不表示未知屋齡／車位已符合。END需非空固定證據代碼，UNKNOWN仍有新raw keys時可繼續探測，達上限則截斷。
### Task 3: 可恢復的本機紀錄與可信背景判讀

**Files:** Create `M/ProbeStore.kt`、`M/ProbeReport.kt`、`M/ProbeJson.kt`；Modify `M/ProbeModels.kt`（事件enum、新raw計數及尾端遺失旗標）；Test `T/ProbeStoreTest.kt`、`T/ProbeReportTest.kt`。

**Interfaces:** `ProbeStore(root: File, processId: String)` 提供 `create(record: RunRecord)`、`savePage(runId: String, index: Int, result: PageResult)`、`append(event: ProbeEvent)`、`finish(runId: String, state: RunState)`、`load(runId: String): RunRecord`、`loadPage(runId: String, index: Int): PageResult`、`export(runId: String, output: OutputStream)`；`backgroundVerdict(events: List<ProbeEvent>, mode: RunMode): BackgroundVerdict`。load只把舊processId且沒有終止記錄的run標INTERRUPTED，不能改寫已完成的歷史。

- [x] **Step 1 — 寫測試。** 成功頁寫入後重載筆數一致；同頁重送不加倍；`.tmp` 半寫入不算完成；正式頁 JSON 損毀明確報錯；RUNNING 程序已不在時呈 INTERRUPTED。匯出不得含測試 cookie／token／電話／address／title／完整 URL 的祕密字串；金額 `798.00` 往返仍精確。
- [x] **Step 2 — 寫背景測試並跑 RED。** 只有心跳／networkObserved=false→INCONCLUSIVE；請求在背景、解析在回前景後→FAILED；切換滿600秒且第60/300/540秒請求均在背景取得新頁並完成解析／落盤才 PASSED；螢幕事件不符、processId變動、未滿600秒→INCONCLUSIVE。FOREGROUND不評背景（NOT_TESTED）；FULL需run完成且至少有背景內新頁成功，仍不推論10分鐘以上耐久性。`./gradlew.bat :app:testDebugUnitTest --tests '*ProbeStoreTest' --tests '*ProbeReportTest'`。
- [x] **Step 3 — 實作落盤。** app私有 `filesDir/runs/<UUID>/`；每頁獨立JSON，temp寫入＋flush/sync＋同目錄原子rename，成功後才增加checkpoint。重開由完整頁重建 seen／計數；不靠未同步的另一份計數檔。時間事件只接受類型化白名單，append後flush；尾端半行標註遺失。磁碟失敗停止採集，UI報錯，不能繼續宣告已保存。
- [x] **Step 4 — 實作匯出與判讀，再跑 GREEN。** ZipOutputStream 只產 `result.md/events.jsonl/sample.json`；sample含來源ID、價格、坪數、地區、排除理由及未知欄位，無原始頁與憑證。紀錄來源是 LOCAL_SELF_CHECK／LIVE，分開判讀；同run/process的單調時間為基準，UTC只供人讀。生產程式中的時鐘與檔案錯誤以函式注入測試，不建多層repository。
- [x] **Step 5 — 提交。** `feat(android): persist probe evidence and reject false background passes`。


Task3契約細節見 [執行紀錄](../tasks/task3.md)：load依完整頁重建索引，loadPage讀回內容；半行事件回傳eventTailIncomplete及EVIDENCE_INCOMPLETE。背景判讀使用第一段連續600秒區間，三個請求窗口為[60,300)、[300,540)、[540,600)秒，須於600秒內落盤。pageIndex與navigationId均不得重用充數。Store為單程序單例同步IO；頁原子提交後才記事件，未知或缺失證據不補造。明細、終止事件及匯出JSON採明列欄位編解碼。
### Task 4: HTTP 與 WebView 採集邊界

**Files:** Create `M/PageTransport.kt`、`M/HttpPageTransport.kt`、`M/WebViewSession.kt`、`P/app/src/main/assets/probe-bridge.js`、`P/app/src/main/assets/selfcheck.html`；Test `T/CaptureGuardTest.kt`、`T/HttpPolicyTest.kt`。

**Interfaces:** 依共用 PageTransport；WebViewSession 額外 `attach(container: ViewGroup)`、`detach()`、`destroy()`、`clearSession()`；純函式 `acceptCapture(origin: String, expectedOrigin: String, mainFrame: Boolean, receivedId: Long, expectedId: Long, bytes: Int): Boolean`。HTTP 補 `suspend fun loadImage(site: Site, url: String): ByteArray` 供Task13預覽、`suspend fun checkRobots(url: String): Boolean?` 供Runner對兩類transport檢查（null=未知）；不回傳或記錄敏感headers。

- [x] **Step 1 — 寫邊界測試。** `591.com.tw.evil.test`、第三方iframe、舊navigationId、超過2MiB橋接訊息拒絕；有效來源＋當頁接受。HTTP 取消不重試；最多3次總嘗試，退避2／4秒且仍滿足每個App發起請求至少3秒間距；429不自動密集重試，回報限流。robots明確禁用路徑停止，取不到內容記未知，不快取某一路徑結論給整站。
- [x] **Step 2 — 跑 RED。** `./gradlew.bat :app:testDebugUnitTest --tests '*CaptureGuardTest' --tests '*HttpPolicyTest'`。
- [x] **Step 3 — 實作 HTTP。** HttpsURLConnection 在 IO dispatcher，連線15秒／單次總30秒，取消立即disconnect；每請求3秒＋0–1999ms jitter，body上限8MiB。只接受來源生成的HTTPS與允許的來源／圖片CDN host，redirect重新檢查，不關閉TLS驗證；Accept-Encoding只宣告實際可解壓格式。沿用 HttpFetcher 的 robots／限速意圖，修正按host快取允許結果的問題為快取內容、逐path判斷；固定測試其現有支援的規則，不另造通用robots套件。UA用實際WebView UA記錄其值，不虛報Windows版Chrome。信義圖片加 `Referer: https://www.sinyi.com.tw/`，最多取一張縮圖且8MiB上限，decode另作尺寸限制。
- [x] **Step 4 — 實作 WebView 候選。** 所有WebView操作在main thread，service持有以application context建立的單一實例；Activity只attach/detach，旋轉不另開一個實例。背景不主動呼叫pauseTimers、無overlay或保持螢幕常亮。導航30秒、等待DOM最多再5秒；DOM不能只憑onPageFinished就視為搜尋完成。SSL錯誤取消，file/content存取關閉，Safe Browsing保持開啟。只在明列來源origin掛橋、main-frame判斷、每次導航更換ID；解析失敗不得回空成功。開始新run時清HTTP資源cache（不默默清登入cookie）；WebView與可用的ServiceWorker設定LOAD_NO_CACHE。networkObserved不能因橋接有訊息就設true：需對應本頁的實際網路證據（如可觀察的非零transferSize）；無法分辨service worker／cache回應時false並標證據不足，不宣稱已證明網路持續。
- [x] **Step 5 — 實作並核對橋接。** 用 `DOCUMENT_START_SCRIPT`＋`WEB_MESSAGE_LISTENER` feature detection，在loadUrl前注入短fetch/XHR觀察程式；只複製允許的591 JSON回應，不攔截／改寫原回應，不讀cookie，不用shouldInterceptRequest冒充response-body監聽。JS傳送url/status/content-type/body/navigationId；原生再驗host、主frame、ID與長度。功能不支援則回明確Unsupported，保留DOM候選與缺證狀態。未知網站訊息不寫原始log。renderer退出清除待取頁、重建前停止本次run，不無限重試。
- [x] **Step 6 — 跑 GREEN 與準備本機頁檢查資產。** JVM上述測試PASS；selfcheck.html包含fetch/XHR、延遲上一導航回應與iframe假訊息，透過WebViewAssetLoader的本機HTTPS origin提供測試回應，僅在使用者按本機檢查時啟用。Task 13提供無adb入口；此階段的未實際執行項目仍留unchecked。提交 `feat(android): add bounded HTTP and WebView capture transports`。


Task4細節與限制見 [執行紀錄](../tasks/task4.md)。JVM及Node資產測試已驗證；WebView內實際本機頁仍留Task13，不能把Node結果當真機證據。

- [ ] **後續Task13驗收：** 在APK內執行AssetLoader本機頁的fetch／XHR／iframe／跨導航延遲檢查。

### Task 5: 591 搜尋與 BFF 取證

**Files:** Create `M/sources/F591Source.kt`、`T/F591SourceTest.kt`、`T/Fixtures.kt`、`F/591/search-page1.json`、`page2.json`、`zero-with-recommendations.json`、`fallback.html`（後三個也在 `F/591/`）。

**Interfaces:** `F591Source : SourceAdapter`，maxPages=40；request每頁firstRow=pageIndex×30，captureJson=true；parse只選屬於目前地區／分頁的回應，不能合併無關推薦。

- [x] **Step 1 — 建樣本／測試。** 參照 `F591Scraper.cs` 的 BuildSearchUrl、TryGetTotal、TryParseJsonResponse、ParseJsonListing 與 `ScraperTests.cs` URL測試。既有無BFF fixture，新增合成JSON：page1 key=100001／750萬／28.50坪／total31，page2 key=100002／800萬；total0但含推薦必須EMPTY。測region3/section38、firstRow30/60、PF,PM→parking=1,2,3；舊頁與錯section不得採納。測試 `zeroTotalRejectsRecommendations` 的核心斷言如下，jsonUrl是合成測試URL，不宣稱實站endpoint：

```kotlin
val q = testQuery(Site.F591)
val raw = captureFixture(F591Source, q, "fixtures/591/zero-with-recommendations.json",
    "https://sale.591.com.tw/test/list?regionid=3&section=38&firstRow=0")
assertEquals(PageKind.EMPTY, F591Source.parse(raw, q, 0).kind)
```
- [x] **Step 2 — 跑 RED。** `./gradlew.bat :app:testDebugUnitTest --tests '*F591SourceTest'`。
- [x] **Step 3 — 實作來源。** 支援現碼已列house_list與total路徑；以URI query解析核對region/section/offset，缺少分頁關聯即標未確認，不憑substring。total=0優先排推薦；一頁全過濾仍依total翻頁。HTML備援單獨註記方式，缺少可靠完整性資訊不能宣告BFF等價。缺欄位／body格式錯誤為INVALID。
- [x] **Step 4 — 跑 GREEN。** 上述命令PASS；pageIndex1與2URL及回應均不同，抽出的ID／金額精確。真機取不到body仍是合法的「方法未證明」結果，不得以fixture通過取代。
- [x] **Step 5 — 提交。** `feat(android): add 591 probe with page-correlated evidence`。

Task5實作與裁決見 [執行紀錄](../tasks/task5.md)：第一頁合成樣本補足30卡並核對total；BFF已出現的filter須一致，HTML仍UNKNOWN。新增areaBasis/typeCodes/useCode/rooms/parkingCodes未知註記。20項來源測試及完整72項測試、lint、APK建置通過；尚無實站證據。

### Task 6: 樂屋資料與全過濾頁回歸

**Files:** Create `M/sources/RakuyaSource.kt`、`T/RakuyaSourceTest.kt`、`F/rakuya/apartment.html`、`house.html`、`filtered-page.html`（後兩個同目錄）。

**Interfaces:** RakuyaSource實作SourceAdapter，maxPages=30；warmupUrl=`https://www.rakuya.com.tw`，逐頁以同一session導覽。

- [x] **Step 1 — 寫測試。** 從 `RakuyaScraperTests.cs` 移入ApartmentCard、TransparentHouse與DuplicateCards：ID `0ca48612345678a`／2088萬／主建26.98坪；透天1580萬／總建45坪／44.5年依HALF_EVEN為44；重複卡一筆。補「第一頁全部過濾、第二頁仍有效」與無卡但沒有空狀態的INVALID案例。

```kotlin
val q = testQuery(Site.Rakuya, "新店區")
val page = RakuyaSource.parse(captureFixture(RakuyaSource, q, "fixtures/rakuya/apartment.html"), q, 0)
assertEquals("0ca48612345678a", page.listings.single().sourceListingKey)
assertEquals(0, page.listings.single().areaPing.compareTo(BigDecimal("26.98")))
```
- [x] **Step 2 — 跑 RED。** `./gradlew.bat :app:testDebugUnitTest --tests '*RakuyaSourceTest'`。
- [x] **Step 3 — 實作。** 取 `section.search-obj` 的data-ehid，先保存原始keys再過濾；沿用BuildSearchUrl映射。可靠下一頁／空狀態才能END；目前fixture無此證據時UNKNOWN並受上限保護，不猜selector或默認0卡=完成。禁止複製現有pageResults.Count==0停止條件。
- [x] **Step 4 — 跑 GREEN。** 同上；以兩頁fixture序列核對Task2決策不漏第二頁。
- [x] **Step 5 — 提交。** `feat(android): add Rakuya probe preserving raw pagination evidence`。

Task6實作與裁決見 [執行紀錄](../tasks/task6.md)：沿用住宅白名單與主建優先，HALF_EVEN取整；無可靠末頁／空結果樣本，非空UNKNOWN、無卡INVALID。15項來源測試及完整87項測試、lint、APK建置通過；review修正地區空白與缺型態誤排除。尚未接UI或實站驗證。

### Task 7: 永慶住宅與精選排除

**Files:** Create `M/sources/YungchingSource.kt`、`T/YungchingSourceTest.kt`、`F/yungching/residential.html`、`parking.html`、`empty-featured.html`（後兩個同目錄）。

**Interfaces:** YungchingSource實作SourceAdapter，maxPages=30；BuildSearchUrl使用1-based pg，住宅／型態映射沿現碼。

- [x] **Step 1 — 寫測試。** 移入ResidentialCard→1234567、750萬、28.50坪、12年；BuildingWithParking→9876543、1280萬、true；NonResidentialCard排除。新增「暫無符合物件」搭配本週精選→EMPTY、滿頁全部過濾→不結束、challenge→CHALLENGE。

```kotlin
val q = testQuery(Site.Yungching)
val page = YungchingSource.parse(captureFixture(YungchingSource, q, "fixtures/yungching/empty-featured.html"), q, 0)
assertEquals(PageKind.EMPTY, page.kind)
assertTrue(page.listings.isEmpty())
```
- [x] **Step 2 — 跑 RED。** `./gradlew.bat :app:testDebugUnitTest --tests '*YungchingSourceTest'`。
- [x] **Step 3 — 實作。** 使用 `li.search-result-list-item a.link`，原始卡與有效結果分開；精選不作搜尋ID。缺資料不能吞錯成空。網站驗證轉等待，由使用者處理，不移植Playwright的驗證點擊。
- [x] **Step 4 — 跑 GREEN。** 同上，增加第二頁query `pg=2`斷言。
- [x] **Step 5 — 提交。** `feat(android): add Yungching probe and empty-state checks`。

Task7實作與裁決見 [執行紀錄](../tasks/task7.md)：住宅／型態映射、精選空結果排除、原始卡完整性與未知欄位；16項來源測試及完整103項測試、lint、APK建置通過。review兩項修正已完成，尚未接UI或實站驗證。

### Task 8: 住商原始卡片計數

**Files:** Create `M/sources/HBHousingSource.kt`、`T/HBHousingSourceTest.kt`、`F/hbhousing/residential.html`、`parking.html`、`ten-filtered-cards.html`（後兩個同目錄）。

**Interfaces:** HBHousingSource實作SourceAdapter，maxPages=100，原始頁大小10。

- [ ] **Step 1 — 寫測試。** 移入ResidentialCard→YS202291／750萬／29.05坪；BuildingWithParking→ZS203105／1280萬／true；OutRawCardCount案例；10張原始卡全部被排除仍MORE。重複原始keys不得無限循環，未知空頁不是EMPTY。

```kotlin
val q = testQuery(Site.HBHousing).copy(maxTotalPrice = BigDecimal("1"))
val page = HBHousingSource.parse(captureFixture(HBHousingSource, q, "fixtures/hbhousing/ten-filtered-cards.html"), q, 0)
assertEquals(10, page.rawKeys.size)
assertTrue(page.listings.isEmpty())
assertEquals(NextPage.MORE, page.next)
```
- [ ] **Step 2 — 跑 RED。** `./gradlew.bat :app:testDebugUnitTest --tests '*HBHousingSourceTest'`。
- [ ] **Step 3 — 實作。** 按現碼section class含`@container`與`h3 a`的detail?sn提取卡片，避開fragment重複連結。1–9張正確原始卡可作現有末頁證據；0卡需明確空標記／頁面證據，否則INVALID。
- [ ] **Step 4 — 跑 GREEN。** 同上；rawKeys數量與過濾前卡片相符，已接受資料依來源ID去重。
- [ ] **Step 5 — 提交。** `feat(android): add HBHousing probe with raw-card boundaries`。

### Task 9: 信義 HTTP、SSR 與圖片

**Files:** Create `M/sources/SinyiSource.kt`、`T/SinyiSourceTest.kt`、`F/sinyi/search.html`、`next-data-only.html`、`video-first.html`（後兩個同目錄）。

**Interfaces:** SinyiSource實作SourceAdapter，maxPages=5，原始頁大小20；request使用HTTP，圖片交Task4.loadImage。

- [ ] **Step 1 — 寫測試。** 沿用URL型態 `/apartment-dalou-huaxia-type/`、車位slugs、`3,4,5~`→`/3-up-roomtotal/`。既有測試沒有parser樣本，依現碼語意class建合成卡：3087NK／750萬／28.50坪；只含全站__NEXT_DATA__不能產生結果；首圖影片跳過、Next image解碼、無圖用來源既有fallback。

```kotlin
val q = testQuery(Site.Sinyi)
val page = SinyiSource.parse(captureFixture(SinyiSource, q, "fixtures/sinyi/next-data-only.html"), q, 0)
assertTrue(page.listings.isEmpty())
assertEquals(PageKind.INVALID, page.kind)
```
- [ ] **Step 2 — 跑 RED。** `./gradlew.bat :app:testDebugUnitTest --tests '*SinyiSourceTest'`。
- [ ] **Step 3 — 實作。** 解析div.buy-list-item與LongInfoCard_Type_*語意class，不綁hash後綴。用原始卡計算20筆分頁，不用解析／過濾後數量。未識別合法零筆頁時保守INVALID；新鮮網頁證據才能補空狀態規則。
- [ ] **Step 4 — 跑 GREEN。** 同上；圖像測試確認Referer只限信義圖片、不帶Supabase headers，圖片失敗單獨回報不抹除列表證據。
- [ ] **Step 5 — 提交。** `feat(android): add Sinyi SSR probe and image checks`。

### Task 10: 中信 JSON envelope 與現價

**Files:** Create `M/sources/CtHouseSource.kt`、`T/CtHouseSourceTest.kt`、`F/cthouse/results.json`、`discounted.json`、`error.json`（後兩個同目錄）。

**Interfaces:** CtHouseSource實作SourceAdapter，maxPages=100；POST `https://buy.cthouse.com.tw/api/house_list.ashx`，JSON `{arg,page}`，page=pageIndex+1；request.captureJson=true、browser=false，HttpPageTransport將JSON body放進RawCapture.json而非html。

- [ ] **Step 1 — 寫測試。** 移入ResidentialItem→2097688／788萬／8.18坪；DiscountedItem取sell_price=798而非原委託價；重複ID一筆。補RS!=OK、houses缺失／錯型、totalpage缺失或不合理→INVALID；RS=OK且合法零筆→EMPTY。跨區fixture本來只驗「解析成板橋」，本APK經共同篩選後須拒絕混入中和。

```kotlin
val q = testQuery(Site.CtHouse)
val page = CtHouseSource.parse(captureFixture(CtHouseSource, q, "fixtures/cthouse/discounted.json",
    CtHouseSource.request(q, 0).url), q, 0)
assertEquals(0, page.listings.single().totalPrice.compareTo(BigDecimal("798")))
```
- [ ] **Step 2 — 跑 RED。** `./gradlew.bat :app:testDebugUnitTest --tests '*CtHouseSourceTest'`。
- [ ] **Step 3 — 實作。** BuildSearchArg沿現碼城市／區域與條件順序，JSON序列化不拼接未跳脫文字。先解析完整envelope與原始keys，再做住宅／價格／地區過濾；根據totalpage翻頁，不因有效0筆提早停。所有數值以原字串轉BigDecimal。
- [ ] **Step 4 — 跑 GREEN。** 同上；加入兩頁序列第一頁全過濾、第二頁有效的斷言。
- [ ] **Step 5 — 提交。** `feat(android): add CtHouse envelope and current-price validation`。

### Task 11: 台灣房屋建坪與未知欄位

**Files:** Create `M/sources/TwHouseSource.kt`、`T/TwHouseSourceTest.kt`、`F/twhouse/residential.html`、`discounted.html`、`empty.html`（後兩個同目錄）。

**Interfaces:** TwHouseSource實作SourceAdapter，maxPages=30；HTTP列表。舊ParseDetail方法沒有接入FetchDistrictAsync，本試驗不假裝列表已得到詳情欄位。

- [ ] **Step 1 — 寫測試。** 移入ResidentialCard→A123456789／760萬／34.56坪／25年；DiscountedCard→TF02157997／1480萬；min25坪URL含 `/25up-ping/fullBuildingPing-ping_type/`。加入「暫無查詢物件」→EMPTY、全過濾頁仍翻頁、缺地址／樓層／車位時unknownFields明示，不把false當已確認無車位。

```kotlin
val q = testQuery(Site.TwHouse).copy(minSizePing = BigDecimal("25"))
assertTrue(TwHouseSource.request(q, 0).url.contains("/25up-ping/fullBuildingPing-ping_type/"))
val page = TwHouseSource.parse(captureFixture(TwHouseSource, q, "fixtures/twhouse/residential.html"), q, 0)
assertTrue("hasParking" in page.listings.single().unknownFields)
```
- [ ] **Step 2 — 跑 RED。** `./gradlew.bat :app:testDebugUnitTest --tests '*TwHouseSourceTest'`。
- [ ] **Step 3 — 實作。** 使用現碼有效/buy/卡片選取與住宅過濾，排除導覽連結；價格取現價、建坪非地坪。保留列表本來缺值的限制，結果欄位矩陣寫「未取得」，若完整需求需詳情取得，列為後續阻礙，不自行增加逐屋大量請求。
- [ ] **Step 4 — 跑 GREEN。** 同上；一頁全被過濾不得沿用舊 `pageResults.Count==0` 完成邏輯。
- [ ] **Step 5 — 提交。** `feat(android): add TwHouse probe with explicit unknown fields`。

### Task 12: 單一作業、背景服務與中斷收尾

**Files:** Create `M/ProbeRunner.kt`、`M/ProbeService.kt`、`M/DeviceSnapshot.kt`；Modify manifest；Test `T/ProbeRunnerTest.kt`、`T/ProbeRecoveryTest.kt`。

**Interfaces:** `ProbeRunner.start(query: ProbeQuery, mode: RunMode): Boolean`（忙碌false）、`resume(runId: String): Boolean`、`cancel()`、`val state: StateFlow<RunRecord?>`；constructor接store、來源map、兩個transport與可替換clock/delay；Service local binder供Activity讀狀態與操作，WebView只attach同一實例。resume只接受同程序且WebView仍保留的WAITING_FOR_USER作業；程序死亡或session失效時false並提示重新開始，保留舊run供匯出。背景測試一旦需人工介入，本次不能改判為無人值守通過。

- [ ] **Step 1 — 寫Runner紅測試。** Fake transports依序回傳filtered→valid→END，最後只計有效唯一ID；second start=false、不建立第二run；取消等待／請求不再發頁；晚到回應無效；存檔失敗FAIL；cap未盡TRUNCATED；驗證頁WAITING_FOR_USER且無自動重試；沒有terminal marker的舊run為INTERRUPTED。用coroutines-test虛擬時間，不真的sleep10分鐘。
- [ ] **Step 2 — 跑 RED。** `./gradlew.bat :app:testDebugUnitTest --tests '*ProbeRunnerTest' --tests '*ProbeRecoveryTest'`。
- [ ] **Step 3 — 實作流程。** 只有可見Activity的使用者點擊可啟動服務；兩種transport皆先呼叫checkRobots。FOREGROUND至多3頁作初測，未到末頁只能記樣本完成／TRUNCATED，不冒充完整掃描；FULL使用每站上限。B1/B2先抓第1頁，待收到背景／螢幕關閉事件後，分別在+60/+300/+540秒發後續頁，至600秒收尾。待測頁不足→INCONCLUSIVE，不循環重抓湊時間。每成功解析／落盤一頁才推進，來源驗證與未知結構採Task2決策。WAITING_FOR_USER停止網路工作並釋放wake lock，service最多保留5分鐘讓使用者回到同一WebView操作；按繼續才啟動新段。逾時保存checkpoint、結束service與WebView，結果為INTERRUPTED／challenge_wait_timeout。重開時若session失效明示重新開始，不承諾程序死亡後自動續跑。
- [ ] **Step 4 — 接Android生命週期。** manifest加入FOREGROUND_SERVICE、FOREGROUND_SERVICE_DATA_SYNC、POST_NOTIFICATIONS、ACCESS_NETWORK_STATE、WAKE_LOCK；service exported=false、type=dataSync、START_NOT_STICKY；及時startForeground，通知含返回／取消。Activity stop不取消工作，不持有Activity引用。RUNNING使用有逾時的PARTIAL_WAKE_LOCK：B1/B2總時限15分鐘（含準備與10分鐘觀察），其他單站至多90分鐘；Runner也以相同界限停止並記TRUNCATED，結束／等待／取消一律finally釋放。這是避免CPU休眠的候選實作，不豁免Doze，必須記錄耗電。SCREEN_ON/OFF與Activity lifecycle、isInteractive、elapsedRealtime記事件；每次start重讀初態。`onTimeout`取消並立即stopSelf，資料依既有checkpoint重建，不在callback等待長IO。renderer gone、磁碟錯誤、系統啟動拒絕各有固定reason。
- [ ] **Step 5 — 補恢復／系統分支測試並跑 GREEN。** 注入timeout／renderer failure→不得COMPLETED；elapsedRealtime跨程序不拼接背景成功；取消遺留IO不改新run；等待5分鐘必收尾，所有停止路徑釋放wake lock。七站循序模式是單一明確按鈕啟動的測試集合，遇人工驗證暫停集合，不是衝突作業排隊。自動休眠／強制停止真機仍等Task14。
- [ ] **Step 6 — 提交。** `feat(android): run observable foreground-service probes`。

### Task 13: 操作畫面、無 adb 自檢與 APK 交付

**Files:** Create `M/ProbeScreen.kt`、`M/ProbeSelfCheck.kt`、`android/validation/README.md`；Modify MainActivity、strings／themes／icon、app/build.gradle.kts、P/.gitignore；Test `T/ProbeInputTest.kt`、`T/ProbeExportTest.kt`。

**Interfaces:** `ProbeScreen(state: RunRecord?, onStart: (ProbeQuery,RunMode)->Unit, onCancel: ()->Unit, onResume: (String)->Unit, onExport: (String)->Unit)`；`suspend fun ProbeSelfCheck.run(): List<Pair<String,Boolean>>` 只跑固定本機樣本並把證據來源標LOCAL_SELF_CHECK，使用非阻塞callback等待與有限timeout。實際畫面由MainActivity提供本機檢查及WebView容器。

- [ ] **Step 1 — 寫輸入／匯出測試與RED。** 欄位只接受有限數值／已支援代碼，無任意網址。預設新北中和、總價2000萬、最小20坪、R1,R2／住宅1，其餘不限；可選永和／新店並改測試條件，不做設定CRUD。`./gradlew.bat :app:testDebugUnitTest --tests '*ProbeInputTest' --tests '*ProbeExportTest'`；拒絕負值、超長代碼、未知站區；匯出包含版本／模式／未測狀態而無祕密字串。
- [ ] **Step 2 — 實作繁中畫面。** 顯示站、區、條件摘要、模式、開始／取消、忙碌提示、網站驗證區、結果列表與未知欄位。結果區包含runId／版本、有效頁／筆數、背景內成功頁數、停止原因。通知拒絕時有說明與App內取消；字體放大仍可滾動、按鈕有文字標籤。提供來源縮圖預覽及圖片成功／失敗結果，不做圖庫。
- [ ] **Step 3 — 接主動匯出與本機自檢。** 用ActivityResultContracts.CreateDocument建立zip，不要求儲存空間廣泛權限／自動上傳。以Task4本機HTTPS頁測fetch/XHR、iframe拒絕、舊頁回應；自檢iframe與回應均由本機asset handler提供，不連真實測試網域。頁面列出各項pass/fail，使用者可直接回報。timeout／renderer等人工注入結果標SIMULATED；不得出現在LIVE通過計數。
- [ ] **Step 4 — 建置與打包。** `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`全PASS；`./gradlew.bat :app:dependencies --configuration debugRuntimeClasspath`保存實際解析版本摘要。本次給使用者的是自含式簽署測試APK，不需USB；debuggable不等於要求開USB偵錯。同一試驗簽章保存在未追蹤的本機安全位置，遞增versionCode；記錄憑證SHA-256，不交出私鑰。用SDK的apksigner驗證APK，PowerShell `Get-FileHash -Algorithm SHA256`記hash。
- [ ] **Step 5 — 寫交付紀錄與提交。** 放 `android/validation/builds/<version>/HouseLens-probe.apk`、`build-info.json`；build-info含SDK／依賴實際解析版本、簽章指紋、git commit＋未提交內容狀態／source hash。P/.gitignore不覆蓋外層產物，新增 `android/validation/.gitignore` 忽略APK、私有raw匯出／builds，審查過的摘要才納入版控。README寫側載、通知、無USB操作、每步預期畫面、卸載會刪資料、如何回報／匯出。提交 `feat(android): deliver self-contained probe APK workflow`，不得stage APK或簽章。

### Task 14: 使用者真機驗證與結論

**Files:** Create `android/validation/summary.md`；由使用者回報後新增 `android/validation/<timestamp>-<site>-<case>/result.md` 與遮蔽資料；Modify `android/03-crawler-feasibility.md`、`android/handoff/handoff.md`。

**Interfaces:** Consumes Task13 APK、Task3匯出以及驗證方案T0–T5。Produces 七站前景／B1／B2／B3／長輪次／中斷／圖片／人工介入矩陣；每格附APK版本和證據路徑，不生成不存在的結果。

- [ ] **Step 1 — 第一輪安裝／自檢。** 使用者安裝並回報版本、裝置／WebView資訊及本機自檢結果；確認不需USB。未安裝／未回報時保持待測，不能勾選後續真機任務。
- [ ] **Step 2 — 前景逐站。** 依既定七站順序，一次提供一站操作；至少兩次前景、兩頁以上、合法零筆／篩選／人工驗證與圖片；解析結果與同條件當下頁面人工核對。無法取得合法零筆時記未測，不要求使用者用不存在的資料完成案例。
- [ ] **Step 3 — 背景與鎖屏。** 只對前景通過的站跑B1／B2各兩次，拔線且保留原設定；確認事件顯示返回App前完成新頁解析。再做B3與正常速率固定一區FULL；最後七站循序輪次。若一站無法背景，照實保留其結論，不能勾「全七站背景通過」。
- [ ] **Step 4 — 中斷與更新。** 斷網1分鐘、取消、滑除、強制停止／重開各自記錄；有行動數據再測切網路。安裝遞增versionCode的同簽章APK後，舊run仍可讀／匯出。真機未觸發timeout／renderer回收／Doze者明列缺證，不用SIMULATED代填。
- [ ] **Step 5 — 彙整並提交文件。** 按規格判定：可前背景、僅前景、需人工、不可完整取得；分開資料完整性與背景可用性。不接受無證據的零誤差或全機型可靠聲明。只有使用者接受必要限制後，才規畫正式移植／資料庫階段。提交 `docs(android): record device validation evidence`，只包含已遮蔽且實際收到的內容。

## 範圍覆蓋與後續界線

驗證方案T0→本計畫1/3/13；T1→4–8；T2→9–11；T3→3/12/14；T4→3/12/14；T5→14。七站都涵蓋，完整房源UI、Google登入、Supabase RLS／RPC、跨端租約與Web寫入更新尚不在本APK內，之後另在 `android/plans/` 建計畫。既有後端parser缺陷在本計畫記錄並避免移植，不趁此修改Web行為。

完整輪次缺欄位／未知末頁／只在調整電池設定後成功時，須保留限制。正式移植不可把「試驗APK能開網頁」當完成。測試先行步驟適用非平凡邏輯；單純資源／文字配置不另造鏡像測試。

## 查證與建置限制

已讀七站scraper與現有unit tests；Task1已建立Android工程並完成下載、安裝及build，證據見 `../tasks/task1.md`。以下來源在2026-11-03查證，版本組合已由Task1實際解析與編譯確認：

- [AGP 9.1.1 相容表](https://developer.android.com/build/releases/agp-9-1-0-release-notes)：API37、Gradle9.3.1、JDK17；[內建Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin)。
- [Compose BOM](https://developer.android.com/develop/ui/compose/bom)、[Compose compiler設定](https://developer.android.com/jetpack/androidx/releases/compose-kotlin)、[Activity版本](https://developer.android.com/jetpack/androidx/releases/activity)。
- [AndroidX WebKit版本](https://developer.android.com/jetpack/androidx/releases/webkit)、[document-start與message listener API](https://developer.android.com/reference/androidx/webkit/WebViewCompat)。這些API的存在不代表591實站必能取得完整回應。
- [jsoup 1.23.2](https://jsoup.org/news/release-1.23.2)、[serialization 1.9.0](https://github.com/Kotlin/kotlinx.serialization/releases/tag/v1.9.0)、[coroutines 1.10.2](https://github.com/Kotlin/kotlinx.coroutines/releases/tag/1.10.2)。
- 背景／timeout／使用者停止依據沿用[驗證方案來源](2026-11-03-s25plus-validation.md#查證依據與限制)。沒有USB偵錯仍可安裝驗證APK，無法保證在指定時間重現系統回收事件。
- [有界wake lock](https://developer.android.com/develop/background-work/background-tasks/awake/wakelock)、[ServiceWorkerWebSettings](https://developer.android.com/reference/android/webkit/ServiceWorkerWebSettings)、[WebViewAssetLoader本機內容](https://developer.android.com/develop/ui/views/layout/webapps/load-local-content)。

## 執行交接

使用者已選Native，不再詢問執行方式。Task1–7完成後，等待使用者繼續Task8，主代理依序實作，每Task結束暫停並review，直接覆寫 `android/handoff/handoff.md` 後等待繼續；context超過60%也先暫停覆寫同檔更新摘要。詳細停點、不可見context讀數的限制與目前進度見 [交接文件](../handoff/handoff.md)。全部實作完成後仍需獨立review；Task14的真機證據須由使用者實際操作回報。
