using System.Net.Http.Json;
using System.Text.Json;
using FluentAssertions;
using HouseLens.Domain.Entities;
using HouseLens.Infrastructure.Persistence;
using Microsoft.Extensions.DependencyInjection;

namespace HouseLens.IntegrationTests.Api;

public class PropertyPaginationTests(TestWebApplicationFactory factory)
    : IClassFixture<TestWebApplicationFactory>
{
    [Theory]
    [InlineData("score", "10000000")]
    [InlineData("priceDrop", "20000000")]
    [InlineData("unitPrice", "30000000")]
    [InlineData("postedDate", "40000000")]
    public async Task TiedSortValues_HaveStableOrderAcrossThreePages(string sortBy, string prefix)
    {
        var district = "排序測試" + sortBy;
        var expected = Enumerable.Range(1, 6)
            .Select(i => Guid.Parse($"{prefix}-0000-0000-0000-{i:D12}")).ToArray();
        using (var scope = factory.Services.CreateScope())
        {
            var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
            foreach (var index in new[] { 3, 0, 5, 2, 1, 4 })
            {
                db.Properties.Add(new Property
                {
                    Id = expected[index], City = "測試市", District = district,
                    CurrentTotalPrice = 800m, CurrentUnitPrice = 20m, AreaPing = 40m,
                    Score = 0.8m, FirstSeenAt = new DateTime(2026, 1, 1, 0, 0, 0, DateTimeKind.Utc)
                });
                await db.SaveChangesAsync();
            }
        }

        using var client = factory.CreateClient();
        for (var repeat = 0; repeat < 2; repeat++)
        {
            var ids = new List<Guid>();
            for (var page = 1; page <= 3; page++)
            {
                var body = await client.GetFromJsonAsync<JsonElement>(
                    $"/api/properties?district={Uri.EscapeDataString(district)}&sortBy={sortBy}&page={page}&pageSize=2");
                body.GetProperty("total").GetInt32().Should().Be(6);
                ids.AddRange(body.GetProperty("items").EnumerateArray().Select(p => p.GetProperty("id").GetGuid()));
            }
            ids.Should().Equal(expected);
            ids.Distinct().Should().HaveCount(6);
        }
    }
}

