using FluentAssertions;

namespace HouseLens.IntegrationTests.Api;

public class ApiStartupTests
{
    [Theory]
    [InlineData("Api:Mode", "unknown")]
    [InlineData("Auth:Authority", "")]
    [InlineData("Auth:Authority", "http://auth.example.test/auth/v1")]
    [InlineData("Auth:Audience", "")]
    [InlineData("Auth:ApprovedUserIds:0", "not-a-uuid")]
    public void InvalidPublicConfiguration_FailsClosed(string key, string value)
    {
        using var factory = new PublicApiWebApplicationFactory();
        factory.Overrides[key] = value;
        Action start = () => factory.CreateClient();
        start.Should().Throw<InvalidOperationException>();
    }

    [Theory]
    [InlineData("urls", "http://0.0.0.0:5000")]
    [InlineData("urls", "http://localhost.evil:5000")]
    [InlineData("Kestrel:Endpoints:Public:Url", "http://*:5000")]
    [InlineData("HTTP_PORTS", "5000")]
    public void PrivateManagement_RejectsNonLoopbackBindings(string key, string value)
    {
        using var factory = new PublicApiWebApplicationFactory();
        factory.Overrides["Api:Mode"] = "PrivateManagement";
        factory.Overrides[key] = value;
        Action start = () => factory.CreateClient();
        start.Should().Throw<InvalidOperationException>();
    }
}
