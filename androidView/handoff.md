# HouseLens Android View 交接

更新：2026-11-07。繁體中文。整份覆蓋並同步 F: 入口／C: 開發 worktree；不記錄 API key、密碼、私鑰、個資或手機序號。

## 原始碼提交狀態（2026-11-07）

本節與 v0.1.1 圖示、v0.1.2–v0.1.5 篩選／排序修正、21個XML資源及相關測試一起納入 `feature/android-viewer` 的同一個本機提交；基準為 `6e8aaa0`，實際提交識別以包含本節的 Git commit 為準。下方各版本的「未commit」描述保留為當時紀錄，不代表目前狀態。

提交前重新執行68項JVM測試全數通過，lint0 errors／20既有warnings；證據為F transfer `viewer-precommit-20261107-235307-824`。UI沿用同份程式最近的26／26驗證，不重連ADB或手機。提交範圍只有androidView程式／資源／測試／文件，排除簽署與Supabase本機設定、APK、SDK、build、暫存logs；未push／merge，保留worktree。另同步圖示計畫與驗收文件的交付狀態；B7尚未完成的人工驗收不變。

## 最新版本：v0.1.5 預設單價低到高（2026-11-07 23:33）

**已簽署並保留資料更新到 S25+，versionCode5→6；更新後已讀取手機實際排序控制項，確認顯示「單價低到高」。下方為歷史版本紀錄。**

- 依使用者要求，`PropertyQuery.sortBy` 預設由 `score` 改為 `unitPrice`；`queryChips` 的非預設判斷與移除排序條件也同步改成 `unitPrice`。首次載入、重設、清除全部／空結果清除、資料讀取失敗fallback與區域導向查詢沿用同一預設。
- 明確保存的手動排序仍照常恢復，不做資料遷移、不一律覆寫舊score偏好。手機原本顯示評分優先，已透過既有排序選單選擇單價低到高；更新後冷啟動再次確認目前排序為單價低到高。其他條件及收藏未清除。
- 排序方向、null優先與ID同值次序未變；保留所有圖示／篩選／排序WIP，未commit／push。同步 `spec.md` 初始預設與versionName0.1.5／code6。
- 回歸涵蓋預設HTTP order、空偏好預設、六種手動排序保存重開（包含score）、新畫面第一筆查詢、無預設排序chip、移除score chip仍保留地區、清除全部、篩選重設從priceDesc回unitPrice。最終JVM68／68、200%完整UI26／26、lint0 errors／20既有warnings、Debug／Release全部通過，獨立審查無findings，模擬器設定已還原。
- 保留不穩定證據：首輪全套的既有 `FilterSheetScrollTest.flinging_past_bottom_settles_without_moving_footer` 在初次 `swipeUp` 後「Content must still scroll by touch」斷言失敗，還沒進入邊界測試；此fixture不使用sortBy／queryChips。未改手勢程式或測試，單跑2／2及再跑全套26／26通過；確切時序原因未證實，不能宣稱因果修復。
- 手機UI讀取用一次性 `/data/local/tmp/houselens-sort-*.xml` 後立即刪除；只輸出排序控制項，不保存原始hierarchy或房源內容。`/dev/tty`輸出方式無XML，勿直接沿用當成可用管道。

最新APK：`C:/Users/David/Downloads/HouseLens/HouseLens-0.1.5.apk`，同目錄 `.sha256`；SHA256 `82D71EA523E84022DEDD22F5D2DB0DACF372FF4FE41B09183906014895270440`，沿用既有憑證。`install -r`成功，appId／firstInstallTime保持，MainActivity冷啟動成功；沒有卸載、清資料、手機instrumentation或逐筆收藏稽核。未逐頁人工檢查正式房源順序，B7其他未完成項仍OPEN。

證據：F transfer `default-sort-red-20261107-231937-017`（11項中2個預設斷言失敗）、`sort-Font200-20261107-232114-929`（25／26、初次捲動失敗）、`sort-Font200-20261107-232647-237`（捲動單跑2／2）、`sort-Font200-20261107-232813-916`（最終26 UI／68 JVM、lint與設定還原）。C `.superpowers/sdd/02-app-plan/anonymous-release-20261107-233148-923`、`default-sort-phone-update-20261107-233231-861`。手機目前排序的檢查使用 F transfer `default-sort-phone-ui.ps1`（不帶ClickLabel唯讀）確認唯一顯示的排序文字為單價低到高。

v0.1.5收尾已停止ADB並以TCP確認5037不接受連線；手機App與資料保留。

## v0.1.4 精簡單價排序文字（歷史，2026-11-07 23:16）

**已簽署並保留資料更新到 S25+，versionCode4→5。下方版本紀錄為歷史。**

- 依使用者要求，兩個選項現在只顯示「單價低到高」與「單價高到低」，移除完整的「（未提供優先）」；共用標籤也讓排序按鈕與條件chip同步精簡。
- 只改 `FilterSheet.kt` 兩段文案、既有 `NavigationTest.kt` 期待值、`spec.md` 顯示名稱及版號。排序key／方向／null優先／同值ID順序全部未變，保留既有所有WIP，未commit／push。
- 既有200%字級六種排序操作測試1／1通過，Root已檢視新選單PNG、兩段括號文字皆移除；獨立來源審查無阻擋。這是文案調整，未重跑完整UI／JVM／Debug lint；Release build及正式artifact／簽章驗證通過，模擬器設定已還原。
- 最新APK：`C:/Users/David/Downloads/HouseLens/HouseLens-0.1.4.apk`，同目錄 `.sha256`；SHA256 `E1B99923A2E834D8B3518AD687268DA80C99C0F458AAC10BE3E15E9798E94C3C`，沿用既有憑證。手機 `install -r` 成功，appId／firstInstallTime保留，MainActivity啟動命令Status ok（當時已在前景）；未卸載、清資料、手機instrumentation或逐筆收藏稽核。
- 證據：F transfer `sort-Font200-20261107-231312-056`（1／1、新PNG、設定還原）；C `.superpowers/sdd/02-app-plan/anonymous-release-20261107-231505-622`、`sort-labels-phone-update-20261107-231606-686`。

v0.1.4收尾已停止ADB並以TCP確認5037不接受連線；手機App與資料保留。

## v0.1.3 總價／單價雙向排序（歷史，2026-11-07 23:08）

**已簽署並以保留資料方式更新到 S25+；下方版本紀錄為歷史，以本節為最新狀態。使用者已確認 v0.1.2 的篩選抖動問題修好。**

- 依使用者要求新增「總價高到低」、「單價高到低」，將原單價排序明確標示為「單價低到高」。原系統評分與最近收錄保留，合計六項。
- 單價兩方向沿用既有 null 優先規則，UI保留「未提供優先」註記。使用既有 `price`／`unitPrice` 升冪key及新增 `priceDesc`／`unitPriceDesc`；舊儲存條件不需遷移。
- 在既有 Supabase 查詢加入兩個降冪分支，全部排序保留 ID 升冪作同值次序，對全結果排序後分頁；沒有本機重排當前頁、DB／權限／依賴變更。
- 最小產品修改：`FilterSheet.kt` 標籤、`ValueFormats.kt` 驗證允許值、`SupabaseHouseLensData.kt` 查詢分支；另更新 `spec.md` 六項排序契約與 versionCode4／versionName0.1.3。保留圖示及篩選修正WIP，未commit／push。
- 測試：六種完整HTTP order及第1／2頁offset、舊／新price key儲存重開、六項UI選單逐一操作並保留行政區。一般字級選單1／1通過；最終200%完整UI26／26、JVM68／68通過，lint0 errors／20既有warnings。一般／200%選單PNG已Root與獨立reviewer檢視，長單價標籤換行完整。
- **保留已知測試不穩定紀錄：**第一次200%全套的既有 `NavigationTest.find_detail_return_keeps_query_scroll_and_loaded_data_across_rotation` 在最初選行政區後的查詢等待逾時（尚未排序／開詳情）。未改產品碼，單跑1／1通過；兩處行政區點擊後補 `.assertIsSelected()` 再跑全套26／26通過。不延長timeout、不宣稱已因果修復；原失敗仍留存。

最新APK：`C:/Users/David/Downloads/HouseLens/HouseLens-0.1.3.apk`，同目錄 `.sha256`；SHA256 `B4531600AD83494964B1121629EFAF070FAB55E0ACAEC509F34BE20647646970`。沿用既有憑證 `de6fe3b9729224098250683aed7c1a2cdb9f3d4f28ac4a19848903b51c4d27ee`，Release版本／簽章／hash均通過。

手機已 `install -r` 成功，code3→4，appId／firstInstallTime保持一致，已啟動。沒有卸載、清資料、手機instrumentation或逐筆收藏稽核。本次未在手機逐項手動操作排序，未逐頁核對正式房源；契約測試驗證實際SDK送出的order，UI測試使用fixture。B7其他項仍OPEN。

證據：F transfer下 `sort-red-20261107-225439-293`（11項中2項因新key不支援而失敗）、`sort-Normal-20261107-225913-593`（1／1）、`sort-Font200-20261107-230030-326`（25／26及初次大字PNG）、`sort-Font200-20261107-230304-308`（導覽單跑1／1）、`sort-Font200-20261107-230506-445`（最終26／26、68／68、lint及settings還原）。C `.superpowers/sdd/02-app-plan/anonymous-Jvm-20261107-225540-066`、`anonymous-release-20261107-230742-118`、`sort-phone-update-20261107-230845-725`。

v0.1.3收尾已停止ADB並以TCP確認5037不接受連線；手機App與資料保留。

## v0.1.2 篩選彈窗邊界抖動修正（歷史，2026-11-07 22:49）

**已完成修正、簽署並以保留資料方式更新 S25+；下方 v0.1.1 內容是歷史交接，版本與交付狀態以本節為準。**

- 使用者回報篩選房源選單滑至頂／底後持續上下抖動。`FilterSheet.kt` 的內容 `verticalScroll` 在邊界仍把手勢交給 `ModalBottomSheet` 拖曳／回彈；只設定 `dragHandle=null` 並未關閉該行為。
- 最小修正為 `sheetGesturesEnabled=false` 加一行原因註解。內容照常捲動，整張彈窗固定；刻意取消滑動關閉，保留「關閉篩選」、系統返回與遮罩關閉。後兩者有 Material3 1.4.0 原始碼確認，本次未對篩選彈窗做返回／遮罩的獨立 runtime 驗證。
- 本次只修改上述手勢設定、版號為 code3／0.1.2，新增 `app/src/androidTest/java/tw/houselens/viewer/find/FilterSheetScrollTest.kt`；保留全部既有未提交圖示變更。沒有資料層、收藏儲存、權限、依賴或網站修改，沒有 Git commit／push。
- 新測試以真實 touch swipe 驗證內容可捲動，頂／底各重複3次手勢並每160ms取樣套用按鈕位置、持續3.2秒；另外核對邊界、關閉／套用 callback。
- 修正前診斷曾量到彈窗底部539→536→539px；強化後 RED 兩項皆在內容 swipe 後因持續 pending recompositions 而 `ComposeNotIdleException`。修正後同測試2／2 PASS，取樣全數539px；不把 API26 的重繪逾時冒稱為 S25+ 原始可見抖動的直接錄影重現。offset／inset／anchor 的細部回饋機制仍是來源分析推論。
- 完整200%字級 UI 25／25、JVM67／67、lint0 errors／20既有warnings、Debug及Release build全部通過。獨立 reviewer 無阻擋 finding；模擬器 size/font/rotation/IME已還原。

最新交付：`C:/Users/David/Downloads/HouseLens/HouseLens-0.1.2.apk`（同目錄 `.sha256`），SHA256 `0FC3F64E305128814F0FB33358934FCE186779858A59DEB482F0214D8C8D8BFD`。沿用憑證 `de6fe3b9729224098250683aed7c1a2cdb9f3d4f28ac4a19848903b51c4d27ee`。

S25+ 已 `install -r` 成功，versionCode2→3，appId／firstInstallTime保持一致並成功啟動；沒有卸載、清資料或對手機執行 instrumentation，沒有逐筆稽核收藏。**仍待使用者在手機實際滑動確認原始症狀；原B7未驗證項仍OPEN。**

證據索引（F transfer＝`F:/SourceCode/HouseLens/.superpowers/sdd/02-app-plan/transfer/`，C＝既有唯一開發worktree）：

- 初次診斷：F transfer `task-icons-Normal-20261107-223827-247`（頂端dismiss失敗、底端短暫位移；保留為診斷，非最終RED）。
- RED：F transfer `task-icons-Normal-20261107-224013-502`（2項持續重繪逾時）。
- GREEN：F transfer `task-icons-Normal-20261107-224211-459`（2／2 PASS）。
- 完整檢查：F transfer `task-icons-Font200-20261107-224334-239`（25 UI／67 JVM、lint與settings restore）。
- Release：C `.superpowers/sdd/02-app-plan/anonymous-release-20261107-224749-311`。
- 手機更新：C `.superpowers/sdd/02-app-plan/filter-phone-update-20261107-224905-079`。

本次收尾已再次停止 ADB，TCP確認127.0.0.1:5037不接受連線；手機App與資料保留。

## v0.1.1 交接結論（歷史）

**v0.1.1 圖示版已完成、簽署、交付，並以保留資料方式更新到 S25+。圖示原始碼改動仍未提交。**

- v0.1.0 免登入／正式 Supabase 瀏覽已完成並提交；正式 APK 在手機曾實際顯示687筆房源。
- v0.1.1 已配置與網站一致的房屋、愛心、星號及圓角細線向量圖示，涵蓋四頁導覽、資訊、篩選／排序／刷新、收藏／評分／歷史、返回／外連與 adaptive launcher。沒有新增圖示套件或改動資料層。
- 同簽章更新成功，versionCode1→2；appId與原始安裝時間保持一致，沒有解除安裝或清資料。沒有逐筆讀取收藏，不能稱為逐筆資料稽核。
- 使用者已獲告知：分享給家人只需 `.apk`，`.sha256`為選用校驗檔；日常只需網路，不需USB或開發電腦。
- 可用APK與本次圖示需求已完成；**完整B7人工／受控效能驗收仍OPEN**。
- 交接整理後另依使用者要求停止ADB連線。沒有新增開發、建置、測試或Git提交。

## 已確認範圍與授權

1. 第一版不用登入，開APK直接使用；UI/UX優先。
2. Android只瀏覽現有Supabase房源，不爬取、不排程、不管理、不寫房源。目前沒有執行Worker。
3. 使用者已明確批准最小欄位的正式匿名唯讀及houselens Data API；已告知持公開key的其他客戶端也可讀，不限APK持有人。不能自行擴大公開欄位／寫權。
4. 長期APK簽署身分已明確批准建立；使用者承諾安全備份。後續沿用，不重建／覆寫。
5. 網站圖示與配色是App的參考；網站檔案只讀，沒有複製管理／立即抓取功能。

Data API在Free方案內，本次沒有升級付費服務。已向使用者說明當時的500MB DB／project、5GB月egress、2個active Free projects、低活動約一週可能暫停等限制；日後回答價格請重查官方文件，不把本摘要視為永久費率。

## 工作區、Git與保護界線

| 項目 | 狀態 |
| --- | --- |
| 唯一開發worktree | `C:/Users/David/.codex/worktrees/9611/HouseLens` |
| 分支／HEAD | `feature/android-viewer`／`6e8aaa00025fffec3affac335f4644538bcd6731` |
| 已提交內容 | `feat: deliver anonymous Supabase viewer v0.1.0` |
| 未提交內容 | v0.1.1圖示／launcher／version2、9個Compose consumers、既有測試的截圖與48dp斷言，以及本次handoff同步 |
| 新資源 | 21個XML位於`app/src/main/res/drawable/`、`mipmap-anydpi/`、`mipmap-anydpi-v33/`、`values/icon_colors.xml` |
| F:原目錄 | `F:/SourceCode/HouseLens`只作交接入口／transfer；保留其其他使用者變更 |

所有開發git／build／test明確使用C workdir，先核對git status／log。開發主要限androidView/；必要根.gitignore已於v0.1.0提交。不讀取／沿用android/、androidtest/，不修改backend/frontend；可唯讀參考網站SVG。

本環境直接write root是F，C寫入／build需sandbox escalation。既有流程：精確腳本放F transfer→Root檢閱→明確提升執行。不要繞過授權、git clean -fdx、或刪SDK／Gradle cache／logs／clusters／plan workspace。來源修改腳本多鎖定舊版且一次性，不能盲跑。

## 權威入口與文件落差

以下相對路徑以C worktree為根，F transfer另註。

| 文件 | 用途 |
| --- | --- |
| `androidView/spec.md` | 頂端最新免登入／icon裁定優先於歷史Google／會員段落 |
| `androidView/plans/03-anonymous-v1-plan.md` | 匿名v0.1.0第一版交付已完成 |
| `androidView/plans/04-icons-plan.md` | 圖示設計；完成勾選／收尾紀錄尚待同步 |
| `androidView/validation/acceptance.md` | v0.1.0詳細14AC與限制；尚待補入已完成的v0.1.1交付 |
| `androidView/supabase/README.md` | 已部署匿名權限、精確欄位與停權／恢復流程 |
| `.superpowers/sdd/03-anonymous-v1-plan/progress.md`、`04-icons-plan/progress.md` | ledgers；圖示完成／提交紀錄尚待收尾 |
| F `.superpowers/sdd/02-app-plan/transfer/task-icons-report.md` | 最新v0.1.1實作、測試、簽署、更新完整報告 |
| 同目錄 `task-icons-review.md` | 獨立spec／quality review無findings；已看一般／大字PNG與真正launcher |
| 同目錄 `anonymous-final-review.md` | v0.1.0最後修正與橫向裁切的scoped review通過 |

**本handoff與task-icons-report比部分plan／acceptance狀態新。** 舊01-supabase-plan／02-app-plan已標歷史，不要把其Google／會員／callback未勾項重新加回需求，也不要重派已完成B1–B6／S1。

## 交付物與手機資料

| 項目 | 最新值 |
| --- | --- |
| APK | `C:/Users/David/Downloads/HouseLens/HouseLens-0.1.1.apk` |
| 校驗 | 同目錄`HouseLens-0.1.1.sha256`；安裝不必提供 |
| 大小／版本 | 12,361,345bytes；versionCode2／versionName0.1.1 |
| Package／平台 | `tw.houselens.viewer`；minSdk26／targetSdk36；非debuggable |
| APK SHA256 | `9D91A18F0C9B4BF29206FB73DD22D17636B1BF5C4302DBE9E9E4B12C47EEDE5E` |
| 公開憑證SHA256 | `de6fe3b9729224098250683aed7c1a2cdb9f3d4f28ac4a19848903b51c4d27ee`，與v0.1.0相同 |
| 手機 | Samsung S25+／SM-S9360／Android17 API37／OneUI9；下次重查連線，不保存序號 |

**手機已有正式App，可能已有收藏。不得對它跑會卸載target的connectedDebugAndroidTest、解除安裝或清資料。** 後續自動測試用emulator-5580；手機更新需同簽章、合理提高版號、install-r。簽章衝突就停，不以解除安裝解決。使用者已被告知可拔USB，準備好更新再確認連線。

私有簽署資料位於`C:/Users/David/AppData/Local/HouseLens/Signing`；owner-only ACL已核對。沿用ignored `androidView/signing.local.properties`與recovery，不讀出／貼出密碼或私鑰。使用者承諾備份，未代替他驗證離機備份。APK不進Git；F原分支未忽略.superpowers/delivery，所以交付檔放Downloads。v0.1.0檔留作歷史交付，不要強制降版或先刪App。

## 已驗證摘要

詳細測試／失敗經過引用reports，以下只列實際範圍與恢復索引。

| 檢查 | 結果／證據 |
| --- | --- |
| v0.1.1 JVM／lint／Debug | 67 PASS、0errors／20既有warnings；C `.superpowers/sdd/02-app-plan/anonymous-Jvm-20261107-215844-402` |
| 一般字級／200%完整UI | 5／5與23／23 PASS；F transfer `task-icons-Normal-20261107-213027-677`、`task-icons-Font200-20261107-213133-246`；14張PNG已實作者／獨立reviewer檢視 |
| 圖示版橫向 | 選定3／3 PASS；F transfer `task-icons-Landscape-20261107-213831-862`。完整欄位／48dp斷言保留；新增橫向PNG尚未另拉回人工檢視，不得引用舊版20:23／20:52PNG冒充 |
| 原生launcher | API26真正adaptive render 1／1 PASS；F transfer `task-icons-launcher-20261107-215607-444`，Root／reviewer已看正確粉白造型與safe-zone。API33 themed-icon僅來源／打包審查，未實機操作 |
| v0.1.1 Release | C `.superpowers/sdd/02-app-plan/anonymous-release-20261107-220413-815`：metadata／簽章／hash通過；release resource table含base anydpi與v33 |
| 手機更新 | C `.superpowers/sdd/04-icons-plan/phone-update-20261107-220828-442`：updateOnly、appId／firstInstallTime保留、noClearOrUninstall、launched皆true |

沿用基礎證據：v0.1.0 S25+核心旅程3／3通過；test-only Espresso3.7已修正Android17相容問題。真實業務資料層11GET／9端點全部成功解碼（找房／選項／排行／降價／區域／最近輪次／首筆詳情歷史），C `.superpowers/sdd/02-app-plan/anonymous-live-business-20261107-204455-790`。正式手機687筆的最終摘要在F transfer `anonymous-release-phone-live.json`。沒有保存原始手機hierarchy、輸出房源內容或把fixture當真實連線。Release8個拒絕案例與真正簽署正向案例都已通過。

所有臨時live／launcher測試來源已移除；失敗logs與stale APK已保留在各報告索引。曾因資源目錄移動出現增量漏包，已保留舊包並只clean app/build後確認兩變體；不要重改正確顏色或把stale包當交付物。

## 架構與正式環境

- MainActivity→ViewerContent→HouseLensNav，四頁找房／精選／收藏／區域；資訊頁取代帳號。沒有Auth/member/fakeAuthorized/bypass。
- 真正匿名Postgrest，APK只含公開設定，DB管理憑證不在APK。URL／key已在ignored supabase.local.properties，不再索取或貼進文件。
- 本機namespace是project＋local-device-v1；收藏／已套用條件不跨裝置同步，不匯入或刪除舊登入owner檔。圖片client與Supabase憑證隔離；其他契約見spec／資料層。
- 正式已依明確批准部署六個非FORCE RLS／六anon SELECT policies／四invoker views與最小欄位grants；API寫權0、houselens routines0。未改房源／Auth列，沒有啟動Worker。
- 使用者已在Dashboard開放houselens並保留graphql_public/public；REST200、SDK、正式業務DTO均已成功，沒有設定阻擋。
- SQL隔離39項notice／19漂移拒絕、重裝／原子回復／撤權／恢復與writer相容通過，clusters已停止並保留。正式不做試寫／刪除；停權用已審查revoke.sql，不做全schema REVOKE／DROP。具體欄位、證據與流程見supabase/README.md。

## 接續順序與未完成項

1. 核對C worktree、HEAD、WIP，保留未提交圖示變更。先讀task-icons-report／review，不重做實作或為相同證據重跑整套。
2. 收尾同步acceptance、04-icons-plan與ledgers的0.1.1完成狀態；若需補圖示版橫向pixel證據，先核對既存檔案新鮮度，不重新建置只是取圖。
3. 檢查diff與私密／二進位排除後，依使用者當次指示完成本機圖示提交。6e8aaa0基準已提交，圖示WIP尚未提交；本次沒有push、merge或商店發布。
4. 依使用者回饋或原B7剩餘驗收接續，不能自行加入登入／爬蟲／管理功能。

完整B7尚待：真TalkBack、手勢／多視窗人工驗收、真正程序終止恢復、手機200%字級、行動網路且電腦實際關機、受控P95／frame／ANR。不能用Activity recreation、fixture或220／285ms Activity launch冒充。

舊591首次漏選b7-full-green.log仍是失敗證據，原因未得到因果確認；後來逐chip斷言／全套通過不算因果修復。最後review1–7與可重現橫向裁切已修正且review通過；歷史細節讀reports，不重開已解決項。

## 執行環境與注意事項

已依使用者要求執行 adb kill-server，並以TCP探測確認127.0.0.1:5037不再接受連線。此Windows USB後端不支援單獨detach，因此手機與模擬器的ADB連線一併停止；模擬器程式與手機App／資料保留。USB若仍插著，下次呼叫ADB可能自動重新連上；需要裝置時再啟動，不要為了驗證斷線而再跑adb devices。

交接時沒有active build、instrumentation、remote mutation或tool wait；子代理已完成。模擬器／Gradle daemon保留，接續仍先查process／log再啟動工作。

| 設定 | 值（相對C worktree） |
| --- | --- |
| JAVA_HOME | `C:/Program Files/Android/openjdk/jdk-21.0.8` |
| ANDROID_HOME | `.superpowers/sdd/02-app-plan/tools/sdk` |
| GRADLE_USER_HOME | `.superpowers/sdd/02-app-plan/gradle-home` |
| ANDROID_USER_HOME／AVD | `.superpowers/sdd/02-app-plan/android-user`／其下avd |
| 模擬器 | emulator-5580／API26；size/font/orientation/IME已還原 |
| Gradle | `androidView/gradlew.bat -p androidView ... --max-workers=2`；每次明確限定device |

F transfer已有圖示、release、更新runner；修改腳本多為一次性，不能盲跑。簽署runner保留quoted keytool -J參數。每次唯一log；shell無輸出時先查既有程序／log並使用login:false，不盲目重開。

Computer Use瀏覽器host／Windows原生pipe本session不可用，Supabase管理plugin未連接。使用者已手動完成Dashboard，不能誤當正式連線未設定或重複索取key。

## Suggested skills

- `handoff`：`C:/Users/David/.codex/skills/handoff/SKILL.md`；使用者指定F路徑優先於技能預設暫存位置，並同步C同名檔。
- `ponytail:ponytail`（full）：最小完整改動，保留資料與已驗證流程，避免不必要依賴／重測。
- `superpowers:subagent-driven-development`：新實作依ledger接續與獨立review，不重派完成項。
- `superpowers:systematic-debugging`：有新實際失敗才定位根因，不以重跑通過冒稱修復。
- `superpowers:verification-before-completion`、`superpowers:requesting-code-review`：提交／交付前核對實際證據及範圍。
- `superpowers:test-driven-development`：非平凡行為／bugfix用有意義RED/GREEN；可逆視覺調整沿用操作回歸與渲染核對，不新增路徑鏡像測試。
- `computer-use:computer-use`：需要管理UI才用，先確認工具連線；手機資料保護界線不變。
