# reset-data 工具

清理 / 檢視本機 SQLite 資料庫（`HouseLens.db`）的小型命令列工具。

## 用途

- `reset`：清除「物件相關」資料表（PriceHistoryEntries、PropertyScores、
  NotificationLogs、Listings、Properties、SourceRunResults、CrawlRuns），
  **保留**設定資料（DistrictConfigs、TrackingCriteria、ScoringConfig）。
- `query`：列出前 10 筆 Listings 的 Title / Url / ImageUrl，方便驗證抓取結果。

> 若要「完整」重置（連同自訂設定一起清掉），請直接刪除 `HouseLens.db` 檔，
> 重啟 API 時會自動 migrate + seed。詳見
> `specs/001-house-price-tracker/quickstart.md` 的「常用操作指令」。

## 使用方式

```powershell
# 清除物件與歷史、保留設定
dotnet run --project backend/tools/reset-data/reset-data.csproj -- `
  "F:\SourceCode\HouseLens\backend\src\HouseLens.Api\HouseLens.db" reset

# 檢視前 10 筆 Listings（預設模式，省略第二個參數亦可）
dotnet run --project backend/tools/reset-data/reset-data.csproj -- `
  "F:\SourceCode\HouseLens\backend\src\HouseLens.Api\HouseLens.db" query
```

參數：
1. 資料庫檔路徑（預設 `HouseLens.db`）
2. 模式：`query`（預設）或 `reset`
