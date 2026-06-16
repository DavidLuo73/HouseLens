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
}
