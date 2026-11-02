# Task 3 執行紀錄

計畫：android/plans/2026-11-03-probe-apk-implementation.md Task3；Base 8a8a54c；分支 android/probe-bootstrap。Native 主代理實作，所有程式與紀錄在 android 下。

## 範圍與介面裁決

- 新增Store／Report及測試；必要模型補充事件enum、newRawCount、事件完整性旗標。沒有既有RunRecord/ProbeEvent呼叫端，後續Runner/來源須遵循此契約。
- Ruling: 最小新增loadPage(runId,index)，讓Runner依完整頁重建seen/唯一筆數；不另保存可失同步的計數檔。
- 每頁原子提交後才產生PAGE_SAVED，newRawCount由Store對先前完整頁的rawKeys計算；同頁相同內容重送不再計數，不允許以不同內容覆蓋。
- 終止狀態與終止事件放同一個原子terminal.json；finish使用可注入UTC/單調時鐘。頁檔成功但PAGE_SAVED缺失只能恢復資料，不能補造當時背景時間。
- kind/reason改enum；excluded/evidence/unknownFields採固定白名單，輸出不使用任意網頁文字。金額以十進位字串保存，保持798.00精度。
- Ruling: B1/B2使用第一段連續背景/螢幕狀態符合的區間，至少600秒；三個請求窗口為[60,300)、[300,540)、[540,600)秒，四階段須於600秒內完成。允許排程延遲但不能用同頁或沒有新raw key充數。FULL另須RUN_COMPLETED，至少一個背景內新頁。
- 跨run/process/site、非LIVE、逆序時間、缺初態／證據、半行事件均不能PASSED；背景回應於回前景後才解析／落盤為FAILED。人工驗證、取消／失敗不能重變成功。
- 事件尾端缺newline視為遺失，load註記且加入EVIDENCE_INCOMPLETE供判讀；中間或正式頁JSON損毀明確報錯。寫入失敗向外拋出，該Store不得接著把run完成為成功；Runner停止／UI提示留Task12/13。
- Store單例在單程序使用，同步其公開操作；不承諾跨程序寫入或突然斷電的檔案系統耐久性。原子rename若不支援就失敗，不降級成非原子覆寫。

## 目前狀態

已完成：實作、紅綠測試、獨立review及修正，完整驗證通過。Task4未開始；context百分比不可見，更新handoff/2.md後停下等待繼續。

## 已完成的獨立 review 與修正

Store/JSON及Report分由兩位唯讀reviewer檢查，未委派實作。下列缺口都先以測試重現再修正：

- 背景request之後回前景才收到response／parse／save，仍必須FAILED；不再要求response先位於背景區間。RESUMED本身也代表人工介入，不能再PASS。
- terminal提交失敗時，即使已有600秒取證，export仍須拒絕成功輸出；成功保存FAILED終止紀錄後才可匯出該失敗狀態。
- nullable JSON錯型不得吞成null，正式資料損毀須報錯；UTC錯誤統一為輸入驗證例外。
- 半行事件尾端不准繼續savePage／append，避免讓後續事件黏到破損行；匯出補入已知query與device欄位。
- load改為每頁只讀取／解析一次，復用pages map與newRawCount；沒有增加跨呼叫cache。未做真機效能benchmark。
- 補頁已原子提交但事件尚未提交的失敗窗口測試；重載保留頁，不補造PAGE_SAVED時間。另測600秒後才存檔、不能以第二段長區間取代第一段失敗觀察。

額外自查：不同pageIndex重用navigationId也不能充當獨立取證；已用navigation-red.log確認舊程式失敗，修正後納入完整GREEN。

## 後續接線契約

- App使用 `ProbeStore(File(filesDir, "runs"), processId)` 單例，於IO dispatcher呼叫；processId每程序新UUID。預設時鐘用Instant與Android elapsedRealtime，測試注入時鐘／commit回呼，不引入repository框架。
- Record immutable metadata、page-N.json、events.jsonl、terminal.json分工：只有完整page檔算checkpoint；同頁同內容重送不加倍、不同內容拒絕覆寫。loadPage供重建seen／唯一房源數。
- Runner按同pageIndex/navigationId發REQUEST_STARTED、RESPONSE_RECEIVED、PAGE_PARSED，接著savePage；Store在成功落盤後產PAGE_SAVED及實算newRawCount。不要自己append PAGE_SAVED或RUN_*終止事件。
- finish只接終止state，使用注入時鐘把終止event原子寫入terminal.json。具體NETWORK_ERROR/TIMEOUT停止原因由Task12接線時透過可選reason參數擴充finish一併保存；目前reason enum與事件序列化已支援，不能另寫競爭的終止事件。
- PageResult evidence／excluded／unknownFields的白名單在ProbeStore.kt，來源有新固定代碼時須明確加表及測試，不能接受任意網頁文字。raw/source ID限1–80個英數、底線、連字號，符合現有fixture。
- ProbeJson.kt為手寫JsonElement編解碼及匯出欄位，不用serialization compiler plugin或反射。數值以十進位字串保存；匯出僅result.md/events.jsonl/sample.json，不含原始HTML／BFF、title、address、完整URL、imageUrl。
- 檔案API依[Android Files.move](https://developer.android.com/reference/java/nio/file/Files#move(java.nio.file.Path,%20java.nio.file.Path,%20java.nio.file.CopyOption...))，JSON依[kotlinx JsonPrimitive](https://kotlinlang.org/api/kotlinx.serialization/kotlinx-serialization-json/kotlinx.serialization.json/-json-primitive/)並以鎖版1.9.0實際編譯驗證；僅本機JVM證據，不代替Android檔案系統及系統回收測試。

## 完成證據

- 原始RED：`:app:testDebugUnitTest --tests '*ProbeStoreTest' --tests '*ProbeReportTest' --offline --console plain --no-daemon` → exit1，缺API／事件enum，見 `validation/builds/task3/red.log`。
- Review回歸：`review-red.log`／`review-red2.log`重現nullable錯型、半行後續寫、缺匯出快照、terminal提交失敗、UTC例外、回前景後才response、RESUMED等失敗；`navigation-red.log`重現navigationId重用可誤判。
- 最終完整命令：`./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --console plain --no-daemon` → **exit0／BUILD SUCCESSFUL；39 tests，0 failures／errors／skipped；lint0 errors／8個原鎖版新版提示；APK建置成功**。完整紀錄 `validation/builds/task3/final.log`，build-info.json記工具鏈參照、原始碼commit及APK hash。
- 新增Store13項及Report10項測試，連同前兩Task的16項全部通過。未加依賴、修改SDK或全域設定。
- 本機規則／檔案測試不證明實站、真機背景、耗電或Android實際檔案系統行為；未測跨程序多寫者或突然斷電，沒有宣稱支援。Task1交付APK不覆蓋；目前UI尚未接入Store／Report。

Task 3: complete。無執行中的測試或必修review遺留，等使用者「繼續」才進Task4。
