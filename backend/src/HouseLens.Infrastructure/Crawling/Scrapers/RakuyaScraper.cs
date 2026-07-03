using HouseLens.Application.Crawling;
using HouseLens.Domain.Enums;
using HtmlAgilityPack;
using Microsoft.Extensions.Logging;
using System.Text.RegularExpressions;

namespace HouseLens.Infrastructure.Crawling.Scrapers;

/// <summary>
/// 樂屋網（www.rakuya.com.tw）中古屋買賣爬蟲。
/// 網站具有 TLS 指紋辨識等反爬機制，以 PlaywrightFetcher（真實 Chromium）繞過。
/// 分頁：&amp;page=N；URL 由 BuildSearchUrl 依 DistrictCriteria 組成：
/// /sell/result?zipcode={郵遞區號}&amp;usecode={用途}&amp;typecode={型態}&amp;price=~{上限萬}&amp;size={坪數}~&amp;room={房數}。
/// 物件識別碼（ehid）取自 section[data-ehid] 屬性；詳情頁：/sell_item/info?ehid={ehid}。
/// </summary>
public partial class RakuyaScraper(PlaywrightFetcher fetcher, ILogger<RakuyaScraper> logger) : ISourceScraper
{
    public SourceSite SourceSite => SourceSite.Rakuya;

    private const string BaseUrl = "https://www.rakuya.com.tw";
    private const int MaxPagesPerDistrict = 30; // 防護上限（正常依「共 N 筆」總數與無新物件判斷提早停止）

    // 住宅型態白名單；商辦/店面/廠辦/土地等一律過濾。
    private static readonly HashSet<string> ResidentialTypes = new(StringComparer.Ordinal)
    {
        "公寓", "電梯大廈", "住宅大樓", "華廈", "透天厝", "別墅", "透天別墅", "住宅", "樓中樓", "套房",
    };

    // 行政區名 → 郵遞區號（樂屋網以 zipcode 參數指定行政區）
    private static readonly Dictionary<string, (string Zipcode, string City)> DistrictMap = new()
    {
        ["中和區"] = ("235", "新北市"),
        ["永和區"] = ("234", "新北市"),
        ["新店區"] = ("231", "新北市"),
        ["板橋區"] = ("220", "新北市"),
        ["樹林區"] = ("238", "新北市"),
        ["新莊區"] = ("242", "新北市"),
        ["中壢區"] = ("320", "桃園市"),
        ["桃園區"] = ("330", "桃園市"),
    };

    public async Task<IReadOnlyList<PropertyDto>> FetchAsync(
        IReadOnlyDictionary<string, DistrictCriteria> districtCriteria,
        IProgress<ScraperDistrictProgress>? progress,
        Func<IReadOnlyList<PropertyDto>, Task>? onDistrictCompleted = null,
        CancellationToken cancellationToken = default)
    {
        var results = new List<PropertyDto>();

        // 暖機：訪首頁讓 WAF 設置 challenge cookies
        logger.LogInformation("Rakuya: warming up via homepage {BaseUrl}", BaseUrl);
        await fetcher.FetchAsync(BaseUrl, cancellationToken);

        var validDistricts = districtCriteria
            .Where(kv => DistrictMap.ContainsKey(kv.Key))
            .ToList();
        var total = validDistricts.Count;

        for (var i = 0; i < validDistricts.Count; i++)
        {
            var (district, criteria) = validDistricts[i];
            var (zipcode, city) = DistrictMap[district];

            // 每 3 個行政區重新暖機，模擬自然瀏覽行為，避免 CF 行為分析觸發 re-challenge。
            if (i > 0 && i % 3 == 0)
            {
                logger.LogInformation("Rakuya: re-warming up before district {District}", district);
                await fetcher.FetchAsync(BaseUrl, cancellationToken);
            }

            progress?.Report(new(district, i, total, IsStarting: true, FetchedCount: 0));

            var districtResults = await FetchDistrictAsync(district, city, zipcode, criteria, cancellationToken);
            results.AddRange(districtResults);

            progress?.Report(new(district, i, total, IsStarting: false, FetchedCount: districtResults.Count));
            logger.LogInformation("Rakuya district {District} (max={Max}萬): {Count} listings",
                district, criteria.MaxTotalPrice, districtResults.Count);

            if (onDistrictCompleted is not null) await onDistrictCompleted(districtResults);
        }

        var unknownDistricts = districtCriteria.Keys.Where(d => !DistrictMap.ContainsKey(d));
        foreach (var d in unknownDistricts)
            logger.LogWarning("Rakuya: unknown district (not in DistrictMap): {District}", d);

        return results;
    }

    /// <summary>
    /// 依 DistrictCriteria 組出樂屋網搜尋 URL（不含 page 參數）。
    /// usecode（1住宅/2商用/6住辦/3車位）、typecode（R1公寓/R2大樓華廈/R3套房/R4別墅/R5透天厝/R6樓中樓）、
    /// price=~N（0~N 萬）、size=N~（N 坪以上）、room=2,3,4,5~（房數）、
    /// age=~N（屋齡 N 年以下）、other=PF,PM（停車位：PF 平面 / PM 機械）皆為伺服器端篩選。
    /// </summary>
    public static string BuildSearchUrl(string zipcode, DistrictCriteria criteria)
    {
        var useCode = string.IsNullOrWhiteSpace(criteria.UseCode) ? "1" : criteria.UseCode.Trim();
        var typeCodes = string.IsNullOrWhiteSpace(criteria.TypeCodes) ? "R1,R2" : criteria.TypeCodes.Trim();
        var priceParam = criteria.MaxTotalPrice > 0 ? $"&price=~{criteria.MaxTotalPrice:0}" : "";
        var sizeParam = criteria.MinSizePing > 0 ? $"&size={criteria.MinSizePing:0.##}~" : "";
        var roomParam = string.IsNullOrWhiteSpace(criteria.Rooms) ? "" : $"&room={criteria.Rooms.Trim()}";
        var ageParam = criteria.MaxAgeYears > 0 ? $"&age=~{criteria.MaxAgeYears}" : "";
        var otherParam = string.IsNullOrWhiteSpace(criteria.ParkingCodes) ? "" : $"&other={criteria.ParkingCodes.Trim()}";
        return $"{BaseUrl}/sell/result?zipcode={zipcode}&usecode={useCode}&typecode={typeCodes}{priceParam}{sizeParam}{roomParam}{ageParam}{otherParam}";
    }

    private async Task<List<PropertyDto>> FetchDistrictAsync(
        string district, string city, string zipcode, DistrictCriteria criteria, CancellationToken ct)
    {
        var all = new List<PropertyDto>();
        var seen = new HashSet<string>();
        var maxWan = criteria.MaxTotalPrice;
        var maxAge = criteria.MaxAgeYears;
        var baseSearchUrl = BuildSearchUrl(zipcode, criteria);

        for (var page = 1; page <= MaxPagesPerDistrict; page++)
        {
            // 伺服器端篩選減少無效物件下載量；client-side 的型態/價格過濾仍保留作為防線。
            var url = $"{baseSearchUrl}&page={page}";

            // 每次都從首頁導覽過來，讓 CF 看到自然的導覽歷史（Homepage → Search Result）。
            var html = await fetcher.FetchAsync(url, ct, navigateFromUrl: BaseUrl);

            // 第一頁若取得 null 或 0 筆（可能是未偵測到的 CF 挑戰），重新暖機後重試一次。
            if (page == 1 && (html is null || ParseListings(html, city, district, maxWan, null, maxAge).Count == 0))
            {
                logger.LogWarning("Rakuya: page 1 no results for {District}, re-warming and retrying...", district);
                await fetcher.FetchAsync(BaseUrl, ct);
                html = await fetcher.FetchAsync(url, ct, navigateFromUrl: BaseUrl);
            }

            if (html is null)
            {
                logger.LogWarning("Rakuya: failed to fetch {Url}", url);
                break;
            }

            var pageResults = ParseListings(html, city, district, maxWan, seen, maxAge);
            logger.LogInformation("Rakuya {District} page {Page}: {Count} listings", district, page, pageResults.Count);

            // 本頁無任何「新」物件（去重後）即視為抓完：樂屋網對超出範圍的 page 會重複回傳最後一頁內容。
            // 注意：不可用「過濾後筆數 < 每頁容量」判斷最後一頁 —— 型態/價格過濾與去重會使
            // 每頁有效筆數低於頁面卡片數，曾導致第一頁就誤判提早終止。
            if (pageResults.Count == 0) break;
            all.AddRange(pageResults);

            // 從「共 N 筆」計算總頁數，抓完即停，避免多打一次無效請求。
            var totalMatch = TotalCountRegex().Match(html);
            if (totalMatch.Success && int.TryParse(totalMatch.Groups[1].Value, out var totalCount))
            {
                var cardsPerPage = Math.Max(pageResults.Count, 1);
                if (page == 1 && totalCount <= cardsPerPage) break;
                // 用頁面原始卡片數較準，但保守處理：只有當已確定超過總數所需頁數才停
                if (all.Count >= totalCount) break;
            }
        }

        return all;
    }

    /// <summary>
    /// 解析樂屋網列表頁 HTML → PropertyDto。
    /// 選擇器基於 section.search-obj 結構，不依賴 data-v-* 框架屬性。
    /// </summary>
    public List<PropertyDto> ParseListings(
        string html, string city, string district, decimal maxWan, HashSet<string>? seen = null, int maxAgeYears = 0)
    {
        var results = new List<PropertyDto>();
        var doc = new HtmlDocument();
        doc.LoadHtml(html);

        var cards = doc.DocumentNode.SelectNodes("//section[contains(@class,'search-obj')]");
        if (cards is null)
        {
            logger.LogWarning("Rakuya: no search-obj cards found (html len={Len}) for {District}",
                html.Length, district);
            return results;
        }

        foreach (var card in cards)
        {
            try
            {
                var dto = ParseCard(card, city, district, maxWan, maxAgeYears);
                if (dto is null) continue;

                if (seen is not null && !seen.Add(dto.SourceListingKey)) continue;

                results.Add(dto);
            }
            catch (Exception ex)
            {
                logger.LogDebug("Rakuya: failed to parse card: {Msg}", ex.Message);
            }
        }

        return results;
    }

    private PropertyDto? ParseCard(HtmlNode card, string city, string district, decimal maxWan, int maxAgeYears = 0)
    {
        // 物件識別碼（ehid）取自 data-ehid 屬性
        var ehid = card.GetAttributeValue("data-ehid", "").Trim();
        if (string.IsNullOrEmpty(ehid)) return null;
        var url = $"{BaseUrl}/sell_item/info?ehid={ehid}";

        // 建物型態（info__detail-info > ul > li 第一項）
        var typeLi = card.SelectSingleNode(".//div[contains(@class,'info__detail-info')]//ul/li[1]");
        var buildingType = CleanText(typeLi?.InnerText) ?? "";
        if (!string.IsNullOrEmpty(buildingType) && !ResidentialTypes.Contains(buildingType))
            return null;

        // 標題
        var titleNode = card.SelectSingleNode(".//div[contains(@class,'card__head')]//h2");
        var title = CleanText(titleNode?.InnerText) ?? district;

        // 地址（info__geo--community 優先，fallback 到 info__geo--area）
        var communityNode = card.SelectSingleNode(".//span[@class='info__geo--community']");
        var address = CleanText(communityNode?.InnerText)
            ?? CleanText(card.SelectSingleNode(".//span[contains(@class,'info__geo--area')]")?.InnerText);

        // 樓層（li.info__floor）
        var floorNode = card.SelectSingleNode(".//li[contains(@class,'info__floor')]");
        var floor = CleanText(floorNode?.InnerText);

        // 屋齡（info__detail-info > ul > li 含「年」的項目）
        int? ageYears = null;
        var detailLis = card.SelectNodes(".//div[contains(@class,'info__detail-info')]//ul/li");
        if (detailLis is not null)
        {
            foreach (var li in detailLis)
            {
                var liText = li.InnerText?.Trim() ?? "";
                var ageMatch = AgeRegex().Match(liText);
                if (ageMatch.Success && decimal.TryParse(ageMatch.Groups[1].Value, out var ageDec))
                {
                    ageYears = (int)Math.Round(ageDec);
                    break;
                }
            }
        }

        // 坪數（主建優先，fallback 到總建）
        decimal areaPing = 0m;
        var spaceLis = card.SelectNodes(".//ul[contains(@class,'info__spaceList')]//li");
        if (spaceLis is not null)
        {
            string? mainAreaText = null;
            string? totalAreaText = null;
            foreach (var li in spaceLis)
            {
                var liText = li.InnerText?.Trim() ?? "";
                if (liText.StartsWith("主建", StringComparison.Ordinal)) mainAreaText = liText;
                else if (liText.StartsWith("總建", StringComparison.Ordinal)) totalAreaText = liText;
            }
            var areaText = mainAreaText ?? totalAreaText ?? "";
            var areaMatch = AreaRegex().Match(areaText);
            if (areaMatch.Success) decimal.TryParse(areaMatch.Groups[1].Value, out areaPing);
        }

        // 停車位（group__tags 下含「車位」的 feature tag）
        var parkingNode = card.SelectSingleNode(
            ".//div[contains(@class,'group__tags')]//span[contains(@class,'is--feature') and contains(text(),'車位')]");
        var hasParking = parkingNode is not null;

        // 總價（info__price--total > b，千分位逗號去除，單位萬）
        var priceNode = card.SelectSingleNode(".//span[contains(@class,'info__price--total')]//b");
        if (priceNode is null) return null;
        var priceText = priceNode.InnerText.Replace(",", "").Trim();
        if (!decimal.TryParse(priceText, out var totalPrice) || totalPrice <= 0) return null;

        // Client-side 價格上限過濾
        if (maxWan > 0 && totalPrice > maxWan) return null;

        // Client-side 屋齡上限過濾（伺服器端 age=~N 已篩，這裡是防線；未解析出屋齡者不過濾）
        if (maxAgeYears > 0 && ageYears.HasValue && ageYears.Value > maxAgeYears) return null;

        // 圖片（card__photo 內第一張 img 的 src，去除 query string）
        string? imageUrl = null;
        var imgNode = card.SelectSingleNode(".//div[contains(@class,'card__photo')]//img[@src]");
        if (imgNode is not null)
        {
            var src = imgNode.GetAttributeValue("src", "");
            if (!string.IsNullOrEmpty(src) && src.StartsWith("http", StringComparison.Ordinal))
                imageUrl = src.Split('?')[0];
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
            SourceSite: SourceSite.Rakuya,
            SourceListingKey: ehid,
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
        return string.IsNullOrEmpty(decoded) ? null : decoded;
    }

    [GeneratedRegex(@"([\d.]+)\s*年")]
    private static partial Regex AgeRegex();

    [GeneratedRegex(@"[主總]建([\d.]+)坪")]
    private static partial Regex AreaRegex();

    [GeneratedRegex(@"\s+")]
    private static partial Regex WhitespaceRegex();

    [GeneratedRegex(@"共\s*(\d+)\s*筆")]
    private static partial Regex TotalCountRegex();
}
