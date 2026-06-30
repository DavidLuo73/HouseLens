# TwHouseScraper 實作計畫

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增台灣房屋（twhg.com.tw）售屋爬蟲，對齊 SeedData 八個行政區，透過列表頁＋詳情頁兩階段補齊車位、樓層、地址等評分所需欄位。

**Architecture:** `TwHouseScraper` 實作 `ISourceScraper`，以 `HttpFetcher` 抓 SSR HTML（`/buy/list/{city}/{zip}-zip/recomended-desc?page=N`），HtmlAgilityPack 解析列表卡片取得基本欄位；再逐筆抓 `/buy/{code}` 詳情頁補齊車位／樓層／地址，並以詳情頁的型態欄（`<dt>類型</dt><dd>`）做第二層住宅過濾。

**Tech Stack:** C# 10, .NET 10, HtmlAgilityPack, `HttpFetcher`（已含 robots.txt 檢查、3s 節流、退避重試）, xUnit, FluentAssertions

## Global Constraints

- `SourceSite.TwHouse` 列舉已存在，不得改動 enum 值
- `HttpFetcher` 已內建速率限制（3s + jitter）與 robots.txt 檢查；爬蟲**不得**自行繞過
- `PropertyDto` record 不得修改其定義
- `ISourceScraper.FetchAsync` 方法簽章不得修改
- 不存取 `robots.txt` 的 Disallow 路徑：`/api/`、`/sale/`
- `ParseListings` 與 `ParseDetail` 必須是 `public` 方法以便單元測試
- 測試使用離線 HTML fixture 字串，**不得**真實發送 HTTP 請求
- `partial class` + `[GeneratedRegex]` 以符合 .NET 10 source generator 規範
- DI 須同時於 `Worker/Program.cs` 與 `Api/Program.cs` 各加一行

---

## 夾具 HTML 說明

Task 1 / Task 2 的 fixture HTML 基於網站偵察（WebFetch）所得的**近似結構**。  
**實作前請開瀏覽器確認：**

1. 訪問 `https://www.twhg.com.tw/buy/list/taoyuan-city/330-zip/recomended-desc`
2. DevTools → Inspect → 找到一張 `<li>` 物件卡片 → 右鍵 Copy outerHTML  
3. 訪問其中一個 `/buy/{code}` 詳情頁 → 找到型態/車位/樓層/地址區塊 → Copy outerHTML  
4. 將以下 fixture const 字串**替換**為真實 HTML，再跑測試確認通過  

若 fixture 不符，`ParseListings` 返回空 List，測試失敗「expected 1 but was 0」→ 調整 fixture 與選擇器後重跑。

---

## 檔案清單

| 操作 | 路徑 |
|------|------|
| Create | `backend/src/HouseLens.Infrastructure/Crawling/Scrapers/TwHouseScraper.cs` |
| Create | `backend/tests/HouseLens.UnitTests/Crawling/TwHouseScraperTests.cs` |
| Modify | `backend/src/HouseLens.Worker/Program.cs` |
| Modify | `backend/src/HouseLens.Api/Program.cs` |

---

## Task 1: TwHouseScraper 骨架 + ParseListings（列表頁解析）

**Files:**
- Create: `backend/src/HouseLens.Infrastructure/Crawling/Scrapers/TwHouseScraper.cs`
- Create: `backend/tests/HouseLens.UnitTests/Crawling/TwHouseScraperTests.cs`

**Interfaces:**
- Produces: `public List<PropertyDto> ParseListings(string html, string queryCity, string queryDistrict, decimal maxWan, HashSet<string>? seen = null)`
- Produces: `public SourceSite SourceSite => SourceSite.TwHouse`

---

- [ ] **Step 1: 寫出失敗的 ParseListings 單元測試**

建立 `backend/tests/HouseLens.UnitTests/Crawling/TwHouseScraperTests.cs`：

```csharp
using FluentAssertions;
using HouseLens.Domain.Enums;
using HouseLens.Infrastructure.Crawling.Scrapers;
using Microsoft.Extensions.Logging.Abstractions;

namespace HouseLens.UnitTests.Crawling;

public class TwHouseScraperTests
{
    private readonly TwHouseScraper _scraper = new(null!, NullLogger<TwHouseScraper>.Instance);

    // ── 住宅大樓卡片（新北市中和區，760 萬，屋齡 25.3 年 → 25）
    // TODO: 替換為瀏覽器 DevTools 複製的真實 <li> outerHTML
    private const string ResidentialCard = """
        <li>
          <a href="/buy/A123456789">
            <img src="https://www.twhg.com.tw/photo/A123456789_1.jpg" alt="中和三房美廈A123456789">
            <h3>中和三房美廈A123456789</h3>
            <p>新北市中和區中山路二段</p>
            <p>大樓 3房2廳2衛 25.3年</p>
            <p>建坪 34.56 坪</p>
            <p>760萬</p>
          </a>
        </li>
        """;

    // ── 超出總價上限（1,200 萬 > 800 萬，應被過濾）
    private const string OverPriceCard = """
        <li>
          <a href="/buy/B987654321">
            <h3>中和豪宅B987654321</h3>
            <p>新北市中和區景平路</p>
            <p>大樓 5房3廳 5.0年</p>
            <p>建坪 68.00 坪</p>
            <p>1,200萬</p>
          </a>
        </li>
        """;

    // ── 非住宅（含「辦公室」關鍵字，應被過濾）
    private const string OfficeCard = """
        <li>
          <a href="/buy/C111222333">
            <h3>中和辦公室C111222333</h3>
            <p>新北市中和區中正路</p>
            <p>辦公室 3間 10.5年</p>
            <p>建坪 45.00 坪</p>
            <p>600萬</p>
          </a>
        </li>
        """;

    // ── 含千位逗號的價格（「1,180萬」格式）
    private const string CommaPrice = """
        <li>
          <a href="/buy/D444555666">
            <h3>桃園三房D444555666</h3>
            <p>桃園市桃園區三民路二段</p>
            <p>大樓 3房2廳2衛 32.1年</p>
            <p>建坪 40.55 坪</p>
            <p>1,180萬</p>
          </a>
        </li>
        """;

    private static string WrapInPage(params string[] cards) =>
        $"<html><body><ul>{string.Join("", cards)}</ul></body></html>";

    private static readonly IReadOnlyDictionary<string, decimal> DefaultPrices =
        new Dictionary<string, decimal>
        {
            ["中和區"] = 800m,
            ["桃園區"] = 1300m,
        };

    [Fact]
    public void ParseListings_ResidentialCard_ParsedCorrectly()
    {
        var html = WrapInPage(ResidentialCard);

        var results = _scraper.ParseListings(html, "新北市", "中和區", 800m);

        results.Should().HaveCount(1);
        var dto = results[0];
        dto.SourceSite.Should().Be(SourceSite.TwHouse);
        dto.SourceListingKey.Should().Be("A123456789");
        dto.Url.Should().Be("https://www.twhg.com.tw/buy/A123456789");
        dto.City.Should().Be("新北市");
        dto.District.Should().Be("中和區");
        dto.Title.Should().NotContain("A123456789"); // 代碼後綴已被移除
        dto.TotalPrice.Should().Be(760m);
        dto.AreaPing.Should().Be(34.56m);
        dto.AgeYears.Should().Be(25); // Math.Round(25.3) = 25
        dto.UnitPrice.Should().BeApproximately(760m / 34.56m, 0.01m);
        dto.HasParking.Should().BeFalse(); // 列表頁無法確定車位
        dto.Floor.Should().BeNull();       // 列表頁無樓層，由詳情頁補齊
        dto.Address.Should().BeNull();     // 列表頁無完整地址，由詳情頁補齊
    }

    [Fact]
    public void ParseListings_OverPriceCard_IsFiltered()
    {
        var html = WrapInPage(OverPriceCard);

        var results = _scraper.ParseListings(html, "新北市", "中和區", 800m);

        results.Should().BeEmpty();
    }

    [Fact]
    public void ParseListings_OfficeCard_IsFiltered()
    {
        var html = WrapInPage(OfficeCard);

        var results = _scraper.ParseListings(html, "新北市", "中和區", 800m);

        results.Should().BeEmpty();
    }

    [Fact]
    public void ParseListings_CommaPriceCard_ParsedCorrectly()
    {
        var html = WrapInPage(CommaPrice);

        var results = _scraper.ParseListings(html, "桃園市", "桃園區", 1300m);

        results.Should().HaveCount(1);
        results[0].TotalPrice.Should().Be(1180m);
        results[0].AgeYears.Should().Be(32); // Math.Round(32.1)
        results[0].AreaPing.Should().Be(40.55m);
    }

    [Fact]
    public void ParseListings_DuplicateKey_DeduplicatedViaSeen()
    {
        var html = WrapInPage(ResidentialCard, ResidentialCard);
        var seen = new HashSet<string>();

        var results = _scraper.ParseListings(html, "新北市", "中和區", 800m, seen);

        results.Should().HaveCount(1);
        seen.Should().Contain("A123456789");
    }

    [Fact]
    public void ParseListings_MixedCards_OnlyValidResidential()
    {
        var html = WrapInPage(ResidentialCard, OverPriceCard, OfficeCard);

        var results = _scraper.ParseListings(html, "新北市", "中和區", 800m);

        results.Should().HaveCount(1);
        results[0].SourceListingKey.Should().Be("A123456789");
    }

    [Fact]
    public void ParseListings_EmptyHtml_ReturnsEmpty()
    {
        var results = _scraper.ParseListings("<html><body></body></html>", "新北市", "中和區", 800m);

        results.Should().BeEmpty();
    }

    [Fact]
    public void ParseListings_UnitPrice_CalculatedFromAreaWhenPresent()
    {
        var html = WrapInPage(ResidentialCard);

        var results = _scraper.ParseListings(html, "新北市", "中和區", 800m);

        results.Should().HaveCount(1);
        // 760 / 34.56 ≈ 22.00
        results[0].UnitPrice.Should().BeApproximately(760m / 34.56m, 0.01m);
    }
}
```

- [ ] **Step 2: 確認測試失敗（類別不存在）**

```
dotnet test backend/tests/HouseLens.UnitTests --filter "FullyQualifiedName~TwHouseScraperTests" -v minimal
```

預期：**編譯失敗**，`TwHouseScraper` 型別未定義。

- [ ] **Step 3: 建立 TwHouseScraper 骨架，實作 ParseListings**

建立 `backend/src/HouseLens.Infrastructure/Crawling/Scrapers/TwHouseScraper.cs`：

```csharp
using HouseLens.Application.Crawling;
using HouseLens.Domain.Enums;
using HtmlAgilityPack;
using Microsoft.Extensions.Logging;
using System.Text.RegularExpressions;

namespace HouseLens.Infrastructure.Crawling.Scrapers;

/// <summary>
/// 台灣房屋（twhg.com.tw）買屋爬蟲。
/// 列表頁 /buy/list/{city}/{zip}-zip/ 為 SSR HTML，以 HttpFetcher + HtmlAgilityPack 解析。
/// robots.txt：/buy/ 路徑未被 Disallow；/api/ 與 /sale/ 被封但本爬蟲不使用。
/// 兩階段：(1) 列表頁取標題/坪/屋齡/總價；(2) 詳情頁補車位/樓層/地址。
/// </summary>
public partial class TwHouseScraper(HttpFetcher fetcher, ILogger<TwHouseScraper> logger) : ISourceScraper
{
    public SourceSite SourceSite => SourceSite.TwHouse;

    private const string BaseUrl = "https://www.twhg.com.tw";
    private const int MaxPagesPerDistrict = 5;

    private static readonly HashSet<string> ResidentialTypes = new(StringComparer.Ordinal)
    {
        "公寓", "大樓", "華廈", "透天厝", "透天/別墅", "別墅",
    };

    // 標題或卡片文字含以下關鍵字時直接略過，不需抓詳情頁
    private static readonly HashSet<string> NonResidentialKeywords = new(StringComparer.Ordinal)
    {
        "辦公室", "店面", "廠房", "廠辦", "倉庫", "工廠",
    };

    private sealed record DistrictInfo(string City, string CitySlug, string Zip);

    private static readonly Dictionary<string, DistrictInfo> DistrictMap = new()
    {
        ["中和區"] = new("新北市", "newtaipei-city", "235"),
        ["永和區"] = new("新北市", "newtaipei-city", "234"),
        ["新店區"] = new("新北市", "newtaipei-city", "231"),
        ["板橋區"] = new("新北市", "newtaipei-city", "220"),
        ["樹林區"] = new("新北市", "newtaipei-city", "238"),
        ["新莊區"] = new("新北市", "newtaipei-city", "242"),
        ["中壢區"] = new("桃園市", "taoyuan-city", "320"),
        ["桃園區"] = new("桃園市", "taoyuan-city", "330"),
    };

    public Task<IReadOnlyList<PropertyDto>> FetchAsync(
        IReadOnlyDictionary<string, decimal> districtMaxPrices,
        IProgress<ScraperDistrictProgress>? progress,
        CancellationToken cancellationToken = default)
        => throw new NotImplementedException("Task 3 will implement FetchAsync");

    /// <summary>
    /// 解析台灣房屋列表頁 HTML，回傳住宅物件草稿（Floor/Address/HasParking 待詳情頁補齊）。
    /// </summary>
    public List<PropertyDto> ParseListings(
        string html,
        string queryCity,
        string queryDistrict,
        decimal maxWan,
        HashSet<string>? seen = null)
    {
        var results = new List<PropertyDto>();
        var doc = new HtmlDocument();
        doc.LoadHtml(html);

        // 尋找 href 含 /buy/ 的連結，由 ListingCodeRegex 二次驗證為物件代碼格式
        var anchors = doc.DocumentNode.SelectNodes("//a[contains(@href,'/buy/')]");
        if (anchors is null)
        {
            logger.LogWarning("TwHouse: no /buy/ anchors found (html len={Len}) for {District}",
                html.Length, queryDistrict);
            return results;
        }

        foreach (var anchor in anchors)
        {
            try
            {
                var dto = ParseCard(anchor, queryCity, queryDistrict, maxWan);
                if (dto is null) continue;
                if (seen is not null && !seen.Add(dto.SourceListingKey)) continue;
                results.Add(dto);
            }
            catch (Exception ex)
            {
                logger.LogDebug("TwHouse: failed to parse card: {Msg}", ex.Message);
            }
        }

        return results;
    }

    private PropertyDto? ParseCard(HtmlNode anchor, string queryCity, string queryDistrict, decimal maxWan)
    {
        var href = anchor.GetAttributeValue("href", "");
        var codeMatch = ListingCodeRegex().Match(href);
        if (!codeMatch.Success) return null;
        var code = codeMatch.Groups[1].Value;
        var url = $"{BaseUrl}/buy/{code}";

        var text = HtmlEntity.DeEntitize(anchor.InnerText);

        // 快速非住宅過濾（避免為辦公室/店面等發出詳情頁請求）
        if (NonResidentialKeywords.Any(kw => text.Contains(kw, StringComparison.Ordinal)))
            return null;

        // 總價（萬），去除千位逗號
        var priceMatch = PriceRegex().Match(text);
        if (!priceMatch.Success) return null;
        if (!decimal.TryParse(priceMatch.Groups[1].Value.Replace(",", ""), out var totalPrice)
            || totalPrice <= 0)
            return null;
        if (maxWan > 0 && totalPrice > maxWan) return null;

        // 建坪
        decimal areaPing = 0m;
        var areaMatch = AreaRegex().Match(text);
        if (areaMatch.Success) decimal.TryParse(areaMatch.Groups[1].Value, out areaPing);

        // 屋齡（取整數，四捨五入）
        int? ageYears = null;
        var ageMatch = AgeRegex().Match(text);
        if (ageMatch.Success && decimal.TryParse(ageMatch.Groups[1].Value, out var ageDec))
            ageYears = (int)Math.Round(ageDec);

        // 地址 → 解析 city/district，fallback 用查詢參數
        string city = queryCity, district = queryDistrict;
        var addrMatch = AddressRegex().Match(text);
        if (addrMatch.Success)
        {
            city = addrMatch.Groups[1].Value;
            district = addrMatch.Groups[2].Value;
        }

        // 標題（優先 h3，移除物件代碼後綴如 "A123456789"）
        var titleNode = anchor.SelectSingleNode(".//h3") ?? anchor;
        var rawTitle = HtmlEntity.DeEntitize(titleNode.InnerText).Trim();
        var title = CodeSuffixRegex().Replace(rawTitle, "").Trim();
        if (string.IsNullOrWhiteSpace(title)) title = district;

        // 圖片（第一個 img src）
        string? imageUrl = null;
        var img = anchor.SelectSingleNode(".//img[@src]");
        if (img is not null)
        {
            var src = img.GetAttributeValue("src", "");
            if (!string.IsNullOrEmpty(src)) imageUrl = src;
        }

        decimal? unitPrice = areaPing > 0 ? Math.Round(totalPrice / areaPing, 2) : null;

        return new PropertyDto(
            City: city,
            District: district,
            Address: null,      // 詳情頁補齊
            AreaPing: areaPing,
            Floor: null,        // 詳情頁補齊
            AgeYears: ageYears,
            HasParking: false,  // 詳情頁補齊
            TotalPrice: totalPrice,
            UnitPrice: unitPrice,
            SourceSite: SourceSite.TwHouse,
            SourceListingKey: code,
            Title: title,
            Url: url,
            PostedDate: null,
            ImageUrl: imageUrl);
    }

    // 僅匹配 /buy/{大寫英數代碼}（如 /buy/A123456789），不匹配 /buy/list/、/buy/map/ 等
    [GeneratedRegex(@"/buy/([A-Z][A-Z0-9]+)$")]
    private static partial Regex ListingCodeRegex();

    // 格式：「760萬」或「1,180萬」，捕獲數字部分（含逗號）
    [GeneratedRegex(@"([\d,]+)\s*萬")]
    private static partial Regex PriceRegex();

    // 格式：「建坪 34.56 坪」或「34.56坪」
    [GeneratedRegex(@"建坪\s*([\d.]+)\s*坪")]
    private static partial Regex AreaRegex();

    // 格式：「25.3年」，捕獲數值部分
    [GeneratedRegex(@"([\d.]+)\s*年")]
    private static partial Regex AgeRegex();

    // 格式：「新北市中和區中山路」→ group1=新北市, group2=中和區
    [GeneratedRegex(@"([一-龥]+[市縣])([一-龥]+[區鎮市鄉])")]
    private static partial Regex AddressRegex();

    // 移除標題末尾的物件代碼後綴，如「中和三房美廈A123456789」→「中和三房美廈」
    [GeneratedRegex(@"[A-Z][A-Z0-9]{5,}$")]
    private static partial Regex CodeSuffixRegex();
}
```

- [ ] **Step 4: 執行測試確認通過**

```
dotnet test backend/tests/HouseLens.UnitTests --filter "FullyQualifiedName~TwHouseScraperTests" -v minimal
```

預期：**全部通過**。若失敗「expected 1 but was 0」→ 依夾具 HTML 說明節替換 fixture 字串並調整選擇器後重跑。

- [ ] **Step 5: Commit**

```
git add backend/src/HouseLens.Infrastructure/Crawling/Scrapers/TwHouseScraper.cs
git add backend/tests/HouseLens.UnitTests/Crawling/TwHouseScraperTests.cs
git commit -m "feat(scraper): 新增 TwHouseScraper 骨架與 ParseListings（列表頁解析）"
```

---

## Task 2: ParseDetail（詳情頁補齊）

**Files:**
- Modify: `backend/src/HouseLens.Infrastructure/Crawling/Scrapers/TwHouseScraper.cs` （新增 ParseDetail 及輔助方法）
- Modify: `backend/tests/HouseLens.UnitTests/Crawling/TwHouseScraperTests.cs` （新增詳情頁測試）

**Interfaces:**
- Consumes: Task 1 的 `TwHouseScraper`
- Produces: `public (string? Address, string? Floor, bool HasParking, string? PropertyType) ParseDetail(string html)`

---

- [ ] **Step 1: 新增 ParseDetail 失敗測試**

在 `TwHouseScraperTests.cs` 的類別尾端（結尾 `}` 前）新增：

```csharp
// ─── ParseDetail 測試 ───────────────────────────────────────────────────────

// 詳情頁 HTML：含車位、5/7樓、完整地址、大樓型態
// TODO: 替換為瀏覽器 DevTools 複製的真實詳情頁 <dl> 區塊 HTML
private const string DetailWithParking = """
    <html><body>
      <dl>
        <dt>地址</dt><dd>桃園市桃園區三民路二段123號 查看地圖</dd>
        <dt>樓層</dt><dd>5/7樓</dd>
        <dt>車位</dt><dd>含車位</dd>
        <dt>類型</dt><dd>大樓</dd>
      </dl>
    </body></html>
    """;

// 詳情頁 HTML：無車位、透天型態
private const string DetailNoParking = """
    <html><body>
      <dl>
        <dt>地址</dt><dd>新北市中和區中山路二段45號</dd>
        <dt>樓層</dt><dd>3/3樓</dd>
        <dt>車位</dt><dd>無車位</dd>
        <dt>類型</dt><dd>透天厝</dd>
      </dl>
    </body></html>
    """;

// 詳情頁 HTML：非住宅（辦公室型態）
private const string DetailOfficeType = """
    <html><body>
      <dl>
        <dt>地址</dt><dd>新北市中和區中正路10號</dd>
        <dt>樓層</dt><dd>6/10樓</dd>
        <dt>車位</dt><dd>無車位</dd>
        <dt>類型</dt><dd>辦公室</dd>
      </dl>
    </body></html>
    """;

// 詳情頁 HTML：空頁（dt/dd 不存在）
private const string DetailEmpty = "<html><body><div>找不到物件</div></body></html>";

[Fact]
public void ParseDetail_WithParking_ParsedCorrectly()
{
    var (address, floor, hasParking, propertyType) = _scraper.ParseDetail(DetailWithParking);

    address.Should().Be("桃園市桃園區三民路二段123號");
    floor.Should().Be("5/7樓");
    hasParking.Should().BeTrue();
    propertyType.Should().Be("大樓");
}

[Fact]
public void ParseDetail_NoParking_HasParkingFalse()
{
    var (_, _, hasParking, propertyType) = _scraper.ParseDetail(DetailNoParking);

    hasParking.Should().BeFalse();
    propertyType.Should().Be("透天厝");
}

[Fact]
public void ParseDetail_AddressStripsMapLink()
{
    // "桃園市桃園區三民路二段123號 查看地圖" → 應移除「查看地圖」後綴
    var (address, _, _, _) = _scraper.ParseDetail(DetailWithParking);

    address.Should().NotContain("查看地圖");
    address.Should().Be("桃園市桃園區三民路二段123號");
}

[Fact]
public void ParseDetail_OfficeType_ReturnsPropertyType()
{
    var (_, _, _, propertyType) = _scraper.ParseDetail(DetailOfficeType);

    propertyType.Should().Be("辦公室");
}

[Fact]
public void ParseDetail_EmptyPage_ReturnsNulls()
{
    var (address, floor, hasParking, propertyType) = _scraper.ParseDetail(DetailEmpty);

    address.Should().BeNull();
    floor.Should().BeNull();
    hasParking.Should().BeFalse();
    propertyType.Should().BeNull();
}
```

- [ ] **Step 2: 確認測試失敗（ParseDetail 方法不存在）**

```
dotnet test backend/tests/HouseLens.UnitTests --filter "FullyQualifiedName~TwHouseScraperTests" -v minimal
```

預期：**編譯失敗**，`ParseDetail` 方法未定義。

- [ ] **Step 3: 在 TwHouseScraper.cs 新增 ParseDetail 及 GetDdValue**

在 `CodeSuffixRegex()` 方法**之前**（private methods 區段）插入以下程式碼：

```csharp
/// <summary>
/// 解析台灣房屋詳情頁 HTML，取得補齊資訊。
/// 詳情頁使用 &lt;dl&gt;&lt;dt&gt;標籤&lt;/dt&gt;&lt;dd&gt;值&lt;/dd&gt; 結構。
/// </summary>
public (string? Address, string? Floor, bool HasParking, string? PropertyType) ParseDetail(string html)
{
    var doc = new HtmlDocument();
    doc.LoadHtml(html);

    // 地址：去除「查看地圖」後綴與前後空白
    var rawAddress = GetDdValue(doc, "地址");
    var address = rawAddress?.Replace("查看地圖", "").Trim();
    if (string.IsNullOrWhiteSpace(address)) address = null;

    var floor = GetDdValue(doc, "樓層");
    if (string.IsNullOrWhiteSpace(floor)) floor = null;

    var parkingText = GetDdValue(doc, "車位") ?? "";
    var hasParking = parkingText.Contains("含車位", StringComparison.Ordinal);

    var propertyType = GetDdValue(doc, "類型");
    if (string.IsNullOrWhiteSpace(propertyType)) propertyType = null;

    return (address, floor, hasParking, propertyType);
}

private static string? GetDdValue(HtmlDocument doc, string label) =>
    doc.DocumentNode
        .SelectSingleNode($"//dt[normalize-space(text())='{label}']/following-sibling::dd[1]")
        ?.InnerText.Trim();
```

- [ ] **Step 4: 執行測試確認全部通過**

```
dotnet test backend/tests/HouseLens.UnitTests --filter "FullyQualifiedName~TwHouseScraperTests" -v minimal
```

預期：**所有 ParseListings + ParseDetail 測試通過**（共 11 個 tests）。

- [ ] **Step 5: Commit**

```
git add backend/src/HouseLens.Infrastructure/Crawling/Scrapers/TwHouseScraper.cs
git add backend/tests/HouseLens.UnitTests/Crawling/TwHouseScraperTests.cs
git commit -m "feat(scraper): 新增 TwHouseScraper ParseDetail（詳情頁車位/樓層/地址解析）"
```

---

## Task 3: FetchAsync（網路層協調）+ DI 註冊

**Files:**
- Modify: `backend/src/HouseLens.Infrastructure/Crawling/Scrapers/TwHouseScraper.cs`（實作 FetchAsync + FetchDistrictAsync）
- Modify: `backend/src/HouseLens.Worker/Program.cs`
- Modify: `backend/src/HouseLens.Api/Program.cs`

**Interfaces:**
- Consumes: `ParseListings(...)` 與 `ParseDetail(...)` (Task 1, 2)
- Consumes: `HttpFetcher.FetchAsync(url, ct)` → `string?`
- Consumes: `IProgress<ScraperDistrictProgress>` 的 Report 模式（見 CtHouseScraper.FetchAsync）
- Produces: `ISourceScraper.FetchAsync(districtMaxPrices, progress, ct)` → `IReadOnlyList<PropertyDto>`

---

- [ ] **Step 1: 實作 FetchAsync 與 FetchDistrictAsync**

將 `TwHouseScraper.cs` 中的 `FetchAsync` 替換為：

```csharp
public async Task<IReadOnlyList<PropertyDto>> FetchAsync(
    IReadOnlyDictionary<string, decimal> districtMaxPrices,
    IProgress<ScraperDistrictProgress>? progress,
    CancellationToken cancellationToken = default)
{
    var results = new List<PropertyDto>();
    var seen = new HashSet<string>();

    var knownDistricts = districtMaxPrices.Keys
        .Where(d => DistrictMap.ContainsKey(d))
        .ToList();

    foreach (var d in districtMaxPrices.Keys.Except(knownDistricts))
        logger.LogWarning("TwHouse: unknown district (not in DistrictMap): {District}", d);

    var total = knownDistricts.Count;

    for (var i = 0; i < knownDistricts.Count; i++)
    {
        var district = knownDistricts[i];
        var maxWan = districtMaxPrices[district];
        var info = DistrictMap[district];

        progress?.Report(new(district, i, total, IsStarting: true, FetchedCount: 0));
        logger.LogInformation("TwHouse: scraping {District} (max={Max}萬)", district, maxWan);

        var districtResults = await FetchDistrictAsync(
            info.City, district, info.CitySlug, info.Zip, maxWan, seen, cancellationToken);
        results.AddRange(districtResults);

        progress?.Report(new(district, i, total, IsStarting: false, FetchedCount: districtResults.Count));
        logger.LogInformation("TwHouse: {District} done, {Count} listings", district, districtResults.Count);
    }

    return results;
}

private async Task<List<PropertyDto>> FetchDistrictAsync(
    string city,
    string district,
    string citySlug,
    string zip,
    decimal maxWan,
    HashSet<string> seen,
    CancellationToken ct)
{
    // ── 第一階段：列表頁分頁抓取 ──────────────────────────────────────────
    var drafts = new List<PropertyDto>();

    for (var page = 1; page <= MaxPagesPerDistrict; page++)
    {
        var url = $"{BaseUrl}/buy/list/{citySlug}/{zip}-zip/recomended-desc?page={page}";
        var html = await fetcher.FetchAsync(url, ct);
        if (html is null)
        {
            logger.LogWarning("TwHouse: failed to fetch list {Url}", url);
            break;
        }

        var pageResults = ParseListings(html, city, district, maxWan, seen);
        logger.LogInformation("TwHouse: {District} list page {Page}: {Count} listings",
            district, page, pageResults.Count);

        drafts.AddRange(pageResults);
        if (pageResults.Count == 0) break; // 空頁 = 最後一頁
    }

    // ── 第二階段：詳情頁補齊（Address / Floor / HasParking / 型態驗證）──
    var completed = new List<PropertyDto>();

    foreach (var draft in drafts)
    {
        var detailHtml = await fetcher.FetchAsync(draft.Url, ct);
        if (detailHtml is null)
        {
            logger.LogWarning("TwHouse: failed to fetch detail {Url}", draft.Url);
            completed.Add(draft); // 退回列表階段資料（HasParking=false, Floor=null）
            continue;
        }

        var (address, floor, hasParking, propertyType) = ParseDetail(detailHtml);

        // 詳情頁型態確認：若非住宅型態則丟棄（列表頁關鍵字過濾無法捕捉全部）
        if (propertyType is not null && !ResidentialTypes.Contains(propertyType))
        {
            logger.LogDebug("TwHouse: filtered non-residential type '{Type}' for {Key}",
                propertyType, draft.SourceListingKey);
            continue;
        }

        completed.Add(draft with
        {
            Address = address ?? draft.Address,
            Floor = floor ?? draft.Floor,
            HasParking = hasParking,
        });
    }

    return completed;
}
```

- [ ] **Step 2: 確認 Infrastructure 專案可建置**

```
dotnet build backend/src/HouseLens.Infrastructure
```

預期：**0 errors**。

- [ ] **Step 3: 在 Worker/Program.cs 新增 DI 註冊**

在 `backend/src/HouseLens.Worker/Program.cs` 的 `CtHouseScraper` 行之後新增：

```csharp
builder.Services.AddScoped<ISourceScraper, TwHouseScraper>();
```

完整區段（修改後）：

```csharp
builder.Services.AddScoped<ISourceScraper, CtHouseScraper>();
builder.Services.AddScoped<ISourceScraper, TwHouseScraper>(); // ← 新增
builder.Services.AddScoped<CrawlOrchestrator>();
```

- [ ] **Step 4: 在 Api/Program.cs 新增 DI 註冊**

在 `backend/src/HouseLens.Api/Program.cs` 的 `CtHouseScraper` 行之後新增：

```csharp
builder.Services.AddScoped<ISourceScraper, TwHouseScraper>();
```

完整區段（修改後）：

```csharp
// RakuyaScraper 暫時停用（樂屋抓取問題待修）
// builder.Services.AddScoped<ISourceScraper, RakuyaScraper>();
builder.Services.AddScoped<ISourceScraper, CtHouseScraper>();
builder.Services.AddScoped<ISourceScraper, TwHouseScraper>(); // ← 新增
builder.Services.AddScoped<CrawlOrchestrator>();
```

- [ ] **Step 5: 確認全方案可建置**

```
dotnet build backend
```

預期：**0 errors, 0 warnings**（若有 warning 請確認非型別問題）。

- [ ] **Step 6: 跑全套單元測試確認未破壞現有測試**

```
dotnet test backend/tests/HouseLens.UnitTests -v minimal
```

預期：**所有測試通過**（含既有 CtHouseScraperTests、HBHousingScraperTests 等）。

- [ ] **Step 7: Commit**

```
git add backend/src/HouseLens.Infrastructure/Crawling/Scrapers/TwHouseScraper.cs
git add backend/src/HouseLens.Worker/Program.cs
git add backend/src/HouseLens.Api/Program.cs
git commit -m "feat(scraper): 完成 TwHouseScraper FetchAsync 與 DI 註冊"
```

---

## 完成驗收標準

| 項目 | 驗證方式 |
|------|----------|
| `SourceSite.TwHouse` 對應到爬蟲 | `dotnet test` 通過，DI 可解析 |
| ParseListings 正確解析標題/坪/屋齡/總價 | `TwHouseScraperTests` 通過 |
| ParseListings 過濾：超價、辦公室關鍵字 | 相應 test 通過 |
| ParseListings 去重（seen HashSet） | `DeduplicatedViaSeen` test 通過 |
| ParseDetail 正確解析車位/樓層/地址/型態 | 相應 test 通過 |
| ParseDetail 移除地址的「查看地圖」後綴 | `AddressStripsMapLink` test 通過 |
| FetchAsync 兩階段抓取可建置無錯誤 | `dotnet build backend` 0 errors |
| Worker 與 Api 均已 DI 註冊 | 兩個 Program.cs 各含 `TwHouseScraper` 行 |
| 現有所有單元測試未被破壞 | `dotnet test backend/tests/HouseLens.UnitTests` 全通過 |
