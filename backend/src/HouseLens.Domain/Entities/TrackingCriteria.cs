namespace HouseLens.Domain.Entities;

public class TrackingCriteria
{
    public int Id { get; set; } = 1;
    public string Districts { get; set; } = "[]";
    public decimal MaxTotalPrice { get; set; } = 800m;
}
