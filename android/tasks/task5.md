# Task 5 執行紀錄

計畫：android/plans/2026-11-03-probe-apk-implementation.md Task5；Base eef027c；分支 android/probe-bootstrap。主代理實作，代理僅唯讀研究／review。

## 範圍與裁決

- 新增 F591Source、Fixtures、F591SourceTest 與四份合成樣本；ProbeStore 僅擴充固定 evidence／unknownFields 白名單。呼叫端為新測試，Runner／UI 尚未建立，留 Task12–13。無依賴、SDK、後端或資料庫變更。
- Ruling: 沿用既有 android/tasks 與 validation 位置記錄任務，不使用技能預設根目錄 ledger／新 worktree，符合使用者固定 android 目錄與 Native 逐 Task 要求。
- Ruling: 沒有可靠實站 BFF 樣本，沿用 Task4 精確 HTTPS host 與候選 path 白名單，測試的 /test/list 只是合成 URL；不新增實站 endpoint 猜測。缺地區／offset、衝突 body 均 INVALID，不能合併不同回應的 total 與列表。
- Ruling: 搜尋第一頁沿用後端省略 firstRow；BFF 每頁仍必須明確帶 firstRow（含 0）。真機若省略或改成 POST body，先回方法未確認，再依實際證據改關聯方式。
- Ruling: 最高價／最低坪數保留 BigDecimal 小數，不照後端取整數或兩位造成條件改變。網站是否接受精確小數留真機確認。
- Ruling: 既有 591 沒有 typeCodes/useCode 映射，僅容許原預設 R1,R2／1 或空值，並將有設定但不能核對的 typeCodes/useCode/rooms 記 unknownFields；areaBasis 永遠未確認，不用 areaPing 做本地 minSizePing 排除。其他代碼明確拒絕，避免默默變成不限。
- Ruling: HTML 僅候選卡片，evidence=html_fallback、next=UNKNOWN，不宣告 BFF 等價或完整。無卡或壞 ID／價格 INVALID；卡片缺車位不以文字「車位」推論有／無。
- Ruling: 既有 source 欄位別名與七個列表／三個 total 路徑沿用；可選資料缺失保留未知，必填 ID／價格失敗整頁 INVALID。缺 total 的非空列表 UNKNOWN，total=0 優先排除推薦。數字 ID 優先於 UUID，不造 ID。
- Ruling: total 已知時原始卡片數須等於 min(30,total-offset)。將 page1 合成樣本補滿30張，保留規格指定的第一筆100001／750.00／28.50與total31；不能因計畫簡寫只列一筆而接受不完整頁。若真站頁大小不同會保守 INVALID，須有實際證據才調整。
- Ruling: BFF 若帶已知 type/price/houseage/parking/area/pattern，必須與當頁查詢一致；缺少這些可選參數時仍以地區／offset關聯及欄位未知註記為界。尚未證明回應完整反映全部來源端限制。

## 獨立 review

唯讀 reviewer 找到4項必修：短頁仍END、不同顯式篩選可供total0、boolean ID、車位類型未標未知。另主代理發現document path沒有核對與total千分位格式過寬。均已加入回歸並修正，完整驗證通過；沒有必修遺留。

- `boundary-red.log`：17項中2項失敗，重現錯誤document path與boolean ID。
- `review-red.log`：19項中4項失敗，另重現短頁與不同顯式篩選回應被採納。
- `parking-red.log`：指定測試1項失敗，重現有車位卻漏標parkingCodes未知。
- ID只接受字串或整數token；total使用嚴格千分位格式與精確整數轉換；當前document要求根搜尋路徑與query一致。頁大小及已出現的filter值均核對。車位類型以parkingCodes未知標記，不猜BFF enum。

## 進度

Task 5: complete。實作、獨立 review 與全部必修修正、完整驗證已完成；Task6未開始，依使用者逐Task停點等待繼續。

## 驗證

- RED：`:app:testDebugUnitTest --tests '*F591SourceTest' --offline --console plain --no-daemon '-Pkotlin.compiler.execution.strategy=in-process'` → exit1，缺 F591Source API（預期）；見 validation/builds/task5/red.log。
- 所有 fixture networkObserved=false；Store 整合測試使用 SIMULATED，不能當 LIVE。
- 第一輪 GREEN `green.log`：14項來源測試通過；之後加入審查回歸，最終來源測試20項。
- 最終命令（repo root）：`./android/probe/gradlew.bat -p android/probe :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --console plain --no-daemon '-Pkotlin.compiler.execution.strategy=in-process'` → exit0／BUILD SUCCESSFUL，**72 tests／0 failures／0 errors**，包含來源20項與既有52項。見 `validation/builds/task5/verified.log`、`build-info.json`；後者包含實際lint計數、APK及來源hash。
- 來源契約與Store實際save/load/export整合測試通過；HTML標記及未知欄位可落盤。無新增依賴，Task1留存APK未覆蓋，建置產物仍只顯示原最小UI。
- 未重跑未修改的Node橋接測試；未發實站請求、安裝手機、adb 或執行 WebView。本機fixture不證明網站或背景可用，真機BFF端點／數字精度／query小數與30筆假設留Task13–14。
- 全部操作已結束，無阻礙；context實際百分比不可見。繼續時執行Task6樂屋，不能重做Task1–5。
