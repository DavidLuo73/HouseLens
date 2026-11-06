using HouseLens.Application.Queries;
using HouseLens.Domain.Enums;
using HouseLens.Infrastructure.Persistence;
using Microsoft.EntityFrameworkCore;

namespace HouseLens.Api.Endpoints;

public static class PropertiesEndpoints
{
    public static IEndpointRouteBuilder MapPropertiesEndpoints(this IEndpointRouteBuilder app)
    {
        app.MapGet("/api/properties", GetProperties);
        app.MapGet("/api/properties/{id:guid}", GetProperty);
        return app;
    }

    private static async Task<IResult> GetProperties(
        AppDbContext db,
        string[]? district = null,
        string[]? sourceSite = null,
        decimal? minPrice = null,
        decimal? maxPrice = null,
        bool? hasParking = null,
        bool? priceDropped = null,
        string status = "active",
        string sortBy = "score",
        int page = 1,
        int pageSize = 20)
    {
        var offset = ((long)page - 1) * pageSize;
        if (page < 1 || pageSize is < 1 or > 100 || offset > int.MaxValue ||
            minPrice < 0 || maxPrice < 0 || minPrice > maxPrice ||
            !new[] { "active", "delisted" }.Contains(status, StringComparer.OrdinalIgnoreCase) ||
            !new[] { "score", "unitPrice", "priceDrop", "postedDate" }.Contains(sortBy, StringComparer.OrdinalIgnoreCase) ||
            sourceSite?.Any(s => !Enum.GetNames<SourceSite>().Contains(s, StringComparer.OrdinalIgnoreCase)) == true)
            return Results.Problem(statusCode: StatusCodes.Status400BadRequest,
                title: "Invalid property query", detail: "Check pagination, price range, status, sort order and source names.");

        var statusEnum = status.Equals("delisted", StringComparison.OrdinalIgnoreCase)
            ? PropertyStatus.Delisted : PropertyStatus.Active;

        var query = db.Properties
            .Where(p => p.Status == statusEnum)
            .AsQueryable();

        if (district is { Length: > 0 })
            query = query.Where(p => district.Contains(p.District));

        if (sourceSite is { Length: > 0 })
        {
            var sites = sourceSite.Select(s => Enum.Parse<SourceSite>(s, ignoreCase: true)).ToHashSet();
            query = query.Where(p => p.Listings.Any(l => sites.Contains(l.SourceSite)));
        }

        if (minPrice.HasValue)
            query = query.Where(p => p.CurrentTotalPrice >= minPrice.Value);

        if (maxPrice.HasValue)
            query = query.Where(p => p.CurrentTotalPrice <= maxPrice.Value);

        if (hasParking.HasValue)
            query = query.Where(p => p.HasParking == hasParking.Value);

        if (priceDropped == true)
        {
            var latestDropped = db.PriceHistoryEntries
                .Where(h => h.ChangeFlag == PriceChangeFlag.Decreased)
                .Select(h => h.PropertyId);
            query = query.Where(p => latestDropped.Contains(p.Id));
        }

        var ordered = sortBy.ToLowerInvariant() switch
        {
            "unitprice" => query.OrderBy(p => p.CurrentUnitPrice.HasValue).ThenBy(p => p.CurrentUnitPrice),
            "pricedrop" => query.OrderBy(p => p.CurrentTotalPrice),
            "posteddate" => query.OrderByDescending(p => p.FirstSeenAt),
            _ => query.OrderByDescending(p => p.Score.HasValue).ThenByDescending(p => p.Score).ThenBy(p => p.CurrentTotalPrice)
        };

        var total = await query.CountAsync();

        var items = await ordered.ThenBy(p => p.Id)
            .Skip((int)offset)
            .Take(pageSize)
            .AsSplitQuery()
            .Select(p => new
            {
                p.Id,
                Title = p.Listings.OrderBy(l => l.Id).Select(l => l.Title).FirstOrDefault() ?? p.Address ?? "未提供",
                p.City,
                p.District,
                p.AreaPing,
                p.Floor,
                p.AgeYears,
                p.HasParking,
                p.CurrentTotalPrice,
                p.CurrentUnitPrice,
                Status = p.Status.ToString().ToLower(),
                p.Score,
                p.IsNew,
                LatestChangeFlag = p.PriceHistory.OrderByDescending(h => h.CapturedAt)
                    .Select(h => h.ChangeFlag.ToString().ToLower()).FirstOrDefault(),
                LatestChangePercent = p.PriceHistory.OrderByDescending(h => h.CapturedAt)
                    .Select(h => h.ChangePercent).FirstOrDefault(),
                LatestIsBigDrop = p.PriceHistory.OrderByDescending(h => h.CapturedAt)
                    .Select(h => h.IsBigDrop).FirstOrDefault(),
                ImageUrl = p.Listings.OrderBy(l => l.Id).Where(l => l.ImageUrl != null).Select(l => l.ImageUrl).FirstOrDefault(),
                ListingUrl = p.Listings.OrderBy(l => l.Id).Select(l => l.Url).FirstOrDefault(),
                Sources = p.Listings.Select(l => new { SourceSite = l.SourceSite.ToString(), l.Url, l.ImageUrl }),
                PriceHistory = p.PriceHistory
                    .OrderByDescending(h => h.CapturedAt)
                    .Select(h => new
                    {
                        h.CapturedAt,
                        h.TotalPrice,
                        h.UnitPrice,
                        ChangeFlag = h.ChangeFlag.ToString().ToLower(),
                        h.ChangePercent,
                        h.IsBigDrop
                    })
            })
            .ToListAsync();

        return Results.Ok(new { total, page, pageSize, items });
    }

    private static async Task<IResult> GetProperty(Guid id, AppDbContext db)
    {
        var property = await db.Properties
            .Include(p => p.Listings)
            .Include(p => p.PriceHistory.OrderByDescending(h => h.CapturedAt))
            .AsSplitQuery()
            .FirstOrDefaultAsync(p => p.Id == id);

        if (property is null)
            return Results.NotFound(new { error = new { code = "NOT_FOUND", message = "Property not found" } });

        var title = property.Listings.FirstOrDefault()?.Title ?? property.Address ?? "未提供";

        return Results.Ok(new
        {
            property.Id,
            Title = title,
            property.City,
            property.District,
            property.Address,
            property.AreaPing,
            property.Floor,
            property.AgeYears,
            property.HasParking,
            property.CurrentTotalPrice,
            property.CurrentUnitPrice,
            Status = property.Status.ToString().ToLower(),
            property.Score,
            property.IsNew,
            property.FirstSeenAt,
            property.LastSeenAt,
            ImageUrl = property.Listings.Select(l => l.ImageUrl).FirstOrDefault(u => u != null),
            ListingUrl = property.Listings.Select(l => l.Url).FirstOrDefault(),
            Sources = property.Listings.Select(l => new
            {
                SourceSite = l.SourceSite.ToString(),
                l.Url,
                l.ImageUrl,
                l.Title,
                l.PostedDate
            }),
            PriceHistory = property.PriceHistory.Select(h => new
            {
                h.CapturedAt,
                h.TotalPrice,
                h.UnitPrice,
                ChangeFlag = h.ChangeFlag.ToString().ToLower(),
                h.ChangePercent,
                h.IsBigDrop
            })
        });
    }
}
