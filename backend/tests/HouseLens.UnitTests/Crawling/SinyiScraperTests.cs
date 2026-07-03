using FluentAssertions;
using HouseLens.Application.Crawling;
using HouseLens.Infrastructure.Crawling.Scrapers;
using Xunit;

namespace HouseLens.UnitTests.Crawling;

public class SinyiScraperTests
{
    [Fact]
    public void BuildSearchUrl_FullCriteria_ProducesAllSegmentsInOrder()
    {
        var criteria = new DistrictCriteria(
            MaxTotalPrice: 1000m,
            MinSizePing: 20m,
            Rooms: "2",
            TypeCodes: "apartment,dalou,huaxia",
            UseCode: "1",
            MaxAgeYears: 30,
            ParkingCodes: "plane,auto,mix,mechanical,firstfloor,tower,other,yesparking");

        var url = SinyiScraper.BuildSearchUrl("NewTaipei-city", "234", criteria, 1);

        url.Should().Be(
            "https://www.sinyi.com.tw/buy/list/0-1000-price/apartment-dalou-huaxia-type/" +
            "plane-auto-mix-mechanical-firstfloor-tower-other-yesparking/20-up-area/0-30-year/" +
            "2-up-roomtotal/NewTaipei-city/234-zip/default-desc/1");
    }

    [Fact]
    public void BuildSearchUrl_MinimalCriteria_OmitsOptionalSegments()
    {
        var criteria = new DistrictCriteria(MaxTotalPrice: 800m, TypeCodes: "", ParkingCodes: "");

        var url = SinyiScraper.BuildSearchUrl("Taoyuan-city", "330", criteria, 3);

        url.Should().Be(
            "https://www.sinyi.com.tw/buy/list/0-800-price/apartment-townhouse-villa-dalou-huaxia-type/" +
            "Taoyuan-city/330-zip/default-desc/3");
    }

    [Fact]
    public void BuildSearchUrl_RakuyaTypeCodes_MapToSinyiSlugs()
    {
        var criteria = new DistrictCriteria(MaxTotalPrice: 800m, TypeCodes: "R1,R2");

        var url = SinyiScraper.BuildSearchUrl("NewTaipei-city", "235", criteria, 1);

        url.Should().Contain("/apartment-dalou-huaxia-type/");
    }

    [Fact]
    public void BuildSearchUrl_RakuyaParkingCodes_MapToSinyiSlugsWithYesparking()
    {
        var criteria = new DistrictCriteria(MaxTotalPrice: 800m, ParkingCodes: "PF,PM");

        var url = SinyiScraper.BuildSearchUrl("NewTaipei-city", "235", criteria, 1);

        url.Should().Contain("/plane-auto-mix-mechanical-firstfloor-tower-yesparking/");
    }

    [Fact]
    public void BuildSearchUrl_ParkingTypesWithoutYesparking_AppendsYesparking()
    {
        var criteria = new DistrictCriteria(MaxTotalPrice: 800m, ParkingCodes: "plane,mechanical");

        var url = SinyiScraper.BuildSearchUrl("NewTaipei-city", "235", criteria, 1);

        url.Should().Contain("/plane-mechanical-yesparking/");
    }

    [Fact]
    public void BuildSearchUrl_MultiRooms_UsesMinAsUpRoomtotal()
    {
        var criteria = new DistrictCriteria(MaxTotalPrice: 800m, Rooms: "3,4,5~");

        var url = SinyiScraper.BuildSearchUrl("NewTaipei-city", "235", criteria, 1);

        url.Should().Contain("/3-up-roomtotal/");
    }

    [Fact]
    public void BuildSearchUrl_UnknownTypeCodes_FallBackToResidentialDefault()
    {
        var criteria = new DistrictCriteria(MaxTotalPrice: 800m, TypeCodes: "XX,YY");

        var url = SinyiScraper.BuildSearchUrl("NewTaipei-city", "235", criteria, 1);

        url.Should().Contain("/apartment-townhouse-villa-dalou-huaxia-type/");
    }
}
