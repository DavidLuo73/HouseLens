# HouseLens Supabase Read Access Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. User selected this execution workflow on 2026-11-07; steps use checkbox (`- [ ]`) syntax.

**Goal:** 讓核准的本人／家人透過 Supabase Kotlin SDK 讀取既有房源，無須新增 API server。

**Architecture:** 使用現有 houselens schema、最小 ViewerMembers 名單、SELECT-only grants／RLS 與四個 security-invoker views。原網站與爬取端繼續使用現有資料，沒有新服務、同步系統或資料複製。

**Tech Stack:** Supabase PostgreSQL／Auth／PostgREST；SQL 與 PowerShell 自測；Android 使用 Supabase Kotlin SDK 的 Auth＋Postgrest。

**Spec:** [../spec.md](../spec.md) §8–10；同讀 [../plan.md](../plan.md)。本計畫取代已撤回的 A1–A3 API 計畫。

## Global Constraints

- 不修改 backend／frontend／android／androidtest 的程式，不新增 .NET API、Edge Function、Caddy 或主機。
- App 僅 SELECT；anon 不授予讀取，authenticated 不授予 INSERT／UPDATE／DELETE。
- View 使用 `security_invoker=true`；RLS 不 FORCE，不破壞現有 owner／BYPASSRLS 資料產生帳號。
- 不做全 schema GRANT ALL／default grants；不開放 NotificationLogs、管理設定與原始錯誤文字。
- SQL 先在本機隔離資料庫測試；正式套用前核對實際 owner／grants／policies 與 diff。

## Review Focus

1. 使用 publishable key／任意 Google 帳號不等於核准 → S1 測 anon／未核准拒絕。
2. 修改 member 名單後舊 JWT 尚有效 → S1 測 RLS 即時失去列讀取，B2 查會員列。
3. View 繞過 RLS或多重 grant 暴露管理表 → S1 以真實角色執行查詢／寫入拒絕測試。
4. 排序同值、歷史超過 REST 上限 → S1 固定 ID 次序，B1 分批查歷史不截斷。
5. 政策影響既有寫入帳號 → S1／S2 核對 owner／BYPASSRLS，不直接覆蓋既有新政策。

---

## 檔案與介面

新增 `androidView/supabase/viewer.sql`、`androidView/supabase/tests/bootstrap.sql`、`androidView/supabase/tests/verify.sql`、`androidView/supabase/Test-ViewerSql.ps1`、`androidView/supabase/README.md`。所有安裝、測試、回復界線集中這裡，不建立後端 migration 專案。

SQL 介面由 spec §8 定義：ViewerMembers(UserId UUID primary key references auth.users)，viewer_properties、viewer_top_properties、viewer_big_drops、viewer_district_stats。Views 欄位採明確 camelCase alias，對接 App DTO；底層表的 PascalCase／整數 enum 依既有 EF 模型，不能假設 todos／public schema。

- viewer_properties：id/title/city/district/address/areaPing/floor/ageYears/hasParking/currentTotalPrice/currentUnitPrice/status/score/isNew/firstSeenAt/lastSeenAt/imageUrl/listingUrl/sourceSites/int[]/latestChangeFlag/latestChangePercent/latestIsBigDrop/hasPriceDrop。主刊登依 Listing.Id，最近歷史依 CapturedAt、Id；不塞完整歷史。
- viewer_top_properties：district、rank、id/title/totalPrice/unitPrice/ageYears/hasParking/score；active且score存在，依區名排名並以ID解同分，rank≤5。
- viewer_big_drops：id/title/district/address/originalPrice/newPrice/dropAmount/dropPercent/capturedAt/status；每房源最後一次 IsBigDrop 觀測，事件價與現價分開。比例為小數，除數為0／未知時 originalPrice／dropAmount 回 null，不造數字。
- viewer_district_stats：district/propertyCount/avgUnitPrice/minTotalPrice/maxTotalPrice/priceBuckets(range,count)/trend(date,avgUnitPrice)/insufficientData；沿用既有網站樣本與分箱／趨勢語意，空資料以旗標及null表示。

### Task S1: 可測的唯讀權限與查詢 views

**Files:** 上述 SQL、測試 SQL 與 Test-ViewerSql.ps1。

**Interfaces:** consumes 現有 EF 資料結構；produces 以上表／view。測試 bootstrap 只允許 localhost 暫存資料庫，模擬 auth.uid() 與 anon／authenticated；正式 viewer.sql 不建立假 Auth、不含資料庫密碼或核准的真實 user ID。

- [ ] **Step 1: 寫失敗自測。** verify.sql 以固定 UUID 兩位使用者及合成房源，SET ROLE／JWT claim 後斷言：anon讀取拒絕、未核准看0筆、核准看預期ID、移除名單後0筆、他人會員列不可見、全部寫方法被拒絕、既有owner仍可寫。測同分同價6筆跨三頁、跨來源／同時歷史、最後降價而非現價、0／1筆區域、缺單價的fallback與分布邊界。使用 SQL DO／RAISE EXCEPTION 及交易 rollback；不做只檢查SQL文字的測試。
- [ ] **Step 2: 確認 RED。** `pwsh -File androidView/supabase/Test-ViewerSql.ps1`；預期缺少views／grants／RLS，測試失敗。不得以連線錯誤當成RED；測試腳本本身有 `-SelfTest` 驗證非loopback目標遭拒，避免碰正式庫。
- [ ] **Step 3: 最小實作 viewer.sql。** 先建立 ViewerMembers own-row SELECT policy，再對必要表建立 membership SELECT policy與欄位 grants；四個 invoker views 不改既有表資料。SourceRunResults 不授予 ErrorMessage；同名區沿用網站區名語意。安裝包在交易中，確認既有同名物件是否由本功能管理，遇到未知政策／grants變更即報告，不盲目drop/recreate。
- [ ] **Step 4: 驗證 GREEN。** 以 PostgreSQL 17 本機隔離庫跑整套角色與計算自測，全部PASS；記錄SQL執行與rollback結果。另用實際PostgREST／SDK fixture確認 header、大小寫、count／分頁，不假設SQL成功即代表REST已公開。
- [ ] **Step 5: 提交。** `git commit -m "feat: add read-only Supabase viewer queries"`；只提交腳本與合成fixture。

### Task S2: Supabase 設定與真實連線驗證

**Files:** `androidView/supabase/README.md`、`androidView/validation/acceptance.md`。

**Interfaces:** consumes S1腳本、使用者提供的Project URL／publishable key；produces Data API可讀與Google PKCE設定。App callback固定 `tw.houselens.viewer://auth/callback`，不需要自建網域。

- [ ] **Step 1: 整理可檢閱部署說明。** 列出將改的表、grants、policies與views，先確認資料產生帳號owner／BYPASSRLS及Dashboard exposed schemas；寫明停用App讀權的回復步驟，不刪房源／Auth使用者。
- [ ] **Step 2: 安全套用與設定。** 只在S1通過且正式變更範圍可檢閱後套用；將houselens加入Data API exposed schemas、Google provider及精確callback allowlist，維運者核對Google身分後加入ViewerMembers。不得自動核准任意Google帳號。
- [ ] **Step 3: 真實驗證。** App／REST以匿名、未核准、核准三種身分確認隔離；撤銷名單後下一次畫面刷新拒絕。寫入拒絕先在隔離測試庫證明，不向正式房源試寫／刪除。B7驗證外出與電腦關機仍能讀Supabase。
- [ ] **Step 4: 提交文件。** `git commit -m "docs: document Supabase viewer setup and verification"`；未完成的Dashboard／Google設定明列，不宣稱上線。

