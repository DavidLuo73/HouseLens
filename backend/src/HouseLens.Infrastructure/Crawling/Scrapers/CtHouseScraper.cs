using HouseLens.Application.Crawling;
using HouseLens.Domain.Enums;
using Microsoft.Extensions.Logging;
using System.Text.Json;
using System.Text.RegularExpressions;

namespace HouseLens.Infrastructure.Crawling.Scrapers;

/// <summary>
/// 中信房屋買屋網（buy.cthouse.com.tw）爬蟲。
/// 後端 API 為 POST /api/house_list.ashx，body {"arg":"{城市}-city/{區}-town/0-{maxWan}-price/","page":N}，
/// 伺服器端已完成城市/行政區/總價篩選，不需 Playwright。
/// robots.txt: User-agent: * / Allow: / （全域允許）
/// </summary>
public partial class CtHouseScraper(HttpFetcher fetcher, ILogger<CtHouseScraper> logger) : ISourceScraper
{
    public SourceSite SourceSite => SourceSite.CtHouse;

    private const string ApiUrl = "https://buy.cthouse.com.tw/api/house_list.ashx";
    private const string MediaBaseUrl = "https://media.cthouse.com.tw/photo";
    private const string HouseBaseUrl = "https://buy.cthouse.com.tw";
    private const int MaxPagesPerDistrict = 10;

    // 住宅用途碼（house_type_usage）：1=住宅，2=商業/工業
    private const int ResidentialUsage = 1;

    // 住宅型態碼白名單（house_type_class）：1=公寓/住宅，2=電梯大樓/華廈
    private static readonly HashSet<int> ResidentialTypeClasses = [1, 2];

    // 行政區 → 中信城市名（與 SeedData 對齊，僅追蹤的新北六區、桃園二區）
    private static readonly Dictionary<string, string> DistrictToCity = new()
    {
        ["中和區"] = "新北市",
        ["永和區"] = "新北市",
        ["新店區"] = "新北市",
        ["板橋區"] = "新北市",
        ["樹林區"] = "新北市",
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
        var seen = new HashSet<string>();

        var knownDistricts = districtMaxPrices.Keys.Where(d => DistrictToCity.ContainsKey(d)).ToList();
        var unknownDistricts = districtMaxPrices.Keys.Except(knownDistricts).ToList();
        foreach (var d in unknownDistricts)
            logger.LogWarning("CtHouse: unknown district (not in DistrictToCity): {District}", d);

        var total = knownDistricts.Count;

        for (var i = 0; i < knownDistricts.Count; i++)
        {
            var district = knownDistricts[i];
            var maxWan = districtMaxPrices[district];
            var city = DistrictToCity[district];

            progress?.Report(new(district, i, total, IsStarting: true, FetchedCount: 0));
            logger.LogInformation("CtHouse: scraping {City} {District} (max={Max}萬)", city, district, maxWan);

            var districtResults = await FetchDistrictAsync(city, district, maxWan, seen, cancellationToken);
            results.AddRange(districtResults);

            progress?.Report(new(district, i, total, IsStarting: false, FetchedCount: districtResults.Count));
            logger.LogInformation("CtHouse: {District} done, {Count} listings", district, districtResults.Count);
        }

        return results;
    }

    private async Task<List<PropertyDto>> FetchDistrictAsync(
        string city,
        string district,
        decimal maxWan,
        HashSet<string> seen,
        CancellationToken ct)
    {
        var all = new List<PropertyDto>();
        var maxWanInt = (int)maxWan;
        var arg = $"{city}-city/{district}-town/0-{maxWanInt}-price/";

        for (var page = 1; page <= MaxPagesPerDistrict; page++)
        {
            var body = JsonSerializer.Serialize(new { arg, page });
            var json = await fetcher.PostJsonAsync(ApiUrl, body, ct);
            if (json is null)
            {
                logger.LogWarning("CtHouse: failed to fetch {District} page {Page}", district, page);
                break;
            }

            int totalPages;
            try
            {
                using var doc = JsonDocument.Parse(json);
                var root = doc.RootElement;

                if (!root.TryGetProperty("RS", out var rs) || rs.GetString() != "OK")
                {
                    logger.LogWarning("CtHouse: RS != OK for {District} page {Page}", district, page);
                    break;
                }

                totalPages = root.TryGetProperty("totalpage", out var tp) ? tp.GetInt32() : 1;

                if (!root.TryGetProperty("houses", out var houses) || houses.ValueKind != JsonValueKind.Array)
                    break;

                var pageResults = ParseListings(houses, city, district, maxWan, seen);
                all.AddRange(pageResults);
                logger.LogInformation("CtHouse: {District} page {Page}/{Total}: {Count} listings",
                    district, page, totalPages, pageResults.Count);

                if (page >= totalPages) break;
            }
            catch (Exception ex)
            {
                logger.LogError(ex, "CtHouse: failed to parse response for {District} page {Page}", district, page);
                break;
            }
        }

        return all;
    }

    /// <summary>
    /// 解析 houses JSON 陣列 → PropertyDto 列表（供單元測試直接呼叫）。
    /// </summary>
    public List<PropertyDto> ParseListings(
        JsonElement houses,
        string queryCity,
        string queryDistrict,
        decimal maxWan,
        HashSet<string>? seen = null)
    {
        var results = new List<PropertyDto>();

        foreach (var item in houses.EnumerateArray())
        {
            try
            {
                var dto = ParseItem(item, queryCity, queryDistrict, maxWan);
                if (dto is null) continue;
                if (seen is not null && !seen.Add(dto.SourceListingKey)) continue;
                results.Add(dto);
            }
            catch (Exception ex)
            {
                logger.LogDebug("CtHouse: failed to parse item: {Msg}", ex.Message);
            }
        }

        return results;
    }

    private PropertyDto? ParseItem(JsonElement item, string queryCity, string queryDistrict, decimal maxWan)
    {
        // 住宅型態過濾：排除商業/工業用途與非住宅型態（含店面）
        var typeUsage = GetInt(item, "house_type_usage");
        var typeClass = GetInt(item, "house_type_class");
        if (typeUsage != ResidentialUsage || !ResidentialTypeClasses.Contains(typeClass))
            return null;

        // 物件 ID
        var id = GetStringOrNumber(item, "id");
        if (string.IsNullOrEmpty(id)) return null;

        // 總價（萬，字串型，如 "770"）
        if (!TryGetPrice(item, out var totalPrice) || totalPrice <= 0) return null;

        // Client-side 價格驗證（防推薦/廣告物件跨區混入）
        if (maxWan > 0 && totalPrice > maxWan) return null;

        // 地址解析 city/district（不可完全信任查詢參數，推薦物件可能跨區）
        var address = GetString(item, "address");
        string city = queryCity, district = queryDistrict;
        if (address is not null)
        {
            var m = AddressRegex().Match(address);
            if (m.Success)
            {
                city = m.Groups[1].Value;
                district = m.Groups[2].Value;
            }
        }

        // 坪數（house_area = 總坪數，= size_val）
        var areaPing = GetDecimal(item, "house_area", "size_val");

        // 單價（萬/坪）
        decimal? unitPrice = null;
        var unitFromApi = GetDecimal(item, "unit_price");
        if (unitFromApi > 0)
            unitPrice = unitFromApi;
        else if (areaPing > 0)
            unitPrice = Math.Round(totalPrice / areaPing, 2);

        // 屋齡（整數年）
        int? ageYears = null;
        if (item.TryGetProperty("age", out var ageProp) && ageProp.ValueKind == JsonValueKind.Number
            && ageProp.TryGetInt32(out var ageInt) && ageInt >= 0)
            ageYears = ageInt;

        // 樓層（floor_val 是實值如「13樓/共15樓」，floor_txt 是標籤「樓層」，不用）
        var floor = GetString(item, "floor_val");
        if (string.IsNullOrWhiteSpace(floor)) floor = null;

        // 車位（parking_lot_belong 0/1）
        var hasParking = GetInt(item, "parking_lot_belong") != 0;

        // 物件標題
        var title = GetString(item, "case_name") ?? district;

        // URL
        var urlPath = GetString(item, "url") ?? $"/house/{id}.html";
        var url = urlPath.StartsWith("http") ? urlPath : HouseBaseUrl + urlPath;

        // 圖片（imgs 是路徑陣列，如 ["/project2/house_photo/202605/2100467-1_new.jpg", ...]）
        string? imageUrl = null;
        if (item.TryGetProperty("imgs", out var imgs) && imgs.ValueKind == JsonValueKind.Array)
        {
            foreach (var el in imgs.EnumerateArray())
            {
                var path = el.ValueKind == JsonValueKind.String ? el.GetString() : null;
                if (!string.IsNullOrEmpty(path))
                {
                    imageUrl = MediaBaseUrl + path;
                    break;
                }
            }
        }

        // 發布日期
        DateTime? postedDate = null;
        if (item.TryGetProperty("created_date", out var cd) && cd.ValueKind == JsonValueKind.String)
            DateTime.TryParse(cd.GetString(), out var dt);

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
            SourceSite: SourceSite.CtHouse,
            SourceListingKey: id,
            Title: title,
            Url: url,
            PostedDate: postedDate,
            ImageUrl: imageUrl);
    }

    private static bool TryGetPrice(JsonElement item, out decimal price)
    {
        price = 0m;
        if (!item.TryGetProperty("sell_price", out var prop)) return false;
        if (prop.ValueKind == JsonValueKind.String)
            return decimal.TryParse(prop.GetString(), out price);
        if (prop.ValueKind == JsonValueKind.Number)
            return prop.TryGetDecimal(out price);
        return false;
    }

    private static string? GetString(JsonElement item, params string[] keys)
    {
        foreach (var key in keys)
            if (item.TryGetProperty(key, out var v) && v.ValueKind == JsonValueKind.String)
                return v.GetString();
        return null;
    }

    private static string? GetStringOrNumber(JsonElement item, params string[] keys)
    {
        foreach (var key in keys)
        {
            if (!item.TryGetProperty(key, out var v)) continue;
            if (v.ValueKind == JsonValueKind.String) { var s = v.GetString(); if (!string.IsNullOrEmpty(s)) return s; }
            if (v.ValueKind == JsonValueKind.Number && v.TryGetInt64(out var n)) return n.ToString();
        }
        return null;
    }

    private static decimal GetDecimal(JsonElement item, params string[] keys)
    {
        foreach (var key in keys)
        {
            if (!item.TryGetProperty(key, out var v)) continue;
            if (v.ValueKind == JsonValueKind.Number && v.TryGetDecimal(out var d)) return d;
            if (v.ValueKind == JsonValueKind.String && decimal.TryParse(v.GetString(), out var s)) return s;
        }
        return 0m;
    }

    private static int GetInt(JsonElement item, string key)
    {
        if (item.TryGetProperty(key, out var v))
        {
            if (v.ValueKind == JsonValueKind.Number && v.TryGetInt32(out var i)) return i;
            if (v.ValueKind == JsonValueKind.String && int.TryParse(v.GetString(), out var s)) return s;
        }
        return 0;
    }

    // 地址格式：新北市中和區中山路3段 → group1=新北市, group2=中和區
    [GeneratedRegex(@"^([一-龥]+[市縣])([一-龥]+[區鎮市鄉])")]
    private static partial Regex AddressRegex();
}
