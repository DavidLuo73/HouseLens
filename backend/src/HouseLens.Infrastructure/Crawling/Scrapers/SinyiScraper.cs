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

    // 信義建物型態 slug 白名單（URL 第 2 段 {…}-type）
    private static readonly string[] SinyiTypeSlugs = ["apartment", "dalou", "huaxia", "townhouse", "villa"];

    // 樂屋網 typecode → 信義型態 slug 對映（平台未設定信義專屬 TypeCodes 時的回退）
    private static readonly Dictionary<string, string[]> RakuyaTypeToSinyi = new(StringComparer.OrdinalIgnoreCase)
    {
        ["R1"] = ["apartment"],           // 公寓
        ["R2"] = ["dalou", "huaxia"],     // 大樓/華廈
        ["R4"] = ["villa"],               // 別墅
        ["R5"] = ["townhouse"],           // 透天厝
    };

    // 信義停車位 slug 白名單（URL 車位段），依官方順序排列
    private static readonly string[] SinyiParkingSlugs =
        ["plane", "auto", "mix", "mechanical", "firstfloor", "tower", "other", "yesparking"];

    // 樂屋網停車位代碼 → 信義車位 slug 對映（回退用）：PF 平面類 / PM 機械類
    private static readonly Dictionary<string, string[]> RakuyaParkingToSinyi = new(StringComparer.OrdinalIgnoreCase)
    {
        ["PF"] = ["plane", "auto", "firstfloor"],
        ["PM"] = ["mix", "mechanical", "tower"],
    };

    // 行政區 → (城市英文 slug, 城市中文名, 信義 zip)。zip 與 slug 皆由 __NEXT_DATA__ zipCodeTW 驗證。
    private static readonly Dictionary<string, (string CitySlug, string City, string Zip)> DistrictMap = new()
    {
        // 新北市
        ["板橋區"] = ("NewTaipei-city", "新北市", "220"),
        ["汐止區"] = ("NewTaipei-city", "新北市", "221"),
        ["深坑區"] = ("NewTaipei-city", "新北市", "222"),
        ["瑞芳區"] = ("NewTaipei-city", "新北市", "224"),
        ["新店區"] = ("NewTaipei-city", "新北市", "231"),
        ["永和區"] = ("NewTaipei-city", "新北市", "234"),
        ["中和區"] = ("NewTaipei-city", "新北市", "235"),
        ["土城區"] = ("NewTaipei-city", "新北市", "236"),
        ["三峽區"] = ("NewTaipei-city", "新北市", "237"),
        ["樹林區"] = ("NewTaipei-city", "新北市", "238"),
        ["鶯歌區"] = ("NewTaipei-city", "新北市", "239"),
        ["三重區"] = ("NewTaipei-city", "新北市", "241"),
        ["新莊區"] = ("NewTaipei-city", "新北市", "242"),
        ["泰山區"] = ("NewTaipei-city", "新北市", "243"),
        ["林口區"] = ("NewTaipei-city", "新北市", "244"),
        ["蘆洲區"] = ("NewTaipei-city", "新北市", "247"),
        ["五股區"] = ("NewTaipei-city", "新北市", "248"),
        ["八里區"] = ("NewTaipei-city", "新北市", "249"),
        ["淡水區"] = ("NewTaipei-city", "新北市", "251"),
        // 桃園市
        ["中壢區"] = ("Taoyuan-city", "桃園市", "320"),
        ["平鎮區"] = ("Taoyuan-city", "桃園市", "324"),
        ["龍潭區"] = ("Taoyuan-city", "桃園市", "325"),
        ["楊梅區"] = ("Taoyuan-city", "桃園市", "326"),
        ["新屋區"] = ("Taoyuan-city", "桃園市", "327"),
        ["觀音區"] = ("Taoyuan-city", "桃園市", "328"),
        ["桃園區"] = ("Taoyuan-city", "桃園市", "330"),
        ["龜山區"] = ("Taoyuan-city", "桃園市", "333"),
        ["八德區"] = ("Taoyuan-city", "桃園市", "334"),
        ["大溪區"] = ("Taoyuan-city", "桃園市", "335"),
        ["大園區"] = ("Taoyuan-city", "桃園市", "337"),
        ["蘆竹區"] = ("Taoyuan-city", "桃園市", "338"),
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
            var map = DistrictMap[district];

            progress?.Report(new(district, i, total, IsStarting: true, FetchedCount: 0));

            var districtResults = await FetchDistrictAsync(
                district, map.City, map.CitySlug, map.Zip, criteria, cancellationToken);
            results.AddRange(districtResults);

            progress?.Report(new(district, i, total, IsStarting: false, FetchedCount: districtResults.Count));
            logger.LogInformation("Sinyi district {District} (max={Max}萬): {Count} listings",
                district, (int)criteria.MaxTotalPrice, districtResults.Count);

            if (onDistrictCompleted is not null) await onDistrictCompleted(districtResults);
        }

        var unknownDistricts = districtCriteria.Keys.Where(d => !DistrictMap.ContainsKey(d));
        foreach (var d in unknownDistricts)
            logger.LogWarning("Sinyi: unknown district (not in DistrictMap): {District}", d);

        return results;
    }

    /// <summary>
    /// 依 DistrictCriteria 組出信義搜尋列表 URL。路徑段依序為（無設定者省略）：
    /// 價格 {0-N}-price／型態 {…}-type／車位 {…-yesparking}／坪數 {N}-up-area／
    /// 屋齡 0-{N}-year（N 年以下）／房數 {N}-up-roomtotal／城市／{zip}-zip／排序／頁次。
    /// 各 slug 皆經實站驗證（price、type、area、year、roomtotal、車位段）。
    /// </summary>
    public static string BuildSearchUrl(string citySlug, string zip, DistrictCriteria criteria, int page)
    {
        var segments = new List<string>();

        if (criteria.MaxTotalPrice > 0)
            segments.Add($"0-{criteria.MaxTotalPrice:0}-price");

        segments.Add(BuildTypeSlug(criteria.TypeCodes));

        var parking = BuildParkingSlug(criteria.ParkingCodes);
        if (parking is not null) segments.Add(parking);

        if (criteria.MinSizePing > 0)
            segments.Add($"{criteria.MinSizePing:0}-up-area");

        if (criteria.MaxAgeYears > 0)
            segments.Add($"0-{criteria.MaxAgeYears}-year");

        var minRooms = ParseMinRooms(criteria.Rooms);
        if (minRooms > 0)
            segments.Add($"{minRooms}-up-roomtotal");

        segments.Add(citySlug);
        segments.Add($"{zip}-zip");
        segments.Add("default-desc");
        segments.Add(page.ToString());

        return $"{BaseUrl}/buy/list/{string.Join('/', segments)}";
    }

    /// <summary>建物型態段：優先採信義 slug；樂屋網 R 代碼則對映；無法辨識時回退全住宅型態。</summary>
    private static string BuildTypeSlug(string typeCodes)
    {
        var codes = SplitCodes(typeCodes);
        var slugs = codes.Where(c => SinyiTypeSlugs.Contains(c, StringComparer.OrdinalIgnoreCase))
            .Select(c => c.ToLowerInvariant())
            .Concat(codes.SelectMany(c => RakuyaTypeToSinyi.GetValueOrDefault(c, [])))
            .Distinct()
            .ToList();
        if (slugs.Count == 0) return ResidentialTypeSlug;

        // 依信義官方順序輸出，避免同組合產生不同 URL
        var ordered = SinyiTypeSlugs.Where(slugs.Contains);
        return $"{string.Join('-', ordered)}-type";
    }

    /// <summary>
    /// 車位段：採信義 slug（含 yesparking）；樂屋網 PF/PM 代碼則對映為對應車位型式並強制 yesparking。
    /// 空／無法辨識 → null（不加車位條件）。
    /// </summary>
    private static string? BuildParkingSlug(string parkingCodes)
    {
        var codes = SplitCodes(parkingCodes);
        var slugs = codes.Where(c => SinyiParkingSlugs.Contains(c, StringComparer.OrdinalIgnoreCase))
            .Select(c => c.ToLowerInvariant())
            .Concat(codes.SelectMany(c => RakuyaParkingToSinyi.GetValueOrDefault(c, [])))
            .Distinct()
            .ToList();
        if (slugs.Count == 0) return null;

        // 有指定任何車位型式即代表「必須有車位」
        if (slugs.Any(s => s != "yesparking") && !slugs.Contains("yesparking"))
            slugs.Add("yesparking");

        var ordered = SinyiParkingSlugs.Where(slugs.Contains);
        return string.Join('-', ordered);
    }

    /// <summary>從 Rooms 條件（如 "2,3,5~"）取最小房數，信義以 {N}-up-roomtotal（N 房以上）表達。</summary>
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
        string district, string city, string citySlug, string zip, DistrictCriteria criteria, CancellationToken ct)
    {
        var all = new List<PropertyDto>();

        for (var page = 1; page <= MaxPagesPerDistrict; page++)
        {
            // 例：/buy/list/0-1000-price/apartment-dalou-huaxia-type/plane-auto-…-yesparking/20-up-area/0-30-year/2-up-roomtotal/NewTaipei-city/234-zip/default-desc/1
            var url = BuildSearchUrl(citySlug, zip, criteria, page);

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
