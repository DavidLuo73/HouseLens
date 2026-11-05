# Task 8 執行紀錄

計畫：android/plans/2026-11-03-probe-apk-implementation.md Task8；Base 4db731e；分支 android/probe-bootstrap。主代理實作，代理僅唯讀研究／review。

## 範圍與裁決

- 新增 HBHousingSource、HBHousingSourceTest 與三份合成 HTML 樣本；目前呼叫端僅測試，Runner／UI 留 Task12–13。沿用 Fixtures、ProbeRules、Store 與 transport，不新增依賴。
- Ruling: 延續使用者指定的 android/ 固定路徑、既有功能分支及逐 Task 停點；紀錄仍放 android/tasks 與 validation，不建立其他 worktree 或技能預設根目錄工作區。
- Ruling: rawKeys 保留每張原始卡，包括重複 ID；listings／excluded 按 ID 去重。每張卡先驗 ID 與正價格，壞卡整頁 INVALID，不因型態排除或去重而吞掉壞卡。
- Ruling: 10張正確原始卡為 MORE，1–9張為 END／last_page；超過10張顯示頁大小假設不適用，保留 UNKNOWN。重複滿頁由共同規則 INCONCLUSIVE；短頁的獨立卡數末頁證據仍優先於重複檢查，沿用既定 nextDecision，不改共用優先序。
- Ruling: 後端沒有住商合法零筆 selector／樣本，不能發明標記；0卡保守 INVALID，實站取得明確證據後再支援 EMPTY。這可能使真正零筆搜尋無法通過，不能誤報完整。
- Ruling: URL 沿現碼順序與型態映射，拒絕未知代碼；金額／坪數保留小數。房數取最小值、車位只要求有車位，未逐筆核實的精確房數／車位類型與用途保留未知；面積基準未知，不用建坪本地排除 minSizePing。
- Ruling: 樣本沿後端測試結構，住宅地址改為新北中和、車位地址改為新北永和，以符合 probe 查詢範圍；全部合成、networkObserved=false，不能當 LIVE。圖片保留來源 URL 與時間戳，沿精確 CDN 白名單，不自行改圖。

## 進度

Task 8: complete。實作、獨立 review 與必修修正、完整驗證已完成；Task9未開始，依使用者逐Task停點等待繼續。

## 審查與修正

- 主代理自查找到型態辨識過廣：用「樓／房」字判斷其他欄位，使大樓／廠房變成未知，繞過型態排除。`type-red.log` 已重現（16項中1項失敗）；改為辨識數字開頭等欄位格式，測試驗證大樓可辨識、選公寓排除大樓、商用大樓／廠房排除。
- 獨立 reviewer 找到 Important：任意含「車位」且未命中少數負向詞的文案會被當成已知 true，如「車位：無／無固定車位／車位另租」。`review-red.log` 重現（17項中1項失敗），改為只接受「有車位／車位」明確標記（允許空白），其他保持未知；回歸另含拆 span 與 NBSP 正／負向文案。
- reviewer 已核對原始卡數與去重、壞卡驗證、URL關聯、分頁邊界、Store／匯出；其餘沒有必修或次要遺留。
- reviewer 未評估實站／真機與尚未接線的 Runner／UI；裁決維持 Task12–14 範圍，本輪合成測試不可替代這些證據。

## 驗證

- RED：`:app:testDebugUnitTest --tests '*HBHousingSourceTest' --offline --console plain --no-daemon '-Pkotlin.compiler.execution.strategy=in-process'` → exit1，缺 HBHousingSource（預期）；見 `validation/builds/task8/red.log`。
- 初次 GREEN：同一命令 → exit0／BUILD SUCCESSFUL，15項來源測試通過，見 `green.log`。型態回歸 RED 見 `type-red.log`。
- 型態修正後完整驗證 exit0，見 `type-green-full.log`；車位回歸 RED 見 `review-red.log`。
- 最終命令（repo root）：`./android/probe/gradlew.bat -p android/probe :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --console plain --no-daemon '-Pkotlin.compiler.execution.strategy=in-process'` → **exit0／BUILD SUCCESSFUL，120 tests／0 failures／0 errors，lint0 errors／5個既有鎖版新版提示**。來源17項、既有103項全部通過；見 `validation/builds/task8/verified.log`、`build-info.json`。
- 測試涵蓋住宅／車位現價與面積、HALF_EVEN、URL／代碼／頁碼、10張全過濾後下一頁、1–9張末頁、重複原始卡仍滿頁、超過10張UNKNOWN、缺必要欄位、challenge、未知空頁、型態／價齡／地區排除、未知欄位及Store save/load/export。
- 使用 README 的局部 JAVA_HOME／GRADLE_USER_HOME／ANDROID_USER_HOME，無新依賴、SDK、manifest、Store、transport、UI、後端或正式資料變更。建置摘要保存本次來源檔hash與APK hash；Task1留存APK未覆蓋。
- 未重跑未修改的Node橋接；未跑實站、WebView、adb／手機或背景驗證。住商零筆標記、實際短頁邊界、小數查詢支援仍需實站取證，不能宣稱完整採集已通過。
- 全部命令已結束，無阻礙；context實際百分比不可見。下一步Task9信義HTTP／SSR／圖片，先讀SinyiScraper及測試，核對搜尋卡與全站JSON邊界、20張原始卡、影片首圖／Next image與圖片Referer。
