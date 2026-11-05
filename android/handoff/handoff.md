# HouseLens Android 驗證 APK 交接

更新：2026-11-06（Asia/Taipei）。工作目錄：`F:\SourceCode\HouseLens`。請以繁體中文溝通。

## 下一輪從這裡開始

**Task 6 實作、測試與獨立 review 已完成，停在使用者指定的逐 Task 停點；Task 7 尚未開始。** 等使用者說「繼續」，再依 [驗證 APK 實作計畫](../plans/2026-11-03-probe-apk-implementation.md) 執行 Task 7：永慶住宅與精選排除。不要重做 Task1–6 或重問執行方式。

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
| Task3 | **完成**：本機Store／JSON、恢復／匯出與背景證據判讀；尚未接UI |
| Task4 | **完成**：HTTP／WebView候選與橋接邊界，未接UI；本機頁尚未在WebView實跑 |
| Task5 | **完成**：591 adapter／合成樣本／20項來源測試，未接UI、未實站驗證 |
| Task6 | **完成**：樂屋adapter／三份合成樣本／15項來源測試，未接UI、未實站驗證 |
| Task7–13 | **未開始**：目前手機畫面仍不能抓取、保存或匯出 |
| Task14 | **未開始**：尚無手機安裝、七站或背景成功證據 |
| Web／Supabase／正式App | 未修改或部署 |

Task1 主要檔案：`probe/` 內的Gradle配置／wrapper、manifest／resources（含備份規則）、`ProbeModels.kt`、`MainActivity.kt`、`ProbeModelsTest.kt`；外層新增 `.gitignore`、`validation/.gitignore`、`tasks/task1.md`，同步計畫與入口。

APK：`android/validation/builds/task1/HouseLens-probe.apk`（0.1.0／versionCode1）。只顯示版本與「尚未開始驗證」。簽章已用 apksigner verify 驗證，SHA256及工具鏈在同目錄 `build-info.json`。APK／logs／build-info均忽略，不入Git。

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

## Task6 最新結果與下一步

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
