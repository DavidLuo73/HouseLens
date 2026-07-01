using HouseLens.Domain.Enums;

namespace HouseLens.Application.Crawling;

public interface ISourceScraper
{
    SourceSite SourceSite { get; }
    /// <summary>
    /// districtMaxPrices: key=行政區名, value=該區總價上限（萬）
    /// onDistrictCompleted: 每個行政區抓取完成後立即回呼該區結果，供呼叫端逐區儲存，
    /// 避免整體來源逾時被取消時，已抓完行政區的資料隨著例外一起遺失。
    /// </summary>
    Task<IReadOnlyList<PropertyDto>> FetchAsync(
        IReadOnlyDictionary<string, decimal> districtMaxPrices,
        IProgress<ScraperDistrictProgress>? progress,
        Func<IReadOnlyList<PropertyDto>, Task>? onDistrictCompleted = null,
        CancellationToken cancellationToken = default);
}
