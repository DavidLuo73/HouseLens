using HouseLens.Application.Crawling;
using HouseLens.Domain.Enums;
using HtmlAgilityPack;
using Microsoft.Extensions.Logging;
using System.Text.RegularExpressions;

namespace HouseLens.Infrastructure.Crawling.Scrapers;

/// <summary>
/// 永慶房屋買屋網（buy.yungching.com.tw）爬蟲。
/// 含永慶直營與有巢氏加盟物件，使用 Angular SSR，物件卡片直接內嵌於 HTML。
/// 網站具有 TLS 指紋辨識等強力反爬機制，以 PlaywrightFetcher（真實 Chromium）繞過。
/// 列表頁需以總價區間縮小範圍，否則會退化成預設排序的「熱門」清單，在有限分頁內
/// 幾乎抓不到目標行政區符合價格的物件：/list/{城市}-{行政區}_c/-{總價}_price，
/// 分頁：?pg=N（N≥2）。
/// 注意：網站 URL 上的「{型態}_type」區段實測對結果無任何篩選效果（不論帶入合法
/// 型態、亂數字串、或省略，回傳結果與筆數皆相同），型態純粹由前端「已選條件」
/// 麵包屑顯示，並未真正套用；因此型態過濾完全依賴本檔 ResidentialTypes 白名單於
/// client-side 執行。
/// </summary>
public partial class YungchingScraper(PlaywrightFetcher fetcher, ILogger<YungchingScraper> logger) : ISourceScraper
{
    public SourceSite SourceSite => SourceSite.Yungching;

    private const string BaseUrl = "https://buy.yungching.com.tw";
    private const int MaxPagesPerDistrict = 30; // 禮貌性上限，避免過量請求

    // 住宅型態白名單（電梯大廈=住宅大樓、無電梯公寓=公寓、華廈）；
    // 不在此清單的型態（辦公商業大樓、廠辦、店面、套房、土地、其他）一律過濾。
    private static readonly HashSet<string> ResidentialTypes = new(StringComparer.Ordinal)
    {
        "公寓", "住宅大樓", "華廈",
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
        IReadOnlyDictionary<string, DistrictCriteria> districtCriteria,
        IProgress<ScraperDistrictProgress>? progress,
        Func<IReadOnlyList<PropertyDto>, Task>? onDistrictCompleted = null,
        CancellationToken cancellationToken = default)
    {
        var results = new List<PropertyDto>();

        // 暖機：訪永慶首頁讓 WAF 設置 challenge cookies，後續列表頁請求會帶著這些 cookies。
        // 即使首頁返回 403，WAF 可能仍透過 Set-Cookie 設置驗證 token，
        // 下次請求帶著該 token 才會被放行。
        logger.LogInformation("Yungching: warming up via homepage {BaseUrl}", BaseUrl);
        await fetcher.FetchAsync(BaseUrl, cancellationToken);

        var validDistricts = districtCriteria
            .Where(kv => DistrictMap.ContainsKey(kv.Key))
            .ToList();
        var total = validDistricts.Count;

        for (var i = 0; i < validDistricts.Count; i++)
        {
            var (district, criteria) = validDistricts[i];
            var maxPrice = criteria.MaxTotalPrice;
            var city = DistrictMap[district];

            progress?.Report(new(district, i, total, IsStarting: true, FetchedCount: 0));

            var districtResults = await FetchDistrictAsync(district, city, (int)maxPrice, cancellationToken);
            results.AddRange(districtResults);

            progress?.Report(new(district, i, total, IsStarting: false, FetchedCount: districtResults.Count));
            logger.LogInformation("Yungching district {District} (max={Max}萬): {Count} listings",
                district, (int)maxPrice, districtResults.Count);

            if (onDistrictCompleted is not null) await onDistrictCompleted(districtResults);
        }

        var unknownDistricts = districtCriteria.Keys.Where(d => !DistrictMap.ContainsKey(d));
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

        var encodedCity = Uri.EscapeDataString(city);
        var encodedDistrict = Uri.EscapeDataString(district);
        var priceSegment = maxWan > 0 ? $"/-{maxWan}_price" : "";
        var listBase = $"{BaseUrl}/list/{encodedCity}-{encodedDistrict}_c{priceSegment}";

        for (var page = 1; page <= MaxPagesPerDistrict; page++)
        {
            var url = page == 1 ? listBase : $"{listBase}?pg={page}";

            // Playwright 以真實 Chromium 導航，Referer 與 Sec-Fetch-* 均由瀏覽器自動附加
            var html = await fetcher.FetchAsync(url, ct);
            if (html is null)
            {
                logger.LogWarning("Yungching: failed to fetch {Url}", url);
                break;
            }

            var pageResults = ParseListings(html, city, district, maxWan, seen);
            logger.LogInformation("Yungching {District} page {Page}: {Count} listings", district, page, pageResults.Count);

            // 每頁實際筆數不固定（置頂廣告、型態/價格篩選皆會影響計數），不可用固定 PageSize 判斷最後一頁；
            // 超過最後一頁時網站會回傳與前一頁相同內容，seen 去重後這裡自然變成 0，才是可靠的結束訊號。
            if (pageResults.Count == 0) break;
            all.AddRange(pageResults);
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

        // Client-side 總價上限過濾（URL 已依總價區間篩選，此為精確二次確認）
        if (maxWan > 0 && totalPrice > maxWan) return null;

        // 圖片（img-wrapper 內第一張 img 的 src）
        // 永慶 CDN（yccdn.yungching.com.tw/v1/image/）支援 width= 參數調整尺寸，
        // 列表頁縮圖為 width=480，替換為 1200 以取得高解析度圖片。
        // 注意：屬性值需先 DeEntitize，HtmlAgilityPack 不會自動解碼 src 內的 &amp;，
        // 否則存下的網址會殘留字面上的「&amp;」，導致 width 參數解析失敗、CDN 退回小尺寸圖。
        string? imageUrl = null;
        var imgNode = card.SelectSingleNode(".//*[contains(@class,'img-wrapper')]//img[@src]");
        if (imgNode is not null)
        {
            var src = HtmlEntity.DeEntitize(imgNode.GetAttributeValue("src", ""));
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
