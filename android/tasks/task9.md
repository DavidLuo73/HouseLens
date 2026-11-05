# Task 9 執行紀錄

計畫：android/plans/2026-11-03-probe-apk-implementation.md Task9；Base 01c6f09；分支 android/probe-bootstrap。主代理實作，代理僅唯讀研究／review。

## 範圍與裁決

- 新增 SinyiSource、SinyiSourceTest 與 search.html／next-data-only.html／video-first.html 三份合成樣本。呼叫端目前僅測試，Runner／UI 留 Task12–13；沿用 Fixtures、ProbeRules、Store、HTTP transport，不新增依賴。
- Ruling: 延續使用者指定的 android/ 固定路徑、既有功能分支與逐 Task 停點；任務與驗證紀錄放 android/tasks 和 validation，不建立其他 worktree 或根目錄技能工作區。
- Ruling: 後端只有 URL 測試，沒有 parser fixture；依既有 buy-list-item／LongInfoCard_Type_* 語意建立合成卡。只處理搜尋卡，不從全站 __NEXT_DATA__ 擷取物件；fixture networkObserved=false，不是 LIVE。
- Ruling: rawKeys 保留每張原始卡（含重複 ID），listings／excluded 才去重。每卡先驗 ID／價格，再過濾；20張 MORE、1–19張 END／last_page、超過20張 UNKNOWN，0卡沒有可靠空標記故 INVALID。短頁末頁證據沿共同規則優先於重複檢查。
- Ruling: URL 映射與順序沿後端，拒絕未知代碼；金額／坪數保留小數，不無聲取整。房數取最小值，頁限5。未知型態不沿後端回退全住宅，必須拒絕。
- Ruling: 現有後端沒有已確認的卡片型態／用途／車位 selector；保留 typeCodes／useCode／hasParking 未知，不以標題或 URL 宣稱已核實。精確房數／車位類型及坪數基準亦保持未知。地址明確跨區時照實排除，缺城市／區才從 query 補值並標未知。
- Ruling: 排除標題／地址的數字後解析金額；沒有可靠現價 selector 時，多個不同金額保守 INVALID，不取第一個可能為原價的值。這會拒絕部分含原價／車位價的合法卡，需新鮮網頁證據才能縮小 selector。
- Ruling: 圖片解碼後驗 HTTPS 精確 host，影片依 URI path 排除（含帶 query 的影片），支援 data-src。無可用圖片沿用 res.sinyi.com.tw/buy/{id}/smallimg/A.JPG fallback，並標 imageUrl 未知。圖片失敗由現有 loadImage 個別拋固定 reason/status，列表仍可保存／匯出；UI 顯示留 Task13。

## 進度

Task 9: complete。實作、獨立 review 與修正、最終完整驗證已完成；尚未開始 Task10，依使用者逐Task停點等待繼續。

## 驗證

- RED：來源測試命令 exit1，缺少 SinyiSource API（預期）；見 validation/builds/task9/red.log。
- 使用 README 的局部 JAVA_HOME／GRADLE_USER_HOME／ANDROID_USER_HOME；所有 Gradle 命令使用 --offline --console plain --no-daemon 與 -Pkotlin.compiler.execution.strategy=in-process。
- 未執行實站、手機／WebView、adb 或背景驗證；尚無合法零筆、短頁及小數查詢的實站證據。Task1留存APK未覆蓋。

## 審查

- 獨立 reviewer 已核對 URL 關聯、原始卡計數與去重、壞卡處理、圖片白名單、Store 相容，發現價格 token 截尾（Important）與樓層前綴截尾（Minor）。
- 價格回歸案例：1, 750萬／7.5e2萬／−750萬，以及主代理延伸的分數／混合全形數字，均應 INVALID／parse_error。
- 樓層回歸案例：B1樓/11樓、地下1樓/11樓、1~3樓/11樓。尚未支援的格式保留 null＋floor 未知，不能改成地上單一樓層。
- 首次完整驗證 full.log：exit0，135 tests／0 failures／0 errors，lint0 errors／5個既有新版提示，APK建置成功。review修正後需再跑最終完整驗證。
- 回歸 RED：review-red.log，17項來源測試中2項依預期失敗（價格與樓層截尾）。修正擴大金額候選 token 後驗完整格式、樓層前後增加邊界；未增加依賴或修改共用 transport／rules。

## 最終驗證與停點

- 最終命令（repo root）：`./android/probe/gradlew.bat -p android/probe :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --console plain --no-daemon '-Pkotlin.compiler.execution.strategy=in-process'` → exit0／BUILD SUCCESSFUL，**137 tests／0 failures／0 errors，lint0 errors／5個既有鎖版新版提示**。其中17項信義來源測試，既有120項全數通過；見 `validation/builds/task9/verified.log`、`build-info.json`。
- 獨立 reviewer 再核對兩處修正與回歸案例後確認原問題解決，沒有必修遺留。圖片失敗隔離測試走實際 HttpPageTransport／Store，僅連線邊界替換為本機回應；檢查 Referer 只限信義圖片，無 Authorization／apikey／Cookie，失敗後列表仍可 load/export。
- 本輪變更僅限 adapter、測試、三份樣本及文件；無依賴、SDK、manifest、共用 rules／Store／transport、UI、後端或正式資料變更。未重跑未修改的Node橋接；未跑實站、WebView、adb／手機或背景。
- 全部命令已結束，無執行中工作或阻礙；context實際百分比不可見。下一步 Task10 中信 JSON envelope／現價，先讀 CtHouseScraper 及測試，重點為 RS／houses／totalpage 驗證、sell_price、跨區排除與全過濾頁仍翻頁。
