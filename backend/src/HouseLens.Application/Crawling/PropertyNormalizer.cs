namespace HouseLens.Application.Crawling;

public static class PropertyNormalizer
{
    public static PropertyDto Normalize(PropertyDto dto)
    {
        var address = string.IsNullOrWhiteSpace(dto.Address) ? null : NormalizeAddress(dto.Address);
        var title = string.IsNullOrWhiteSpace(dto.Title) ? "未提供" : dto.Title;
        var floor = string.IsNullOrWhiteSpace(dto.Floor) ? null : dto.Floor;

        return dto with
        {
            Address = address,
            Title = title,
            Floor = floor
        };
    }

    public static bool MeetsTrackingCriteria(
        PropertyDto dto,
        IReadOnlyDictionary<string, decimal> districtMaxPrices)
    {
        if (!districtMaxPrices.TryGetValue(dto.District, out var maxPrice)) return false;
        if (dto.TotalPrice > maxPrice) return false;
        return true;
    }

    private static string NormalizeAddress(string address)
    {
        // Remove extra whitespace
        address = string.Join(" ", address.Split(' ', StringSplitOptions.RemoveEmptyEntries));

        // Normalize common address abbreviations
        address = address
            .Replace("台北", "臺北")
            .Trim();

        return address;
    }
}
