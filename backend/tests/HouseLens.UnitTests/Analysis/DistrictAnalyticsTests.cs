using HouseLens.Application.Analysis;
using HouseLens.Domain.Entities;
using HouseLens.Domain.Enums;
using FluentAssertions;

namespace HouseLens.UnitTests.Analysis;

public class DistrictAnalyticsTests
{
    [Fact]
    public void CalcDistrictStats_WithProperties_ReturnsCorrectAvg()
    {
        var properties = new List<Property>
        {
            new() { District = "中和區", CurrentTotalPrice = 700m, AreaPing = 28m, Status = PropertyStatus.Active },
            new() { District = "中和區", CurrentTotalPrice = 800m, AreaPing = 30m, Status = PropertyStatus.Active }
        };

        var stats = DistrictAnalyticsService.CalcStats(properties, "中和區");

        stats.PropertyCount.Should().Be(2);
        stats.AvgUnitPrice.Should().BeGreaterThan(0);
        stats.MinTotalPrice.Should().Be(700m);
        stats.MaxTotalPrice.Should().Be(800m);
    }

    [Fact]
    public void CalcDistrictStats_EmptyDistrict_ReturnsInsufficient()
    {
        var stats = DistrictAnalyticsService.CalcStats([], "新莊區");

        stats.PropertyCount.Should().Be(0);
        stats.InsufficientData.Should().BeTrue();
    }

    [Fact]
    public void CalcDistrictStats_TwoProperties_IsSufficient()
    {
        var properties = new List<Property>
        {
            new() { District = "永和區", CurrentTotalPrice = 888m, AreaPing = 30m, Status = PropertyStatus.Active },
            new() { District = "永和區", CurrentTotalPrice = 898m, AreaPing = 32m, Status = PropertyStatus.Active }
        };

        var stats = DistrictAnalyticsService.CalcStats(properties, "永和區");

        stats.InsufficientData.Should().BeFalse();
    }

    [Fact]
    public void CalcDistrictStats_OneProperty_IsInsufficient()
    {
        var properties = new List<Property>
        {
            new() { District = "永和區", CurrentTotalPrice = 888m, AreaPing = 30m, Status = PropertyStatus.Active }
        };

        var stats = DistrictAnalyticsService.CalcStats(properties, "永和區");

        stats.InsufficientData.Should().BeTrue();
    }

    [Fact]
    public void CalcDistrictStats_AllSamePrice_ReturnsSingleBucket()
    {
        var properties = Enumerable.Range(0, 5)
            .Select(_ => new Property { District = "樹林區", CurrentTotalPrice = 800m, AreaPing = 30m, Status = PropertyStatus.Active })
            .ToList();

        var stats = DistrictAnalyticsService.CalcStats(properties, "樹林區");

        stats.PriceBuckets.Should().HaveCount(1);
        stats.PriceBuckets[0].Range.Should().Be("800-800");
        stats.PriceBuckets[0].Count.Should().Be(5);
    }

    [Fact]
    public void CalcTrend_GroupsByDateAndAveragesUnitPrice()
    {
        var p1 = new Property { District = "中和區", AreaPing = 30m, Status = PropertyStatus.Active };
        var p2 = new Property { District = "中和區", AreaPing = 40m, Status = PropertyStatus.Active };
        var entries = new List<PriceHistoryEntry>
        {
            new() { PropertyId = p1.Id, CapturedAt = new DateTime(2026, 7, 1, 3, 0, 0), UnitPrice = 30m, TotalPrice = 900m },
            new() { PropertyId = p2.Id, CapturedAt = new DateTime(2026, 7, 1, 9, 0, 0), UnitPrice = 20m, TotalPrice = 800m },
            new() { PropertyId = p1.Id, CapturedAt = new DateTime(2026, 7, 5, 3, 0, 0), UnitPrice = 28m, TotalPrice = 840m }
        };

        var trend = DistrictAnalyticsService.CalcTrend([p1, p2], entries, "中和區");

        trend.Should().HaveCount(2);
        trend[0].Date.Should().Be(new DateOnly(2026, 7, 1));
        trend[0].AvgUnitPrice.Should().Be(25m);
        trend[1].Date.Should().Be(new DateOnly(2026, 7, 5));
        trend[1].AvgUnitPrice.Should().Be(28m);
    }

    [Fact]
    public void CalcTrend_MissingUnitPrice_FallsBackToTotalDividedByArea()
    {
        var p1 = new Property { District = "中和區", AreaPing = 30m, Status = PropertyStatus.Active };
        var entries = new List<PriceHistoryEntry>
        {
            new() { PropertyId = p1.Id, CapturedAt = new DateTime(2026, 7, 1), UnitPrice = null, TotalPrice = 900m }
        };

        var trend = DistrictAnalyticsService.CalcTrend([p1], entries, "中和區");

        trend.Should().HaveCount(1);
        trend[0].AvgUnitPrice.Should().Be(30m);
    }

    [Fact]
    public void CalcTrend_IgnoresOtherDistrictsAndUnpriceableEntries()
    {
        var p1 = new Property { District = "中和區", AreaPing = 0m, Status = PropertyStatus.Active };
        var p2 = new Property { District = "新店區", AreaPing = 30m, Status = PropertyStatus.Active };
        var entries = new List<PriceHistoryEntry>
        {
            new() { PropertyId = p1.Id, CapturedAt = new DateTime(2026, 7, 1), UnitPrice = null, TotalPrice = 900m },
            new() { PropertyId = p2.Id, CapturedAt = new DateTime(2026, 7, 1), UnitPrice = 25m, TotalPrice = 750m }
        };

        var trend = DistrictAnalyticsService.CalcTrend([p1, p2], entries, "中和區");

        trend.Should().BeEmpty();
    }
}
