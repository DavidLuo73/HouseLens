using Microsoft.Extensions.Logging;
using Microsoft.Playwright;

namespace HouseLens.Infrastructure.Crawling;

/// <summary>
/// 以真實 Chromium 瀏覽器抓取頁面，可繞過 TLS 指紋辨識與 JS Challenge 等強力反爬機制。
/// 跨請求共用同一 BrowserContext，cookies 自動持久化。
/// 使用前須執行：playwright install chromium
/// </summary>
public sealed class PlaywrightFetcher : IAsyncDisposable
{
    private readonly ILogger<PlaywrightFetcher> _logger;
    private readonly TimeSpan _minDelay = TimeSpan.FromSeconds(3);
    private readonly SemaphoreSlim _initLock = new(1, 1);

    private IPlaywright? _playwright;
    private IBrowser? _browser;
    private IBrowserContext? _context;
    private bool _initialized;

    public PlaywrightFetcher(ILogger<PlaywrightFetcher> logger)
    {
        _logger = logger;
    }

    // cf_clearance 等 session cookie 的持久化路徑，跨應用重啟保留，不受 dotnet clean 影響。
    private static string GetCookieStatePath() =>
        Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "HouseLens", "browser-storage-state.json");

    private async Task EnsureInitializedAsync(CancellationToken ct)
    {
        if (_initialized) return;
        await _initLock.WaitAsync(ct);
        try
        {
            if (_initialized) return;
            _playwright = await Playwright.CreateAsync();

            // 使用系統 Chrome 並以有頭（可見視窗）模式運行。
            // headless=true 會暴露多項可被 WAF 辨識的 JS 屬性差異（canvas、audio、screen 等），
            // 有頭模式可完整通過這些偵測。
            _browser = await _playwright.Chromium.LaunchAsync(new()
            {
                Headless = false,
                Channel = "chrome",
                Args = ["--disable-blink-features=AutomationControlled"],
            });

            // 載入先前儲存的 StorageState（若存在），內含 cf_clearance 等 Cloudflare session cookie，
            // 讓跨應用重啟的爬取無需再次通過 Cloudflare 挑戰。
            var cookieStatePath = GetCookieStatePath();
            var hasSavedState = File.Exists(cookieStatePath);

            _context = await _browser.NewContextAsync(new()
            {
                ViewportSize = new() { Width = 1920, Height = 1080 },
                Locale = "zh-TW",
                UserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
                StorageStatePath = hasSavedState ? cookieStatePath : null,
            });

            await _context.SetExtraHTTPHeadersAsync(new Dictionary<string, string>
            {
                ["Accept-Language"] = "zh-TW,zh;q=0.9,en-US;q=0.8,en;q=0.7",
            });

            // 移除 navigator.webdriver 及其他自動化指紋
            await _context.AddInitScriptAsync("""
                Object.defineProperty(navigator, 'webdriver', {get: () => undefined});
                Object.defineProperty(navigator, 'plugins', {get: () => [1,2,3,4,5]});
                Object.defineProperty(navigator, 'languages', {get: () => ['zh-TW','zh','en-US','en']});
                window.chrome = { runtime: {} };
                const origQuery = window.navigator.permissions.query;
                window.navigator.permissions.query = (params) =>
                    params.name === 'notifications'
                        ? Promise.resolve({ state: Notification.permission })
                        : origQuery(params);
                """);

            _initialized = true;
            _logger.LogInformation(
                "PlaywrightFetcher: browser context ready ({State})",
                hasSavedState ? "loaded saved session" : "fresh session");
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "PlaywrightFetcher: browser init failed");
            throw;
        }
        finally
        {
            _initLock.Release();
        }
    }

    /// <summary>
    /// 將目前 BrowserContext 的 cookies（含 cf_clearance）序列化存入磁碟，
    /// 供下次應用啟動時載入，避免 Cloudflare 重複挑戰。
    /// </summary>
    private async Task SaveCookieStateAsync()
    {
        try
        {
            var path = GetCookieStatePath();
            Directory.CreateDirectory(Path.GetDirectoryName(path)!);
            var state = await _context!.StorageStateAsync();
            await File.WriteAllTextAsync(path, state);
            _logger.LogInformation("PlaywrightFetcher: session cookies saved to {Path}", path);
        }
        catch (Exception ex)
        {
            _logger.LogDebug(ex, "PlaywrightFetcher: failed to save session cookies (non-fatal)");
        }
    }

    public async Task<string?> FetchAsync(string url, CancellationToken ct = default)
    {
        await EnsureInitializedAsync(ct);

        var jitterMs = Random.Shared.Next(0, 2000);
        await Task.Delay(_minDelay + TimeSpan.FromMilliseconds(jitterMs), ct);

        var page = await _context!.NewPageAsync();
        try
        {
            // DOMContentLoaded：Cloudflare 挑戰頁永遠不會到達 NetworkIdle（持續發 request），
            // 改用 DOMContentLoaded 確保在 30 秒內拿到頁面，再自行處理後續等待。
            var response = await page.GotoAsync(url, new()
            {
                WaitUntil = WaitUntilState.DOMContentLoaded,
                Timeout = 30_000,
            });

            // 給 Cloudflare JS Challenge 約 3 秒自動解除（真實 Chrome 通常在此期間自動通過）
            await Task.Delay(3_000, ct);
            var content = await page.ContentAsync();

            // 偵測 Cloudflare Turnstile / Bot Management 挑戰頁面。
            // 挑戰通過後 cf_clearance cookie 會存在 BrowserContext，後續同 context 的頁面自動放行。
            if (IsCloudflareChallenge(content))
            {
                _logger.LogWarning(
                    "PlaywrightFetcher: Cloudflare challenge detected for {Url}, attempting auto-solve...", url);

                // 嘗試自動點擊 Turnstile checkbox（對 managed/checkbox 模式有效）
                await TryClickTurnstileAsync(page);

                // 等待最多 30 秒讓挑戰自動解除
                var deadline = Environment.TickCount64 + 30_000;
                while (Environment.TickCount64 < deadline && !ct.IsCancellationRequested)
                {
                    await Task.Delay(2_000, ct);
                    content = await page.ContentAsync();
                    if (!IsCloudflareChallenge(content))
                    {
                        try
                        {
                            await page.WaitForLoadStateAsync(LoadState.NetworkIdle,
                                new() { Timeout = 30_000 });
                            content = await page.ContentAsync();
                        }
                        catch (TimeoutException) { }
                        _logger.LogInformation("PlaywrightFetcher: Cloudflare challenge auto-solved for {Url}", url);
                        // 儲存 cf_clearance 等 session cookie，下次應用重啟後直接帶入，不再重複挑戰
                        _ = SaveCookieStateAsync();
                        break;
                    }
                }

                if (IsCloudflareChallenge(content))
                {
                    _logger.LogError(
                        "PlaywrightFetcher: Cloudflare challenge not resolved within 30s for {Url}", url);
                    return null;
                }
            }

            if (response is null)
            {
                _logger.LogWarning("PlaywrightFetcher: null response for {Url}", url);
                return null;
            }

            if (!response.Ok)
            {
                // 回傳 HTML 讓上層判斷（部分 WAF 以 4xx 狀態碼回傳實際內容）
                _logger.LogWarning("PlaywrightFetcher: HTTP {Status} for {Url} (html={Len} chars)",
                    response.Status, url, content.Length);
                // 5xx 視為無法使用，其餘仍回傳讓解析器嘗試
                return response.Status >= 500 ? null : content;
            }

            // 挑戰通過或無挑戰：等頁面完全穩定後再取 HTML（DOMContentLoaded 後頁面可能仍在 hydrate）
            try
            {
                await page.WaitForLoadStateAsync(LoadState.NetworkIdle, new() { Timeout = 15_000 });
                content = await page.ContentAsync();
            }
            catch (TimeoutException) { /* JS SPA 可能持續 polling，不影響靜態內容 */ }

            return content;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "PlaywrightFetcher: failed to fetch {Url}", url);
            return null;
        }
        finally
        {
            await page.CloseAsync();
        }
    }

    /// <summary>
    /// 嘗試自動點擊 Cloudflare Turnstile checkbox（managed 模式）。
    /// 先等待 iframe 出現，再等待其內容初始化，最後嘗試點擊 checkbox。
    /// </summary>
    private async Task TryClickTurnstileAsync(IPage page)
    {
        try
        {
            // 等待 Turnstile iframe 出現（最多 10 秒）
            IElementHandle? iframeEl = null;
            foreach (var selector in new[] {
                "iframe[src*='challenges.cloudflare.com']",
                "iframe[src*='cloudflare.com']",
            })
            {
                try
                {
                    iframeEl = await page.WaitForSelectorAsync(selector,
                        new() { State = WaitForSelectorState.Attached, Timeout = 10_000 });
                    if (iframeEl is not null) break;
                }
                catch (TimeoutException) { }
            }

            if (iframeEl is null)
            {
                // 印出所有 frame URL 以供診斷
                var urls = string.Join(", ", page.Frames.Select(f => f.Url));
                _logger.LogDebug("PlaywrightFetcher: no Turnstile iframe found. Frames: [{Urls}]", urls);
                return;
            }

            // 等待 iframe 內容初始化
            await Task.Delay(2_000);

            // 優先：FrameLocator（Playwright 推薦 API，支援跨 frame 操作）
            var cfFrameLocator = page.FrameLocator("iframe[src*='cloudflare.com']");
            string[] clickSelectors = [".ctp-checkbox-label", "input[type='checkbox']", "label"];
            foreach (var sel in clickSelectors)
            {
                try
                {
                    await cfFrameLocator.Locator(sel).First.ClickAsync(new() { Timeout = 4_000 });
                    _logger.LogInformation("PlaywrightFetcher: auto-clicked Turnstile via FrameLocator '{Sel}'", sel);
                    return;
                }
                catch { }
            }

            // 備援：直接遍歷 page.Frames
            foreach (var frame in page.Frames)
            {
                if (!frame.Url.Contains("cloudflare.com", StringComparison.Ordinal)) continue;

                _logger.LogDebug("PlaywrightFetcher: trying frame {Url}", frame.Url);
                foreach (var sel in clickSelectors)
                {
                    var el = await frame.QuerySelectorAsync(sel);
                    if (el is null) continue;
                    await el.ClickAsync();
                    _logger.LogInformation("PlaywrightFetcher: auto-clicked Turnstile via frame '{Sel}'", sel);
                    return;
                }
            }

            _logger.LogWarning("PlaywrightFetcher: Turnstile iframe present but no clickable element found");
        }
        catch (Exception ex)
        {
            _logger.LogDebug(ex, "PlaywrightFetcher: TryClickTurnstile failed (best-effort)");
        }
    }

    /// <summary>
    /// 判斷目前頁面是否為 Cloudflare Bot Management / Managed Challenge 封鎖頁（非正常頁面）。
    ///
    /// 注意：cf-turnstile / challenges.cloudflare.com 也出現在正常頁面的 Turnstile widget 中，
    /// 不能單獨作為封鎖條件；cf_chl_opt 與 "Just a moment" title 才是封鎖頁專有特徵。
    /// </summary>
    private static bool IsCloudflareChallenge(string html) =>
        // cf_chl_opt：Cloudflare managed challenge 頁面的專屬 JS 設定物件
        html.Contains("cf_chl_opt", StringComparison.Ordinal) ||
        // Cloudflare 封鎖頁標準 title（多語言版本）
        html.Contains(">Just a moment<", StringComparison.Ordinal) ||
        html.Contains(">請稍候<", StringComparison.Ordinal) ||
        // 強信號：challenge iframe + 中文驗證提示 同時存在（純 widget 頁不含後者）
        (html.Contains("challenges.cloudflare.com", StringComparison.Ordinal) &&
         (html.Contains("驗證您是人類", StringComparison.Ordinal) ||
          html.Contains("Verify you are human", StringComparison.Ordinal)));

    public async ValueTask DisposeAsync()
    {
        if (_context is not null) await _context.DisposeAsync();
        if (_browser is not null) await _browser.DisposeAsync();
        _playwright?.Dispose();
    }
}
