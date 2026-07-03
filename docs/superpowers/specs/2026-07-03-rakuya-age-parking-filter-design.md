# 樂屋網屋齡與停車位篩選 — 設計文件

日期：2026-07-03

## 目標

樂屋網搜尋 URL 支援 `age`（屋齡上限）與 `other`（停車位 PF 平面 / PM 機械）參數，
且兩者為**地區層級**設定：每個縣市地區（DistrictConfig）可各自設定屋齡上限與停車位需求，
動態連動到樂屋網爬蟲 URL。

參考 URL：
`/sell/result?zipcode=235&usecode=1&typecode=R1,R2&price=~1000&size=20~&room=2,3,4,5~&age=~30&other=PF,PM`

## 設計決策

- 屋齡與停車位皆放 `DistrictConfig`（分地區），不放 `PlatformFilterConfig`（使用者確認）。
- 停車位為複選 checkbox：平面（PF）、機械（PM）；都不勾＝不限。
- 屋齡上限為數字輸入，0＝不限。

## 變更內容

### 後端資料模型

- `DistrictConfig` 新增：
  - `MaxAgeYears`（int，預設 0＝不限）→ 樂屋網 `age=~{N}`
  - `ParkingCodes`（string，逗號分隔如 `"PF,PM"`，預設空＝不限）→ 樂屋網 `other={codes}`
- 新增 EF migration。
- `DistrictCriteria` record 加 `MaxAgeYears = 0`、`ParkingCodes = ""` 參數。

### CrawlOrchestrator

- `BuildCriteriaForScraper` 改為接收完整 `DistrictConfig` 清單（非僅 district→maxPrice 字典），
  將每區的 `MaxAgeYears`/`ParkingCodes` 併入 `DistrictCriteria`。
- 舊版 TrackingCriteria fallback 路徑：屋齡/停車位使用預設值（不限）。

### RakuyaScraper

- `BuildSearchUrl` 補上：
  - `MaxAgeYears > 0` 時附加 `&age=~{N}`
  - `ParkingCodes` 非空時附加 `&other={codes}`
- Client-side 防線：解析卡片後套用 `MaxAgeYears` 過濾（同現有價格過濾模式）。
  停車位不做 client-side 硬過濾（卡片 tag 可能漏標，伺服器端已篩）。
- 單元測試補 age/other 的 `BuildSearchUrl` 案例。

### API（ConfigEndpoints）

- `DistrictConfigRequest` 加 `MaxAgeYears`（預設 0）、`ParkingCodes`（預設空）。
- districts GET/POST/PUT 回傳與寫入這兩欄；`MaxAgeYears < 0` 回 400。

### 前端（DistrictPriceConfig.vue、api.ts）

- 新增表單與表格編輯列加：屋齡上限數字欄（0＝不限）、停車位 PF/PM checkbox。
- 表格新增「屋齡上限」「停車位」顯示欄（不限時顯示「不限」）。
- `api.ts` 的 `DistrictConfig` 型別與 payload 同步加 `maxAgeYears`、`parkingCodes`。

## 錯誤處理與相容性

- 既有資料列 migration 後預設 0／空字串，行為等同現在（不限），無破壞性。
- 其他平台爬蟲忽略新欄位（與現有 MinSizePing/Rooms 模式一致）。

## 測試

- `RakuyaScraperTests`：BuildSearchUrl 帶/不帶 age、other 的組合。
- 後端建置 + 既有測試全過；前端 type-check/build 通過。
