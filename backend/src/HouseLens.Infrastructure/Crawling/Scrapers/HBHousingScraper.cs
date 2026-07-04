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
/// 「熱門推薦」清單，在有限分頁內幾乎抓不到目標行政區的物件。
/// URL 由 BuildSearchUrl 依 DistrictCriteria 組成，路徑段依序（實站驗證）：
/// /buyhouse/{城市}/{zip}/{型態,…}-style/{總價}-down-price/apartment-type/
/// area-{坪}-up-area/{屋齡}-down-age/{房}_up_room-pattern/parking-tag，
/// 分頁：.../{N}-page（N≥2）。
/// </summary>
public partial class HBHousingScraper(PlaywrightFetcher fetcher, ILogger<HBHousingScraper> logger) : ISourceScraper
{
    public SourceSite SourceSite => SourceSite.HBHousing;

    private const string BaseUrl = "https://www.hbhousing.com.tw";
    private const int PageSize = 10;               // 住商每頁 10 筆
    private const int MaxPagesPerDistrict = 100;   // 禮貌性上限，避免過量請求（大量體行政區可達 60~70 頁）

    // 建物型態 URL 代碼（{…}-style 段），依官方順序排列：
    // noelevator 無電梯公寓、elevator 大樓(11樓以上)、mansion 華廈(10樓以下)
    private static readonly string[] HBTypeCodes = ["noelevator", "elevator", "mansion"];

    // URL 型態代碼 → 卡片細節列型態用詞（client-side 白名單用）
    private static readonly Dictionary<string, string[]> TypeCodeToCardTypes = new(StringComparer.OrdinalIgnoreCase)
    {
        ["noelevator"] = ["公寓"],
        ["elevator"] = ["大樓"],
        ["mansion"] = ["華廈"],
    };

    // 樂屋網 typecode → 住商型態代碼對映（平台未設定住商專屬 TypeCodes 時的回退）
    private static readonly Dictionary<string, string[]> RakuyaTypeToHB = new(StringComparer.OrdinalIgnoreCase)
    {
        ["R1"] = ["noelevator"],
        ["R2"] = ["elevator", "mansion"],
    };

    // 預設住宅型態白名單（未設定 TypeCodes 時）；
    // 不在此清單的型態（辦公室、店面、廠房）一律過濾。
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
        ["泰山區"] = new("新北市", "243"), ["林口區"] = new("新北市", "244"), ["汐止區"] = new("新北市", "221"),
        ["深坑區"] = new("新北市", "222"), ["瑞芳區"] = new("新北市", "224"), ["八里區"] = new("新北市", "249"),
        ["淡水區"] = new("新北市", "251"),
        // 桃園市
        ["桃園區"] = new("桃園市", "330"), ["中壢區"] = new("桃園市", "320"), ["平鎮區"] = new("桃園市", "324"),
        ["龜山區"] = new("桃園市", "333"), ["八德區"] = new("桃園市", "334"), ["龍潭區"] = new("桃園市", "325"),
        ["楊梅區"] = new("桃園市", "326"), ["新屋區"] = new("桃園市", "327"), ["觀音區"] = new("桃園市", "328"),
        ["大溪區"] = new("桃園市", "335"), ["大園區"] = new("桃園市", "337"), ["蘆竹區"] = new("桃園市", "338"),
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
            var criteria = districtCriteria[district];
            var info = DistrictZipMap[district];

            progress?.Report(new(district, i, total, IsStarting: true, FetchedCount: 0));
            logger.LogInformation("HBHousing: scraping {District} (max={Max}萬)", district, criteria.MaxTotalPrice);

            var districtResults = await FetchDistrictAsync(info.City, district, info.Zip, criteria, cancellationToken);
            results.AddRange(districtResults);

            progress?.Report(new(district, i, total, IsStarting: false, FetchedCount: districtResults.Count));
            logger.LogInformation("HBHousing: {District} done, {Count} listings", district, districtResults.Count);

            if (onDistrictCompleted is not null) await onDistrictCompleted(districtResults);
        }

        return results;
    }

    /// <summary>
    /// 依 DistrictCriteria 組出住商搜尋列表 URL。路徑段依序為（無設定者省略）：
    /// 城市／{zip} 行政區／{…}-style 型態／{N}-down-price 總價／apartment-type 住宅／
    /// area-{N}-up-area 坪數／{N}-down-age 屋齡／{N}_up_room-pattern 房數／parking-tag 車位；
    /// 分頁 {N}-page（第 1 頁省略）。各段皆經實站驗證有 server-side 篩選效果。
    /// </summary>
    public static string BuildSearchUrl(string city, string zip, DistrictCriteria criteria, int page)
    {
        var segments = new List<string>
        {
            Uri.EscapeDataString(city),
            zip,
            $"{string.Join('-', ResolveTypeCodes(criteria.TypeCodes))}-style",
        };

        if (criteria.MaxTotalPrice > 0)
            segments.Add($"{(int)criteria.MaxTotalPrice}-down-price");

        // 用途固定住宅（UseCode 其他值為樂屋網商用/車位等，不適用住商住宅搜尋）
        if (string.IsNullOrWhiteSpace(criteria.UseCode) || criteria.UseCode.Trim() == "1")
            segments.Add("apartment-type");

        if (criteria.MinSizePing > 0)
            segments.Add($"area-{criteria.MinSizePing:0}-up-area");

        if (criteria.MaxAgeYears > 0)
            segments.Add($"{criteria.MaxAgeYears}-down-age");

        var minRooms = ParseMinRooms(criteria.Rooms);
        if (minRooms > 0)
            segments.Add($"{minRooms}_up_room-pattern");

        // 停車位：住商僅支援「有車位」單一條件（不分平面/機械），有勾任何車位代碼即帶入
        if (!string.IsNullOrWhiteSpace(criteria.ParkingCodes))
            segments.Add("parking-tag");

        if (page > 1)
            segments.Add($"{page}-page");

        return $"{BaseUrl}/buyhouse/{string.Join('/', segments)}";
    }

    /// <summary>型態段：優先採住商代碼（noelevator/elevator/mansion）；樂屋網 R 代碼則對映；無法辨識時回退全部型態。</summary>
    private static List<string> ResolveTypeCodes(string typeCodes)
    {
        var codes = SplitCodes(typeCodes);
        var resolved = codes.Where(c => HBTypeCodes.Contains(c, StringComparer.OrdinalIgnoreCase))
            .Select(c => c.ToLowerInvariant())
            .Concat(codes.SelectMany(c => RakuyaTypeToHB.GetValueOrDefault(c, [])))
            .Distinct()
            .ToList();
        if (resolved.Count == 0) return [.. HBTypeCodes];

        // 依官方順序輸出，避免同組合產生不同 URL
        return HBTypeCodes.Where(resolved.Contains).ToList();
    }

    /// <summary>依 TypeCodes 建立卡片型態白名單；未設定時回傳預設住宅白名單。</summary>
    public static HashSet<string> BuildAllowedCardTypes(string typeCodes)
    {
        if (SplitCodes(typeCodes).Length == 0) return ResidentialTypes;

        var allowed = ResolveTypeCodes(typeCodes)
            .SelectMany(c => TypeCodeToCardTypes.GetValueOrDefault(c, []))
            .ToHashSet(StringComparer.Ordinal);
        return allowed.Count == 0 ? ResidentialTypes : allowed;
    }

    /// <summary>從 Rooms 條件（如 "2,3,5~"）取最小房數，住商以 {N}_up_room-pattern（N 房以上）表達。</summary>
    private static int ParseMinRooms(string rooms)
    {
        var values = SplitCodes(rooms)
            .Select(r => int.TryParse(r.TrimEnd('~'), out var n) ? n : 0)
            .Where(n => n > 0)
            .ToList();
        return values.Count == 0 ? 0 : values.Min();
    }

    private static string[] SplitCodes(string s) =>
        string.IsNullOrWhiteSpace(s)
            ? []
            : s.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries);

    private async Task<List<PropertyDto>> FetchDistrictAsync(
        string city,
        string district,
        string zip,
        DistrictCriteria criteria,
        CancellationToken ct)
    {
        // seen 限定此 district 的分頁去重；跨 district 重複由 DB upsert 處理
        var seen = new HashSet<string>();
        var all = new List<PropertyDto>();
        var maxWan = criteria.MaxTotalPrice;
        var allowedTypes = BuildAllowedCardTypes(criteria.TypeCodes);

        for (var page = 1; page <= MaxPagesPerDistrict; page++)
        {
            var url = BuildSearchUrl(city, zip, criteria, page);

            var html = await fetcher.FetchAsync(url, ct);
            if (html is null)
            {
                logger.LogWarning("HBHousing: failed to fetch {Url}", url);
                break;
            }

            var seenBefore = seen.Count;
            var pageResults = ParseListings(html, city, district, maxWan, seen, allowedTypes, out var rawCardCount);
            logger.LogInformation("HBHousing: {District} list page {Page}: {Count}/{Raw} listings",
                district, page, pageResults.Count, rawCardCount);

            all.AddRange(pageResults);

            // 最後一頁判斷須以「頁上原始卡片數」為準：client-side 型態/價格過濾與去重
            // 會讓 pageResults 少於 PageSize，若以過濾後筆數判斷會提早中斷、漏抓後續分頁。
            if (rawCardCount < PageSize) break;

            // 超過最後一頁時站方會回傳與最後一頁相同內容：整頁滿版卡片卻沒有任何新 key
            // （去重全命中），視為重複頁結束，避免空轉到 MaxPagesPerDistrict。
            if (page > 1 && pageResults.Count == 0 && seen.Count == seenBefore && seenBefore > 0) break;
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
        HashSet<string>? seen = null,
        HashSet<string>? allowedTypes = null) =>
        ParseListings(html, queryCity, queryDistrict, maxWan, seen, allowedTypes, out _);

    public List<PropertyDto> ParseListings(
        string html,
        string queryCity,
        string queryDistrict,
        decimal maxWan,
        HashSet<string>? seen,
        HashSet<string>? allowedTypes,
        out int rawCardCount)
    {
        allowedTypes ??= ResidentialTypes;
        rawCardCount = 0;
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

        rawCardCount = cards.Count;

        foreach (var card in cards)
        {
            try
            {
                var dto = ParseCard(card, queryCity, queryDistrict, maxWan, allowedTypes);
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

    private PropertyDto? ParseCard(HtmlNode card, string queryCity, string queryDistrict, decimal maxWan, HashSet<string> allowedTypes)
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
        if (!string.IsNullOrEmpty(propertyType) && !allowedTypes.Contains(propertyType))
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
