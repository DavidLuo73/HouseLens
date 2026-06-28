using HouseLens.Application.Crawling;
using HouseLens.Domain.Enums;
using HtmlAgilityPack;
using Microsoft.Extensions.Logging;
using System.Text.RegularExpressions;

namespace HouseLens.Infrastructure.Crawling.Scrapers;

/// <summary>
/// 住商不動產買屋網（www.hbhousing.com.tw）爬蟲。
/// 網站為 Nuxt.js SSR，物件卡片直接內嵌於 HTML，使用 PlaywrightFetcher 確保完整渲染。
/// 列表頁按城市：/buyhouse/{城市}，分頁：/buyhouse/{城市}/{N}-page（N≥2）。
/// 無行政區層級 URL，以地址文字中的「XX區」做 client-side 過濾。
/// </summary>
public partial class HBHousingScraper(PlaywrightFetcher fetcher, ILogger<HBHousingScraper> logger) : ISourceScraper
{
    public SourceSite SourceSite => SourceSite.HBHousing;

    private const string BaseUrl = "https://www.hbhousing.com.tw";
    private const int PageSize = 10;           // 住商每頁 10 筆
    private const int MaxPagesPerCity = 8;     // 禮貌性上限，避免過量請求

    // 住宅型態白名單；不在此清單的型態（辦公室、店面、廠房）一律過濾。
    private static readonly HashSet<string> ResidentialTypes = new(StringComparer.Ordinal)
    {
        "公寓", "大樓", "華廈", "透天/別墅", "別墅", "透天厝", "農舍",
    };

    // 行政區 → 城市名（決定要抓哪些城市頁）
    private static readonly Dictionary<string, string> DistrictToCity = new()
    {
        // 台北市
        ["中正區"] = "台北市", ["大同區"] = "台北市", ["中山區"] = "台北市",
        ["松山區"] = "台北市", ["大安區"] = "台北市", ["萬華區"] = "台北市",
        ["信義區"] = "台北市", ["士林區"] = "台北市", ["北投區"] = "台北市",
        ["內湖區"] = "台北市", ["南港區"] = "台北市", ["文山區"] = "台北市",
        // 新北市
        ["板橋區"] = "新北市", ["新莊區"] = "新北市", ["中和區"] = "新北市",
        ["永和區"] = "新北市", ["新店區"] = "新北市", ["土城區"] = "新北市",
        ["樹林區"] = "新北市", ["三峽區"] = "新北市", ["鶯歌區"] = "新北市",
        ["三重區"] = "新北市", ["蘆洲區"] = "新北市", ["五股區"] = "新北市",
        ["泰山區"] = "新北市", ["林口區"] = "新北市",
        // 桃園市
        ["桃園區"] = "桃園市", ["中壢區"] = "桃園市", ["平鎮區"] = "桃園市",
        ["龜山區"] = "桃園市", ["八德區"] = "桃園市",
    };

    public async Task<IReadOnlyList<PropertyDto>> FetchAsync(
        IReadOnlyDictionary<string, decimal> districtMaxPrices,
        IProgress<ScraperDistrictProgress>? progress,
        CancellationToken cancellationToken = default)
    {
        var results = new List<PropertyDto>();

        // 暖機：先訪首頁讓 cookie/session 初始化
        logger.LogInformation("HBHousing: warming up via {BaseUrl}", BaseUrl);
        await fetcher.FetchAsync(BaseUrl, cancellationToken);

        // 找出需要爬取的城市（多個行政區可對應同一城市，dedupe）
        var cities = districtMaxPrices.Keys
            .Where(d => DistrictToCity.ContainsKey(d))
            .Select(d => DistrictToCity[d])
            .Distinct()
            .ToList();

        var unknownDistricts = districtMaxPrices.Keys.Where(d => !DistrictToCity.ContainsKey(d));
        foreach (var d in unknownDistricts)
            logger.LogWarning("HBHousing: unknown district (not in DistrictToCity): {District}", d);

        var seen = new HashSet<string>();

        foreach (var city in cities)
        {
            logger.LogInformation("HBHousing: scraping city {City}", city);
            var cityResults = await FetchCityAsync(city, districtMaxPrices, seen, progress, cancellationToken);
            results.AddRange(cityResults);
        }

        return results;
    }

    private async Task<List<PropertyDto>> FetchCityAsync(
        string city,
        IReadOnlyDictionary<string, decimal> districtMaxPrices,
        HashSet<string> seen,
        IProgress<ScraperDistrictProgress>? progress,
        CancellationToken ct)
    {
        var all = new List<PropertyDto>();
        var encodedCity = Uri.EscapeDataString(city);

        for (var page = 1; page <= MaxPagesPerCity; page++)
        {
            var url = page == 1
                ? $"{BaseUrl}/buyhouse/{encodedCity}"
                : $"{BaseUrl}/buyhouse/{encodedCity}/{page}-page";

            var html = await fetcher.FetchAsync(url, ct);
            if (html is null)
            {
                logger.LogWarning("HBHousing: failed to fetch {Url}", url);
                break;
            }

            var pageResults = ParseListings(html, districtMaxPrices, seen);
            logger.LogInformation("HBHousing {City} page {Page}: {Count} listings", city, page, pageResults.Count);

            all.AddRange(pageResults);

            if (pageResults.Count < PageSize) break; // 最後一頁
        }

        return all;
    }

    /// <summary>
    /// 解析住商列表頁 HTML → PropertyDto。
    /// 頁面為 Nuxt SSR，以 section[contains(@class,'@container')] 定位卡片。
    /// </summary>
    public List<PropertyDto> ParseListings(
        string html,
        IReadOnlyDictionary<string, decimal> districtMaxPrices,
        HashSet<string>? seen = null)
    {
        var results = new List<PropertyDto>();
        var doc = new HtmlDocument();
        doc.LoadHtml(html);

        var cards = doc.DocumentNode.SelectNodes("//section[contains(@class,'@container')]");
        if (cards is null)
        {
            logger.LogWarning("HBHousing: no @container section cards found (html len={Len})", html.Length);
            return results;
        }

        foreach (var card in cards)
        {
            try
            {
                var dto = ParseCard(card, districtMaxPrices);
                if (dto is null) continue;

                if (seen is not null && !seen.Add(dto.SourceListingKey)) continue;

                results.Add(dto);
            }
            catch (Exception ex)
            {
                logger.LogDebug("HBHousing: failed to parse card: {Msg}", ex.Message);
            }
        }

        return results;
    }

    private PropertyDto? ParseCard(HtmlNode card, IReadOnlyDictionary<string, decimal> districtMaxPrices)
    {
        // 物件 SN 與標題從 h3 > a[href*="/detail?sn="] 取得
        var titleLink = card.SelectSingleNode(".//h3//a[contains(@href,'/detail?sn=') and not(contains(@href,'#'))]");
        if (titleLink is null) return null;

        var href = titleLink.GetAttributeValue("href", "");
        var snMatch = SnRegex().Match(href);
        if (!snMatch.Success) return null;
        var sn = snMatch.Groups[1].Value;
        var title = CleanText(titleLink.InnerText) ?? sn;
        var url = $"{BaseUrl}/detail?sn={sn}";

        // 地址：看地圖連結的 preceding-sibling span
        var mapLink = card.SelectSingleNode(".//a[contains(@href,'#life')]");
        var addrSpan = mapLink?.SelectSingleNode("preceding-sibling::span[1]");
        var address = CleanText(addrSpan?.InnerText);

        // 從地址提取城市與行政區（格式：台北市大安區仁愛路）
        string? city = null;
        string? district = null;
        if (address is not null)
        {
            var addrMatch = AddressRegex().Match(address);
            if (addrMatch.Success)
            {
                city = addrMatch.Groups[1].Value;
                district = addrMatch.Groups[2].Value;
            }
        }

        // 行政區需在設定清單中，否則略過
        if (district is null || !districtMaxPrices.TryGetValue(district, out var maxPrice))
            return null;

        // 物件細節：看格局圖按鈕的 preceding-sibling span
        // 格式："公寓 | 3房(室)2廳1衛 | 30.7年 | 5樓/5樓 | 建坪 | 29.05坪"
        var layoutBtn = card.SelectSingleNode(".//button[contains(.,'格局')]");
        var detailSpan = layoutBtn?.SelectSingleNode("preceding-sibling::span[1]");
        var detailText = CleanText(detailSpan?.InnerText) ?? "";
        var parts = detailText.Split(" | ");

        // 建物型態（index 0）
        var propertyType = parts.Length > 0 ? parts[0].Trim() : "";
        if (!string.IsNullOrEmpty(propertyType) && !ResidentialTypes.Contains(propertyType))
            return null;

        // 屋齡（index 2，格式 "30.7年"）
        int? ageYears = null;
        if (parts.Length > 2)
        {
            var ageMatch = AgeRegex().Match(parts[2]);
            if (ageMatch.Success && decimal.TryParse(ageMatch.Groups[1].Value, out var ageDec))
                ageYears = (int)Math.Round(ageDec);
        }

        // 樓層（index 3，格式 "5樓/5樓"）
        string? floor = parts.Length > 3 ? parts[3].Trim() : null;
        if (string.IsNullOrWhiteSpace(floor)) floor = null;

        // 建坪（index 5，格式 "29.05坪"）
        decimal areaPing = 0m;
        if (parts.Length > 5)
        {
            var areaMatch = AreaRegex().Match(parts[5]);
            if (areaMatch.Success) decimal.TryParse(areaMatch.Groups[1].Value, out areaPing);
        }

        // 有無車位（div.tag 含「車位」）
        var hasParking = card.SelectSingleNode(".//div[contains(@class,'tag') and contains(.,'車位')]") is not null;

        // 現價：span.text-error → "1,280 萬" → 去除逗號與「萬」
        var priceNode = card.SelectSingleNode(".//span[contains(@class,'text-error')]");
        if (priceNode is null) return null;
        var priceText = priceNode.InnerText.Replace(",", "").Replace("萬", "").Trim();
        if (!decimal.TryParse(priceText, out var totalPrice) || totalPrice <= 0) return null;

        // Client-side 總價上限過濾
        if (maxPrice > 0 && totalPrice > maxPrice) return null;

        // 圖片（去除 timestamp query string）
        string? imageUrl = null;
        var imgNode = card.SelectSingleNode(".//img[contains(@src,'hbhousing.com.tw')]");
        if (imgNode is not null)
        {
            var src = imgNode.GetAttributeValue("src", "");
            if (!string.IsNullOrEmpty(src))
                imageUrl = src.Split('?')[0]; // 移除 ?timestamp
        }

        var unitPrice = areaPing > 0 ? Math.Round(totalPrice / areaPing, 2) : (decimal?)null;

        return new PropertyDto(
            City: city ?? "",
            District: district,
            Address: address,
            AreaPing: areaPing,
            Floor: floor,
            AgeYears: ageYears,
            HasParking: hasParking,
            TotalPrice: totalPrice,
            UnitPrice: unitPrice,
            SourceSite: SourceSite.HBHousing,
            SourceListingKey: sn,
            Title: title,
            Url: url,
            PostedDate: null,
            ImageUrl: imageUrl);
    }

    private static string? CleanText(string? s)
    {
        if (string.IsNullOrWhiteSpace(s)) return null;
        var decoded = System.Net.WebUtility.HtmlDecode(s);
        decoded = WhitespaceRegex().Replace(decoded, " ").Trim();
        // 去除常見零寬字元（住商地址含 ​ 零寬空格）
        decoded = decoded.Replace("​", "").Trim();
        return string.IsNullOrEmpty(decoded) ? null : decoded;
    }

    [GeneratedRegex(@"\?sn=([A-Za-z0-9]+)")]
    private static partial Regex SnRegex();

    // 地址格式：台北市大安區仁愛路 → group1=台北市, group2=大安區
    [GeneratedRegex(@"^([一-龥]+[市縣])([一-龥]+[區鎮市鄉])")]
    private static partial Regex AddressRegex();

    [GeneratedRegex(@"([\d.]+)\s*年")]
    private static partial Regex AgeRegex();

    [GeneratedRegex(@"([\d.]+)\s*坪")]
    private static partial Regex AreaRegex();

    [GeneratedRegex(@"\s+")]
    private static partial Regex WhitespaceRegex();
}
