using HouseLens.Application.Crawling;
using HouseLens.Domain.Entities;

namespace HouseLens.Application.Dedup;

public static class DuplicateMatcher
{
    private const decimal AreaTolerance = 0.5m;
    private const int AgeTolerance = 1;

    public static bool IsDuplicate(PropertyDto a, PropertyDto b)
    {
        if (a.District != b.District) return false;

        var addrA = NormalizeAddress(a.Address);
        var addrB = NormalizeAddress(b.Address);
        if (addrA != addrB) return false;

        if (Math.Abs(a.AreaPing - b.AreaPing) > AreaTolerance) return false;

        if (a.Floor != null && b.Floor != null && a.Floor != b.Floor) return false;

        if (a.AgeYears.HasValue && b.AgeYears.HasValue
            && Math.Abs(a.AgeYears.Value - b.AgeYears.Value) > AgeTolerance)
            return false;

        return true;
    }

    public static bool IsDuplicate(Property existing, PropertyDto candidate)
    {
        if (existing.District != candidate.District) return false;

        var addrA = NormalizeAddress(existing.Address);
        var addrB = NormalizeAddress(candidate.Address);
        if (addrA != addrB) return false;

        if (Math.Abs(existing.AreaPing - candidate.AreaPing) > AreaTolerance) return false;

        if (existing.Floor != null && candidate.Floor != null && existing.Floor != candidate.Floor) return false;

        if (existing.AgeYears.HasValue && candidate.AgeYears.HasValue
            && Math.Abs(existing.AgeYears.Value - candidate.AgeYears.Value) > AgeTolerance)
            return false;

        return true;
    }

    private static string? NormalizeAddress(string? address)
    {
        if (string.IsNullOrWhiteSpace(address)) return null;
        return address.Trim()
            .Replace("台", "臺")
            .Replace("  ", " ");
    }
}
