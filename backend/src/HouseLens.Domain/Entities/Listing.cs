using HouseLens.Domain.Enums;

namespace HouseLens.Domain.Entities;

public class Listing
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public Guid PropertyId { get; set; }
    public SourceSite SourceSite { get; set; }
    public string SourceListingKey { get; set; } = string.Empty;
    public string Title { get; set; } = string.Empty;
    public string Url { get; set; } = string.Empty;
    public DateTime? PostedDate { get; set; }
    public decimal LatestSourcePrice { get; set; }
    public bool IsActive { get; set; } = true;
    public string? ImageUrl { get; set; }

    public Property Property { get; set; } = null!;
}
