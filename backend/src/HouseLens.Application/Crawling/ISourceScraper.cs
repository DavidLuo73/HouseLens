using HouseLens.Domain.Enums;

namespace HouseLens.Application.Crawling;

public interface ISourceScraper
{
    SourceSite SourceSite { get; }
    /// <summary>
    /// districtMaxPrices: key=行政區名, value=該區總價上限（萬）
    /// </summary>
    Task<IReadOnlyList<PropertyDto>> FetchAsync(
        IReadOnlyDictionary<string, decimal> districtMaxPrices,
        CancellationToken cancellationToken = default);
}
