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
/// 測試量測 SendDailyNotificationsAsync 在 50 筆大降價資料下的執行時間。
/// </summary>
public class NotificationTimingTests : IAsyncLifetime
{
    private readonly string _dbPath =
        Path.Combine(Path.GetTempPath(), $"timing_test_{Guid.NewGuid():N}.db");

    private AppDbContext OpenDb()
    {
        var opts = new DbContextOptionsBuilder<AppDbContext>()
            .UseSqlite($"Data Source={_dbPath}")
            .Options;
        return new AppDbContext(opts);
    }

    public async Task InitializeAsync()
    {
        await using var db = OpenDb();
        await db.Database.EnsureCreatedAsync();

        // 植入追蹤條件（供 GetTrackingDistrictsAsync 使用）
        db.TrackingCriteria.Add(new TrackingCriteria
        {
            Districts = """["中和區","永和區","板橋區","新店區","中壢區"]""",
            MaxTotalPrice = 800m
        });
        await db.SaveChangesAsync();
    }

    public Task DisposeAsync()
    {
        try { if (File.Exists(_dbPath)) File.Delete(_dbPath); } catch { /* best effort */ }
        return Task.CompletedTask;
    }

    private async Task SeedBigDropsAsync(Guid crawlRunId, int count)
    {
        await using var db = OpenDb();
        db.CrawlRuns.Add(new CrawlRun { Id = crawlRunId, Status = RunStatus.Completed });
        await db.SaveChangesAsync();

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
            await db.SaveChangesAsync();

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
    public async Task SendDailyNotifications_With50BigDrops_CompletesWithin2Seconds()
    {
        // Arrange
        var runId = Guid.NewGuid();
        await SeedBigDropsAsync(runId, 50);

        await using var db = OpenDb();
        var repo = new NotificationRepository(db);

        // 使用立即回傳 true 的假 LINE 客戶端（排除網路延遲）
        var fakeClient = new InstantLineClient();
        var service = new NotificationService(fakeClient, repo, NullLogger<NotificationService>.Instance);

        // Act
        var sw = Stopwatch.StartNew();
        await service.SendDailyNotificationsAsync(runId);
        sw.Stop();

        // Assert：純資料庫查詢 + 訊息建構，應遠低於 2 秒
        // 10 分鐘的 SC-007 預算主要由爬蟲消耗；通知本身應在數秒內完成
        sw.Elapsed.Should().BeLessThan(TimeSpan.FromSeconds(2),
            "通知服務本身（不含網路）應在 2 秒內完成，以確保留足夠緩衝讓整體流程在 10 分鐘內送達");
    }
}

file sealed class InstantLineClient : ILineMessagingClient
{
    public Task<bool> PushMessageAsync(string text, CancellationToken ct = default)
        => Task.FromResult(true);
}
