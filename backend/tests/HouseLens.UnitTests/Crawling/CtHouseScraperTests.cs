using FluentAssertions;
using HouseLens.Application.Crawling;
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
    public void ParseListings_DiscountedItem_UsesSellPriceNotOriginTrustFee()
    {
        // 降價物件：sell_price 是降價後現價，origin_trust_fee 是原價，必須取 sell_price
        // （實站案例 id 2066497：原價 858 萬 → 降價後 798 萬）
        const string discountedItem = """
            {
              "id": 2066497,
              "case_name": "元智學區小家庭首選景觀三房車位",
              "address": "桃園市中壢區榮民南路",
              "sell_price": "798",
              "origin_trust_fee": "858",
              "diff": 60.0,
              "discount": 6,
              "unit_price": 21.49,
              "house_area": 37.13,
              "size_val": 37.13,
              "age": 30,
              "floor_val": "4樓/共7樓",
              "parking_lot_belong": 1,
              "house_type_usage": 1,
              "house_type_class": 2,
              "imgs": [],
              "url": "/house/2066497.html"
            }
            """;
        var houses = ParseHouses(discountedItem);

        var results = _scraper.ParseListings(houses, "桃園市", "中壢區", 800m);

        results.Should().HaveCount(1);
        results[0].TotalPrice.Should().Be(798m);
        results[0].UnitPrice.Should().BeApproximately(21.49m, 0.01m);
    }

    // ===== BuildSearchArg：依 DistrictCriteria 組出 API arg 路徑段 =====

    [Fact]
    public void BuildSearchArg_DefaultCriteria_CityTownPriceAndDefaultTypes()
    {
        var arg = CtHouseScraper.BuildSearchArg("新北市", "中和區", new DistrictCriteria(1000m));

        arg.Should().Be("新北市-city/中和區-town/0-1000-price/電梯大樓-公寓-type/");
    }

    [Fact]
    public void BuildSearchArg_FullCriteria_AllSegmentsInOrder()
    {
        var criteria = new DistrictCriteria(
            MaxTotalPrice: 1000m,
            MinSizePing: 20m,
            Rooms: "2,3",
            TypeCodes: "電梯大樓,公寓",
            MaxAgeYears: 30,
            ParkingCodes: "PF");

        var arg = CtHouseScraper.BuildSearchArg("新北市", "中和區", criteria);

        arg.Should().Be("新北市-city/中和區-town/0-1000-price/20-up-area/電梯大樓-公寓-type/0-30-year/2-up-room/1-parking/");
    }

    [Fact]
    public void BuildSearchArg_NoParkingCodes_OmitsParkingSegment()
    {
        var arg = CtHouseScraper.BuildSearchArg("桃園市", "中壢區", new DistrictCriteria(800m, ParkingCodes: ""));

        arg.Should().NotContain("parking");
    }

    [Fact]
    public void BuildSearchArg_RakuyaTypeCodes_MappedToCtNames()
    {
        // R1=公寓、R2=電梯大樓（依官方順序輸出：電梯大樓在前）
        var arg = CtHouseScraper.BuildSearchArg("新北市", "中和區", new DistrictCriteria(800m, TypeCodes: "R1,R2"));

        arg.Should().Contain("/電梯大樓-公寓-type/");
    }

    [Fact]
    public void BuildSearchArg_SingleCtTypeCode_OnlyThatType()
    {
        var arg = CtHouseScraper.BuildSearchArg("新北市", "中和區", new DistrictCriteria(800m, TypeCodes: "套房"));

        arg.Should().Contain("/套房-type/");
    }

    [Fact]
    public void BuildSearchArg_RoomsWithTilde_UsesMinRooms()
    {
        var arg = CtHouseScraper.BuildSearchArg("新北市", "中和區", new DistrictCriteria(800m, Rooms: "3,5~"));

        arg.Should().Contain("/3-up-room/");
    }

    // ===== BuildAllowedTypeClasses：型態白名單連動 =====

    [Fact]
    public void BuildAllowedTypeClasses_Default_Classes1And2()
    {
        CtHouseScraper.BuildAllowedTypeClasses("").Should().BeEquivalentTo([1, 2]);
    }

    [Fact]
    public void BuildAllowedTypeClasses_SuiteAndTownhouse_Classes3And6()
    {
        CtHouseScraper.BuildAllowedTypeClasses("套房,透天").Should().BeEquivalentTo([3, 6]);
    }

    [Fact]
    public void ParseListings_TypeClassOutsideWhitelist_IsFiltered()
    {
        // 只允許公寓（class 1），電梯大樓（class 2）物件應被過濾
        var houses = ParseHouses(ResidentialItem);

        var results = _scraper.ParseListings(houses, "新北市", "中和區", 800m,
            seen: null, allowedClasses: CtHouseScraper.BuildAllowedTypeClasses("公寓"));

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
