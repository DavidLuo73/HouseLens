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

        return app;
    }

    private static async Task<IResult> GetConfig(AppDbContext db)
    {
        var tracking = await db.TrackingCriteria.FirstOrDefaultAsync() ?? new TrackingCriteria();
        var scoring = await db.ScoringConfigs.FirstOrDefaultAsync() ?? new ScoringConfig();

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

        var tracking = await db.TrackingCriteria.FirstOrDefaultAsync() ?? new TrackingCriteria();
        tracking.Districts = System.Text.Json.JsonSerializer.Serialize(request.Tracking.Districts);
        tracking.MaxTotalPrice = request.Tracking.MaxTotalPrice;

        if (db.Entry(tracking).State == Microsoft.EntityFrameworkCore.EntityState.Detached)
            db.TrackingCriteria.Add(tracking);

        var scoring = await db.ScoringConfigs.FirstOrDefaultAsync() ?? new ScoringConfig();
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
            .Select(d => new { d.Id, d.City, d.District, d.MaxTotalPrice, d.IsEnabled })
            .ToListAsync();
        return Results.Ok(items);
    }

    private static async Task<IResult> CreateDistrict(AppDbContext db, DistrictConfigRequest req)
    {
        if (string.IsNullOrWhiteSpace(req.City) || string.IsNullOrWhiteSpace(req.District))
            return Results.BadRequest(new { error = new { code = "INVALID_INPUT", message = "縣市與地區不可為空" } });

        if (req.MaxTotalPrice <= 0)
            return Results.BadRequest(new { error = new { code = "INVALID_PRICE", message = "總價上限必須大於 0" } });

        if (await db.DistrictConfigs.AnyAsync(d => d.City == req.City && d.District == req.District))
            return Results.Conflict(new { error = new { code = "DUPLICATE", message = $"{req.City}{req.District} 已存在" } });

        var entity = new DistrictConfig
        {
            City = req.City,
            District = req.District,
            MaxTotalPrice = req.MaxTotalPrice,
            IsEnabled = req.IsEnabled,
        };
        db.DistrictConfigs.Add(entity);
        await db.SaveChangesAsync();
        return Results.Created($"/api/config/districts/{entity.Id}",
            new { entity.Id, entity.City, entity.District, entity.MaxTotalPrice, entity.IsEnabled });
    }

    private static async Task<IResult> UpdateDistrict(int id, AppDbContext db, DistrictConfigRequest req)
    {
        var entity = await db.DistrictConfigs.FindAsync(id);
        if (entity is null)
            return Results.NotFound(new { error = new { code = "NOT_FOUND", message = "找不到指定地區設定" } });

        if (req.MaxTotalPrice <= 0)
            return Results.BadRequest(new { error = new { code = "INVALID_PRICE", message = "總價上限必須大於 0" } });

        entity.City = req.City;
        entity.District = req.District;
        entity.MaxTotalPrice = req.MaxTotalPrice;
        entity.IsEnabled = req.IsEnabled;
        await db.SaveChangesAsync();
        return Results.Ok(new { entity.Id, entity.City, entity.District, entity.MaxTotalPrice, entity.IsEnabled });
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

public record DistrictConfigRequest(string City, string District, decimal MaxTotalPrice, bool IsEnabled = true);
