using HouseLens.Application.Crawling;
using HouseLens.Domain.Enums;
using HouseLens.Infrastructure.Crawling.Scrapers;
using FluentAssertions;

namespace HouseLens.UnitTests.Crawling;

public class ScraperTests
{
    // T020: HTML fixture tests for scraper parsing
    // These tests use offline HTML snippets to validate scraper output
    // without making real network calls.

    [Fact]
    public void PropertyDto_ShouldHaveAllRequiredFields()
    {
        var dto = new PropertyDto(
            City: "新北市",
            District: "中和區",
            Address: "中和路100號",
            AreaPing: 28.5m,
            Floor: "5/12",
            AgeYears: 10,
            HasParking: true,
            TotalPrice: 780m,
            UnitPrice: 27.4m,
            SourceSite: SourceSite.F591,
            SourceListingKey: "abc123",
            Title: "中和優質公寓",
            Url: "https://591.com.tw/property/abc123",
            PostedDate: null
        );

        dto.City.Should().Be("新北市");
        dto.District.Should().Be("中和區");
        dto.TotalPrice.Should().Be(780m);
        dto.HasParking.Should().BeTrue();
        dto.SourceSite.Should().Be(SourceSite.F591);
    }

    [Fact]
    public void PropertyDto_WithMissingOptionalFields_ShouldBeNull()
    {
        var dto = new PropertyDto(
            City: "新北市",
            District: "板橋區",
            Address: null,
            AreaPing: 30m,
            Floor: null,
            AgeYears: null,
            HasParking: false,
            TotalPrice: 650m,
            UnitPrice: null,
            SourceSite: SourceSite.Rakuya,
            SourceListingKey: "xyz789",
            Title: "板橋兩房",
            Url: "https://rakuya.com.tw/xyz789",
            PostedDate: null
        );

        dto.Address.Should().BeNull();
        dto.Floor.Should().BeNull();
        dto.AgeYears.Should().BeNull();
        dto.UnitPrice.Should().BeNull();
    }

    // ===== 591 搜尋 URL 組裝 =====

    [Fact]
    public void F591_BuildSearchUrl_WithAllCriteria_ShouldIncludeAllParams()
    {
        // 對應實站範例：中和區 1000 萬以下、屋齡 30 年內、含車位、20 坪以上、2~5 房
        var criteria = new DistrictCriteria(
            MaxTotalPrice: 1000m,
            MinSizePing: 20m,
            Rooms: "2,3,4,5~",
            MaxAgeYears: 30,
            ParkingCodes: "PF,PM");

        var url = F591Scraper.BuildSearchUrl(3, 38, criteria);

        url.Should().StartWith("https://sale.591.com.tw/?shType=list&type=2&regionid=3&section=38");
        url.Should().Contain("&price=$_$1000");
        url.Should().Contain("&houseage=$_$30");
        url.Should().Contain("&parking=1,2,3");
        url.Should().Contain("&area=$20_$");
        url.Should().Contain("&pattern=2,3,4,5");
        url.Should().NotContain("firstRow");
    }

    [Fact]
    public void F591_BuildSearchUrl_WithDefaults_ShouldOmitOptionalParams()
    {
        var criteria = new DistrictCriteria(MaxTotalPrice: 800m);

        var url = F591Scraper.BuildSearchUrl(6, 67, criteria, firstRow: 60);

        url.Should().Be("https://sale.591.com.tw/?shType=list&type=2&regionid=6&section=67&price=$_$800&firstRow=60");
    }

    [Theory]
    [InlineData("", null)]
    [InlineData("PF", "1")]
    [InlineData("PM", "2")]
    [InlineData("PF,PM", "1,2,3")]
    [InlineData("1,2", "1,2,3")]
    [InlineData("3", "3")]
    [InlineData("XX", null)]
    public void F591_BuildParkingParam_ShouldMapRakuyaCodes(string codes, string? expected)
    {
        F591Scraper.BuildParkingParam(codes).Should().Be(expected);
    }

    [Theory]
    [InlineData("", null)]
    [InlineData("2,3,4,5~", "2,3,4,5")]
    [InlineData("5~", "5")]
    [InlineData("6", "5")]
    [InlineData("abc", null)]
    public void F591_BuildPatternParam_ShouldMapRoomCodes(string rooms, string? expected)
    {
        F591Scraper.BuildPatternParam(rooms).Should().Be(expected);
    }
}
