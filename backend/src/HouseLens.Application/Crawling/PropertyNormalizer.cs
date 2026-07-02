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
        IReadOnlyDictionary<string, DistrictCriteria> districtCriteria)
    {
        if (!districtCriteria.TryGetValue(dto.District, out var criteria)) return false;
        if (dto.TotalPrice > criteria.MaxTotalPrice) return false;
        // 坪數下限不在此過濾：各平台坪數定義不一（主建/總建），由各爬蟲的伺服器端參數處理
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
