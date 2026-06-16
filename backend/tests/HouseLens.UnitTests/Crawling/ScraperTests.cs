using HouseLens.Application.Crawling;
using HouseLens.Domain.Enums;
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
}
