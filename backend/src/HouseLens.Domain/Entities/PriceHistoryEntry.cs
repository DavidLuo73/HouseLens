using HouseLens.Domain.Enums;

namespace HouseLens.Domain.Entities;

public class PriceHistoryEntry
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public Guid PropertyId { get; set; }
    public Guid CrawlRunId { get; set; }
    public DateTime CapturedAt { get; set; } = DateTime.UtcNow;
    public decimal TotalPrice { get; set; }
    public decimal? UnitPrice { get; set; }
    public SourceSite SourceSite { get; set; }
    public PriceChangeFlag ChangeFlag { get; set; } = PriceChangeFlag.None;
    public decimal? ChangePercent { get; set; }
    public bool IsBigDrop { get; set; }

    public Property Property { get; set; } = null!;
    public CrawlRun CrawlRun { get; set; } = null!;
}
