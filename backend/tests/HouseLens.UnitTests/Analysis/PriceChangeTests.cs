using HouseLens.Application.Analysis;
using HouseLens.Domain.Enums;
using FluentAssertions;

namespace HouseLens.UnitTests.Analysis;

public class PriceChangeTests
{
    [Theory]
    [InlineData(800.0, 750.0, PriceChangeFlag.Decreased, -6.25)]
    [InlineData(700.0, 750.0, PriceChangeFlag.Increased, 7.14)]
    [InlineData(750.0, 750.0, PriceChangeFlag.None, 0.0)]
    public void DetectChange_ShouldReturnCorrectFlag(
        double previous, double current, PriceChangeFlag expectedFlag, double expectedPercent)
    {
        var result = PriceChangeDetector.Detect((decimal)previous, (decimal)current);

        result.Flag.Should().Be(expectedFlag);
        if (expectedFlag != PriceChangeFlag.None)
            result.ChangePercent.Should().BeApproximately((decimal)(expectedPercent / 100), 0.001m);
    }

    [Fact]
    public void DetectChange_BigDrop_WhenPercentExceedsThreshold()
    {
        var result = PriceChangeDetector.Detect(800m, 750m, bigDropPercent: 0.05m, bigDropAmount: 100m);
        result.IsBigDrop.Should().BeTrue(); // 6.25% > 5%
    }

    [Fact]
    public void DetectChange_BigDrop_WhenAmountExceedsThreshold()
    {
        var result = PriceChangeDetector.Detect(800m, 765m, bigDropPercent: 0.10m, bigDropAmount: 30m);
        result.IsBigDrop.Should().BeTrue(); // 35萬 > 30萬
    }

    [Fact]
    public void DetectChange_NotBigDrop_WhenBelowBothThresholds()
    {
        var result = PriceChangeDetector.Detect(800m, 795m, bigDropPercent: 0.05m, bigDropAmount: 30m);
        result.IsBigDrop.Should().BeFalse(); // 5萬 < 30萬, 0.6% < 5%
    }

    [Fact]
    public void DetectChange_NoPreviousPrice_ReturnsNone()
    {
        var result = PriceChangeDetector.Detect(null, 750m);
        result.Flag.Should().Be(PriceChangeFlag.None);
        result.ChangePercent.Should().BeNull();
        result.IsBigDrop.Should().BeFalse();
    }
}
