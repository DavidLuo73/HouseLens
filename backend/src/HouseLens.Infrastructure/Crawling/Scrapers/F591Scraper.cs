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

    // 591.com.tw regionid (confirmed: 1=台北市, 4=基隆市; 3/2/6 需要截圖驗證)
    private static readonly Dictionary<string, (int RegionId, string City)> DistrictMap = new()
    {
        ["中和區"] = (3, "新北市"),
        ["永和區"] = (3, "新北市"),
        ["板橋區"] = (3, "新北市"),
        ["新店區"] = (3, "新北市"),
        ["土城區"] = (3, "新北市"),
        ["樹林區"] = (3, "新北市"),
        ["三峽區"] = (3, "新北市"),
        ["新莊區"] = (3, "新北市"),
        ["中壢區"] = (6, "桃園市"),
        ["桃園區"] = (6, "桃園市"),
    };

    public async Task<IReadOnlyList<PropertyDto>> FetchAsync(
        IReadOnlyDictionary<string, decimal> districtMaxPrices,
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

        // 按城市分組，每個城市只抓一次（避免重複請求）
        var knownDistricts = districtMaxPrices.Keys.Where(d => DistrictMap.ContainsKey(d)).ToList();
        var unknownDistricts = districtMaxPrices.Keys.Except(knownDistricts).ToList();
        foreach (var d in unknownDistricts)
            logger.LogWarning("Unknown district (not in DistrictMap): {District}", d);

        var citiesQueried = new HashSet<int>(); // regionId
        var cityListingsCache = new Dictionary<int, List<PropertyDto>>(); // regionId → listings
        var total = knownDistricts.Count;

        for (var i = 0; i < knownDistricts.Count; i++)
        {
            var district = knownDistricts[i];
            progress?.Report(new(district, i, total, IsStarting: true, FetchedCount: 0));

            var (regionId, city) = DistrictMap[district];
            // 用該縣市所有啟用地區的最高上限來抓城市資料（城市層級一次抓最廣範圍）
            var cityMaxPrice = districtMaxPrices
                .Where(kv => DistrictMap.TryGetValue(kv.Key, out var m) && m.RegionId == regionId)
                .Select(kv => kv.Value)
                .DefaultIfEmpty(800m)
                .Max();

            if (!cityListingsCache.TryGetValue(regionId, out var cityListings))
            {
                if (citiesQueried.Contains(regionId))
                {
                    cityListings = [];
                }
                else
                {
                    citiesQueried.Add(regionId);
                    await fetcher.WaitAsync(cancellationToken);
                    cityListings = await FetchCityAsync(context, regionId, city, cityMaxPrice, cancellationToken);
                    cityListingsCache[regionId] = cityListings;
                    logger.LogInformation("City {City} (regionid={RegionId}): {Count} total listings fetched",
                        city, regionId, cityListings.Count);
                }
            }

            // 客戶端按行政區與該區個別上限過濾
            var districtMaxPrice = districtMaxPrices[district];
            var districtResults = FilterByDistrict(cityListings, district, districtMaxPrice);
            results.AddRange(districtResults);

            progress?.Report(new(district, i, total, IsStarting: false, FetchedCount: districtResults.Count));
            logger.LogInformation("District {District} (max={Max}萬): {Count} listings (filtered from city cache)",
                district, districtMaxPrice, districtResults.Count);

            if (onDistrictCompleted is not null) await onDistrictCompleted(districtResults);
        }

        return results;
    }

    private async Task<List<PropertyDto>> FetchCityAsync(
        IBrowserContext context,
        int regionId,
        string city,
        decimal maxTotalPrice,
        CancellationToken ct)
    {
        var capturedResponses = new List<(string Url, byte[] Body)>();
        var page = await context.NewPageAsync();

        try
        {
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

            // 先訪問主頁讓 591 設置 session cookies（模擬正常用戶行為）
            logger.LogInformation("Warming up session for {City}...", city);
            await page.GotoAsync("https://sale.591.com.tw/", new PageGotoOptions
            {
                WaitUntil = WaitUntilState.NetworkIdle, // 等所有 warmup BFF 完成
                Timeout = 30_000,
            });
            // 清除 warmup（台北市預設）的 BFF 回應，避免干擾目標城市
            capturedResponses.Clear();

            // type=2 = 中古屋；price 單位為萬（BFF 同單位）
            var maxPriceWan = (int)maxTotalPrice;
            var url = $"https://sale.591.com.tw/?type=2&regionid={regionId}&price=0_{maxPriceWan}";
            logger.LogInformation("Navigating to {Url}", url);
            await page.GotoAsync(url, new PageGotoOptions
            {
                WaitUntil = WaitUntilState.NetworkIdle,
                Timeout = 30_000,
            });

            // 截圖：驗證 regionid 對應城市是否正確
            var screenshotPath = Path.Combine(Path.GetTempPath(), $"591-{city}-{regionId}-{DateTime.Now:HHmmss}.png");
            await page.ScreenshotAsync(new PageScreenshotOptions { Path = screenshotPath });
            logger.LogInformation("Screenshot saved to {Path}", screenshotPath);

            // 取得頁面顯示的城市名（多種 selector 嘗試）
            var displayedCity = await page.EvaluateAsync<string>(
                @"() => {
                    const selectors = [
                        '.city-name',
                        '[class*=""city""] span',
                        '.loc-city',
                        '.header-city',
                        '[data-key=""city""] .selected',
                        '.search-city .current',
                        'button[aria-label*=""市""]',
                    ];
                    for (const s of selectors) {
                        const el = document.querySelector(s);
                        if (el?.textContent?.trim()) return el.textContent.trim();
                    }
                    // fallback: page title
                    return document.title ?? 'unknown';
                }");
            logger.LogInformation("Page displayed city for regionid={RegionId}: {DisplayedCity}", regionId, displayedCity);

            // 只取目標 regionid 的 BFF house list 回應（過濾 warmup 殘留的台北市資料）
            var regionIdStr = $"regionid={regionId}";
            var listResponses = capturedResponses
                .Where(r => (r.Url.Contains("house") || r.Url.Contains("sale") || r.Url.Contains("list"))
                            && r.Url.Contains(regionIdStr))
                .ToList();
            logger.LogInformation("Captured {Total} JSON responses, {List} match regionid={RegionId}",
                capturedResponses.Count, listResponses.Count, regionId);

            foreach (var (responseUrl, body) in listResponses)
            {
                try
                {
                    using var doc = JsonDocument.Parse(body);

                    // 完整記錄 JSON 結構幫助診斷
                    LogJsonStructure(responseUrl, doc.RootElement);

                    var parsed = TryParseJsonResponse(doc.RootElement, city);
                    if (parsed.Count > 0)
                    {
                        logger.LogInformation("Extracted {Count} listings via API for {City}", parsed.Count, city);
                        return parsed;
                    }
                }
                catch (Exception ex)
                {
                    logger.LogWarning("Failed to parse API response {Url}: {Msg}", responseUrl, ex.Message);
                }
            }

            // 備援：HTML 解析
            var html = await page.ContentAsync();
            var htmlResults = ParseListingsFromHtml(html, city);
            if (htmlResults.Count > 0)
            {
                logger.LogInformation("Extracted {Count} listings via HTML for {City}", htmlResults.Count, city);
                return htmlResults.ToList();
            }

            logger.LogWarning("No listings for {City} (regionid={RegionId}). Captured {N} JSON API responses.",
                city, regionId, capturedResponses.Count);
            return [];
        }
        finally
        {
            await page.CloseAsync();
        }
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

    private static List<PropertyDto> FilterByDistrict(List<PropertyDto> cityListings, string district, decimal maxTotalPrice)
    {
        return cityListings.Where(l =>
            l.TotalPrice <= maxTotalPrice &&
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
