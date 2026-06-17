using Microsoft.Extensions.Caching.Memory;

namespace HouseLens.Api.Endpoints;

public static class ProxyEndpoints
{
    private static readonly string[] AllowedHosts =
        ["res.sinyi.com.tw", "s.591.com.tw", "img.591.com.tw"];

    // 僅允許安全的點陣圖格式；明確排除 image/svg+xml（可內嵌 script）與 text/html
    private static readonly string[] AllowedContentTypes =
        ["image/jpeg", "image/png", "image/webp", "image/gif"];

    private const long MaxImageBytes = 5 * 1024 * 1024; // 5 MB

    public static IEndpointRouteBuilder MapProxyEndpoints(this IEndpointRouteBuilder app)
    {
        app.MapGet("/api/proxy/image", GetImage);
        return app;
    }

    private static async Task<IResult> GetImage(
        string url,
        IHttpClientFactory factory,
        IMemoryCache cache)
    {
        if (!Uri.TryCreate(url, UriKind.Absolute, out var uri) ||
            uri.Scheme != "https" ||
            !AllowedHosts.Contains(uri.Host))
            return Results.BadRequest();

        if (cache.TryGetValue<ImageCache>(url, out var cached) && cached is not null)
            return Results.File(cached.Data, cached.ContentType);

        try
        {
            var client = factory.CreateClient("ImageProxy");
            using var response = await client.GetAsync(url, HttpCompletionOption.ResponseHeadersRead);
            if (!response.IsSuccessStatusCode)
                return Results.NotFound();

            var contentType = response.Content.Headers.ContentType?.MediaType ?? "";
            if (!AllowedContentTypes.Contains(contentType))
                return Results.NotFound();

            // Content-Length 預檢，避免串流前就知道超限
            if (response.Content.Headers.ContentLength is long declaredLen && declaredLen > MaxImageBytes)
                return Results.NotFound();

            // 串流讀取並強制上限，防止無上限緩衝耗盡記憶體
            await using var src = await response.Content.ReadAsStreamAsync();
            using var ms = new MemoryStream();
            var buf = new byte[81920];
            int n;
            long total = 0;
            while ((n = await src.ReadAsync(buf)) > 0)
            {
                total += n;
                if (total > MaxImageBytes) return Results.NotFound();
                ms.Write(buf, 0, n);
            }
            var data = ms.ToArray();

            cache.Set(url, new ImageCache(data, contentType),
                new MemoryCacheEntryOptions
                {
                    AbsoluteExpirationRelativeToNow = TimeSpan.FromHours(24),
                    Size = data.Length,
                });

            return Results.File(data, contentType);
        }
        catch
        {
            return Results.NotFound();
        }
    }

    private sealed record ImageCache(byte[] Data, string ContentType);
}
