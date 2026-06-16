namespace HouseLens.Domain.Entities;

public class DistrictConfig
{
    public int Id { get; set; }
    public string City { get; set; } = string.Empty;
    public string District { get; set; } = string.Empty;
    public decimal MaxTotalPrice { get; set; } = 800m;
    public bool IsEnabled { get; set; } = true;
}
