using HouseLens.Domain.Entities;
using HouseLens.Domain.Enums;
using Microsoft.Extensions.Logging;

namespace HouseLens.Application.Notification;

public interface INotificationRepository
{
    Task<IReadOnlyList<(Property property, PriceHistoryEntry history)>> GetBigDropsAsync(
        Guid crawlRunId, CancellationToken ct = default);
    Task<IReadOnlyList<(Property property, decimal score)>> GetTopPropertiesAsync(
        string district, int limit = 5, CancellationToken ct = default);
    Task SaveNotificationLogAsync(NotificationLog log, CancellationToken ct = default);
    Task<IReadOnlyList<string>> GetTrackingDistrictsAsync(CancellationToken ct = default);
}

public class NotificationService(
    ILineMessagingClient lineClient,
    INotificationRepository repository,
    ILogger<NotificationService> logger)
{
    public async Task SendDailyNotificationsAsync(Guid crawlRunId, CancellationToken ct = default)
    {
        await SendBigDropNotificationAsync(crawlRunId, ct);
        await SendTop5NotificationAsync(crawlRunId, ct);
    }

    private async Task SendBigDropNotificationAsync(Guid crawlRunId, CancellationToken ct)
    {
        var log = new NotificationLog { CrawlRunId = crawlRunId, Type = "BigDrop" };
        try
        {
            var bigDrops = await repository.GetBigDropsAsync(crawlRunId, ct);
            var message = NotificationBuilder.BuildBigDropMessage(bigDrops);

            if (message is null)
            {
                logger.LogInformation("No big drops for run {RunId}, skipping notification", crawlRunId);
                log.Success = true;
                await repository.SaveNotificationLogAsync(log, ct);
                return;
            }

            log.Success = await lineClient.PushMessageAsync(message, ct);
        }
        catch (Exception ex)
        {
            logger.LogError(ex, "BigDrop notification failed for run {RunId}", crawlRunId);
            log.Success = false;
            log.ErrorMessage = ex.Message;
        }
        await repository.SaveNotificationLogAsync(log, ct);
    }

    private async Task SendTop5NotificationAsync(Guid crawlRunId, CancellationToken ct)
    {
        var log = new NotificationLog { CrawlRunId = crawlRunId, Type = "Top5" };
        try
        {
            var districts = await repository.GetTrackingDistrictsAsync(ct);
            var districtTops = new List<(string, IReadOnlyList<(Property, decimal)>)>();

            foreach (var district in districts)
            {
                var tops = await repository.GetTopPropertiesAsync(district, 5, ct);
                if (tops.Count > 0)
                    districtTops.Add((district, tops));
            }

            var message = NotificationBuilder.BuildTop5Message(districtTops);
            log.Success = await lineClient.PushMessageAsync(message, ct);
        }
        catch (Exception ex)
        {
            logger.LogError(ex, "Top5 notification failed for run {RunId}", crawlRunId);
            log.Success = false;
            log.ErrorMessage = ex.Message;
        }
        await repository.SaveNotificationLogAsync(log, ct);
    }
}
