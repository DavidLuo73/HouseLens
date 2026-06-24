using HouseLens.Application.Crawling;
using HouseLens.Domain.Enums;
using HtmlAgilityPack;
using Microsoft.Extensions.Logging;
using System.Text.RegularExpressions;

namespace HouseLens.Infrastructure.Crawling.Scrapers;

/// <summary>
/// 永慶房屋買屋網（buy.yungching.com.tw）爬蟲。
/// 含永慶直營與有巢氏加盟物件，使用 Angular SSR，物件卡片直接內嵌於 HTML，
/// 以 HttpFetcher 取頁面、HtmlAgilityPack 解析即可，不需 Playwright。
/// 分頁：?pg=N（N≥2）；URL 格式：/list/{城市}-{行政區}_c。
/// </summary>
public partial class YungchingScraper(HttpFetcher fetcher, ILogger<YungchingScraper> logger) : ISourceScraper
{
    public SourceSite SourceSite => SourceSite.Yungching;

    private const string BaseUrl = "https://buy.yungching.com.tw";
    private const int PageSize = 30;           // 永慶每頁約 30 筆（實測；用於判斷最後一頁）
    private const int MaxPagesPerDistrict = 5; // 禮貌性上限，避免過量請求

    // 住宅型態白名單；不在此清單的型態（辦公商業大樓、廠辦、店面、土地）一律過濾。
    private static readonly HashSet<string> ResidentialTypes = new(StringComparer.Ordinal)
    {
        "公寓", "住宅大樓", "華廈", "透天厝", "別墅", "透天別墅",
    };

    // 行政區 → 城市名。永慶 URL 直接使用中文「城市-行政區_c」，不需 zip/slug 對照。
    private static readonly Dictionary<string, string> DistrictMap = new()
    {
        ["中和區"] = "新北市",
        ["永和區"] = "新北市",
        ["新店區"] = "新北市",
        ["板橋區"] = "新北市",
        ["土城區"] = "新北市",
        ["樹林區"] = "新北市",
        ["三峽區"] = "新北市",
        ["新莊區"] = "新北市",
        ["中壢區"] = "桃園市",
        ["桃園區"] = "桃園市",
    };

    public async Task<IReadOnlyList<PropertyDto>> FetchAsync(
        IReadOnlyDictionary<string, decimal> districtMaxPrices,
        IProgress<ScraperDistrictProgress>? progress,
        CancellationToken cancellationToken = default)
    {
        var results = new List<PropertyDto>();

        if (!await fetcher.CheckRobotsAsync($"{BaseUrl}/list", cancellationToken))
        {
            logger.LogInformation("robots.txt disallows crawling yungching buy list");
            return results;
        }

        var validDistricts = districtMaxPrices
            .Where(kv => DistrictMap.ContainsKey(kv.Key))
            .ToList();
        var total = validDistricts.Count;

        for (var i = 0; i < validDistricts.Count; i++)
        {
            var (district, maxPrice) = validDistricts[i];
            var city = DistrictMap[district];

            progress?.Report(new(district, i, total, IsStarting: true, FetchedCount: 0));

            var districtResults = await FetchDistrictAsync(district, city, (int)maxPrice, cancellationToken);
            results.AddRange(districtResults);

            progress?.Report(new(district, i, total, IsStarting: false, FetchedCount: districtResults.Count));
            logger.LogInformation("Yungching district {District} (max={Max}萬): {Count} listings",
                district, (int)maxPrice, districtResults.Count);
        }

        var unknownDistricts = districtMaxPrices.Keys.Where(d => !DistrictMap.ContainsKey(d));
        foreach (var d in unknownDistricts)
            logger.LogWarning("Yungching: unknown district (not in DistrictMap): {District}", d);

        return results;
    }

    private async Task<List<PropertyDto>> FetchDistrictAsync(
        string district, string city, int maxWan, CancellationToken ct)
    {
        var all = new List<PropertyDto>();
        // 跨頁去重：置頂廣告同一物件可能出現在每頁首位
        var seen = new HashSet<string>();

        for (var page = 1; page <= MaxPagesPerDistrict; page++)
        {
            var encodedCity = Uri.EscapeDataString(city);
            var encodedDistrict = Uri.EscapeDataString(district);
            var url = page == 1
                ? $"{BaseUrl}/list/{encodedCity}-{encodedDistrict}_c"
                : $"{BaseUrl}/list/{encodedCity}-{encodedDistrict}_c?pg={page}";

            var html = await fetcher.FetchAsync(url, ct);
            if (html is null)
            {
                logger.LogWarning("Yungching: failed to fetch {Url}", url);
                break;
            }

            var pageResults = ParseListings(html, city, district, maxWan, seen);
            logger.LogInformation("Yungching {District} page {Page}: {Count} listings", district, page, pageResults.Count);

            if (pageResults.Count == 0) break;
            all.AddRange(pageResults);

            if (pageResults.Count < PageSize) break;
        }

        return all;
    }

    /// <summary>
    /// 解析永慶列表頁 HTML → PropertyDto。
    /// 頁面為 Angular SSR，以 semantic class（caseName、caseType、address、regArea、floor、price 等）解析，
    /// 不使用隨 build 變動的 _ngcontent-ng-c* 屬性。
    /// </summary>
    public List<PropertyDto> ParseListings(
        string html, string city, string district, int maxWan, HashSet<string>? seen = null)
    {
        var results = new List<PropertyDto>();
        var doc = new HtmlDocument();
        doc.LoadHtml(html);

        var cards = doc.DocumentNode
            .SelectNodes("//li[contains(@class,'search-result-list-item')]//a[@class='link']");
        if (cards is null)
        {
            logger.LogWarning(
                "Yungching: no search-result-list-item cards found (html len={Len}) for {District}",
                html.Length, district);
            return results;
        }

        foreach (var card in cards)
        {
            try
            {
                var dto = ParseCard(card, city, district, maxWan);
                if (dto is null) continue;

                if (seen is not null && !seen.Add(dto.SourceListingKey)) continue;

                results.Add(dto);
            }
            catch (Exception ex)
            {
                logger.LogDebug("Yungching: failed to parse card: {Msg}", ex.Message);
            }
        }

        return results;
    }

    private PropertyDto? ParseCard(HtmlNode card, string city, string district, int maxWan)
    {
        // 物件 ID 從 href 取得，格式：house/{id}
        var href = card.GetAttributeValue("href", "");
        var keyMatch = HouseKeyRegex().Match(href);
        if (!keyMatch.Success) return null;
        var listingKey = keyMatch.Groups[1].Value;
        var url = $"{BaseUrl}/house/{listingKey}";

        // 建物型態（用於住宅過濾）
        var caseTypeNode = card.SelectSingleNode(".//*[contains(@class,'caseType')]");
        var caseType = CleanText(caseTypeNode?.InnerText) ?? "";
        if (!string.IsNullOrEmpty(caseType) && !ResidentialTypes.Contains(caseType))
            return null;

        // 標題
        var titleNode = card.SelectSingleNode(".//*[contains(@class,'caseName')]");
        var title = CleanText(titleNode?.InnerText) ?? district;

        // 地址（exact class 避免匹配 address-wrapper）
        var addrNode = card.SelectSingleNode(".//*[@class='address']");
        var address = CleanText(addrNode?.InnerText);

        // 建坪（regArea 含「建坪28.15」格式）
        decimal areaPing = 0m;
        var regAreaNode = card.SelectSingleNode(".//*[contains(@class,'regArea')]");
        if (regAreaNode is not null)
        {
            var areaMatch = AreaRegex().Match(regAreaNode.InnerText);
            if (areaMatch.Success) decimal.TryParse(areaMatch.Groups[1].Value, out areaPing);
        }

        // 樓層（格式：6/19樓）
        string? floor = null;
        var floorNode = card.SelectSingleNode(".//*[@class='floor']");
        if (floorNode is not null) floor = CleanText(floorNode.InnerText);

        // 屋齡（case-info 純文字中擷取，例：15.0年）
        int? ageYears = null;
        var caseInfoNode = card.SelectSingleNode(".//*[contains(@class,'case-info')]");
        if (caseInfoNode is not null)
        {
            var ageMatch = AgeRegex().Match(caseInfoNode.InnerText);
            if (ageMatch.Success && decimal.TryParse(ageMatch.Groups[1].Value, out var ageDec))
                ageYears = (int)Math.Round(ageDec);
        }

        // 車位
        var hasParking = card.InnerText.Contains("車位");

        // 總價（price div，如「2,388」，單位萬）
        var priceNode = card.SelectSingleNode(".//*[@class='price']");
        if (priceNode is null) return null;
        var priceText = priceNode.InnerText.Replace(",", "").Trim();
        if (!decimal.TryParse(priceText, out var totalPrice) || totalPrice <= 0) return null;

        // Client-side 價格上限過濾（永慶 URL 無可靠的價格篩選參數）
        if (maxWan > 0 && totalPrice > maxWan) return null;

        // 圖片（img-wrapper 內第一張 img 的 src）
        // 永慶 CDN（yccdn.yungching.com.tw/v1/image/）支援 width= 參數調整尺寸，
        // 列表頁縮圖為 width=480，替換為 1200 以取得高解析度圖片。
        string? imageUrl = null;
        var imgNode = card.SelectSingleNode(".//*[contains(@class,'img-wrapper')]//img[@src]");
        if (imgNode is not null)
        {
            var src = imgNode.GetAttributeValue("src", "");
            if (!string.IsNullOrEmpty(src) && src.StartsWith("http", StringComparison.Ordinal))
                imageUrl = UpscaleYccdnUrl(src);
        }

        var unitPrice = areaPing > 0 ? Math.Round(totalPrice / areaPing, 2) : (decimal?)null;

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
            SourceSite: SourceSite.Yungching,
            SourceListingKey: listingKey,
            Title: title,
            Url: url,
            PostedDate: null,
            ImageUrl: imageUrl);
    }

    private static string UpscaleYccdnUrl(string src) =>
        YccdnWidthRegex().IsMatch(src) ? YccdnWidthRegex().Replace(src, "width=1200") : src;

    private static string? CleanText(string? s)
    {
        if (string.IsNullOrWhiteSpace(s)) return null;
        var decoded = System.Net.WebUtility.HtmlDecode(s);
        decoded = WhitespaceRegex().Replace(decoded, " ").Trim();
        return string.IsNullOrEmpty(decoded) ? null : decoded;
    }

    [GeneratedRegex(@"house/(\d+)")]
    private static partial Regex HouseKeyRegex();

    [GeneratedRegex(@"建坪([\d.]+)")]
    private static partial Regex AreaRegex();

    [GeneratedRegex(@"([\d.]+)\s*年")]
    private static partial Regex AgeRegex();

    [GeneratedRegex(@"\s+")]
    private static partial Regex WhitespaceRegex();

    [GeneratedRegex(@"width=\d+")]
    private static partial Regex YccdnWidthRegex();
}
