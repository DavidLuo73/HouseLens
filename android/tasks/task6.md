# Task 6 執行紀錄

計畫：android/plans/2026-11-03-probe-apk-implementation.md Task6；Base 4a5128a；分支 android/probe-bootstrap。主代理實作，代理僅唯讀研究／review。

## 範圍與裁決

- 新增 RakuyaSource、RakuyaSourceTest、三份從 C# 測試移入的合成 HTML 樣本。呼叫端目前只有測試；Runner／UI 留 Task12–13。沿用既有 transport、Store、共同規則與 Fixtures，不新增依賴。
- Ruling: 沿用 android/tasks 與 validation 記錄、既有功能分支，不建立技能預設根目錄工作區，遵循固定 Android 路徑與逐 Task 停點。
- Ruling: 沒有可靠下一頁／空狀態 selector；有卡一律 UNKNOWN、無卡 INVALID。不以裸「共 N 筆」、過濾後筆數或缺 next link 宣告 END；代價是實站完整性仍待取證，重複末頁會 INCONCLUSIVE、上限會 TRUNCATE。
- Ruling: 房源仍沿用住宅白名單，R1–R6 僅作來源端查詢，typeCodes/useCode 未逐筆核實。用途僅接受住宅1／空預設，其他用途拒絕，避免與住宅排除規則矛盾。計畫要求預設 R1,R2 的透天樣本仍可解析，因此不新增精確型態排除。
- Ruling: 價格／最小坪數保留精確小數，避免舊格式取整改變查詢；網站接受程度需真機驗證。
- Ruling: 主建有效數字優先，否則總建；屋齡及計算單價採 HALF_EVEN。來源 size 的面積基準未證明，不拿混用主建／總建的 areaPing 本地排除，保留 areaBasis 未知。
- Ruling: 缺車位標籤不是已證明無車位，保留 hasParking=false 加 unknownFields；房數／用途／精確車位類型保守標未知。缺失地區或城市若以查詢補值須標未知。
- Ruling: 原始卡先驗 ID／正價格再套篩選，壞卡令整頁 INVALID；去重不依賴過濾結果。當頁文件網址需精確 host/path 及相同查詢／頁碼，禁止把舊頁或其他查詢套進目前頁。

## 進度

Task 6: complete。實作、獨立 review 及兩項必修修正、完整驗證已完成；Task7未開始，依使用者逐Task停點等待繼續。

## 獨立 review

- 地區「新北市 板橋區」移除城市後未 trim，錯補 query 行政區。`review-red.log` 已重現1項失敗；補 trim，回歸含普通／全形／NBSP 空白。
- 缺少型態 li 時，房數／屋齡／樓層變成首項，被誤記 non_residential。新增回歸；只將已辨認的房數／年完整格式與 info__floor 元素視為型態缺值，仍保留 typeCodes 未知。
- Ruling: 同 ID 的有效卡仍沿舊 HTML 行為取首筆；未增加衝突合併機制。後續同 ID 卡仍先驗證 ID／價格，壞資料不能被去重掩蓋。風險為同 ID 異價無法裁決新舊，實站若出現需另取證調整。
- Reviewer 為唯讀程式追蹤；其 JShell 重現未成功載入 classpath，不能當測試通過。可執行 RED／GREEN 由主代理 JUnit 驗證。

## 驗證

- RED：`:app:testDebugUnitTest --tests '*RakuyaSourceTest' --offline --console plain --no-daemon '-Pkotlin.compiler.execution.strategy=in-process'` → exit1，缺 RakuyaSource（預期）；見 `validation/builds/task6/red.log`。
- 初次 GREEN `green.log`：13項來源測試通過；`full.log` 完整測試／lint／APK建置成功。
- `review-red.log`：地區空白1項失敗；修正後 `type-red.log` 的15項測試只剩缺型態1項失敗，地區3類空白通過。
- 最終命令（repo root）：`./android/probe/gradlew.bat -p android/probe :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --console plain --no-daemon '-Pkotlin.compiler.execution.strategy=in-process'` → **exit0／BUILD SUCCESSFUL，87 tests／0 failures／0 errors，lint0 errors／5個既有鎖版新版提示**。來源15項、既有72項全部通過；見 `validation/builds/task6/verified.log`、`build-info.json`（APK／來源hash及實際計數）。無必修遺留。
- 使用 README 的局部 JAVA_HOME／GRADLE_USER_HOME／ANDROID_USER_HOME；無新依賴、SDK、manifest、Store、transport、UI、後端或正式資料變更。
- 所有 fixture `networkObserved=false`；Store 整合使用 SIMULATED，測試不算 LIVE。Task1留存APK未覆蓋，當前建置仍僅有版本與未開始畫面。
- 未重跑未修改的 Node 橋接資產；未跑實站、WebView、adb／手機或背景測試。實站查詢小數、頁面完整性／空結果與session仍待Task13–14取證。
- 全部命令已結束，無阻礙；context實際百分比不可見。下一步Task7永慶，不能重做Task1–6。
