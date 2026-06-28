using FluentAssertions;
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

    private static readonly IReadOnlyDictionary<string, decimal> DefaultPrices = new Dictionary<string, decimal>
    {
        ["大安區"] = 800m,
        ["信義區"] = 1500m,
        ["中正區"] = 3000m,
    };

    private static string WrapInPage(params string[] cards) =>
        $"<html><body>{string.Join("", cards)}</body></html>";

    [Fact]
    public void ParseListings_ResidentialCard_ParsedCorrectly()
    {
        var html = WrapInPage(ApartmentCard);

        var results = _scraper.ParseListings(html, DefaultPrices);

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

        var results = _scraper.ParseListings(html, DefaultPrices);

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

        var results = _scraper.ParseListings(html, DefaultPrices);

        results.Should().BeEmpty();
    }

    [Fact]
    public void ParseListings_OverPriceCard_IsFiltered()
    {
        var prices = new Dictionary<string, decimal> { ["大安區"] = 800m };
        var html = WrapInPage(OverPriceCard);

        var results = _scraper.ParseListings(html, prices);

        results.Should().BeEmpty();
    }

    // ── 不在設定行政區的卡片（北投區未設定，應被過濾）
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
    public void ParseListings_DistrictNotConfigured_IsFiltered()
    {
        // 北投區不在 DefaultPrices，應被過濾
        var html = WrapInPage(NorthDistrictCard);

        var results = _scraper.ParseListings(html, DefaultPrices);

        results.Should().BeEmpty();
    }

    [Fact]
    public void ParseListings_MixedCards_OnlyResidentialUnderMaxPrice()
    {
        var html = WrapInPage(ApartmentCard, OfficeCard, OverPriceCard, BuildingWithParkingCard);

        var results = _scraper.ParseListings(html, DefaultPrices);

        results.Should().HaveCount(2);
        results.Select(r => r.SourceListingKey).Should().BeEquivalentTo(["YS202291", "ZS203105"]);
    }

    [Fact]
    public void ParseListings_DuplicateKey_DeduplicatedViaSeen()
    {
        var html = WrapInPage(ApartmentCard, ApartmentCard);
        var seen = new HashSet<string>();

        var results = _scraper.ParseListings(html, DefaultPrices, seen);

        results.Should().HaveCount(1);
        seen.Should().Contain("YS202291");
    }

    [Fact]
    public void ParseListings_PriceWithCommas_ParsedCorrectly()
    {
        // BuildingWithParkingCard 的價格是 "1,280 萬"
        var html = WrapInPage(BuildingWithParkingCard);

        var results = _scraper.ParseListings(html, DefaultPrices);

        results.Should().HaveCount(1);
        results[0].TotalPrice.Should().Be(1280m);
    }

    [Fact]
    public void ParseListings_EmptyHtml_ReturnsEmpty()
    {
        var results = _scraper.ParseListings("<html><body></body></html>", DefaultPrices);

        results.Should().BeEmpty();
    }

    [Fact]
    public void ParseListings_ImageUrlStripTimestamp_NoQueryString()
    {
        var html = WrapInPage(ApartmentCard);

        var results = _scraper.ParseListings(html, DefaultPrices);

        results[0].ImageUrl.Should().NotContain("?");
        results[0].ImageUrl.Should().StartWith("https://img.hbhousing.com.tw/");
    }
}
