using HouseLens.Application.Notification;
using HouseLens.Domain.Entities;
using HouseLens.Domain.Enums;
using HouseLens.Infrastructure.Notification;
using HouseLens.Infrastructure.Persistence;
using FluentAssertions;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging.Abstractions;

namespace HouseLens.IntegrationTests.Notification;

file sealed class ThrowingLineClient : ILineMessagingClient
{
    public Task<bool> PushMessageAsync(string text, CancellationToken ct = default)
        => throw new HttpRequestException("Connection refused");
}

file sealed class FailingLineClient : ILineMessagingClient
{
    public Task<bool> PushMessageAsync(string text, CancellationToken ct = default)
        => Task.FromResult(false);
}

file sealed class SucceedingLineClient : ILineMessagingClient
{
    public Task<bool> PushMessageAsync(string text, CancellationToken ct = default)
        => Task.FromResult(true);
}

public class NotificationFailureTests : IAsyncLifetime
{
    private readonly string _dbPath =
        Path.Combine(Path.GetTempPath(), $"notif_test_{Guid.NewGuid():N}.db");

    private AppDbContext OpenDb()
    {
        var opts = new DbContextOptionsBuilder<AppDbContext>()
            .UseSqlite($"Data Source={_dbPath}")
            .Options;
        return new AppDbContext(opts);
    }

    private NotificationService BuildService(ILineMessagingClient client)
    {
        var db = OpenDb();
        var repo = new NotificationRepository(db);
        return new NotificationService(client, repo, NullLogger<NotificationService>.Instance);
    }

    public async Task InitializeAsync()
    {
        using var db = OpenDb();
        await db.Database.EnsureCreatedAsync();
    }

    public Task DisposeAsync()
    {
        try { if (File.Exists(_dbPath)) File.Delete(_dbPath); } catch { /* best effort */ }
        return Task.CompletedTask;
    }

    // --- helpers ---

    private async Task InsertCrawlRun(Guid runId)
    {
        await using var db = OpenDb();
        db.CrawlRuns.Add(new CrawlRun { Id = runId, Status = RunStatus.Completed });
        await db.SaveChangesAsync();
    }

    private async Task InsertBigDrop(Guid crawlRunId)
    {
        await using var db = OpenDb();
        var property = new Property
        {
            City = "新北市",
            District = "中和區",
            Address = "中和路1號",
            CurrentTotalPrice = 750m
        };
        db.Properties.Add(property);
        await db.SaveChangesAsync();

        db.PriceHistoryEntries.Add(new PriceHistoryEntry
        {
            PropertyId = property.Id,
            CrawlRunId = crawlRunId,
            TotalPrice = 750m,
            ChangePercent = -0.10m,
            IsBigDrop = true,
            ChangeFlag = PriceChangeFlag.Decreased
        });
        await db.SaveChangesAsync();
    }

    // --- tests ---

    [Fact]
    public async Task SendDailyNotifications_WhenLineClientThrows_DoesNotThrowAndSavesFailureLog()
    {
        var runId = Guid.NewGuid();
        await InsertCrawlRun(runId);
        await InsertBigDrop(runId);

        var service = BuildService(new ThrowingLineClient());

        // LINE 例外不應傳播出 NotificationService
        var act = async () => await service.SendDailyNotificationsAsync(runId);
        await act.Should().NotThrowAsync();

        // NotificationLog 仍應被寫入
        await using var db = OpenDb();
        var logs = await db.NotificationLogs
            .Where(l => l.CrawlRunId == runId)
            .ToListAsync();

        logs.Should().NotBeEmpty();
        logs.Should().AllSatisfy(l =>
        {
            l.Success.Should().BeFalse();
            l.ErrorMessage.Should().NotBeNullOrEmpty();
        });
    }

    [Fact]
    public async Task SendDailyNotifications_WhenLineReturnsFalse_LogsFailure()
    {
        var runId = Guid.NewGuid();
        await InsertCrawlRun(runId);
        await InsertBigDrop(runId);

        var service = BuildService(new FailingLineClient());
        await service.SendDailyNotificationsAsync(runId);

        await using var db = OpenDb();
        var bigDropLog = await db.NotificationLogs
            .FirstOrDefaultAsync(l => l.CrawlRunId == runId && l.Type == "BigDrop");

        bigDropLog.Should().NotBeNull();
        bigDropLog!.Success.Should().BeFalse();
    }

    [Fact]
    public async Task SendDailyNotifications_NoBigDrops_SkipsAndRecordsSuccess()
    {
        var runId = Guid.NewGuid();
        await InsertCrawlRun(runId);
        // 無大降價物件 → BuildBigDropMessage 回 null → 略過發送、仍記 Success=true

        var service = BuildService(new SucceedingLineClient());
        await service.SendDailyNotificationsAsync(runId);

        await using var db = OpenDb();
        var bigDropLog = await db.NotificationLogs
            .FirstOrDefaultAsync(l => l.CrawlRunId == runId && l.Type == "BigDrop");

        bigDropLog.Should().NotBeNull();
        bigDropLog!.Success.Should().BeTrue();
    }
}
