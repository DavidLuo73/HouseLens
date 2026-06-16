namespace HouseLens.Domain.Entities;

public class NotificationLog
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public Guid CrawlRunId { get; set; }
    public string Type { get; set; } = string.Empty;
    public bool Success { get; set; }
    public string? ErrorMessage { get; set; }
    public DateTime SentAt { get; set; } = DateTime.UtcNow;
}
