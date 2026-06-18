using HouseLens.Application.Crawling;
using HouseLens.Domain.Entities;
using HouseLens.Domain.Enums;
using HouseLens.Infrastructure.Persistence;
using Microsoft.EntityFrameworkCore;

namespace HouseLens.Infrastructure.Crawling;

public class CrawlRepository(AppDbContext db) : ICrawlRepository
{
    public async Task<TrackingCriteria> GetTrackingCriteriaAsync(CancellationToken ct = default)
    {
        return await db.TrackingCriteria.OrderBy(x => x.Id).FirstOrDefaultAsync(ct)
            ?? new TrackingCriteria
            {
                Districts = System.Text.Json.JsonSerializer.Serialize(
                    new[] { "中和區", "永和區", "新店區", "板橋區", "樹林區", "新莊區", "中壢區", "桃園區" }),
                MaxTotalPrice = 800m
            };
    }

    public async Task<IReadOnlyList<DistrictConfig>> GetEnabledDistrictConfigsAsync(CancellationToken ct = default)
    {
        return await db.DistrictConfigs
            .Where(d => d.IsEnabled)
            .OrderBy(d => d.City).ThenBy(d => d.District)
            .ToListAsync(ct);
    }

    public async Task<Property?> FindExistingPropertyAsync(
        string sourceListingKey, SourceSite sourceSite, CancellationToken ct = default)
    {
        var listing = await db.Listings
            .Include(l => l.Property)
                .ThenInclude(p => p.Listings)
            .FirstOrDefaultAsync(l => l.SourceListingKey == sourceListingKey
                                   && l.SourceSite == sourceSite, ct);
        return listing?.Property;
    }

    public async Task<CrawlRun> CreateCrawlRunAsync(CancellationToken ct = default)
    {
        var run = new CrawlRun { StartedAt = DateTime.UtcNow, Status = RunStatus.Running };
        db.CrawlRuns.Add(run);
        await db.SaveChangesAsync(ct);
        return run;
    }

    public async Task SavePropertyAsync(Property property, CancellationToken ct = default)
    {
        if (db.Entry(property).State == Microsoft.EntityFrameworkCore.EntityState.Detached)
            db.Properties.Add(property);
        await db.SaveChangesAsync(ct);
    }

    public async Task SaveListingAsync(Listing listing, CancellationToken ct = default)
    {
        db.Listings.Add(listing);
        await db.SaveChangesAsync(ct);
    }

    public async Task SavePriceHistoryAsync(PriceHistoryEntry entry, CancellationToken ct = default)
    {
        // 同一物件可能出現在多個行政區查詢（邊界物件），僅記錄第一次
        var exists = await db.PriceHistoryEntries
            .AnyAsync(h => h.PropertyId == entry.PropertyId && h.CrawlRunId == entry.CrawlRunId, ct);
        if (exists) return;

        db.PriceHistoryEntries.Add(entry);
        try
        {
            await db.SaveChangesAsync(ct);
        }
        catch
        {
            db.Entry(entry).State = Microsoft.EntityFrameworkCore.EntityState.Detached;
            throw;
        }
    }

    public async Task SaveSourceRunResultAsync(SourceRunResult result, CancellationToken ct = default)
    {
        db.SourceRunResults.Add(result);
        await db.SaveChangesAsync(ct);
    }

    public async Task CompleteCrawlRunAsync(CrawlRun run, CancellationToken ct = default)
    {
        await db.SaveChangesAsync(ct);
    }

    public async Task<decimal?> GetPreviousPriceAsync(Guid propertyId, CancellationToken ct = default)
    {
        return await db.PriceHistoryEntries
            .Where(h => h.PropertyId == propertyId)
            .OrderByDescending(h => h.CapturedAt)
            .Select(h => (decimal?)h.TotalPrice)
            .FirstOrDefaultAsync(ct);
    }

    public async Task<ScoringConfig> GetScoringConfigAsync(CancellationToken ct = default)
    {
        return await db.ScoringConfigs.OrderBy(x => x.Id).FirstOrDefaultAsync(ct) ?? new ScoringConfig();
    }

    public async Task<IReadOnlyList<Property>> GetActivePropertiesAsync(CancellationToken ct = default)
    {
        return await db.Properties
            .Where(p => p.Status == PropertyStatus.Active)
            .ToListAsync(ct);
    }

    public async Task UpdatePropertyAsync(Property property, CancellationToken ct = default)
    {
        await db.SaveChangesAsync(ct);
    }
}
