# HouseLens Android View Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付可在外出時使用、只允許核准 Google 帳號登入的 HouseLens Android 瀏覽 App。

**Architecture:** Android 使用單一 Compose app module，讀取既有 .NET API，收藏留在裝置。沿用同一個 API 程式，以公開唯讀與私人管理兩種啟動模式分隔用途；現有 Web 維持私人入口。兩份子計畫各有獨立驗證，不建立新的資料服務或同步系統。

**Tech Stack:** Kotlin／Compose／Material 3、Ktor、Supabase Auth、Coil、DataStore；既有 .NET 10／EF Core／PostgreSQL。

**Spec:** [spec.md](spec.md)，執行者須同時閱讀規格與對應子計畫。日期：2026-11-07；本輪只撰寫計畫，所有工作尚未執行。

## Global Constraints

- 「本次未讀取、搜尋或沿用 `android/`、`androidtest/` 內容」；後續亦不依賴這兩個目錄。
- 「本 App 定位為瀏覽端，不提供觸發爬取、資料清除或共用設定編輯。」
- 「Android 8.0（API 26）以上」；四個入口為找房、精選、收藏、區域。
- 「收藏依『API 環境＋登入 user ID＋property ID』隔離，只在本機保存。」
- 「正式版固定使用 HTTPS」；「API 以驗證後的 Supabase `sub` 作穩定使用者 ID。」
- 「第一版不刪清單完整歷史、不改降價／排序的既有語意。」
- 「按鈕、愛心、關閉、圖表操作與 chips 點擊區至少 48×48dp」；「支援字級放大至 200%」。
- 不動現有爬蟲、Worker、房源 schema；不將本機資料庫連線或管理金鑰加入 APK／文件／測試輸出。

## Review Focus

1. 舊帳號的慢回應在切換帳號後抵達：不得重建舊畫面／快取，B2、B3 有競態測試。
2. 看似自有網域的圖片或跨網域 redirect：不得取得 Bearer，B1、B2 驗證精確 origin 及重新導向。
3. Keystore 金鑰失效、加密檔損壞、OAuth 回呼重送：安全回登入、不遺失收藏 ID，B2 驗證。
4. 超大頁碼、重複排序值、相同時間價格：不 overflow、漏頁或編造順序，A1、B5 驗證。
5. 成員被停用但 token／圖片仍快取：下一次請求拒絕且不能繼續刷新資料，A2、B2 驗證。

---

## 計畫分工與順序

| 計畫 | 內容 | 可獨立驗收的交付 |
| --- | --- | --- |
| [01-api-plan.md](plans/01-api-plan.md) | A1 查詢邊界／分頁；A2 公開唯讀與成員驗證；A3 HTTPS 入口及部署驗證。 | 可由 HTTP client 驗證的安全唯讀 API，私人 Web 的既有合法操作仍可用。 |
| [02-app-plan.md](plans/02-app-plan.md) | B1 契約與專案；B2 登入；B3 收藏；B4 找房；B5 詳情；B6 精選／區域；B7 真機與 APK。 | 使用固定 fixture 可測的原生 App，最後接正式 API 交付。 |

依賴：`A1 → A2 → A3`；`B1 → B2 → B3 → B4 → B5 → B6 → B7`。A1／A2 可與 B1～B6 並行；B2 自動測試先使用模擬 Auth／HTTP，真實登入需 A2／A3。B7 必須等待 A3 及部署輸入完成。不可把 fixture 通過當作外網／Google 已可用。

各任務採最小測試週期：寫有意義的失敗測試 → 確認失敗原因 → 實作 → 指定驗證 → 只提交該任務檔案。首次環境／編譯失敗不算功能 RED。執行前檢查工作樹，必要時依 using-git-worktrees 建立隔離工作區；不搬動或覆蓋既有未提交成果。

## Ponytail 決策

- 同一 API binary，兩個明確模式；不新建 API 專案、BFF、微服務或授權資料表。
- 公開實例完全不映射管理路由；私人實例只綁 loopback，既有 Web 不改 origin。因此本次不增加 Vue 登入、圖片 Blob 管理或 SSE token。
- Android 一個 `app` module、手動建立依賴；只有 API 測試接點需要 interface，不引入 Hilt／Koin、泛用 Repository、UseCase 層或事件匯流排。
- 本機收藏用 DataStore，小量序列化資料；不加 Room、收藏 API、批次查詢、同步 queue 或 WorkManager。
- 圖表使用 Compose Canvas 的簡單線／柱與文字資料列；不加縮放、動畫圖表引擎、WebView 或 ECharts bridge。
- Auth 使用既有 SDK 的 PKCE／session，儲存才接 Android Keystore；不手刻 OAuth、JWT 驗章或 refresh token 協定。
- 不先做 CI/CD、商店發布、地圖、深色主題、會員管理後台。必要限制用 `shortcut: <限制>, <升級時機>` 留一行。

## 部署輸入與暫停點

可以先完成程式與模擬測試；A3／B7 的實際部署需要：持續運作主機、API 網域、HTTPS、Supabase project URL／publishable key、Google provider、核准的 Supabase user IDs、APK 簽署憑證及測試 Google 帳號。由部署者提供秘密，不向使用者要求把秘密貼進對話或版控。

部署預設為一個公開唯讀 API 程序＋反向代理；既有私人 Web／管理 API 留在原本私人環境，不為 App 另架遠端管理服務。外出讀既存資料不要求 Worker 一起搬家；資料持續更新仍由原資料產生端負責。

本機僅確認 PATH 的 Java 為 11、已查到的預設 SDK 為 31；不能視為已具備 B1 工具鏈。B1 設定工作階段的 JDK／SDK，不改全機 PATH。沒有測試 DB 時，完成可做的測試並明列整合測試尚未通過，不以共用 Supabase 代替。

## 規格覆蓋

| 規格要求 | 任務 |
| --- | --- |
| §1–3 範圍／共用資料／原生架構 | A2、B1，以上全域限制 |
| §4 導覽／返回／區域跳轉及還原 | B4、B5、B6 |
| §5.1 登入／帳號 | A2、B2 |
| §5.2–5.3 清單／篩選／分頁 | A1、B1、B4 |
| §5.4 詳情／歷史／來源 | B5 |
| §5.5 精選與大降價 | B6 |
| §5.6 收藏 | B3、B4、B5 |
| §5.7 區域分析／同名區語意 | B1、B6 |
| §6 視覺／無障礙 | B1、B4–B7 |
| §7 快取／錯誤／資料新鮮度 | B1–B6 |
| §8–10 契約／授權／部署 | A1–A3、B1、B2 |
| AC-01／02／13 | A2、A3、B2、B7 |
| AC-03／04／05 | A1、B4、B5 |
| AC-06 | B2、B3 |
| AC-07／08／09 | B1、B5、B6 |
| AC-10／11／14 | A2、B1、B2、B7 |
| AC-12 與效能 | B4–B7 |

## 自我檢查與執行交接

已由主規畫者對照規格檢查覆蓋、每步可驗證、跨任務型別一致、五項 Review Focus 與測試對應；另修正正式部署檢查的操作邊界，避免向共用環境試送爬取／刪除請求。所有產品任務維持未勾選。本次不建立產品程式、安裝依賴或執行建置。

建議後續採 **Native**：同一實作者按序完成兩個子計畫，最後做獨立整體 review，減少登入／狀態介面反覆交接；安全邊界仍須 A2 的真實 JWT 測試及 A3 的外部驗證。若選 **Subagent-driven**，每任務換實作者並獨立 review，品質關卡較密，但交接成本較高。依 writing-plans 的 Execution Handoff，計畫檢閱與選擇執行方式之後才開始實作。
