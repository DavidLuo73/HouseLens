using HouseLens.Infrastructure.Persistence;
using Microsoft.EntityFrameworkCore;

namespace HouseLens.Api.Endpoints;

public static class CrawlRunEndpoints
{
    public static IEndpointRouteBuilder MapCrawlRunEndpoints(this IEndpointRouteBuilder app)
    {
        app.MapGet("/api/crawl-runs/latest", GetLatest);
        return app;
    }

    private static async Task<IResult> GetLatest(AppDbContext db)
    {
        var run = await db.CrawlRuns
            .Include(r => r.SourceResults)
            .OrderByDescending(r => r.StartedAt)
            .FirstOrDefaultAsync();

        if (run is null)
            return Results.NotFound(new { error = new { code = "NOT_FOUND", message = "No crawl run found" } });

        return Results.Ok(new
        {
            run.Id,
            run.StartedAt,
            run.FinishedAt,
            Status = run.Status.ToString().ToLower(),
            run.NewCount,
            run.DelistedCount,
            run.BigDropCount,
            Sources = run.SourceResults.Select(s => new
            {
                SourceSite = s.SourceSite.ToString(),
                s.Success,
                s.FetchedCount,
                s.ErrorMessage
            })
        });
    }
}
