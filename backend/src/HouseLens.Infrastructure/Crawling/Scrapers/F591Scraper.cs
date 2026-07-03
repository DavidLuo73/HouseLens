using HouseLens.Application.Crawling;
using HouseLens.Domain.Enums;
using HtmlAgilityPack;
using Microsoft.Extensions.Logging;
using Microsoft.Playwright;
using System.Text.Json;

namespace HouseLens.Infrastructure.Crawling.Scrapers;

public class F591Scraper(HttpFetcher fetcher, ILogger<F591Scraper> logger) : ISourceScraper
{
    public SourceSite SourceSite => SourceSite.F591;

    private const int PageSize = 30;            // 591 列表每頁 30 筆（firstRow 以 30 遞增）
    private const int MaxPagesPerDistrict = 40; // 防護上限（正常依 data.total 提早停止）

    // 591.com.tw 行政區代碼：regionid=縣市（3=新北市、6=桃園市，實站驗證）；
    // section=行政區（中和區=38、中壢區=67 實站驗證，其餘依 591 官方代碼表遞增序推導；
    // 即使 section 有誤，client-side FilterByDistrict 仍會過濾掉非目標行政區的物件）。
    private static readonly Dictionary<string, (int RegionId, int SectionId, string City)> DistrictMap = new()
    {
        // 新北市 regionid=3
        ["板橋區"] = (3, 26, "新北市"),
        ["汐止區"] = (3, 27, "新北市"),
        ["深坑區"] = (3, 28, "新北市"),
        ["瑞芳區"] = (3, 30, "新北市"),
        ["新店區"] = (3, 34, "新北市"),
        ["永和區"] = (3, 37, "新北市"),
        ["中和區"] = (3, 38, "新北市"),
        ["土城區"] = (3, 39, "新北市"),
        ["三峽區"] = (3, 40, "新北市"),
        ["樹林區"] = (3, 41, "新北市"),
        ["鶯歌區"] = (3, 42, "新北市"),
        ["三重區"] = (3, 43, "新北市"),
        ["新莊區"] = (3, 44, "新北市"),
        ["泰山區"] = (3, 45, "新北市"),
        ["林口區"] = (3, 46, "新北市"),
        ["蘆洲區"] = (3, 47, "新北市"),
        ["五股區"] = (3, 48, "新北市"),
        ["八里區"] = (3, 49, "新北市"),
        ["淡水區"] = (3, 50, "新北市"),
        // 桃園市 regionid=6
        ["中壢區"] = (6, 67, "桃園市"),
        ["平鎮區"] = (6, 68, "桃園市"),
        ["龍潭區"] = (6, 69, "桃園市"),
        ["楊梅區"] = (6, 70, "桃園市"),
        ["新屋區"] = (6, 71, "桃園市"),
        ["觀音區"] = (6, 72, "桃園市"),
        ["桃園區"] = (6, 73, "桃園市"),
        ["龜山區"] = (6, 74, "桃園市"),
        ["八德區"] = (6, 75, "桃園市"),
        ["大溪區"] = (6, 76, "桃園市"),
        ["大園區"] = (6, 78, "桃園市"),
        ["蘆竹區"] = (6, 79, "桃園市"),
    };

    public async Task<IReadOnlyList<PropertyDto>> FetchAsync(
        IReadOnlyDictionary<string, DistrictCriteria> districtCriteria,
        IProgress<ScraperDistrictProgress>? progress,
        Func<IReadOnlyList<PropertyDto>, Task>? onDistrictCompleted = null,
        CancellationToken cancellationToken = default)
    {
        var results = new List<PropertyDto>();

        if (!await fetcher.CheckRobotsAsync("https://sale.591.com.tw/", cancellationToken))
        {
            logger.LogInformation("robots.txt disallows crawling sale.591.com.tw");
            return results;
        }

        using var playwright = await Playwright.CreateAsync();
        await using var browser = await playwright.Chromium.LaunchAsync(new BrowserTypeLaunchOptions
        {
            Headless = true,
            // 隱藏自動化特徵，避免 591 BFF 的 Bot 偵測
            Args = [
                "--disable-blink-features=AutomationControlled",
                // --no-sandbox 不應在非容器環境使用；Docker 需改用非 root 用戶或 Playwright 官方映像
            ],
        });
        await using var context = await browser.NewContextAsync(new BrowserNewContextOptions
        {
            UserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            Locale = "zh-TW",
            ViewportSize = new ViewportSize { Width = 1366, Height = 768 },
            ExtraHTTPHeaders = new Dictionary<string, string>
            {
                ["Accept-Language"] = "zh-TW,zh;q=0.9,en-US;q=0.8,en;q=0.7",
            },
        });
        // 覆蓋 navigator.webdriver 防止被偵測為自動化瀏覽器
        await context.AddInitScriptAsync(
            "Object.defineProperty(navigator, 'webdriver', { get: () => undefined });");

        // 先訪問主頁讓 591 設置 session cookies（模擬正常用戶行為，整個 context 共用）
        logger.LogInformation("Warming up 591 session...");
        var warmupPage = await context.NewPageAsync();
        await warmupPage.GotoAsync("https://sale.591.com.tw/", new PageGotoOptions
        {
            WaitUntil = WaitUntilState.NetworkIdle,
            Timeout = 30_000,
        });
        await warmupPage.CloseAsync();

        var knownDistricts = districtCriteria.Keys.Where(d => DistrictMap.ContainsKey(d)).ToList();
        var unknownDistricts = districtCriteria.Keys.Except(knownDistricts).ToList();
        foreach (var d in unknownDistricts)
            logger.LogWarning("Unknown district (not in DistrictMap): {District}", d);

        var total = knownDistricts.Count;

        for (var i = 0; i < knownDistricts.Count; i++)
        {
            var district = knownDistricts[i];
            progress?.Report(new(district, i, total, IsStarting: true, FetchedCount: 0));

            var districtResults = await FetchDistrictAsync(
                context, district, districtCriteria[district], cancellationToken);
            results.AddRange(districtResults);

            progress?.Report(new(district, i, total, IsStarting: false, FetchedCount: districtResults.Count));
            logger.LogInformation("District {District} (max={Max}萬): {Count} listings",
                district, districtCriteria[district].MaxTotalPrice, districtResults.Count);

            if (onDistrictCompleted is not null) await onDistrictCompleted(districtResults);
        }

        return results;
    }

    /// <summary>
    /// 依 DistrictCriteria 組出 591 中古屋搜尋 URL。
    /// shType=list（列表模式，另有 map 地圖模式）、type=2（中古屋）、
    /// regionid（縣市）、section（行政區）、price=$_$N（N 萬以下）、
    /// houseage=$_$N（屋齡 N 年以下）、parking=1,2,3（平面/機械/平面+機械）、
    /// area=$N_$（N 坪以上）、pattern=2,3,4,5（房數，5=5 房以上）、
    /// firstRow=N（分頁起始筆數，每頁 30 筆）。
    /// </summary>
    public static string BuildSearchUrl(int regionId, int sectionId, DistrictCriteria criteria, int firstRow = 0)
    {
        var url = $"https://sale.591.com.tw/?shType=list&type=2&regionid={regionId}&section={sectionId}";
        if (criteria.MaxTotalPrice > 0) url += $"&price=$_${criteria.MaxTotalPrice:0}";
        if (criteria.MaxAgeYears > 0) url += $"&houseage=$_${criteria.MaxAgeYears}";
        var parking = BuildParkingParam(criteria.ParkingCodes);
        if (parking is not null) url += $"&parking={parking}";
        if (criteria.MinSizePing > 0) url += $"&area=${criteria.MinSizePing:0.##}_$";
        var pattern = BuildPatternParam(criteria.Rooms);
        if (pattern is not null) url += $"&pattern={pattern}";
        if (firstRow > 0) url += $"&firstRow={firstRow}";
        return url;
    }

    /// <summary>
    /// 停車位參數：地區共用的樂屋網代碼 PF（平面）→1、PM（機械）→2；
    /// 同時勾選平面與機械時補上 3（平面+機械混合車位）。591 原生數字代碼 1/2/3 直接沿用。
    /// 空／無法辨識 → null（不限，不帶 parking 參數）。
    /// </summary>
    public static string? BuildParkingParam(string parkingCodes)
    {
        var set = new SortedSet<int>();
        foreach (var c in SplitCodes(parkingCodes))
        {
            if (string.Equals(c, "PF", StringComparison.OrdinalIgnoreCase)) set.Add(1);
            else if (string.Equals(c, "PM", StringComparison.OrdinalIgnoreCase)) set.Add(2);
            else if (int.TryParse(c, out var n) && n is >= 1 and <= 3) set.Add(n);
        }
        if (set.Count == 0) return null;
        if (set.Contains(1) && set.Contains(2)) set.Add(3);
        return string.Join(',', set);
    }

    /// <summary>房數參數：Rooms 條件（如 "2,3,4,5~"）→ 591 pattern 代碼 1~5（5=5 房以上）。</summary>
    public static string? BuildPatternParam(string rooms)
    {
        var values = SplitCodes(rooms)
            .Select(r => int.TryParse(r.TrimEnd('~'), out var n) ? n : 0)
            .Where(n => n >= 1)
            .Select(n => Math.Min(n, 5))
            .Distinct()
            .OrderBy(n => n)
            .ToList();
        return values.Count == 0 ? null : string.Join(',', values);
    }

    private static string[] SplitCodes(string s) =>
        string.IsNullOrWhiteSpace(s)
            ? []
            : s.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries);

    private async Task<List<PropertyDto>> FetchDistrictAsync(
        IBrowserContext context,
        string district,
        DistrictCriteria criteria,
        CancellationToken ct)
    {
        var (regionId, sectionId, city) = DistrictMap[district];
        var all = new List<PropertyDto>();
        var seen = new HashSet<string>();
        var capturedResponses = new List<(string Url, byte[] Body)>();
        var page = await context.NewPageAsync();

        page.Response += async (_, response) =>
        {
            try
            {
                if (Is591JsonApiResponse(response))
                {
                    var body = await response.BodyAsync();
                    capturedResponses.Add((response.Url, body));
                    logger.LogDebug("Captured API response {Url} ({Bytes}B)", response.Url, body.Length);
                }
            }
            catch { }
        };

        try
        {
            int? totalCount = null;

            for (var pageIndex = 0; pageIndex < MaxPagesPerDistrict; pageIndex++)
            {
                // 每次導覽前清空，確保只解析本頁的 BFF 回應
                capturedResponses.Clear();

                var url = BuildSearchUrl(regionId, sectionId, criteria, pageIndex * PageSize);
                logger.LogInformation("591 {District} page {Page}: {Url}", district, pageIndex + 1, url);

                await fetcher.WaitAsync(ct);
                await page.GotoAsync(url, new PageGotoOptions
                {
                    WaitUntil = WaitUntilState.NetworkIdle,
                    Timeout = 30_000,
                });

                var (listings, total) = ExtractFromCaptured(capturedResponses, regionId, sectionId, city);
                if (total.HasValue) totalCount = total;

                // 591 對「無符合結果」不會回空頁，而是改顯示「為您推薦」的物件（多半超出條件），
                // BFF 一樣回傳 house_list —— 必須以 total=0 判斷為無資料，否則會把推薦物件
                // 誤認為搜尋結果、無止盡翻頁。
                if (totalCount == 0)
                {
                    logger.LogInformation(
                        "591 {District}: no matching listings (total=0), ignoring recommended items", district);
                    break;
                }

                // 第一頁 API 攔截失敗（total 也未知）時的 HTML 備援
                if (listings.Count == 0 && pageIndex == 0 && totalCount is null)
                {
                    var html = await page.ContentAsync();
                    if (html.Contains("暫無相關內容"))
                    {
                        logger.LogInformation("591 {District}: empty-state page detected, no matching listings", district);
                        break;
                    }
                    listings = ParseListingsFromHtml(html, city).ToList();
                    if (listings.Count > 0)
                        logger.LogInformation("Extracted {Count} listings via HTML for {District}", listings.Count, district);
                }

                // 頁內先按行政區/價格/屋齡過濾（推薦物件通常會在此被剔除），再去重判斷是否翻頁：
                // 超出範圍的 firstRow 591 會回傳空列表或重複資料 → 無新的有效物件即視為抓完
                var valid = FilterByDistrict(listings, district, criteria.MaxTotalPrice, criteria.MaxAgeYears);
                var fresh = valid.Where(l => seen.Add(l.SourceListingKey)).ToList();
                logger.LogInformation("591 {District} page {Page}: {Fresh}/{Valid}/{Raw} new/valid/raw listings (total={Total})",
                    district, pageIndex + 1, fresh.Count, valid.Count, listings.Count, totalCount);

                if (fresh.Count == 0) break;
                all.AddRange(fresh);

                // 依 BFF 回傳的 data.total 判斷是否已抓完全部分頁
                if (totalCount.HasValue && (pageIndex + 1) * PageSize >= totalCount.Value) break;
            }
        }
        finally
        {
            await page.CloseAsync();
        }

        // 頁內已按行政區/價格/屋齡過濾並去重，這裡直接回傳
        return all;
    }

    /// <summary>
    /// 從攔截到的 BFF JSON 回應解析物件列表與總筆數（data.total）。
    /// 優先採用 URL 帶目標 section 參數的回應（搜尋結果），避免「為您推薦」等
    /// 推薦類 BFF 回應的 house_list / total 混入。
    /// </summary>
    private (List<PropertyDto> Listings, int? Total) ExtractFromCaptured(
        List<(string Url, byte[] Body)> captured, int regionId, int sectionId, string city)
    {
        var regionIdStr = $"regionid={regionId}";
        var sectionStr = $"section={sectionId}";
        var listResponses = captured
            .Where(r => (r.Url.Contains("house") || r.Url.Contains("sale") || r.Url.Contains("list"))
                        && r.Url.Contains(regionIdStr))
            .OrderByDescending(r => r.Url.Contains(sectionStr))
            .ToList();

        int? total = null;
        var totalFromSection = false;
        List<PropertyDto> best = [];

        foreach (var (responseUrl, body) in listResponses)
        {
            try
            {
                using var doc = JsonDocument.Parse(body);
                LogJsonStructure(responseUrl, doc.RootElement);

                var isSectionResponse = responseUrl.Contains(sectionStr);
                var t = TryGetTotal(doc.RootElement);
                if (t.HasValue && (total is null || (isSectionResponse && !totalFromSection)))
                {
                    total = t;
                    totalFromSection = isSectionResponse;
                }

                if (best.Count == 0)
                {
                    var parsed = TryParseJsonResponse(doc.RootElement, city);
                    if (parsed.Count > 0) best = parsed;
                }
            }
            catch (Exception ex)
            {
                logger.LogWarning("Failed to parse API response {Url}: {Msg}", responseUrl, ex.Message);
            }
        }

        return (best, total);
    }

    private static int? TryGetTotal(JsonElement root)
    {
        var el = TryNavigateJsonPath(root, ["data", "total"], ["data", "data", "total"], ["total"]);
        if (el is null) return null;
        if (el.Value.ValueKind == JsonValueKind.Number && el.Value.TryGetInt32(out var n)) return n;
        if (el.Value.ValueKind == JsonValueKind.String
            && int.TryParse(el.Value.GetString()?.Replace(",", ""), out var s)) return s;
        return null;
    }

    private void LogJsonStructure(string url, JsonElement root)
    {
        try
        {
            if (root.ValueKind != JsonValueKind.Object) return;

            var topKeys = string.Join(", ", root.EnumerateObject().Select(p => p.Name));
            logger.LogInformation("API {Url} — top keys: [{Keys}]", url, topKeys);

            if (root.TryGetProperty("data", out var dataEl) && dataEl.ValueKind == JsonValueKind.Object)
            {
                var dataKeys = string.Join(", ", dataEl.EnumerateObject().Select(p => p.Name));
                logger.LogInformation("  data sub-keys: [{Keys}]", dataKeys);

                if (dataEl.TryGetProperty("total", out var total))
                    logger.LogInformation("  data.total = {Total}", total);

                if (dataEl.TryGetProperty("house_list", out var list) && list.ValueKind == JsonValueKind.Array)
                {
                    logger.LogInformation("  data.house_list.length = {Count}", list.GetArrayLength());
                    if (list.GetArrayLength() > 0)
                    {
                        var first = list.EnumerateArray().First();
                        if (first.ValueKind == JsonValueKind.Object)
                        {
                            var keys = string.Join(", ", first.EnumerateObject().Select(p => p.Name));
                            logger.LogInformation("  first listing keys: [{Keys}]", keys);

                            // 只印 URL / 圖片相關欄位的值
                            var urlImageKeys = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
                            {
                                "houseid", "id", "house_id",
                                "detail_url", "url", "app_open_url",
                                "event_click_url", "event_show_url",
                                "photo_url", "img", "main_img", "cover", "imgs", "photo_path"
                            };
                            foreach (var prop in first.EnumerateObject().Where(p => urlImageKeys.Contains(p.Name)))
                            {
                                var valStr = prop.Value.ValueKind switch
                                {
                                    JsonValueKind.Array  => $"[Array len={prop.Value.GetArrayLength()}]",
                                    JsonValueKind.Object => $"{{Object}}",
                                    JsonValueKind.String => $"\"{prop.Value.GetString()}\"",
                                    _ => prop.Value.ToString(),
                                };
                                logger.LogInformation("    {Key}: {Value}", prop.Name, valStr);
                            }
                        }
                    }
                }
            }
        }
        catch { }
    }

    private static bool Is591JsonApiResponse(IResponse response)
    {
        if (response.Status != 200) return false;
        if (!response.Url.Contains("591.com.tw")) return false;
        if (!response.Headers.TryGetValue("content-type", out var ct)) return false;
        if (!ct.Contains("application/json")) return false;
        var url = response.Url;
        return url.Contains("/search") || url.Contains("/house") || url.Contains("/sale") || url.Contains("/list");
    }

    private List<PropertyDto> TryParseJsonResponse(JsonElement root, string city)
    {
        var results = new List<PropertyDto>();

        var houseList = TryNavigateJsonPath(root,
            ["data", "house_list"],
            ["data", "data", "house_list"],
            ["house_list"],
            ["data", "items"],
            ["data", "rows"],
            ["rows"],
            ["items"]);

        if (houseList is null || houseList.Value.ValueKind != JsonValueKind.Array)
        {
            logger.LogDebug("house_list not found or not array (kind={Kind})", houseList?.ValueKind);
            return results;
        }

        foreach (var item in houseList.Value.EnumerateArray())
        {
            try
            {
                var dto = ParseJsonListing(item, city);
                if (dto is not null) results.Add(dto);
            }
            catch (Exception ex)
            {
                logger.LogDebug("Failed to parse listing item: {Msg}", ex.Message);
            }
        }

        return results;
    }

    private static JsonElement? TryNavigateJsonPath(JsonElement root, params string[][] paths)
    {
        foreach (var path in paths)
        {
            var current = root;
            var ok = true;
            foreach (var key in path)
            {
                if (!current.TryGetProperty(key, out current)) { ok = false; break; }
            }
            if (ok) return current;
        }
        return null;
    }

    private static PropertyDto? ParseJsonListing(JsonElement item, string city)
    {
        // 優先取數字型 id（591 URL 格式是 /detail/2/{numericId}.html）
        // houseid 通常是 UUID，不能直接用於 URL
        var listingKey = GetJsonStringOrNumber(item, "id", "house_id", "houseid")
            ?? Guid.NewGuid().ToString();

        // 591 BFF price 欄位已是「萬」為單位（如 2700 = 2700萬），不需除以 10000
        var priceVal = GetJsonDecimal(item, "price", "total_price", "totalPrice");
        if (priceVal <= 0) return null;
        var totalPrice = priceVal; // 已是萬

        // 從 JSON 欄位取行政區（備援：從地址解析）
        var district = GetJsonString(item, "section_name", "section", "district") ?? "";
        var address = GetJsonString(item, "address", "addr", "location", "street_name");
        if (string.IsNullOrEmpty(district) && address is not null)
            district = ExtractDistrictFromAddress(address);
        if (string.IsNullOrEmpty(district))
            district = GetJsonString(item, "region_name") ?? "";

        var title = GetJsonString(item, "house_title", "title", "name") ?? district;
        // showarea 是含坪數的顯示字串，area 是原始數字
        var areaPing = GetJsonDecimal(item, "area", "showarea", "building_size");
        var floor = GetJsonString(item, "floor", "floorStr", "floor_str");
        // houseage 是591 BFF 的屋齡欄位（年）
        var ageText = GetJsonString(item, "houseage", "age", "ageText", "age_str");
        var ageYears = ageText is not null ? ParseIntFromText(ageText) : null;
        var hasParking = GetJsonBool(item, "hasParking", "parking", "has_parking");
        // unitprice 在 591 BFF 也是萬/坪
        var unitPriceFromApi = GetJsonDecimal(item, "unitprice");
        var unitPrice = unitPriceFromApi > 0 ? (decimal?)unitPriceFromApi
            : (areaPing > 0 ? totalPrice / areaPing : null);
        // 優先取 event_click_url 中 url= 參數（內含 URL-encoded 的物件實際網址）
        var rawUrl = GetJsonString(item, "detail_url", "url");
        if (rawUrl is null)
        {
            var eventClickUrl = GetJsonString(item, "event_click_url");
            if (eventClickUrl is not null)
            {
                var m = System.Text.RegularExpressions.Regex.Match(eventClickUrl, @"[?&]url=([^&]+)");
                if (m.Success) rawUrl = Uri.UnescapeDataString(m.Groups[1].Value);
            }
        }
        // Fallback：用 houseid + is_newhouse 決定 URL 格式
        if (rawUrl is null)
        {
            var isNewHouse = GetJsonBool(item, "is_newhouse");
            rawUrl = isNewHouse
                ? $"https://newhouse.591.com.tw/{listingKey}?new=2"
                : $"https://sale.591.com.tw/home/house/detail/2/{listingKey}.html";
        }
        var url = rawUrl;
        var imageUrl = GetJsonFirstImageUrl(item);

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
            SourceSite: SourceSite.F591,
            SourceListingKey: listingKey,
            Title: title,
            Url: url,
            PostedDate: null,
            ImageUrl: imageUrl);
    }

    private static string ExtractDistrictFromAddress(string address)
    {
        // 地址格式通常為 "城市行政區xx路xx號"，取前面的行政區
        var match = System.Text.RegularExpressions.Regex.Match(address, @"[一-鿿]{2,4}區");
        return match.Success ? match.Value : "";
    }

    private static List<PropertyDto> FilterByDistrict(
        List<PropertyDto> listings, string district, decimal maxTotalPrice, int maxAgeYears = 0)
    {
        return listings.Where(l =>
            l.TotalPrice <= maxTotalPrice &&
            (maxAgeYears <= 0 || !l.AgeYears.HasValue || l.AgeYears.Value <= maxAgeYears) &&
            (l.District == district ||
             l.Address?.Contains(district) == true ||
             l.Title?.Contains(district) == true)).ToList();
    }

    private static string? GetJsonString(JsonElement item, params string[] keys)
    {
        foreach (var key in keys)
        {
            if (item.TryGetProperty(key, out var val) && val.ValueKind == JsonValueKind.String)
                return val.GetString();
        }
        return null;
    }

    // 同時支援數字型和字串型欄位（591 的 id 是數字，houseid 是字串 UUID）
    private static string? GetJsonStringOrNumber(JsonElement item, params string[] keys)
    {
        foreach (var key in keys)
        {
            if (!item.TryGetProperty(key, out var val)) continue;
            if (val.ValueKind == JsonValueKind.String)
            {
                var s = val.GetString();
                if (!string.IsNullOrEmpty(s)) return s;
            }
            if (val.ValueKind == JsonValueKind.Number && val.TryGetInt64(out var n))
                return n.ToString();
        }
        return null;
    }

    private static decimal GetJsonDecimal(JsonElement item, params string[] keys)
    {
        foreach (var key in keys)
        {
            if (!item.TryGetProperty(key, out var val)) continue;
            if (val.ValueKind == JsonValueKind.Number && val.TryGetDecimal(out var d)) return d;
            if (val.ValueKind == JsonValueKind.String && decimal.TryParse(val.GetString(), out var s)) return s;
        }
        return 0;
    }

    private static bool GetJsonBool(JsonElement item, params string[] keys)
    {
        foreach (var key in keys)
        {
            if (!item.TryGetProperty(key, out var val)) continue;
            if (val.ValueKind == JsonValueKind.True) return true;
            if (val.ValueKind == JsonValueKind.False) return false;
            if (val.ValueKind == JsonValueKind.Number && val.TryGetInt32(out var i)) return i != 0;
            if (val.ValueKind == JsonValueKind.String)
            {
                var s = val.GetString();
                if (s == "1" || string.Equals(s, "true", StringComparison.OrdinalIgnoreCase)) return true;
            }
        }
        return false;
    }

    private static string? GetJsonFirstImageUrl(JsonElement item)
    {
        // 591 BFF imgs 可能是物件陣列 [{src:...,type:...}] 或字串陣列 ["url"]
        // 影片物件優先取縮圖欄位，沒有才回傳影片 URL 讓前端自行顯示（不再跳過）
        foreach (var key in new[] { "imgs", "img_list", "photos" })
        {
            if (item.TryGetProperty(key, out var arr) && arr.ValueKind == JsonValueKind.Array)
            {
                foreach (var el in arr.EnumerateArray())
                {
                    string? candidate = null;
                    if (el.ValueKind == JsonValueKind.String)
                    {
                        candidate = el.GetString();
                    }
                    else if (el.ValueKind == JsonValueKind.Object)
                    {
                        var type = GetJsonString(el, "type", "media_type");
                        if (string.Equals(type, "video", StringComparison.OrdinalIgnoreCase))
                        {
                            // 優先取縮圖，沒有才用影片 URL（前端用 <video> 顯示）
                            candidate = GetJsonString(el, "thumbnail", "poster", "cover", "img", "thumb")
                                     ?? GetJsonString(el, "src", "url", "path");
                        }
                        else
                        {
                            candidate = GetJsonString(el, "src", "url", "path");
                        }
                    }

                    if (!string.IsNullOrEmpty(candidate))
                        return candidate;
                }
            }
        }
        // 單一圖片欄位（591 BFF list 用 photo_url）
        return GetJsonString(item, "photo_url", "cover_img", "img", "main_img",
                                  "photo_path", "thumbnail_url", "cover", "thumb_url");
    }

    private static int? ParseIntFromText(string text)
    {
        var match = System.Text.RegularExpressions.Regex.Match(text, @"\d+");
        return match.Success && int.TryParse(match.Value, out var val) ? val : null;
    }

    // HTML 備援解析 — 用於 JSON API 攔截失敗時
    public static IReadOnlyList<PropertyDto> ParseListingsFromHtml(string html, string city)
    {
        var results = new List<PropertyDto>();
        var doc = new HtmlDocument();
        doc.LoadHtml(html);

        HtmlNodeCollection? listings = null;
        string[] selectors =
        [
            "//ul[@class='houseList']//li[@class='clear']",
            "//li[contains(@class,'houseList-item')]",
            "//div[contains(@class,'house-item')]",
            "//div[contains(@class,'list-item')]",
        ];

        foreach (var sel in selectors)
        {
            listings = doc.DocumentNode.SelectNodes(sel);
            if (listings is not null) break;
        }

        if (listings is null) return results;

        foreach (var item in listings)
        {
            try
            {
                var listing = ParseHtmlListing(item, city);
                if (listing is not null) results.Add(listing);
            }
            catch { }
        }

        return results;
    }

    private static PropertyDto? ParseHtmlListing(HtmlNode item, string city)
    {
        var titleNode = item.SelectSingleNode(".//a[contains(@class,'houseLeft')]//h3")
            ?? item.SelectSingleNode(".//h3")
            ?? item.SelectSingleNode(".//h2");
        var priceNode = item.SelectSingleNode(".//b[@class='priceNum']")
            ?? item.SelectSingleNode(".//*[contains(@class,'price')]//b")
            ?? item.SelectSingleNode(".//*[contains(@class,'price')]");
        var urlNode = item.SelectSingleNode(".//a[@href]");
        var href = urlNode?.GetAttributeValue("href", "");

        if (titleNode is null || priceNode is null) return null;

        var title = titleNode.InnerText.Trim();
        var priceText = priceNode.InnerText.Trim().Replace(",", "");
        if (!decimal.TryParse(priceText, out var price)) return null;

        var url = href?.StartsWith("http") == true ? href : $"https://sale.591.com.tw{href}";
        var listingKey = href?.Split('/').LastOrDefault()?.Split('.').FirstOrDefault()
            ?? Guid.NewGuid().ToString();

        var address = item.SelectSingleNode(".//*[@class='add']")?.InnerText.Trim()
            ?? item.SelectSingleNode(".//*[contains(@class,'address')]")?.InnerText.Trim();
        var district = address is not null ? ExtractDistrictFromAddress(address) : "";

        var areaPing = ParseDecimalFromText(item.SelectSingleNode(".//li[contains(text(),'坪')]")?.InnerText);
        var floor = item.SelectSingleNode(".//li[contains(text(),'樓')]")?.InnerText.Trim();
        var ageText = item.SelectSingleNode(".//li[contains(text(),'年')]")?.InnerText;
        var ageYears = ageText is not null ? ParseIntFromText(ageText) : null;
        var hasParking = item.InnerText.Contains("有停車位") || item.InnerText.Contains("車位");

        return new PropertyDto(
            City: city,
            District: district,
            Address: string.IsNullOrWhiteSpace(address) ? null : address,
            AreaPing: areaPing ?? 0m,
            Floor: string.IsNullOrWhiteSpace(floor) ? null : floor,
            AgeYears: ageYears,
            HasParking: hasParking,
            TotalPrice: price,          // HTML 抓出的 price 數字也是萬單位
            UnitPrice: areaPing > 0 ? price / areaPing : null,
            SourceSite: SourceSite.F591,
            SourceListingKey: listingKey,
            Title: title,
            Url: url,
            PostedDate: null);
    }

    private static decimal? ParseDecimalFromText(string? text)
    {
        if (string.IsNullOrWhiteSpace(text)) return null;
        var match = System.Text.RegularExpressions.Regex.Match(text, @"[\d.]+");
        return match.Success && decimal.TryParse(match.Value, out var val) ? val : null;
    }
}
