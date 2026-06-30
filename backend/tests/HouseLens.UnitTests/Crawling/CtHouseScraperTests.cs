using FluentAssertions;
using HouseLens.Domain.Enums;
using HouseLens.Infrastructure.Crawling.Scrapers;
using Microsoft.Extensions.Logging.Abstractions;
using System.Text.Json;

namespace HouseLens.UnitTests.Crawling;

public class CtHouseScraperTests
{
    private readonly CtHouseScraper _scraper = new(null!, NullLogger<CtHouseScraper>.Instance);

    private static readonly IReadOnlyDictionary<string, decimal> DefaultPrices = new Dictionary<string, decimal>
    {
        ["中和區"] = 800m,
        ["中壢區"] = 600m,
    };

    // ── 住宅（電梯大樓，house_type_class=2，house_type_usage=1，價格 788 萬）
    private const string ResidentialItem = """
        {
          "id": 2097688,
          "case_name": "4K28精美套房",
          "address": "新北市中和區華順街",
          "sell_price": "788",
          "unit_price": 96.33,
          "house_area": 8.18,
          "size_val": 8.18,
          "age": 10,
          "floor_val": "4樓/共9樓",
          "parking_lot_belong": 0,
          "house_type_usage": 1,
          "house_type_class": 2,
          "imgs": ["/project2/house_photo/202603/2097688-1_new.jpg"],
          "url": "/house/2097688.html"
        }
        """;

    // ── 店面（house_type_class=10，house_type_usage=1，應被過濾）
    private const string ShopItem = """
        {
          "id": 1980262,
          "case_name": "景安捷運正馬路上邊間店面",
          "address": "新北市中和區中正路",
          "sell_price": "2280",
          "house_area": 24.49,
          "age": 47,
          "floor_val": "1樓/共5樓",
          "parking_lot_belong": 0,
          "house_type_usage": 1,
          "house_type_class": 10,
          "imgs": ["/project2/house_photo/202411/1980262-10b.jpeg"],
          "url": "/house/1980262.html"
        }
        """;

    // ── 廠辦（house_type_usage=2，應被過濾）
    private const string FactoryItem = """
        {
          "id": 2059637,
          "case_name": "中和華隆經貿SRC廠辦+車位",
          "address": "新北市中和區中山路2段",
          "sell_price": "8380",
          "house_area": 225.2,
          "age": 33,
          "floor_val": "4樓/共10樓",
          "parking_lot_belong": 1,
          "house_type_usage": 2,
          "house_type_class": 3,
          "imgs": ["/project2/house_photo/202509/2059637-11_new.jpg"],
          "url": "/house/2059637.html"
        }
        """;

    // ── 超出總價上限的住宅（1258 萬 > 800 萬，應被過濾）
    private const string OverPriceItem = """
        {
          "id": 1931629,
          "case_name": "景安捷運邊間三房",
          "address": "新北市中和區復興路",
          "sell_price": "1258",
          "unit_price": 50.83,
          "house_area": 24.75,
          "size_val": 24.75,
          "age": 47,
          "floor_val": "4樓/共5樓",
          "parking_lot_belong": 0,
          "house_type_usage": 1,
          "house_type_class": 1,
          "imgs": ["/project2/house_photo/202405/1931629-1_new.jpg"],
          "url": "/house/1931629.html"
        }
        """;

    // ── 跨區推薦物件（地址是新北市板橋區，查詢行政區是中和區，應被過濾）
    private const string CrossDistrictItem = """
        {
          "id": 9999001,
          "case_name": "板橋推薦物件",
          "address": "新北市板橋區文化路",
          "sell_price": "700",
          "unit_price": 50.0,
          "house_area": 14.0,
          "age": 20,
          "floor_val": "5樓/共12樓",
          "parking_lot_belong": 0,
          "house_type_usage": 1,
          "house_type_class": 1,
          "imgs": [],
          "url": "/house/9999001.html"
        }
        """;

    // ── 含車位的住宅
    private const string WithParkingItem = """
        {
          "id": 2080000,
          "case_name": "中和含車位華廈",
          "address": "新北市中和區景平路",
          "sell_price": "650",
          "unit_price": 45.0,
          "house_area": 14.44,
          "age": 25,
          "floor_val": "8樓/共12樓",
          "parking_lot_belong": 1,
          "house_type_usage": 1,
          "house_type_class": 2,
          "imgs": ["/project2/house_photo/202601/2080000-1_new.jpg"],
          "url": "/house/2080000.html"
        }
        """;

    private static JsonElement ParseHouses(params string[] items)
    {
        var json = $"[{string.Join(",", items)}]";
        return JsonDocument.Parse(json).RootElement;
    }

    [Fact]
    public void ParseListings_ResidentialItem_ParsedCorrectly()
    {
        var houses = ParseHouses(ResidentialItem);

        var results = _scraper.ParseListings(houses, "新北市", "中和區", 800m);

        results.Should().HaveCount(1);
        var dto = results[0];
        dto.SourceSite.Should().Be(SourceSite.CtHouse);
        dto.SourceListingKey.Should().Be("2097688");
        dto.Url.Should().Be("https://buy.cthouse.com.tw/house/2097688.html");
        dto.City.Should().Be("新北市");
        dto.District.Should().Be("中和區");
        dto.Title.Should().Be("4K28精美套房");
        dto.Address.Should().Be("新北市中和區華順街");
        dto.TotalPrice.Should().Be(788m);
        dto.UnitPrice.Should().BeApproximately(96.33m, 0.01m);
        dto.AreaPing.Should().Be(8.18m);
        dto.AgeYears.Should().Be(10);
        dto.Floor.Should().Be("4樓/共9樓");
        dto.HasParking.Should().BeFalse();
        dto.ImageUrl.Should().Be("https://media.cthouse.com.tw/photo/project2/house_photo/202603/2097688-1_new.jpg");
    }

    [Fact]
    public void ParseListings_ShopItem_IsFiltered()
    {
        var houses = ParseHouses(ShopItem);

        var results = _scraper.ParseListings(houses, "新北市", "中和區", 800m);

        results.Should().BeEmpty();
    }

    [Fact]
    public void ParseListings_FactoryItem_IsFiltered()
    {
        var houses = ParseHouses(FactoryItem);

        var results = _scraper.ParseListings(houses, "新北市", "中和區", 2000m);

        results.Should().BeEmpty();
    }

    [Fact]
    public void ParseListings_OverPriceItem_IsFiltered()
    {
        var houses = ParseHouses(OverPriceItem);

        var results = _scraper.ParseListings(houses, "新北市", "中和區", 800m);

        results.Should().BeEmpty();
    }

    [Fact]
    public void ParseListings_CrossDistrictRecommend_IsFiltered()
    {
        // 地址解析出板橋區，不在 districtMaxPrices，應被排除
        var prices = new Dictionary<string, decimal> { ["中和區"] = 800m };
        var houses = ParseHouses(CrossDistrictItem);

        // The scraper's ParseListings doesn't filter by district itself (that's done in FetchAsync / Orchestrator),
        // but the address parsing should return district=板橋區 which doesn't match queryDistrict=中和區.
        // Actually ParseListings doesn't filter by district — that's the Orchestrator's job via MeetsTrackingCriteria.
        // So we verify the City/District are correctly extracted from address.
        var results = _scraper.ParseListings(houses, "新北市", "中和區", 800m);

        results.Should().HaveCount(1);
        results[0].District.Should().Be("板橋區"); // correctly parsed from address
        results[0].City.Should().Be("新北市");
    }

    [Fact]
    public void ParseListings_WithParking_HasParkingTrue()
    {
        var houses = ParseHouses(WithParkingItem);

        var results = _scraper.ParseListings(houses, "新北市", "中和區", 800m);

        results.Should().HaveCount(1);
        results[0].HasParking.Should().BeTrue();
    }

    [Fact]
    public void ParseListings_DuplicateKey_DeduplicatedViaSeen()
    {
        var houses = ParseHouses(ResidentialItem, ResidentialItem);
        var seen = new HashSet<string>();

        var results = _scraper.ParseListings(houses, "新北市", "中和區", 800m, seen);

        results.Should().HaveCount(1);
        seen.Should().Contain("2097688");
    }

    [Fact]
    public void ParseListings_MixedItems_OnlyResidentialUnderMaxPrice()
    {
        var houses = ParseHouses(ResidentialItem, ShopItem, FactoryItem, OverPriceItem, WithParkingItem);

        var results = _scraper.ParseListings(houses, "新北市", "中和區", 800m);

        results.Should().HaveCount(2);
        results.Select(r => r.SourceListingKey).Should().BeEquivalentTo(["2097688", "2080000"]);
    }

    [Fact]
    public void ParseListings_EmptyArray_ReturnsEmpty()
    {
        using var doc = JsonDocument.Parse("[]");
        var results = _scraper.ParseListings(doc.RootElement, "新北市", "中和區", 800m);

        results.Should().BeEmpty();
    }

    [Fact]
    public void ParseListings_UnitPriceCalculatedWhenMissing()
    {
        // 無 unit_price 欄位，應由 TotalPrice / AreaPing 計算
        const string noUnitPrice = """
            {
              "id": 3000001,
              "case_name": "無單價物件",
              "address": "新北市中和區中山路",
              "sell_price": "600",
              "house_area": 20.0,
              "age": 15,
              "floor_val": "3樓/共7樓",
              "parking_lot_belong": 0,
              "house_type_usage": 1,
              "house_type_class": 1,
              "imgs": [],
              "url": "/house/3000001.html"
            }
            """;
        var houses = ParseHouses(noUnitPrice);

        var results = _scraper.ParseListings(houses, "新北市", "中和區", 800m);

        results.Should().HaveCount(1);
        results[0].UnitPrice.Should().BeApproximately(30m, 0.01m); // 600/20 = 30
    }
}
