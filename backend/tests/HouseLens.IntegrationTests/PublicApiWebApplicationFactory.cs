using System.Net;
using System.Net.Http.Headers;
using HouseLens.IntegrationTests.Auth;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.Hosting;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;

namespace HouseLens.IntegrationTests;

public class PublicApiWebApplicationFactory : TestWebApplicationFactory
{
    public TestJwtIssuer Issuer { get; } = new();
    public Dictionary<string, string?> Overrides { get; } = new()
    {
        ["Api:Mode"] = "PublicReadOnly",
        ["Auth:Authority"] = TestJwtIssuer.Authority,
        ["Auth:Audience"] = "authenticated",
        ["Auth:ApprovedUserIds:0"] = TestJwtIssuer.Member,
        ["urls"] = "http://127.0.0.1:5000"
    };

    protected override Dictionary<string, string?> StartupSettings
    {
        get
        {
            var settings = base.StartupSettings;
            foreach (var entry in Overrides) settings[entry.Key] = entry.Value;
            return settings;
        }
    }

    protected override void ConfigureWebHost(IWebHostBuilder builder)
    {
        base.ConfigureWebHost(builder);
        builder.ConfigureAppConfiguration((_, config) => config.AddInMemoryCollection(Overrides));
        builder.ConfigureServices(services =>
        {
            services.Configure<JwtBearerOptions>(JwtBearerDefaults.AuthenticationScheme,
                options => options.BackchannelHttpHandler = Issuer);
            services.AddHttpClient("ImageProxy").ConfigurePrimaryHttpMessageHandler(() => new ImageHandler());
        });
    }

    public HttpClient AuthenticatedClient(string? token = null)
    {
        var client = CreateClient();
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", token ?? Issuer.Token());
        return client;
    }

    private sealed class ImageHandler : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken token)
        {
            var content = new ByteArrayContent([0xff, 0xd8, 0xff, 0xd9]);
            content.Headers.ContentType = new MediaTypeHeaderValue("image/jpeg");
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK) { Content = content });
        }
    }
}
