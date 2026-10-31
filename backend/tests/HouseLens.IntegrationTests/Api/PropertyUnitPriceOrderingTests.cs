using FluentAssertions;
using HouseLens.Domain.Entities;
using HouseLens.Infrastructure.Persistence;
using Microsoft.Extensions.DependencyInjection;
using System.Net.Http.Json;
using System.Text.Json;

namespace HouseLens.IntegrationTests.Api;

public class PropertyUnitPriceOrderingTests(TestWebApplicationFactory factory)
    : IClassFixture<TestWebApplicationFactory>
{
    [Fact]
    public async Task UnitPriceSort_PlacesUnknownPricesFirstThenOrdersPricesAscending()
    {
        var unknown = new Property { City = "新北市", District = "中和區", CurrentUnitPrice = null };
        var lower = new Property { City = "新北市", District = "中和區", CurrentUnitPrice = 20.50m };
        var higher = new Property { City = "新北市", District = "中和區", CurrentUnitPrice = 30.25m };
        using (var scope = factory.Services.CreateScope())
        {
            var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
            db.Properties.AddRange(higher, unknown, lower);
            await db.SaveChangesAsync();
        }

        using var client = factory.CreateClient();
        var body = await client.GetFromJsonAsync<JsonElement>("/api/properties?sortBy=unitPrice");

        body.GetProperty("items").EnumerateArray().Select(p => p.GetProperty("id").GetGuid())
            .Should().Equal(unknown.Id, lower.Id, higher.Id);
    }
}
