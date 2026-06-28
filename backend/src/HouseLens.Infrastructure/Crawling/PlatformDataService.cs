using HouseLens.Domain.Enums;
using HouseLens.Infrastructure.Persistence;
using Microsoft.EntityFrameworkCore;

namespace HouseLens.Infrastructure.Crawling;

public record PlatformStats(
    SourceSite SourceSite,
    string DisplayName,
    int ListingCount,
    int PropertyCount,
    int PriceHistoryCount,
    DateTime? LastCrawlAt,
    bool? LastCrawlSuccess,
    int? LastCrawlFetchedCount);

public record PurgeResult(
    int ListingsDeleted,
    int PropertiesDeleted,
    int PriceHistoryDeleted,
    int SourceRunResultsDeleted);

public class PlatformDataService(AppDbContext db)
{
    private static readonly (SourceSite Site, string Name)[] ImplementedPlatforms =
    [
        (SourceSite.F591, "591 房屋"),
        (SourceSite.Sinyi, "信義房屋"),
        (SourceSite.Yungching, "永慶不動產"),
        (SourceSite.HBHousing, "住商不動產"),
        (SourceSite.Rakuya, "樂屋"),
    ];

    public async Task<IReadOnlyList<PlatformStats>> GetStatsAsync(CancellationToken ct = default)
    {
        var result = new List<PlatformStats>();

        foreach (var (site, name) in ImplementedPlatforms)
        {
            var listingCount = await db.Listings.CountAsync(l => l.SourceSite == site, ct);
            var propertyCount = await db.Listings
                .Where(l => l.SourceSite == site)
                .Select(l => l.PropertyId)
                .Distinct()
                .CountAsync(ct);
            var priceHistoryCount = await db.PriceHistoryEntries
                .CountAsync(h => h.SourceSite == site, ct);

            var lastResult = await db.SourceRunResults
                .Where(r => r.SourceSite == site)
                .OrderByDescending(r => r.CrawlRun.StartedAt)
                .Select(r => new { r.CrawlRun.StartedAt, r.Success, r.FetchedCount })
                .FirstOrDefaultAsync(ct);

            result.Add(new PlatformStats(
                site,
                name,
                listingCount,
                propertyCount,
                priceHistoryCount,
                lastResult?.StartedAt,
                lastResult?.Success,
                lastResult?.FetchedCount));
        }

        return result;
    }

    public async Task<PurgeResult> PurgePlatformAsync(SourceSite site, CancellationToken ct = default)
    {
        // 1. 取得受影響 PropertyId（刪除前先蒐集）
        var affectedPropertyIds = await db.Listings
            .Where(l => l.SourceSite == site)
            .Select(l => l.PropertyId)
            .Distinct()
            .ToListAsync(ct);

        // 2. 刪除該平台所有 PriceHistoryEntry（須先於 Listing/Property 刪除，避免 Cascade 搶先）
        var priceHistoryEntries = await db.PriceHistoryEntries
            .Where(h => h.SourceSite == site)
            .ToListAsync(ct);
        db.PriceHistoryEntries.RemoveRange(priceHistoryEntries);
        await db.SaveChangesAsync(ct);
        var priceHistoryDeleted = priceHistoryEntries.Count;

        // 3. 刪除該平台所有 Listing
        var listings = await db.Listings
            .Where(l => l.SourceSite == site)
            .ToListAsync(ct);
        db.Listings.RemoveRange(listings);
        await db.SaveChangesAsync(ct);
        var listingsDeleted = listings.Count;

        // 4. 刪除孤兒 Property（無任何 Listing），Cascade 連帶刪 PropertyScore
        var orphanIds = await db.Properties
            .Where(p => affectedPropertyIds.Contains(p.Id)
                     && !db.Listings.Any(l => l.PropertyId == p.Id))
            .Select(p => p.Id)
            .ToListAsync(ct);

        var orphans = await db.Properties
            .Where(p => orphanIds.Contains(p.Id))
            .Include(p => p.Scores)
            .ToListAsync(ct);
        db.Properties.RemoveRange(orphans);
        await db.SaveChangesAsync(ct);
        var propertiesDeleted = orphans.Count;

        // 5. 重算共享 Property（仍有其他平台 Listing）的最低現價
        var survivingIds = affectedPropertyIds.Except(orphanIds).ToList();
        if (survivingIds.Count > 0)
        {
            var survivors = await db.Properties
                .Where(p => survivingIds.Contains(p.Id))
                .Include(p => p.Listings)
                .ToListAsync(ct);

            foreach (var prop in survivors)
            {
                var minPrice = prop.Listings.Min(l => l.LatestSourcePrice);
                prop.CurrentTotalPrice = minPrice;
                if (prop.AreaPing is > 0)
                    prop.CurrentUnitPrice = minPrice / prop.AreaPing;
            }

            await db.SaveChangesAsync(ct);
        }

        // 6. 刪除該平台的 SourceRunResult
        var sourceRunResults = await db.SourceRunResults
            .Where(r => r.SourceSite == site)
            .ToListAsync(ct);
        db.SourceRunResults.RemoveRange(sourceRunResults);
        await db.SaveChangesAsync(ct);
        var sourceRunResultsDeleted = sourceRunResults.Count;

        return new PurgeResult(listingsDeleted, propertiesDeleted, priceHistoryDeleted, sourceRunResultsDeleted);
    }
}
