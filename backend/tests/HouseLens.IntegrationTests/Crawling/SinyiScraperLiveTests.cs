using HouseLens.Domain.Enums;
using HouseLens.Infrastructure.Crawling;
using HouseLens.Infrastructure.Crawling.Scrapers;
using FluentAssertions;
using Microsoft.Extensions.Logging.Abstractions;

namespace HouseLens.IntegrationTests.Crawling;

/// <summary>
/// 對信義房屋實站發出請求的整合測試（會走網路）。
/// 預設 CI 可用 `--filter Category!=Live` 排除；手動驗證用
/// `dotnet test --filter Category=Live` 執行。
/// </summary>
[Trait("Category", "Live")]
public class SinyiScraperLiveTests
{
    [Fact]
    public async Task FetchAsync_Zhonghe_ReturnsValidListings()
    {
        using var fetcher = new HttpFetcher(NullLogger<HttpFetcher>.Instance);
        var scraper = new SinyiScraper(fetcher, NullLogger<SinyiScraper>.Instance);

        var districtMaxPrices = new Dictionary<string, decimal> { ["中和區"] = 800m };

        var results = await scraper.FetchAsync(districtMaxPrices);

        results.Should().NotBeEmpty("信義中和區 800萬以下應有物件");

        var first = results[0];
        first.SourceSite.Should().Be(SourceSite.Sinyi);
        first.District.Should().Be("中和區");
        first.City.Should().Be("新北市");
        first.TotalPrice.Should().BeGreaterThan(0).And.BeLessThanOrEqualTo(800m);
        first.SourceListingKey.Should().NotBeNullOrWhiteSpace();
        first.Url.Should().StartWith("https://www.sinyi.com.tw/buy/house/");
        first.ImageUrl.Should().StartWith("https://res.sinyi.com.tw/buy/");
        first.Title.Should().NotBeNullOrWhiteSpace();

        // 大多數物件應能解析出建坪（>0）
        results.Count(r => r.AreaPing > 0).Should().BeGreaterThan(0);
    }
}
