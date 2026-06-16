using HouseLens.Domain.Entities;
using HouseLens.Domain.Enums;

namespace HouseLens.Application.Scoring;

public record TopPropertyItem(
    Guid Id,
    string Title,
    decimal TotalPrice,
    decimal? UnitPrice,
    int? AgeYears,
    bool HasParking,
    decimal Score
);

public record DistrictTopItems(string District, IReadOnlyList<TopPropertyItem> Items);

public static class TopPropertiesService
{
    public static IReadOnlyList<DistrictTopItems> GetTopByDistrict(
        IReadOnlyList<Property> properties,
        IReadOnlyList<string> districts,
        int limit = 5)
    {
        return districts.Select(district =>
        {
            var topItems = properties
                .Where(p => p.District == district
                         && p.Status == PropertyStatus.Active
                         && p.Score.HasValue)
                .OrderByDescending(p => p.Score)
                .Take(limit)
                .Select(p => new TopPropertyItem(
                    Id: p.Id,
                    Title: p.Listings.FirstOrDefault()?.Title ?? p.Address ?? "未提供",
                    TotalPrice: p.CurrentTotalPrice,
                    UnitPrice: p.CurrentUnitPrice,
                    AgeYears: p.AgeYears,
                    HasParking: p.HasParking,
                    Score: p.Score!.Value
                ))
                .ToList();

            return new DistrictTopItems(district, topItems);
        }).ToList();
    }
}
