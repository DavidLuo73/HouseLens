using HouseLens.Domain.Enums;

namespace HouseLens.Domain.Entities;

/// <summary>
/// 平台專屬的搜尋篩選設定（每個平台一筆），套用到該平台爬取的所有地區。
/// 地區與總價上限仍由全平台共用的 DistrictConfig 決定。
/// 目前僅樂屋網使用（對應其 URL 參數）；其他平台忽略這些欄位。
/// </summary>
public class PlatformFilterConfig
{
    public int Id { get; set; }
    public SourceSite SourceSite { get; set; }

    /// <summary>最小坪數（0 = 不限制）。樂屋網對應 size={N}~。</summary>
    public decimal MinSizePing { get; set; } = 0m;

    /// <summary>房數（逗號分隔，如 "2,3,4,5~"；空字串 = 不限制）。樂屋網對應 room= 參數。</summary>
    public string Rooms { get; set; } = string.Empty;

    /// <summary>建物型態代碼（逗號分隔）。樂屋網 typecode：R1公寓/R2大樓華廈/R3套房/R4別墅/R5透天厝/R6樓中樓。</summary>
    public string TypeCodes { get; set; } = "R1,R2";

    /// <summary>用途代碼。樂屋網 usecode：1住宅/2商用/6住辦/3車位。</summary>
    public string UseCode { get; set; } = "1";
}
