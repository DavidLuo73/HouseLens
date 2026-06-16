using FluentAssertions;
using Microsoft.AspNetCore.Mvc.Testing;
using System.Net;
using System.Net.Http.Json;
using System.Text.Json;

namespace HouseLens.IntegrationTests.Api;

// T046: Contract test for GET /api/analytics/districts
public class AnalyticsEndpointTests(TestWebApplicationFactory factory)
    : IClassFixture<TestWebApplicationFactory>
{
    private readonly HttpClient _client = factory.CreateClient();

    private static readonly JsonSerializerOptions _jsonOpts = new(JsonSerializerDefaults.Web);

    [Fact]
    public async Task GetDistricts_Returns200WithDistrictsArray()
    {
        var response = await _client.GetAsync("/api/analytics/districts");

        response.StatusCode.Should().Be(HttpStatusCode.OK);

        var body = await response.Content.ReadFromJsonAsync<JsonElement>(_jsonOpts);
        body.TryGetProperty("districts", out var districts).Should().BeTrue();
        districts.ValueKind.Should().Be(JsonValueKind.Array);
    }

    [Fact]
    public async Task GetDistricts_EachItemHasRequiredFields()
    {
        var response = await _client.GetAsync("/api/analytics/districts");
        response.StatusCode.Should().Be(HttpStatusCode.OK);

        var body = await response.Content.ReadFromJsonAsync<JsonElement>(_jsonOpts);
        var districts = body.GetProperty("districts");

        foreach (var d in districts.EnumerateArray())
        {
            d.TryGetProperty("district", out _).Should().BeTrue();
            d.TryGetProperty("propertyCount", out _).Should().BeTrue();
            d.TryGetProperty("avgUnitPrice", out _).Should().BeTrue();
            d.TryGetProperty("minTotalPrice", out _).Should().BeTrue();
            d.TryGetProperty("maxTotalPrice", out _).Should().BeTrue();
            d.TryGetProperty("priceBuckets", out _).Should().BeTrue();
            d.TryGetProperty("trend", out _).Should().BeTrue();
            d.TryGetProperty("insufficientData", out _).Should().BeTrue();
        }
    }

    [Fact]
    public async Task GetDistricts_WhenNoData_ReturnsInsufficientData()
    {
        var response = await _client.GetAsync("/api/analytics/districts");
        response.StatusCode.Should().Be(HttpStatusCode.OK);

        var body = await response.Content.ReadFromJsonAsync<JsonElement>(_jsonOpts);
        var districts = body.GetProperty("districts");

        // 空資料庫下每個區域皆應標示 insufficientData: true
        foreach (var d in districts.EnumerateArray())
        {
            d.GetProperty("insufficientData").GetBoolean().Should().BeTrue();
            d.GetProperty("propertyCount").GetInt32().Should().Be(0);
        }
    }

    [Fact]
    public async Task GetDistricts_PriceBucketsIsArray()
    {
        var response = await _client.GetAsync("/api/analytics/districts");
        var body = await response.Content.ReadFromJsonAsync<JsonElement>(_jsonOpts);

        foreach (var d in body.GetProperty("districts").EnumerateArray())
        {
            d.GetProperty("priceBuckets").ValueKind.Should().Be(JsonValueKind.Array);
            d.GetProperty("trend").ValueKind.Should().Be(JsonValueKind.Array);
        }
    }
}
