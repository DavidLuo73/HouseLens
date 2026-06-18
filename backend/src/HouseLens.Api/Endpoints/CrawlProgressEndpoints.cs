using HouseLens.Application.Crawling;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace HouseLens.Api.Endpoints;

public static class CrawlProgressEndpoints
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        Converters = { new JsonStringEnumConverter() }
    };

    public static IEndpointRouteBuilder MapCrawlProgressEndpoints(this IEndpointRouteBuilder app)
    {
        app.MapGet("/api/crawl-runs/stream", StreamProgress);
        return app;
    }

    private static async Task StreamProgress(
        CrawlProgressState state,
        HttpResponse response,
        CancellationToken ct)
    {
        response.Headers.Append("Content-Type", "text/event-stream");
        response.Headers.Append("Cache-Control", "no-cache");
        response.Headers.Append("X-Accel-Buffering", "no");

        try
        {
            while (!ct.IsCancellationRequested)
            {
                var snapshot = state.GetSnapshot();
                var json = JsonSerializer.Serialize(snapshot, JsonOptions);
                await response.WriteAsync($"data: {json}\n\n", ct);
                await response.Body.FlushAsync(ct);
                await Task.Delay(500, ct);
            }
        }
        catch (OperationCanceledException) { }
    }
}
