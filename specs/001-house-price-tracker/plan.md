# Implementation Plan: 房屋售價追蹤系統

**Branch**: `001-house-price-tracker` | **Date**: 2026-06-14 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-house-price-tracker/spec.md`

## Summary

打造一套單一使用者的本機網頁應用：以 .NET 10 背景服務（Worker）每日定時爬取台灣
各大房仲平台符合條件（新北六區、桃園二區、總價 800 萬以下）的物件，正規化後寫入
SQLite，追蹤歷史價格與下架／重複狀態，計算降價偵測與物件評分，並透過 LINE
Messaging API 推播「大降價清單」與「各區域 Top 5 優質物件」。ASP.NET Core Web API
對前端 Vue 3 應用提供物件查詢、歷史價格與區域趨勢統計，前端呈現清單、篩選、
價格趨勢圖與區域分析。

**範圍說明（依憲章原則 I 浮現權衡）**：本計畫納入兩項在初版規格草稿後新增的能力——
(1) 物件評分演算法、(2) LINE 推播通知（大降價、Top 5）。這兩項已於 spec 的
FR-016~FR-023 與對應使用者故事（US7、US8）正式納入範圍，spec 與本計畫一致；
另含大降價物件總覽（FR-023）作為站內檢視。

## Technical Context

**Language/Version**: C# / .NET 10 (LTS)；前端 TypeScript 5.x + Vue 3.5

**Primary Dependencies**:
- 後端：ASP.NET Core Web API、EF Core 10（SQLite provider）、背景服務採
  `Microsoft.Extensions.Hosting` BackgroundService 搭配 Quartz.NET（每日 Cron 觸發）
- 爬蟲：HtmlAgilityPack / AngleSharp（靜態頁面）；Microsoft.Playwright（SPA 渲染頁面）
- 前端：Vue 3 + Vite、Pinia（狀態）、Vue Router、圖表採 Apache ECharts（vue-echarts）

**Storage**: SQLite 單檔資料庫，透過 EF Core 存取與 Migrations 管理結構

**Testing**: 後端 xUnit + FluentAssertions（單元／整合）、爬蟲解析以離線 HTML 樣本
做 fixture 測試；前端 Vitest + Vue Test Utils

**Target Platform**: 本機 Windows（開發機 Windows 10）執行；後端為單一可執行主機程序
（API + Worker 同進程或拆分兩程序），前端為本機瀏覽器存取 `localhost`

**Project Type**: Web application（backend + frontend + background worker）

**Performance Goals**:
- 每日抓取在合理速率限制下於 60 分鐘內完成所有來源（個人用途，非即時）
- 物件清單查詢 API 在資料量達數萬筆時，常用篩選查詢於本機回應 < 500ms

**Constraints**:
- MUST 尊重各來源 `robots.txt` 與服務條款，套用速率限制與重試退避（憲章附加約束）
- 單一使用者、本機部署，無需高併發與水平擴展
- 個資與安全：LINE Channel Access Token 等密鑰 MUST 以本機設定／環境變數保存，
  不得進入版本控制

**Scale/Scope**: 8 個追蹤區域、初期 1–3 個來源平台；累積物件量級預估數千至數萬筆、
歷史價格點數十萬筆（SQLite 可勝任）

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

依 `.specify/memory/constitution.md` v1.0.0 四項原則檢核：

| 原則 | 檢核 | 狀態 |
|------|------|------|
| I. 先思考再編碼 | 已浮現範圍擴張（評分／LINE）與四項待確認事項，並於 research.md 逐一定案 | ✅ 通過 |
| II. 簡潔優先 | 採單檔 SQLite，不預先導入 PostgreSQL；不建立未要求之抽象 | ⚠ 見下方追蹤 |
| III. 外科手術式變更 | 全新專案，無既有程式碼可破壞；模組邊界清楚 | ✅ 通過 |
| IV. 目標導向執行 | 對應 spec SC-001~SC-005 定義可驗證標準；每模組附測試策略 | ✅ 通過 |
| 附加約束：負責任爬取 | research.md 明訂 robots.txt 遵循、速率限制、退避與來源失敗隔離 | ✅ 通過 |

**原則 II 複雜度追蹤**：物件評分演算法（多因子加權）與 LINE 推播屬使用者明確要求，
非投機性功能；惟需確保權重以設定檔驅動、演算法保持單一函式可測，避免過度設計。
詳見 Complexity Tracking。

## Project Structure

### Documentation (this feature)

```text
specs/001-house-price-tracker/
├── plan.md              # 本檔（/speckit-plan 輸出）
├── research.md          # Phase 0 輸出
├── data-model.md        # Phase 1 輸出
├── quickstart.md        # Phase 1 輸出
├── contracts/           # Phase 1 輸出（REST API 契約）
│   └── api.md
└── tasks.md             # Phase 2 輸出（/speckit-tasks 產生，非本指令）
```

### Source Code (repository root)

```text
backend/
├── src/
│   ├── HouseLens.Domain/          # 實體、列舉、領域規則（Property, Listing, PriceHistory…）
│   ├── HouseLens.Application/     # 用例服務：去重、降價偵測、評分、查詢
│   ├── HouseLens.Infrastructure/  # EF Core DbContext、Migrations、爬蟲擷取器、LINE 用戶端
│   ├── HouseLens.Worker/          # 背景排程（Quartz）每日抓取流程協調
│   └── HouseLens.Api/             # ASP.NET Core Web API（查詢、歷史、區域統計端點）
└── tests/
    ├── HouseLens.UnitTests/       # 去重／降價／評分／解析器（HTML fixture）單元測試
    └── HouseLens.IntegrationTests/# API 端點與 EF Core（SQLite in-memory/暫存檔）整合測試

frontend/
├── src/
│   ├── components/      # 清單、篩選器、車位標示、價格趨勢圖、區域分析卡片
│   ├── pages/           # 物件清單、物件詳情、區域分析、Top5/降價總覽
│   ├── services/        # API 用戶端（型別化）
│   └── stores/          # Pinia 狀態（篩選條件、查詢結果）
└── tests/               # Vitest 元件與 store 測試
```

**Structure Decision**: 採 Web application 結構，後端以分層方案（Domain /
Application / Infrastructure / Worker / Api）切分，使爬蟲、分析、API、排程各自獨立
可測；前端為獨立 Vue 3 + Vite 專案，透過型別化 API 用戶端串接後端。Worker 與 Api
可同方案不同啟動專案，本機可分別啟動。

## Complexity Tracking

> 僅在 Constitution Check 有需正當化之違規時填寫

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| 分層方案（5 個後端專案） | 爬蟲、分析、排程、API 關注點差異大，分層利於各自測試與替換來源 | 單一專案在爬蟲擷取器數量成長後耦合度高、測試難隔離 |
| 物件評分多因子演算法 | 使用者明確要求據以產生 Top 5 推播 | 純排序（僅依單價）無法反映屋齡／車位／地段綜合價值，不滿足需求 |
| 引入 Playwright（除 Html 解析外） | 部分來源為 SPA，靜態解析取不到資料 | 純 HTTP 抓取對 JS 渲染頁面無效；惟僅在必要來源啟用以控制複雜度 |
