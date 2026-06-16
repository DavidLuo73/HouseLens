using HouseLens.Domain.Entities;
using HouseLens.Domain.Enums;

namespace HouseLens.Application.Dedup;

public static class PropertyMerger
{
    public static void MergeInto(Property duplicate, Property primary)
    {
        duplicate.Status = PropertyStatus.Merged;
        duplicate.MergedIntoPropertyId = primary.Id;

        // Move listings to primary
        foreach (var listing in duplicate.Listings)
        {
            listing.PropertyId = primary.Id;
        }

        // Update primary price to lowest active listing
        var allPrices = primary.Listings
            .Where(l => l.IsActive)
            .Select(l => l.LatestSourcePrice)
            .ToList();

        if (allPrices.Count > 0)
        {
            primary.CurrentTotalPrice = allPrices.Min();
            primary.LastSeenAt = DateTime.UtcNow;
        }
    }
}
