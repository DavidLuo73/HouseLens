using HouseLens.Application.Crawling;
using HouseLens.Domain.Enums;
using HouseLens.Infrastructure.Crawling;
using HouseLens.Infrastructure.Crawling.Scrapers;
using FluentAssertions;
using Microsoft.Extensions.Logging.Abstractions;

namespace HouseLens.IntegrationTests.Crawling;

/// <summary>
/// 對台灣房屋實站發出請求的整合測試（會走網路）。
/// 預設 CI 可用 `--filter Category!=Live` 排除；手動驗證用
/// `dotnet test --filter Category=Live` 執行。
/// </summary>
[Trait("Category", "Live")]
public class TwHouseScraperLiveTests
{
    [Fact]
    public async Task FetchAsync_Zhonghe_ReturnsValidListings()
    {
        using var fetcher = new HttpFetcher(NullLogger<HttpFetcher>.Instance);
        var scraper = new TwHouseScraper(fetcher, NullLogger<TwHouseScraper>.Instance);

        var districtMaxPrices = new Dictionary<string, DistrictCriteria> { ["中和區"] = new(1000m) };

        var results = await scraper.FetchAsync(districtMaxPrices, progress: null);

        results.Should().NotBeEmpty("台灣房屋中和區 1000萬以下應有物件");
        results.Should().HaveCountGreaterThanOrEqualTo(10, "分頁 zip 篩選需正確生效，不應退化為城市層級推薦清單");

        results.Should().OnlyContain(r => r.District == "中和區", "地址解析出的行政區應與查詢區一致");

        var first = results[0];
        first.SourceSite.Should().Be(SourceSite.TwHouse);
        first.City.Should().Be("新北市");
        first.TotalPrice.Should().BeGreaterThan(0).And.BeLessThanOrEqualTo(1000m);
        first.SourceListingKey.Should().NotBeNullOrWhiteSpace();
        first.Url.Should().StartWith("https://www.twhg.com.tw/buy/");
        first.Title.Should().NotBeNullOrWhiteSpace();
        first.Title.Should().NotContain(first.SourceListingKey);

        results.Should().NotContain(r => r.Title.Contains("土地"), "土地物件應被剔除");

        // 大多數物件應能解析出建坪與圖片
        results.Count(r => r.AreaPing > 0).Should().BeGreaterThan(0);
        results.Count(r => r.ImageUrl != null).Should().BeGreaterThan(0);
    }
}
