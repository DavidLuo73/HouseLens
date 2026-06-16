# HouseLens Backend

.NET 10 ASP.NET Core Web API + Quartz.NET Worker，負責每日爬取台灣房仲平台物件、追蹤歷史價格並透過 LINE 推播通知。

## 技術棧

| 層次 | 技術 |
|------|------|
| 語言 / 執行期 | C# 13 / .NET 10 LTS |
| Web API | ASP.NET Core Minimal API |
| ORM / 資料庫 | EF Core 10 + SQLite |
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

預設使用當前目錄的 `HouseLens.db`，可於 `appsettings.json` 調整：

```json
"ConnectionStrings": {
  "Default": "Data Source=HouseLens.db"
}
```

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

> **安全提示**：`.gitignore` 已排除 `appsettings.*.json`、`secrets.json`、`.env*` 與 `*.db` 等敏感檔案，請勿將金鑰直接寫入 `appsettings.json`。

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
# 1. 套用 EF Core Migration（初次或更新後）
dotnet ef database update \
  -p backend/src/HouseLens.Infrastructure \
  -s backend/src/HouseLens.Api

# 2. 啟動 API（前端 /api 代理目標）
dotnet run --project backend/src/HouseLens.Api

# 3. 啟動排程 Worker（另開終端）
dotnet run --project backend/src/HouseLens.Worker
```

## 執行測試

```bash
dotnet test backend
```

執行全部單元測試（`HouseLens.UnitTests`）與整合測試（`HouseLens.IntegrationTests`）。

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
- LINE 密鑰以 User Secrets / 環境變數管理，不進入版本控制
- SQLite 資料庫檔（`*.db`）已加入 `.gitignore`
