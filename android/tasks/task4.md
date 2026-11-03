# Task 4 執行紀錄

計畫：android/plans/2026-11-03-probe-apk-implementation.md Task4；Base ffb5a92；分支 android/probe-bootstrap。主代理實作，代理僅唯讀研究／review。

## 範圍與裁決

- 只新增採集介面、HTTP／WebView候選、橋接資產與測試；UI／Runner／來源解析留後續Task。沒有既有transport呼叫端，模型及Store白名單不需改動。
- 沿用指定android目錄與既有任務紀錄，不另建worktree或根目錄ledger。保留所有原有未追蹤檔。
- Ruling: 七站主機與圖片CDN使用現有後端明列清單，HTTPS且精確host比對；未知CDN保守拒絕，須有實際來源證據才補入。
- Ruling: Accept-Encoding使用identity，避免宣告不會解壓的格式。HTTP與WebView共用RequestPacer，後續Runner必須注入同一實例，應用程式主動請求最少3秒間距；網站內部子資源由WebView管理。
- Ruling: robots只支援既有User-agent *／HouseLens與Disallow前綴規則，內容依origin快取、每path重算；未知／無法解析不得當允許。這不是完整robots協定實作。
- Ruling: WebView本機自檢入口留Task13，Task4提供資產及AssetLoader候選；未在真機執行不勾選實際驗證。

## 進度

Task 4: complete。實作、兩位獨立唯讀review及最後完整驗證已完成。未開始Task5；依逐Task停點等待使用者繼續。

## 實作與接續契約

- `PageTransport.kt` 提供PageTransport／SourceAdapter、精確HTTPS／host白名單、capture guard／訊息解碼、共用RequestPacer與固定TransportException(reason,status)。不傳遞原始例外中的URL或敏感本文。
- `HttpPageTransport(userAgent, pacer)` 使用實際WebViewSession.userAgent；Runner不可塞Windows Chrome假UA。可替換IO dispatcher／連線工廠僅為離線測試，預設HttpsURLConnection且保留平台TLS驗證。沒有全域CookieHandler、授權headers或Supabase設定。
- connect15秒，整次嘗試（含redirect及限速）30秒、最多3次網路／5xx嘗試；2／4秒退避與最少3秒＋jitter間距同時成立。429、取消、逾時、過大／不支援回應不自動重試。取消會disconnect當前連線。redirect最多5次，每跳重新驗host，POST body不可跨origin轉送。
- HTTP body與圖片8MiB、橋接每訊息2MiB且每頁累計8MiB。信義圖片獨有Referer；圖片不自動批次下載，Task13每列表最多一張縮圖，decode仍須尺寸限制。
- robots快取本文，每個path/query重新判斷；合併連續User-agent群組。Allow／crawl-delay／wildcard等未支援語法回未知，明確匹配Disallow仍拒絕；404／無法取得回null，429仍向外回限流。Runner對兩種transport都先checkRobots，未知必須記錄，不當已允許證據。
- WebViewSession使用application context；建立、userAgent、attach/detach/clearSession/destroy/cancel於main thread，capture自動切Main.immediate。HTTP與WebView必須共用同一pacer，同時只保留一個session，開始新run呼叫clearSession清資源cache及history、不清登入cookie。
- `clearSession` 不銷毀session；renderer退出則這個實例永久不可重用，回RENDERER_GONE後由Runner停止本run，再另建實例。等待限速、導航及DOM callback各階段都避免操作已銷毀WebView；等待中的evaluate會收到實際失敗原因。
- 每次導航移除前一document-start腳本並帶不可變navigationId；sourceOrigin／主frame／當頁URL／ID／status／JSON MIME／大小逐層驗證。redirect先更新受信任文件URL；不同URL舊錯誤不得污染新頁。WebView原生錯誤回呼沒有navigationId，同URL重載的晚到錯誤無法完全辨認，可能保守失敗，不能當成功。
- 導航30秒＋DOM總窗口最多5秒；DOM ready並至少兩次250ms觀察穩定才作解析候選，搜尋是否完成仍由來源parser判斷。缺BFF可保留DOM候選，但captureJson的networkObserved為false；不能把這個HTML備援當BFF等價。
- Ruling: DOCUMENT_START_SCRIPT缺失時回UNSUPPORTED，避免在onPageFinished後注入新ID把舊DOM誤標當頁；只有message listener缺失時仍可做DOM capture，JSON capture明確UNSUPPORTED。
- JS保留原fetch回應，讀clone；XHR支援text及json。XHR responseType=json經瀏覽器先解析後再序列化，原文字格式／超過JS安全精度的數值不能還原，來源parser不得宣稱其原始數字文字已保真。
- 591橋接目前僅允許sale.591.com.tw及路徑段search／house／sale／list，這是候選白名單，並非已查得實際BFF endpoint。Task5／真機若有其他host或路徑，取得證據後才擴充，不改回substring信任整個URL。
- 網路證據需同一URL、本次fetch/XHR起始時間以後的ResourceTiming，DOM只認navigation timing；排除worker、缺timing與headers-only驗證cache。使用encodedBodySize＋300的transferSize關係，無法判定時false。這只是候選可觀察證據，仍需真機比對。
- 本機selfCheck=true才啟用WebViewAssetLoader，非本機origin及未知asset路徑本地拒絕，不fall through到外網。selfcheck.html含fetch／XHR、完整iframe假訊息、真正延遲讀取的local response；下一次導航可帶previousNavigationId以重放舊ID。Task13補UI與結果斷言；selfcheck不計LIVE。

## Review／修正與測試紀錄

- 原始RED `red.log`：CaptureGuard／HttpPolicy待建API缺失。首次log目錄路徑錯誤不算RED，已修正並清理誤建空目錄。
- 第一輪GREEN `green.log`：guard與HTTP測試通過；預設Kotlin daemon有既有沙箱temp警告，內建fallback成功。後續僅以命令列 `'-Pkotlin.compiler.execution.strategy=in-process'` 使用編譯器，不變更工程或全域設定。一次未加引號造成PowerShell拆參數的失敗不算RED。
- 訊息解碼RED `bridge-policy-red.log`：缺CaptureMessage／decodeCapture；實作後完整build `full.log`成功。
- Review RED `review-red.log`：兩次20秒redirect超過總30秒、robots多agent群組誤判允許；2項測試實際失敗，再修正。reviewer另以既有compiled classes＋假連線重現35.4秒redirect成功，未連實站。
- JS RED `bridge-red.log`缺資產；`timing-red.log`重現舊timing被新回應採信；`xhr-red.log`重現XHR responseType=json漏抓。修正後Node VM直接執行正式資產，另驗fetch原response不被替換、immutable導航ID、惡意host拒絕、訊息上限、fresh timing正例與service worker否例。
- 原生WebView審查修正：redirect早期JSON、不同URL晚到錯誤、renderer等待evaluation及限速階段、銷毀後finally呼叫、DOM整體5秒窗口；補實際feature guard消除新lint警告。這些原生callback時序尚無裝置執行證據。
- 沒有新增依賴、改SDK／manifest／UI／模型／Store／後端。Task1留存APK未覆蓋。

## 待實際裝置執行（按計畫留Task13–14）

- [ ] WebViewAssetLoader自檢頁在WebView內實跑，包括iframe和跨導航晚到回應；Node VM不能代替此結果。
- [ ] 真實網站的BFF host/path、UA、redirect、ResourceTiming與renderer／SSL callback行為。
- [ ] S25+前景／背景／耗電及圖片decode尺寸限制接線。

查證：[AndroidX WebViewCompat](https://developer.android.com/reference/androidx/webkit/WebViewCompat)、[WebViewClient](https://developer.android.com/reference/android/webkit/WebViewClient)、[HttpURLConnection](https://developer.android.com/reference/java/net/HttpURLConnection)、[Resource Timing](https://www.w3.org/TR/resource-timing/)。API存在不等於實站方法已成功。Context實際百分比不可見。

## 最終驗證

- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --console plain --no-daemon '-Pkotlin.compiler.execution.strategy=in-process'` → exit0／BUILD SUCCESSFUL，52 tests／0 failures／0 errors，lint0 errors／5個鎖版新版提示，APK成功建置。見 `validation/builds/task4/verified.log`／`build-info.json`。
- `node app/src/test/js/probe-bridge.test.cjs` → exit0，見 `bridge-final.log`。直接執行正式JS，不以文字grep冒充行為驗證。
- 原有39項加CaptureGuard3項及HttpPolicy10項；沒有未結束指令或必修review遺留。原生WebView callback及手機案例仍依上方清單待驗證。
