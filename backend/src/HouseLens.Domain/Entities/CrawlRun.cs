using HouseLens.Domain.Enums;

namespace HouseLens.Domain.Entities;

public class CrawlRun
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public DateTime StartedAt { get; set; } = DateTime.UtcNow;
    public DateTime? FinishedAt { get; set; }
    public RunStatus Status { get; set; } = RunStatus.Running;
    public int NewCount { get; set; }
    public int DelistedCount { get; set; }
    public int BigDropCount { get; set; }

    public ICollection<SourceRunResult> SourceResults { get; set; } = [];
    public ICollection<PriceHistoryEntry> PriceHistoryEntries { get; set; } = [];
}
