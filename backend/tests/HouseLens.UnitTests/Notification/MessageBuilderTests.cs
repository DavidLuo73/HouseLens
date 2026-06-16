using HouseLens.Application.Notification;
using HouseLens.Domain.Entities;
using HouseLens.Domain.Enums;
using FluentAssertions;

namespace HouseLens.UnitTests.Notification;

public class MessageBuilderTests
{
    [Fact]
    public void BuildBigDropMessage_NoBigDrops_ReturnsNull()
    {
        var result = NotificationBuilder.BuildBigDropMessage([]);
        result.Should().BeNull();
    }

    [Fact]
    public void BuildBigDropMessage_WithBigDrop_ContainsRequiredFields()
    {
        var property = new Property
        {
            District = "中和區",
            Address = "中和路100號",
            CurrentTotalPrice = 750m,
            Listings = [new Listing { Url = "https://591.com.tw/abc", SourceSite = SourceSite.F591, SourceListingKey = "abc", Title = "中和公寓" }]
        };

        var history = new PriceHistoryEntry
        {
            TotalPrice = 750m,
            ChangePercent = -0.0625m,
            IsBigDrop = true
        };

        var result = NotificationBuilder.BuildBigDropMessage([(property, history)]);

        result.Should().NotBeNull();
        result.Should().Contain("中和區");
        result.Should().Contain("750");
        result.Should().Contain("591.com.tw");
    }

    [Fact]
    public void BuildTop5Message_WithItems_ContainsDistrictName()
    {
        var prop = new Property
        {
            District = "中壢區",
            Address = "中壢路50號",
            CurrentTotalPrice = 600m,
            HasParking = true
        };

        var result = NotificationBuilder.BuildTop5Message(
        [
            ("中壢區", [(prop, 0.85m)])
        ]);

        result.Should().Contain("中壢區");
        result.Should().Contain("600");
        result.Should().Contain("0.85");
    }

    [Fact]
    public void BuildBigDropMessage_MoreThan10Items_LimitsToFirst10()
    {
        var items = Enumerable.Range(1, 12)
            .Select(i => (
                new Property { District = "中和區", Address = $"中和路{i}號", CurrentTotalPrice = 750m },
                new PriceHistoryEntry { TotalPrice = 750m, ChangePercent = -0.1m, IsBigDrop = true }
            ))
            .ToList<(Property, PriceHistoryEntry)>();

        var result = NotificationBuilder.BuildBigDropMessage(items);

        result.Should().NotBeNull();
        var addressLines = result!.Split('\n').Count(l => l.TrimStart().StartsWith("📍"));
        addressLines.Should().Be(10);
    }

    [Fact]
    public void BuildTop5Message_DistrictWithNoItems_IsSkipped()
    {
        var prop = new Property { Address = "永和路1號", CurrentTotalPrice = 700m };

        var result = NotificationBuilder.BuildTop5Message(
        [
            ("中和區", []),
            ("永和區", [(prop, 0.90m)])
        ]);

        result.Should().Contain("永和區");
        result.Should().NotContain("中和區");
    }
}
