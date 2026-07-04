using HouseLens.Application.Crawling;
using HouseLens.Domain.Enums;
using Microsoft.Extensions.Logging;
using System.Text.Json;
using System.Text.RegularExpressions;

namespace HouseLens.Infrastructure.Crawling.Scrapers;

/// <summary>
/// 中信房屋買屋網（buy.cthouse.com.tw）爬蟲。
/// 後端 API 為 POST /api/house_list.ashx，body {"arg":"{路徑段}","page":N}，
/// arg 與搜尋頁 URL 路徑相同，伺服器端支援城市/行政區/總價/坪數/型態/屋齡/房數/車位篩選
/// （各段皆經實站驗證），不需 Playwright。arg 由 BuildSearchArg 依 DistrictCriteria 組成，
/// 路徑段依序：{城市}-city/{區}-town/0-{maxWan}-price/{坪}-up-area/{型態,…}-type/
/// 0-{年}-year/{房}-up-room/1-parking。
/// robots.txt: User-agent: * / Allow: / （全域允許）
/// </summary>
public partial class CtHouseScraper(HttpFetcher fetcher, ILogger<CtHouseScraper> logger) : ISourceScraper
{
    public SourceSite SourceSite => SourceSite.CtHouse;

    private const string ApiUrl = "https://buy.cthouse.com.tw/api/house_list.ashx";
    private const string MediaBaseUrl = "https://media.cthouse.com.tw/photo";
    private const string HouseBaseUrl = "https://buy.cthouse.com.tw";
    private const int MaxPagesPerDistrict = 100; // 禮貌性上限；實際依 totalpage 結束（寬條件行政區實測可超過 10 頁）

    // 住宅用途碼（house_type_usage）：1=住宅，2=商業/工業
    private const int ResidentialUsage = 1;

    // 建物型態 URL 名稱（{…}-type 段，實站驗證有效），依官方順序排列
    private static readonly string[] CtTypeCodes = ["電梯大樓", "公寓", "套房", "透天"];

    // URL 型態名稱 → house_type_class 代碼（client-side 白名單用）：
    // 電梯大樓=2（含華廈）、公寓=1、套房=3、透天=6
    private static readonly Dictionary<string, int[]> TypeCodeToClasses = new(StringComparer.Ordinal)
    {
        ["電梯大樓"] = [2],
        ["公寓"] = [1],
        ["套房"] = [3],
        ["透天"] = [6],
    };

    // 樂屋網 typecode → 中信型態名稱對映（平台未設定中信專屬 TypeCodes 時的回退）
    private static readonly Dictionary<string, string[]> RakuyaTypeToCt = new(StringComparer.OrdinalIgnoreCase)
    {
        ["R1"] = ["公寓"],
        ["R2"] = ["電梯大樓"],
        ["R3"] = ["套房"],
        ["R5"] = ["透天"],
    };

    // 預設型態（未設定 TypeCodes 或無法辨識時）：電梯大樓＋公寓（維持原白名單 class 1,2 行為）
    private static readonly string[] DefaultTypeCodes = ["電梯大樓", "公寓"];

    // 行政區 → 中信城市名（涵蓋前端可選的新北 19 區＋桃園 12 區）
    private static readonly Dictionary<string, string> DistrictToCity = new()
    {
        // 新北市
        ["板橋區"] = "新北市", ["汐止區"] = "新北市", ["深坑區"] = "新北市", ["瑞芳區"] = "新北市",
        ["新店區"] = "新北市", ["永和區"] = "新北市", ["中和區"] = "新北市", ["土城區"] = "新北市",
        ["三峽區"] = "新北市", ["樹林區"] = "新北市", ["鶯歌區"] = "新北市", ["三重區"] = "新北市",
        ["新莊區"] = "新北市", ["泰山區"] = "新北市", ["林口區"] = "新北市", ["蘆洲區"] = "新北市",
        ["五股區"] = "新北市", ["八里區"] = "新北市", ["淡水區"] = "新北市",
        // 桃園市
        ["中壢區"] = "桃園市", ["平鎮區"] = "桃園市", ["龍潭區"] = "桃園市", ["楊梅區"] = "桃園市",
        ["新屋區"] = "桃園市", ["觀音區"] = "桃園市", ["桃園區"] = "桃園市", ["龜山區"] = "桃園市",
        ["八德區"] = "桃園市", ["大溪區"] = "桃園市", ["大園區"] = "桃園市", ["蘆竹區"] = "桃園市",
    };

    public async Task<IReadOnlyList<PropertyDto>> FetchAsync(
        IReadOnlyDictionary<string, DistrictCriteria> districtCriteria,
        IProgress<ScraperDistrictProgress>? progress,
        Func<IReadOnlyList<PropertyDto>, Task>? onDistrictCompleted = null,
        CancellationToken cancellationToken = default)
    {
        var results = new List<PropertyDto>();
        var seen = new HashSet<string>();

        var knownDistricts = districtCriteria.Keys.Where(d => DistrictToCity.ContainsKey(d)).ToList();
        var unknownDistricts = districtCriteria.Keys.Except(knownDistricts).ToList();
        foreach (var d in unknownDistricts)
            logger.LogWarning("CtHouse: unknown district (not in DistrictToCity): {District}", d);

        var total = knownDistricts.Count;

        for (var i = 0; i < knownDistricts.Count; i++)
        {
            var district = knownDistricts[i];
            var criteria = districtCriteria[district];
            var city = DistrictToCity[district];

            progress?.Report(new(district, i, total, IsStarting: true, FetchedCount: 0));
            logger.LogInformation("CtHouse: scraping {City} {District} (max={Max}萬)", city, district, criteria.MaxTotalPrice);

            var districtResults = await FetchDistrictAsync(city, district, criteria, seen, cancellationToken);
            results.AddRange(districtResults);

            progress?.Report(new(district, i, total, IsStarting: false, FetchedCount: districtResults.Count));
            logger.LogInformation("CtHouse: {District} done, {Count} listings", district, districtResults.Count);

            if (onDistrictCompleted is not null) await onDistrictCompleted(districtResults);
        }

        return results;
    }

    /// <summary>
    /// 依 DistrictCriteria 組出中信 API 的 arg 路徑段（與搜尋頁 URL 路徑一致）。
    /// 依序為（無設定者省略）：{城市}-city／{區}-town／0-{N}-price 總價／{N}-up-area 坪數／
    /// {型態,…}-type／0-{N}-year 屋齡／{N}-up-room 房數／1-parking 車位。
    /// 各段皆經實站驗證有 server-side 篩選效果。
    /// </summary>
    public static string BuildSearchArg(string city, string district, DistrictCriteria criteria)
    {
        var segments = new List<string>
        {
            $"{city}-city",
            $"{district}-town",
        };

        if (criteria.MaxTotalPrice > 0)
            segments.Add($"0-{(int)criteria.MaxTotalPrice}-price");

        if (criteria.MinSizePing > 0)
            segments.Add($"{criteria.MinSizePing:0}-up-area");

        segments.Add($"{string.Join('-', ResolveTypeCodes(criteria.TypeCodes))}-type");

        if (criteria.MaxAgeYears > 0)
            segments.Add($"0-{criteria.MaxAgeYears}-year");

        var minRooms = ParseMinRooms(criteria.Rooms);
        if (minRooms > 0)
            segments.Add($"{minRooms}-up-room");

        // 停車位：中信僅支援有/無車位（1/0-parking，不分平面/機械）；
        // 不勾＝不限，URL 不帶車位段；勾選任一型式即要求有車位
        if (!string.IsNullOrWhiteSpace(criteria.ParkingCodes))
            segments.Add("1-parking");

        return string.Join('/', segments) + "/";
    }

    /// <summary>型態段：優先採中信名稱（電梯大樓/公寓/套房/透天）；樂屋網 R 代碼則對映；無法辨識時回退預設（電梯大樓＋公寓）。</summary>
    private static List<string> ResolveTypeCodes(string typeCodes)
    {
        var codes = SplitCodes(typeCodes);
        var resolved = codes.Where(c => CtTypeCodes.Contains(c, StringComparer.Ordinal))
            .Concat(codes.SelectMany(c => RakuyaTypeToCt.GetValueOrDefault(c, [])))
            .Distinct()
            .ToList();
        if (resolved.Count == 0) return [.. DefaultTypeCodes];

        // 依官方順序輸出，避免同組合產生不同 arg
        return CtTypeCodes.Where(resolved.Contains).ToList();
    }

    /// <summary>依 TypeCodes 建立 house_type_class 白名單（client-side 防推薦物件混入非目標型態）。</summary>
    public static HashSet<int> BuildAllowedTypeClasses(string typeCodes) =>
        ResolveTypeCodes(typeCodes).SelectMany(c => TypeCodeToClasses.GetValueOrDefault(c, [])).ToHashSet();

    /// <summary>從 Rooms 條件（如 "2,3,5~"）取最小房數，中信以 {N}-up-room（N 房以上）表達。</summary>
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
        DistrictCriteria criteria,
        HashSet<string> seen,
        CancellationToken ct)
    {
        var all = new List<PropertyDto>();
        var maxWan = criteria.MaxTotalPrice;
        var allowedClasses = BuildAllowedTypeClasses(criteria.TypeCodes);
        var arg = BuildSearchArg(city, district, criteria);

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

                var seenBefore = seen.Count;
                var pageResults = ParseListings(houses, city, district, maxWan, seen, allowedClasses);
                all.AddRange(pageResults);
                logger.LogInformation("CtHouse: {District} page {Page}/{Total}: {Count} listings",
                    district, page, totalPages, pageResults.Count);

                if (page >= totalPages) break;

                // 防呆：超過實際頁數時站方可能回傳重複內容，整頁去重全命中即視為結束
                if (page > 1 && pageResults.Count == 0 && seen.Count == seenBefore && seenBefore > 0) break;
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
        HashSet<string>? seen = null,
        HashSet<int>? allowedClasses = null)
    {
        allowedClasses ??= BuildAllowedTypeClasses("");
        var results = new List<PropertyDto>();

        foreach (var item in houses.EnumerateArray())
        {
            try
            {
                var dto = ParseItem(item, queryCity, queryDistrict, maxWan, allowedClasses);
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

    private PropertyDto? ParseItem(JsonElement item, string queryCity, string queryDistrict, decimal maxWan, HashSet<int> allowedClasses)
    {
        // 住宅型態過濾：排除商業/工業用途與不在型態白名單的物件（含店面、推薦物件混入）
        var typeUsage = GetInt(item, "house_type_usage");
        var typeClass = GetInt(item, "house_type_class");
        if (typeUsage != ResidentialUsage || !allowedClasses.Contains(typeClass))
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
