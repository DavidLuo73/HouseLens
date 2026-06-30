using Microsoft.Extensions.Logging;
using System.Net;
using System.Net.Http.Headers;
using System.Text;
using System.Text.RegularExpressions;

namespace HouseLens.Infrastructure.Crawling;

public class HttpFetcher(ILogger<HttpFetcher> logger) : IDisposable
{
    private readonly HttpClient _client = CreateHttpClient();
    private readonly System.Collections.Concurrent.ConcurrentDictionary<string, bool> _robotsCache = new();
    private readonly TimeSpan _minDelay = TimeSpan.FromSeconds(3);
    private const int MaxRetries = 3;

    private static HttpClient CreateHttpClient()
    {
        var handler = new SocketsHttpHandler
        {
            AutomaticDecompression = DecompressionMethods.GZip | DecompressionMethods.Deflate | DecompressionMethods.Brotli,
            ConnectTimeout = TimeSpan.FromSeconds(15),   // TCP 握手上限
        };
        var client = new HttpClient(handler);
        client.DefaultRequestHeaders.Add("User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");
        client.DefaultRequestHeaders.Add("Accept",
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8");
        client.DefaultRequestHeaders.Add("Accept-Language", "zh-TW,zh;q=0.9,en-US;q=0.8,en;q=0.7");
        client.DefaultRequestHeaders.Add("sec-ch-ua",
            "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"");
        client.DefaultRequestHeaders.Add("sec-ch-ua-mobile", "?0");
        client.DefaultRequestHeaders.Add("sec-ch-ua-platform", "\"Windows\"");
        client.DefaultRequestHeaders.Add("Sec-Fetch-Dest", "document");
        client.DefaultRequestHeaders.Add("Sec-Fetch-Mode", "navigate");
        client.DefaultRequestHeaders.Add("Sec-Fetch-Site", "none");
        client.DefaultRequestHeaders.Add("Sec-Fetch-User", "?1");
        client.DefaultRequestHeaders.Add("Cache-Control", "max-age=0");
        client.DefaultRequestHeaders.Add("Upgrade-Insecure-Requests", "1");
        client.Timeout = Timeout.InfiniteTimeSpan;       // 由 per-request CTS 控制，避免與內部 timer 衝突
        return client;
    }

    public async Task<string?> FetchAsync(string url, CancellationToken ct = default,
        IReadOnlyDictionary<string, string>? extraHeaders = null)
    {
        var uri = new Uri(url);

        if (!await IsAllowedByRobotsAsync(uri, ct))
        {
            logger.LogInformation("robots.txt disallows {Url}", url);
            return null;
        }

        await ApplyRateLimitAsync(ct);

        for (var attempt = 1; attempt <= MaxRetries; attempt++)
        {
            using var reqCts = CancellationTokenSource.CreateLinkedTokenSource(ct);
            reqCts.CancelAfter(TimeSpan.FromSeconds(30));
            try
            {
                using var req = new HttpRequestMessage(HttpMethod.Get, url);
                if (extraHeaders is not null)
                    foreach (var kv in extraHeaders)
                        req.Headers.TryAddWithoutValidation(kv.Key, kv.Value);

                var response = await _client.SendAsync(req, reqCts.Token);
                response.EnsureSuccessStatusCode();
                return await response.Content.ReadAsStringAsync(reqCts.Token);
            }
            catch (OperationCanceledException) when (ct.IsCancellationRequested)
            {
                throw; // 外部取消，往上傳遞
            }
            catch (OperationCanceledException) when (attempt < MaxRetries)
            {
                // per-request 逾時，繼續 retry
                var delay = TimeSpan.FromSeconds(Math.Pow(2, attempt));
                logger.LogWarning("Attempt {Attempt} timed out for {Url}. Retry in {Delay}s",
                    attempt, url, delay.TotalSeconds);
                await Task.Delay(delay, ct);
            }
            catch (HttpRequestException ex) when (attempt < MaxRetries)
            {
                var delay = TimeSpan.FromSeconds(Math.Pow(2, attempt));
                logger.LogWarning("Attempt {Attempt} failed for {Url}: {Msg}. Retry in {Delay}s",
                    attempt, url, ex.Message, delay.TotalSeconds);
                await Task.Delay(delay, ct);
            }
            catch (Exception ex)
            {
                logger.LogError(ex, "Failed to fetch {Url} after {MaxRetries} attempts", url, MaxRetries);
                return null;
            }
        }

        return null;
    }

    public async Task<string?> PostJsonAsync(string url, string jsonBody, CancellationToken ct = default,
        IReadOnlyDictionary<string, string>? extraHeaders = null)
    {
        var uri = new Uri(url);

        if (!await IsAllowedByRobotsAsync(uri, ct))
        {
            logger.LogInformation("robots.txt disallows {Url}", url);
            return null;
        }

        await ApplyRateLimitAsync(ct);

        for (var attempt = 1; attempt <= MaxRetries; attempt++)
        {
            using var reqCts = CancellationTokenSource.CreateLinkedTokenSource(ct);
            reqCts.CancelAfter(TimeSpan.FromSeconds(30));
            try
            {
                using var req = new HttpRequestMessage(HttpMethod.Post, url);
                req.Content = new StringContent(jsonBody, Encoding.UTF8, "application/json");
                if (extraHeaders is not null)
                    foreach (var kv in extraHeaders)
                        req.Headers.TryAddWithoutValidation(kv.Key, kv.Value);

                var response = await _client.SendAsync(req, reqCts.Token);
                response.EnsureSuccessStatusCode();
                return await response.Content.ReadAsStringAsync(reqCts.Token);
            }
            catch (OperationCanceledException) when (ct.IsCancellationRequested)
            {
                throw;
            }
            catch (OperationCanceledException) when (attempt < MaxRetries)
            {
                var delay = TimeSpan.FromSeconds(Math.Pow(2, attempt));
                logger.LogWarning("Attempt {Attempt} timed out for {Url}. Retry in {Delay}s",
                    attempt, url, delay.TotalSeconds);
                await Task.Delay(delay, ct);
            }
            catch (HttpRequestException ex) when (attempt < MaxRetries)
            {
                var delay = TimeSpan.FromSeconds(Math.Pow(2, attempt));
                logger.LogWarning("Attempt {Attempt} failed for {Url}: {Msg}. Retry in {Delay}s",
                    attempt, url, ex.Message, delay.TotalSeconds);
                await Task.Delay(delay, ct);
            }
            catch (Exception ex)
            {
                logger.LogError(ex, "Failed to POST {Url} after {MaxRetries} attempts", url, MaxRetries);
                return null;
            }
        }

        return null;
    }

    public Task<bool> CheckRobotsAsync(string url, CancellationToken ct = default)
        => IsAllowedByRobotsAsync(new Uri(url), ct);

    public Task WaitAsync(CancellationToken ct = default)
        => ApplyRateLimitAsync(ct);

    private async Task<bool> IsAllowedByRobotsAsync(Uri uri, CancellationToken ct)
    {
        var baseUrl = $"{uri.Scheme}://{uri.Host}";
        if (_robotsCache.TryGetValue(baseUrl, out var cached))
            return cached;

        try
        {
            var robotsUrl = $"{baseUrl}/robots.txt";
            var content = await _client.GetStringAsync(robotsUrl, ct);
            var allowed = !IsPathDisallowedByRobots(content, uri.PathAndQuery);
            _robotsCache.TryAdd(baseUrl, allowed);
            return allowed;
        }
        catch
        {
            _robotsCache.TryAdd(baseUrl, true);
            return true;
        }
    }

    private static bool IsPathDisallowedByRobots(string robotsTxt, string path)
    {
        var lines = robotsTxt.Split('\n', StringSplitOptions.RemoveEmptyEntries);
        var inRelevantSection = false;

        foreach (var line in lines)
        {
            var trimmed = line.Trim();
            if (trimmed.StartsWith("User-agent:", StringComparison.OrdinalIgnoreCase))
            {
                var agent = trimmed[11..].Trim();
                inRelevantSection = agent == "*" || agent.Contains("HouseLens");
            }
            else if (inRelevantSection && trimmed.StartsWith("Disallow:", StringComparison.OrdinalIgnoreCase))
            {
                var disallowed = trimmed[9..].Trim();
                if (!string.IsNullOrEmpty(disallowed) && path.StartsWith(disallowed))
                    return true;
            }
        }

        return false;
    }

    private async Task ApplyRateLimitAsync(CancellationToken ct)
    {
        var jitterMs = Random.Shared.Next(0, 2000); // Random.Shared 是執行緒安全的
        await Task.Delay(_minDelay + TimeSpan.FromMilliseconds(jitterMs), ct);
    }

    public void Dispose() => _client.Dispose();
}
