using HouseLens.Application.Crawling;
using HouseLens.Domain.Enums;
using HtmlAgilityPack;
using Microsoft.Extensions.Logging;
using System.Text.RegularExpressions;

namespace HouseLens.Infrastructure.Crawling.Scrapers;

/// <summary>
/// 台灣房屋（twhg.com.tw）買屋爬蟲。列表頁為 SSR HTML，以 HttpFetcher 抓取。
/// 搜尋 URL 由 BuildSearchUrl 依 DistrictCriteria 組成，路徑段依序（2026-07 實站驗證）：
/// /buy/list/{citySlug}/{zip}-zips/{型態,…以-串接}-kinds/{N}down-price/{N}up-ping/
/// fullBuildingPing-ping_type/{N}up-bedrooms/{N}down-house_year/1up-park_count/recomended-desc?page=N。
/// ping_type 指坪數篩選基準：fullBuildingPing=建坪（ping_type=1）、landPing=土地坪數（ping_type=3）；
/// 本爬蟲固定用建坪。車位段 1up-park_count=有車位、0~0-park_count=無車位、省略=不限。
/// 超過最後一頁時頁面顯示「暫無查詢物件」字樣（非重複回傳前頁內容），以此為分頁結束訊號。
/// </summary>
public partial class TwHouseScraper(HttpFetcher fetcher, ILogger<TwHouseScraper> logger) : ISourceScraper
{
    public SourceSite SourceSite => SourceSite.TwHouse;

    private const string BaseUrl = "https://www.twhg.com.tw";
    private const int MaxPagesPerDistrict = 30; // 禮貌性上限，避免過量請求
    private const string NoResultMarker = "暫無查詢物件";

    // 台灣房屋搜尋 URL 的建物型態 slug（{…}-kinds 段），依官方順序排列
    private static readonly string[] TwhgKindSlugs = ["apartment", "midrise", "condo"];

    // 樂屋網 typecode → 台灣房屋 kind slug 對映（平台未設定台灣房屋專屬 TypeCodes 時的回退）
    private static readonly Dictionary<string, string[]> RakuyaTypeToTwhg = new(StringComparer.OrdinalIgnoreCase)
    {
        ["R1"] = ["apartment"],
        ["R2"] = ["condo", "midrise"],
    };

    private static readonly HashSet<string> ResidentialTypes = new(StringComparer.Ordinal)
    {
        "公寓", "大樓", "華廈", "透天厝", "透天/別墅", "別墅",
    };

    private static readonly HashSet<string> NonResidentialKeywords = new(StringComparer.Ordinal)
    {
        "辦公室", "店面", "廠房", "廠辦", "倉庫", "工廠", "土地", "建地", "農地",
    };

    private sealed record DistrictInfo(string City, string CitySlug, string Zip);

    private static readonly Dictionary<string, DistrictInfo> DistrictMap = new()
    {
        // 新北市（zip = 郵遞區號，與前端 CITY_DISTRICTS 清單一致）
        ["板橋區"] = new("新北市", "newTaipei-city", "220"),
        ["汐止區"] = new("新北市", "newTaipei-city", "221"),
        ["深坑區"] = new("新北市", "newTaipei-city", "222"),
        ["瑞芳區"] = new("新北市", "newTaipei-city", "224"),
        ["新店區"] = new("新北市", "newTaipei-city", "231"),
        ["永和區"] = new("新北市", "newTaipei-city", "234"),
        ["中和區"] = new("新北市", "newTaipei-city", "235"),
        ["土城區"] = new("新北市", "newTaipei-city", "236"),
        ["三峽區"] = new("新北市", "newTaipei-city", "237"),
        ["樹林區"] = new("新北市", "newTaipei-city", "238"),
        ["鶯歌區"] = new("新北市", "newTaipei-city", "239"),
        ["三重區"] = new("新北市", "newTaipei-city", "241"),
        ["新莊區"] = new("新北市", "newTaipei-city", "242"),
        ["泰山區"] = new("新北市", "newTaipei-city", "243"),
        ["林口區"] = new("新北市", "newTaipei-city", "244"),
        ["蘆洲區"] = new("新北市", "newTaipei-city", "247"),
        ["五股區"] = new("新北市", "newTaipei-city", "248"),
        ["八里區"] = new("新北市", "newTaipei-city", "249"),
        ["淡水區"] = new("新北市", "newTaipei-city", "251"),
        // 桃園市
        ["中壢區"] = new("桃園市", "taoyuan-city", "320"),
        ["平鎮區"] = new("桃園市", "taoyuan-city", "324"),
        ["龍潭區"] = new("桃園市", "taoyuan-city", "325"),
        ["楊梅區"] = new("桃園市", "taoyuan-city", "326"),
        ["新屋區"] = new("桃園市", "taoyuan-city", "327"),
        ["觀音區"] = new("桃園市", "taoyuan-city", "328"),
        ["桃園區"] = new("桃園市", "taoyuan-city", "330"),
        ["龜山區"] = new("桃園市", "taoyuan-city", "333"),
        ["八德區"] = new("桃園市", "taoyuan-city", "334"),
        ["大溪區"] = new("桃園市", "taoyuan-city", "335"),
        ["大園區"] = new("桃園市", "taoyuan-city", "337"),
        ["蘆竹區"] = new("桃園市", "taoyuan-city", "338"),
    };

    public async Task<IReadOnlyList<PropertyDto>> FetchAsync(
        IReadOnlyDictionary<string, DistrictCriteria> districtCriteria,
        IProgress<ScraperDistrictProgress>? progress,
        Func<IReadOnlyList<PropertyDto>, Task>? onDistrictCompleted = null,
        CancellationToken cancellationToken = default)
    {
        var results = new List<PropertyDto>();

        var knownDistricts = districtCriteria.Keys
            .Where(d => DistrictMap.ContainsKey(d))
            .ToList();

        foreach (var d in districtCriteria.Keys.Except(knownDistricts))
            logger.LogWarning("TwHouse: unknown district (not in DistrictMap): {District}", d);

        var total = knownDistricts.Count;

        for (var i = 0; i < knownDistricts.Count; i++)
        {
            var district = knownDistricts[i];
            var criteria = districtCriteria[district];
            var info = DistrictMap[district];

            progress?.Report(new(district, i, total, IsStarting: true, FetchedCount: 0));
            logger.LogInformation("TwHouse: scraping {District} (max={Max}萬)", district, criteria.MaxTotalPrice);

            var districtResults = await FetchDistrictAsync(
                info.City, district, info.CitySlug, info.Zip, criteria, cancellationToken);
            results.AddRange(districtResults);

            progress?.Report(new(district, i, total, IsStarting: false, FetchedCount: districtResults.Count));
            logger.LogInformation("TwHouse: {District} done, {Count} listings", district, districtResults.Count);

            if (onDistrictCompleted is not null) await onDistrictCompleted(districtResults);
        }

        return results;
    }

    /// <summary>
    /// 依 DistrictCriteria 組出台灣房屋搜尋列表 URL。路徑段依序為（無設定者省略）：
    /// {citySlug}/{zip}-zips／型態 {…}-kinds／價格 {N}down-price／坪數 {N}up-ping＋
    /// fullBuildingPing-ping_type（建坪基準）／房數 {N}up-bedrooms／屋齡 {N}down-house_year／
    /// 車位 1up-park_count（勾選任一車位型式即要求有車位，不勾＝不限）／排序 recomended-desc；分頁 ?page=N。
    /// </summary>
    public static string BuildSearchUrl(string citySlug, string zip, DistrictCriteria criteria, int page)
    {
        var segments = new List<string>
        {
            citySlug,
            $"{zip}-zips",
            $"{string.Join('-', ResolveKindSlugs(criteria.TypeCodes))}-kinds",
        };

        if (criteria.MaxTotalPrice > 0)
            segments.Add($"{criteria.MaxTotalPrice:0}down-price");

        if (criteria.MinSizePing > 0)
        {
            segments.Add($"{criteria.MinSizePing:0.##}up-ping");
            segments.Add("fullBuildingPing-ping_type"); // 坪數以建坪計（landPing=土地坪數，不適用住宅）
        }

        var minRooms = ResolveMinRooms(criteria.Rooms);
        if (minRooms > 0)
            segments.Add($"{minRooms}up-bedrooms");

        if (criteria.MaxAgeYears > 0)
            segments.Add($"{criteria.MaxAgeYears}down-house_year");

        if (!string.IsNullOrWhiteSpace(criteria.ParkingCodes))
            segments.Add("1up-park_count");

        segments.Add("recomended-desc");
        return $"{BaseUrl}/buy/list/{string.Join('/', segments)}?page={page}";
    }

    /// <summary>型態段：優先採台灣房屋 slug（apartment/midrise/condo）；樂屋網 R 代碼則對映；無法辨識時回退全部住宅型態。</summary>
    private static List<string> ResolveKindSlugs(string typeCodes)
    {
        var codes = string.IsNullOrWhiteSpace(typeCodes)
            ? []
            : typeCodes.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries);
        var slugs = codes.Where(c => TwhgKindSlugs.Contains(c, StringComparer.OrdinalIgnoreCase))
            .Select(c => c.ToLowerInvariant())
            .Concat(codes.SelectMany(c => RakuyaTypeToTwhg.GetValueOrDefault(c, [])))
            .Distinct()
            .ToList();
        if (slugs.Count == 0) return [.. TwhgKindSlugs];

        // 依官方順序輸出，避免同組合產生不同 URL
        return TwhgKindSlugs.Where(slugs.Contains).ToList();
    }

    /// <summary>房數條件：取 Rooms（如 "2,3,5~"）中最小的數字作為 {N}up-bedrooms；空或無法解析＝不限。</summary>
    private static int ResolveMinRooms(string rooms)
    {
        if (string.IsNullOrWhiteSpace(rooms)) return 0;
        var values = rooms.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
            .Select(r => int.TryParse(r.TrimEnd('~'), out var n) ? n : 0)
            .Where(n => n > 0)
            .ToList();
        return values.Count == 0 ? 0 : values.Min();
    }

    private async Task<List<PropertyDto>> FetchDistrictAsync(
        string city,
        string district,
        string citySlug,
        string zip,
        DistrictCriteria criteria,
        CancellationToken ct)
    {
        // seen 限定此 district 的分頁去重；跨 district 重複由 DB upsert 處理
        var seen = new HashSet<string>();
        var results = new List<PropertyDto>();
        var maxWan = criteria.MaxTotalPrice;

        for (var page = 1; page <= MaxPagesPerDistrict; page++)
        {
            var url = BuildSearchUrl(citySlug, zip, criteria, page);
            var html = await fetcher.FetchAsync(url, ct);
            if (html is null)
            {
                logger.LogWarning("TwHouse: failed to fetch list {Url}", url);
                break;
            }

            // 超過最後一頁（或條件無符合物件）時，頁面顯示「暫無查詢物件」，為可靠的分頁結束訊號
            if (html.Contains(NoResultMarker, StringComparison.Ordinal))
            {
                logger.LogInformation("TwHouse: {District} page {Page}: no matching listings, stopping pagination",
                    district, page);
                break;
            }

            var pageResults = ParseListings(html, city, district, maxWan, seen);
            logger.LogInformation("TwHouse: {District} list page {Page}: {Count} listings",
                district, page, pageResults.Count);

            results.AddRange(pageResults);
            // 保險：頁面無「暫無查詢物件」字樣但解析（含 seen 去重）後為 0，也視為到底
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

        var titleNode = anchor.SelectSingleNode(".//h2") ?? anchor.SelectSingleNode(".//h3") ?? anchor;
        var rawTitle = HtmlEntity.DeEntitize(titleNode.InnerText).Trim();
        var title = CodeSuffixRegex().Replace(rawTitle, "").Trim();
        if (string.IsNullOrWhiteSpace(title)) title = district;

        // 列表頁圖片以 CSS background-image 呈現，非 <img src>
        string? imageUrl = null;
        var photoNode = anchor.SelectSingleNode(".//div[contains(@style,'background-image')]");
        if (photoNode is not null)
        {
            var style = HtmlEntity.DeEntitize(photoNode.GetAttributeValue("style", ""));
            var bgMatch = BackgroundImageRegex().Match(style);
            if (bgMatch.Success) imageUrl = bgMatch.Groups[1].Value;
        }
        if (imageUrl is null)
        {
            var img = anchor.SelectSingleNode(".//img[@src]");
            if (img is not null)
            {
                var src = img.GetAttributeValue("src", "");
                if (!string.IsNullOrEmpty(src)) imageUrl = src;
            }
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

    [GeneratedRegex(@"url\('([^']+)'\)")]
    private static partial Regex BackgroundImageRegex();
}
