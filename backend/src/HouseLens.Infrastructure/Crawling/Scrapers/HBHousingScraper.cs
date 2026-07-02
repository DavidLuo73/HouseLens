using HouseLens.Application.Crawling;
using HouseLens.Domain.Enums;
using HtmlAgilityPack;
using Microsoft.Extensions.Logging;
using System.Text.RegularExpressions;

namespace HouseLens.Infrastructure.Crawling.Scrapers;

/// <summary>
/// 住商不動產買屋網（www.hbhousing.com.tw）爬蟲。
/// 網站為 Nuxt.js SSR，物件卡片直接內嵌於 HTML，使用 PlaywrightFetcher 確保完整渲染。
/// 列表頁需以行政區郵遞區號＋型態＋總價區間縮小範圍，否則會退化成城市層級的
/// 「熱門推薦」清單，在有限分頁內幾乎抓不到目標行政區的物件：
/// /buyhouse/{城市}/{zip}/noelevator-elevator-mansion-style/{總價}-down-price，
/// 分頁：.../{N}-page（N≥2）。
/// </summary>
public partial class HBHousingScraper(PlaywrightFetcher fetcher, ILogger<HBHousingScraper> logger) : ISourceScraper
{
    public SourceSite SourceSite => SourceSite.HBHousing;

    private const string BaseUrl = "https://www.hbhousing.com.tw";
    private const int PageSize = 10;               // 住商每頁 10 筆
    private const int MaxPagesPerDistrict = 100;   // 禮貌性上限，避免過量請求（大量體行政區可達 60~70 頁）

    // 建物型態 URL 區段：無電梯公寓、大樓(11樓以上)、華廈(10樓以下)
    private const string TypeSlug = "noelevator-elevator-mansion-style";

    // 住宅型態白名單；不在此清單的型態（辦公室、店面、廠房）一律過濾。
    private static readonly HashSet<string> ResidentialTypes = new(StringComparer.Ordinal)
    {
        "公寓", "大樓", "華廈", "透天/別墅", "別墅", "透天厝", "農舍",
    };

    private sealed record DistrictInfo(string City, string Zip);

    // 行政區 → 城市 / 郵遞區號（皆為標準台灣郵遞區號，已對照 hbhousing.com.tw 實際頁面驗證）
    private static readonly Dictionary<string, DistrictInfo> DistrictZipMap = new()
    {
        // 台北市
        ["中正區"] = new("台北市", "100"), ["大同區"] = new("台北市", "103"), ["中山區"] = new("台北市", "104"),
        ["松山區"] = new("台北市", "105"), ["大安區"] = new("台北市", "106"), ["萬華區"] = new("台北市", "108"),
        ["信義區"] = new("台北市", "110"), ["士林區"] = new("台北市", "111"), ["北投區"] = new("台北市", "112"),
        ["內湖區"] = new("台北市", "114"), ["南港區"] = new("台北市", "115"), ["文山區"] = new("台北市", "116"),
        // 新北市
        ["板橋區"] = new("新北市", "220"), ["新莊區"] = new("新北市", "242"), ["中和區"] = new("新北市", "235"),
        ["永和區"] = new("新北市", "234"), ["新店區"] = new("新北市", "231"), ["土城區"] = new("新北市", "236"),
        ["樹林區"] = new("新北市", "238"), ["三峽區"] = new("新北市", "237"), ["鶯歌區"] = new("新北市", "239"),
        ["三重區"] = new("新北市", "241"), ["蘆洲區"] = new("新北市", "247"), ["五股區"] = new("新北市", "248"),
        ["泰山區"] = new("新北市", "243"), ["林口區"] = new("新北市", "244"),
        // 桃園市
        ["桃園區"] = new("桃園市", "330"), ["中壢區"] = new("桃園市", "320"), ["平鎮區"] = new("桃園市", "324"),
        ["龜山區"] = new("桃園市", "333"), ["八德區"] = new("桃園市", "334"),
    };

    public async Task<IReadOnlyList<PropertyDto>> FetchAsync(
        IReadOnlyDictionary<string, DistrictCriteria> districtCriteria,
        IProgress<ScraperDistrictProgress>? progress,
        Func<IReadOnlyList<PropertyDto>, Task>? onDistrictCompleted = null,
        CancellationToken cancellationToken = default)
    {
        var results = new List<PropertyDto>();

        // 暖機：先訪首頁讓 cookie/session 初始化
        logger.LogInformation("HBHousing: warming up via {BaseUrl}", BaseUrl);
        await fetcher.FetchAsync(BaseUrl, cancellationToken);

        var knownDistricts = districtCriteria.Keys
            .Where(d => DistrictZipMap.ContainsKey(d))
            .ToList();

        foreach (var d in districtCriteria.Keys.Except(knownDistricts))
            logger.LogWarning("HBHousing: unknown district (not in DistrictZipMap): {District}", d);

        var total = knownDistricts.Count;

        for (var i = 0; i < knownDistricts.Count; i++)
        {
            var district = knownDistricts[i];
            var maxWan = districtCriteria[district].MaxTotalPrice;
            var info = DistrictZipMap[district];

            progress?.Report(new(district, i, total, IsStarting: true, FetchedCount: 0));
            logger.LogInformation("HBHousing: scraping {District} (max={Max}萬)", district, maxWan);

            var districtResults = await FetchDistrictAsync(info.City, district, info.Zip, maxWan, cancellationToken);
            results.AddRange(districtResults);

            progress?.Report(new(district, i, total, IsStarting: false, FetchedCount: districtResults.Count));
            logger.LogInformation("HBHousing: {District} done, {Count} listings", district, districtResults.Count);

            if (onDistrictCompleted is not null) await onDistrictCompleted(districtResults);
        }

        return results;
    }

    private async Task<List<PropertyDto>> FetchDistrictAsync(
        string city,
        string district,
        string zip,
        decimal maxWan,
        CancellationToken ct)
    {
        // seen 限定此 district 的分頁去重；跨 district 重複由 DB upsert 處理
        var seen = new HashSet<string>();
        var all = new List<PropertyDto>();
        var encodedCity = Uri.EscapeDataString(city);
        var priceSegment = maxWan > 0 ? $"/{(int)maxWan}-down-price" : "";

        for (var page = 1; page <= MaxPagesPerDistrict; page++)
        {
            var url = page == 1
                ? $"{BaseUrl}/buyhouse/{encodedCity}/{zip}/{TypeSlug}{priceSegment}"
                : $"{BaseUrl}/buyhouse/{encodedCity}/{zip}/{TypeSlug}{priceSegment}/{page}-page";

            var html = await fetcher.FetchAsync(url, ct);
            if (html is null)
            {
                logger.LogWarning("HBHousing: failed to fetch {Url}", url);
                break;
            }

            var pageResults = ParseListings(html, city, district, maxWan, seen);
            logger.LogInformation("HBHousing: {District} list page {Page}: {Count} listings",
                district, page, pageResults.Count);

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
        string queryCity,
        string queryDistrict,
        decimal maxWan,
        HashSet<string>? seen = null)
    {
        var results = new List<PropertyDto>();
        var doc = new HtmlDocument();
        doc.LoadHtml(html);

        var cards = doc.DocumentNode.SelectNodes("//section[contains(@class,'@container')]");
        if (cards is null)
        {
            logger.LogWarning("HBHousing: no @container section cards found (html len={Len}) for {District}",
                html.Length, queryDistrict);
            return results;
        }

        foreach (var card in cards)
        {
            try
            {
                var dto = ParseCard(card, queryCity, queryDistrict, maxWan);
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

    private PropertyDto? ParseCard(HtmlNode card, string queryCity, string queryDistrict, decimal maxWan)
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

        // 從地址提取城市與行政區（格式：台北市大安區仁愛路），解析失敗則回退查詢參數
        string city = queryCity;
        string district = queryDistrict;
        if (address is not null)
        {
            var addrMatch = AddressRegex().Match(address);
            if (addrMatch.Success)
            {
                city = addrMatch.Groups[1].Value;
                district = addrMatch.Groups[2].Value;
            }
        }

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

        // Client-side 總價上限過濾（URL 已依總價區間篩選，此為精確二次確認）
        if (maxWan > 0 && totalPrice > maxWan) return null;

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
            City: city,
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
