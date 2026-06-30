# Design: 台灣房屋售屋爬蟲（TwHouseScraper）

**Date**: 2026-06-30
**Feature**: 新增台灣房屋（twhg.com.tw）買屋物件爬蟲
**Approved by**: User

---

## 1. 背景與目標

HouseLens 已有 6 個來源爬蟲（591、信義、永慶、住商、樂屋、中信），`SourceSite.TwHouse` 列舉已存在但尚無實作。本設計補上台灣房屋爬蟲，對齊 SeedData 八個追蹤行政區，完整收錄車位、樓層等評分所需欄位。

---

## 2. robots.txt 合規確認

台灣房屋 `robots.txt`（https://www.twhg.com.tw/robots.txt）：

```
Disallow: /api/
Disallow: /sale/
```

**售屋列表位於 `/buy/list/...`，未被封鎖**，可合法抓取。本爬蟲不存取任何被 Disallow 的路徑，完全符合憲章「尊重 robots.txt」原則。

---

## 3. 技術選型

| 決策 | 選擇 | 理由 |
|------|------|------|
| 抓取器 | `HttpFetcher` | 列表頁為 SSR HTML（物件卡片直接內嵌），無 Cloudflare 保護 |
| HTML 解析 | HtmlAgilityPack | 既有依賴，與信義爬蟲一致 |
| Playwright | 不使用 | 頁面不需 JS 渲染，避免引入不必要複雜度 |

---

## 4. 抓取策略：兩階段

### 第一階段：列表頁（分頁）

**URL 模式**：
```
GET https://www.twhg.com.tw/buy/list/{city-slug}/{zip}-zip/{price-scope}/recomended-desc?page={N}
```

- 分頁：每頁約 30 筆，上限 `MaxPagesPerDistrict = 5`（控制每區抓取量）
- 每區依序處理，中間有 `HttpFetcher` 3 秒節流

**列表頁可取欄位**：標題、城市/區、建坪（AreaPing）、屋齡（AgeYears）、格局（參考用）、總價、物件 URL（`/buy/{code}`）、物件圖片

### 第二階段：詳情頁補齊

**URL 模式**：
```
GET https://www.twhg.com.tw/buy/{code}
```

**補抓欄位**：
- 車位（HasParking）
- 樓層（Floor，如「5F / 7F」）
- 完整地址（Address）

**失敗容錯**：詳情頁抓取失敗 → 該筆退回列表階段資料（HasParking=false、Floor=null、Address=null），記 Warning log，不中斷整體流程。

---

## 5. 行政區 → 城市 slug / zip 對照（SeedData 八區）

| 行政區 | 城市 | city-slug | zip |
|--------|------|-----------|-----|
| 中和區 | 新北市 | newtaipei-city | 235 |
| 永和區 | 新北市 | newtaipei-city | 234 |
| 新店區 | 新北市 | newtaipei-city | 231 |
| 板橋區 | 新北市 | newtaipei-city | 220 |
| 樹林區 | 新北市 | newtaipei-city | 238 |
| 新莊區 | 新北市 | newtaipei-city | 242 |
| 中壢區 | 桃園市 | taoyuan-city | 320 |
| 桃園區 | 桃園市 | taoyuan-city | 330 |

（zip 參考信義爬蟲已驗證值，實作時於網站確認台灣房屋城市 slug 實際用法）

---

## 6. 價格篩選策略

台灣房屋列表 URL 使用**預設級距**（`-scope`）而非任意上限。策略如下：

1. 依 `districtMaxPrices[district]` 決定最接近且涵蓋上限的級距 slug（需實作時對照網站確認可用級距）
2. 抓取後執行 **client-side 精確過濾**：`totalPrice > maxWan → 剔除`（同 CtHouseScraper）

---

## 7. 物件過濾規則

| 過濾條件 | 處理方式 |
|----------|----------|
| 非住宅型態（土地/店面/廠辦/停車位） | 依卡片型態標籤剔除 |
| 總價超出上限 | client-side 精確剔除 |
| 重複物件（同一 `SourceListingKey`） | `seen` HashSet 去重 |
| 跨區推薦物件 | 地址正則解析驗證 city/district，不信任查詢參數 |

---

## 8. `PropertyDto` 欄位對應

| PropertyDto 欄位 | 來源 | 備註 |
|-----------------|------|------|
| City | 地址正則解析 | fallback 用查詢參數 |
| District | 地址正則解析 | fallback 用查詢參數 |
| Address | 詳情頁 | 列表頁通常只有路名 |
| AreaPing | 列表頁 | 建坪 |
| Floor | 詳情頁 | 如「5F / 7F」 |
| AgeYears | 列表頁 | 屋齡（整數年） |
| HasParking | 詳情頁 | fallback=false |
| TotalPrice | 列表頁 | 萬 |
| UnitPrice | 計算 | TotalPrice / AreaPing（若 AreaPing > 0） |
| SourceSite | 常數 | `SourceSite.TwHouse` |
| SourceListingKey | 列表頁 | URL 路徑中的 `{code}`（如 `C802401082`） |
| Title | 列表頁 | 物件標題 |
| Url | 列表頁 | `https://www.twhg.com.tw/buy/{code}` |
| PostedDate | 列表頁（若有） | 上架日期，缺則 null |
| ImageUrl | 列表頁 | 第一張圖 |

---

## 9. 效能與節流估算

| 項目 | 估算 |
|------|------|
| 區數 | 8 |
| 每區最大頁數 | 5 |
| 每頁約筆數 | 30 |
| 最大列表請求數 | 8 × 5 = 40 次 |
| 最大物件數 | ~240 筆 |
| 詳情補齊請求數 | ~240 次（每筆 1 次） |
| 節流（3 秒 + 最多 2 秒 jitter） | ~240 × 3~5 秒 ≈ 12~20 分鐘 |

**總耗時估計約 12~20 分鐘**，於 60 分鐘目標內（含其他來源分配）。

---

## 10. 類別設計

```
HouseLens.Infrastructure/Crawling/Scrapers/TwHouseScraper.cs
```

```csharp
public partial class TwHouseScraper(HttpFetcher fetcher, ILogger<TwHouseScraper> logger)
    : ISourceScraper
{
    public SourceSite SourceSite => SourceSite.TwHouse;

    // FetchAsync()         → 分區分頁協調（實作 ISourceScraper 介面）
    // FetchDistrictAsync() → 單區分頁抓取 + 詳情補齊
    // FetchListPageAsync() → GET /buy/list/{city}/{zip}-zip/.../page=N，回傳草稿列表
    // FetchDetailAsync()   → GET /buy/{code}，補齊 Address/Floor/HasParking
    // ParseListPage()      → HtmlAgilityPack 解析列表頁 HTML（供單元測試）
    // ParseDetail()        → HtmlAgilityPack 解析詳情頁 HTML（供單元測試）
}
```

---

## 11. DI 註冊

`Worker/Program.cs` 與 `Api/Program.cs` 各加一行：

```csharp
builder.Services.AddScoped<ISourceScraper, TwHouseScraper>();
```

---

## 12. 單元測試

`HouseLens.UnitTests/Crawling/TwHouseScraperTests.cs`

| 測試案例 | 驗證內容 |
|----------|----------|
| 列表頁解析 | 標題/總價/建坪/屋齡/URL/圖片正確解析 |
| 詳情頁解析 | 車位/樓層/地址正確解析 |
| 住宅型態過濾 | 非住宅（店面/土地）被剔除 |
| 價格上限過濾 | 超過 maxWan 的物件被剔除 |
| SourceListingKey 去重 | 重複物件只保留一筆 |
| 詳情失敗容錯 | 詳情頁 null 時退回列表欄位（HasParking=false, Floor=null） |

以離線 HTML fixture（列表頁 + 詳情頁各一份）執行，不依賴網路。

---

## 13. 不納入範圍

- 地圖頁（`/buy/map/`）：同資料，重複抓取無必要
- 實價登錄（`/price-trend/`）：不同資料集，現有需求未要求
- 租屋（`/rent/`）：系統追蹤售屋，非租屋
