using HouseLens.Application.Crawling;
using HouseLens.Domain.Enums;
using HouseLens.Infrastructure.Crawling;

namespace HouseLens.Api.Endpoints;

public static class AdminEndpoints
{
    // 防止並發觸發多個爬蟲（單使用者本機部署）
    private static readonly SemaphoreSlim _crawlLock = new(1, 1);

    public static IEndpointRouteBuilder MapAdminEndpoints(this IEndpointRouteBuilder app)
    {
        app.MapPost("/api/admin/trigger-crawl", TriggerCrawl);
        app.MapGet("/api/admin/platform-stats", GetPlatformStats);
        app.MapDelete("/api/admin/platform/{sourceSite}", PurgePlatform);
        app.MapPost("/api/admin/platform/{sourceSite}/recrawl", RecrawlPlatform);
        return app;
    }

    private static IResult TriggerCrawl(IServiceScopeFactory scopeFactory, ILoggerFactory loggerFactory)
    {
        if (!_crawlLock.Wait(0))
            return Results.Conflict(new { message = "爬蟲正在執行中，請稍後再試" });

        var logger = loggerFactory.CreateLogger("AdminEndpoints");
        _ = Task.Run(async () =>
        {
            try
            {
                using var scope = scopeFactory.CreateScope();
                var orchestrator = scope.ServiceProvider.GetRequiredService<CrawlOrchestrator>();
                var runId = await orchestrator.RunAsync();
                logger.LogInformation("Manual crawl completed. RunId={RunId}", runId);
            }
            catch (Exception ex)
            {
                logger.LogError(ex, "Manual crawl failed");
            }
            finally
            {
                _crawlLock.Release();
            }
        });

        return Results.Accepted("/api/crawl-runs/latest",
            new { message = "爬蟲已觸發，請呼叫 GET /api/crawl-runs/latest 查詢進度" });
    }

    private static async Task<IResult> GetPlatformStats(PlatformDataService svc, CancellationToken ct)
    {
        var stats = await svc.GetStatsAsync(ct);
        return Results.Ok(stats);
    }

    private static async Task<IResult> PurgePlatform(
        string sourceSite,
        PlatformDataService svc,
        ILoggerFactory loggerFactory,
        CancellationToken ct)
    {
        if (!Enum.TryParse<SourceSite>(sourceSite, ignoreCase: true, out var site))
            return Results.BadRequest(new { message = $"未知平台：{sourceSite}" });

        if (!_crawlLock.Wait(0))
            return Results.Conflict(new { message = "爬蟲正在執行中，無法同時清空資料" });

        try
        {
            var logger = loggerFactory.CreateLogger("AdminEndpoints");
            logger.LogInformation("Purging platform {Site}", site);
            var result = await svc.PurgePlatformAsync(site, ct);
            logger.LogInformation(
                "Purged {Site}: listings={L}, properties={P}, history={H}, sourceResults={S}",
                site, result.ListingsDeleted, result.PropertiesDeleted,
                result.PriceHistoryDeleted, result.SourceRunResultsDeleted);
            return Results.Ok(result);
        }
        finally
        {
            _crawlLock.Release();
        }
    }

    private static IResult RecrawlPlatform(
        string sourceSite,
        IServiceScopeFactory scopeFactory,
        ILoggerFactory loggerFactory)
    {
        if (!Enum.TryParse<SourceSite>(sourceSite, ignoreCase: true, out var site))
            return Results.BadRequest(new { message = $"未知平台：{sourceSite}" });

        if (!_crawlLock.Wait(0))
            return Results.Conflict(new { message = "爬蟲正在執行中，請稍後再試" });

        var logger = loggerFactory.CreateLogger("AdminEndpoints");
        _ = Task.Run(async () =>
        {
            try
            {
                using var scope = scopeFactory.CreateScope();
                var orchestrator = scope.ServiceProvider.GetRequiredService<CrawlOrchestrator>();
                var runId = await orchestrator.RunAsync(onlySource: site);
                logger.LogInformation("Single-platform recrawl completed. Site={Site} RunId={RunId}", site, runId);
            }
            catch (Exception ex)
            {
                logger.LogError(ex, "Single-platform recrawl failed for {Site}", site);
            }
            finally
            {
                _crawlLock.Release();
            }
        });

        return Results.Accepted("/api/crawl-runs/latest",
            new { message = $"{site} 重新抓取已觸發，請呼叫 GET /api/crawl-runs/latest 查詢進度" });
    }
}
