using HouseLens.Domain.Entities;
using HouseLens.Infrastructure.Persistence;
using Microsoft.EntityFrameworkCore;

namespace HouseLens.Api.Endpoints;

public static class ConfigEndpoints
{
    public static IEndpointRouteBuilder MapConfigEndpoints(this IEndpointRouteBuilder app)
    {
        app.MapGet("/api/config", GetConfig);
        app.MapPut("/api/config", PutConfig);

        // District price config CRUD
        app.MapGet("/api/config/districts", GetDistricts);
        app.MapPost("/api/config/districts", CreateDistrict);
        app.MapPut("/api/config/districts/{id:int}", UpdateDistrict);
        app.MapDelete("/api/config/districts/{id:int}", DeleteDistrict);
        app.MapPatch("/api/config/districts/{id:int}/toggle", ToggleDistrict);

        // Platform-specific search filters（樂屋網／信義／591／永慶／住商／中信使用）
        app.MapGet("/api/config/platform-filters", GetPlatformFilters);
        app.MapPut("/api/config/platform-filters/{sourceSite}", UpsertPlatformFilter);

        return app;
    }

    private static async Task<IResult> GetConfig(AppDbContext db)
    {
        var tracking = await db.TrackingCriteria.OrderBy(x => x.Id).FirstOrDefaultAsync() ?? new TrackingCriteria();
        var scoring = await db.ScoringConfigs.OrderBy(x => x.Id).FirstOrDefaultAsync() ?? new ScoringConfig();

        return Results.Ok(new
        {
            tracking = new
            {
                districts = System.Text.Json.JsonSerializer.Deserialize<string[]>(tracking.Districts) ?? [],
                maxTotalPrice = tracking.MaxTotalPrice
            },
            scoring = new
            {
                weightUnitPrice = scoring.WeightUnitPrice,
                weightAge = scoring.WeightAge,
                weightParking = scoring.WeightParking,
                weightLocation = scoring.WeightLocation,
                bigDropPercent = scoring.BigDropPercent,
                bigDropAmount = scoring.BigDropAmount
            }
        });
    }

    private static async Task<IResult> PutConfig(AppDbContext db, ConfigRequest request)
    {
        var weightSum = request.Scoring.WeightUnitPrice
                      + request.Scoring.WeightAge
                      + request.Scoring.WeightParking
                      + request.Scoring.WeightLocation;

        if (Math.Abs(weightSum - 1.0m) > 0.001m)
        {
            return Results.BadRequest(new
            {
                error = new { code = "INVALID_WEIGHTS", message = $"權重合計必須為 1.0，目前為 {weightSum:F4}" }
            });
        }

        var tracking = await db.TrackingCriteria.OrderBy(x => x.Id).FirstOrDefaultAsync() ?? new TrackingCriteria();
        tracking.Districts = System.Text.Json.JsonSerializer.Serialize(request.Tracking.Districts);
        tracking.MaxTotalPrice = request.Tracking.MaxTotalPrice;

        if (db.Entry(tracking).State == Microsoft.EntityFrameworkCore.EntityState.Detached)
            db.TrackingCriteria.Add(tracking);

        var scoring = await db.ScoringConfigs.OrderBy(x => x.Id).FirstOrDefaultAsync() ?? new ScoringConfig();
        scoring.WeightUnitPrice = request.Scoring.WeightUnitPrice;
        scoring.WeightAge = request.Scoring.WeightAge;
        scoring.WeightParking = request.Scoring.WeightParking;
        scoring.WeightLocation = request.Scoring.WeightLocation;
        scoring.BigDropPercent = request.Scoring.BigDropPercent;
        scoring.BigDropAmount = request.Scoring.BigDropAmount;

        if (db.Entry(scoring).State == Microsoft.EntityFrameworkCore.EntityState.Detached)
            db.ScoringConfigs.Add(scoring);

        await db.SaveChangesAsync();

        return Results.Ok(new { message = "設定已儲存" });
    }

    private static async Task<IResult> GetDistricts(AppDbContext db)
    {
        var items = await db.DistrictConfigs
            .OrderBy(d => d.City).ThenBy(d => d.District)
            .Select(d => new { d.Id, d.City, d.District, d.MaxTotalPrice, d.IsEnabled, d.MaxAgeYears, d.ParkingCodes })
            .ToListAsync();
        return Results.Ok(items);
    }

    private static async Task<IResult> CreateDistrict(AppDbContext db, DistrictConfigRequest req)
    {
        if (string.IsNullOrWhiteSpace(req.City) || string.IsNullOrWhiteSpace(req.District))
            return Results.BadRequest(new { error = new { code = "INVALID_INPUT", message = "縣市與地區不可為空" } });

        if (req.MaxTotalPrice <= 0)
            return Results.BadRequest(new { error = new { code = "INVALID_PRICE", message = "總價上限必須大於 0" } });

        if (req.MaxAgeYears < 0)
            return Results.BadRequest(new { error = new { code = "INVALID_AGE", message = "屋齡上限不可為負數" } });

        if (await db.DistrictConfigs.AnyAsync(d => d.City == req.City && d.District == req.District))
            return Results.Conflict(new { error = new { code = "DUPLICATE", message = $"{req.City}{req.District} 已存在" } });

        var entity = new DistrictConfig
        {
            City = req.City,
            District = req.District,
            MaxTotalPrice = req.MaxTotalPrice,
            IsEnabled = req.IsEnabled,
            MaxAgeYears = req.MaxAgeYears,
            ParkingCodes = req.ParkingCodes ?? "",
        };
        db.DistrictConfigs.Add(entity);
        await db.SaveChangesAsync();
        return Results.Created($"/api/config/districts/{entity.Id}",
            new { entity.Id, entity.City, entity.District, entity.MaxTotalPrice, entity.IsEnabled, entity.MaxAgeYears, entity.ParkingCodes });
    }

    private static async Task<IResult> UpdateDistrict(int id, AppDbContext db, DistrictConfigRequest req)
    {
        var entity = await db.DistrictConfigs.FindAsync(id);
        if (entity is null)
            return Results.NotFound(new { error = new { code = "NOT_FOUND", message = "找不到指定地區設定" } });

        if (req.MaxTotalPrice <= 0)
            return Results.BadRequest(new { error = new { code = "INVALID_PRICE", message = "總價上限必須大於 0" } });

        if (req.MaxAgeYears < 0)
            return Results.BadRequest(new { error = new { code = "INVALID_AGE", message = "屋齡上限不可為負數" } });

        entity.City = req.City;
        entity.District = req.District;
        entity.MaxTotalPrice = req.MaxTotalPrice;
        entity.IsEnabled = req.IsEnabled;
        entity.MaxAgeYears = req.MaxAgeYears;
        entity.ParkingCodes = req.ParkingCodes ?? "";
        await db.SaveChangesAsync();
        return Results.Ok(new { entity.Id, entity.City, entity.District, entity.MaxTotalPrice, entity.IsEnabled, entity.MaxAgeYears, entity.ParkingCodes });
    }

    private static async Task<IResult> DeleteDistrict(int id, AppDbContext db)
    {
        var entity = await db.DistrictConfigs.FindAsync(id);
        if (entity is null)
            return Results.NotFound(new { error = new { code = "NOT_FOUND", message = "找不到指定地區設定" } });

        db.DistrictConfigs.Remove(entity);
        await db.SaveChangesAsync();
        return Results.NoContent();
    }

    private static async Task<IResult> GetPlatformFilters(AppDbContext db)
    {
        var items = await db.PlatformFilterConfigs
            .OrderBy(p => p.SourceSite)
            .Select(p => new
            {
                sourceSite = p.SourceSite.ToString(),
                p.MinSizePing,
                p.Rooms,
                p.TypeCodes,
                p.UseCode,
            })
            .ToListAsync();
        return Results.Ok(items);
    }

    private static async Task<IResult> UpsertPlatformFilter(string sourceSite, AppDbContext db, PlatformFilterRequest req)
    {
        if (!Enum.TryParse<Domain.Enums.SourceSite>(sourceSite, ignoreCase: true, out var site))
            return Results.BadRequest(new { error = new { code = "INVALID_SOURCE", message = $"未知平台：{sourceSite}" } });

        if (req.MinSizePing < 0)
            return Results.BadRequest(new { error = new { code = "INVALID_SIZE", message = "最小坪數不可為負數" } });

        var entity = await db.PlatformFilterConfigs.FirstOrDefaultAsync(p => p.SourceSite == site)
            ?? db.PlatformFilterConfigs.Add(new PlatformFilterConfig { SourceSite = site }).Entity;

        entity.MinSizePing = req.MinSizePing;
        entity.Rooms = req.Rooms ?? "";
        // 允許空字串（各爬蟲自帶預設：樂屋 R1,R2；信義全部住宅型態）
        entity.TypeCodes = req.TypeCodes?.Trim() ?? "";
        entity.UseCode = string.IsNullOrWhiteSpace(req.UseCode) ? "1" : req.UseCode;
        await db.SaveChangesAsync();

        return Results.Ok(new
        {
            sourceSite = entity.SourceSite.ToString(),
            entity.MinSizePing,
            entity.Rooms,
            entity.TypeCodes,
            entity.UseCode,
        });
    }

    private static async Task<IResult> ToggleDistrict(int id, AppDbContext db)
    {
        var entity = await db.DistrictConfigs.FindAsync(id);
        if (entity is null)
            return Results.NotFound(new { error = new { code = "NOT_FOUND", message = "找不到指定地區設定" } });

        entity.IsEnabled = !entity.IsEnabled;
        await db.SaveChangesAsync();
        return Results.Ok(new { entity.Id, entity.IsEnabled });
    }
}

public record ConfigRequest(TrackingRequest Tracking, ScoringRequest Scoring);
public record TrackingRequest(string[] Districts, decimal MaxTotalPrice);
public record ScoringRequest(
    decimal WeightUnitPrice,
    decimal WeightAge,
    decimal WeightParking,
    decimal WeightLocation,
    decimal BigDropPercent,
    decimal BigDropAmount
);

public record DistrictConfigRequest(
    string City,
    string District,
    decimal MaxTotalPrice,
    bool IsEnabled = true,
    int MaxAgeYears = 0,
    string? ParkingCodes = "");

public record PlatformFilterRequest(
    decimal MinSizePing = 0m,
    string? Rooms = "",
    string? TypeCodes = "R1,R2",
    string? UseCode = "1");
