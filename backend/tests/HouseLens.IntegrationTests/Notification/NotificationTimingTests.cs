using HouseLens.Application.Notification;
using HouseLens.Domain.Entities;
using HouseLens.Domain.Enums;
using HouseLens.Infrastructure.Notification;
using HouseLens.Infrastructure.Persistence;
using FluentAssertions;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging.Abstractions;
using System.Diagnostics;

namespace HouseLens.IntegrationTests.Notification;

/// <summary>
/// SC-007: 通知服務在模擬資料集下應於合理時間完成，確保排程後 10 分鐘內可送達。
/// 遠端 PostgreSQL 的查詢與通知組裝分配 1 分鐘，保留 9 分鐘給爬蟲與實際 LINE 傳送。
/// </summary>
public class NotificationTimingTests : IAsyncLifetime
{
    private readonly PostgresTestDatabase _database = new();

    private AppDbContext OpenDb() => _database.CreateDbContext();

    public async Task InitializeAsync()
    {
        await _database.InitializeAsync();
        await using var db = OpenDb();

        // 植入追蹤條件（供 GetTrackingDistrictsAsync 使用）
        db.TrackingCriteria.Add(new TrackingCriteria
        {
            Districts = """["中和區","永和區","板橋區","新店區","中壢區"]""",
            MaxTotalPrice = 800m
        });
        await db.SaveChangesAsync();
    }

    public Task DisposeAsync() => _database.DisposeAsync().AsTask();

    private async Task SeedBigDropsAsync(Guid crawlRunId, int count)
    {
        await using var db = OpenDb();
        db.CrawlRuns.Add(new CrawlRun { Id = crawlRunId, Status = RunStatus.Completed });

        for (int i = 0; i < count; i++)
        {
            var property = new Property
            {
                City = "新北市",
                District = i % 2 == 0 ? "中和區" : "永和區",
                Address = $"測試路{i + 1}號",
                CurrentTotalPrice = 700m + i,
                Score = 0.75m
            };
            db.Properties.Add(property);

            db.PriceHistoryEntries.Add(new PriceHistoryEntry
            {
                PropertyId = property.Id,
                CrawlRunId = crawlRunId,
                TotalPrice = 700m + i,
                ChangePercent = -0.08m,
                IsBigDrop = true,
                ChangeFlag = PriceChangeFlag.Decreased
            });
        }
        await db.SaveChangesAsync();
    }

    [Fact]
    public async Task SendDailyNotifications_With50BigDropsOnPostgres_CompletesWithinOneMinute()
    {
        // Arrange
        var runId = Guid.NewGuid();
        await SeedBigDropsAsync(runId, 50);

        await using var db = OpenDb();
        var repo = new NotificationRepository(db);

        // 排除 LINE 傳送，但保留遠端資料庫查詢與寫入的網路成本。
        var fakeClient = new InstantLineClient();
        var service = new NotificationService(fakeClient, repo, NullLogger<NotificationService>.Instance);

        // 排除連線建立成本，量測通知作業本身。
        await db.Database.OpenConnectionAsync();
        var sw = Stopwatch.StartNew();
        await service.SendDailyNotificationsAsync(runId);
        sw.Stop();

        sw.Elapsed.Should().BeLessThan(TimeSpan.FromMinutes(1),
            "遠端 PostgreSQL 通知作業須保留 SC-007 十分鐘預算中的九分鐘給爬蟲與實際 LINE 傳送");

        var logs = await db.NotificationLogs.Where(log => log.CrawlRunId == runId).ToListAsync();
        logs.Should().HaveCount(2).And.OnlyContain(log => log.Success);
        logs.Select(log => log.Type).Should().BeEquivalentTo(["BigDrop", "Top5"]);
    }
}

file sealed class InstantLineClient : ILineMessagingClient
{
    public Task<bool> PushMessageAsync(string text, CancellationToken ct = default)
        => Task.FromResult(true);
}
