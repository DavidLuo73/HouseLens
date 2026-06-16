using HouseLens.Domain.Enums;

namespace HouseLens.Domain.Entities;

public class SourceRunResult
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public Guid CrawlRunId { get; set; }
    public SourceSite SourceSite { get; set; }
    public bool Success { get; set; }
    public int FetchedCount { get; set; }
    public string? ErrorMessage { get; set; }

    public CrawlRun CrawlRun { get; set; } = null!;
}
