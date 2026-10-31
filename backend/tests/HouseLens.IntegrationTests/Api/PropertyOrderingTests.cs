using FluentAssertions;
using HouseLens.Domain.Entities;
using HouseLens.Infrastructure.Persistence;
using Microsoft.Extensions.DependencyInjection;
using System.Net.Http.Json;
using System.Text.Json;

namespace HouseLens.IntegrationTests.Api;

public class PropertyOrderingTests(TestWebApplicationFactory factory)
    : IClassFixture<TestWebApplicationFactory>
{
    [Fact]
    public async Task DefaultSort_PlacesScoredPropertiesBeforeUnscoredProperties()
    {
        var scored = new Property { City = "新北市", District = "中和區", Score = 0.7m };
        var unscored = new Property { City = "新北市", District = "中和區", Score = null };
        using (var scope = factory.Services.CreateScope())
        {
            var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
            db.Properties.AddRange(unscored, scored);
            await db.SaveChangesAsync();
        }

        using var client = factory.CreateClient();
        var body = await client.GetFromJsonAsync<JsonElement>("/api/properties");

        body.GetProperty("items").EnumerateArray().Select(p => p.GetProperty("id").GetGuid())
            .Should().Equal(scored.Id, unscored.Id);
    }
}
