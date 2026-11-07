using HouseLens.Application.Crawling;
using HouseLens.Domain.Enums;
using HouseLens.Infrastructure.Crawling;
using HouseLens.Infrastructure.Crawling.Scrapers;
using FluentAssertions;
using Microsoft.Extensions.Logging.Abstractions;

namespace HouseLens.IntegrationTests.Crawling;

/// <summary>
/// 對住商不動產實站發出請求的整合測試（會走網路，需 Playwright）。
/// 預設 CI 可用 `--filter Category!=Live` 排除；手動驗證用
/// `dotnet test --filter Category=Live` 執行。
/// </summary>
[Trait("Category", "Live")]
public class HBHousingScraperLiveTests
{
    [Fact]
    public async Task FetchAsync_Yonghe_ReturnsValidListings()
    {
        await using var fetcher = new PlaywrightFetcher(NullLogger<PlaywrightFetcher>.Instance);
        var scraper = new HBHousingScraper(fetcher, NullLogger<HBHousingScraper>.Instance);

        var districtMaxPrices = new Dictionary<string, DistrictCriteria> { ["永和區"] = new(1000m) };

        var results = await scraper.FetchAsync(districtMaxPrices, progress: null);

        results.Should().NotBeEmpty("住商永和區 1000萬以下應有物件");
        // 不寫死筆數下限：實際物件數隨市況變動（2026-11 實站僅 6~8 筆）；
        // 「zip 篩選失效、退化為城市層級推薦清單」由下方行政區一致性檢查負責攔截

        results.Should().OnlyContain(r => r.District == "永和區", "地址解析出的行政區應與查詢區一致");

        var first = results[0];
        first.SourceSite.Should().Be(SourceSite.HBHousing);
        first.City.Should().Be("新北市");
        first.TotalPrice.Should().BeGreaterThan(0).And.BeLessThanOrEqualTo(1000m);
        first.SourceListingKey.Should().NotBeNullOrWhiteSpace();
        first.Url.Should().StartWith("https://www.hbhousing.com.tw/detail?sn=");
        first.Title.Should().NotBeNullOrWhiteSpace();

        results.Count(r => r.AreaPing > 0).Should().BeGreaterThan(0);
        results.Count(r => r.ImageUrl != null).Should().BeGreaterThan(0);
    }
}
