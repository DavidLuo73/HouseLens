using HouseLens.Api.Auth;
using HouseLens.Api.Endpoints;
using HouseLens.Application.Crawling;
using HouseLens.Infrastructure;
using HouseLens.Infrastructure.Crawling;
using HouseLens.Infrastructure.Crawling.Scrapers;
using HouseLens.Infrastructure.Persistence;

var builder = WebApplication.CreateBuilder(args);
builder.Configuration
    .AddJsonFile(Path.GetFullPath(Path.Combine(builder.Environment.ContentRootPath,
        "..", "..", "appsettings.Local.json")), optional: true)
    .AddEnvironmentVariables()
    .AddCommandLine(args);

var publicReadOnly = (builder.Configuration["Api:Mode"] ?? "PublicReadOnly") switch
{
    "PublicReadOnly" => true,
    "PrivateManagement" => false,
    _ => throw new InvalidOperationException("Api:Mode must be PublicReadOnly or PrivateManagement.")
};
builder.WebHost.UseUrls(ApiAuthentication.RequireLoopbackBindings(builder.Configuration));
builder.Services.AddInfrastructure(builder.Configuration);

if (publicReadOnly)
{
    builder.Services.AddPublicApiAuthentication(builder.Configuration);
}
else
{
    builder.Services.AddSingleton<HttpFetcher>();
    builder.Services.AddSingleton<PlaywrightFetcher>();
    builder.Services.AddSingleton<CrawlProgressState>();
    builder.Services.AddScoped<ISourceScraper, F591Scraper>();
    builder.Services.AddScoped<ISourceScraper, SinyiScraper>();
    builder.Services.AddScoped<ISourceScraper, YungchingScraper>();
    builder.Services.AddScoped<ISourceScraper, HBHousingScraper>();
    builder.Services.AddScoped<ISourceScraper, RakuyaScraper>();
    builder.Services.AddScoped<ISourceScraper, CtHouseScraper>();
    builder.Services.AddScoped<ISourceScraper, TwHouseScraper>();
    builder.Services.AddScoped<CrawlOrchestrator>();
    builder.Services.AddScoped<PlatformDataService>();
}

builder.Services.AddCors(options =>
{
    options.AddDefaultPolicy(policy =>
        policy.WithOrigins("http://localhost:5173", "http://localhost:4173")
              .AllowAnyHeader()
              .AllowAnyMethod());
});

builder.Services.AddMemoryCache(opts => opts.SizeLimit = 50 * 1024 * 1024);
builder.Services.AddHttpClient("ImageProxy", client =>
{
    client.DefaultRequestHeaders.Add("Referer", "https://www.sinyi.com.tw/");
    client.DefaultRequestHeaders.Add("User-Agent",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
    client.Timeout = TimeSpan.FromSeconds(10);
    client.MaxResponseContentBufferSize = 5 * 1024 * 1024;
})
.ConfigurePrimaryHttpMessageHandler(() => new SocketsHttpHandler { AllowAutoRedirect = false });

builder.Services.Configure<Microsoft.AspNetCore.Http.Json.JsonOptions>(opts =>
{
    opts.SerializerOptions.Converters.Add(new HouseLens.Api.UtcDateTimeConverter());
    opts.SerializerOptions.Converters.Add(new HouseLens.Api.UtcNullableDateTimeConverter());
});
builder.Services.AddProblemDetails();

var app = builder.Build();
app.UseExceptionHandler();
app.UseCors();
if (publicReadOnly)
{
    app.UseAuthentication();
    app.UseAuthorization();
}

// Public reads must not run migrations or seed shared data on startup.
if (!publicReadOnly && !app.Environment.IsEnvironment("Testing"))
{
    using var scope = app.Services.CreateScope();
    var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
    await SeedData.SeedAsync(db);
}

app.MapGet("/health", () => Results.Ok(new { status = "ok" })).AllowAnonymous();
IEndpointRouteBuilder reads = publicReadOnly
    ? app.MapGroup("").RequireAuthorization("ApprovedMember") : app;
reads.MapPropertiesEndpoints();
reads.MapCrawlRunEndpoints();
reads.MapAnalyticsEndpoints();
reads.MapProxyEndpoints();
if (publicReadOnly)
{
    reads.MapDistrictOptionsEndpoint();
}
else
{
    app.MapConfigEndpoints();
    app.MapAdminEndpoints();
    app.MapCrawlProgressEndpoints();
}
app.Run();

public partial class Program { }
