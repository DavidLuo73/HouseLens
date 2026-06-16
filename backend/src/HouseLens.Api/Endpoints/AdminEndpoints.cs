using HouseLens.Application.Crawling;

namespace HouseLens.Api.Endpoints;

public static class AdminEndpoints
{
    // 防止並發觸發多個爬蟲（單使用者本機部署）
    private static readonly SemaphoreSlim _crawlLock = new(1, 1);

    public static IEndpointRouteBuilder MapAdminEndpoints(this IEndpointRouteBuilder app)
    {
        app.MapPost("/api/admin/trigger-crawl", TriggerCrawl);
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
}
