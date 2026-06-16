using HouseLens.Application.Analysis;
using HouseLens.Domain.Enums;
using HouseLens.Infrastructure.Persistence;
using Microsoft.EntityFrameworkCore;

namespace HouseLens.Api.Endpoints;

public static class AnalyticsEndpoints
{
    public static IEndpointRouteBuilder MapAnalyticsEndpoints(this IEndpointRouteBuilder app)
    {
        app.MapGet("/api/analytics/districts", GetDistricts);
        app.MapGet("/api/analytics/top-properties", GetTopProperties);
        return app;
    }

    private static async Task<IResult> GetDistricts(AppDbContext db)
    {
        var criteria = await db.TrackingCriteria.FirstOrDefaultAsync();
        var districts = criteria is not null
            ? System.Text.Json.JsonSerializer.Deserialize<string[]>(criteria.Districts) ?? []
            : Array.Empty<string>();

        var properties = await db.Properties
            .Where(p => p.Status == PropertyStatus.Active)
            .ToListAsync();

        var districtStats = districts.Select(d =>
        {
            var stats = DistrictAnalyticsService.CalcStats(properties, d);
            return new
            {
                stats.District,
                stats.PropertyCount,
                stats.AvgUnitPrice,
                stats.MinTotalPrice,
                stats.MaxTotalPrice,
                PriceBuckets = stats.PriceBuckets.Select(b => new { b.Range, b.Count }),
                Trend = stats.Trend.Select(t => new { t.Date, t.AvgUnitPrice }),
                stats.InsufficientData
            };
        });

        return Results.Ok(new { districts = districtStats });
    }

    private static async Task<IResult> GetTopProperties(AppDbContext db, string type = "topRated", int limit = 5)
    {
        if (type.Equals("bigDrop", StringComparison.OrdinalIgnoreCase))
        {
            return await GetBigDropProperties(db);
        }

        // topRated
        var criteria = await db.TrackingCriteria.FirstOrDefaultAsync();
        var districts = criteria is not null
            ? System.Text.Json.JsonSerializer.Deserialize<string[]>(criteria.Districts) ?? []
            : Array.Empty<string>();

        var properties = await db.Properties
            .Where(p => p.Status == PropertyStatus.Active && p.Score.HasValue)
            .Include(p => p.Listings)
            .ToListAsync();

        var byDistrict = districts.Select(d =>
        {
            var items = properties
                .Where(p => p.District == d)
                .OrderByDescending(p => p.Score)
                .Take(limit)
                .Select(p => new
                {
                    p.Id,
                    Title = p.Listings.FirstOrDefault()?.Title ?? p.Address ?? "未提供",
                    TotalPrice = p.CurrentTotalPrice,
                    UnitPrice = p.CurrentUnitPrice,
                    p.AgeYears,
                    p.HasParking,
                    Score = p.Score!.Value
                })
                .ToList();

            return new { district = d, items };
        });

        return Results.Ok(new { type = "topRated", byDistrict });
    }

    private static async Task<IResult> GetBigDropProperties(AppDbContext db)
    {
        var bigDropIds = await db.PriceHistoryEntries
            .Where(h => h.IsBigDrop)
            .Select(h => h.PropertyId)
            .Distinct()
            .ToListAsync();

        if (bigDropIds.Count == 0)
            return Results.Ok(new { type = "bigDrop", items = Array.Empty<object>() });

        var properties = await db.Properties
            .Where(p => bigDropIds.Contains(p.Id))
            .Include(p => p.Listings)
            .ToListAsync();

        var allEntries = await db.PriceHistoryEntries
            .Where(h => h.IsBigDrop && bigDropIds.Contains(h.PropertyId))
            .ToListAsync();

        var latestByProperty = allEntries
            .GroupBy(h => h.PropertyId)
            .ToDictionary(g => g.Key, g => g.OrderByDescending(h => h.CapturedAt).First());

        var propertyDict = properties.ToDictionary(p => p.Id);

        var items = bigDropIds
            .Where(id => propertyDict.ContainsKey(id) && latestByProperty.ContainsKey(id))
            .Select(id =>
            {
                var p = propertyDict[id];
                var h = latestByProperty[id];
                var listing = p.Listings.FirstOrDefault();
                return BigDropQueryService.CalcItem(
                    p.Id,
                    listing?.Title ?? p.Address ?? "未提供",
                    p.District,
                    p.Address,
                    h.TotalPrice,
                    h.ChangePercent,
                    listing?.Url ?? ""
                );
            });

        return Results.Ok(new { type = "bigDrop", items });
    }
}
