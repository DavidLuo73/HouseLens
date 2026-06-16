using HouseLens.Domain.Entities;

namespace HouseLens.Application.Analysis;

public record DistrictStats(
    string District,
    int PropertyCount,
    decimal AvgUnitPrice,
    decimal MinTotalPrice,
    decimal MaxTotalPrice,
    IReadOnlyList<PriceBucket> PriceBuckets,
    IReadOnlyList<PriceTrendPoint> Trend,
    bool InsufficientData
);

public record PriceBucket(string Range, int Count);
public record PriceTrendPoint(DateOnly Date, decimal AvgUnitPrice);

public static class DistrictAnalyticsService
{
    private const int MinPropertiesForStats = 3;

    public static DistrictStats CalcStats(
        IReadOnlyList<Property> properties,
        string district)
    {
        var active = properties
            .Where(p => p.District == district && p.CurrentTotalPrice > 0)
            .ToList();

        if (active.Count == 0)
        {
            return new DistrictStats(
                District: district,
                PropertyCount: 0,
                AvgUnitPrice: 0,
                MinTotalPrice: 0,
                MaxTotalPrice: 0,
                PriceBuckets: [],
                Trend: [],
                InsufficientData: true
            );
        }

        var unitPrices = active
            .Select(p => p.CurrentUnitPrice.HasValue && p.CurrentUnitPrice.Value > 0
                ? p.CurrentUnitPrice.Value
                : p.AreaPing > 0 ? p.CurrentTotalPrice / p.AreaPing : 0)
            .Where(u => u > 0)
            .ToList();

        var avgUnitPrice = unitPrices.Count > 0 ? unitPrices.Average() : 0;

        var buckets = BuildBuckets(active.Select(p => p.CurrentTotalPrice).ToList());

        return new DistrictStats(
            District: district,
            PropertyCount: active.Count,
            AvgUnitPrice: avgUnitPrice,
            MinTotalPrice: active.Min(p => p.CurrentTotalPrice),
            MaxTotalPrice: active.Max(p => p.CurrentTotalPrice),
            PriceBuckets: buckets,
            Trend: [],
            InsufficientData: active.Count < MinPropertiesForStats
        );
    }

    private static IReadOnlyList<PriceBucket> BuildBuckets(List<decimal> prices)
    {
        var min = (double)prices.Min();
        var max = (double)prices.Max();
        var range = max - min;
        if (range <= 0) return [];

        var step = range / 5;
        var buckets = new List<PriceBucket>();

        for (var i = 0; i < 5; i++)
        {
            var lo = (decimal)(min + i * step);
            var hi = (decimal)(min + (i + 1) * step);
            var count = prices.Count(p => p >= lo && (i == 4 ? p <= hi : p < hi));
            buckets.Add(new PriceBucket($"{lo:F0}-{hi:F0}", count));
        }

        return buckets;
    }
}
