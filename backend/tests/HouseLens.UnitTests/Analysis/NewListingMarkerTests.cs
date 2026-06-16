using HouseLens.Application.Analysis;
using HouseLens.Domain.Entities;
using FluentAssertions;

namespace HouseLens.UnitTests.Analysis;

public class NewListingMarkerTests
{
    [Fact]
    public void MarkAsNew_SetsIsNewTrue()
    {
        var property = new Property { IsNew = false };
        NewListingMarker.MarkAsNew(property);
        property.IsNew.Should().BeTrue();
    }

    [Fact]
    public void ClearNewFlag_MultipleProperties_AllSetToFalse()
    {
        var properties = new[]
        {
            new Property { IsNew = true },
            new Property { IsNew = true },
            new Property { IsNew = false }
        };

        NewListingMarker.ClearNewFlag(properties);

        properties.Should().AllSatisfy(p => p.IsNew.Should().BeFalse());
    }

    [Fact]
    public void ClearNewFlag_EmptyList_NoException()
    {
        var act = () => NewListingMarker.ClearNewFlag([]);
        act.Should().NotThrow();
    }
}
