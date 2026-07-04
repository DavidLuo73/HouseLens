using FluentAssertions;
using HouseLens.Application.Crawling;
using HouseLens.Domain.Enums;
using HouseLens.Infrastructure.Crawling.Scrapers;
using Microsoft.Extensions.Logging.Abstractions;

namespace HouseLens.UnitTests.Crawling;

public class HBHousingScraperTests
{
    // ParseListings 不呼叫 fetcher，傳 null! 即可
    private readonly HBHousingScraper _scraper = new(
        null!,
        NullLogger<HBHousingScraper>.Instance);

    // ── 住宅卡片（公寓，台北市大安區，有降價，應被解析）
    private const string ApartmentCard = """
        <section class="@container" data-v-ca0a70d6="">
          <div class="wrapper" data-v-ca0a70d6="">
            <button class="absolute top-0 right-0 z-10" data-v-ca0a70d6=""></button>
            <div class="relative" data-v-ca0a70d6="">
              <img src="https://img.hbhousing.com.tw/pictures/A271%2fA271YS202291a.jpg?1782581465" alt="測試公寓" />
            </div>
            <div class="relative" data-v-ca0a70d6="">
              <div class="overflow-hidden relative" data-v-ca0a70d6="">
                <div class="@3xl:col-start-1 @3xl:col-end-3" data-v-ca0a70d6="">
                  <h3 class="font-bold" data-v-ca0a70d6="">
                    <a href="/detail?sn=YS202291" rel="noopener noreferrer" target="_blank" data-v-ca0a70d6="">大安優質公寓</a>
                  </h3>
                  <p class="hidden font-montserrat" data-v-ca0a70d6=""> 物件編號 YS202291</p>
                </div>
                <div class="flex justify-between" data-v-ca0a70d6="">
                  <div class="mb-1" data-v-ca0a70d6="">
                    <p class="attribute" data-v-ca0a70d6="">
                      <i class="i-carbon-location-filled" data-v-ca0a70d6=""></i>
                      <span data-v-ca0a70d6="">台北市大安區仁愛路</span>
                      <a class="text-primary" href="/detail?sn=YS202291#life" data-v-ca0a70d6="">看地圖</a>
                    </p>
                    <p class="attribute" data-v-ca0a70d6="">
                      <i class="i-ph-house-fill" data-v-ca0a70d6=""></i>
                      <span data-v-ca0a70d6="">公寓 | 3房(室)2廳1衛 | 30.7年 | 5樓/5樓 | 建坪 | 29.05坪</span>
                      <button class="text-primary underline ml-1.5" data-v-ca0a70d6="">看格局圖</button>
                    </p>
                  </div>
                </div>
                <p class="text-neutral-dark font-montserrat" data-v-ca0a70d6="">
                  <span class="line-through" data-v-ca0a70d6="">880萬</span>
                  <span class="mx-1" data-v-ca0a70d6="">></span>
                  <span class="text-error" data-v-ca0a70d6="">750 萬</span>
                  <span class="@3xl:text-size-10 font-medium" data-v-ca0a70d6="">750</span>
                </p>
                <div class="flex justify-between" data-v-ca0a70d6="">
                  <div class="flex flex-wrap" data-v-ca0a70d6="">
                    <div class="tag" data-v-ca0a70d6="">近學校</div>
                    <div class="tag" data-v-ca0a70d6="">降價物件</div>
                  </div>
                </div>
              </div>
            </div>
            <div class="" data-v-ca0a70d6=""></div>
          </div>
        </section>
        """;

    // ── 住宅大樓卡片（含車位，信義區，無折扣，應被解析）
    private const string BuildingWithParkingCard = """
        <section class="@container" data-v-ca0a70d6="">
          <div class="wrapper" data-v-ca0a70d6="">
            <button class="absolute top-0 right-0 z-10" data-v-ca0a70d6=""></button>
            <div class="relative" data-v-ca0a70d6="">
              <img src="https://img.hbhousing.com.tw/pictures/A050%2fA050ZS203105a.jpg?1782581465" alt="大樓含車位" />
            </div>
            <div class="relative" data-v-ca0a70d6="">
              <div class="overflow-hidden relative" data-v-ca0a70d6="">
                <div class="@3xl:col-start-1 @3xl:col-end-3" data-v-ca0a70d6="">
                  <h3 class="font-bold" data-v-ca0a70d6="">
                    <a href="/detail?sn=ZS203105" rel="noopener noreferrer" target="_blank" data-v-ca0a70d6="">信義電梯大樓含車位</a>
                  </h3>
                </div>
                <div class="flex justify-between" data-v-ca0a70d6="">
                  <div class="mb-1" data-v-ca0a70d6="">
                    <p class="attribute" data-v-ca0a70d6="">
                      <i class="i-carbon-location-filled" data-v-ca0a70d6=""></i>
                      <span data-v-ca0a70d6="">台北市信義區信義路</span>
                      <a class="text-primary" href="/detail?sn=ZS203105#life" data-v-ca0a70d6="">看地圖</a>
                    </p>
                    <p class="attribute" data-v-ca0a70d6="">
                      <i class="i-ph-house-fill" data-v-ca0a70d6=""></i>
                      <span data-v-ca0a70d6="">大樓 | 3房(室)2廳2衛 | 8.0年 | 10樓/15樓 | 建坪 | 35.00坪</span>
                      <button class="text-primary underline ml-1.5" data-v-ca0a70d6="">看格局圖</button>
                    </p>
                  </div>
                </div>
                <p class="text-neutral-dark font-montserrat" data-v-ca0a70d6="">
                  <span class="text-error" data-v-ca0a70d6="">1,280 萬</span>
                  <span class="@3xl:text-size-10 font-medium" data-v-ca0a70d6="">1280</span>
                </p>
                <div class="flex justify-between" data-v-ca0a70d6="">
                  <div class="flex flex-wrap" data-v-ca0a70d6="">
                    <div class="tag" data-v-ca0a70d6="">有車位</div>
                    <div class="tag" data-v-ca0a70d6="">近捷運</div>
                  </div>
                </div>
              </div>
            </div>
            <div class="" data-v-ca0a70d6=""></div>
          </div>
        </section>
        """;

    // ── 非住宅卡片（辦公室，應被過濾）
    private const string OfficeCard = """
        <section class="@container" data-v-ca0a70d6="">
          <div class="wrapper" data-v-ca0a70d6="">
            <button class="absolute top-0 right-0 z-10" data-v-ca0a70d6=""></button>
            <div class="relative" data-v-ca0a70d6=""></div>
            <div class="relative" data-v-ca0a70d6="">
              <div class="overflow-hidden relative" data-v-ca0a70d6="">
                <div class="@3xl:col-start-1 @3xl:col-end-3" data-v-ca0a70d6="">
                  <h3 class="font-bold" data-v-ca0a70d6="">
                    <a href="/detail?sn=OS999999" rel="noopener noreferrer" target="_blank" data-v-ca0a70d6="">中正商業辦公室</a>
                  </h3>
                </div>
                <div class="flex justify-between" data-v-ca0a70d6="">
                  <div class="mb-1" data-v-ca0a70d6="">
                    <p class="attribute" data-v-ca0a70d6="">
                      <i class="i-carbon-location-filled" data-v-ca0a70d6=""></i>
                      <span data-v-ca0a70d6="">台北市中正區忠孝東路</span>
                      <a class="text-primary" href="/detail?sn=OS999999#life" data-v-ca0a70d6="">看地圖</a>
                    </p>
                    <p class="attribute" data-v-ca0a70d6="">
                      <i class="i-ph-house-fill" data-v-ca0a70d6=""></i>
                      <span data-v-ca0a70d6="">辦公室 | -- | 15.0年 | 6樓/19樓 | 建坪 | 28.15坪</span>
                      <button class="text-primary underline ml-1.5" data-v-ca0a70d6="">看格局圖</button>
                    </p>
                  </div>
                </div>
                <p class="text-neutral-dark font-montserrat" data-v-ca0a70d6="">
                  <span class="text-error" data-v-ca0a70d6="">2,388 萬</span>
                  <span class="@3xl:text-size-10 font-medium" data-v-ca0a70d6="">2388</span>
                </p>
              </div>
            </div>
            <div class="" data-v-ca0a70d6=""></div>
          </div>
        </section>
        """;

    // ── 超出總價上限的卡片（華廈，應被過濾）
    private const string OverPriceCard = """
        <section class="@container" data-v-ca0a70d6="">
          <div class="wrapper" data-v-ca0a70d6="">
            <button class="absolute top-0 right-0 z-10" data-v-ca0a70d6=""></button>
            <div class="relative" data-v-ca0a70d6=""></div>
            <div class="relative" data-v-ca0a70d6="">
              <div class="overflow-hidden relative" data-v-ca0a70d6="">
                <div class="@3xl:col-start-1 @3xl:col-end-3" data-v-ca0a70d6="">
                  <h3 class="font-bold" data-v-ca0a70d6="">
                    <a href="/detail?sn=PS111111" rel="noopener noreferrer" target="_blank" data-v-ca0a70d6="">大安豪宅</a>
                  </h3>
                </div>
                <div class="flex justify-between" data-v-ca0a70d6="">
                  <div class="mb-1" data-v-ca0a70d6="">
                    <p class="attribute" data-v-ca0a70d6="">
                      <i class="i-carbon-location-filled" data-v-ca0a70d6=""></i>
                      <span data-v-ca0a70d6="">台北市大安區豪宅路</span>
                      <a class="text-primary" href="/detail?sn=PS111111#life" data-v-ca0a70d6="">看地圖</a>
                    </p>
                    <p class="attribute" data-v-ca0a70d6="">
                      <i class="i-ph-house-fill" data-v-ca0a70d6=""></i>
                      <span data-v-ca0a70d6="">華廈 | 5房(室)3廳3衛 | 5.0年 | 18樓/20樓 | 建坪 | 60.00坪</span>
                      <button class="text-primary underline ml-1.5" data-v-ca0a70d6="">看格局圖</button>
                    </p>
                  </div>
                </div>
                <p class="text-neutral-dark font-montserrat" data-v-ca0a70d6="">
                  <span class="text-error" data-v-ca0a70d6="">3,500 萬</span>
                  <span class="@3xl:text-size-10 font-medium" data-v-ca0a70d6="">3500</span>
                </p>
              </div>
            </div>
            <div class="" data-v-ca0a70d6=""></div>
          </div>
        </section>
        """;

    private static string WrapInPage(params string[] cards) =>
        $"<html><body>{string.Join("", cards)}</body></html>";

    [Fact]
    public void ParseListings_ResidentialCard_ParsedCorrectly()
    {
        var html = WrapInPage(ApartmentCard);

        var results = _scraper.ParseListings(html, "台北市", "大安區", 800m);

        results.Should().HaveCount(1);
        var dto = results[0];
        dto.SourceSite.Should().Be(SourceSite.HBHousing);
        dto.SourceListingKey.Should().Be("YS202291");
        dto.Url.Should().Be("https://www.hbhousing.com.tw/detail?sn=YS202291");
        dto.City.Should().Be("台北市");
        dto.District.Should().Be("大安區");
        dto.Title.Should().Be("大安優質公寓");
        dto.Address.Should().Be("台北市大安區仁愛路");
        dto.TotalPrice.Should().Be(750m);
        dto.AreaPing.Should().Be(29.05m);
        dto.Floor.Should().Be("5樓/5樓");
        dto.AgeYears.Should().Be(31);  // Math.Round(30.7) = 31
        dto.HasParking.Should().BeFalse();
        dto.ImageUrl.Should().Be("https://img.hbhousing.com.tw/pictures/A271%2fA271YS202291a.jpg");
        dto.UnitPrice.Should().BeApproximately(750m / 29.05m, 0.01m);
    }

    [Fact]
    public void ParseListings_BuildingWithParking_ParsedCorrectly()
    {
        var html = WrapInPage(BuildingWithParkingCard);

        var results = _scraper.ParseListings(html, "台北市", "信義區", 1500m);

        results.Should().HaveCount(1);
        var dto = results[0];
        dto.SourceListingKey.Should().Be("ZS203105");
        dto.City.Should().Be("台北市");
        dto.District.Should().Be("信義區");
        dto.TotalPrice.Should().Be(1280m);
        dto.HasParking.Should().BeTrue();
        dto.AgeYears.Should().Be(8);
        dto.Floor.Should().Be("10樓/15樓");
        dto.AreaPing.Should().Be(35.00m);
    }

    [Fact]
    public void ParseListings_NonResidentialCard_IsFiltered()
    {
        var html = WrapInPage(OfficeCard);

        var results = _scraper.ParseListings(html, "台北市", "中正區", 3000m);

        results.Should().BeEmpty();
    }

    [Fact]
    public void ParseListings_OverPriceCard_IsFiltered()
    {
        var html = WrapInPage(OverPriceCard);

        var results = _scraper.ParseListings(html, "台北市", "大安區", 800m);

        results.Should().BeEmpty();
    }

    // ── 地址行政區與查詢行政區不同（住商未提供行政區層級 URL 前的推薦清單殘留情境），
    // 應以地址解析出的真實行政區為準，而非查詢參數
    private const string NorthDistrictCard = """
        <section class="@container" data-v-ca0a70d6="">
          <div class="wrapper" data-v-ca0a70d6="">
            <button class="absolute top-0 right-0 z-10" data-v-ca0a70d6=""></button>
            <div class="relative" data-v-ca0a70d6=""></div>
            <div class="relative" data-v-ca0a70d6="">
              <div class="overflow-hidden relative" data-v-ca0a70d6="">
                <div class="@3xl:col-start-1 @3xl:col-end-3" data-v-ca0a70d6="">
                  <h3 class="font-bold" data-v-ca0a70d6="">
                    <a href="/detail?sn=NP000001" rel="noopener noreferrer" target="_blank" data-v-ca0a70d6="">北投溫泉公寓</a>
                  </h3>
                </div>
                <div class="flex justify-between" data-v-ca0a70d6="">
                  <div class="mb-1" data-v-ca0a70d6="">
                    <p class="attribute" data-v-ca0a70d6="">
                      <i class="i-carbon-location-filled" data-v-ca0a70d6=""></i>
                      <span data-v-ca0a70d6="">台北市北投區新民路</span>
                      <a class="text-primary" href="/detail?sn=NP000001#life" data-v-ca0a70d6="">看地圖</a>
                    </p>
                    <p class="attribute" data-v-ca0a70d6="">
                      <i class="i-ph-house-fill" data-v-ca0a70d6=""></i>
                      <span data-v-ca0a70d6="">公寓 | 2房(室)1廳1衛 | 45.7年 | 5樓/5樓 | 建坪 | 20.00坪</span>
                      <button class="text-primary underline ml-1.5" data-v-ca0a70d6="">看格局圖</button>
                    </p>
                  </div>
                </div>
                <p class="text-neutral-dark font-montserrat" data-v-ca0a70d6="">
                  <span class="text-error" data-v-ca0a70d6="">500 萬</span>
                  <span class="@3xl:text-size-10 font-medium" data-v-ca0a70d6="">500</span>
                </p>
              </div>
            </div>
            <div class="" data-v-ca0a70d6=""></div>
          </div>
        </section>
        """;

    [Fact]
    public void ParseListings_AddressDistrictDiffersFromQuery_UsesParsedAddress()
    {
        // 查詢參數為大安區，但卡片實際地址是北投區 → 應以解析出的地址為準
        var html = WrapInPage(NorthDistrictCard);

        var results = _scraper.ParseListings(html, "台北市", "大安區", 800m);

        results.Should().HaveCount(1);
        results[0].District.Should().Be("北投區");
    }

    [Fact]
    public void ParseListings_MixedCards_OnlyResidentialUnderMaxPrice()
    {
        var html = WrapInPage(ApartmentCard, OfficeCard, OverPriceCard, BuildingWithParkingCard);

        var results = _scraper.ParseListings(html, "台北市", "大安區", 800m);

        results.Should().HaveCount(1);
        results.Select(r => r.SourceListingKey).Should().BeEquivalentTo(["YS202291"]);
    }

    [Fact]
    public void ParseListings_DuplicateKey_DeduplicatedViaSeen()
    {
        var html = WrapInPage(ApartmentCard, ApartmentCard);
        var seen = new HashSet<string>();

        var results = _scraper.ParseListings(html, "台北市", "大安區", 800m, seen);

        results.Should().HaveCount(1);
        seen.Should().Contain("YS202291");
    }

    [Fact]
    public void ParseListings_PriceWithCommas_ParsedCorrectly()
    {
        // BuildingWithParkingCard 的價格是 "1,280 萬"
        var html = WrapInPage(BuildingWithParkingCard);

        var results = _scraper.ParseListings(html, "台北市", "信義區", 1500m);

        results.Should().HaveCount(1);
        results[0].TotalPrice.Should().Be(1280m);
    }

    [Fact]
    public void ParseListings_EmptyHtml_ReturnsEmpty()
    {
        var results = _scraper.ParseListings("<html><body></body></html>", "台北市", "大安區", 800m);

        results.Should().BeEmpty();
    }

    // ── BuildSearchUrl：URL 段組合 ──────────────────────────────

    [Fact]
    public void BuildSearchUrl_AllCriteria_ProducesAllSegmentsInOrder()
    {
        var criteria = new DistrictCriteria(
            MaxTotalPrice: 1000m,
            MinSizePing: 20m,
            Rooms: "2,3,5~",
            TypeCodes: "noelevator,elevator,mansion",
            UseCode: "1",
            MaxAgeYears: 30,
            ParkingCodes: "PF,PM");

        var url = HBHousingScraper.BuildSearchUrl("新北市", "235", criteria, 1);

        url.Should().Be(
            "https://www.hbhousing.com.tw/buyhouse/" +
            Uri.EscapeDataString("新北市") +
            "/235/noelevator-elevator-mansion-style/1000-down-price/apartment-type" +
            "/area-20-up-area/30-down-age/2_up_room-pattern/parking-tag");
    }

    [Fact]
    public void BuildSearchUrl_Page2_AppendsPageSegment()
    {
        var criteria = new DistrictCriteria(1000m);

        var url = HBHousingScraper.BuildSearchUrl("桃園市", "320", criteria, 2);

        url.Should().EndWith("/2-page");
        url.Should().Contain("/320/");
    }

    [Fact]
    public void BuildSearchUrl_MinimalCriteria_OmitsOptionalSegments()
    {
        // 無坪數/屋齡/房數/車位 → 對應段全部省略；型態未設定 → 全部三種
        var criteria = new DistrictCriteria(800m, TypeCodes: "", UseCode: "1");

        var url = HBHousingScraper.BuildSearchUrl("新北市", "234", criteria, 1);

        url.Should().Contain("/noelevator-elevator-mansion-style/");
        url.Should().Contain("/800-down-price/apartment-type");
        url.Should().NotContain("-up-area").And.NotContain("-down-age");
        url.Should().NotContain("room-pattern").And.NotContain("parking-tag").And.NotContain("-page");
    }

    [Fact]
    public void BuildSearchUrl_RakuyaTypeCodes_MappedToHBCodes()
    {
        // R1 → noelevator；R2 → elevator+mansion（回退對映）
        var r1 = HBHousingScraper.BuildSearchUrl("新北市", "235", new DistrictCriteria(1000m, TypeCodes: "R1"), 1);
        var r2 = HBHousingScraper.BuildSearchUrl("新北市", "235", new DistrictCriteria(1000m, TypeCodes: "R2"), 1);

        r1.Should().Contain("/noelevator-style/");
        r2.Should().Contain("/elevator-mansion-style/");
    }

    [Fact]
    public void BuildAllowedCardTypes_TypeCodes_MapToCardTypeWhitelist()
    {
        HBHousingScraper.BuildAllowedCardTypes("noelevator")
            .Should().BeEquivalentTo(["公寓"]);
        HBHousingScraper.BuildAllowedCardTypes("elevator,mansion")
            .Should().BeEquivalentTo(["大樓", "華廈"]);
        // 未設定 → 預設住宅白名單（含透天等）
        HBHousingScraper.BuildAllowedCardTypes("").Should().Contain(["公寓", "大樓", "華廈", "透天厝"]);
    }

    [Fact]
    public void ParseListings_AllowedTypes_FiltersCardsOutsideWhitelist()
    {
        // 只允許公寓 → 大樓卡片（ZS203105）應被過濾
        var html = WrapInPage(ApartmentCard, BuildingWithParkingCard);
        var allowed = HBHousingScraper.BuildAllowedCardTypes("noelevator");

        var results = _scraper.ParseListings(html, "台北市", "大安區", 1500m, null, allowed);

        results.Select(r => r.SourceListingKey).Should().BeEquivalentTo(["YS202291"]);
    }

    [Fact]
    public void ParseListings_OutRawCardCount_CountsAllCardsBeforeFiltering()
    {
        // 4 張卡片：辦公室與超價會被過濾，但 rawCardCount 仍應為 4（分頁判斷用）
        var html = WrapInPage(ApartmentCard, OfficeCard, OverPriceCard, BuildingWithParkingCard);

        var results = _scraper.ParseListings(html, "台北市", "大安區", 800m, null, null, out var rawCount);

        rawCount.Should().Be(4);
        results.Should().HaveCount(1);
    }

    [Fact]
    public void ParseListings_ImageUrlStripTimestamp_NoQueryString()
    {
        var html = WrapInPage(ApartmentCard);

        var results = _scraper.ParseListings(html, "台北市", "大安區", 800m);

        results[0].ImageUrl.Should().NotContain("?");
        results[0].ImageUrl.Should().StartWith("https://img.hbhousing.com.tw/");
    }
}
