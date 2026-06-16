using HouseLens.Application.Crawling;
using HouseLens.Application.Notification;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;
using Quartz;

namespace HouseLens.Worker;

[DisallowConcurrentExecution]
public class CrawlJob(IServiceScopeFactory scopeFactory, ILogger<CrawlJob> logger) : IJob
{
    public async Task Execute(IJobExecutionContext context)
    {
        logger.LogInformation("CrawlJob started at {Time}", DateTimeOffset.Now);

        using var scope = scopeFactory.CreateScope();
        var orchestrator = scope.ServiceProvider.GetRequiredService<CrawlOrchestrator>();
        var notificationService = scope.ServiceProvider.GetRequiredService<NotificationService>();

        try
        {
            var runId = await orchestrator.RunAsync(context.CancellationToken);
            await notificationService.SendDailyNotificationsAsync(runId, context.CancellationToken);
            logger.LogInformation("CrawlJob completed at {Time}", DateTimeOffset.Now);
        }
        catch (Exception ex)
        {
            logger.LogError(ex, "CrawlJob failed");
            throw new JobExecutionException(ex, refireImmediately: false);
        }
    }
}
