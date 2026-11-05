# Task 11 執行紀錄

計畫：android/plans/2026-11-03-probe-apk-implementation.md Task11；Base 6bf11e7；分支 android/probe-bootstrap。主代理實作，代理僅唯讀研究／review。

## 範圍與裁決

- 新增 TwHouseSource、來源測試與三份合成 HTML 樣本；沿用 SourceAdapter、ProbeRules、Fixtures、HTTP transport、Store。呼叫端目前只有測試，Runner／UI 留 Task12–13。
- Ruling: 沿用固定 android/ 路徑與既有功能分支，任務紀錄使用本檔，不另建 worktree 或根目錄工作區。保留既有未追蹤文件。
- Ruling: 樣本取自既有 C# 單元測試，包裝頁及空頁為合成；SIMULATED／networkObserved=false，不能當作當前網站或手機證據。
- Ruling: 型態與房數／車位沿既有 URL 映射；未知代碼拒絕，金額與坪數保留小數。列表缺完整地址／樓層／車位一律未知，不接逐屋詳情。
- Ruling: 原始卡先驗 ID／正現價，再過濾及去重；壞卡整頁 INVALID。相同 ID 相同資料只留一筆，衝突資料 INVALID；rawKeys 保留原始重複卡。非空頁 UNKNOWN，只有合法空標記 END；全過濾頁繼續、重複頁 INCONCLUSIVE、30頁上限 TRUNCATE。
- Ruling: 使用建坪與 fullBuildingPing 查詢段，坪數不改成地坪；沿既有 adapter 保守保留 areaBasis 未核實，不新增本地 minSizePing 排除，留實站核對。房數／精確型態／用途／車位型式也不由搜尋 URL 假裝逐筆確認。

## 進度

Task 11: complete。實作、獨立 review、必要修正與最終完整驗證完成；Task12尚未開始，依使用者逐Task停點等待繼續。

## 驗證與審查

- red.log：缺 TwHouseSource API，exit1，符合原始 RED 預期。
- green.log：16項中1項失敗，地區比對未涵蓋全形空白，跨區物件未排除；已將地區空白比對納入 Unicode Zs，再跑完整驗證。
- 獨立唯讀研究核對 C# URL／fixture／未接入的 ParseDetail，確認不能沿用過濾後零筆停止、全頁 marker 子字串及未知車位 false 的判定。
- 獨立 review 提出五項可重現問題：地址「萬」誤當價格、缺現價退回舊價、同ID的住宅判斷衝突、非房源 h2/h3 導覽誤當卡片，以及缺行政區時覆寫已知城市。先補回歸 RED，再修正。
- full.log：全形空白修正後完整 test／lint／assemble exit0／BUILD SUCCESSFUL；原16項來源測試通過。
- review-red.log：新增5項回歸後，21項中5項依預期失敗，分別重現上述審查問題。
- 主代理修正：現價定位末端欄位並驗完整數字，允許數字與「萬」拆標籤；末端空白／未知／格式錯誤不回退舊價。地址「萬壽路」不再進入價格解析；最終版本改為下述完整容器驗證。
- 主代理修正：住宅排除理由在去重前算，連同完整 listing 比對衝突；非房源標題導覽排除，但有房源內容的壞連結仍報 INVALID；城市與行政區分開讀取，不因缺區覆寫已知城市。
- Ruling: 既有樣本沒有共用價格 class，以末端欄位定位現價；若實站增加末尾 badge／欄位，保守 INVALID，需有實站證據後調整 selector。已在程式加 shortcut，不能宣稱任意版型都支援。
- verified.log：五項修正後完整 test／lint／assemble exit0，174項測試通過。複核再發現巢狀標籤可把父層負號略過，尚需修正；此檔保留該輪結果，最終結果另存 final.log。
- 價格修正裁決：直接驗證既有住宅 `.mt-auto` 價格容器或降價卡最後一個直接子欄位的完整文字，移除葉節點向祖先試找金額的推論。未知版型／多金額容器保守 INVALID，待實站補 selector。
- nested-price-red.log：巢狀負號回歸1項依預期失敗；最終22項來源測試包含負號／待更新文案／錯誤單位、拆分數字與單位，修正後最終結果見下方。

## 列表欄位矩陣

| 欄位 | 目前取得方式／限制 |
| --- | --- |
| ID／URL | 精確本站 HTTPS `/buy/{ID}`，安全英數 ID；不接受第三方連結 |
| 現價 | 既有價格容器或末端直接欄位的完整正金額，保留小數；壞現價不得退回原價 |
| 建坪／單價 | 只取「建坪」，不取地坪；單價 HALF_EVEN 兩位；缺值標未知 |
| 屋齡 | 列表年數，HALF_EVEN 整數；缺值標未知 |
| 城市／行政區 | 列表文字；無法取得才補 query 並標未知；已知跨區排除 |
| 標題／圖片 | h2 或 h3 去掉 ID 後綴；CSS 背景圖或 img，精確 CDN 白名單 |
| 完整地址／樓層／車位／刊登日期 | 未取得；null／false 只是模型佔位，unknownFields 明示 |
| 型態／用途／房數／車位型式／面積查詢基準 | 沿既有查詢映射，但未逐筆核實；不把 URL 當符合證據 |

完整地址／樓層／車位若成為完整需求的必要欄位，需另行評估詳情取得；本 Task 不增加逐屋請求。

## 最終驗證與停點

- 最終命令（repo root）：`./android/probe/gradlew.bat -p android/probe :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --console plain --no-daemon '-Pkotlin.compiler.execution.strategy=in-process'` → exit0／BUILD SUCCESSFUL，**175 tests／0 failures／0 errors，lint0 errors／5個既有鎖版新版提示**。其中22項台灣房屋來源測試、既有153項全通過；見 validation/builds/task11/final.log 與 build-info.json。
- 沿用 README 的局部 JAVA_HOME／GRADLE_USER_HOME／ANDROID_USER_HOME，無下載或依賴變更。build-info保存工具鏈參照、來源hash、當時未提交狀態及APK SHA256；Task1留存APK未覆蓋。
- Reviewer已核對最終六項修正，沒有必修遺留；完整價格欄位的負號／空白／待更新／錯誤單位均有回歸。最終未另擴充UI、Runner或詳情採集。
- Store save/load/export通過，未知欄位與evidence相容既有白名單。全部fixture為SIMULATED，networkObserved=false；未跑實站、WebView、adb／手機、背景或未修改的Node橋接。未知版型、圖片、空頁、CSS可見性、小數URL與條件語意仍待實站核對。
- 全部命令已結束，沒有執行中工作或阻礙；context實際百分比不可見。下一步Task12先寫Runner／Recovery紅測試並核對共用Store／Report／transport接線契約；本輪依約停下。
