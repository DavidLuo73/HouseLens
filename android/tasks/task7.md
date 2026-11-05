# Task 7 執行紀錄

計畫：android/plans/2026-11-03-probe-apk-implementation.md Task7；Base 424726e；分支 android/probe-bootstrap。主代理實作，代理僅唯讀研究／review。

## 範圍與裁決

- 新增 YungchingSource、YungchingSourceTest 與三份合成 HTML 樣本；呼叫端目前只有測試，Runner／UI 留 Task12–13。沿用 Fixtures、Store、transport 與共同規則，不新增依賴。
- Ruling: 沿用 android/tasks 與 validation 紀錄及既有分支，遵循使用者固定路徑和逐 Task 停點，不建立技能預設根目錄工作區。
- Ruling: 查詢與當頁 URL 先核對；CHALLENGE 優先於合法空標記；空標記優先於精選卡。未辨識空狀態無卡回 INVALID；非空頁沒有可靠末頁證據，保留 UNKNOWN，不以過濾後筆數或重複頁宣告 END。
- Ruling: 型態與車位沿現碼映射，未知代碼拒絕；住宅用途固定。價格與坪數保留小數；房數尚無來源 URL 映射，保留未知。實站支援程度待真機核對。
- Ruling: 地址解析城市／行政區，缺值以 query 補值並標未知。缺車位標記不證明無車位；房數、用途、精確車位類型及面積篩選基準保守標未知。
- Ruling: 圖片沿用來源縮圖尺寸與既有精確 CDN 白名單，jsoup 解碼 HTML 實體；不照搬後端放大到1200。非此驗證 APK 的必要行為。
- Ruling: HTML 同 ID 取首筆，仍先驗證每張卡必要欄位，壞重複卡不能被去重掩蓋；同 ID 異價的新舊裁決仍待實站證據。

## 獨立 review

- 唯讀 reviewer 找到兩項 Important：inline display:none／visibility:hidden 的空文字會誤判 EMPTY/END；`.room` 的「無 車位」被 split 成正向車位。主代理已核對呼叫端與控制流程，`review-red.log` 重現2項失敗；移除明確 inline 隱藏子樹並在車位判斷前擋負向文案，回歸通過。沒有必修遺留。
- 靜態 DOM 不能還原外部 CSS 的 computed visibility；空標記只採固定文字、排除卡片／腳本／模板與明確隱藏元素，實站 DOM 仍需 Task14 核對。

## 進度

Task 7: complete。實作、獨立 review 與必修修正、完整驗證已完成；Task8未開始，依使用者逐Task停點等待繼續。

## 驗證

- RED：`:app:testDebugUnitTest --tests '*YungchingSourceTest' --offline --console plain --no-daemon '-Pkotlin.compiler.execution.strategy=in-process'` → exit1，缺 YungchingSource（預期）；見 `validation/builds/task7/red.log`。
- 初次 GREEN `green.log`：14項來源測試通過；`full.log` 完整測試／lint／APK建置 exit0。
- `review-red.log`：16項來源測試中2項失敗，確認隱藏空標記與負向車位兩個問題；修正後完整驗證通過。
- 最終命令（repo root）：`./android/probe/gradlew.bat -p android/probe :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --console plain --no-daemon '-Pkotlin.compiler.execution.strategy=in-process'` → **exit0／BUILD SUCCESSFUL，103 tests／0 failures／0 errors，lint0 errors／5個既有鎖版新版提示**。來源16項、既有87項全部通過；見 `validation/builds/task7/verified.log`、`build-info.json`。
- 測試涵蓋原住宅／車位欄位、HALF_EVEN、型態／價齡／地區排除、URL／代碼／頁碼、20張全過濾後下一頁、重複頁不冒充完成、缺必要欄位、challenge優先、精選排除、未知欄位與Store save/load/export。
- 使用 README 的局部 JAVA_HOME／GRADLE_USER_HOME／ANDROID_USER_HOME，無新依賴、SDK、manifest、Store、transport、UI、後端或正式資料變更。
- 所有 fixture 均為合成且 networkObserved=false，Store測試明列SIMULATED。Task1留存APK未覆蓋，當前建置UI仍僅有版本與未開始畫面。
- 未重跑未修改的Node橋接；未跑實站、WebView、adb／手機或背景驗證。非空頁末頁證據、空標記的實際DOM及外部CSS可見性、小數查詢支援仍需實站取證。
- 全部命令已結束，無阻礙；context實際百分比不可見。下一步Task8住商原始卡片計數，先讀HBHousingScraper及測試，重點是10張全過濾仍MORE、1–9張正確原始卡的末頁證據與0卡合法空標記。
