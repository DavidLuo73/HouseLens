namespace HouseLens.Domain.Entities;

public class PropertyScore
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public Guid PropertyId { get; set; }
    public Guid CrawlRunId { get; set; }
    public decimal Score { get; set; }
    public decimal? UnitPriceScore { get; set; }
    public decimal? AgeScore { get; set; }
    public decimal? ParkingScore { get; set; }
    public decimal? LocationScore { get; set; }
    public DateTime CalculatedAt { get; set; } = DateTime.UtcNow;

    public Property Property { get; set; } = null!;
}
