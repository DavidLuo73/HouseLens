using HouseLens.Application.Analysis;
using HouseLens.Domain.Entities;
using HouseLens.Domain.Enums;
using FluentAssertions;

namespace HouseLens.IntegrationTests.Analysis;

public class DelistingTests
{
    [Fact]
    public void UpdateStatus_MissingCount2_ShouldMarkDelisted()
    {
        var property = new Property
        {
            Status = PropertyStatus.Active,
            MissingCount = 2
        };

        StatusUpdater.UpdateDelistingStatus(property);

        property.Status.Should().Be(PropertyStatus.Delisted);
    }

    [Fact]
    public void UpdateStatus_MissingCount1_ShouldRemainActive()
    {
        var property = new Property
        {
            Status = PropertyStatus.Active,
            MissingCount = 1
        };

        StatusUpdater.UpdateDelistingStatus(property);

        property.Status.Should().Be(PropertyStatus.Active);
    }

    [Fact]
    public void ReactivateProperty_WhenReappears_ShouldRestoreActive()
    {
        var property = new Property
        {
            Status = PropertyStatus.Delisted,
            MissingCount = 5
        };

        StatusUpdater.ReactivateProperty(property);

        property.Status.Should().Be(PropertyStatus.Active);
        property.MissingCount.Should().Be(0);
    }
}
