using HouseLens.Domain.Entities;

namespace HouseLens.Application.Scoring;

public static class ScoreCalculator
{
    private static readonly Dictionary<string, decimal> LocationWeights = new()
    {
        ["中和區"] = 0.75m,
        ["永和區"] = 0.80m,
        ["新店區"] = 0.70m,
        ["板橋區"] = 0.72m,
        ["樹林區"] = 0.55m,
        ["新莊區"] = 0.65m,
        ["中壢區"] = 0.60m,
        ["桃園區"] = 0.58m,
    };

    public static decimal Calculate(
        Property property,
        ScoringConfig config,
        decimal avgUnitPrice)
    {
        var factors = new Dictionary<string, (decimal weight, decimal? score)>
        {
            ["unitPrice"] = (config.WeightUnitPrice, CalcUnitPriceScore(property.CurrentUnitPrice, avgUnitPrice)),
            ["age"] = (config.WeightAge, CalcAgeScore(property.AgeYears)),
            ["parking"] = (config.WeightParking, property.HasParking ? 1m : 0m),
            ["location"] = (config.WeightLocation, CalcLocationScore(property.District)),
        };

        // Remove factors with missing data and renormalize weights
        var available = factors.Where(f => f.Value.score.HasValue).ToList();
        if (available.Count == 0) return 0m;

        var totalWeight = available.Sum(f => f.Value.weight);
        if (totalWeight <= 0) return 0m;

        var weightedSum = available.Sum(f => f.Value.weight * f.Value.score!.Value);
        return Math.Round(weightedSum / totalWeight, 4);
    }

    private static decimal? CalcUnitPriceScore(decimal? unitPrice, decimal avgUnitPrice)
    {
        if (!unitPrice.HasValue || avgUnitPrice <= 0) return null;
        // Score = 1 when price is 30% below avg; 0 when 30% above avg
        var ratio = (decimal)unitPrice.Value / avgUnitPrice;
        return Math.Clamp(1.3m - ratio, 0m, 1m);
    }

    private static decimal? CalcAgeScore(int? ageYears)
    {
        if (!ageYears.HasValue) return null;
        // Score decays linearly from 1 (new) to 0 (40+ years)
        return Math.Clamp(1m - ageYears.Value / 40m, 0m, 1m);
    }

    private static decimal CalcLocationScore(string district)
    {
        return LocationWeights.TryGetValue(district, out var weight) ? weight : 0.5m;
    }
}
