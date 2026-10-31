# reset-data 工具

檢視與維護 Supabase PostgreSQL 的 HouseLens 資料。執行寫入操作前，先停止 API 與 Worker。

## 設定

只讀取環境變數 `ConnectionStrings__Default`，不接受命令列密碼或 SQLite 路徑。
若已在共用的本機設定檔設定連線，可在專案根目錄載入：

```powershell
$env:ConnectionStrings__Default = (Get-Content -Raw backend/appsettings.Local.json | ConvertFrom-Json).ConnectionStrings.Default
```

連線使用 Npgsql 格式，例如 `Host=...;Port=5432;Database=postgres;Username=postgres.PROJECT_REF;Password=...;Search Path=houselens;SSL Mode=VerifyFull;Root Certificate=C:/certs/prod-ca-2021.crt`。
未指定 `Search Path` 時使用 `houselens`。工具不建立資料表，請先啟動 API 套用 migration。

## 用途與指令

```powershell
# 列出前 10 筆 Listings 的 Title／Url／ImageUrl（預設模式）
dotnet run --project backend/tools/reset-data -- query

# 檢視前 5 筆永慶圖片 URL
dotnet run --project backend/tools/reset-data -- query-yungching

# 清除遠端物件、歷史、爬取與通知紀錄，保留全部設定
dotnet run --project backend/tools/reset-data -- reset --confirm

# 將永慶 yccdn 圖片 URL 的 width 更新為 1200
dotnet run --project backend/tools/reset-data -- fix-yungching-images --confirm
```

兩種寫入模式都必須明確加上 `--confirm`，並以交易執行；失敗時整筆回滾。
`reset` 刪除 PriceHistoryEntries、PropertyScores、NotificationLogs、Listings、Properties、SourceRunResults、CrawlRuns。
DistrictConfigs、TrackingCriteria、ScoringConfigs、PlatformFilterConfigs 與 migration 紀錄保留。
工具不會刪除 schema 或原有 SQLite 檔案，也不會將 SQLite 資料匯入 PostgreSQL。

## 本機參數檢查

```powershell
pwsh backend/tools/reset-data/check-cli.ps1
```

檢查無效參數、缺少寫入確認及缺少連線設定時會在連線前失敗；不連線到資料庫。
