# HouseLens Backend

.NET 10 ASP.NET Core Web API + Quartz.NET Worker，負責每日爬取台灣房仲平台物件、追蹤歷史價格並透過 LINE 推播通知。

資料表、欄位與關聯請見 [ER Model 與資料字典](../docs/er-model.md)。

## 技術棧

| 層次 | 技術 |
|------|------|
| 語言 / 執行期 | C# / .NET 10 |
| Web API | ASP.NET Core Minimal API |
| ORM / 資料庫 | EF Core 10.0.9 + Npgsql 10.0.3 + Supabase PostgreSQL |
| 排程 | Quartz.NET |
| 爬蟲 | HtmlAgilityPack / AngleSharp |
| 推播 | LINE Messaging API |
| 測試 | xUnit + FluentAssertions |

## 專案結構

```
backend/
├── src/
│   ├── HouseLens.Domain/          # 實體、列舉、領域規則
│   ├── HouseLens.Application/     # 用例服務（查詢、分析、評分、通知）
│   ├── HouseLens.Infrastructure/  # EF Core、爬蟲擷取器、LINE 用戶端
│   ├── HouseLens.Worker/          # Quartz 排程 Job（CrawlJob）
│   └── HouseLens.Api/             # ASP.NET Core Minimal API
└── tests/
    ├── HouseLens.UnitTests/       # 純邏輯單元測試
    └── HouseLens.IntegrationTests/# API 端點 + EF Core 整合測試
```

## 安裝先決條件

- .NET 10 SDK（[下載](https://dotnet.microsoft.com/download)）
- （可選）Playwright 瀏覽器（當啟用 SPA 來源時）

## 設定

### 1. 資料庫連線字串

API 與 Worker 都使用 `ConnectionStrings:Default`，缺少設定時會停止啟動。
在 `backend/appsettings.Local.json`（已加入忽略規則）設定共用連線：

```json
{
  "ConnectionStrings": {
    "Default": "Host=aws-0-REGION.pooler.supabase.com;Port=5432;Database=postgres;Username=postgres.PROJECT_REF;Password=YOUR_PASSWORD;Search Path=houselens;SSL Mode=VerifyFull;Root Certificate=C:/certs/prod-ca-2021.crt"
  }
}
```

將主機、使用者與密碼換成 Supabase Dashboard「Connect → Session pooler」提供的值。
本專案附有 `backend/certs/supabase-root.crt`；將 `Root Certificate` 指向其絕對路徑。也可從 Database Settings 下載 CA。
保留 `SSL Mode=VerifyFull` 以驗證憑證及主機名稱；不要以停用驗證處理憑證錯誤。
可參考 [Supabase SSL 設定](https://supabase.com/docs/guides/platform/ssl-enforcement)。

環境變數 `ConnectionStrings__Default` 可覆寫本機檔案；`reset-data` 工具只讀取此環境變數。
使用 Npgsql 的 `Host=...;Database=...` 格式，不直接貼入 `postgresql://...` URI。

應用程式資料使用 `houselens` schema，不需要將此 schema 加到 Supabase Data API 的 exposed schemas。
API 與 Worker 首次啟動都會建立 schema、套用 PostgreSQL migration 並植入預設設定；初始化鎖避免同時啟動時重複新增。
兩者必須指向相同的資料庫與 schema，且使用 Session pooler（5432）。
舊的 `HouseLens.db` 保留作為原始資料；新的 PostgreSQL migration 不會讀取或自動搬移 SQLite 資料。

### 2. LINE 推播密鑰（MUST 以本機設定保存，不得入版控）

使用 .NET User Secrets：

```bash
cd backend/src/HouseLens.Api
dotnet user-secrets set "LINE:ChannelAccessToken" "你的_Channel_Access_Token"
dotnet user-secrets set "LINE:TargetUserId" "你的_LINE_User_ID"
```

或設定環境變數（以 `__` 分隔階層）：

```bash
LINE__ChannelAccessToken=你的Token
LINE__TargetUserId=你的UserId
```

> **安全提示**：請把密碼與 Token 放在 `backend/appsettings.Local.json` 或環境變數。
> `appsettings.json` 與 `appsettings.Development.json` 是可提交的設定，不能放入機密。

### 3. 排程 Cron（選用）

預設每日清晨 6:00 觸發（`0 0 6 * * ?` Quartz Cron 格式）：

```json
"Schedule": {
  "DailyCron": "0 0 6 * * ?"
}
```

### 4. 評分門檻（選用）

```json
"Scoring": {
  "BigDropPercent": 0.05,
  "BigDropAmount": 30
}
```

## 建置與執行

```bash
# 1. 啟動 API（建立 schema、套用 migration／seed，提供前端 /api）
dotnet run --project backend/src/HouseLens.Api

# 2. 啟動排程 Worker（另開終端）
dotnet run --project backend/src/HouseLens.Worker
```

## 執行測試

```powershell
# 使用可建立 schema 的 PostgreSQL 測試連線；每個 fixture 自建 schema，結束時移除
$env:HOUSELENS_TEST_CONNECTION = 'Host=...;Database=postgres;Username=...;Password=...;SSL Mode=VerifyFull;Root Certificate=C:/certs/prod-ca-2021.crt'
dotnet test backend --filter "Category!=Live"
```

執行全部單元測試（`HouseLens.UnitTests`）與整合測試（`HouseLens.IntegrationTests`）。
整合測試以真正的 PostgreSQL migration 建立隔離的測試資料表，不使用 SQLite 替身。
只執行純邏輯測試時，不需要資料庫：`dotnet test backend/tests/HouseLens.UnitTests`。

資料檢視、重置與永慶圖片修復指令見 [reset-data 工具](tools/reset-data/README.md)。

## API 端點速覽

| 方法 | 路徑 | 說明 |
|------|------|------|
| GET | `/api/properties` | 物件清單（支援區域、價格、車位、降價篩選與分頁） |
| GET | `/api/properties/{id}` | 物件詳情含完整歷史價格 |
| GET | `/api/crawl-runs/latest` | 最近一次批次狀態 |
| GET | `/api/analytics/districts` | 各區平均單價、分布與趨勢 |
| GET | `/api/analytics/top-properties?type=topRated` | 各區 Top 5 優質物件 |
| GET | `/api/analytics/top-properties?type=bigDrop` | 近期大降價物件總覽 |
| GET | `/api/config` | 追蹤條件與評分設定 |
| PUT | `/api/config` | 更新評分設定 |
| GET | `/health` | 健康檢查 |

## 安全說明

- API CORS 僅允許 `localhost:5173`（開發）與 `localhost:4173`（預覽），不對外公開
- 資料庫密碼與 LINE 密鑰使用忽略的本機設定或環境變數，不進入版本控制
- PostgreSQL 連線驗證 Supabase CA 與主機名稱，前端不持有資料庫憑證
- 原有 SQLite 資料庫檔（`*.db`）維持忽略，不會在切換時刪除
