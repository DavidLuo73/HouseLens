using HouseLens.Application.Notification;
using HouseLens.Domain.Entities;
using HouseLens.Domain.Enums;
using HouseLens.Infrastructure.Persistence;
using Microsoft.EntityFrameworkCore;

namespace HouseLens.Infrastructure.Notification;

public class NotificationRepository(AppDbContext db) : INotificationRepository
{
    public async Task<IReadOnlyList<(Property property, PriceHistoryEntry history)>> GetBigDropsAsync(
        Guid crawlRunId, CancellationToken ct = default)
    {
        var entries = await db.PriceHistoryEntries
            .Where(h => h.CrawlRunId == crawlRunId && h.IsBigDrop)
            .Include(h => h.Property)
                .ThenInclude(p => p.Listings)
            .ToListAsync(ct);

        return entries.Select(h => (h.Property, h)).ToList();
    }

    public async Task<IReadOnlyList<(Property property, decimal score)>> GetTopPropertiesAsync(
        string district, int limit = 5, CancellationToken ct = default)
    {
        var props = await db.Properties
            .Where(p => p.District == district
                     && p.Status == PropertyStatus.Active
                     && p.Score.HasValue)
            .OrderByDescending(p => p.Score)
            .Take(limit)
            .ToListAsync(ct);

        return props.Select(p => (p, p.Score!.Value)).ToList();
    }

    public async Task SaveNotificationLogAsync(NotificationLog log, CancellationToken ct = default)
    {
        db.NotificationLogs.Add(log);
        await db.SaveChangesAsync(ct);
    }

    public async Task<IReadOnlyList<string>> GetTrackingDistrictsAsync(CancellationToken ct = default)
    {
        var criteria = await db.TrackingCriteria.FirstOrDefaultAsync(ct);
        if (criteria is null) return [];
        return System.Text.Json.JsonSerializer.Deserialize<string[]>(criteria.Districts) ?? [];
    }
}
