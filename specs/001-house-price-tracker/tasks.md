---
description: "Task list for 房屋售價追蹤系統 implementation"
---

# Tasks: 房屋售價追蹤系統

**Input**: Design documents from `/specs/001-house-price-tracker/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/api.md, quickstart.md

**Tests**: 依專案憲章原則 IV（目標導向執行：先寫可驗證測試再實作），各使用者故事
納入針對性的契約／整合測試任務。測試任務 MUST 先撰寫並確認失敗，再進行實作。

**Organization**: 任務依使用者故事分組，使每個故事可獨立實作與測試。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可平行執行（不同檔案、無未完成相依）
- **[Story]**: 所屬使用者故事（US1–US8）
- 描述含明確檔案路徑

## Path Conventions

- 後端：`backend/src/HouseLens.{Domain,Application,Infrastructure,Worker,Api}/`
- 後端測試：`backend/tests/HouseLens.{UnitTests,IntegrationTests}/`
- 前端：`frontend/src/`、`frontend/tests/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 專案初始化與基礎結構

- [X] T001 建立 .NET 解決方案與五個後端專案（Domain、Application、Infrastructure、Worker、Api）於 `backend/src/`，依 plan.md 分層結構
- [X] T002 [P] 加入後端 NuGet 相依：EF Core 10 + SQLite provider、Quartz.NET、HtmlAgilityPack、AngleSharp、Microsoft.Playwright 至對應專案
- [X] T003 [P] 於 `frontend/` 建立 Vue 3 + Vite + TypeScript 專案，並加入 Pinia、Vue Router、vue-echarts
- [X] T004 [P] 設定後端 `.editorconfig` 與分析器規則於 repository root
- [X] T005 [P] 設定前端 ESLint + Prettier + Vitest 於 `frontend/`
- [X] T006 建立測試專案 `backend/tests/HouseLens.UnitTests` 與 `backend/tests/HouseLens.IntegrationTests`（xUnit + FluentAssertions），並加入專案參考

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 所有使用者故事共用、且必須先完成的核心基礎

**⚠️ CRITICAL**: 此階段完成前，任何使用者故事不得開始

- [X] T007 [P] 定義列舉 PropertyStatus、SourceSite、PriceChangeFlag、RunStatus 於 `backend/src/HouseLens.Domain/Enums/`
- [X] T008 [P] 建立 Property 實體於 `backend/src/HouseLens.Domain/Entities/Property.cs`
- [X] T009 [P] 建立 Listing 實體於 `backend/src/HouseLens.Domain/Entities/Listing.cs`
- [X] T010 [P] 建立 PriceHistoryEntry 實體於 `backend/src/HouseLens.Domain/Entities/PriceHistoryEntry.cs`
- [X] T011 [P] 建立 CrawlRun 與 SourceRunResult 實體於 `backend/src/HouseLens.Domain/Entities/`
- [X] T012 [P] 建立 TrackingCriteria、ScoringConfig、PropertyScore、NotificationLog 實體於 `backend/src/HouseLens.Domain/Entities/`
- [X] T013 建立 AppDbContext 與各實體 EF Core 設定於 `backend/src/HouseLens.Infrastructure/Persistence/`（依 data-model.md 關係與唯一性約束）
- [X] T014 建立初始 EF Core Migration 並接上 `database update` 啟動流程於 `backend/src/HouseLens.Infrastructure/Migrations/`
- [X] T015 建立預設資料種子（八個追蹤區、總價上限 800、ScoringConfig 預設權重與大降價門檻）於 `backend/src/HouseLens.Infrastructure/Persistence/SeedData.cs`
- [X] T016 [P] 設定組態管理（appsettings + user-secrets：LINE 權杖、排程 Cron、評分/門檻）於 `backend/src/HouseLens.Api` 與 `HouseLens.Worker`
- [X] T017 [P] 設定 logging 與全域錯誤處理於 Api 與 Worker
- [X] T018 建立 ASP.NET Core API 主機（DI、路由、統一錯誤回應中介層）於 `backend/src/HouseLens.Api/Program.cs`，含 CORS 允許本機前端
- [X] T019 [P] 定義 `ISourceScraper` 抽象與正規化 `PropertyDto` 於 `backend/src/HouseLens.Application/Crawling/`

**Checkpoint**: 基礎就緒，使用者故事可開始

---

## Phase 3: User Story 1 - 每日自動抓取並記錄符合條件物件 (Priority: P1) 🎯 MVP

**Goal**: 系統每日定時自動抓取八區、總價 ≤800 萬的物件，正規化後完整記錄各欄位，
不需人工介入；可查看當日物件清單與抓取批次狀態。

**Independent Test**: 觸發一次抓取後，`GET /api/properties` 回傳之物件皆位於追蹤區、
總價 ≤800 萬且含全部必要欄位（缺漏標示「未提供」）；`GET /api/crawl-runs/latest` 為 completed。

### Tests for User Story 1 ⚠️（先寫並確認失敗）

- [X] T020 [P] [US1] 解析器單元測試：以離線 HTML fixture 驗證擷取與正規化於 `backend/tests/HouseLens.UnitTests/Crawling/ScraperTests.cs`
- [X] T021 [P] [US1] 整合測試：抓取→寫入後 `GET /api/properties` 僅含符合條件物件、欄位齊全於 `backend/tests/HouseLens.IntegrationTests/Crawling/CrawlPipelineTests.cs`
- [X] T022 [P] [US1] 契約測試：`GET /api/crawl-runs/latest` 回傳批次與各來源成敗於 `backend/tests/HouseLens.IntegrationTests/Api/CrawlRunEndpointTests.cs`

### Implementation for User Story 1

- [X] T023 [P] [US1] 實作首個靜態來源擷取器（HtmlAgilityPack）實作 `ISourceScraper` 於 `backend/src/HouseLens.Infrastructure/Crawling/Scrapers/`
- [X] T024 [US1] 實作條件過濾（追蹤區 + 總價上限）與欄位缺漏標示於 `backend/src/HouseLens.Application/Crawling/PropertyNormalizer.cs`
- [X] T025 [US1] 實作負責任爬取：robots.txt 遵循、速率限制、退避重試於 `backend/src/HouseLens.Infrastructure/Crawling/HttpFetcher.cs`（FR-015）
- [X] T026 [US1] 實作抓取協調與持久化（寫入 Property/Listing/PriceHistory/CrawlRun/SourceRunResult，單一來源失敗隔離）於 `backend/src/HouseLens.Application/Crawling/CrawlOrchestrator.cs`（FR-014）
- [X] T027 [US1] 實作每日 Quartz 排程任務與開發用手動觸發於 `backend/src/HouseLens.Worker/CrawlJob.cs`、`Program.cs`（FR-001）
- [X] T028 [P] [US1] 實作 `GET /api/properties`（基本清單）於 `backend/src/HouseLens.Api/Endpoints/PropertiesEndpoints.cs`
- [X] T029 [P] [US1] 實作 `GET /api/crawl-runs/latest` 於 `backend/src/HouseLens.Api/Endpoints/CrawlRunEndpoints.cs`
- [X] T030 [P] [US1] 前端：型別化 API 用戶端與基本物件清單頁於 `frontend/src/services/api.ts`、`frontend/src/pages/PropertyList.vue`

**Checkpoint**: US1 可獨立運作——每日自動抓取並可於前端檢視當日清單（MVP 完成）

---

## Phase 4: User Story 2 - 歷史價格追蹤與狀態變化標記 (Priority: P2)

**Goal**: 記錄每物件各時間點價格，標記降價/漲價，連續 2 批次未見標記為已下架並保留歷史。

**Independent Test**: 連續兩批次調整某物件價格→`GET /api/properties/{id}` 歷史含降價標記；
移除某物件連續兩批次→狀態轉為 delisted 且歷史保留。

### Tests for User Story 2 ⚠️

- [X] T031 [P] [US2] 單元測試：價格變動標記與百分比計算於 `backend/tests/HouseLens.UnitTests/Analysis/PriceChangeTests.cs`
- [X] T032 [P] [US2] 整合測試：連續批次的下架判定（MissingCount≥2）於 `backend/tests/HouseLens.IntegrationTests/Analysis/DelistingTests.cs`

### Implementation for User Story 2

- [X] T033 [US2] 實作價格比對與 ChangeFlag/百分比/IsBigDrop 計算於 `backend/src/HouseLens.Application/Analysis/PriceChangeDetector.cs`（FR-006、FR-019）
- [X] T034 [US2] 實作下架判定（連續 2 批次未見→Delisted、重現恢復 Active、保留歷史）於 `backend/src/HouseLens.Application/Analysis/StatusUpdater.cs`（FR-007、research §8）
- [X] T035 [US2] 將價格比對與下架判定接入抓取協調流程於 `CrawlOrchestrator.cs`
- [X] T036 [P] [US2] 實作 `GET /api/properties/{id}`（含 priceHistory 與 sources）於 `backend/src/HouseLens.Api/Endpoints/PropertiesEndpoints.cs`
- [X] T037 [P] [US2] 前端：物件詳情頁與價格趨勢圖、清單價格變化標記於 `frontend/src/pages/PropertyDetail.vue`

**Checkpoint**: US1+US2 各自可獨立驗證

---

## Phase 5: User Story 3 - 重複物件比對與合併 (Priority: P2)

**Goal**: 辨識並合併跨平台同一物件為單一 Property，保留各來源連結與價格。

**Independent Test**: 同一物件兩來源 fixture（地址/坪數/樓層/屋齡相符）→`GET /api/properties` 呈現單一條目、sources 含兩連結。

### Tests for User Story 3 ⚠️

- [X] T038 [P] [US3] 單元測試：去重比對門檻（相符合併、差異不合併）於 `backend/tests/HouseLens.UnitTests/Dedup/MatcherTests.cs`

### Implementation for User Story 3

- [X] T039 [US3] 實作模糊比對服務（正規化地址 + 坪數±0.5 + 樓層相同 + 屋齡±1，門檻可設定）於 `backend/src/HouseLens.Application/Dedup/DuplicateMatcher.cs`（FR-005、research §7）
- [X] T040 [US3] 實作合併邏輯（MergedIntoPropertyId、保留 Listings、代表價取最新最低）於 `backend/src/HouseLens.Application/Dedup/PropertyMerger.cs`
- [X] T041 [US3] 將去重合併接入抓取協調流程於 `CrawlOrchestrator.cs`，並確保清單只顯示主物件

**Checkpoint**: US1–US3 各自可獨立驗證

---

## Phase 6: User Story 4 - 新物件提示 (Priority: P3)

**Goal**: 將先前未記錄且符合條件的新上架物件標記 IsNew，後續批次取消。

**Independent Test**: 注入全新物件→該物件 isNew=true；下一批次後 isNew=false。

### Tests for User Story 4 ⚠️

- [X] T042 [P] [US4] 整合測試：新物件標記與次批次取消於 `backend/tests/HouseLens.IntegrationTests/Analysis/NewListingTests.cs`

### Implementation for User Story 4

- [X] T043 [US4] 實作新物件標記與取消邏輯於 `backend/src/HouseLens.Application/Analysis/NewListingMarker.cs`，並接入協調流程（FR-008）
- [X] T044 [P] [US4] 前端：清單「新物件」標記呈現於 `frontend/src/components/PropertyCard.vue`

**Checkpoint**: US1–US4 各自可獨立驗證

---

## Phase 7: User Story 5 - 價格分析報表與視覺化 (Priority: P3)

**Goal**: 依地區/屋齡/坪數維度提供平均單價、總價分布與趨勢，至少一種視覺化呈現。

**Independent Test**: 有資料下 `GET /api/analytics/districts` 回傳統計；前端呈現趨勢圖；資料不足維度標示 insufficientData。

### Tests for User Story 5 ⚠️

- [X] T045 [P] [US5] 單元測試：區域統計與資料不足判定於 `backend/tests/HouseLens.UnitTests/Analysis/DistrictAnalyticsTests.cs`
- [X] T046 [P] [US5] 契約測試：`GET /api/analytics/districts` 回應結構於 `backend/tests/HouseLens.IntegrationTests/Api/AnalyticsEndpointTests.cs`

### Implementation for User Story 5

- [X] T047 [US5] 實作區域分析服務（平均單價、分布桶、趨勢、insufficientData）於 `backend/src/HouseLens.Application/Analysis/DistrictAnalyticsService.cs`（FR-009）
- [X] T048 [P] [US5] 實作 `GET /api/analytics/districts` 於 `backend/src/HouseLens.Api/Endpoints/AnalyticsEndpoints.cs`
- [X] T049 [P] [US5] 前端：區域分析頁與 ECharts 趨勢/分布圖於 `frontend/src/pages/DistrictAnalytics.vue`（FR-010）

**Checkpoint**: US1–US5 各自可獨立驗證

---

## Phase 8: User Story 6 - 篩選與檢視物件清單 (Priority: P3)

**Goal**: 依地區/價格區間/有車位/是否降價篩選與排序，有車位優先標示，無車位仍保留。

**Independent Test**: 套用「中壢區 + 有車位 + 近期降價」→結果皆符合且有車位明顯標示。

### Tests for User Story 6 ⚠️

- [X] T050 [P] [US6] 契約測試：`GET /api/properties` 各篩選/排序/分頁參數於 `backend/tests/HouseLens.IntegrationTests/Api/PropertyFilterTests.cs`

### Implementation for User Story 6

- [X] T051 [US6] 擴充 `GET /api/properties` 篩選/排序/分頁（district、price、hasParking、priceDropped、status、sortBy、page）於 `PropertiesEndpoints.cs` 與 `backend/src/HouseLens.Application/Queries/PropertyQueryService.cs`（FR-011、FR-012、FR-013）
- [X] T052 [P] [US6] 前端：篩選器、排序、車位優先標示、分頁於 `frontend/src/pages/PropertyList.vue`、`frontend/src/components/FilterBar.vue`、`frontend/src/stores/filters.ts`
- [X] T072 [US6] 實作大降價查詢與 `GET /api/analytics/top-properties?type=bigDrop` 分支（當日/近期大降價清單，含地址、原價、新價、降幅、連結）於 `backend/src/HouseLens.Application/Analysis/BigDropQueryService.cs`、`backend/src/HouseLens.Api/Endpoints/AnalyticsEndpoints.cs`（FR-023，沿用 US2 大降價判定結果）
- [X] T073 [P] [US6] 前端「大降價物件總覽」頁於 `frontend/src/pages/BigDropOverview.vue`（FR-023）

**Checkpoint**: US1–US6 各自可獨立驗證

> 註：T072–T073 於本輪一致性修正補入（對應分析 C1），沿用接續編號以避免重編既有任務。

---

## Phase 9: User Story 7 - 物件綜合評分與優質排行 (Priority: P3)

**Goal**: 為每個符合條件物件計算可調權重的綜合評分，產生各區 Top 5；權重可調並保存。

**Independent Test**: 物件皆獲評分；各區可列 Top 5（或實際筆數）；調整權重後排行更新並保存。

### Tests for User Story 7 ⚠️

- [X] T053 [P] [US7] 單元測試：評分計算（含因子缺漏時重新正規化權重）於 `backend/tests/HouseLens.UnitTests/Scoring/ScoreCalculatorTests.cs`
- [X] T054 [P] [US7] 契約測試：`GET /api/analytics/top-properties` 與 `PUT /api/config` 權重合計驗證於 `backend/tests/HouseLens.IntegrationTests/Api/ScoringEndpointTests.cs`

### Implementation for User Story 7

- [X] T055 [US7] 實作評分計算純函式（單價相對行情/屋齡/車位/地段加權，缺漏因子權重重新正規化）於 `backend/src/HouseLens.Application/Scoring/ScoreCalculator.cs`（FR-016）
- [X] T056 [US7] 實作 Top 5 排行服務（各區依評分排序、不足 5 筆顯示實際筆數、僅含 Active）於 `backend/src/HouseLens.Application/Scoring/TopPropertiesService.cs`（FR-018）
- [X] T057 [US7] 將評分計算接入每日分析流程於 `CrawlOrchestrator.cs`
- [X] T058 [P] [US7] 實作 `GET /api/analytics/top-properties` 與 `GET/PUT /api/config`（權重合計=1.0 驗證）於 `backend/src/HouseLens.Api/Endpoints/AnalyticsEndpoints.cs`、`ConfigEndpoints.cs`（FR-017）
- [X] T059 [P] [US7] 前端：各區 Top 5 總覽頁與評分權重設定頁於 `frontend/src/pages/TopProperties.vue`、`frontend/src/pages/ScoringConfig.vue`

**Checkpoint**: US1–US7 各自可獨立驗證

---

## Phase 10: User Story 8 - 每日 LINE 推播通知 (Priority: P3)

**Goal**: 每日分析後透過 LINE 推播大降價清單與各區 Top 5；發送失敗不影響資料保存。

**Independent Test**: 設定有效權杖執行流程→LINE 收到兩則通知含必要欄位；無大降價時不發空通知；發送失敗記錄於 NotificationLog。

### Tests for User Story 8 ⚠️

- [X] T060 [P] [US8] 單元測試：訊息組裝（大降價/Top 5 欄位）與空大降價略過於 `backend/tests/HouseLens.UnitTests/Notification/MessageBuilderTests.cs`
- [X] T061 [P] [US8] 整合測試：發送失敗時記錄 NotificationLog 且不影響資料保存於 `backend/tests/HouseLens.IntegrationTests/Notification/NotificationFailureTests.cs`

### Implementation for User Story 8

- [X] T062 [P] [US8] 實作 LINE Messaging API 用戶端（push、權杖取自 secrets）於 `backend/src/HouseLens.Infrastructure/Notification/LineMessagingClient.cs`（FR-022）
- [X] T063 [US8] 實作通知訊息組裝（大降價清單、各區 Top 5、空大降價略過）於 `backend/src/HouseLens.Application/Notification/NotificationBuilder.cs`（FR-020）
- [X] T064 [US8] 實作通知發送與 NotificationLog 記錄、失敗隔離於 `backend/src/HouseLens.Application/Notification/NotificationService.cs`（FR-021）
- [X] T065 [US8] 將通知發送接入每日 Worker 流程（抓取→分析→評分→通知）於 `backend/src/HouseLens.Worker/CrawlJob.cs`

**Checkpoint**: US1–US8 全部可獨立驗證

---

## Phase 11: Polish & Cross-Cutting Concerns

**Purpose**: 跨故事的收尾與品質強化

- [X] T066 [P] 補充單元測試覆蓋核心邏輯邊界於 `backend/tests/HouseLens.UnitTests/`
- [X] T067 [P] 前端元件與 store 測試於 `frontend/tests/`
- [X] T068 [P] 撰寫 README / 部署與設定說明（含 LINE 權杖設定、排程 Cron）於 `backend/README.md`、`frontend/README.md`
- [X] T069 安全強化檢查：確認密鑰未入版控、本機 API 僅 localhost 存取（plan Constraints、FR-022）
- [X] T070 效能檢查：數萬筆資料下常用查詢回應 < 500ms（plan Performance Goals）
- [X] T071 依 quickstart.md 執行端到端驗證情境 1–7
- [X] T074 驗證 SC-007 通知時效：通知於每日分析完成後 10 分鐘內送達（量測 Worker 流程時間）於 `backend/tests/HouseLens.IntegrationTests/Notification/NotificationTimingTests.cs`（對應分析 C2）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 無相依，可立即開始
- **Foundational (Phase 2)**: 依賴 Setup 完成；阻擋所有使用者故事
- **User Stories (Phase 3–10)**: 皆依賴 Foundational 完成
  - US2、US3、US4、US7 透過 `CrawlOrchestrator` 接入抓取流程，與 US1 在管線層有整合點，
    建議於 US1 之後進行；各故事本身仍可獨立測試
  - US5、US6、US8 主要新增端點/前端/服務，相依度低
- **Polish (Phase 11)**: 依賴所需故事完成

### User Story Dependencies

- **US1 (P1)**: Foundational 後即可開始，無對其他故事相依（MVP）
- **US2、US3 (P2)**: Foundational 後可開始；實作上接入 US1 的抓取管線（整合點），但可獨立驗證
- **US4、US5、US6、US7、US8 (P3)**: Foundational 後可開始；US6 的大降價總覽（T072–T073）沿用 US2 的大降價判定結果；US8 的通知內容使用 US2 的大降價與 US7 的評分，建議排於其後

### Within Each User Story

- 測試先寫並失敗 → 模型 → 服務 → 端點 → 整合 → 前端
- 故事完成並驗證後再進入下一優先序

### Parallel Opportunities

- Setup 中 T002–T006（[P]）可平行
- Foundational 中 T007–T012、T016–T017、T019（[P]）可平行；T013–T015、T018 須序列（依賴實體與 DbContext）
- 各故事內標 [P] 的測試、後端端點與前端頁面可平行
- Foundational 完成後，多名開發者可平行承接不同故事

---

## Parallel Example: User Story 1

```bash
# 先平行撰寫 US1 測試（確認失敗）：
Task: "解析器單元測試 in backend/tests/HouseLens.UnitTests/Crawling/ScraperTests.cs"
Task: "抓取管線整合測試 in backend/tests/HouseLens.IntegrationTests/Crawling/CrawlPipelineTests.cs"
Task: "crawl-runs/latest 契約測試 in backend/tests/HouseLens.IntegrationTests/Api/CrawlRunEndpointTests.cs"

# 端點與前端可平行：
Task: "GET /api/properties in backend/src/HouseLens.Api/Endpoints/PropertiesEndpoints.cs"
Task: "GET /api/crawl-runs/latest in backend/src/HouseLens.Api/Endpoints/CrawlRunEndpoints.cs"
Task: "物件清單頁 in frontend/src/pages/PropertyList.vue"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. 完成 Phase 1：Setup
2. 完成 Phase 2：Foundational（關鍵，阻擋所有故事）
3. 完成 Phase 3：US1（每日自動抓取與清單檢視）
4. **STOP and VALIDATE**：獨立驗證 US1（quickstart 情境 1）
5. 可展示／試用

### Incremental Delivery

1. Setup + Foundational → 基礎就緒
2. US1 → 驗證 → MVP
3. 依序 US2（歷史/下架）→ US3（去重）→ US4（新物件）→ US5（分析）→ US6（篩選）
   → US7（評分/Top5）→ US8（LINE 推播），每個故事完成即增量交付
4. 每個故事獨立驗證，不破壞既有故事

---

## Notes

- [P] = 不同檔案、無相依，可平行
- [Story] 標籤對應 spec.md 使用者故事以利追溯
- 依憲章原則 IV：實作前先確認測試失敗；原則 II：避免過度設計，評分/門檻以設定驅動
- 每完成一個任務或邏輯群組即提交
- 可於任一 Checkpoint 停下，獨立驗證該故事
