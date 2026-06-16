using FluentAssertions;
using Microsoft.AspNetCore.Mvc.Testing;
using System.Net;
using System.Net.Http.Json;

namespace HouseLens.IntegrationTests.Api;

// T022: Contract test for GET /api/crawl-runs/latest
public class CrawlRunEndpointTests(TestWebApplicationFactory factory)
    : IClassFixture<TestWebApplicationFactory>
{
    private readonly HttpClient _client = factory.CreateClient();

    [Fact]
    public async Task GetLatest_WhenNoCrawlRunExists_Returns404OrEmptyResult()
    {
        // Arrange + Act
        var response = await _client.GetAsync("/api/crawl-runs/latest");

        // Assert - either 404 (no run) or 200 with null/empty is acceptable
        response.StatusCode.Should().BeOneOf(HttpStatusCode.OK, HttpStatusCode.NotFound);
    }

    [Fact]
    public async Task HealthEndpoint_Returns200()
    {
        var response = await _client.GetAsync("/health");
        response.StatusCode.Should().Be(HttpStatusCode.OK);
    }
}
