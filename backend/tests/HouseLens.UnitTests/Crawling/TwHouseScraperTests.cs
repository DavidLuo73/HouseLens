using FluentAssertions;
using HouseLens.Domain.Enums;
using HouseLens.Infrastructure.Crawling.Scrapers;
using Microsoft.Extensions.Logging.Abstractions;

namespace HouseLens.UnitTests.Crawling;

public class TwHouseScraperTests
{
    private readonly TwHouseScraper _scraper = new(null!, NullLogger<TwHouseScraper>.Instance);

    // ── 住宅大樓卡片（新北市中和區，760 萬，屋齡 25.3 年 → 25）
    // TODO: 替換為瀏覽器 DevTools 複製的真實 <li> outerHTML
    private const string ResidentialCard = """
        <li>
          <a href="/buy/A123456789">
            <img src="https://www.twhg.com.tw/photo/A123456789_1.jpg" alt="中和三房美廈A123456789">
            <h3>中和三房美廈A123456789</h3>
            <p>新北市中和區中山路二段</p>
            <p>大樓 3房2廳2衛 25.3年</p>
            <p>建坪 34.56 坪</p>
            <p>760萬</p>
          </a>
        </li>
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
        dto.Title.Should().NotContain("A123456789"); // 代碼後綴已被移除
        dto.TotalPrice.Should().Be(760m);
        dto.AreaPing.Should().Be(34.56m);
        dto.AgeYears.Should().Be(25); // Math.Round(25.3) = 25
        dto.UnitPrice.Should().BeApproximately(760m / 34.56m, 0.01m);
        dto.HasParking.Should().BeFalse(); // 列表頁無法確定車位
        dto.Floor.Should().BeNull();       // 列表頁無樓層，由詳情頁補齊
        dto.Address.Should().BeNull();     // 列表頁無完整地址，由詳情頁補齊
    }

    [Fact]
    public void ParseListings_OverPriceCard_IsFiltered()
    {
        var html = WrapInPage(OverPriceCard);

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
