using Microsoft.Extensions.Caching.Memory;

namespace HouseLens.Api.Endpoints;

public static class ProxyEndpoints
{
    private static readonly string[] AllowedHosts =
        ["res.sinyi.com.tw", "s.591.com.tw", "img.591.com.tw"];

    // 僅允許安全的點陣圖格式；明確排除 image/svg+xml（可內嵌 script）與 text/html
    private static readonly string[] AllowedContentTypes =
        ["image/jpeg", "image/png", "image/webp", "image/gif"];

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
            using var response = await client.GetAsync(url);
            if (!response.IsSuccessStatusCode)
                return Results.NotFound();

            var contentType = response.Content.Headers.ContentType?.MediaType ?? "";
            if (!AllowedContentTypes.Contains(contentType))
                return Results.NotFound();

            var data = await response.Content.ReadAsByteArrayAsync();

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
