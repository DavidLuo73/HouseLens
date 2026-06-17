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

// 圖片 proxy：快取上限 50 MB，防盜鏈繞過用
builder.Services.AddMemoryCache(opts => opts.SizeLimit = 50 * 1024 * 1024);
builder.Services.AddHttpClient("ImageProxy", client =>
{
    client.DefaultRequestHeaders.Add("Referer", "https://www.sinyi.com.tw/");
    client.DefaultRequestHeaders.Add("User-Agent",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
    client.Timeout = TimeSpan.FromSeconds(10);
})
.ConfigurePrimaryHttpMessageHandler(() => new SocketsHttpHandler
{
    AllowAutoRedirect = false,  // 防止 SSRF via redirect 繞過 host 白名單
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
app.MapProxyEndpoints();

app.Run();

// Make the implicit Program class visible for integration tests
public partial class Program { }
