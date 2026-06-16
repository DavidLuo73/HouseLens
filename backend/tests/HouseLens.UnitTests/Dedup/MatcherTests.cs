using HouseLens.Application.Crawling;
using HouseLens.Application.Dedup;
using HouseLens.Domain.Enums;
using FluentAssertions;

namespace HouseLens.UnitTests.Dedup;

public class MatcherTests
{
    private static PropertyDto MakeDto(
        string address, decimal area, string floor, int? age,
        SourceSite site = SourceSite.F591, string key = "k1") =>
        new("新北市", "中和區", address, area, floor, age, false,
            700m, 25m, site, key, "Title", "https://example.com", null);

    [Fact]
    public void IsDuplicate_MatchingProperties_ReturnsTrue()
    {
        var a = MakeDto("中和路100號", 28.5m, "5/12", 10);
        var b = MakeDto("中和路100號", 28.7m, "5/12", 11, SourceSite.Rakuya, "k2");

        DuplicateMatcher.IsDuplicate(a, b).Should().BeTrue();
    }

    [Fact]
    public void IsDuplicate_DifferentAddress_ReturnsFalse()
    {
        var a = MakeDto("中和路100號", 28.5m, "5/12", 10);
        var b = MakeDto("板橋路200號", 28.5m, "5/12", 10, SourceSite.Rakuya, "k2");

        DuplicateMatcher.IsDuplicate(a, b).Should().BeFalse();
    }

    [Fact]
    public void IsDuplicate_AreaTooFarApart_ReturnsFalse()
    {
        var a = MakeDto("中和路100號", 28.5m, "5/12", 10);
        var b = MakeDto("中和路100號", 30m, "5/12", 10, SourceSite.Rakuya, "k2");

        DuplicateMatcher.IsDuplicate(a, b).Should().BeFalse(); // diff > 0.5 ping
    }

    [Fact]
    public void IsDuplicate_DifferentFloor_ReturnsFalse()
    {
        var a = MakeDto("中和路100號", 28.5m, "5/12", 10);
        var b = MakeDto("中和路100號", 28.5m, "6/12", 10, SourceSite.Rakuya, "k2");

        DuplicateMatcher.IsDuplicate(a, b).Should().BeFalse();
    }
}
