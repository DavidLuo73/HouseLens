# 設計：物件清單的歷史價格圖表與明細

**日期**：2026-06-18
**狀態**：已核可（待寫實作計畫）
**相關**：`specs/001-house-price-tracker/plan.md`

## 目標

在前端物件清單（`PropertyList` → `PropertyCard`）的查詢結果中，讓**每一個物件**都能呈現歷史價格走勢與明細，體驗對齊參考圖（類似 BigGo 價格歷史）：

- 卡片內**常駐迷你走勢圖（sparkline）**。
- 卡片上「看明細」按鈕，點擊後在清單頁**彈出 Modal**：左側較大折線圖＋最高/最低價統計，右側可捲動的歷史明細表。

## 非目標（YAGNI）

- **不做**參考圖中的「最近 90 天」時間範圍下拉選擇器（目前歷史資料點不多，全顯示即可）。
- 不調整清單既有的篩選／排序／分頁邏輯。
- 不新增後端端點（複用既有清單與詳情端點）。

## 現況

- `PropertyDetail.vue`（詳情頁）**已具備**價格趨勢折線圖（ECharts）與歷史明細表，內容已接近參考圖。
- 後端 `GET /api/properties/{id}`（詳情）回傳完整 `priceHistory`；`GET /api/properties`（清單）目前每筆僅取最新 1 筆歷史，回傳的 `Property` **不含** `priceHistory` 陣列。
- 因此本功能主要是前端工作，僅需後端清單投影補上歷史資料。

## 資料策略

清單 API **一次回傳**精簡歷史序列（使用者已選定）。資料量小（單一使用者、每頁最多 20 筆、歷史點數有限），一次載入畫面不閃爍，優於逐卡懶載入。

## 變更內容

### 1. 後端

**`backend/src/HouseLens.Api/Endpoints/PropertiesEndpoints.cs`** — `GetProperties`

在清單項目的 `.Select(...)` 投影中補上 `PriceHistory` 陣列，形狀與 `GetProperty` 一致：

```csharp
PriceHistory = p.PriceHistory
    .OrderByDescending(h => h.CapturedAt)
    .Select(h => new {
        h.CapturedAt,
        h.TotalPrice,
        h.UnitPrice,
        ChangeFlag = h.ChangeFlag.ToString().ToLower(),
        h.ChangePercent,
        h.IsBigDrop
    })
```

`LatestChangeFlag` / `LatestChangePercent` / `LatestIsBigDrop` 等既有欄位維持不變。其餘篩選、排序、分頁不動。

### 2. 前端型別

**`frontend/src/services/api.ts`**

`Property` 介面加上：

```ts
priceHistory: PriceHistoryEntry[]
```

（`PriceHistoryEntry` 型別已存在；`PropertyDetail` 仍 extends `Property`，原本重複宣告的 `priceHistory` 可移除。）

### 3. 前端元件

#### 新增 `PriceHistoryPanel.vue`（共用：左圖右表）
- 把目前散在 `PropertyDetail.vue` 的折線圖 `chartOption` 與歷史表格邏輯抽成單一可複用元件。
- Props：`history: PriceHistoryEntry[]`、`unitPing`（坪數，供顯示）等必要資料。
- 內容：折線圖（含大降價標記）＋最高/最低價統計＋歷史明細表。
- 供 `PriceHistoryModal` 與 `PropertyDetail` 共用，消除重複。

#### 新增 `PriceHistoryModal.vue`
- 對齊參考圖的彈窗：標題列（物件標題）、左側折線圖＋最高/最低價、右側可捲動明細表、底部「查看商品」（開 `listingUrl`）／「關閉」。
- 內部使用 `PriceHistoryPanel`。
- 以 `v-if` 控制顯示；點背景遮罩或「關閉」可關閉。

#### 修改 `PropertyCard.vue`
- 卡片價格區下方新增**迷你 sparkline**：ECharts 折線、無座標軸、無 tooltip、極簡樣式，資料取自 `property.priceHistory`（依時間正序）。
- 歷史點數 < 2 時隱藏 sparkline（或顯示「—」）。
- 新增「看明細」按鈕；點擊 `emit('open-history', property)`。

#### 修改 `PropertyList.vue`
- 維護 Modal 開關狀態與當前選中物件。
- 監聽 `PropertyCard` 的 `open-history` 事件 → 開啟 `PriceHistoryModal`。

#### 修改 `PropertyDetail.vue`
- 改為複用 `PriceHistoryPanel`，行為與外觀不變。

### 4. ECharts 模組註冊

sparkline 與 Modal 皆用 `LineChart`。在各使用元件以 `use([...])` 註冊所需模組（`LineChart`、`CanvasRenderer`，sparkline 視需要 `GridComponent`，Modal 需 `GridComponent`/`TooltipComponent`）。

## 測試

- **後端**（xUnit 整合測試）：`GET /api/properties` 回應的每個 item 含 `priceHistory` 陣列，且內容與該物件歷史一致、依時間倒序。
- **前端**（Vitest + Vue Test Utils）：
  - `PropertyCard`：有 ≥2 筆歷史時顯示 sparkline；不足時隱藏。點「看明細」發出 `open-history` 事件。
  - `PriceHistoryModal`：傳入歷史時渲染圖與表、正確計算最高/最低價；「關閉」可關閉。
  - `PropertyList`：收到 `open-history` 後開啟 Modal。

## 元件邊界

| 元件 | 職責 | 依賴 |
|------|------|------|
| `PriceHistoryPanel` | 呈現折線圖＋統計＋明細表（純呈現） | `priceHistory` props、ECharts |
| `PriceHistoryModal` | 彈窗外殼、開關、查看商品/關閉 | `PriceHistoryPanel` |
| `PropertyCard` | 卡片資訊＋sparkline＋觸發看明細 | `Property`、ECharts |
| `PropertyList` | 清單查詢＋Modal 狀態協調 | `PropertyCard`、`PriceHistoryModal` |
