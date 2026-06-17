using FluentAssertions;
using HouseLens.Domain.Entities;
using HouseLens.Domain.Enums;
using HouseLens.Infrastructure.Persistence;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using System.Net;
using System.Net.Http.Json;
using System.Text.Json;

namespace HouseLens.IntegrationTests.Api;

// 清單端點需回傳每個物件的完整歷史價格序列（供 sparkline 與明細 Modal）
public class PropertyPriceHistoryTests(TestWebApplicationFactory factory)
    : IClassFixture<TestWebApplicationFactory>
{
    private readonly TestWebApplicationFactory _factory = factory;
    private readonly HttpClient _client = factory.CreateClient();
    private static readonly JsonSerializerOptions _jsonOpts = new(JsonSerializerDefaults.Web);

    [Fact]
    public async Task GetProperties_ItemIncludesPriceHistoryOrderedDesc()
    {
        var propId = Guid.NewGuid();
        var crawlRunId1 = Guid.NewGuid();
        var crawlRunId2 = Guid.NewGuid();
        using (var scope = _factory.Services.CreateScope())
        {
            var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
            db.CrawlRuns.AddRange(
                new CrawlRun { Id = crawlRunId1 },
                new CrawlRun { Id = crawlRunId2 });
            db.Properties.Add(new Property
            {
                Id = propId,
                City = "新北市",
                District = "中和區",
                AreaPing = 30m,
                CurrentTotalPrice = 1280m,
                Status = PropertyStatus.Active,
            });
            db.PriceHistoryEntries.AddRange(
                new PriceHistoryEntry
                {
                    PropertyId = propId,
                    CrawlRunId = crawlRunId1,
                    TotalPrice = 1300m,
                    CapturedAt = new DateTime(2026, 6, 1, 0, 0, 0, DateTimeKind.Utc),
                    ChangeFlag = PriceChangeFlag.None,
                },
                new PriceHistoryEntry
                {
                    PropertyId = propId,
                    CrawlRunId = crawlRunId2,
                    TotalPrice = 1280m,
                    CapturedAt = new DateTime(2026, 6, 11, 0, 0, 0, DateTimeKind.Utc),
                    ChangeFlag = PriceChangeFlag.Decreased,
                    ChangePercent = -0.015m,
                    IsBigDrop = false,
                });
            await db.SaveChangesAsync();
        }

        var response = await _client.GetAsync("/api/properties");
        response.StatusCode.Should().Be(HttpStatusCode.OK);

        var body = await response.Content.ReadFromJsonAsync<JsonElement>(_jsonOpts);
        var item = body.GetProperty("items").EnumerateArray()
            .Single(i => i.GetProperty("id").GetGuid() == propId);

        item.TryGetProperty("priceHistory", out var history).Should().BeTrue();
        history.ValueKind.Should().Be(JsonValueKind.Array);
        history.GetArrayLength().Should().Be(2);

        var first = history[0];
        first.GetProperty("capturedAt").GetDateTime()
            .Should().Be(new DateTime(2026, 6, 11, 0, 0, 0, DateTimeKind.Utc));
        first.GetProperty("totalPrice").GetDecimal().Should().Be(1280m);
        first.GetProperty("changeFlag").GetString().Should().Be("decreased");
        first.TryGetProperty("unitPrice", out _).Should().BeTrue();
        first.TryGetProperty("changePercent", out _).Should().BeTrue();
        first.TryGetProperty("isBigDrop", out _).Should().BeTrue();
    }
}
