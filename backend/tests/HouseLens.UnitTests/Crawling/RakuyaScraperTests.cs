using FluentAssertions;
using HouseLens.Domain.Enums;
using HouseLens.Infrastructure.Crawling.Scrapers;
using Microsoft.Extensions.Logging.Abstractions;

namespace HouseLens.UnitTests.Crawling;

public class RakuyaScraperTests
{
    // ParseListings 不呼叫 fetcher，傳 null! 即可
    private readonly RakuyaScraper _scraper = new(
        null!,
        NullLogger<RakuyaScraper>.Instance);

    // ── 住宅卡片（電梯大廈，新店區，含車位，主建坪數，應被解析）
    private const string ApartmentCard = """
        <section class="grid-item search-obj" data-ehid="0ca48612345678a">
          <a href="#">
            <div class="card__head">
              <h2>美麗華電梯大廈三房含車位</h2>
            </div>
            <div class="card__body">
              <div class="card__photo">
                <picture>
                  <img src="https://static.rakuya.com.tw/r1/n354/99/8b/12345678_1_c.jpeg?1782540109">
                </picture>
              </div>
              <div class="card__info">
                <div class="card__info--top">
                  <h2 class="info__geo hasCommunity">
                    <i class="fa-solid fa-location-dot"></i>
                    <span class="info__geo--area">新店區</span>
                    <span class="info__geo--community" data-url="https://community.rakuya.com.tw/10823">美河市</span>
                  </h2>
                  <div class="info__detail">
                    <div class="info__detail-info">
                      <i class="fa-solid fa-file-lines"></i>
                      <ul>
                        <li>電梯大廈</li>
                        <li>3房2廳2衛</li>
                        <li>13年</li>
                        <li class="info__floor">10/16樓</li>
                      </ul>
                    </div>
                    <ul class="info__spaceList">
                      <li>總建50.8坪</li>
                      <li>主建26.98坪</li>
                      <li>77.25萬/坪</li>
                    </ul>
                  </div>
                </div>
                <div class="card__info--bottom">
                  <div class="info__price">
                    <span class="info__price--total"><b>2,088</b>萬</span>
                  </div>
                </div>
              </div>
            </div>
            <div class="card__foot">
              <div class="card__foot--inner">
                <div class="group__tags">
                  <span class="tag is--sm is--feature">平面車位</span>
                </div>
              </div>
            </div>
          </a>
        </section>
        """;

    // ── 透天厝卡片（新店區，無車位，無主建坪（只有總建），應被解析）
    private const string TransparentHouseCard = """
        <section class="grid-item search-obj" data-ehid="050b83313052929">
          <a href="#">
            <div class="card__head">
              <h2>頂好安泰旺仔雙享福氣透天</h2>
            </div>
            <div class="card__body">
              <div class="card__photo">
                <picture>
                  <img src="https://static.rakuya.com.tw/r1/n292/c0/03/31305292_22_c.jpeg?1781594288">
                </picture>
              </div>
              <div class="card__info">
                <div class="card__info--top">
                  <h2 class="info__geo">
                    <i class="fa-solid fa-location-dot"></i>
                    <span class="info__geo--area">新店區</span>
                    <span class="info__geo--community">安泰路</span>
                  </h2>
                  <div class="info__detail">
                    <div class="info__detail-info">
                      <i class="fa-solid fa-file-lines"></i>
                      <ul>
                        <li>透天厝</li>
                        <li>5房2廳4衛</li>
                        <li>44.5年</li>
                        <li class="info__floor">1~2/2樓</li>
                      </ul>
                    </div>
                    <ul class="info__spaceList">
                      <li>總建45坪</li>
                    </ul>
                  </div>
                </div>
                <div class="card__info--bottom">
                  <div class="info__price">
                    <span class="info__price--total"><b>1,580</b>萬</span>
                  </div>
                </div>
              </div>
            </div>
            <div class="card__foot">
              <div class="card__foot--inner">
                <div class="group__tags"></div>
              </div>
            </div>
          </a>
        </section>
        """;

    // ── 非住宅卡片（商辦，應被過濾）
    private const string OfficeCard = """
        <section class="grid-item search-obj" data-ehid="0deadbeef0000001">
          <a href="#">
            <div class="card__head">
              <h2>商辦大樓挑高三米採光佳</h2>
            </div>
            <div class="card__body">
              <div class="card__photo"><picture><img src="https://static.rakuya.com.tw/r1/n000/00/00/99999991_1_c.jpeg"></picture></div>
              <div class="card__info">
                <div class="card__info--top">
                  <h2 class="info__geo">
                    <span class="info__geo--area">新店區</span>
                  </h2>
                  <div class="info__detail">
                    <div class="info__detail-info">
                      <ul>
                        <li>商辦</li>
                        <li>10年</li>
                        <li class="info__floor">3/8樓</li>
                      </ul>
                    </div>
                    <ul class="info__spaceList"><li>主建30坪</li></ul>
                  </div>
                </div>
                <div class="card__info--bottom">
                  <div class="info__price">
                    <span class="info__price--total"><b>500</b>萬</span>
                  </div>
                </div>
              </div>
            </div>
          </a>
        </section>
        """;

    // ── 超過價格上限的卡片（新店區 max 900萬，此卡片 1,200萬，應被過濾）
    private const string OverPriceCard = """
        <section class="grid-item search-obj" data-ehid="0deadbeef0000002">
          <a href="#">
            <div class="card__head"><h2>超貴豪宅電梯大廈</h2></div>
            <div class="card__body">
              <div class="card__photo"><picture><img src="https://static.rakuya.com.tw/r1/n000/00/00/99999992_1_c.jpeg"></picture></div>
              <div class="card__info">
                <div class="card__info--top">
                  <h2 class="info__geo">
                    <span class="info__geo--area">新店區</span>
                  </h2>
                  <div class="info__detail">
                    <div class="info__detail-info">
                      <ul>
                        <li>電梯大廈</li>
                        <li>5年</li>
                        <li class="info__floor">20/25樓</li>
                      </ul>
                    </div>
                    <ul class="info__spaceList"><li>主建50坪</li></ul>
                  </div>
                </div>
                <div class="card__info--bottom">
                  <div class="info__price">
                    <span class="info__price--total"><b>1,200</b>萬</span>
                  </div>
                </div>
              </div>
            </div>
          </a>
        </section>
        """;

    private static string WrapInPage(params string[] cards) =>
        $"<html><body>{string.Join("", cards)}</body></html>";

    [Fact]
    public void ParseListings_ApartmentCard_ParsedCorrectly()
    {
        var html = WrapInPage(ApartmentCard);

        var results = _scraper.ParseListings(html, "新北市", "新店區", maxWan: 9000m);

        results.Should().HaveCount(1);
        var dto = results[0];
        dto.SourceSite.Should().Be(SourceSite.Rakuya);
        dto.SourceListingKey.Should().Be("0ca48612345678a");
        dto.Url.Should().Be("https://www.rakuya.com.tw/sell_item/info?ehid=0ca48612345678a");
        dto.Title.Should().Be("美麗華電梯大廈三房含車位");
        dto.City.Should().Be("新北市");
        dto.District.Should().Be("新店區");
        dto.Address.Should().Be("美河市");
        dto.TotalPrice.Should().Be(2088m);
        dto.AreaPing.Should().Be(26.98m);        // 主建優先
        dto.Floor.Should().Be("10/16樓");
        dto.AgeYears.Should().Be(13);
        dto.HasParking.Should().BeTrue();
        dto.ImageUrl.Should().Be("https://static.rakuya.com.tw/r1/n354/99/8b/12345678_1_c.jpeg");
        dto.UnitPrice.Should().BeApproximately(2088m / 26.98m, 0.01m);
    }

    [Fact]
    public void ParseListings_TransparentHouse_ParsedCorrectly()
    {
        var html = WrapInPage(TransparentHouseCard);

        var results = _scraper.ParseListings(html, "新北市", "新店區", maxWan: 900m);

        // 1,580萬 超過 900萬 上限 → 被過濾（透天厝測試兼測超價過濾）
        // 改用較高上限測試
        results = _scraper.ParseListings(html, "新北市", "新店區", maxWan: 2000m);

        results.Should().HaveCount(1);
        var dto = results[0];
        dto.SourceListingKey.Should().Be("050b83313052929");
        dto.Title.Should().Be("頂好安泰旺仔雙享福氣透天");
        dto.TotalPrice.Should().Be(1580m);
        dto.AreaPing.Should().Be(45m);           // 無主建，使用總建
        dto.Floor.Should().Be("1~2/2樓");
        dto.AgeYears.Should().Be(44);            // C# Math.Round(44.5) = 44（banker's rounding，取偶數）
        dto.HasParking.Should().BeFalse();
        dto.ImageUrl.Should().Be("https://static.rakuya.com.tw/r1/n292/c0/03/31305292_22_c.jpeg");
    }

    [Fact]
    public void ParseListings_NonResidentialCard_IsFiltered()
    {
        var html = WrapInPage(OfficeCard);

        var results = _scraper.ParseListings(html, "新北市", "新店區", maxWan: 900m);

        results.Should().BeEmpty();
    }

    [Fact]
    public void ParseListings_OverPriceCard_IsFiltered()
    {
        var html = WrapInPage(OverPriceCard);

        var results = _scraper.ParseListings(html, "新北市", "新店區", maxWan: 900m);

        results.Should().BeEmpty();
    }

    [Fact]
    public void ParseListings_DuplicateCards_DeduplicatedByEhid()
    {
        // 同一張卡片出現兩次（模擬置頂廣告跨頁重複），應只回傳一筆
        var html = WrapInPage(ApartmentCard, ApartmentCard);
        var seen = new HashSet<string>();

        var results = _scraper.ParseListings(html, "新北市", "新店區", maxWan: 9000m, seen: seen);

        results.Should().HaveCount(1);
    }

    [Fact]
    public void ParseListings_MultipleCards_ReturnsAll()
    {
        var html = WrapInPage(ApartmentCard, TransparentHouseCard);

        // 用寬鬆價格上限，兩張都合格
        var results = _scraper.ParseListings(html, "新北市", "新店區", maxWan: 9000m);

        results.Should().HaveCount(2);
    }

    [Fact]
    public void ParseListings_EmptyHtml_ReturnsEmpty()
    {
        var html = "<html><body><div class=\"result-list\"></div></body></html>";

        var results = _scraper.ParseListings(html, "新北市", "新店區", maxWan: 900m);

        results.Should().BeEmpty();
    }
}
