using HouseLens.Application.Crawling;
using HouseLens.Domain.Enums;
using HtmlAgilityPack;
using Microsoft.Extensions.Logging;
using System.Text.RegularExpressions;

namespace HouseLens.Infrastructure.Crawling.Scrapers;

/// <summary>
/// 信義房屋（sale）爬蟲。信義買屋列表頁為 Next.js 伺服器渲染（SSR），
/// 物件卡片直接內嵌於 HTML，故以 HttpFetcher 取頁面、HtmlAgilityPack 解析即可，
/// 不需 Playwright。__NEXT_DATA__ 僅含全站資料（熱門/新聞/zipcode），不含搜尋結果。
/// </summary>
public partial class SinyiScraper(HttpFetcher fetcher, ILogger<SinyiScraper> logger) : ISourceScraper
{
    public SourceSite SourceSite => SourceSite.Sinyi;

    private const string BaseUrl = "https://www.sinyi.com.tw";
    private const int PageSize = 20;            // 信義列表每頁 20 筆
    private const int MaxPagesPerDistrict = 5;  // 禮貌性上限，避免過量請求

    // 住宅型態過濾（信義官方分組）：公寓 / 透天 / 別墅 / 電梯大樓 / 華廈。
    // 排除「單售車位」「土地」「店面」「廠辦」等非住宅，避免污染房屋追蹤清單。
    private const string ResidentialTypeSlug = "apartment-townhouse-villa-dalou-huaxia-type";

    // 行政區 → (城市英文 slug, 城市中文名, 信義 zip)。zip 與 slug 皆由 __NEXT_DATA__ zipCodeTW 驗證。
    private static readonly Dictionary<string, (string CitySlug, string City, string Zip)> DistrictMap = new()
    {
        ["中和區"] = ("NewTaipei-city", "新北市", "235"),
        ["永和區"] = ("NewTaipei-city", "新北市", "234"),
        ["新店區"] = ("NewTaipei-city", "新北市", "231"),
        ["板橋區"] = ("NewTaipei-city", "新北市", "220"),
        ["土城區"] = ("NewTaipei-city", "新北市", "236"),
        ["樹林區"] = ("NewTaipei-city", "新北市", "238"),
        ["三峽區"] = ("NewTaipei-city", "新北市", "237"),
        ["新莊區"] = ("NewTaipei-city", "新北市", "242"),
        ["中壢區"] = ("Taoyuan-city", "桃園市", "320"),
        ["桃園區"] = ("Taoyuan-city", "桃園市", "330"),
    };

    public async Task<IReadOnlyList<PropertyDto>> FetchAsync(
        IReadOnlyDictionary<string, DistrictCriteria> districtCriteria,
        IProgress<ScraperDistrictProgress>? progress,
        Func<IReadOnlyList<PropertyDto>, Task>? onDistrictCompleted = null,
        CancellationToken cancellationToken = default)
    {
        var results = new List<PropertyDto>();

        if (!await fetcher.CheckRobotsAsync($"{BaseUrl}/buy/list", cancellationToken))
        {
            logger.LogInformation("robots.txt disallows crawling sinyi buy list");
            return results;
        }

        var validDistricts = districtCriteria
            .Where(kv => DistrictMap.ContainsKey(kv.Key))
            .ToList();
        var total = validDistricts.Count;

        for (var i = 0; i < validDistricts.Count; i++)
        {
            var (district, criteria) = validDistricts[i];
            var maxPrice = criteria.MaxTotalPrice;
            var map = DistrictMap[district];

            progress?.Report(new(district, i, total, IsStarting: true, FetchedCount: 0));

            var districtResults = await FetchDistrictAsync(
                district, map.City, map.CitySlug, map.Zip, (int)maxPrice, cancellationToken);
            results.AddRange(districtResults);

            progress?.Report(new(district, i, total, IsStarting: false, FetchedCount: districtResults.Count));
            logger.LogInformation("Sinyi district {District} (max={Max}萬): {Count} listings",
                district, (int)maxPrice, districtResults.Count);

            if (onDistrictCompleted is not null) await onDistrictCompleted(districtResults);
        }

        var unknownDistricts = districtCriteria.Keys.Where(d => !DistrictMap.ContainsKey(d));
        foreach (var d in unknownDistricts)
            logger.LogWarning("Sinyi: unknown district (not in DistrictMap): {District}", d);

        return results;
    }

    private async Task<List<PropertyDto>> FetchDistrictAsync(
        string district, string city, string citySlug, string zip, int maxWan, CancellationToken ct)
    {
        var all = new List<PropertyDto>();

        for (var page = 1; page <= MaxPagesPerDistrict; page++)
        {
            // 例：/buy/list/0-800-price/apartment-townhouse-villa-dalou-huaxia-type/NewTaipei-city/235-zip/index/DESC/1
            var url = $"{BaseUrl}/buy/list/0-{maxWan}-price/{ResidentialTypeSlug}/{citySlug}/{zip}-zip/index/DESC/{page}";

            // HttpFetcher 內含 robots 檢查、速率限制（3s + jitter）與重試退避
            var html = await fetcher.FetchAsync(url, ct);
            if (html is null)
            {
                logger.LogWarning("Sinyi: failed to fetch {Url}", url);
                break;
            }

            var pageResults = ParseListings(html, city, district);
            logger.LogInformation("Sinyi {District} page {Page}: {Count} listings", district, page, pageResults.Count);

            if (pageResults.Count == 0) break;
            all.AddRange(pageResults);

            // 不足一頁代表已是最後一頁
            if (pageResults.Count < PageSize) break;
        }

        return all;
    }

    /// <summary>
    /// 解析信義列表頁 HTML → PropertyDto。
    /// 使用穩定的語意 class（buy-list-item / LongInfoCard_Type_*）與 regex，
    /// 避開 Next.js 帶 hash 後綴的 class（如 __HJdCh，會隨 build 變動）。
    /// </summary>
    public List<PropertyDto> ParseListings(string html, string city, string district)
    {
        var results = new List<PropertyDto>();
        var doc = new HtmlDocument();
        doc.LoadHtml(html);

        var cards = doc.DocumentNode.SelectNodes("//div[contains(@class,'buy-list-item')]");
        if (cards is null)
        {
            logger.LogWarning("Sinyi: no buy-list-item cards found (html len={Len}) for {District}", html.Length, district);
            return results;
        }

        foreach (var card in cards)
        {
            try
            {
                var dto = ParseCard(card, city, district);
                if (dto is not null) results.Add(dto);
            }
            catch (Exception ex)
            {
                logger.LogDebug("Sinyi: failed to parse card: {Msg}", ex.Message);
            }
        }

        return results;
    }

    private PropertyDto? ParseCard(HtmlNode card, string city, string district)
    {
        var outerHtml = card.OuterHtml;

        // 物件編號（URL 與圖片皆由此組成），例：/buy/house/3087NK?breadcrumb=list
        var keyMatch = HouseKeyRegex().Match(outerHtml);
        if (!keyMatch.Success) return null;
        var listingKey = keyMatch.Groups[1].Value;
        var url = $"{BaseUrl}/buy/house/{listingKey}";

        // 取第一張非影片圖片（如果第一個媒體是影片則跳過，嘗試下一張）
        string? imageUrl = null;
        var imgNodes = card.SelectNodes(".//img[@src]");
        if (imgNodes != null)
        {
            foreach (var img in imgNodes)
            {
                var resolved = ResolveImgSrc(img);
                if (!string.IsNullOrEmpty(resolved) && !IsVideoUrl(resolved))
                {
                    imageUrl = resolved;
                    break;
                }
            }
        }
        imageUrl ??= $"https://res.sinyi.com.tw/buy/{listingKey}/smallimg/A.JPG";

        // 標題
        var titleNode = card.SelectSingleNode(".//div[contains(@class,'LongInfoCard_Type_Name')]");
        var title = CleanText(titleNode?.InnerText) ?? district;

        // 地址（位址區塊的第一個 span，如「新北市中和區中和路」）
        string? address = null;
        var addrNode = card.SelectSingleNode(".//div[contains(@class,'LongInfoCard_Type_Address')]");
        if (addrNode is not null)
            address = CleanText(addrNode.SelectSingleNode(".//span")?.InnerText);

        // 整張卡片純文字 — 價格 / 建坪 / 樓層 / 屋齡 / 車位以 regex 擷取
        var text = CleanText(card.InnerText) ?? "";

        // 價格（萬）— 取第一個「數字+萬」
        var priceMatch = PriceRegex().Match(text);
        if (!priceMatch.Success) return null;
        if (!decimal.TryParse(priceMatch.Groups[1].Value.Replace(",", ""), out var totalPrice) || totalPrice <= 0)
            return null;

        // 建坪
        decimal areaPing = 0m;
        var areaMatch = AreaRegex().Match(text);
        if (areaMatch.Success) decimal.TryParse(areaMatch.Groups[1].Value, out areaPing);

        // 樓層，例：11樓/11樓
        string? floor = null;
        var floorMatch = FloorRegex().Match(text);
        if (floorMatch.Success) floor = floorMatch.Value.Replace(" ", "");

        // 屋齡，例：28.4年 → 28
        int? ageYears = null;
        var ageMatch = AgeRegex().Match(text);
        if (ageMatch.Success && decimal.TryParse(ageMatch.Groups[1].Value, out var ageDec))
            ageYears = (int)Math.Round(ageDec);

        // 車位
        var hasParking = text.Contains("車位");

        var unitPrice = areaPing > 0 ? totalPrice / areaPing : (decimal?)null;

        return new PropertyDto(
            City: city,
            District: district,
            Address: address,
            AreaPing: areaPing,
            Floor: floor,
            AgeYears: ageYears,
            HasParking: hasParking,
            TotalPrice: totalPrice,
            UnitPrice: unitPrice,
            SourceSite: SourceSite.Sinyi,
            SourceListingKey: listingKey,
            Title: title,
            Url: url,
            PostedDate: null,
            ImageUrl: imageUrl);
    }

    /// <summary>
    /// 解析 img 節點的真實圖片 URL（處理 Next.js /_next/image?url= 編碼）。
    /// </summary>
    private static string? ResolveImgSrc(HtmlNode imgNode)
    {
        var src = imgNode.GetAttributeValue("src", "");

        // Next.js Image Optimization：/_next/image?url=<encoded-cdn-url>&w=...
        if (!string.IsNullOrEmpty(src) && src.StartsWith("/_next/image", StringComparison.Ordinal))
        {
            var qIdx = src.IndexOf('?');
            if (qIdx >= 0)
            {
                foreach (var pair in src[(qIdx + 1)..].Split('&'))
                {
                    var eqIdx = pair.IndexOf('=');
                    if (eqIdx > 0 && pair[..eqIdx] == "url")
                    {
                        src = Uri.UnescapeDataString(pair[(eqIdx + 1)..]);
                        break;
                    }
                }
            }
        }

        if (string.IsNullOrEmpty(src) || !src.StartsWith("http", StringComparison.Ordinal))
            src = imgNode.GetAttributeValue("data-src", "");

        return src?.StartsWith("http", StringComparison.Ordinal) == true ? src : null;
    }

    private static bool IsVideoUrl(string? url)
    {
        if (string.IsNullOrEmpty(url)) return false;
        var lower = url.ToLowerInvariant();
        return lower.EndsWith(".mp4") || lower.EndsWith(".m3u8") || lower.EndsWith(".webm")
            || lower.EndsWith(".mov") || lower.Contains("/video/") || lower.Contains("_video");
    }

    private static string? CleanText(string? s)
    {
        if (string.IsNullOrWhiteSpace(s)) return null;
        var decoded = System.Net.WebUtility.HtmlDecode(s);
        decoded = WhitespaceRegex().Replace(decoded, " ").Trim();
        return string.IsNullOrEmpty(decoded) ? null : decoded;
    }

    [GeneratedRegex(@"/buy/house/([0-9A-Za-z]+)")]
    private static partial Regex HouseKeyRegex();

    [GeneratedRegex(@"([\d,]+)\s*萬")]
    private static partial Regex PriceRegex();

    [GeneratedRegex(@"建坪\s*([\d.]+)")]
    private static partial Regex AreaRegex();

    [GeneratedRegex(@"\d+樓\s*/\s*\d+樓")]
    private static partial Regex FloorRegex();

    [GeneratedRegex(@"([\d.]+)\s*年")]
    private static partial Regex AgeRegex();

    [GeneratedRegex(@"\s+")]
    private static partial Regex WhitespaceRegex();
}
