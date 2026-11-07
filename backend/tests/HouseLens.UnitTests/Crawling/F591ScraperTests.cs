using HouseLens.Domain.Enums;
using HouseLens.Infrastructure.Crawling.Scrapers;
using FluentAssertions;

namespace HouseLens.UnitTests.Crawling;

/// <summary>
/// 591 列表頁已改為 Nuxt SSR：物件資料不再由瀏覽器另外呼叫 BFF API 取得，
/// 而是內嵌在 window.__NUXT__.pinia["sale-list"]（wareList / wareTotal / isRecommendWare）。
/// 以下 fixture 取自 2026-11-08 實站 sale-list store 的欄位結構。
/// </summary>
public class F591ScraperTests
{
    private const string SearchResultJson = """
    {
      "total": 3155,
      "isRecommend": false,
      "list": [
        {
          "type": "2", "houseid": 21003434, "kind": 9, "kind_name": "住宅", "shape_name": "公寓",
          "region_id": 3, "region_name": "新北市", "section_id": 38, "section_name": "中和區",
          "title": "明德公園公寓2樓", "has_carport": 0, "room": "2房1廳1衛", "floor": "2F/4F",
          "photo_url": "https://img1.591.com.tw/house/2026/10/02/179093242510629701.jpg!400x300.jpg",
          "area": 18.72, "houseage": 48, "showhouseage": "48年", "address": "華福街42巷",
          "unit_price": "55.02萬/坪", "unitprice": "55.02", "price": 1030, "showprice": "1,030",
          "is_carport": "0"
        },
        {
          "type": "2", "houseid": 21015430, "section_name": "中和區", "title": "中和電梯三房含車位",
          "has_carport": 1, "floor": "8F/12F", "area": 35.5, "houseage": 20, "address": "中山路二段",
          "unitprice": "42.2", "price": 1498, "is_carport": "1"
        }
      ]
    }
    """;

    [Fact]
    public void ParseNuxtSaleList_ShouldParseWareListAndTotal()
    {
        var (listings, total) = F591Scraper.ParseNuxtSaleList(SearchResultJson, "新北市");

        total.Should().Be(3155);
        listings.Should().HaveCount(2);

        var first = listings[0];
        first.SourceSite.Should().Be(SourceSite.F591);
        first.SourceListingKey.Should().Be("21003434");
        first.City.Should().Be("新北市");
        first.District.Should().Be("中和區");
        first.Address.Should().Be("華福街42巷");
        first.Title.Should().Be("明德公園公寓2樓");
        first.TotalPrice.Should().Be(1030m);
        first.UnitPrice.Should().Be(55.02m);
        first.AreaPing.Should().Be(18.72m);
        first.Floor.Should().Be("2F/4F");
        first.AgeYears.Should().Be(48);           // houseage 現為數字型欄位
        first.HasParking.Should().BeFalse();
        first.Url.Should().Be("https://sale.591.com.tw/home/house/detail/2/21003434.html");
        first.ImageUrl.Should().Be("https://img1.591.com.tw/house/2026/10/02/179093242510629701.jpg!400x300.jpg");

        listings[1].HasParking.Should().BeTrue();  // has_carport=1
        listings[1].AgeYears.Should().Be(20);
    }

    [Fact]
    public void ParseNuxtSaleList_WhenRecommendWare_ShouldReturnZeroTotalAndNoListings()
    {
        // 條件無符合物件時，591 把「為您推薦」物件塞進 wareList，wareTotal 也是推薦物件數，
        // 唯一可靠的判斷是 isRecommendWare=true
        var json = SearchResultJson.Replace("\"isRecommend\": false", "\"isRecommend\": true");

        var (listings, total) = F591Scraper.ParseNuxtSaleList(json, "新北市");

        total.Should().Be(0);
        listings.Should().BeEmpty();
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("null")]
    [InlineData("{\"total\":null,\"isRecommend\":false,\"list\":null}")]
    public void ParseNuxtSaleList_WhenStoreMissing_ShouldReturnUnknownTotal(string? json)
    {
        var (listings, total) = F591Scraper.ParseNuxtSaleList(json, "新北市");

        total.Should().BeNull();
        listings.Should().BeEmpty();
    }
}
