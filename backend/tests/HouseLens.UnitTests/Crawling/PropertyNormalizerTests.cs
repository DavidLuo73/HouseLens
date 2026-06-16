using HouseLens.Application.Crawling;
using HouseLens.Domain.Enums;
using FluentAssertions;

namespace HouseLens.UnitTests.Crawling;

public class PropertyNormalizerTests
{
    private static readonly Dictionary<string, decimal> TrackedDistricts = new()
    {
        ["中和區"] = 800m,
        ["永和區"] = 800m,
        ["板橋區"] = 800m,
    };
    private const decimal MaxPrice = 800m;

    private static PropertyDto MakeDto(string district = "中和區", decimal price = 700m,
        string? address = "中和路1號", string? title = "標題") =>
        new("新北市", district, address, 28m, "5/12", 10, false,
            price, 25m, SourceSite.F591, "k1", title!, "https://example.com", null);

    // --- MeetsTrackingCriteria ---

    [Fact]
    public void MeetsTrackingCriteria_ValidDistrictAndPrice_ReturnsTrue()
    {
        var dto = MakeDto();
        PropertyNormalizer.MeetsTrackingCriteria(dto, TrackedDistricts).Should().BeTrue();
    }

    [Fact]
    public void MeetsTrackingCriteria_PriceTooHigh_ReturnsFalse()
    {
        var dto = MakeDto(price: 801m);
        PropertyNormalizer.MeetsTrackingCriteria(dto, TrackedDistricts).Should().BeFalse();
    }

    [Fact]
    public void MeetsTrackingCriteria_DistrictNotTracked_ReturnsFalse()
    {
        var dto = MakeDto(district: "新莊區");
        PropertyNormalizer.MeetsTrackingCriteria(dto, TrackedDistricts).Should().BeFalse();
    }

    [Fact]
    public void MeetsTrackingCriteria_PriceExactlyAtMax_ReturnsTrue()
    {
        var dto = MakeDto(price: 800m);
        PropertyNormalizer.MeetsTrackingCriteria(dto, TrackedDistricts).Should().BeTrue();
    }

    // --- Normalize ---

    [Fact]
    public void Normalize_NullTitle_ReplacedWithDefault()
    {
        var dto = MakeDto(title: null);
        // PropertyDto requires non-null Title by record definition; test empty string variant
        var dtoEmpty = dto with { Title = "" };
        var result = PropertyNormalizer.Normalize(dtoEmpty);
        result.Title.Should().Be("未提供");
    }

    [Fact]
    public void Normalize_WhitespaceTitle_ReplacedWithDefault()
    {
        var dto = MakeDto() with { Title = "   " };
        var result = PropertyNormalizer.Normalize(dto);
        result.Title.Should().Be("未提供");
    }

    [Fact]
    public void Normalize_TaipeiCharVariant_Normalized()
    {
        var dto = MakeDto(address: "台北路100號");
        var result = PropertyNormalizer.Normalize(dto);
        result.Address.Should().Be("臺北路100號");
    }

    [Fact]
    public void Normalize_NullAddress_RemainsNull()
    {
        var dto = MakeDto(address: null);
        var result = PropertyNormalizer.Normalize(dto);
        result.Address.Should().BeNull();
    }

    [Fact]
    public void Normalize_WhitespaceAddress_BecomesNull()
    {
        var dto = MakeDto(address: "   ");
        var result = PropertyNormalizer.Normalize(dto);
        result.Address.Should().BeNull();
    }
}
