using HouseLens.Domain.Entities;
using HouseLens.Domain.Enums;

namespace HouseLens.Application.Notification;

public static class NotificationBuilder
{
    public static string? BuildBigDropMessage(
        IReadOnlyList<(Property property, PriceHistoryEntry history)> bigDrops)
    {
        if (bigDrops.Count == 0) return null;

        var sb = new System.Text.StringBuilder();
        sb.AppendLine("🏠 大降價物件通知");
        sb.AppendLine($"共 {bigDrops.Count} 筆大降價物件：");
        sb.AppendLine();

        foreach (var (prop, hist) in bigDrops.Take(10))
        {
            var address = prop.Address ?? "地址未提供";
            var originalPrice = hist.TotalPrice / (1 + (hist.ChangePercent ?? 0));
            var dropPct = Math.Abs(hist.ChangePercent ?? 0) * 100;
            var url = prop.Listings.FirstOrDefault()?.Url ?? "";

            sb.AppendLine($"📍 {prop.District} {address}");
            sb.AppendLine($"   原價: {originalPrice:F0} 萬 → 新價: {hist.TotalPrice:F0} 萬 (↓{dropPct:F1}%)");
            if (!string.IsNullOrEmpty(url))
                sb.AppendLine($"   🔗 {url}");
            sb.AppendLine();
        }

        return sb.ToString().TrimEnd();
    }

    public static string BuildTop5Message(
        IReadOnlyList<(string district, IReadOnlyList<(Property property, decimal score)> items)> districtTops)
    {
        var sb = new System.Text.StringBuilder();
        sb.AppendLine("⭐ 各區域 Top 5 優質物件");
        sb.AppendLine();

        foreach (var (district, items) in districtTops)
        {
            if (items.Count == 0) continue;

            sb.AppendLine($"【{district}】");
            var rank = 1;
            foreach (var (prop, score) in items.Take(5))
            {
                var parking = prop.HasParking ? "🅿" : "";
                var address = prop.Address ?? "地址未提供";
                sb.AppendLine($"{rank}. {address} {parking}");
                sb.AppendLine($"   總價 {prop.CurrentTotalPrice:F0}萬 | 評分 {score:F2}");
                rank++;
            }
            sb.AppendLine();
        }

        return sb.ToString().TrimEnd();
    }
}
