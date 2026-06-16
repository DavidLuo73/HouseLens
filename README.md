# HouseLens 房屋售價追蹤系統

單一使用者、本機部署的房屋售價追蹤應用。每日定時爬取台灣房仲平台（目前
[591 售屋網](https://sale.591.com.tw/)）符合條件的物件，正規化後寫入 SQLite，
追蹤歷史價格、下架與重複狀態，計算降價偵測與物件評分，並透過 LINE Messaging API
推播「大降價清單」與「各區域 Top 5 優質物件」。前端以 Vue 3 呈現物件清單、篩選、
價格趨勢圖與區域分析。

> 預設追蹤範圍：新北市（中和、永和、新店、板橋、樹林、新莊）與桃園市（中壢、
> 桃園），各地區可於前端「地區設定」頁個別調整總價上限與啟用狀態。

## 功能特色

- **每日自動爬取**：背景 Worker 以 Quartz Cron（預設清晨 6 點）排程抓取
- **歷史價格追蹤**：記錄每次抓取的價格，標記漲跌與「大降價」
- **重複物件合併**：同一物件跨來源（地址／坪數／樓層／屋齡相符）合併為單一條目
- **物件評分**：依單價、屋齡、車位、地段多因子加權評分，產生各區 Top 5
- **下架偵測**：連續未見的物件標記為已下架，歷史仍保留
- **LINE 推播**：每日分析完成後推播大降價清單與各區優質物件
- **前端介面**：物件清單／篩選、物件詳情與價格趨勢圖、區域分析、地區與評分設定

## 技術棧

| 層 | 技術 |
|----|------|
| 後端 | C# / .NET 10、ASP.NET Core Minimal API、EF Core 10（SQLite） |
| 背景排程 | `Microsoft.Extensions.Hosting` + Quartz.NET |
| 爬蟲 | Microsoft.Playwright（SPA 渲染）、HtmlAgilityPack（靜態備援） |
| 通知 | LINE Messaging API |
| 前端 | TypeScript 5 + Vue 3.5、Vite、Pinia、Vue Router、ECharts |
| 測試 | xUnit + FluentAssertions（後端）、Vitest（前端） |

## 專案結構

```text
backend/
├── src/
│   ├── HouseLens.Domain/          # 實體、列舉、領域規則
│   ├── HouseLens.Application/     # 用例服務：去重、降價偵測、評分、查詢
│   ├── HouseLens.Infrastructure/  # EF Core、Migrations、爬蟲擷取器、LINE 用戶端
│   ├── HouseLens.Worker/          # 背景排程（Quartz）每日抓取流程協調
│   └── HouseLens.Api/             # ASP.NET Core Web API
├── tests/
│   ├── HouseLens.UnitTests/       # 去重／降價／評分／解析器單元測試
│   └── HouseLens.IntegrationTests/# API 端點與 EF Core 整合測試
└── tools/
    └── reset-data/                   # 本機 DB 清理／檢視小工具

frontend/
├── src/
│   ├── components/   # 清單、篩選器、價格趨勢圖、區域分析卡片
│   ├── pages/        # 物件清單、詳情、區域分析、Top5／降價總覽、設定
│   ├── services/     # 型別化 API 用戶端
│   └── stores/       # Pinia 狀態（篩選條件、查詢結果）
└── tests/            # Vitest 元件與 store 測試

specs/001-house-price-tracker/   # 規格、計畫、資料模型、API 契約、quickstart
```

## 先決條件

- [.NET 10 SDK](https://dotnet.microsoft.com/)
- [Node.js](https://nodejs.org/) 20+
- Playwright 瀏覽器（首次執行爬蟲前安裝）：
  ```powershell
  pwsh backend/src/HouseLens.Infrastructure/bin/Debug/net10.0/playwright.ps1 install chromium
  ```
- LINE 官方帳號的 Channel Access Token（僅測試推播時需要）

## 設定

機密設定（LINE Token 等）**勿入版控**，請以使用者祕密或環境變數保存。

| 設定鍵 | 說明 | 預設 |
|--------|------|------|
| 連線字串 | SQLite 資料庫位置 | `Data Source=HouseLens.db` |
| `LINE__ChannelAccessToken` | LINE 推播 Token | （無，需自行設定） |
| `LINE__TargetUserId` | 推播目標使用者 ID | （無） |
| `Schedule__DailyCron` | Worker 每日排程 Cron | `0 0 6 * * ?`（清晨 6 點） |

前端 API 基底位址預設指向 `http://localhost:5000`。

## 建置與啟動

```powershell
# 後端 API（啟動時自動套用 migration 並植入預設設定）
dotnet run --project backend/src/HouseLens.Api          # http://localhost:5000

# 背景排程 Worker（另一終端）
dotnet run --project backend/src/HouseLens.Worker

# 前端
cd frontend; npm install; npm run dev                      # http://localhost:5173
```

## 常用操作

詳細步驟與 `curl` 範例見
[`specs/001-house-price-tracker/quickstart.md`](specs/001-house-price-tracker/quickstart.md)。

```powershell
# 即時觸發一次爬取
Invoke-RestMethod -Method Post -Uri "http://localhost:5000/api/admin/trigger-crawl"

# 查看最近一次爬取狀態
Invoke-RestMethod -Uri "http://localhost:5000/api/crawl-runs/latest"

# 查看抓取到的物件（支援篩選與排序）
Invoke-RestMethod -Uri "http://localhost:5000/api/properties"

# 完整重置 DB（清掉所有資料，重啟後自動 migrate + seed）
# 1) 停止 API  2) 刪除 db 檔  3) 重新啟動 API  4) 觸發爬取
Remove-Item backend/src/HouseLens.Api/HouseLens.db* -ErrorAction SilentlyContinue

# 只清物件與歷史、保留地區／評分設定
dotnet run --project backend/tools/reset-data/reset-data.csproj -- `
  backend/src/HouseLens.Api/HouseLens.db reset
```

## API 端點

| 方法 | 路徑 | 說明 |
|------|------|------|
| GET | `/api/properties` | 物件清單（篩選：`district`、`minPrice`、`maxPrice`、`hasParking`、`priceDropped`、`status`；排序：`sortBy`；分頁：`page`、`pageSize`） |
| GET | `/api/properties/{id}` | 單一物件詳情，含價格歷史與來源連結 |
| GET | `/api/analytics/districts` | 各區平均單價、分布與趨勢 |
| GET | `/api/analytics/top-properties` | 各區優質物件 Top N（`type=bigDrop` 取大降價清單） |
| GET | `/api/crawl-runs/latest` | 最近一次爬取狀態與各來源結果 |
| POST | `/api/admin/trigger-crawl` | 即時觸發一次爬取（背景執行，防重複） |
| GET / PUT | `/api/config` | 評分權重與大降價門檻設定 |
| GET | `/api/config/districts` | 列出地區價格設定 |
| POST | `/api/config/districts` | 新增地區 |
| PUT / DELETE | `/api/config/districts/{id}` | 修改／刪除地區 |
| PATCH | `/api/config/districts/{id}/toggle` | 切換地區啟用／停用 |

## 前端頁面

| 路徑 | 頁面 |
|------|------|
| `/` | 物件清單（篩選、排序、縮圖與連結） |
| `/properties/:id` | 物件詳情與價格趨勢圖 |
| `/analytics` | 區域分析 |
| `/top-properties` | 優質排行 |
| `/big-drop` | 大降價總覽 |
| `/config` | 評分設定 |
| `/config/districts` | 地區價格設定 |

## 測試

```powershell
dotnet test backend            # 後端單元 + 整合測試
cd frontend; npm run test      # 前端 Vitest
```

## 負責任爬取

本系統遵循各來源 `robots.txt` 與服務條款，套用速率限制與重試退避；單一來源
失敗不影響其他來源（可於 `GET /api/crawl-runs/latest` 觀察各來源結果）。請勿
移除速率限制或將本工具用於高頻、大量或商業用途。
