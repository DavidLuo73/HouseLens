using HouseLens.Domain.Enums;
using HouseLens.Infrastructure.Crawling;
using HouseLens.Infrastructure.Crawling.Scrapers;
using FluentAssertions;
using Microsoft.Extensions.Logging.Abstractions;

namespace HouseLens.IntegrationTests.Crawling;

/// <summary>
/// 對永慶房屋實站發出請求的整合測試（會走網路，需 Playwright）。
/// 預設 CI 可用 `--filter Category!=Live` 排除；手動驗證用
/// `dotnet test --filter Category=Live` 執行。
/// </summary>
[Trait("Category", "Live")]
public class YungchingScraperLiveTests
{
    [Fact]
    public async Task FetchAsync_Zhonghe_ReturnsValidListings()
    {
        await using var fetcher = new PlaywrightFetcher(NullLogger<PlaywrightFetcher>.Instance);
        var scraper = new YungchingScraper(fetcher, NullLogger<YungchingScraper>.Instance);

        var districtMaxPrices = new Dictionary<string, decimal> { ["中和區"] = 1000m };

        var results = await scraper.FetchAsync(districtMaxPrices, progress: null);

        results.Should().NotBeEmpty("永慶中和區 1000萬以下、電梯大廈/華廈/無電梯公寓應有物件");
        results.Should().HaveCountGreaterThanOrEqualTo(15, "價格區間 URL 篩選需正確生效，不應退化為熱門推薦清單");

        results.Should().OnlyContain(r => r.District == "中和區");

        var first = results[0];
        first.SourceSite.Should().Be(SourceSite.Yungching);
        first.City.Should().Be("新北市");
        first.TotalPrice.Should().BeGreaterThan(0).And.BeLessThanOrEqualTo(1000m);
        first.SourceListingKey.Should().NotBeNullOrWhiteSpace();
        first.Url.Should().StartWith("https://buy.yungching.com.tw/house/");
        first.Title.Should().NotBeNullOrWhiteSpace();

        results.Count(r => r.AreaPing > 0).Should().BeGreaterThan(0);
    }
}
