using HouseLens.Domain.Enums;

namespace HouseLens.Domain.Entities;

public class Property
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public string City { get; set; } = string.Empty;
    public string District { get; set; } = string.Empty;
    public string? Address { get; set; }
    public decimal AreaPing { get; set; }
    public string? Floor { get; set; }
    public int? AgeYears { get; set; }
    public bool HasParking { get; set; }
    public decimal CurrentTotalPrice { get; set; }
    public decimal? CurrentUnitPrice { get; set; }
    public PropertyStatus Status { get; set; } = PropertyStatus.Active;
    public decimal? Score { get; set; }
    public bool IsNew { get; set; }
    public DateTime FirstSeenAt { get; set; } = DateTime.UtcNow;
    public DateTime LastSeenAt { get; set; } = DateTime.UtcNow;
    public int MissingCount { get; set; }
    public Guid? MergedIntoPropertyId { get; set; }

    public ICollection<Listing> Listings { get; set; } = [];
    public ICollection<PriceHistoryEntry> PriceHistory { get; set; } = [];
    public ICollection<PropertyScore> Scores { get; set; } = [];
}
