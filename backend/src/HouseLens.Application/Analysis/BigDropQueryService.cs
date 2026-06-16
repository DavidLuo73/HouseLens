namespace HouseLens.Application.Analysis;

public record BigDropItem(
    Guid Id,
    string Title,
    string District,
    string? Address,
    decimal OriginalPrice,
    decimal NewPrice,
    decimal DropAmount,
    decimal DropPercent,
    string Url
);

public static class BigDropQueryService
{
    public static BigDropItem CalcItem(
        Guid propertyId,
        string title,
        string district,
        string? address,
        decimal newPrice,
        decimal? changePercent,
        string url)
    {
        var originalPrice = changePercent is not null and not 0m
            ? newPrice / (1m + changePercent.Value)
            : newPrice;
        var dropAmount = originalPrice - newPrice;
        var dropPercent = -(changePercent ?? 0m);

        return new BigDropItem(
            propertyId, title, district, address,
            Math.Round(originalPrice, 1),
            newPrice,
            Math.Round(dropAmount, 1),
            dropPercent,
            url
        );
    }
}
