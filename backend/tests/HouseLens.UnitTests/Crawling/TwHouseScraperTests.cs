using FluentAssertions;
using HouseLens.Domain.Enums;
using HouseLens.Infrastructure.Crawling.Scrapers;
using Microsoft.Extensions.Logging.Abstractions;

namespace HouseLens.UnitTests.Crawling;

public class TwHouseScraperTests
{
    private readonly TwHouseScraper _scraper = new(null!, NullLogger<TwHouseScraper>.Instance);

    // ── 住宅大樓卡片（實際擷取自 twhg.com.tw 中和區列表頁真實 HTML 結構）
    private const string ResidentialCard = """
        <li><div class="h-full"><a href="/buy/A123456789" rel="noopener noreferrer" target="_blank" class="h-full flex flex-col group rounded-2xl">
          <div class="relative h-56 overflow-hidden rounded-2xl">
            <div class="absolute inset-0 bg-cover bg-center" style="background-image:url(&#39;https://img.twhg.com.tw/admin/images/OB01/AAAA/A123456789.jpg&#39;);"></div>
          </div>
          <div class="p-3 gap-y-2 grow flex flex-col">
            <div><h2 class="font-bold">中和三房美廈</h2><p class="text-xs text-grayscale-500">A123456789</p></div>
            <p class="text-sm">新北市中和區中山路二段</p>
            <div class="flex text-sm leading-none flex-wrap gap-y-1">
              <div>建坪 34.56 坪</div><span class="px-1">|</span><div>25.3年</div><span class="px-1">|</span><div>3房2廳2衛</div>
            </div>
            <div class="mt-auto flex justify-end items-baseline"><span class="font-bold text-lg text-red-500">760萬</span></div>
          </div>
        </a></div></li>
        """;

    // ── 超出總價上限（1,200 萬 > 800 萬，應被過濾）
    private const string OverPriceCard = """
        <li>
          <a href="/buy/B987654321">
            <h3>中和豪宅B987654321</h3>
            <p>新北市中和區景平路</p>
            <p>大樓 5房3廳 5.0年</p>
            <p>建坪 68.00 坪</p>
            <p>1,200萬</p>
          </a>
        </li>
        """;

    // ── 非住宅（含「辦公室」關鍵字，應被過濾）
    private const string OfficeCard = """
        <li>
          <a href="/buy/C111222333">
            <h3>中和辦公室C111222333</h3>
            <p>新北市中和區中正路</p>
            <p>辦公室 3間 10.5年</p>
            <p>建坪 45.00 坪</p>
            <p>600萬</p>
          </a>
        </li>
        """;

    // ── 含折扣（原價 1,800萬 + 現售 1,480萬），應取後者
    private const string DiscountedCard = """
        <li>
          <a href="https://www.twhg.com.tw/buy/TF02157997">
            <h3>永和降價美廈TF02157997</h3>
            <p>新北市永和區中山路一段</p>
            <p>大樓 3房2廳 15.2年</p>
            <p>建坪 32.00 坪</p>
            <span>1,800萬</span>
            <span>1,480萬</span>
          </a>
        </li>
        """;

    // ── 含千位逗號的價格（「1,180萬」格式）
    private const string CommaPrice = """
        <li>
          <a href="/buy/D444555666">
            <h3>桃園三房D444555666</h3>
            <p>桃園市桃園區三民路二段</p>
            <p>大樓 3房2廳2衛 32.1年</p>
            <p>建坪 40.55 坪</p>
            <p>1,180萬</p>
          </a>
        </li>
        """;

    // ── 土地物件（實際擷取自 twhg.com.tw，標題含「土地」、以「地坪」取代「建坪」，應被剔除）
    private const string LandParcelCard = """
        <li><div class="h-full"><a href="/buy/TF11613507" rel="noopener noreferrer" target="_blank" class="h-full flex flex-col group rounded-2xl">
          <div class="p-3 gap-y-2 grow flex flex-col">
            <div><h2 class="font-bold">中和橫路段持分土地2</h2><p class="text-xs text-grayscale-500">TF11613507</p></div>
            <p class="text-sm">新北市中和區橫路段</p>
            <div class="flex text-sm leading-none flex-wrap gap-y-1"><div>地坪 22.52 坪</div></div>
            <div class="mt-auto flex justify-end items-baseline"><span class="font-bold text-lg text-red-500">68萬</span></div>
          </div>
        </a></div></li>
        """;

    private static string WrapInPage(params string[] cards) =>
        $"<html><body><ul>{string.Join("", cards)}</ul></body></html>";

    private static readonly IReadOnlyDictionary<string, decimal> DefaultPrices =
        new Dictionary<string, decimal>
        {
            ["中和區"] = 800m,
            ["桃園區"] = 1300m,
        };

    [Fact]
    public void ParseListings_ResidentialCard_ParsedCorrectly()
    {
        var html = WrapInPage(ResidentialCard);

        var results = _scraper.ParseListings(html, "新北市", "中和區", 800m);

        results.Should().HaveCount(1);
        var dto = results[0];
        dto.SourceSite.Should().Be(SourceSite.TwHouse);
        dto.SourceListingKey.Should().Be("A123456789");
        dto.Url.Should().Be("https://www.twhg.com.tw/buy/A123456789");
        dto.City.Should().Be("新北市");
        dto.District.Should().Be("中和區");
        dto.Title.Should().Be("中和三房美廈");
        dto.TotalPrice.Should().Be(760m);
        dto.AreaPing.Should().Be(34.56m);
        dto.AgeYears.Should().Be(25); // Math.Round(25.3) = 25
        dto.UnitPrice.Should().BeApproximately(760m / 34.56m, 0.01m);
        dto.HasParking.Should().BeFalse(); // 列表頁無法確定車位
        dto.Floor.Should().BeNull();       // 列表頁無樓層，由詳情頁補齊
        dto.Address.Should().BeNull();     // 列表頁無完整地址，由詳情頁補齊
        dto.ImageUrl.Should().Be("https://img.twhg.com.tw/admin/images/OB01/AAAA/A123456789.jpg"); // 圖片為 CSS background-image，非 <img src>
    }

    [Fact]
    public void ParseListings_OverPriceCard_IsFiltered()
    {
        var html = WrapInPage(OverPriceCard);

        var results = _scraper.ParseListings(html, "新北市", "中和區", 800m);

        results.Should().BeEmpty();
    }

    [Fact]
    public void ParseListings_LandParcelCard_IsFiltered()
    {
        var html = WrapInPage(LandParcelCard);

        var results = _scraper.ParseListings(html, "新北市", "中和區", 800m);

        results.Should().BeEmpty();
    }

    [Fact]
    public void ParseListings_OfficeCard_IsFiltered()
    {
        var html = WrapInPage(OfficeCard);

        var results = _scraper.ParseListings(html, "新北市", "中和區", 800m);

        results.Should().BeEmpty();
    }

    [Fact]
    public void ParseListings_DiscountedCard_TakesLastPrice()
    {
        var html = WrapInPage(DiscountedCard);

        var results = _scraper.ParseListings(html, "新北市", "永和區", 1500m);

        results.Should().HaveCount(1);
        results[0].TotalPrice.Should().Be(1480m); // 現售價，非原價 1800
        results[0].SourceListingKey.Should().Be("TF02157997");
    }

    [Fact]
    public void ParseListings_CommaPriceCard_ParsedCorrectly()
    {
        var html = WrapInPage(CommaPrice);

        var results = _scraper.ParseListings(html, "桃園市", "桃園區", 1300m);

        results.Should().HaveCount(1);
        results[0].TotalPrice.Should().Be(1180m);
        results[0].AgeYears.Should().Be(32); // Math.Round(32.1)
        results[0].AreaPing.Should().Be(40.55m);
    }

    [Fact]
    public void ParseListings_DuplicateKey_DeduplicatedViaSeen()
    {
        var html = WrapInPage(ResidentialCard, ResidentialCard);
        var seen = new HashSet<string>();

        var results = _scraper.ParseListings(html, "新北市", "中和區", 800m, seen);

        results.Should().HaveCount(1);
        seen.Should().Contain("A123456789");
    }

    [Fact]
    public void ParseListings_MixedCards_OnlyValidResidential()
    {
        var html = WrapInPage(ResidentialCard, OverPriceCard, OfficeCard);

        var results = _scraper.ParseListings(html, "新北市", "中和區", 800m);

        results.Should().HaveCount(1);
        results[0].SourceListingKey.Should().Be("A123456789");
    }

    [Fact]
    public void ParseListings_EmptyHtml_ReturnsEmpty()
    {
        var results = _scraper.ParseListings("<html><body></body></html>", "新北市", "中和區", 800m);

        results.Should().BeEmpty();
    }

    [Fact]
    public void ParseListings_UnitPrice_CalculatedFromAreaWhenPresent()
    {
        var html = WrapInPage(ResidentialCard);

        var results = _scraper.ParseListings(html, "新北市", "中和區", 800m);

        results.Should().HaveCount(1);
        // 760 / 34.56 ≈ 22.00
        results[0].UnitPrice.Should().BeApproximately(760m / 34.56m, 0.01m);
    }

    // ─── BuildSearchUrl 測試 ─────────────────────────────────────────────────────

    [Fact]
    public void BuildSearchUrl_FullCriteria_AllSegmentsInOrder()
    {
        var criteria = new HouseLens.Application.Crawling.DistrictCriteria(
            MaxTotalPrice: 1000m,
            MinSizePing: 20m,
            Rooms: "2,3,5~",
            TypeCodes: "apartment,midrise,condo",
            UseCode: "1",
            MaxAgeYears: 30,
            ParkingCodes: "PF,PM");

        var url = TwHouseScraper.BuildSearchUrl("newTaipei-city", "235", criteria, 1);

        url.Should().Be("https://www.twhg.com.tw/buy/list/newTaipei-city/235-zips/apartment-midrise-condo-kinds/1000down-price/20up-ping/fullBuildingPing-ping_type/2up-bedrooms/30down-house_year/1up-park_count/recomended-desc?page=1");
    }

    [Fact]
    public void BuildSearchUrl_MinimalCriteria_OmitsOptionalSegments()
    {
        var criteria = new HouseLens.Application.Crawling.DistrictCriteria(
            MaxTotalPrice: 800m, TypeCodes: "");

        var url = TwHouseScraper.BuildSearchUrl("taoyuan-city", "320", criteria, 3);

        // 未設定坪數/房數/屋齡/車位 → 對應段省略；型態空 → 回退全部住宅型態
        url.Should().Be("https://www.twhg.com.tw/buy/list/taoyuan-city/320-zips/apartment-midrise-condo-kinds/800down-price/recomended-desc?page=3");
    }

    [Fact]
    public void BuildSearchUrl_RakuyaTypeCodes_MappedToTwhgKinds()
    {
        // R1（公寓）→ apartment；R2（大樓/華廈）→ condo+midrise，輸出依官方順序
        var r1 = new HouseLens.Application.Crawling.DistrictCriteria(500m, TypeCodes: "R1");
        var r2 = new HouseLens.Application.Crawling.DistrictCriteria(500m, TypeCodes: "R2");

        TwHouseScraper.BuildSearchUrl("newTaipei-city", "234", r1, 1)
            .Should().Contain("/apartment-kinds/");
        TwHouseScraper.BuildSearchUrl("newTaipei-city", "234", r2, 1)
            .Should().Contain("/midrise-condo-kinds/");
    }

    [Fact]
    public void BuildSearchUrl_MinRoomsTakenFromRoomsList()
    {
        var criteria = new HouseLens.Application.Crawling.DistrictCriteria(800m, Rooms: "3,4,5~", TypeCodes: "");

        TwHouseScraper.BuildSearchUrl("newTaipei-city", "235", criteria, 1)
            .Should().Contain("/3up-bedrooms/");
    }

    [Fact]
    public void BuildSearchUrl_PingIncludesFullBuildingPingType()
    {
        var criteria = new HouseLens.Application.Crawling.DistrictCriteria(800m, MinSizePing: 25m, TypeCodes: "");

        TwHouseScraper.BuildSearchUrl("newTaipei-city", "235", criteria, 1)
            .Should().Contain("/25up-ping/fullBuildingPing-ping_type/");
    }

    // ─── ParseDetail 測試 ───────────────────────────────────────────────────────

    // 詳情頁 HTML：含車位、5/7樓、完整地址、大樓型態
    // TODO: 替換為瀏覽器 DevTools 複製的真實詳情頁 <dl> 區塊 HTML
    private const string DetailWithParking = """
        <html><body>
          <dl>
            <dt>地址</dt><dd>桃園市桃園區三民路二段123號 查看地圖</dd>
            <dt>樓層</dt><dd>5/7樓</dd>
            <dt>車位</dt><dd>含車位</dd>
            <dt>類型</dt><dd>大樓</dd>
          </dl>
        </body></html>
        """;

    // 詳情頁 HTML：無車位、透天型態
    private const string DetailNoParking = """
        <html><body>
          <dl>
            <dt>地址</dt><dd>新北市中和區中山路二段45號</dd>
            <dt>樓層</dt><dd>3/3樓</dd>
            <dt>車位</dt><dd>無車位</dd>
            <dt>類型</dt><dd>透天厝</dd>
          </dl>
        </body></html>
        """;

    // 詳情頁 HTML：非住宅（辦公室型態）
    private const string DetailOfficeType = """
        <html><body>
          <dl>
            <dt>地址</dt><dd>新北市中和區中正路10號</dd>
            <dt>樓層</dt><dd>6/10樓</dd>
            <dt>車位</dt><dd>無車位</dd>
            <dt>類型</dt><dd>辦公室</dd>
          </dl>
        </body></html>
        """;

    // 詳情頁 HTML：空頁（dt/dd 不存在）
    private const string DetailEmpty = "<html><body><div>找不到物件</div></body></html>";

    [Fact]
    public void ParseDetail_WithParking_ParsedCorrectly()
    {
        var (address, floor, hasParking, propertyType) = _scraper.ParseDetail(DetailWithParking);

        address.Should().Be("桃園市桃園區三民路二段123號");
        floor.Should().Be("5/7樓");
        hasParking.Should().BeTrue();
        propertyType.Should().Be("大樓");
    }

    [Fact]
    public void ParseDetail_NoParking_HasParkingFalse()
    {
        var (_, _, hasParking, propertyType) = _scraper.ParseDetail(DetailNoParking);

        hasParking.Should().BeFalse();
        propertyType.Should().Be("透天厝");
    }

    [Fact]
    public void ParseDetail_AddressStripsMapLink()
    {
        // "桃園市桃園區三民路二段123號 查看地圖" → 應移除「查看地圖」後綴
        var (address, _, _, _) = _scraper.ParseDetail(DetailWithParking);

        address.Should().NotContain("查看地圖");
        address.Should().Be("桃園市桃園區三民路二段123號");
    }

    [Fact]
    public void ParseDetail_OfficeType_ReturnsPropertyType()
    {
        var (_, _, _, propertyType) = _scraper.ParseDetail(DetailOfficeType);

        propertyType.Should().Be("辦公室");
    }

    [Fact]
    public void ParseDetail_EmptyPage_ReturnsNulls()
    {
        var (address, floor, hasParking, propertyType) = _scraper.ParseDetail(DetailEmpty);

        address.Should().BeNull();
        floor.Should().BeNull();
        hasParking.Should().BeFalse();
        propertyType.Should().BeNull();
    }
}
