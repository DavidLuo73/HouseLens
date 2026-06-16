using HouseLens.Domain.Enums;

namespace HouseLens.Application.Analysis;

public record PriceChangeResult(
    PriceChangeFlag Flag,
    decimal? ChangePercent,
    bool IsBigDrop
);

public static class PriceChangeDetector
{
    public static PriceChangeResult Detect(
        decimal? previousPrice,
        decimal currentPrice,
        decimal bigDropPercent = 0.05m,
        decimal bigDropAmount = 30m)
    {
        if (!previousPrice.HasValue || previousPrice.Value == currentPrice)
            return new PriceChangeResult(PriceChangeFlag.None, null, false);

        var delta = currentPrice - previousPrice.Value;
        var changePercent = delta / previousPrice.Value;
        var flag = delta < 0 ? PriceChangeFlag.Decreased : PriceChangeFlag.Increased;

        var isBigDrop = flag == PriceChangeFlag.Decreased
            && (Math.Abs(changePercent) >= bigDropPercent || Math.Abs(delta) >= bigDropAmount);

        return new PriceChangeResult(flag, changePercent, isBigDrop);
    }
}
