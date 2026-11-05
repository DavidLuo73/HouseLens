# Task 10 執行紀錄

計畫：android/plans/2026-11-03-probe-apk-implementation.md Task10；Base 73b9d9d；分支 android/probe-bootstrap。主代理實作，代理僅唯讀研究／review。

## 範圍與裁決

- 新增 CtHouseSource、來源測試與三份合成 JSON 樣本。沿用 HTTP transport、共用規則、Fixtures 與 Store；呼叫端目前只有測試，Runner／UI 留 Task12–13。
- Ruling: 沿用固定 android/ 路徑與現有功能分支，任務紀錄使用本檔，不另建 worktree 或根目錄工作區。保留既有 staged handoff/2.md 刪除與未追蹤文件。
- Ruling: 舊 C# 測試只有 houses item，envelope 是合成；discounted 地址改為支援的新北中和，現價保留 798.00。所有測試為 SIMULATED，不能當實站成功。
- Ruling: totalpage 必須是非負 JSON 整數；非空頁須 1-based 頁碼不超過 totalpage；零筆只接受第一頁、totalpage=0 或 1、houses=[]。空中間／末頁視為不一致，等待實站證據修訂。
- Ruling: 原始 keys 先於篩選及去重；重複 ID 相同資料只留一筆，衝突資料保守 INVALID。缺 ID／現價或錯型整頁 INVALID，不因非住宅或重複而忽略。
- Ruling: 代碼沿後端映射，但未知代碼拒絕；價格／坪數保留小數。查詢面積基準仍未知，不用總建坪本地排除 minSizePing。用途／型態／車位缺欄位不假設已知。

## 進度

Task 10: complete。實作、獨立 review 與修正、最終完整驗證完成；Task11 尚未開始，依使用者逐 Task 停點等待繼續。

## 驗證與審查

- RED：來源測試 exit1，缺 CtHouseSource API，符合預期；見 validation/builds/task10/red.log。
- GREEN：來源測試 exit0／BUILD SUCCESSFUL，14項通過；見 green.log。新測試的 ExperimentalCoroutinesApi opt-in 警告已補 annotation。
- Gradle 沿用 README 局部 JAVA_HOME／GRADLE_USER_HOME／ANDROID_USER_HOME，命令使用 --offline --console plain --no-daemon 與 -Pkotlin.compiler.execution.strategy=in-process。
- 獨立研究已核對舊 C# 測試與計畫衝突：降價 fixture 的桃園地址、過濾後零筆提前停止、跨區測試只解析未排除。Android 已依前述裁決處理。
- 獨立 review 確認需補 HTML 人機驗證分支：captureJson transport 會把 HTML body 放進 RawCapture.json；現行 parser 直接 INVALID，無法導向 WAIT_FOR_USER。將以回歸 RED→GREEN 補完；合法 RS!=OK JSON 仍須 INVALID，不能被 JSON 字串中的驗證文案影響。
- POST 的 arg/page 不存在回應 URL，parser 只核對精確 endpoint；請求／回應的頁次關聯由 transport 當次呼叫及 Runner 的 navigationId 維持，不能單靠 URL 證明。Runner 留 Task12。
- 來源日期只保留可解析的 ISO 日期／時間字串；無日期或不支援格式保留 postedDate 未知。車位只有明確0/1可判定，平面／機械類型仍未知。

## Review 修正

- 首輪完整驗證 full.log：exit0／BUILD SUCCESSFUL，151 tests／0 failures／0 errors，lint0 errors／5個原有鎖版新版提示，APK建置成功。
- 獨立 reviewer 另發現 RS 使用 display-text helper 會 trim，把 `" OK "` 誤當成功。兩項回歸在 review-red.log 重現：16項來源測試中2項失敗（HTML challenge／RS精確狀態）。
- 主代理修正：僅以 `<` 開頭的 HTML body 辨識既有 challenge selector／文字；正常 JSON 直接比較 `JsonPrimitive("OK")`。錯誤 JSON 內的驗證文案不影響 INVALID，合法物件標題也不能偽裝 challenge。兩處修正均無新依賴、共用程式或 transport 修改。
- Reviewer 再核對兩處修正及負向測試，確認原問題已解決，沒有必修遺留。

## 最終驗證與停點

- 最終命令（repo root）：`./android/probe/gradlew.bat -p android/probe :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --console plain --no-daemon '-Pkotlin.compiler.execution.strategy=in-process'` → exit0／BUILD SUCCESSFUL，**153 tests／0 failures／0 errors，lint0 errors／5個既有鎖版新版提示**。其中16項中信來源測試、既有137項全通過；見 validation/builds/task10/verified.log 與 build-info.json。
- POST transport 整合測試僅替換連線邊界，驗證 Content-Type／body／navigationId／RawCapture.json，並走實際 Store save/load/export；現價 798.00 精度保留，evidence／unknownFields 全與現有白名單相容。
- 無依賴、SDK、manifest、共用規則／Store／transport、UI、後端或正式資料變更；Task1留存APK未覆蓋。此次樣本皆為合成，空頁totalpage慣例、小數條件、實際欄位與網站現況仍需實站核對。
- 未跑實站、WebView、adb／手機或背景；未重跑未修改的Node橋接資產。HTTP非2xx由transport回HTTP_ERROR；本次challenge分支涵蓋可交到parser的成功HTTP HTML，不宣稱403等本文可取得。
- 全部命令已結束，沒有執行中工作或阻礙；context實際百分比不可見。下一步 Task11 台灣房屋：先讀 TwHouseScraper 與測試，確認建坪／現價、合法空標記、全過濾頁，以及列表缺地址／樓層／車位必須保留未知；不自行加逐屋詳情請求。
