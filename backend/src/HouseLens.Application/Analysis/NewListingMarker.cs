using HouseLens.Domain.Entities;

namespace HouseLens.Application.Analysis;

public static class NewListingMarker
{
    public static void MarkAsNew(Property property)
    {
        property.IsNew = true;
    }

    public static void ClearNewFlag(IEnumerable<Property> previouslyNewProperties)
    {
        foreach (var property in previouslyNewProperties)
        {
            property.IsNew = false;
        }
    }
}
