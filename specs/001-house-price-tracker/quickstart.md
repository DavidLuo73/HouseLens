# Quickstart 驗證指南: 房屋售價追蹤系統

本指南說明如何在本機建置、執行並驗證系統端到端可運作。實作細節（程式碼、
migration、完整測試）屬 `tasks.md` 與實作階段，本文僅提供可執行的驗證情境。

## 先決條件

- .NET 10 SDK
- Node.js 20+（前端）
- Playwright 瀏覽器（僅當啟用 SPA 來源時）：`pwsh backend/src/HouseLens.Infrastructure/bin/.../playwright.ps1 install chromium`
- LINE 官方帳號的 Channel Access Token（測試推播時需要）

## 設定

1. 後端設定（`backend/src/HouseLens.Api` 與 `HouseLens.Worker`）：
   - SQLite 連線字串（預設 `Data Source=HouseLens.db`）
   - LINE：`LINE__ChannelAccessToken`、`LINE__TargetUserId`（以使用者祕密管理或環境變數，
     **勿入版控**）
   - 排程：`Schedule__DailyCron`（預設 `0 0 6 * * ?` 清晨 6 點）
   - 門檻：`Scoring__BigDropPercent`、`Scoring__BigDropAmount` 等（見 data-model）

2. 前端設定（`frontend`）：API 基底位址（預設 `http://localhost:5xxx/api`）。

## 建置與啟動

```bash
# 後端：套用 migration 並啟動 API
dotnet ef database update -p backend/src/HouseLens.Infrastructure -s backend/src/HouseLens.Api
dotnet run --project backend/src/HouseLens.Api

# 後端：啟動背景排程 Worker（另一終端）
dotnet run --project backend/src/HouseLens.Worker

# 前端
cd frontend && npm install && npm run dev
```

> API 啟動時會自動套用 EF migration 並植入預設設定（地區價格、評分權重），
> 因此首次啟動不需手動跑 `dotnet ef database update`；資料庫檔案預設為
> `backend/src/HouseLens.Api/HouseLens.db`。

## 常用操作指令

以下指令以本機 API（`http://localhost:5000`）為例。Windows 使用 PowerShell，
其餘平台可改用 `curl`（範例並列）。

### 1. 即時觸發爬取

呼叫管理端點 `POST /api/admin/trigger-crawl`，會在背景啟動一次完整抓取流程
（591 各追蹤地區 → 正規化 → 合併 → 評分）。為避免重複執行，若已有爬蟲進行中
會回傳 `409 Conflict`。

```powershell
# PowerShell
Invoke-RestMethod -Method Post -Uri "http://localhost:5000/api/admin/trigger-crawl"
```

```bash
# curl
curl -X POST http://localhost:5000/api/admin/trigger-crawl
```

回應範例：
```json
{ "message": "爬蟲已觸發，請呼叫 GET /api/crawl-runs/latest 查詢進度" }
```

> 觸發為非同步：指令立即返回，實際抓取在背景進行（591 需啟動 Playwright
> 瀏覽器，通常需數十秒至數分鐘）。以「3. 查看爬取結果」輪詢進度。

### 2. 整個重置 DB，讓所有資訊重新抓

最簡單且可重複的方式是刪除 SQLite 資料庫檔，重啟 API 後會自動重建 schema 並
重新植入預設設定（地區價格、評分權重），接著手動觸發一次抓取即可。

```powershell
# PowerShell
# (1) 先停止正在執行的 API（Ctrl+C 該終端，或關閉對應 dotnet 進程）

# (2) 刪除資料庫檔（含 WAL/SHM 暫存檔）
Remove-Item "F:\SourceCode\HouseLens\backend\src\HouseLens.Api\HouseLens.db*" -ErrorAction SilentlyContinue

# (3) 重新啟動 API（啟動時自動 migrate + seed）
dotnet run --project backend/src/HouseLens.Api

# (4) 待 API 就緒後觸發抓取
Invoke-RestMethod -Method Post -Uri "http://localhost:5000/api/admin/trigger-crawl"
```

替代方案（保留資料庫檔、用 EF 工具重建 schema）：
```bash
dotnet ef database drop -f -p backend/src/HouseLens.Infrastructure -s backend/src/HouseLens.Api
dotnet ef database update  -p backend/src/HouseLens.Infrastructure -s backend/src/HouseLens.Api
```

> **重置範圍說明**：刪除 db 檔會清掉「全部」資料（物件、價格歷史、爬取紀錄、
> 以及你在「地區設定」頁手動調整過的價格上限），重啟後設定會還原為程式內建
> 預設值。若只想清掉物件與歷史、**保留**你的地區/評分設定，請改用「2b」。

### 2b. 只清物件與歷史、保留設定（選用）

若不想動到自訂的地區價格與評分設定，可只刪除物件相關資料表。下列指令以
專案內的小工具執行（`backend/tools/reset-data`，未入版控，必要時依此重建）：

```powershell
# 清除物件 / 價格歷史 / 評分 / 通知 / 爬取紀錄，保留 DistrictConfigs、TrackingCriteria、ScoringConfig
dotnet run --project backend/tools/reset-data/reset-data.csproj -- `
  "F:\SourceCode\HouseLens\backend\src\HouseLens.Api\HouseLens.db" reset

# 之後再觸發一次抓取
Invoke-RestMethod -Method Post -Uri "http://localhost:5000/api/admin/trigger-crawl"
```

### 3. 查看爬取結果

**查看最近一次爬取狀態**（`GET /api/crawl-runs/latest`）：
```powershell
# PowerShell
Invoke-RestMethod -Uri "http://localhost:5000/api/crawl-runs/latest"
```
```bash
# curl
curl http://localhost:5000/api/crawl-runs/latest
```

回應重點欄位：
- `status`：`running`（進行中）/ `completed`（完成）/ `failed`（失敗）
- `newCount` / `delistedCount` / `bigDropCount`：本次新增 / 下架 / 大降價筆數
- `sources[]`：各來源（如 `F591`）的 `success`、`fetchedCount`、`errorMessage`

輪詢直到完成（PowerShell）：
```powershell
do {
  Start-Sleep -Seconds 10
  $s = Invoke-RestMethod -Uri "http://localhost:5000/api/crawl-runs/latest"
  Write-Host "status=$($s.status) new=$($s.newCount)"
} while ($s.status -eq "running")
```

**查看抓取到的物件清單**（`GET /api/properties`，支援篩選與排序）：
```powershell
# 全部（預設依評分排序、第一頁 20 筆）
Invoke-RestMethod -Uri "http://localhost:5000/api/properties"

# 篩選：中壢區 + 有車位 + 近期降價
Invoke-RestMethod -Uri "http://localhost:5000/api/properties?district=中壢區&hasParking=true&priceDropped=true"
```
查詢參數：`district`（可重複）、`minPrice`、`maxPrice`、`hasParking`、
`priceDropped`、`status`（`active`/`delisted`）、`sortBy`
（`score`/`unitPrice`/`priceDrop`/`postedDate`）、`page`、`pageSize`。

**查看單一物件詳情與完整價格歷史**（`GET /api/properties/{id}`）：
```powershell
Invoke-RestMethod -Uri "http://localhost:5000/api/properties/{物件Id}"
```
回傳含 `priceHistory[]`（每次抓取的價格、漲跌旗標、是否大降價）與
`sources[]`（各來源連結與圖片）。

> 前端對應頁面：物件清單 `http://localhost:5173/`、區域分析 `/analytics`、
> 大降價總覽 `/big-drop`、地區價格設定 `/config/districts`。

## 驗證情境（對應 spec 成功標準）

### 情境 1：手動觸發一次抓取並記錄（US1 / SC-001）
- 以開發用觸發方式（Worker 啟動時立即跑一次或提供開發端點）執行一次抓取。
- 驗證：`GET /api/crawl-runs/latest` 回傳 `status: completed`，且 `GET /api/properties`
  回傳之物件皆位於八個追蹤區、總價 ≤ 800，每筆含全部必要欄位（缺漏顯示「未提供」）。

### 情境 2：歷史價格與降價標記（US2 / SC-004）
- 連續執行兩個批次，第二次以 fixture 調低某物件價格。
- 驗證：該物件 `GET /api/properties/{id}` 的 `priceHistory` 含兩筆，第二筆
  `changeFlag: decreased`；降幅達門檻時 `isBigDrop: true`。
- 將某物件自來源 fixture 移除並連續兩批次未見 → `status` 轉為 `delisted`，歷史仍保留。

### 情境 3：重複物件合併（US3 / SC-003）
- 提供同一物件在兩個來源（地址/坪數/樓層/屋齡相符）的 fixture。
- 驗證：`GET /api/properties` 中該物件為單一條目，`sources` 含兩個來源連結。

### 情境 4：新物件標記（US4）
- 在既有清單後注入一筆全新物件並抓取。
- 驗證：該物件 `isNew: true`；下一批次後該旗標變為 `false`。

### 情境 5：區域分析與視覺化（US5 / SC-005）
- 在已有資料下開啟前端區域分析頁。
- 驗證：`GET /api/analytics/districts` 回傳各區平均單價、分布與趨勢；前端呈現至少
  一張各區域趨勢圖；資料不足之維度標示 `insufficientData`。

### 情境 6：篩選與車位標示（US6 / SC-002）
- 前端套用「中壢區 + 有車位 + 近期降價」篩選。
- 驗證：結果皆符合條件，有車位物件被明顯標示，且可於 1 分鐘內找到任一物件並
  查看其歷史價格。

### 情境 7：LINE 推播（範圍擴張項）
- 設定有效 Token 後執行一次完整排程流程。
- 驗證：LINE 收到「大降價清單」與「各區域 Top 5」兩則訊息，內容含必要欄位與連結。

## 測試

```bash
dotnet test backend            # 後端單元 + 整合測試（含解析器 HTML fixture、API 端點）
cd frontend && npm run test     # 前端 Vitest
```

## 負責任爬取檢查（憲章 / FR-015）

- 確認各來源擷取器讀取並遵循 `robots.txt`，套用速率限制與退避。
- 確認單一來源失敗不影響其他來源（觀察 `crawl-runs/latest` 的各來源結果）。
