using System.Net;
using FluentAssertions;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Routing;
using Microsoft.Extensions.DependencyInjection;

namespace HouseLens.IntegrationTests.Api;

public class PublicRouteTests(PublicApiWebApplicationFactory factory)
    : IClassFixture<PublicApiWebApplicationFactory>
{
    [Fact]
    public async Task PublicSurface_HasOnlySpecifiedReadsAndHealth()
    {
        using var client = factory.AuthenticatedClient();
        var endpoints = factory.Services.GetRequiredService<EndpointDataSource>().Endpoints.OfType<RouteEndpoint>().ToArray();
        var expected = new[] { "/health", "/api/properties", "/api/properties/{id:guid}",
            "/api/analytics/districts", "/api/analytics/top-properties", "/api/config/districts",
            "/api/crawl-runs/latest", "/api/proxy/image" };
        endpoints.Select(e => e.RoutePattern.RawText).Should().BeEquivalentTo(expected);
        endpoints.Should().OnlyContain(e => e.Metadata.GetMetadata<HttpMethodMetadata>()!.HttpMethods.SequenceEqual(new[] { "GET" }));

        foreach (var path in new[] { "/api/admin/platform-stats", "/api/config", "/api/config/platform-filters", "/api/crawl-runs/stream" })
            (await client.GetAsync(path)).StatusCode.Should().Be(HttpStatusCode.NotFound);
        foreach (var method in new[] { HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch, HttpMethod.Delete })
        {
            using var request = new HttpRequestMessage(method, "/api/properties");
            (await client.SendAsync(request)).StatusCode.Should().BeOneOf(HttpStatusCode.NotFound, HttpStatusCode.MethodNotAllowed);
        }
        using var anonymous = factory.CreateClient();
        (await anonymous.GetAsync("/health")).StatusCode.Should().Be(HttpStatusCode.OK);
    }
}
