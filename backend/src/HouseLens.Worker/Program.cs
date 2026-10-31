using HouseLens.Application.Crawling;
using HouseLens.Infrastructure;
using HouseLens.Infrastructure.Crawling;
using HouseLens.Infrastructure.Crawling.Scrapers;
using HouseLens.Infrastructure.Persistence;
using HouseLens.Worker;
using Quartz;

var builder = Host.CreateApplicationBuilder(args);
builder.Configuration
    .AddJsonFile(Path.GetFullPath(Path.Combine(builder.Environment.ContentRootPath,
        "..", "..", "appsettings.Local.json")), optional: true)
    .AddEnvironmentVariables()
    .AddCommandLine(args);

builder.Services.AddInfrastructure(builder.Configuration);

// Register crawl infrastructure
builder.Services.AddSingleton<HttpFetcher>();
builder.Services.AddSingleton<PlaywrightFetcher>();
builder.Services.AddSingleton<CrawlProgressState>();
builder.Services.AddScoped<ICrawlRepository, CrawlRepository>();
builder.Services.AddScoped<ISourceScraper, F591Scraper>();
builder.Services.AddScoped<ISourceScraper, SinyiScraper>();
builder.Services.AddScoped<ISourceScraper, YungchingScraper>();
builder.Services.AddScoped<ISourceScraper, HBHousingScraper>();
builder.Services.AddScoped<ISourceScraper, RakuyaScraper>();
builder.Services.AddScoped<ISourceScraper, CtHouseScraper>();
builder.Services.AddScoped<ISourceScraper, TwHouseScraper>();
builder.Services.AddScoped<CrawlOrchestrator>();

// Quartz scheduler
var dailyCron = builder.Configuration["Schedule:DailyCron"] ?? "0 0 6 * * ?";

builder.Services.AddQuartz(q =>
{
    var jobKey = new JobKey("CrawlJob");
    q.AddJob<CrawlJob>(opts => opts.WithIdentity(jobKey));
    q.AddTrigger(opts => opts
        .ForJob(jobKey)
        .WithIdentity("CrawlJob-trigger")
        .WithCronSchedule(dailyCron));
});

builder.Services.AddQuartzHostedService(q => q.WaitForJobsToComplete = true);

var host = builder.Build();
using (var scope = host.Services.CreateScope())
    await SeedData.SeedAsync(scope.ServiceProvider.GetRequiredService<AppDbContext>());
await host.RunAsync();
