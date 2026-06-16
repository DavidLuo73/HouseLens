using HouseLens.Application.Analysis;
using HouseLens.Domain.Entities;
using HouseLens.Domain.Enums;
using FluentAssertions;

namespace HouseLens.UnitTests.Analysis;

public class StatusUpdaterTests
{
    [Fact]
    public void IncrementMissing_Once_MissingCount1_StillActive()
    {
        var property = new Property { Status = PropertyStatus.Active, MissingCount = 0 };
        StatusUpdater.IncrementMissing(property);
        property.MissingCount.Should().Be(1);
        property.Status.Should().Be(PropertyStatus.Active);
    }

    [Fact]
    public void IncrementMissing_Twice_MissingCount2_BecomesDelisted()
    {
        var property = new Property { Status = PropertyStatus.Active, MissingCount = 0 };
        StatusUpdater.IncrementMissing(property);
        StatusUpdater.IncrementMissing(property);
        property.MissingCount.Should().Be(2);
        property.Status.Should().Be(PropertyStatus.Delisted);
    }

    [Fact]
    public void IncrementMissing_AlreadyDelisted_StaysDelisted()
    {
        var property = new Property { Status = PropertyStatus.Delisted, MissingCount = 2 };
        StatusUpdater.IncrementMissing(property);
        property.Status.Should().Be(PropertyStatus.Delisted);
        property.MissingCount.Should().Be(3);
    }

    [Fact]
    public void ReactivateProperty_SetsActiveAndResetsMissing()
    {
        var property = new Property { Status = PropertyStatus.Delisted, MissingCount = 3 };
        StatusUpdater.ReactivateProperty(property);
        property.Status.Should().Be(PropertyStatus.Active);
        property.MissingCount.Should().Be(0);
        property.LastSeenAt.Should().BeCloseTo(DateTime.UtcNow, TimeSpan.FromSeconds(5));
    }

    [Fact]
    public void UpdateDelistingStatus_BelowThreshold_NoChange()
    {
        var property = new Property { Status = PropertyStatus.Active, MissingCount = 1 };
        StatusUpdater.UpdateDelistingStatus(property);
        property.Status.Should().Be(PropertyStatus.Active);
    }

    [Fact]
    public void UpdateDelistingStatus_AtThreshold_Delists()
    {
        var property = new Property { Status = PropertyStatus.Active, MissingCount = 2 };
        StatusUpdater.UpdateDelistingStatus(property);
        property.Status.Should().Be(PropertyStatus.Delisted);
    }
}
