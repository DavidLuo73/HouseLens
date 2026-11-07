# HouseLens Android 驗證 APK 交接

更新：2026-11-06（Asia/Taipei）。工作目錄：`F:\SourceCode\HouseLens`。請以繁體中文溝通。

## 下一輪從這裡開始

**Task13及Task14 Step1本機驗收完成；591前景共4次仍0頁未通過，Task14整體尚未完成。** 最新0.2.1兩份ZIP都PARSE_ERROR，截圖可見中和售屋行動版列表、無當下可見驗證，但runId被裁切。已交付 **0.2.2／code4**，以15種固定F591拒絕碼取代一律PARSE_ERROR；215測試、lint、APK同簽章均驗證。下一步使用者覆蓋更新，以同條件只重試一次591前景，提供新ZIP或完整停止原因碼，即可定位URL／query／JSON／HTML哪一關拒絕。不再重問網站是否顯示；沒有宣稱修復591採集，其餘六站及背景待測。

所有 Android 程式碼必須寫在 **`F:\SourceCode\HouseLens\android\`** 下（使用者本輪再次明確指定），含工程、任務及驗證紀錄。工程位於 `android/probe/`。

## 執行與停點規則

1. **Native 實作**：主代理依序修改程式；可用唯讀研究／review 代理，不委派實作。全部14個Task完成後仍需獨立整體審查。
2. **一次一個 Task**：做完必要實作與適當測試後，停止啟動下一 Task，review diff、需求覆蓋、證據與風險。必修問題在本 Task 修完才算完成。
3. **每 Task 回報後停下**：更新本檔並等待使用者「繼續」；此為使用者直接要求，優先於技能的批次執行預設。
4. **Context 超過60%**：若有可靠讀數，停止開始新工作，在安全邊界收集已啟動操作結果、更新本檔並停下，不為完成 Task 硬撐。本環境沒有可靠百分比，不能把 token budget 或估計當讀數；採每 Task 停點與長任務提早交接。
5. 已定方向與授權不重問；實際工具下載／受限寫入權限仍依當時規則處理。
6. **固定交接檔名**：每次產生交接文件，一律直接覆寫 `F:\SourceCode\HouseLens\android\handoff\handoff.md`，不另建編號或日期版本。

## 必讀入口

- [驗證 APK 實作計畫](../plans/2026-11-03-probe-apk-implementation.md)：14個Task、共用介面、fixture及驗證步驟。
- [Task14 執行紀錄](../tasks/task14.md)／[驗證摘要](../validation/summary.md)／[最新591比對](../validation/20261106-591-foreground-0.2.1/comparison.md)：本機驗收完成；591共4筆0頁未通過；0.2.2固定原因碼待真機重試。
- [Task13 執行紀錄](../tasks/task13.md)：操作畫面、歷史／匯出、自檢、212項測試、review與APK交付。`validation/builds/task13/HouseLens-probe.apk`（0.2.0／code2）。
- [安裝與操作說明](../validation/README.md)：無USB安裝、本機自檢、結果／圖片／匯出與回報。
- [Task12 執行紀錄](../tasks/task12.md)：Runner／Service／生命週期、取消／存檔競態、elapsed期限、review與203項測試；末段含Task13接線契約。
- [Task11 執行紀錄](../tasks/task11.md)：台灣房屋建坪／現價、未知欄位、空頁／全過濾頁、review修正及175項測試。
- [Task10 執行紀錄](../tasks/task10.md)：中信POST／envelope／現價、全過濾頁／跨區、review修正及153項測試。
- [Task9 執行紀錄](../tasks/task9.md)：信義HTTP／SSR、原始20卡分頁、圖片解碼／影片／fallback、review修正及137項測試。
- [Task8 執行紀錄](../tasks/task8.md)：住商原始卡數／分頁、未知零筆、型態／車位修正、review及120項測試。
- [Task7 執行紀錄](../tasks/task7.md)：永慶住宅／型態、合法空頁排除精選、全過濾頁、review修正及103項測試。
- [Task6 執行紀錄](../tasks/task6.md)：樂屋查詢／解析、主建與HALF_EVEN、全過濾頁／未知分頁、review修正及87項測試。
- [Task5 執行紀錄](../tasks/task5.md)：591查詢／BFF關聯、合成樣本、HTML備援、完整性與未知欄位裁決、review修正及72項測試。
- [Task4 執行紀錄](../tasks/task4.md)：HTTP／WebView安全邊界、robots／限速、橋接與本機頁、review修正及原生待驗證限制。
- [Task3 執行紀錄](../tasks/task3.md)：Store／Report／JSON契約、背景窗口、失敗恢復與review修正。
- [Task2 執行紀錄](../tasks/task2.md)：共用規則、查詢範圍、面積與未知欄位裁決、測試及review。
- [Task1 執行紀錄](../tasks/task1.md)：工具鏈、實際依賴、決定、RED/GREEN及review。
- [工程建置說明](../probe/README.md)：本機環境變數及Gradle命令。
- [S25+ 驗證方案](../plans/2026-11-03-s25plus-validation.md)：無USB操作與背景證據門檻。
- [七站驗證規格](../03-crawler-feasibility.md)、[規劃入口](../README.md)。

## 目前進度

| 範圍 | 實際狀態 |
| --- | --- |
| 需求／驗證方案／14 Task計畫 | 已完成規劃，採Native逐Task執行 |
| Task1 | **完成**：Gradle工程、模型、最小畫面、測試、lint、APK、獨立review與修正 |
| Task2 | **完成**：查詢驗證、共同排除理由、分頁決策與15個規則測試 |
| Task3 | **完成**：本機Store／JSON、恢復／匯出與背景證據判讀；已由Task13接UI |
| Task4 | **完成**：HTTP／WebView候選與橋接邊界，已由Task13接UI；本機頁尚未在WebView實跑 |
| Task5 | **完成**：591 adapter／合成樣本／20項來源測試，已由Task13接UI、未實站驗證 |
| Task6 | **完成**：樂屋adapter／三份合成樣本／15項來源測試，已由Task13接UI、未實站驗證 |
| Task7 | **完成**：永慶adapter／三份合成樣本／16項來源測試，已由Task13接UI、未實站驗證 |
| Task8 | **完成**：住商adapter／三份合成樣本／17項來源測試，已由Task13接UI、未實站驗證 |
| Task9 | **完成**：信義adapter／三份合成樣本／17項來源測試，已由Task13接UI、未實站驗證 |
| Task10 | **完成**：中信adapter／三份合成JSON／16項來源測試，已由Task13接UI、未實站驗證 |
| Task11 | **完成**：台灣房屋adapter／三份合成HTML／22項來源測試，已由Task13接UI、未實站驗證 |
| Task12 | **完成**：單作業Runner、dataSync服務、生命週期／wake lock／取消與恢復，已由Task13接操作UI |
| Task13 | **完成**：操作UI／歷史／縮圖／主動匯出／本機自檢，0.2.0 APK已簽章驗證；首次本機驗收見Task14 |
| Task14 | **進行中**：Step1完成；591前景4次0頁未通過；0.2.2診斷版已交付，其餘六站與背景未測 |
| Web／Supabase／正式App | 未修改或部署 |

Task1 主要檔案：`probe/` 內的Gradle配置／wrapper、manifest／resources（含備份規則）、`ProbeModels.kt`、`MainActivity.kt`、`ProbeModelsTest.kt`；外層新增 `.gitignore`、`validation/.gitignore`、`tasks/task1.md`，同步計畫與入口。

目前交付APK：`android/validation/builds/task14/0.2.2/HouseLens-probe.apk`（**0.2.2／versionCode4**）。保留0.2.1可讀性，新增591固定拒絕原因。SHA-256：`96A9E5806E73C694C59BC5F8C4D8FAC838B10FD6A590E82A25634728936F1928`。apksigner驗證成功，與0.2.1同簽章；build-info含工具鏈、憑證及81個原始碼hash。舊APK保留，產物忽略不入Git；0.2.2待真機驗證。

## Task1 歷史驗證與 review

- RED：`./gradlew.bat :app:testDebugUnitTest --tests '*ProbeModelsTest' --console plain --no-daemon`，依預期在缺少 Site 模型時失敗。`validation/builds/task1/red.log`。
- GREEN：`./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console plain --no-daemon`，exit0、BUILD SUCCESSFUL；模型測試1個，0失敗／0錯誤。最終紀錄 `verified.log`。
- 依賴：`:app:dependencies --configuration debugRuntimeClasspath --offline` 成功，保存 `dependencies.log`。簽章 `signature.log`。
- Review：獨立 reviewer 找到Windows SDK路徑未跳脫（首次lint失敗）、allowBackup不足以排除新版裝置轉移；兩項均修正。後者加dataExtractionRules排除所有資料域並設fullBackupContent=false。鎖版依賴的新版提示保留，不為提示升級。
- 本機檢查不能證明手機能啟動、畫面正確或背景可用；沒有執行實站請求、adb／模擬器或Supabase操作。

## Task2 驗證與接續契約

- 變更：新增 `probe/app/src/main/java/tw/houselens/probe/ProbeRules.kt`、`probe/app/src/test/java/tw/houselens/probe/ProbeRulesTest.kt`，模型加PageDecision；同步任務紀錄、計畫及本檔。無依賴、SDK或UI變更。
- 有效RED為缺少三函式與PageDecision；GREEN 15項規則測試通過。完整 `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --console plain --no-daemon` exit0、**16 tests／0失敗、lint0 errors／8個原有新版提示**。紀錄位於 `validation/builds/task2/`（red.log／green.log／full.log／build-info.json）。
- 沙箱編譯時Kotlin daemon預設使用者暫存檔被拒，內建fallback成功；完整驗證經工具授權於沙箱外執行成功。SDK／Gradle cache仍在android/.tools，Kotlin daemon的系統暫存是工具預設，不是Android程式碼。
- 獨立review沒有必修bug；非阻擋缺口為頁限與重複／空頁／空證據的交叉案例尚未獨立測試，但對應分支均已測、現行優先序正確，未另外擴充。
- validateQuery目前支援probe原定新北中和／永和／新店，數值非負，代碼最多128字及安全格式。站別已支援代碼的語意驗證在來源adapter／Task13補完，不能直接信任任意符合格式的代碼。
- 共同篩選精確比對site／ID／正價格／城市／行政區，已知屋齡與車位才可排除；maxTotalPrice=0不限制。exclusionReason回null只代表無可證排除，未知資料仍未知。
- **不可把areaPing直接拿來套minSizePing**：樂屋主建優先，其他常總建，591基準未明；沿用來源端處理並在Task5–11驗證。nullable與unknownFields不改成已確認值。
- 分頁以raw keys及可靠END證據判斷；來源parser仍須將缺ID／價格解析失敗回報INVALID，保留固定evidence／理由碼，不能用一般排除掩蓋解析失敗。
- Task1留存APK未覆蓋；Task2只新增純規則，尚無來源／Runner呼叫，也無新手機驗收功能。未執行實站、adb或Supabase操作。
- 以上是Task2停點紀錄；Task3現已完成，最新結果如下。Context實際百分比不可見。

## Task3 歷史結果與接線契約

- 變更：新增 `ProbeStore.kt`、`ProbeReport.kt`、`ProbeJson.kt`、`ProbeStoreTest.kt`、`ProbeReportTest.kt`；模型加入ProbeEventKind／ProbeReason enum、newRawCount、eventTailIncomplete。沒有新增依賴、修改SDK或UI。
- 原始RED缺API；review回歸曾重現7個邊界問題及navigationId重用。最終完整 `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --console plain --no-daemon` **exit0／BUILD SUCCESSFUL，39 tests／0失敗／0錯誤，lint0 errors／8個原有新版提示**。其中Store13項、Report10項；見 `validation/builds/task3/final.log`、`build-info.json`及各red log。
- 獨立review分Store與Report兩個唯讀檢查；主代理已修正：前景才回應／解析誤判、RESUMED人工介入漏擋、terminal失敗仍可匯出PASSED、nullable錯型吞null、半行續寫、快照匯出遺漏與恢復重複IO。最後另以紅綠測試擋不同頁重用navigationId。沒有必修遺留。
- 使用 `ProbeStore(File(filesDir, "runs"), processId)` 單例，在IO dispatcher呼叫；processId每程序新UUID。新增loadPage讀回完整頁，load只由完整page-N.json重建savedPageIndices；不信任temp或另一份計數檔。同頁相同內容冪等、不同內容拒絕覆寫。
- 事件必須使用enum。同頁同nav依序REQUEST_STARTED／RESPONSE_RECEIVED／PAGE_PARSED後呼叫savePage；**PAGE_SAVED及newRawCount由Store在原子提交後產生**，外部不可append這類事件。頁已存但事件遺失只恢復資料，不補造背景時間。
- finish把終止狀態及事件一起原子寫terminal.json；舊process無terminal的run顯示INTERRUPTED，不改已完成歷史。半行事件加eventTailIncomplete與EVIDENCE_INCOMPLETE；正式頁／完整事件行損毀報IOException。磁碟寫入失敗會禁止後續成功finish／export，Runner仍須捕捉例外並停止及報錯（Task12），不能忽略後繼續工作。
- 背景判讀只認LIVE、同run/process/site、非逆序時間。B1/B2第一段連續符合的區間需600秒，三槽[60,300)、[300,540)、[540,600)秒須獨立新頁與新nav，且600秒內完成四階段。只有心跳／cache／重複／缺證不可PASS；背景請求在回前景後才完成解析／落盤（含回應晚到）、取消或人工介入為FAILED。FULL另需RUN_COMPLETED及至少一個背景內新頁。
- 匯出僅result.md/events.jsonl/sample.json，保存版本、查詢／裝置快照、狀態、逐頁來源ID／價格／坪數／地區／排除／未知資訊；不匯出title/address/完整URL/imageUrl/原始HTML/BFF。金額字串往返保留798.00精度。
- Task4新增來源證據／排除碼時須遵循ProbeStore固定白名單，不能塞原始網頁文字。停止原因的enum／JSON已就緒；Task12接線需要具體terminal reason時，可在finish加預設null的reason參數一併原子寫入，不要自行append RUN_*。
- 僅單程序單Store範圍，未宣稱跨程序多寫者、突然斷電或真機檔案系統已驗證。UI／網站／背景服務未接入；Task1留存APK未覆蓋。
- 本輪所有測試命令已結束，無執行中工作或阻礙。下一步Task4先寫capture origin/mainFrame/navigationId/size及HTTP政策紅測試。Context百分比不可見，仍採逐Task停點。
## Task4 歷史結果與接續契約

- 新增 `PageTransport.kt`／`HttpPageTransport.kt`／`WebViewSession.kt`、assets `probe-bridge.js`／`selfcheck.html`、`CaptureGuardTest.kt`／`HttpPolicyTest.kt`／`src/test/js/probe-bridge.test.cjs`。無依賴、SDK、manifest、UI、模型、Store或後端變更。
- 最後完整命令：`./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --console plain --no-daemon '-Pkotlin.compiler.execution.strategy=in-process'` → **exit0／BUILD SUCCESSFUL，52 tests／0失敗／0錯誤，lint0 errors／5個既有鎖版新版提示**。紀錄 `validation/builds/task4/verified.log`／`build-info.json`；Node正式橋接資產檢查exit0，見 `bridge-final.log`。
- 13個新增JVM測試涵蓋精確origin／frame／ID／大小、訊息狀態及MIME、HTTP最多3次／2和4秒退避／至少3秒間距、取消disconnect、不重試429、robots逐path、redirect全鏈30秒、8MiB、POST與圖片headers。紅測試及review回歸log詳見Task4紀錄。
- HTTP與WebView必須注入**同一個RequestPacer**。WebViewSession在main thread建立，service持有application context實例，Activity僅attach/detach；HTTP的UA傳session.userAgent。每run開始clearSession清資源cache／history、不清登入cookie。兩種transport開始前由Runner呼叫HTTP checkRobots；null是未知，不能當明確允許。
- HTTP只許明列HTTPS來源／CDN，redirect逐跳驗證；connect15秒、整次嘗試含redirect／限速30秒、body8MiB、橋接每訊息2MiB／每頁8MiB。TLS保持平台預設，Accept-Encoding=identity；信義圖片才帶固定Referer。例外僅固定ProbeReason/status，沒有原始URL／本文。Task13仍須限制每次一張縮圖及decode尺寸。
- robots快取本文、逐path/query判斷，支援User-agent群組及literal Disallow；未支援Allow／wildcard／crawl-delay時未知。429向上回限流；其他取得失敗未知。不能當完整robots套件。
- WebView在loadUrl前注入不可變ID的document-start腳本；native再驗origin／mainFrame／當頁URL／ID／狀態／JSON MIME／大小。fetch保留原response並讀clone，XHR支援text／json（後者無法還原原始數字文字精度）。缺document-start明確UNSUPPORTED；只有listener缺失時可保留DOM採集候選，不能做JSON採集。
- 網路證據不能由收到訊息推論：同URL且本次請求後的timing、transferSize／encodedBodySize關係、排除service worker；DOM只認navigation timing。缺BFF或有cache／不可分辨的資料時false。Task5不得把候選網路判斷與fixture測試寫成實站已成功。
- 原生導航30秒＋DOM最多5秒，DOM穩定只是解析候選；來源parser仍負責CHALLENGE／INVALID／完整性。renderer退出回RENDERER_GONE並停止使用該實例；等待限速／evaluation／finally都防止操作銷毀WebView。不同URL舊錯誤不污染新頁；同URL重載的原生錯誤回呼無navigationId，可能保守失敗，真機待驗證。
- 兩位獨立唯讀review已完成。已修：redirect每跳重置timeout、robots多agent漏禁、舊timing套新回應、XHR json漏抓、同源redirect早期JSON遺失、不同URL舊錯誤、renderer等待／銷毀收尾、本機iframe假訊息不完整與假延遲案例。新feature lint警告已以實際guard修掉。原生callback修正只有編譯／靜態審查證據，未宣稱裝置測試通過。
- selfCheck=true才啟用AssetLoader本機HTTPS與延遲回應，未知路徑／外網都本地拒絕；selfcheck頁包含fetch／XHR／完整iframe訊息，下一頁可帶previousNavigationId重放舊ID。**Task13才接無adb入口並實跑**，此項保留未勾選；Task1留存APK未覆蓋。
- **Task5注意**：現有後端沒有可靠的591 BFF host/path樣本。橋接目前只接受sale.591.com.tw及search／house／sale／list路徑段，若Task5／真機證據顯示其他endpoint再擴充精確白名單。不能複製後端`url.Contains("591.com.tw")`。先建Fixtures.kt與合成樣本，測region／section／firstRow關聯及total=0不混入推薦。
- 以上為Task4歷史停點；Task5現已完成，最新結果如下。context實際百分比不可見。

## Task5 歷史結果與接續契約

- 新增 `probe/app/src/main/java/tw/houselens/probe/sources/F591Source.kt`、`F591SourceTest.kt`、`Fixtures.kt`、四份 `src/test/resources/fixtures/591/` 合成樣本；`ProbeStore.kt`只擴充固定evidence／unknownFields白名單。沒有依賴、SDK、manifest、transport、UI或後端變更。
- 最終完整命令（repo root）：`./android/probe/gradlew.bat -p android/probe :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --console plain --no-daemon '-Pkotlin.compiler.execution.strategy=in-process'` → **exit0／BUILD SUCCESSFUL，72 tests／0失敗／0錯誤，lint0 errors／5個既有鎖版新版提示**。紀錄 `validation/builds/task5/verified.log`／`build-info.json`；原始RED、GREEN及review回歸另見Task5紀錄。
- 591 maxPages40，每頁30；中和38／永和37／新店34、regionid3。房數及車位沿用既有映射；未知代碼拒絕。最高價與最小坪數保留精確小數，網站能否接受需真機核對。
- document必須是根搜尋路徑且query一致；BFF要求HTTPS精確host與候選path、regionid/section/firstRow唯一且精確（第一頁也必須明列0）。已出現的type/price/houseage/parking/area/pattern也須一致。缺關聯、不同body衝突或格式錯誤回INVALID；不把total與列表拼自不同回應。
- total0優先排推薦；正total須有正確的 `min(30,total-offset)` 原始卡數。原始keys先於排除，整頁過濾仍按total翻頁；缺total只能UNKNOWN。缺ID、boolean ID、壞價格、重複JSON卡或短頁不能偽裝成功。page1合成樣本補足30卡（首筆100001／750.00／28.50／total31），page2為100002／800；fixture永遠networkObserved=false。
- HTML備援採既有卡片selector但固定 `html_fallback`／UNKNOWN；無卡或壞卡INVALID，不宣告BFF等價。Store可落盤／匯出這個evidence。只有JSON原始資料去重不清楚時拒絕；HTML重複卡依ID去重。
- 新增未知欄位 `areaBasis/typeCodes/useCode/rooms/parkingCodes`。591面積基準尚未明確，不用areaPing本地排除minSizePing；預設住宅型態／用途尚無來源mapping；有車位不代表已證明指定車位類型。這些未知會保留到匯出，不能宣稱全部條件已核實。
- 獨立唯讀review找出短頁假完整、不同顯式篩選可供total0、boolean ID、漏標車位類型未知；主代理以RED→GREEN修完。另修正document path及total千分位格式。最終來源20項、既有52項全部通過，沒有必修遺留。
- 限制：沒有真實BFF endpoint/body；若實站省略offset、改POST、不同host/path或頁大小，會保守INVALID，需實際證據後調整。未重跑未修改的Node資產；未跑實站／WebView／adb／手機或背景。Task1留存APK未覆蓋，當前建置UI仍只有版本與未開始。
- 全部指令已結束，沒有阻礙；context實際百分比不可見。下一步Task6先讀RakuyaScraper與測試、沿用Fixtures，主建面積與HALF_EVEN取整、全過濾頁不停止是重點。依使用者要求此處停下。

## Task6 歷史結果與接續契約

- 新增 `probe/app/src/main/java/tw/houselens/probe/sources/RakuyaSource.kt`、`RakuyaSourceTest.kt`、`src/test/resources/fixtures/rakuya/apartment.html`／`house.html`／`filtered-page.html`。沿用Fixtures、ProbeRules、Store與transport，沒有依賴、SDK、manifest、UI或後端變更。
- 最終命令（repo root）：`./android/probe/gradlew.bat -p android/probe :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --console plain --no-daemon '-Pkotlin.compiler.execution.strategy=in-process'` → **exit0／BUILD SUCCESSFUL，87 tests／0失敗／0錯誤，lint0 errors／5個既有鎖版新版提示**。來源15項、既有72項通過；紀錄 `validation/builds/task6/verified.log`／`build-info.json`，其餘紅綠測試見Task6紀錄。
- 樂屋 maxPages30，page=pageIndex+1，warmup `https://www.rakuya.com.tw`；browser=true、captureJson=false。中和235／永和234／新店231，沿用usecode/typecode/price/size/room/age/other。空type/use回預設R1,R2／1；用途僅住宅1，型態R1–R6、車位PF／PM，不接受未知代碼。精確小數不取整，是否接受仍需實站核對。
- apartment→`0ca48612345678a`／2088萬／主建26.98坪／13年／有車位；house→`050b83313052929`／1580萬／總建45坪／44.5年HALF_EVEN→44。主建有效數字優先，否則總建；單價HALF_EVEN至2位。混用面積不做本地minSizePing排除，areaBasis仍標未知。
- 原始卡必須有安全ID與正價格，先驗證再過濾／去重；壞卡整頁INVALID。住宅白名單、價格、已知屋齡與地區套排除；整頁過濾仍保留rawKeys，下一頁有效資料可繼續。有效重複ID仍沿HTML取首筆，異價新舊未裁決；重複卡壞價格不會被忽略。
- 現有樣本沒有可靠末頁／空結果selector：非空頁一律`NextPage.UNKNOWN`／`evidence=unknown`，無卡INVALID；裸「共N筆」及缺next link不作END。重複末頁由共同規則回INCONCLUSIVE，上限TRUNCATE，尚不能證明實站完整掃描。
- 缺車位標籤保留false＋hasParking未知；房數、精確型態、用途、指定車位類型未逐筆核實會標未知。缺地區／城市以query補值時也加unknownFields。當頁文件須HTTPS精確host/path與相同query／page，拒絕舊頁、不同條件及重複query key。
- 獨立唯讀review找到兩項必修，均由主代理RED→GREEN修完：城市與行政區間空白造成跨區物件被補成查詢區；缺型態時把房數／屋齡／樓層當非住宅。回歸涵蓋普通／全形／NBSP空白及逐步缺欄位。沒有必修遺留。
- Store實際save/load/export測試通過，fixture networkObserved=false／SIMULATED，不能當LIVE。未重跑未修改的Node橋接資產；未跑實站／WebView／adb／手機或背景。Task1留存APK未覆蓋，當前UI仍只有版本與未開始。
- 全部指令已結束，沒有阻礙；context實際百分比不可見。下一步Task7先讀YungchingScraper與測試，住宅／型態、停車位、精選與合法空結果優先序是重點；依使用者要求此處停下。

## Task7 歷史結果與接續契約

- 新增 `probe/app/src/main/java/tw/houselens/probe/sources/YungchingSource.kt`、`YungchingSourceTest.kt`、`src/test/resources/fixtures/yungching/residential.html`／`parking.html`／`empty-featured.html`。沿用 Fixtures、ProbeRules、Store 與 transport；沒有依賴、SDK、manifest、UI 或後端變更。
- 最終命令（repo root）：`./android/probe/gradlew.bat -p android/probe :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --console plain --no-daemon '-Pkotlin.compiler.execution.strategy=in-process'` → **exit0／BUILD SUCCESSFUL，103 tests／0失敗／0錯誤，lint0 errors／5個既有鎖版新版提示**。來源16項、既有87項通過；見 `validation/builds/task7/verified.log`／`build-info.json`。
- 永慶 maxPages30，第一頁無query、第二頁 `pg=2`；browser=true、captureJson=false、warmup首頁。URL沿用現碼城市／區、價格、型態、坪數、住宅、車位、屋齡順序；價格／坪數保留精確小數。R1→無電梯公寓、R2→電梯大廈＋華廈，卡片對應公寓／住宅大樓／華廈；PF／PM與原生車位名稱／y沿現碼映射；未知代碼或非住宅useCode拒絕。
- 住宅樣本→1234567／750萬／建坪28.50／12.5年HALF_EVEN→12；車位樣本→9876543／1280萬／35坪／8年／永和區／有車位。地址明確跨區時排除，不能用query覆寫。缺城市／區域才以query補值並標未知；車位缺標記／負向文案保守未知。房數未有URL映射，房數、用途、精確車位類型與面積篩選基準未核實保留unknownFields。
- document必須HTTPS精確host、相同解碼後path與相同query／page；優先序為URL核對→CHALLENGE→HTTP狀態→合法空結果→卡片。卡片ID必須合法本站house數字路徑且價格正值；壞卡整頁INVALID，先驗證再去重／篩選。有效重複ID沿HTML取首筆，異價新舊仍未裁決。
- 固定「暫無符合物件」空標記回EMPTY／END／empty_marker，忽略本週精選，rawKeys與listings皆空；卡片／腳本／模板及明確隱藏元素中的文字不算空標記。無卡無標記INVALID，非空頁UNKNOWN，全過濾頁保留rawKeys繼續，重複頁INCONCLUSIVE、上限TRUNCATE；尚不能證明實站完整分頁。
- 獨立唯讀review發現兩項必修並由主代理RED→GREEN修完：inline隱藏空標記誤報END；「無 車位」誤確認有車位。新增回歸含display:none、visibility:hidden／collapse、大小寫／important、NBSP與拆span負向文案。沒有必修遺留。外部CSS的computed visibility不由靜態HTML還原，實站空標記仍需核對。
- Store save/load/export實測通過，fixture networkObserved=false／SIMULATED，不可當LIVE。圖片沿用來源縮圖，不照搬放大1200。未重跑未修改的Node資產；未跑實站／WebView／adb／手機或背景。Task1留存APK未覆蓋，當前UI仍只有版本與未開始。
- 全部命令已結束，沒有阻礙；context實際百分比不可見。下一步Task8先讀HBHousingScraper與測試，重點為10張原始卡全過濾仍MORE、1–9張正確原始卡末頁證據、0卡合法空標記；依使用者要求此處停下。

## Task8 歷史結果與接續契約

- 新增 `probe/app/src/main/java/tw/houselens/probe/sources/HBHousingSource.kt`、`HBHousingSourceTest.kt`、`src/test/resources/fixtures/hbhousing/residential.html`／`parking.html`／`ten-filtered-cards.html`。沿用 Fixtures、ProbeRules、Store 與 transport；沒有依賴、SDK、manifest、UI 或後端變更。
- 最終命令（repo root）：`./android/probe/gradlew.bat -p android/probe :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --console plain --no-daemon '-Pkotlin.compiler.execution.strategy=in-process'` → **exit0／BUILD SUCCESSFUL，120 tests／0失敗／0錯誤，lint0 errors／5個既有鎖版新版提示**。來源17項、既有103項通過；見 `validation/builds/task8/verified.log`／`build-info.json`。
- 住商 maxPages100，原始頁大小10，第一頁省略頁碼、第二頁 `/2-page`；browser=true、captureJson=false、warmup首頁。中和235／永和234／新店231，URL沿型態／價格／住宅／坪數／屋齡／房數／車位順序。R1→noelevator，R2→elevator＋mansion，卡片對應公寓／大樓／華廈；空type沿後端預設住宅白名單，未知代碼或非住宅useCode拒絕。小數不取整，房數取最小值，PF／PM都映射parking-tag；精確房數與車位類型保留未知。
- 合成住宅樣本→YS202291／750萬／29.05坪／30.7年HALF_EVEN→31；車位樣本→ZS203105／1280萬／35.00坪／8年／有車位。沿後端結構，地址改為新北中和／永和；明確跨區照實排除，缺地址／地區以query補值且標未知。圖片保留原來源URL及時間戳，仍受精確CDN白名單限制。
- 當頁文件須HTTPS精確host、相同解碼後path與相同query。CHALLENGE先於卡片；ID須本站 `/detail?sn=` 加完整英數ID，忽略fragment標題連結，現價須正值。每張原始卡先驗ID／價格，再過濾及去重；壞重複卡或被排除的壞卡仍整頁INVALID。
- **rawKeys保留每張原始卡，包括重複ID**；listings／excluded才依ID去重。10張正確卡一律MORE，全部排除也繼續；1–9張以last_page回END，超過10張保留UNKNOWN。重複滿頁由共同規則INCONCLUSIVE；短頁的獨立末頁證據仍優先於重複檢查，未改共用規則；100頁仍MORE時TRUNCATE。
- **尚無可靠住商零筆標記樣本，0卡保守INVALID**；未發明EMPTY規則。此限制可能讓真正零筆搜尋無法通過，待實站證據補完。短頁與小數URL條件仍需實站核對；建坪不代表已知查詢坪數基準，不本地排除minSizePing，areaBasis仍未知。
- 主代理自查修正型態辨識把「大樓／廠房」當其他欄位；`type-red.log`重現後修正。獨立review找到車位文案過寬；`review-red.log`重現「車位：無／無固定車位／車位另租」誤認true，已改為只認明確「有車位／車位」（允許空白／拆span），其餘未知。兩項均有RED→GREEN，沒有必修遺留。
- Store save/load/export實測通過，保留重複rawKeys與分頁evidence；fixture networkObserved=false／SIMULATED，不可當LIVE。未重跑未修改的Node資產；未跑實站／WebView／adb／手機或背景。Task1留存APK未覆蓋，當前UI仍只有版本與未開始。
- 全部命令已結束，沒有阻礙；context實際百分比不可見。下一步Task9先讀SinyiScraper與測試，重點為只取搜尋卡／不取全站__NEXT_DATA__、20張原始卡分頁、首圖影片與Next image解碼、信義圖片Referer；依使用者要求此處停下。

## Task9 歷史結果與接續契約

- 新增 `probe/app/src/main/java/tw/houselens/probe/sources/SinyiSource.kt`、`SinyiSourceTest.kt`、三份 `src/test/resources/fixtures/sinyi/` 合成樣本（search／next-data-only／video-first）。沿用 Fixtures、ProbeRules、Store 與 HTTP transport，無依賴、SDK、manifest、共用程式、UI或後端變更。
- 最終命令（repo root）：`./android/probe/gradlew.bat -p android/probe :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --console plain --no-daemon '-Pkotlin.compiler.execution.strategy=in-process'` → **exit0／BUILD SUCCESSFUL，137 tests／0失敗／0錯誤，lint0 errors／5個既有鎖版新版提示**。來源17項、既有120項全通過；見 `validation/builds/task9/verified.log`／`build-info.json`。原始RED缺SinyiSource；review回歸RED為17項中2項失敗，見Task9紀錄。
- 信義 maxPages5、原始頁大小20，HTTP GET／browser=false／captureJson=false／無warmup。中和235、永和234、新店231；URL沿價格／型態／車位／坪數／屋齡／最小房數／城市／zip／排序／1-based頁碼。R1→apartment、R2→dalou＋huaxia、R4→villa、R5→townhouse；PF／PM沿現碼映射加yesparking；未知代碼或非住宅用途拒絕，小數不取整。
- 只解析 `div.buy-list-item` 搜尋卡與 `LongInfoCard_Type_*` 語意class，不綁hash，不收全站 `__NEXT_DATA__`。合成主卡3087NK／750萬／28.50坪／28.4年HALF_EVEN→28。當頁URL須精確安全host/path/query；CHALLENGE先於卡片，HTTP失敗或壞卡INVALID。
- 每卡先驗本站house路徑ID與正價格，壞卡不能因過濾／重複被略過。rawKeys保留每張原始卡（含重複），listings／excluded才去重；20卡MORE（全過濾仍繼續）、1–19卡END／last_page、超過20卡UNKNOWN。沒有可靠零筆marker，0卡仍INVALID。短頁END沿共同規則優先於重複判斷；上限5頁仍MORE則TRUNCATE。
- 地區從地址解析，明確跨區排除，缺值才補query並標未知。沒有可靠型態／用途／車位selector，typeCodes／useCode／hasParking保持未知，不能用標題有「車位」或URL宣稱符合；房數、車位類型、面積基準同樣保留未知。建坪不直接套minSizePing。
- 價格先移除標題／地址，再驗完整數字候選；多個不同金額保守INVALID，不取第一個可能為原價的金額。獨立review找到 `1, 750萬`／`7.5e2萬`／`−750萬` 被截尾成合法價格，以及B1／地下／跨樓層被截成地上單一樓層；主代理以RED→GREEN修完，新增分數／混合全形金額回歸。未知樓層保留null＋floor未知。reviewer已再核對修正，沒有必修遺留。
- 圖片按DOM順序解碼Next image後驗HTTPS精確host；URI path識別影片，包含帶query的mp4／m3u8／webm／mov、/video/及_video，並支援data-src。找不到可用圖時沿來源fallback `res.sinyi.com.tw/buy/{id}/smallimg/A.JPG`，標imageUrl未知；未假造圖片成功。沿既有loadImage，Referer僅限信義圖片，測試確認無Authorization／apikey／Cookie；圖片404後Store列表仍可load/export，UI單獨顯示失敗留Task13。
- fixture均SIMULATED／networkObserved=false。真實DOM、合法零筆、含多金額卡片、短頁、小數URL、圖片可用性仍待實站核對；合成測試不證明手機或背景成功。未跑實站／WebView／adb／手機／背景，未重跑未修改Node橋接。Task1留存APK未覆蓋，當前UI仍只有版本與未開始。
- 全部命令已結束，無阻礙；context實際百分比不可見。下一步Task10先讀CtHouseScraper與測試，重點為POST JSON `{arg,page}`、RS／houses／totalpage envelope驗證、sell_price現價、跨區排除與全過濾頁不停止；依使用者要求此處停下。

## Task10 歷史結果與接續契約

- 新增 `probe/app/src/main/java/tw/houselens/probe/sources/CtHouseSource.kt`、`CtHouseSourceTest.kt`、三份 `src/test/resources/fixtures/cthouse/` 合成JSON（results／discounted／error）。沿用 Fixtures、ProbeRules、Store 與 HTTP transport；無依賴、SDK、manifest、共用程式、UI或後端變更。
- 最終命令（repo root）：`./android/probe/gradlew.bat -p android/probe :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --console plain --no-daemon '-Pkotlin.compiler.execution.strategy=in-process'` → **exit0／BUILD SUCCESSFUL，153 tests／0失敗／0錯誤，lint0 errors／5個既有鎖版新版提示**。來源16項、既有137項全通過；見 `validation/builds/task10/verified.log`／`build-info.json`。原始RED缺API，review-red為16項中2項依預期失敗。
- 中信 maxPages100；POST `https://buy.cthouse.com.tw/api/house_list.ashx`、JSON `{arg,page}`、page=pageIndex+1、captureJson=true／browser=false／無warmup。arg沿城市／區／價格／坪數／型態／屋齡／最小房數／車位順序，型態排序電梯大樓、公寓、套房、透天；R2/R1/R3/R5分別映射class2/1/3/6。空type預設電梯大樓＋公寓；未知代碼、非住宅用途拒絕。PF/PM僅有車位，精確類型未知；價格／坪數保留小數。
- ResidentialItem→2097688／788萬／8.18坪／單價96.33／10年／無車位（明確0）。Discounted→2066497／現價798.00，絕不退回原價858；原C#樣本在桃園，合成樣本地址明確改成新北中和；另測板橋／桃園推薦須排除。
- 精確HTTPS endpoint核對後驗整個envelope：RS須原始JSON字串精確OK；totalpage必須非負JSON整數，houses必須陣列。非空頁須頁碼不超過totalpage；零筆只接受第一頁、totalpage0或1、houses空陣列，其餘空頁INVALID。**空頁慣例仍是合成裁決，需實站核對**；超過100總頁數不算格式錯誤，抓到上限仍MORE則TRUNCATE。
- 每筆先驗安全ID／正現價，再依用途／型態與共用規則排除。rawKeys保留每張原始卡（含重複）；相同ID相同資料僅留一筆，衝突資料／壞重複卡整頁INVALID。全過濾頁仍依totalpage繼續；非末頁重複由共同規則INCONCLUSIVE，可信末頁證據仍優先。
- 缺城市／區才補query並標未知，已知跨區不可覆寫；缺用途／型態／車位仍未知，車位只有0/1可確認。總建坪不直接套minSizePing，areaBasis未知；房數與車位精確類型仍未逐筆核實。價格／面積／單價直接由原始字串轉BigDecimal，單價fallback HALF_EVEN兩位；ISO來源日期保留，其他日期格式未知。
- 獨立review兩項必修均由主代理RED→GREEN修完：200 HTML人機驗證被誤當INVALID；RS被display helper trim成OK。現在HTML既有challenge marker回CHALLENGE／WAIT_FOR_USER，正常錯誤JSON與含驗證文字的物件不會偽裝challenge；RS精確比較。Reviewer再核對確認沒有必修遺留。非2xx仍由transport回HTTP_ERROR，未宣稱能讀403驗證本文。
- 實際HTTP transport整合測試只替換連線邊界，驗POST／JSON body／Content-Type／navigationId／RawCapture.json；Store save/load/export保留798.00及白名單證據。arg/page不在回應URL，頁次關聯需由當次transport呼叫與Task12 Runner navigationId維持，parser不能單靠endpoint證明頁次。
- fixture均SIMULATED／networkObserved=false；未跑實站、WebView、adb／手機／背景或未修改的Node橋接。空頁、小數條件、欄位與圖片可用性仍待實站核對；Task1留存APK未覆蓋，當前UI仍只有版本與未開始。
- 全部命令已結束，無阻礙；context實際百分比不可見。下一步Task11先讀TwHouseScraper與測試，重點為建坪非地坪、現價、合法空標記、全過濾頁繼續，列表缺地址／樓層／車位保持未知；不自行加逐屋詳情請求。依使用者要求此處停下。

## Task11 最新結果與下一步

- 新增 `probe/app/src/main/java/tw/houselens/probe/sources/TwHouseSource.kt`、`TwHouseSourceTest.kt`、三份 `src/test/resources/fixtures/twhouse/` 合成HTML（residential／discounted／empty）。沿用 Fixtures、ProbeRules、Store 與 HTTP transport；沒有依賴、SDK、manifest、共用程式、UI或後端變更。
- 最終命令（repo root）：`./android/probe/gradlew.bat -p android/probe :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --console plain --no-daemon '-Pkotlin.compiler.execution.strategy=in-process'` → **exit0／BUILD SUCCESSFUL，175 tests／0失敗／0錯誤，lint0 errors／5個既有鎖版新版提示**。來源22項、既有153項全通過；見 `validation/builds/task11/final.log`／`build-info.json`。原始RED缺API；review-red為21項中5失敗；nested-price-red重現巢狀負號。
- 台灣房屋 maxPages30，HTTP GET／browser=false／captureJson=false／無warmup；中和235／永和234／新店231，1-based `?page=N`。型態順序apartment／midrise／condo，R1→apartment、R2→midrise＋condo；空type預設三種。房數取最小值，PF／PM僅映射有車位；未知代碼／非住宅用途拒絕。價格與坪數保留小數，坪數必帶 `fullBuildingPing-ping_type`。
- Residential→A123456789／760萬／建坪34.56／25年／單價21.99；Discounted→TF02157997／現價1480萬／32.00坪／永和區，不能取原價1800。只讀建坪，不讀地坪。屋齡／單價HALF_EVEN；areaBasis及精確條件仍待實站核實，不新增本地minSizePing排除。
- 當頁須精確安全HTTPS host/path/query；challenge先於空頁，HTTP失敗／壞卡INVALID。排除列表導覽與非房源標題連結，有房源內容的壞ID仍報錯。每卡先驗安全ID與正現價，再篩選／去重；rawKeys保留重複原始卡。同ID完整listing與排除理由須一致，衝突整頁INVALID。
- 非空頁UNKNOWN／unknown，全過濾頁保留rawKeys繼續；重複頁INCONCLUSIVE，30頁上限TRUNCATE。合法可見「暫無查詢物件」回EMPTY／END／empty_marker，忽略推薦卡；卡片／script／template／hidden／inline隱藏文字不算空證據。外部CSS可見性仍需實站核對。
- 城市與行政區分開解析，含全形／NBSP空白；已知跨區排除，缺值才補query並標未知。完整地址／樓層／車位／刊登日期列表未取得；null／false只是佔位，unknownFields明示。型態／用途、指定房數／車位型式未逐筆核實；沒有接入舊ParseDetail或增加逐屋請求。完整欄位矩陣在Task11紀錄。
- 獨立review六項均RED→GREEN修完：地址「萬」誤當價格、缺現價退回舊價、重複卡住宅判斷衝突、非房源標題導覽誤當卡片、缺行政區覆寫已知城市、巢狀金額略過父層負號。Reviewer已核對最終修正，沒有必修遺留。
- 現價直接驗既有住宅 `.mt-auto` 容器或降價卡末端直接欄位的完整文字，允許數字／單位拆標籤，拒絕負號／待更新／錯誤單位／空白。**未知價格版型或容器同時含多個金額會保守INVALID**；不得改回最後一個成功解析金額，需取得實站DOM後補selector。程式已註明shortcut。
- Store save/load/export實測通過；fixture均SIMULATED／networkObserved=false。未跑實站、WebView、adb／手機／背景或未修改的Node橋接。圖片／空頁／小數條件與目前網站結構仍待實站確認；Task1留存APK未覆蓋，當前UI仍只有版本與未開始。
- 全部命令已結束，無阻礙；context實際百分比不可見。下一步Task12先核對Store／Report／transport接線契約，寫Runner／Recovery紅測試：單作業、filtered→valid→END、取消／晚到回應、存檔失敗、頁限／challenge及舊程序恢復，再接服務與生命週期。依使用者要求此處停下。

## Task12 最新結果與下一步

- 新增 `ProbeRunner.kt`、`ProbeService.kt`、`DeviceSnapshot.kt`、`ProbeRunnerTest.kt`、`ProbeRecoveryTest.kt`；修改manifest、MainActivity生命週期、WebView存活／renderer callback、Store terminal reason／電量，以及Models／JSON選填電量欄位。沒有新增依賴、調整SDK、修改來源adapter／後端或正式資料。
- 最終命令（repo root）：`./android/probe/gradlew.bat -p android/probe :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --console plain --no-daemon '-Pkotlin.compiler.execution.strategy=in-process'` → **exit0／BUILD SUCCESSFUL，203 tests／0失敗／0錯誤，lint0 errors／5個既有鎖版新版提示**。Runner27＋Recovery1＋既有175；最終為`validation/builds/task12/final-current.log`與`build-info.json`，不能誤用中間紅燈log。
- Runner main-confined、Store IO串行；start忙碌false，取消後必須等舊capture／commit／事件／terminal收尾才釋放。filtered→valid→END按raw推進；FORE最多3頁，FULL來源cap；網路錯誤／renderer／解析／磁碟／系統拒絕均固定reason，不能將晚到回應算入新run。
- B1/B2先第1頁，首次符合背景條件後+60/+300/+540秒取頁，+600秒評估；不足新頁／缺網路證據／未滿600秒為INCONCLUSIVE。COMPLETED只是這個觀察流程完成，不代表完整搜尋；完整輪次用FULL。所有測試均虛擬時間，不是真機背景證據。
- WAITING不自動重試、不持wake lock，最多5分鐘elapsed；只接受同run／程序／存活browser session。人工解驗證後重新導航同頁，會用新navigationId，原run的背景判讀仍FAILED。HTTP challenge不提供假WebView續跑；session失效需重新開始。休眠時callback可能延遲，但恢復或resume後不得用過期等待繼續。
- Service使用dataSync／START_NOT_STICKY，只有可見Activity點擊可start；通知有返回／取消。onTimeout立即取消、釋放lock與stopSelf，不等待IO。背景15分鐘／其他90分鐘，Runner與wake lock共用截止，且每個網路／磁碟suspension後核對elapsed，避免uptime計時晚醒後補發。
- 每run重讀裝置／網路／電量／省電／螢幕初態；只記系統非識別資訊。processId與Store同程序單例（初始化同步）；不另建writer。terminal原子保存停止原因及結束電量，export列起訖百分比；舊JSON沒有batteryPercent仍可讀。百分比差不是校準耗電量，真機仍需記充電情境。
- 獨立review分Runner與Service。已修：立即取消卡槽、集合初態已背景未排程、空末頁／提前返回誤COMPLETED、commit期間漏生命週期造成假PASS（含同毫秒）、elapsed跨期限仍發capture／第二個robots、人工等待過期仍resume、服務升前景拒絕未立即停止、多Activity舊onStop拔掉新WebView。邏輯問題皆有RED→GREEN；原生兩項以靜態review／編譯／lint覆核，沒有假稱真機已測。兩位reviewer最終沒有必修遺留。
- **保守邊界**：頁提交期間的前背景／螢幕變化直接FAILED／EVIDENCE_LOST，避免未知事件先後造成假PASS；需重跑取得乾淨觀察區間。磁碟連terminal也不能保存時，UI state為FAILED／STORAGE_ERROR，不得提示「已保存」。
- **Task13入口**：Activity bind取得LocalBinder（state/busy/start/startCollection/resume/cancel/attach/detach）；bind本身不啟動採集或WebView。現有MainActivity已接onStart/onStop。真正容器接線時補identity-matched `detach(container)`；重建attach同一session，不能新建第二個WebView。Store export／load在IO；不要在UI另外建立Store。
- 七站集合只由明確按鈕啟動，固定591→樂屋→永慶→住商→信義→中信→台灣房屋；WAITING暫停集合，取消／失敗／中斷停止集合；衝突不排隊。Task13負責畫面、結果／歷史、通知拒絕提示、App內取消、主動匯出、圖片限制、本機自檢與新版本APK打包。
- 未跑實站／WebView原生callback／手機／adb／背景；未重跑未改動的Node橋接。Task1留存APK未覆蓋，當前UI仍只有版本與未開始。沒有任何七站或背景真機成功結論。
- 所有命令已結束，無阻礙；context實際百分比不可見。本輪依逐Task要求停下，下一步Task13先寫輸入／匯出紅測試，再接binder與自檢。

## Task13 最新結果與下一步

- 主代理完成操作UI、輸入／七站預驗、單站／集合／取消／resume、通知拒絕提示、結果／未知欄位、歷史／縮圖、CreateDocument主動ZIP、本機WebView自檢。新增檔案與完整契約見Task13紀錄。沿既有Store單例與IO，不加依賴、不改SDK、來源parser、Web／後端／Supabase。
- 最終完整 `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --console plain --no-daemon -Pkotlin.compiler.execution.strategy=in-process` **exit0／BUILD SUCCESSFUL，212 tests／0失敗／0錯誤，lint0 errors／5個既有新版提示**；`validation/builds/task13/final.log`。Node正式橋接資產PASS（bridge-final.log），依賴解析exit0（dependencies.log）。簽章驗證exit0，同Task1憑證。
- 版本0.2.0／code2，已打包 `validation/builds/task13/HouseLens-probe.apk`；SHA、簽章、SDK／依賴／git基底及每檔source hash在build-info。未提交APK、logs、私鑰或既有無關未追蹤文件。
- RunRecord新增可空appVersion；Runner保存當次版本，JSON舊紀錄缺值仍可讀，但顯示UNKNOWN，不能用目前版本補造。匯出另外記exportedWith。歷史load／loadPage／export走IO。UI背景新頁摘要也拒絕重用page/nav或EVIDENCE_LOST，不能只數PAGE_SAVED。
- WebView仍由Service持有；容器detach檢查identity，同容器attach不重新移除。Service本機忙碌槽讓selfcheck／圖片／匯出與LIVE互斥。通知返回沿CLEAR_TOP／SINGLE_TOP。沒有新增排隊／清除資料功能。
- 自檢只用本機assets，fetch／XHR、iframe送出後拒絕、延遲舊ID重放後拒絕均需marker，ready等完成再capture；WebView全程60秒上限。舊ID重放不等於真實跨導航晚回應已測。TIMEOUT／RENDERER_GONE固定注入走native收尾，標SIMULATED，不能冒充系統真機證據。
- 自檢另建立LOCAL_SELF_CHECK的LOCAL1／798.00萬／28.50坪樣本，測保存／重讀／ZIP，可從歷史主動匯出。自檢項目結果留畫面供回報；不計入LIVE背景通過。
- 縮圖只能明確點擊單張，HTTP沿白名單／Referer／8MiB限制，bitmap先驗尺寸與5000萬像素上限，再縮採樣最長邊512。圖片結果留本次畫面供回報，失敗不影響房源或匯出。
- Review兩份已完成。修正選擇器返回早於binder連接時遺失匯出、旋轉取消本機作業後提示遺失；重建會提示檢查／刪除不完整ZIP再手動重試，不宣稱續傳。歷史版本測試改用不同的0.1.7及精確標題；自查背景新頁重用nav問題以RED→GREEN修正。原生生命週期只有編譯／lint／靜態review，沒有裝置證據。
- **Task14入口**：使用者說繼續後先請安裝0.2.0並回報版本、裝置／WebView、本機自檢、歷史重讀與ZIP；取得實際結果才填驗證summary。之後依既定順序一次一站前景，再做B1／B2／FULL。不要因本機測試或自檢PASS就勾七站／背景成功。全部14 Task完成後仍需獨立整體review。
- 尚未跑手機／adb／模擬器／實站／背景；原生WebView、Compose排版／大字體、通知拒絕、picker／旋轉、圖片、更新保留資料均待Task14。所有命令已結束，無阻礙；context實際百分比不可見。本輪依逐Task停點，等待使用者繼續。

## Task14 目前進度與下一步

- 2026-11-06使用者已要求讀交接後繼續。沿原工作區 `android/probe-bootstrap`、既有逐Task紀錄與固定交接檔執行，不重建工程／worktree；全部Android產物仍在 `android/`。
- 交付前實際核對：`Get-FileHash -Algorithm SHA256` 比對APK及build-info列出的81個來源檔案，全部一致；`apksigner.bat verify --verbose --print-certs` exit0、憑證符合Task13；`aapt2.exe dump badging` exit0，套件 `tw.houselens.probe`／0.2.0／code2。證據位於 `validation/builds/task14/preflight.json`、`signature.log`、`apk-badging.log`，均忽略不入Git。
- 已提供使用者首次驗收操作：安裝Task13 APK→執行本機自檢→回報App／Android／WebView與PASS／FAIL→本機歷史確認LOCAL1／798.00萬／28.50坪→關閉重開讀相同runId→主動匯出ZIP。若無法安裝／啟動，先收錯誤文字定位。
- **已收到手機結果**：7份ZIP全部為0.2.0、LOCAL_SELF_CHECK／COMPLETED、1頁1筆LOCAL1／798.00萬／28.50坪、background NOT_TESTED。原始三檔已審查並保存於 `validation/20261106-local-selfcheck/<runId>/`，manifest記原ZIP SHA-256及UTC／台北時間；原ZIP在使用者指定 `androidtest/` 保持不動。七份是本機樣本，不能當成七站或中信採集證據。
- 使用者明確回覆「1.對 自檢都是PASS 2.有，重開後仍可讀取」。自檢8項與重開讀取記為使用者回報通過；ZIP內容／版本／樣本記為檔案核對通過。Step1已勾選，Step2–5未完成。後續591兩筆結果如下，不影響本機驗收紀錄。
- 實際裝置SM-S9360／Android17／SDK37／WebView156.0.8078.47，Wi-Fi、省電false、電池最佳化豁免false，build與安全更新見summary。7份只有一個processId、每份一筆RUN_COMPLETED；沒有跨程序／背景／LIVE證據。ZIP不含自檢PASS清單，不能靠COMPLETED替代使用者回報。
- 後續使用者以2份591 ZIP更新androidtest。原樣三檔與hash存 `validation/20261106-591-foreground/`：bdddc2f3為TIMEOUT（請求後21.280秒），8aeee239已capture回傳／PAGE_PARSED 0/0後PARSE_ERROR；皆0頁0筆、無PAGE_SAVED、背景NOT_TESTED。不是3頁頁限或合法零結果。使用者看得到網站但畫面太小，無法辨認內容。
- 唯讀診斷與主代理追查一致：目前事件未區分TIMEOUT階段／來源，也沒保存INVALID分支碼、HTTP狀態、JSON匹配數等；networkObserved=false不能推論離線。未定位根因，不改來源白名單、parser或timeout。
- 主代理修改4個app檔（MainActivity尺寸、WebViewSession縮放、ProbeScreen提示、build版本）；無依賴、SDK、橋接、Runner、Store或後端變動。初次lint提示ConfigurationScreenWidthHeight已改LocalWindowInfo修正。最終完整Gradle exit0／212 tests 0失敗0錯誤／lint0 errors、5個既有新版提示，見 `validation/builds/task14/0.2.1/final.log`；同簽章及versionCode3驗證成功。未重跑未改動的Node橋接。
- 可讀性獨立review沒有必修問題；保留平台內建縮放按鈕（非阻擋備註：UI已棄用，具體可用性／網站縮放限制待真機）。尺寸重算沿同一WebView與identity detach，不另建視窗或導航；簡單配置／文字不造鏡像測試。
- 未使用adb、模擬器、Supabase或電腦代抓，未宣稱591修復／任何背景通過。所有命令與代理均已結束，下一個外部依賴是更新0.2.1後的591一次重試與畫面；若仍無法定位，再補受限固定碼診斷。Task14完成後仍須獨立整體review。Context實際百分比不可見。

## 0.2.1重試與0.2.2最新交付

- 使用者更新androidtest為1451b374／d9a84839兩ZIP與97155.jpg。原樣去敏匯出位於 `validation/20261106-591-foreground-0.2.1/<runId>/`，manifest記hash；原截圖只備份到忽略的 `validation/raw/20261106-591-0.2.1/`，screenshot.json記hash。不要強行關聯截圖到某筆run，因其ID被裁；不以2380萬起社區卡推論價格篩選失效。
- 兩筆不同程序皆收到capture、networkObserved=false、PAGE_PARSED0/0→FAILED PARSE_ERROR，0頁0筆。截圖已能看見手機版列表；根因仍無法從舊ZIP重建，不能再用放大UI或猜selector當修復。
- 主代理新增15個ProbeReason.F591_*，F591Source各INVALID gate以enum.name回evidence；Runner只在F591＋INVALID時精確匹配固定enum，保存PAGE_PARSED.reason與terminal.reason。未知字串fallback PARSE_ERROR。沒有新JSON欄位／Store白名單擴充／原始URL、HTML、BFF匯出；既有UI直接顯示停止原因。
- 新增3個測試方法（17個parser案例涵蓋15碼、真Store重讀及ZIP、不可信evidence），RED為50項中2項預期失敗，GREEN全套215項0失敗0錯誤、lint0 errors及5個既有版本提示；最終 `validation/builds/task14/0.2.2/final.log` exit0。APK/code4/同憑證及81source hashes已核對。
- 獨立scope／code review無必修，未放寬成功、EMPTY、CHALLENGE或資料驗證條件。只改多重文件錯誤的診斷優先序URL→PATH→QUERY。舊紀錄名稱／null照讀，不承諾舊APK降版讀新原因碼。
- 全部命令／代理已結束，Task14仍未完成；下一步0.2.2一次591前景的ZIP／原因碼。未訪實站、未用adb、未跑未改動Node橋接、不開始背景或其他Task；沒有新增依賴。Context百分比不可見。

## 工具鏈與接續注意事項

- 功能分支 `android/probe-bootstrap`，起點 `7ead221`。因使用者固定完整路徑，在原工作區切功能分支，未建立其他worktree。不要合併main或推送，除非另獲指示。
- JDK下載並驗checksum：Microsoft17.0.21.0.2+1，位於 `android/.tools/jdk-17.0.21.0.2+1/`；不變更全域Java。PATH預設仍為JDK11，建置必須照README設定JAVA_HOME。
- SDK位於 `android/.tools/sdk/`：Platform37.0 revision2、Build Tools36.0.0、Command-line Tools19.0；AGP自動裝Platform Tools37.0.1。compile/target仍37，min26。
- Gradle9.3.1、AGP9.1.1、Kotlin／Compose compiler2.2.10；其餘依賴維持原候選鎖版，實際全組已解析編譯。Compose BOM2026.09.00解析UI1.12.1與Material3 1.4.0。
- `GRADLE_USER_HOME=android/.tools/gradle-home`、`ANDROID_USER_HOME=android/.tools/android-user`；debug.keystore位於後者，保持同一份才能更新已安裝APK，**不可提交或匯出私鑰**。
- `probe/local.properties` 的sdk.dir是 `F\:/SourceCode/HouseLens/android/.tools/sdk`，檔案不入Git。
- 既有未追蹤 `.claude/settings.local.json`、其他 `android/` 文件、根 `handoff/` 需保留。只stage指定檔案，不用git add .、reset或clean。全域ignore讀取警告為既有情況，不改全域設定。
- 不輸出 `backend/appsettings.Local.json`，不得抄入APK。

## 已裁決的契約細節

- Site.code固定0–6；ProbeListing保留C#同名camelCase與nullable、BigDecimal與單位，sourceSite不改成site。未知欄位用unknownFields，日期保留ISO來源字串。
- EvidenceSource=LIVE/LOCAL_SELF_CHECK/SIMULATED，與RunMode四種執行模式分開；RunRecord與ProbeEvent必須明確提供，無預設LIVE。Task3判讀／匯出須排除非LIVE證據。
- DeviceSnapshot目前只有純資料型別；Task12才讀取Android資訊。PageDecision／查詢驗證已由Task2完成；事件enum／白名單、持久化、匯出與背景判讀已由Task3完成；transport介面留Task4。
- 所有後續交接（含Task14）固定寫入本檔 `android/handoff/handoff.md`，每次直接覆寫同名檔案。

## 不要改掉的產品決定

Samsung S25+／One UI9.0／Android17由使用者提供；自行安裝及回報，**不開USB偵錯**。七站皆保留，先驗採集與背景；只有本機資料及主動匯出，不先做Google登入／Supabase／完整App。只有一個正式Supabase專案，不准當試驗庫。衝突提示忙碌並手動重試，不自動排隊。若某站只能前景，先提供證據由使用者決定，不自動縮站或改.NET代抓。

下一個停點仍更新本檔：當前Task、完成／未完成、檔案、實際命令／結果、review／修正、依賴變動、執行中指令或阻礙與下一步。Context讀數不可見就如實寫不可見。
