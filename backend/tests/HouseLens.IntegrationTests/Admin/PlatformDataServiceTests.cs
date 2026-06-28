using FluentAssertions;
using HouseLens.Domain.Entities;
using HouseLens.Domain.Enums;
using HouseLens.Infrastructure.Crawling;
using HouseLens.Infrastructure.Persistence;
using Microsoft.EntityFrameworkCore;

namespace HouseLens.IntegrationTests.Admin;

public class PlatformDataServiceTests : IDisposable
{
    private readonly string _dbPath = Path.Combine(Path.GetTempPath(), $"platformtest_{Guid.NewGuid():N}.db");

    private AppDbContext CreateDb()
    {
        var opts = new DbContextOptionsBuilder<AppDbContext>()
            .UseSqlite($"Data Source={_dbPath}")
            .Options;
        var db = new AppDbContext(opts);
        db.Database.EnsureCreated();
        return db;
    }

    public void Dispose()
    {
        try { if (File.Exists(_dbPath)) File.Delete(_dbPath); } catch { /* best effort */ }
    }

    private static Property MakeProperty(decimal price = 500m) => new()
    {
        City = "新北市",
        District = "中和區",
        Address = "某路1號",
        CurrentTotalPrice = price,
        CurrentUnitPrice = price / 30m,
        Status = PropertyStatus.Active,
    };

    private static Listing MakeListing(Guid propertyId, SourceSite site, string key = "key1") => new()
    {
        PropertyId = propertyId,
        SourceSite = site,
        SourceListingKey = key,
        Url = $"https://example.com/{key}",
        LatestSourcePrice = 500m,
        IsActive = true,
    };

    private static CrawlRun MakeCrawlRun() => new()
    {
        StartedAt = DateTime.UtcNow,
        FinishedAt = DateTime.UtcNow,
        Status = RunStatus.Completed,
    };

    private static PriceHistoryEntry MakeHistory(Guid propertyId, Guid crawlRunId, SourceSite site) => new()
    {
        PropertyId = propertyId,
        CrawlRunId = crawlRunId,
        TotalPrice = 500m,
        SourceSite = site,
    };

    // ── GetStats ──────────────────────────────────────────────────────────────

    [Fact]
    public async Task GetStats_WithOneF591Listing_ReturnsCorrectCounts()
    {
        // Arrange
        await using var db = CreateDb();
        var property = MakeProperty();
        db.Properties.Add(property);
        db.Listings.Add(MakeListing(property.Id, SourceSite.F591));
        await db.SaveChangesAsync();

        var svc = new PlatformDataService(db);

        // Act
        var stats = await svc.GetStatsAsync();

        // Assert
        var f591Stats = stats.Single(s => s.SourceSite == SourceSite.F591);
        f591Stats.ListingCount.Should().Be(1);
        f591Stats.PropertyCount.Should().Be(1);
    }

    [Fact]
    public async Task GetStats_WithSharedProperty_CountsEachPlatformSeparately()
    {
        // Arrange: one property, two listings from different platforms
        await using var db = CreateDb();
        var property = MakeProperty();
        db.Properties.Add(property);
        db.Listings.Add(MakeListing(property.Id, SourceSite.F591, "key-591"));
        db.Listings.Add(MakeListing(property.Id, SourceSite.Yungching, "key-yc"));
        await db.SaveChangesAsync();

        var svc = new PlatformDataService(db);

        // Act
        var stats = await svc.GetStatsAsync();

        // Assert
        stats.Single(s => s.SourceSite == SourceSite.F591).ListingCount.Should().Be(1);
        stats.Single(s => s.SourceSite == SourceSite.Yungching).ListingCount.Should().Be(1);
        // PropertyCount: this property is referenced by both platforms
        stats.Single(s => s.SourceSite == SourceSite.F591).PropertyCount.Should().Be(1);
        stats.Single(s => s.SourceSite == SourceSite.Yungching).PropertyCount.Should().Be(1);
    }

    // ── PurgePlatform: orphan property ────────────────────────────────────────

    [Fact]
    public async Task PurgePlatform_OnlyPlatformListing_DeletesListingAndProperty()
    {
        // Arrange: property with only one F591 listing
        await using var db = CreateDb();
        var property = MakeProperty();
        db.Properties.Add(property);
        db.Listings.Add(MakeListing(property.Id, SourceSite.F591));
        await db.SaveChangesAsync();

        var svc = new PlatformDataService(db);

        // Act
        var result = await svc.PurgePlatformAsync(SourceSite.F591);

        // Assert
        result.ListingsDeleted.Should().Be(1);
        result.PropertiesDeleted.Should().Be(1);
        (await db.Listings.CountAsync()).Should().Be(0);
        (await db.Properties.CountAsync()).Should().Be(0);
    }

    [Fact]
    public async Task PurgePlatform_OnlyTargetPlatformAffected_OtherPlatformListingUntouched()
    {
        // Arrange: two separate properties, one F591 one Yungching
        await using var db = CreateDb();

        var prop591 = MakeProperty();
        var propYc = MakeProperty(450m);
        db.Properties.AddRange(prop591, propYc);
        db.Listings.Add(MakeListing(prop591.Id, SourceSite.F591, "key-591"));
        db.Listings.Add(MakeListing(propYc.Id, SourceSite.Yungching, "key-yc"));
        await db.SaveChangesAsync();

        var svc = new PlatformDataService(db);

        // Act
        await svc.PurgePlatformAsync(SourceSite.F591);

        // Assert: Yungching listing and property untouched
        (await db.Listings.Where(l => l.SourceSite == SourceSite.Yungching).CountAsync()).Should().Be(1);
        (await db.Properties.CountAsync()).Should().Be(1);
    }

    // ── PurgePlatform: shared property ────────────────────────────────────────

    [Fact]
    public async Task PurgePlatform_SharedProperty_KeepsPropertyAndOtherPlatformListing()
    {
        // Arrange: one property with two listings
        await using var db = CreateDb();
        var property = MakeProperty();
        db.Properties.Add(property);
        db.Listings.Add(MakeListing(property.Id, SourceSite.F591, "key-591"));
        db.Listings.Add(MakeListing(property.Id, SourceSite.Yungching, "key-yc"));
        await db.SaveChangesAsync();

        var svc = new PlatformDataService(db);

        // Act
        var result = await svc.PurgePlatformAsync(SourceSite.F591);

        // Assert
        result.ListingsDeleted.Should().Be(1);
        result.PropertiesDeleted.Should().Be(0);
        (await db.Properties.CountAsync()).Should().Be(1);
        (await db.Listings.CountAsync()).Should().Be(1);
        (await db.Listings.SingleAsync()).SourceSite.Should().Be(SourceSite.Yungching);
    }

    [Fact]
    public async Task PurgePlatform_SharedProperty_RecalculatesPriceFromRemainingListings()
    {
        // Arrange: shared property with F591 price 600, Yungching price 450
        await using var db = CreateDb();
        var property = MakeProperty(600m);
        db.Properties.Add(property);
        db.Listings.Add(new Listing
        {
            PropertyId = property.Id,
            SourceSite = SourceSite.F591,
            SourceListingKey = "key-591",
            Url = "https://example.com/591",
            LatestSourcePrice = 600m,
            IsActive = true,
        });
        db.Listings.Add(new Listing
        {
            PropertyId = property.Id,
            SourceSite = SourceSite.Yungching,
            SourceListingKey = "key-yc",
            Url = "https://example.com/yc",
            LatestSourcePrice = 450m,
            IsActive = true,
        });
        await db.SaveChangesAsync();

        var svc = new PlatformDataService(db);

        // Act
        await svc.PurgePlatformAsync(SourceSite.F591);

        // Assert: price updated to Yungching's price
        var updated = await db.Properties.SingleAsync();
        updated.CurrentTotalPrice.Should().Be(450m);
    }

    // ── PurgePlatform: price history ──────────────────────────────────────────

    [Fact]
    public async Task PurgePlatform_DeletesPriceHistoryForTargetPlatform()
    {
        // Arrange
        await using var db = CreateDb();
        var property = MakeProperty();
        var crawlRun = MakeCrawlRun();
        db.Properties.Add(property);
        db.CrawlRuns.Add(crawlRun);
        db.Listings.Add(MakeListing(property.Id, SourceSite.F591));
        await db.SaveChangesAsync();
        db.PriceHistoryEntries.Add(MakeHistory(property.Id, crawlRun.Id, SourceSite.F591));
        await db.SaveChangesAsync();

        var svc = new PlatformDataService(db);

        // Act
        var result = await svc.PurgePlatformAsync(SourceSite.F591);

        // Assert
        result.PriceHistoryDeleted.Should().Be(1);
        (await db.PriceHistoryEntries.CountAsync()).Should().Be(0);
    }

    [Fact]
    public async Task PurgePlatform_SharedProperty_KeepsPriceHistoryOfOtherPlatform()
    {
        // Arrange: same property, same crawl run, two history entries (different SourceSite)
        await using var db = CreateDb();
        var property = MakeProperty();
        var crawlRun = MakeCrawlRun();
        db.Properties.Add(property);
        db.CrawlRuns.Add(crawlRun);
        db.Listings.Add(MakeListing(property.Id, SourceSite.F591, "key-591"));
        db.Listings.Add(MakeListing(property.Id, SourceSite.Yungching, "key-yc"));
        await db.SaveChangesAsync();
        db.PriceHistoryEntries.Add(MakeHistory(property.Id, crawlRun.Id, SourceSite.F591));
        db.PriceHistoryEntries.Add(MakeHistory(property.Id, crawlRun.Id, SourceSite.Yungching));
        await db.SaveChangesAsync();

        var svc = new PlatformDataService(db);

        // Act
        await svc.PurgePlatformAsync(SourceSite.F591);

        // Assert: Yungching history kept
        (await db.PriceHistoryEntries.CountAsync()).Should().Be(1);
        (await db.PriceHistoryEntries.SingleAsync()).SourceSite.Should().Be(SourceSite.Yungching);
    }

    // ── PurgePlatform: SourceRunResult ────────────────────────────────────────

    [Fact]
    public async Task PurgePlatform_DeletesSourceRunResultsForTargetPlatform()
    {
        // Arrange
        await using var db = CreateDb();
        var crawlRun = MakeCrawlRun();
        db.CrawlRuns.Add(crawlRun);
        await db.SaveChangesAsync();
        db.SourceRunResults.Add(new SourceRunResult
        {
            CrawlRunId = crawlRun.Id,
            SourceSite = SourceSite.F591,
            Success = true,
            FetchedCount = 10,
        });
        await db.SaveChangesAsync();

        var svc = new PlatformDataService(db);

        // Act
        var result = await svc.PurgePlatformAsync(SourceSite.F591);

        // Assert
        result.SourceRunResultsDeleted.Should().Be(1);
        (await db.SourceRunResults.CountAsync()).Should().Be(0);
    }

    [Fact]
    public async Task PurgePlatform_KeepsSourceRunResultsOfOtherPlatforms()
    {
        // Arrange: two SourceRunResults, one per platform
        await using var db = CreateDb();
        var crawlRun = MakeCrawlRun();
        db.CrawlRuns.Add(crawlRun);
        await db.SaveChangesAsync();
        db.SourceRunResults.Add(new SourceRunResult { CrawlRunId = crawlRun.Id, SourceSite = SourceSite.F591, Success = true });
        db.SourceRunResults.Add(new SourceRunResult { CrawlRunId = crawlRun.Id, SourceSite = SourceSite.Yungching, Success = true });
        await db.SaveChangesAsync();

        var svc = new PlatformDataService(db);

        // Act
        await svc.PurgePlatformAsync(SourceSite.F591);

        // Assert
        (await db.SourceRunResults.CountAsync()).Should().Be(1);
        (await db.SourceRunResults.SingleAsync()).SourceSite.Should().Be(SourceSite.Yungching);
    }
}
