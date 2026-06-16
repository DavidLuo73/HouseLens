namespace HouseLens.Domain.Entities;

public class ScoringConfig
{
    public int Id { get; set; } = 1;
    public decimal WeightUnitPrice { get; set; } = 0.40m;
    public decimal WeightAge { get; set; } = 0.25m;
    public decimal WeightParking { get; set; } = 0.20m;
    public decimal WeightLocation { get; set; } = 0.15m;
    public decimal BigDropPercent { get; set; } = 0.05m;
    public decimal BigDropAmount { get; set; } = 30m;
}
