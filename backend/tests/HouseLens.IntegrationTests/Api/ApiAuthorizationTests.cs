using System.Net;
using FluentAssertions;
using HouseLens.IntegrationTests.Auth;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.Extensions.Options;

namespace HouseLens.IntegrationTests.Api;

public class ApiAuthorizationTests(PublicApiWebApplicationFactory factory)
    : IClassFixture<PublicApiWebApplicationFactory>
{
    [Theory]
    [InlineData("/api/properties", HttpStatusCode.OK)]
    [InlineData("/api/properties/00000000-0000-0000-0000-000000000001", HttpStatusCode.NotFound)]
    [InlineData("/api/analytics/districts", HttpStatusCode.OK)]
    [InlineData("/api/analytics/top-properties", HttpStatusCode.OK)]
    [InlineData("/api/config/districts", HttpStatusCode.OK)]
    [InlineData("/api/crawl-runs/latest", HttpStatusCode.NotFound)]
    [InlineData("/api/proxy/image?url=https%3A%2F%2Fres.sinyi.com.tw%2Ftest.jpg", HttpStatusCode.OK)]
    public async Task ReadRoutes_RequireApprovedIdentity(string path, HttpStatusCode allowedStatus)
    {
        using var anonymous = factory.CreateClient();
        (await anonymous.GetAsync(path)).StatusCode.Should().Be(HttpStatusCode.Unauthorized);
        using var stranger = factory.AuthenticatedClient(factory.Issuer.Token(subject: Guid.NewGuid().ToString()));
        (await stranger.GetAsync(path)).StatusCode.Should().Be(HttpStatusCode.Forbidden);
        using var approved = factory.AuthenticatedClient();
        (await approved.GetAsync(path)).StatusCode.Should().Be(allowedStatus);
    }

    [Theory]
    [InlineData("issuer")]
    [InlineData("audience")]
    [InlineData("expired")]
    [InlineData("signature")]
    public async Task InvalidToken_IsRejected(string defect)
    {
        var token = factory.Issuer.Token(
            issuer: defect == "issuer" ? "https://other.example.test" : TestJwtIssuer.Authority,
            audience: defect == "audience" ? "wrong" : "authenticated",
            expired: defect == "expired", wrongKey: defect == "signature");
        using var client = factory.AuthenticatedClient(token);
        (await client.GetAsync("/api/properties")).StatusCode.Should().Be(HttpStatusCode.Unauthorized);
    }

    [Fact]
    public async Task RemovedMember_IsRejectedWithSameToken_IncludingCachedImage()
    {
        using var client = factory.AuthenticatedClient();
        const string image = "/api/proxy/image?url=https%3A%2F%2Fres.sinyi.com.tw%2Ftest.jpg";
        (await client.GetAsync(image)).StatusCode.Should().Be(HttpStatusCode.OK);
        var config = factory.Services.GetRequiredService<IConfiguration>();
        try
        {
            config["Auth:ApprovedUserIds:0"] = "";
            (await client.GetAsync("/api/properties")).StatusCode.Should().Be(HttpStatusCode.Forbidden);
            (await client.GetAsync(image)).StatusCode.Should().Be(HttpStatusCode.Forbidden);
        }
        finally { config["Auth:ApprovedUserIds:0"] = TestJwtIssuer.Member; }
    }

    [Fact]
    public async Task SigningKeyRotation_CanLoadNewJwks()
    {
        using var original = factory.AuthenticatedClient();
        (await original.GetAsync("/api/properties")).StatusCode.Should().Be(HttpStatusCode.OK);
        factory.Issuer.Rotate();
        var options = factory.Services.GetRequiredService<IOptionsMonitor<JwtBearerOptions>>().Get("Bearer");
        // A fresh standard metadata manager represents expiry of the framework metadata cache.
        options.ConfigurationManager = new Microsoft.IdentityModel.Protocols.ConfigurationManager<Microsoft.IdentityModel.Protocols.OpenIdConnect.OpenIdConnectConfiguration>(
            TestJwtIssuer.Authority + "/.well-known/openid-configuration",
            new Microsoft.IdentityModel.Protocols.OpenIdConnect.OpenIdConnectConfigurationRetriever(),
            new Microsoft.IdentityModel.Protocols.HttpDocumentRetriever(new HttpClient(factory.Issuer, disposeHandler: false)));
        using var rotated = factory.AuthenticatedClient();
        (await rotated.GetAsync("/api/properties")).StatusCode.Should().Be(HttpStatusCode.OK);
        await factory.Issuer.JwksRead.Task.WaitAsync(TimeSpan.FromSeconds(5));
    }
}
