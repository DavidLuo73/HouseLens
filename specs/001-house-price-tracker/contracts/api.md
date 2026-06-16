# API Contracts: 房屋售價追蹤系統

後端 ASP.NET Core Web API 對前端 Vue 應用提供之 REST 端點。所有回應為 JSON，
金額單位為「萬元」。基底路徑 `/api`。本契約為介面層級約定，不含實作細節。

## 物件查詢

### GET /api/properties

依條件篩選物件清單（FR-011、FR-012、FR-013）。

Query 參數（皆選用，可組合）：

| 參數 | 型別 | 說明 |
|------|------|------|
| district | string[] | 行政區，多值 |
| minPrice / maxPrice | number | 總價區間（萬元） |
| hasParking | bool | 是否有車位 |
| priceDropped | bool | 僅顯示近期降價物件 |
| status | string | active / delisted（預設 active） |
| sortBy | string | score / unitPrice / priceDrop / postedDate（預設 score 由高到低） |
| page / pageSize | int | 分頁（預設 1 / 20） |

回應 `200`：
```json
{
  "total": 123,
  "page": 1,
  "pageSize": 20,
  "items": [
    {
      "id": "guid",
      "title": "string",
      "city": "新北市",
      "district": "中和區",
      "areaPing": 28.5,
      "floor": "5/12",
      "ageYears": 12,
      "hasParking": true,
      "currentTotalPrice": 780,
      "currentUnitPrice": 27.4,
      "status": "active",
      "score": 0.82,
      "isNew": false,
      "latestChangeFlag": "decreased",
      "latestChangePercent": -6.2,
      "sources": [
        { "sourceSite": "F591", "url": "https://..." }
      ]
    }
  ]
}
```
- 排序預設將有車位物件之評分加成已反映於 `score`；前端另以標記呈現 `hasParking`。

### GET /api/properties/{id}

單一物件詳情與歷史價格（FR-006、FR-013）。

回應 `200`：物件完整欄位 + `priceHistory` 陣列：
```json
{
  "id": "guid",
  "...": "（同清單項目完整欄位）",
  "sources": [ { "sourceSite": "F591", "url": "...", "title": "...", "postedDate": "2026-05-01" } ],
  "priceHistory": [
    { "capturedAt": "2026-06-10T06:00:00", "totalPrice": 820, "unitPrice": 28.8, "changeFlag": "none", "changePercent": null, "isBigDrop": false },
    { "capturedAt": "2026-06-14T06:00:00", "totalPrice": 780, "unitPrice": 27.4, "changeFlag": "decreased", "changePercent": -4.9, "isBigDrop": false }
  ]
}
```
回應 `404`：物件不存在。

## 區域分析

### GET /api/analytics/districts

各區域價格統計，供區域分析頁與圖表（FR-009、FR-010）。

Query：`metric`（avgUnitPrice / priceDistribution / trend，預設全部回傳）。

回應 `200`：
```json
{
  "districts": [
    {
      "district": "中和區",
      "propertyCount": 42,
      "avgUnitPrice": 28.1,
      "minTotalPrice": 520,
      "maxTotalPrice": 800,
      "priceBuckets": [ { "range": "500-600", "count": 8 } ],
      "trend": [ { "date": "2026-06-01", "avgUnitPrice": 28.4 } ]
    }
  ]
}
```
- 某維度資料不足時，對應欄位回傳空陣列並附 `insufficientData: true`（spec User Story 5 AC2）。

### GET /api/analytics/top-properties

各區域 Top N 優質物件與當日大降價清單（對應排程推播內容，供前端總覽頁）。

Query：`type`（topRated / bigDrop，預設 topRated）、`limit`（預設 5）。

回應 `200`（topRated）：
```json
{
  "type": "topRated",
  "byDistrict": [
    { "district": "中壢區", "items": [ { "id": "guid", "title": "...", "totalPrice": 680, "unitPrice": 22.1, "ageYears": 8, "hasParking": true, "score": 0.88 } ] }
  ]
}
```

## 抓取批次狀態

### GET /api/crawl-runs/latest

最近一次抓取批次的彙總與各來源成敗（FR-014，供前端顯示資料新鮮度）。

回應 `200`：
```json
{
  "id": "guid",
  "startedAt": "2026-06-14T06:00:00",
  "finishedAt": "2026-06-14T06:38:00",
  "status": "completed",
  "newCount": 7,
  "delistedCount": 3,
  "bigDropCount": 2,
  "sources": [
    { "sourceSite": "F591", "success": true, "fetchedCount": 120 },
    { "sourceSite": "Sinyi", "success": false, "errorMessage": "timeout" }
  ]
}
```

## 設定（選用）

### GET /api/config / PUT /api/config

讀取與更新 TrackingCriteria 與 ScoringConfig（追蹤地區、總價上限、評分權重、
降價門檻）。權重 PUT 時 MUST 驗證四項合計為 1.0，否則回 `400`。

## 錯誤格式

所有錯誤回應採統一結構：
```json
{ "error": { "code": "string", "message": "string" } }
```
常見狀態碼：`400` 參數錯誤、`404` 資源不存在、`500` 伺服器錯誤。

## 備註

- 本 API 為單一使用者本機應用，初版不含驗證授權（localhost 存取）；
  若日後對外部署，MUST 補上驗證與密鑰保護（plan Constraints）。
- 內部背景排程觸發的爬蟲、LINE 推播不對外開放 HTTP 端點，由 Worker 服務內部執行。
