using FluentAssertions;
using HouseLens.Domain.Enums;
using HouseLens.Infrastructure.Crawling;
using HouseLens.Infrastructure.Crawling.Scrapers;
using Microsoft.Extensions.Logging.Abstractions;

namespace HouseLens.UnitTests.Crawling;

public class YungchingScraperTests
{
    // ParseListings 不呼叫 fetcher，傳 null! 即可
    private readonly YungchingScraper _scraper = new(
        null!,
        NullLogger<YungchingScraper>.Instance);

    // ── 住宅卡片（公寓，應被解析）
    private const string ApartmentCard = """
        <li class="search-result-list-item">
          <yc-ng-buy-house-card>
            <a class="link" href="house/1234567">
              <div class="yc-ng-buy-house-card grid">
                <div class="img-wrapper">
                  <img src="https://yccdn.yungching.com.tw/v1/image/?key=TESTKEY&amp;width=480&amp;height=0" alt="測試">
                </div>
                <div class="info-wrapper">
                  <div class="caseName">中和優質公寓</div>
                  <div class="address-wrapper">
                    <span class="address">新北市中和區中和路</span>
                  </div>
                  <div class="case-info">
                    <span class="caseType">公寓</span>
                    <span>12.5年</span>
                    <span class="regArea">建坪28.50</span>
                    <span class="mainArea">主+陽18.00</span>
                    <span class="floor">5/12樓</span>
                    <br>
                    <span class="room">3房(室)2廳1衛</span>
                  </div>
                </div>
                <div class="price-wrapper">
                  <div class="discount-and-price-wrapper">
                    <div class="price">750</div>
                  </div>
                </div>
              </div>
            </a>
          </yc-ng-buy-house-card>
        </li>
        """;

    // ── 住宅大樓卡片（含車位，應被解析）
    private const string BuildingWithParkingCard = """
        <li class="search-result-list-item">
          <yc-ng-buy-house-card>
            <a class="link" href="house/9876543">
              <div class="yc-ng-buy-house-card grid">
                <div class="img-wrapper">
                  <img src="https://yccdn.yungching.com.tw/v1/image/?key=TESTKEY2&amp;width=480&amp;height=0" alt="測試2">
                </div>
                <div class="info-wrapper">
                  <div class="caseName">永和電梯住宅含車位</div>
                  <div class="address-wrapper">
                    <span class="address">新北市永和區永和路</span>
                  </div>
                  <div class="case-info">
                    <span class="caseType">住宅大樓</span>
                    <span>8.0年</span>
                    <span class="regArea">建坪35.00</span>
                    <span class="floor">10/15樓</span>
                    <br>
                    <span class="room">3房(室)2廳2衛 車位</span>
                  </div>
                </div>
                <div class="price-wrapper">
                  <div class="discount-and-price-wrapper">
                    <div class="price">1,280</div>
                  </div>
                </div>
              </div>
            </a>
          </yc-ng-buy-house-card>
        </li>
        """;

    // ── 非住宅卡片（辦公商業大樓，應被過濾）
    private const string OfficeCard = """
        <li class="search-result-list-item">
          <yc-ng-buy-house-card>
            <a class="link" href="house/5555555">
              <div class="yc-ng-buy-house-card grid">
                <div class="img-wrapper">
                  <img src="https://yccdn.yungching.com.tw/v1/image/?key=OFFICEKEY&amp;width=480&amp;height=0" alt="辦公室">
                </div>
                <div class="info-wrapper">
                  <div class="caseName">中和商業辦公室</div>
                  <div class="address-wrapper">
                    <span class="address">新北市中和區景新街</span>
                  </div>
                  <div class="case-info">
                    <span class="caseType">辦公商業大樓</span>
                    <span>15.0年</span>
                    <span class="regArea">建坪28.15</span>
                    <span class="floor">6/19樓</span>
                  </div>
                </div>
                <div class="price-wrapper">
                  <div class="discount-and-price-wrapper">
                    <div class="price">2,388</div>
                  </div>
                </div>
              </div>
            </a>
          </yc-ng-buy-house-card>
        </li>
        """;

    // ── 超出總價上限的卡片（應被過濾）
    private const string OverPriceCard = """
        <li class="search-result-list-item">
          <yc-ng-buy-house-card>
            <a class="link" href="house/3333333">
              <div class="yc-ng-buy-house-card grid">
                <div class="img-wrapper"></div>
                <div class="info-wrapper">
                  <div class="caseName">豪宅</div>
                  <div class="address-wrapper">
                    <span class="address">新北市中和區豪宅路</span>
                  </div>
                  <div class="case-info">
                    <span class="caseType">華廈</span>
                    <span>5.0年</span>
                    <span class="regArea">建坪60.00</span>
                    <span class="floor">18/20樓</span>
                  </div>
                </div>
                <div class="price-wrapper">
                  <div class="discount-and-price-wrapper">
                    <div class="price">3,500</div>
                  </div>
                </div>
              </div>
            </a>
          </yc-ng-buy-house-card>
        </li>
        """;

    private string WrapInList(params string[] cards) =>
        $"<ul class=\"search-result-list grid\">{string.Join("", cards)}</ul>";

    [Fact]
    public void ParseListings_ResidentialCard_ParsedCorrectly()
    {
        var html = WrapInList(ApartmentCard);

        var results = _scraper.ParseListings(html, "新北市", "中和區", maxWan: 800);

        results.Should().HaveCount(1);
        var dto = results[0];
        dto.SourceSite.Should().Be(SourceSite.Yungching);
        dto.SourceListingKey.Should().Be("1234567");
        dto.Url.Should().Be("https://buy.yungching.com.tw/house/1234567");
        dto.City.Should().Be("新北市");
        dto.District.Should().Be("中和區");
        dto.Title.Should().Be("中和優質公寓");
        dto.Address.Should().Be("新北市中和區中和路");
        dto.TotalPrice.Should().Be(750m);
        dto.AreaPing.Should().Be(28.50m);
        dto.Floor.Should().Be("5/12樓");
        dto.AgeYears.Should().Be(12);  // C# Banker's Rounding: Math.Round(12.5) = 12
        dto.HasParking.Should().BeFalse();
        dto.ImageUrl.Should().StartWith("https://yccdn.yungching.com.tw/");
        dto.ImageUrl.Should().Contain("width=1200");
        dto.ImageUrl.Should().NotContain("width=480");
        dto.ImageUrl.Should().NotContain("&amp;"); // 屬性值須解碼 HTML 實體，否則 width 參數會被 CDN 忽略而退回小圖
        dto.UnitPrice.Should().BeApproximately(750m / 28.50m, 0.01m);
    }

    [Fact]
    public void ParseListings_BuildingWithParking_ParsedCorrectly()
    {
        var html = WrapInList(BuildingWithParkingCard);

        var results = _scraper.ParseListings(html, "新北市", "永和區", maxWan: 1500);

        results.Should().HaveCount(1);
        var dto = results[0];
        dto.SourceListingKey.Should().Be("9876543");
        dto.TotalPrice.Should().Be(1280m);
        dto.HasParking.Should().BeTrue();
        dto.AgeYears.Should().Be(8);
        dto.Floor.Should().Be("10/15樓");
        dto.AreaPing.Should().Be(35.00m);
    }

    [Fact]
    public void ParseListings_NonResidentialCard_IsFiltered()
    {
        var html = WrapInList(OfficeCard);

        var results = _scraper.ParseListings(html, "新北市", "中和區", maxWan: 3000);

        results.Should().BeEmpty();
    }

    [Fact]
    public void ParseListings_OverPriceCard_IsFiltered()
    {
        var html = WrapInList(OverPriceCard);

        var results = _scraper.ParseListings(html, "新北市", "中和區", maxWan: 800);

        results.Should().BeEmpty();
    }

    [Fact]
    public void ParseListings_MixedCards_OnlyResidentialUnderMaxPrice()
    {
        var html = WrapInList(ApartmentCard, OfficeCard, OverPriceCard, BuildingWithParkingCard);

        var results = _scraper.ParseListings(html, "新北市", "中和區", maxWan: 1500);

        results.Should().HaveCount(2);
        results.Select(r => r.SourceListingKey).Should().BeEquivalentTo(["1234567", "9876543"]);
    }

    [Fact]
    public void ParseListings_DuplicateKey_DeduplicatedViaSeen()
    {
        var html = WrapInList(ApartmentCard, ApartmentCard);
        var seen = new HashSet<string>();

        var results = _scraper.ParseListings(html, "新北市", "中和區", maxWan: 800, seen);

        results.Should().HaveCount(1);
        seen.Should().Contain("1234567");
    }

    [Fact]
    public void ParseListings_PriceWithCommas_ParsedCorrectly()
    {
        var html = WrapInList(BuildingWithParkingCard);

        var results = _scraper.ParseListings(html, "新北市", "永和區", maxWan: 2000);

        results.Should().HaveCount(1);
        results[0].TotalPrice.Should().Be(1280m);  // 1,280 → 1280
    }

    [Fact]
    public void ParseListings_EmptyHtml_ReturnsEmpty()
    {
        var results = _scraper.ParseListings("<html><body></body></html>", "新北市", "中和區", maxWan: 800);

        results.Should().BeEmpty();
    }

    [Fact]
    public void ParseListings_ImageUrl_UpscaledTo1200()
    {
        var html = WrapInList(ApartmentCard);

        var results = _scraper.ParseListings(html, "新北市", "中和區", maxWan: 800);

        results[0].ImageUrl.Should().StartWith("https://yccdn.yungching.com.tw/");
        results[0].ImageUrl.Should().Contain("width=1200");
        results[0].ImageUrl.Should().NotContain("width=480");
        results[0].ImageUrl.Should().NotContain("&amp;"); // 屬性值須解碼 HTML 實體，否則 width 參數會被 CDN 忽略而退回小圖
    }
}
