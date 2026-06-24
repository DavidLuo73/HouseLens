using HouseLens.Application.Crawling;
using HouseLens.Infrastructure;
using HouseLens.Infrastructure.Crawling;
using HouseLens.Infrastructure.Crawling.Scrapers;
using HouseLens.Worker;
using Quartz;

var builder = Host.CreateApplicationBuilder(args);

builder.Services.AddInfrastructure(builder.Configuration);

// Register crawl infrastructure
builder.Services.AddSingleton<HttpFetcher>();
builder.Services.AddScoped<ICrawlRepository, CrawlRepository>();
builder.Services.AddScoped<ISourceScraper, F591Scraper>();
builder.Services.AddScoped<ISourceScraper, SinyiScraper>();
builder.Services.AddScoped<ISourceScraper, YungchingScraper>();
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
host.Run();
