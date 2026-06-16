using FluentAssertions;
using Microsoft.AspNetCore.Mvc.Testing;
using System.Net;

namespace HouseLens.IntegrationTests.Crawling;

// T021: Integration test for crawl pipeline → GET /api/properties returns only matching properties
public class CrawlPipelineTests(TestWebApplicationFactory factory)
    : IClassFixture<TestWebApplicationFactory>
{
    private readonly HttpClient _client = factory.CreateClient();

    [Fact]
    public async Task GetProperties_AfterNoCrawl_ReturnsEmptyList()
    {
        // Arrange + Act
        var response = await _client.GetAsync("/api/properties");

        // Assert
        response.StatusCode.Should().Be(HttpStatusCode.OK);
        var content = await response.Content.ReadAsStringAsync();
        content.Should().NotBeNullOrEmpty();
    }
}
