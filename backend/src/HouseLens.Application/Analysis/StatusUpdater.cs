using HouseLens.Domain.Entities;
using HouseLens.Domain.Enums;

namespace HouseLens.Application.Analysis;

public static class StatusUpdater
{
    private const int DelistThreshold = 2;

    public static void UpdateDelistingStatus(Property property)
    {
        if (property.MissingCount >= DelistThreshold)
            property.Status = PropertyStatus.Delisted;
    }

    public static void ReactivateProperty(Property property)
    {
        property.Status = PropertyStatus.Active;
        property.MissingCount = 0;
        property.LastSeenAt = DateTime.UtcNow;
    }

    public static void IncrementMissing(Property property)
    {
        property.MissingCount++;
        UpdateDelistingStatus(property);
    }
}
