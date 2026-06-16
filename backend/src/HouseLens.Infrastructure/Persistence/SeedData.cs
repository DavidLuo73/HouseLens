using HouseLens.Domain.Entities;
using Microsoft.EntityFrameworkCore;

namespace HouseLens.Infrastructure.Persistence;

public static class SeedData
{
    private static readonly (string City, string District, decimal MaxTotalPrice)[] DefaultDistrictConfigs =
    [
        ("新北市", "中和區", 1000m),
        ("新北市", "永和區", 800m),
        ("新北市", "新店區", 900m),
        ("新北市", "板橋區", 900m),
        ("新北市", "樹林區", 700m),
        ("新北市", "新莊區", 750m),
        ("桃園市", "中壢區", 600m),
        ("桃園市", "桃園區", 650m),
    ];

    public static async Task SeedAsync(AppDbContext db)
    {
        await db.Database.MigrateAsync();

        if (!await db.DistrictConfigs.AnyAsync())
        {
            foreach (var (city, district, maxPrice) in DefaultDistrictConfigs)
            {
                db.DistrictConfigs.Add(new DistrictConfig
                {
                    City = city,
                    District = district,
                    MaxTotalPrice = maxPrice,
                    IsEnabled = true,
                });
            }
        }

        if (!await db.TrackingCriteria.AnyAsync())
        {
            db.TrackingCriteria.Add(new TrackingCriteria
            {
                Id = 1,
                Districts = System.Text.Json.JsonSerializer.Serialize(
                    DefaultDistrictConfigs.Select(d => d.District).ToArray()),
                MaxTotalPrice = 1000m
            });
        }

        if (!await db.ScoringConfigs.AnyAsync())
        {
            db.ScoringConfigs.Add(new ScoringConfig
            {
                Id = 1,
                WeightUnitPrice = 0.40m,
                WeightAge = 0.25m,
                WeightParking = 0.20m,
                WeightLocation = 0.15m,
                BigDropPercent = 0.05m,
                BigDropAmount = 30m
            });
        }

        await db.SaveChangesAsync();
    }
}
