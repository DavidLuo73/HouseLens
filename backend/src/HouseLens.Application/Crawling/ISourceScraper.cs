using HouseLens.Domain.Enums;

namespace HouseLens.Application.Crawling;

/// <summary>
/// 單一行政區的爬取條件。
/// MaxTotalPrice=總價上限（萬）；MinSizePing=最小坪數（0=不限）；
/// Rooms=房數（逗號分隔，如 "2,3,4,5~"，空=不限）；
/// TypeCodes/UseCode=建物型態/用途代碼（目前僅樂屋網使用，其餘平台自行對映或忽略）。
/// </summary>
public record DistrictCriteria(
    decimal MaxTotalPrice,
    decimal MinSizePing = 0m,
    string Rooms = "",
    string TypeCodes = "R1,R2",
    string UseCode = "1");

public interface ISourceScraper
{
    SourceSite SourceSite { get; }
    /// <summary>
    /// districtCriteria: key=行政區名, value=該區爬取條件
    /// onDistrictCompleted: 每個行政區抓取完成後立即回呼該區結果，供呼叫端逐區儲存，
    /// 避免整體來源逾時被取消時，已抓完行政區的資料隨著例外一起遺失。
    /// </summary>
    Task<IReadOnlyList<PropertyDto>> FetchAsync(
        IReadOnlyDictionary<string, DistrictCriteria> districtCriteria,
        IProgress<ScraperDistrictProgress>? progress,
        Func<IReadOnlyList<PropertyDto>, Task>? onDistrictCompleted = null,
        CancellationToken cancellationToken = default);
}
