namespace HouseLens.Domain.Entities;

public class DistrictConfig
{
    public int Id { get; set; }
    public string City { get; set; } = string.Empty;
    public string District { get; set; } = string.Empty;
    public decimal MaxTotalPrice { get; set; } = 800m;
    public bool IsEnabled { get; set; } = true;

    /// <summary>屋齡上限（年，0 = 不限）。樂屋網對應 age=~{N}。</summary>
    public int MaxAgeYears { get; set; }

    /// <summary>停車位需求代碼（逗號分隔，如 "PF,PM"；空 = 不限）。樂屋網對應 other= 參數：PF 平面 / PM 機械。</summary>
    public string ParkingCodes { get; set; } = string.Empty;
}
