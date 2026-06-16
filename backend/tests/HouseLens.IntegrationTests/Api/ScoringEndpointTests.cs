using FluentAssertions;
using System.Net;
using System.Net.Http.Json;
using System.Text.Json;

namespace HouseLens.IntegrationTests.Api;

// T054: Contract tests for GET /api/analytics/top-properties and GET/PUT /api/config
public class ScoringEndpointTests(TestWebApplicationFactory factory)
    : IClassFixture<TestWebApplicationFactory>
{
    private readonly HttpClient _client = factory.CreateClient();
    private static readonly JsonSerializerOptions _jsonOpts = new(JsonSerializerDefaults.Web);

    [Fact]
    public async Task GetTopProperties_DefaultType_Returns200WithTopRatedStructure()
    {
        var response = await _client.GetAsync("/api/analytics/top-properties");

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>(_jsonOpts);
        body.TryGetProperty("type", out var type).Should().BeTrue();
        type.GetString().Should().Be("topRated");
        body.TryGetProperty("byDistrict", out var byDistrict).Should().BeTrue();
        byDistrict.ValueKind.Should().Be(JsonValueKind.Array);
    }

    [Fact]
    public async Task GetTopProperties_TopRatedType_EachDistrictHasDistrictAndItemsFields()
    {
        var response = await _client.GetAsync("/api/analytics/top-properties?type=topRated");

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>(_jsonOpts);
        foreach (var d in body.GetProperty("byDistrict").EnumerateArray())
        {
            d.TryGetProperty("district", out _).Should().BeTrue();
            d.TryGetProperty("items", out var items).Should().BeTrue();
            items.ValueKind.Should().Be(JsonValueKind.Array);
        }
    }

    [Fact]
    public async Task GetTopProperties_BigDropType_Returns200WithItemsArray()
    {
        var response = await _client.GetAsync("/api/analytics/top-properties?type=bigDrop");

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>(_jsonOpts);
        body.TryGetProperty("type", out var type).Should().BeTrue();
        type.GetString().Should().Be("bigDrop");
        body.TryGetProperty("items", out var items).Should().BeTrue();
        items.ValueKind.Should().Be(JsonValueKind.Array);
    }

    [Fact]
    public async Task GetTopProperties_EmptyDb_TopRatedHasDistrictsWithEmptyItems()
    {
        var response = await _client.GetAsync("/api/analytics/top-properties?type=topRated");

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>(_jsonOpts);
        var byDistrict = body.GetProperty("byDistrict");
        foreach (var d in byDistrict.EnumerateArray())
        {
            d.GetProperty("items").GetArrayLength().Should().Be(0);
        }
    }

    [Fact]
    public async Task GetConfig_Returns200WithTrackingAndScoringFields()
    {
        var response = await _client.GetAsync("/api/config");

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>(_jsonOpts);
        body.TryGetProperty("tracking", out var tracking).Should().BeTrue();
        tracking.TryGetProperty("districts", out _).Should().BeTrue();
        tracking.TryGetProperty("maxTotalPrice", out _).Should().BeTrue();
        body.TryGetProperty("scoring", out var scoring).Should().BeTrue();
        scoring.TryGetProperty("weightUnitPrice", out _).Should().BeTrue();
        scoring.TryGetProperty("weightAge", out _).Should().BeTrue();
        scoring.TryGetProperty("weightParking", out _).Should().BeTrue();
        scoring.TryGetProperty("weightLocation", out _).Should().BeTrue();
        scoring.TryGetProperty("bigDropPercent", out _).Should().BeTrue();
        scoring.TryGetProperty("bigDropAmount", out _).Should().BeTrue();
    }

    [Fact]
    public async Task PutConfig_WithValidWeightsSumTo1_Returns200()
    {
        var config = new
        {
            tracking = new { districts = Array.Empty<string>(), maxTotalPrice = 800 },
            scoring = new
            {
                weightUnitPrice = 0.40,
                weightAge = 0.25,
                weightParking = 0.20,
                weightLocation = 0.15,
                bigDropPercent = 0.05,
                bigDropAmount = 30
            }
        };

        var response = await _client.PutAsJsonAsync("/api/config", config);

        response.StatusCode.Should().Be(HttpStatusCode.OK);
    }

    [Fact]
    public async Task PutConfig_WithWeightsSumNotEqualTo1_Returns400WithErrorBody()
    {
        var config = new
        {
            tracking = new { districts = Array.Empty<string>(), maxTotalPrice = 800 },
            scoring = new
            {
                weightUnitPrice = 0.50,
                weightAge = 0.25,
                weightParking = 0.20,
                weightLocation = 0.15,
                bigDropPercent = 0.05,
                bigDropAmount = 30
            }
        };

        var response = await _client.PutAsJsonAsync("/api/config", config);

        response.StatusCode.Should().Be(HttpStatusCode.BadRequest);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>(_jsonOpts);
        body.TryGetProperty("error", out var error).Should().BeTrue();
        error.TryGetProperty("code", out _).Should().BeTrue();
        error.TryGetProperty("message", out _).Should().BeTrue();
    }

    [Fact]
    public async Task GetTopProperties_WithLimitParam_Returns200()
    {
        var response = await _client.GetAsync("/api/analytics/top-properties?type=topRated&limit=3");

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>(_jsonOpts);
        body.TryGetProperty("byDistrict", out _).Should().BeTrue();
    }
}
