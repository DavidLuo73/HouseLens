using HouseLens.Api.Endpoints;
using HouseLens.Application.Crawling;
using HouseLens.Infrastructure;
using HouseLens.Infrastructure.Crawling;
using HouseLens.Infrastructure.Crawling.Scrapers;
using HouseLens.Infrastructure.Persistence;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddInfrastructure(builder.Configuration);

// Crawl services（供 /api/admin/trigger-crawl 手動觸發使用）
builder.Services.AddSingleton<HttpFetcher>();
builder.Services.AddScoped<ISourceScraper, F591Scraper>();
builder.Services.AddScoped<ISourceScraper, SinyiScraper>();
builder.Services.AddScoped<CrawlOrchestrator>();

builder.Services.AddCors(options =>
{
    options.AddDefaultPolicy(policy =>
        policy.WithOrigins("http://localhost:5173", "http://localhost:4173")
              .AllowAnyHeader()
              .AllowAnyMethod());
});

builder.Services.AddProblemDetails();

var app = builder.Build();

app.UseExceptionHandler();
app.UseCors();

// Database seed on startup (skip in test environment)
if (!app.Environment.IsEnvironment("Testing"))
{
    using var scope = app.Services.CreateScope();
    await SeedData.SeedAsync(scope.ServiceProvider.GetRequiredService<AppDbContext>());
}

app.MapGet("/health", () => Results.Ok(new { status = "ok" }));
app.MapPropertiesEndpoints();
app.MapCrawlRunEndpoints();
app.MapAnalyticsEndpoints();
app.MapConfigEndpoints();
app.MapAdminEndpoints();

app.Run();

// Make the implicit Program class visible for integration tests
public partial class Program { }
