# HouseLens Android 規劃

更新：2026-11-06。此目錄包含規劃與已完成Task1–13的獨立驗證工程 `probe/`；Task14本機驗收完成，591前景4次0頁未通過。0.2.1截圖可見手機版列表；最新0.2.2 APK補固定拒絕原因碼，待一次重試定位。所有Android程式與紀錄都在本目錄，不混入Web規劃。

## 已確認的方向

- Kotlin＋Jetpack Compose 原生 App，先交付簽署 APK。
- Google 帳號登入；本人與家人共用房源及設定，所有有效成員均可管理。
- 七個平台的爬蟲、資料整理及呈現都納入 Android 移植目標。
- Android 直接使用 Supabase SDK／Data API／資料庫函式，不依賴 .NET HTTP API。
- Web 保留現有 .NET 架構，與 Android 使用同一份 Supabase 資料。
- Android 只有手動「立即抓取」，沒有每日排程，也不移植 LINE 推播。
- 優先爭取手動開始後可切換 App／鎖屏繼續；瀏覽器來源尚待真機證明，不能宣稱已可行。
- 驗證裝置為使用者的 Samsung S25+／One UI 9.0／Android 17；使用者自行安裝、操作並回報，不開 USB 偵錯。
- 同時抓取／清除時，後來者提示忙碌並手動重試，不自動排隊；目前只有一個正式 Supabase 專案。

## 閱讀順序

1. [Android 移植設計](01-android-design.md)：責任邊界、完整功能、兩端共存及中斷處理。
2. [Supabase 實際結構與相容性](02-supabase-schema.md)：遠端唯讀盤點結果與必要調整。
3. [七平台爬蟲可行性驗證規格](03-crawler-feasibility.md)：實作前必須取得的證據與停止條件。
4. [遠端結構快照](supabase-schema-2026-11-01.json)：欄位、約束、索引、RLS 與 grants；不含業務資料或連線密鑰。
5. [S25+ 驗證計畫與任務](plans/2026-11-03-s25plus-validation.md)：無 USB 偵錯的 APK 操作與證據門檻；該檔為原始計畫，實際結果以驗證摘要為準。
6. [驗證 APK 實作計畫](plans/2026-11-03-probe-apk-implementation.md)：14個任務的檔案、測試與交付步驟；Task1–13已完成，Task14本機驗收完成、591前景未通過，0.2.2待診斷重試。
7. [最新交接](handoff/handoff.md)：目前進度、逐 Task 停點、context 60% 門檻與下一輪入口。每次交接直接覆寫此固定檔案，不另建編號或日期版本。

## 狀態與邊界

已完成原始碼閱讀、官方文件查證、遠端結構盤點；使用者已回報本機驗收及591前景失敗證據。尚無實站採集成功、效能／耗電或背景通過結論；未修改遠端資料、RLS、grants 或 schema。

本次方向取代舊文件中的「Android 經由 .NET API 存取」及「Android 需要每日排程／LINE」假設。原有 Web／Worker 功能不因 Android 排除項目而自動刪除。

詳細實作任務應以本設計檢閱結果及七站可行性證據為基礎；不得將 WebView 候選方案寫成已驗證的 Playwright 替代品。


[APK 安裝與操作說明](validation/README.md)｜[Task13 執行紀錄](tasks/task13.md)｜[Task14 執行紀錄](tasks/task14.md)｜[驗證摘要](validation/summary.md)
