using HouseLens.Application.Crawling;
using HouseLens.Domain.Enums;
using HtmlAgilityPack;
using Microsoft.Extensions.Logging;
using System.Text.RegularExpressions;

namespace HouseLens.Infrastructure.Crawling.Scrapers;

public partial class TwHouseScraper(HttpFetcher fetcher, ILogger<TwHouseScraper> logger) : ISourceScraper
{
    public SourceSite SourceSite => SourceSite.TwHouse;

    private const string BaseUrl = "https://www.twhg.com.tw";
    private const int MaxPagesPerDistrict = 5;

    private static readonly HashSet<string> ResidentialTypes = new(StringComparer.Ordinal)
    {
        "公寓", "大樓", "華廈", "透天厝", "透天/別墅", "別墅",
    };

    private static readonly HashSet<string> NonResidentialKeywords = new(StringComparer.Ordinal)
    {
        "辦公室", "店面", "廠房", "廠辦", "倉庫", "工廠",
    };

    private sealed record DistrictInfo(string City, string CitySlug, string Zip);

    private static readonly Dictionary<string, DistrictInfo> DistrictMap = new()
    {
        ["中和區"] = new("新北市", "newTaipei-city", "235"),
        ["永和區"] = new("新北市", "newTaipei-city", "234"),
        ["新店區"] = new("新北市", "newTaipei-city", "231"),
        ["板橋區"] = new("新北市", "newTaipei-city", "220"),
        ["樹林區"] = new("新北市", "newTaipei-city", "238"),
        ["新莊區"] = new("新北市", "newTaipei-city", "242"),
        ["中壢區"] = new("桃園市", "taoyuan-city", "320"),
        ["桃園區"] = new("桃園市", "taoyuan-city", "330"),
    };

    public async Task<IReadOnlyList<PropertyDto>> FetchAsync(
        IReadOnlyDictionary<string, decimal> districtMaxPrices,
        IProgress<ScraperDistrictProgress>? progress,
        CancellationToken cancellationToken = default)
    {
        var results = new List<PropertyDto>();

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
                info.City, district, info.CitySlug, info.Zip, maxWan, cancellationToken);
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
        CancellationToken ct)
    {
        // seen 限定此 district 的分頁去重；跨 district 重複由 DB upsert 處理
        var seen = new HashSet<string>();
        var results = new List<PropertyDto>();
        var priceSegment = maxWan > 0 ? $"/{(int)maxWan}down-price" : "";

        for (var page = 1; page <= MaxPagesPerDistrict; page++)
        {
            var url = $"{BaseUrl}/buy/list/{citySlug}/{zip}-zip{priceSegment}/recomended-desc?page={page}";
            var html = await fetcher.FetchAsync(url, ct);
            if (html is null)
            {
                logger.LogWarning("TwHouse: failed to fetch list {Url}", url);
                break;
            }

            var pageResults = ParseListings(html, city, district, maxWan, seen);
            logger.LogInformation("TwHouse: {District} list page {Page}: {Count} listings",
                district, page, pageResults.Count);

            results.AddRange(pageResults);
            if (pageResults.Count == 0) break;
        }

        return results;
    }

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

    /// <summary>
    /// 解析台灣房屋詳情頁 HTML，取得補齊資訊。
    /// 詳情頁使用 &lt;dl&gt;&lt;dt&gt;標籤&lt;/dt&gt;&lt;dd&gt;值&lt;/dd&gt; 結構。
    /// </summary>
    public (string? Address, string? Floor, bool HasParking, string? PropertyType) ParseDetail(string html)
    {
        var doc = new HtmlDocument();
        doc.LoadHtml(html);

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

    private PropertyDto? ParseCard(HtmlNode anchor, string queryCity, string queryDistrict, decimal maxWan)
    {
        var href = anchor.GetAttributeValue("href", "");
        var codeMatch = ListingCodeRegex().Match(href);
        if (!codeMatch.Success) return null;
        var code = codeMatch.Groups[1].Value;
        var url = $"{BaseUrl}/buy/{code}";

        var text = HtmlEntity.DeEntitize(anchor.InnerText);

        if (NonResidentialKeywords.Any(kw => text.Contains(kw, StringComparison.Ordinal)))
            return null;

        // 清單頁同時顯示原價與折扣價，取最後一個（現售價）
        var priceMatches = PriceRegex().Matches(text);
        if (priceMatches.Count == 0) return null;
        var priceMatch = priceMatches[priceMatches.Count - 1];
        if (!decimal.TryParse(priceMatch.Groups[1].Value.Replace(",", ""), out var totalPrice)
            || totalPrice <= 0)
            return null;
        if (maxWan > 0 && totalPrice > maxWan) return null;

        decimal areaPing = 0m;
        var areaMatch = AreaRegex().Match(text);
        if (areaMatch.Success) decimal.TryParse(areaMatch.Groups[1].Value, out areaPing);

        int? ageYears = null;
        var ageMatch = AgeRegex().Match(text);
        if (ageMatch.Success && decimal.TryParse(ageMatch.Groups[1].Value, out var ageDec))
            ageYears = (int)Math.Round(ageDec);

        string city = queryCity, district = queryDistrict;
        var addrMatch = AddressRegex().Match(text);
        if (addrMatch.Success)
        {
            city = addrMatch.Groups[1].Value;
            district = addrMatch.Groups[2].Value;
        }

        var titleNode = anchor.SelectSingleNode(".//h3") ?? anchor;
        var rawTitle = HtmlEntity.DeEntitize(titleNode.InnerText).Trim();
        var title = CodeSuffixRegex().Replace(rawTitle, "").Trim();
        if (string.IsNullOrWhiteSpace(title)) title = district;

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
            Address: null,
            AreaPing: areaPing,
            Floor: null,
            AgeYears: ageYears,
            HasParking: false,
            TotalPrice: totalPrice,
            UnitPrice: unitPrice,
            SourceSite: SourceSite.TwHouse,
            SourceListingKey: code,
            Title: title,
            Url: url,
            PostedDate: null,
            ImageUrl: imageUrl);
    }

    [GeneratedRegex(@"/buy/([A-Z][A-Z0-9]+)$")]
    private static partial Regex ListingCodeRegex();

    [GeneratedRegex(@"([\d,]+)\s*萬")]
    private static partial Regex PriceRegex();

    [GeneratedRegex(@"建坪\s*([\d.]+)\s*坪")]
    private static partial Regex AreaRegex();

    [GeneratedRegex(@"([\d.]+)\s*年")]
    private static partial Regex AgeRegex();

    [GeneratedRegex(@"([一-龥]+[市縣])([一-龥]+[區鎮市鄉])")]
    private static partial Regex AddressRegex();

    [GeneratedRegex(@"[A-Z][A-Z0-9]{5,}$")]
    private static partial Regex CodeSuffixRegex();
}
