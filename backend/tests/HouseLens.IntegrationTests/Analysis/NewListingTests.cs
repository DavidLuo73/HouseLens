using HouseLens.Application.Analysis;
using HouseLens.Domain.Entities;
using FluentAssertions;

namespace HouseLens.IntegrationTests.Analysis;

public class NewListingTests
{
    [Fact]
    public void MarkAsNew_ShouldSetIsNewTrue()
    {
        var property = new Property { IsNew = false };

        NewListingMarker.MarkAsNew(property);

        property.IsNew.Should().BeTrue();
    }

    [Fact]
    public void ClearNewFlag_ShouldSetIsNewFalse_OnAllProperties()
    {
        var properties = new[]
        {
            new Property { IsNew = true },
            new Property { IsNew = true },
        };

        NewListingMarker.ClearNewFlag(properties);

        properties.Should().AllSatisfy(p => p.IsNew.Should().BeFalse());
    }

    [Fact]
    public void ClearNewFlag_DoesNotAffect_PropertiesAlreadyNotNew()
    {
        var properties = new[]
        {
            new Property { IsNew = true },
            new Property { IsNew = false },
        };

        NewListingMarker.ClearNewFlag(properties);

        properties[0].IsNew.Should().BeFalse();
        properties[1].IsNew.Should().BeFalse();
    }

    [Fact]
    public void NewListingFlow_SecondBatch_ClearsIsNewFlag()
    {
        // First batch: property is newly discovered and marked
        var property = new Property { IsNew = false };
        NewListingMarker.MarkAsNew(property);
        property.IsNew.Should().BeTrue();

        // Second batch: clear IsNew from the previous batch before processing
        var previouslyNew = new[] { property };
        NewListingMarker.ClearNewFlag(previouslyNew);

        property.IsNew.Should().BeFalse();
    }
}
