using HouseLens.Domain.Entities;
using Microsoft.EntityFrameworkCore;
using Npgsql;

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
        await db.Database.OpenConnectionAsync();
        try
        {
            // API and Worker may bootstrap the shared database simultaneously.
            await db.Database.ExecuteSqlRawAsync("SELECT pg_advisory_lock(184832467)");
            await SeedCoreAsync(db);
        }
        finally
        {
            try { await db.Database.ExecuteSqlRawAsync("SELECT pg_advisory_unlock(184832467)"); }
            finally { await db.Database.CloseConnectionAsync(); }
        }
    }

    private static async Task SeedCoreAsync(AppDbContext db)
    {
        var schema = new NpgsqlConnectionStringBuilder(db.Database.GetConnectionString()).SearchPath;
        if (string.IsNullOrWhiteSpace(schema) || schema.Contains(','))
            throw new InvalidOperationException("A single PostgreSQL Search Path is required for initialization.");
        using var identifiers = new NpgsqlCommandBuilder();
#pragma warning disable EF1003 // Schema identifiers cannot be parameters; QuoteIdentifier escapes them.
        await db.Database.ExecuteSqlRawAsync("CREATE SCHEMA IF NOT EXISTS " + identifiers.QuoteIdentifier(schema));
#pragma warning restore EF1003
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

        // 平台篩選設定：樂屋網與信義房屋支援額外篩選（型態/坪數/房數/車位）
        if (!await db.PlatformFilterConfigs.AnyAsync(p => p.SourceSite == Domain.Enums.SourceSite.Rakuya))
        {
            db.PlatformFilterConfigs.Add(new PlatformFilterConfig
            {
                SourceSite = Domain.Enums.SourceSite.Rakuya,
                MinSizePing = 0m,
                Rooms = "",
                TypeCodes = "R1,R2",
                UseCode = "1",
            });
        }

        if (!await db.PlatformFilterConfigs.AnyAsync(p => p.SourceSite == Domain.Enums.SourceSite.Sinyi))
        {
            db.PlatformFilterConfigs.Add(new PlatformFilterConfig
            {
                SourceSite = Domain.Enums.SourceSite.Sinyi,
                MinSizePing = 0m,
                Rooms = "",
                TypeCodes = "apartment,dalou,huaxia",
                UseCode = "1", // 信義不使用用途代碼，保留欄位預設
            });
        }

        if (!await db.PlatformFilterConfigs.AnyAsync(p => p.SourceSite == Domain.Enums.SourceSite.F591))
        {
            db.PlatformFilterConfigs.Add(new PlatformFilterConfig
            {
                SourceSite = Domain.Enums.SourceSite.F591,
                MinSizePing = 0m,
                Rooms = "",
                TypeCodes = "", // 591 中古屋搜尋不使用建物型態代碼（以 pattern 房數為主）
                UseCode = "1",  // 591 不使用用途代碼，保留欄位預設
            });
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
