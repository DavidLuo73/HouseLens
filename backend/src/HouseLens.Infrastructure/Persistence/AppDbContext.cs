using HouseLens.Domain.Entities;
using Microsoft.EntityFrameworkCore;

namespace HouseLens.Infrastructure.Persistence;

public class AppDbContext(DbContextOptions<AppDbContext> options) : DbContext(options)
{
    public DbSet<Property> Properties => Set<Property>();
    public DbSet<Listing> Listings => Set<Listing>();
    public DbSet<PriceHistoryEntry> PriceHistoryEntries => Set<PriceHistoryEntry>();
    public DbSet<CrawlRun> CrawlRuns => Set<CrawlRun>();
    public DbSet<SourceRunResult> SourceRunResults => Set<SourceRunResult>();
    public DbSet<TrackingCriteria> TrackingCriteria => Set<TrackingCriteria>();
    public DbSet<ScoringConfig> ScoringConfigs => Set<ScoringConfig>();
    public DbSet<PropertyScore> PropertyScores => Set<PropertyScore>();
    public DbSet<NotificationLog> NotificationLogs => Set<NotificationLog>();
    public DbSet<DistrictConfig> DistrictConfigs => Set<DistrictConfig>();
    public DbSet<PlatformFilterConfig> PlatformFilterConfigs => Set<PlatformFilterConfig>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        base.OnModelCreating(modelBuilder);

        modelBuilder.Entity<Property>(e =>
        {
            e.HasKey(p => p.Id);
            e.Property(p => p.City).IsRequired().HasMaxLength(20);
            e.Property(p => p.District).IsRequired().HasMaxLength(20);
            e.Property(p => p.Address).HasMaxLength(200);
            e.Property(p => p.AreaPing).HasPrecision(10, 2);
            e.Property(p => p.Floor).HasMaxLength(20);
            e.Property(p => p.CurrentTotalPrice).HasPrecision(12, 2);
            e.Property(p => p.CurrentUnitPrice).HasPrecision(12, 2);
            e.Property(p => p.Score).HasPrecision(5, 4);
            e.HasIndex(p => new { p.District, p.Status });
            e.HasIndex(p => p.Status);
        });

        modelBuilder.Entity<Listing>(e =>
        {
            e.HasKey(l => l.Id);
            e.Property(l => l.SourceListingKey).IsRequired().HasMaxLength(100);
            e.Property(l => l.Title).HasMaxLength(200);
            e.Property(l => l.Url).IsRequired().HasMaxLength(500);
            e.Property(l => l.ImageUrl).HasMaxLength(1000);
            e.Property(l => l.LatestSourcePrice).HasPrecision(12, 2);
            e.HasIndex(l => new { l.SourceSite, l.SourceListingKey }).IsUnique();
            e.HasOne(l => l.Property)
                .WithMany(p => p.Listings)
                .HasForeignKey(l => l.PropertyId)
                .OnDelete(DeleteBehavior.Cascade);
        });

        modelBuilder.Entity<PriceHistoryEntry>(e =>
        {
            e.HasKey(p => p.Id);
            e.Property(p => p.TotalPrice).HasPrecision(12, 2);
            e.Property(p => p.UnitPrice).HasPrecision(12, 2);
            e.Property(p => p.ChangePercent).HasPrecision(8, 4);
            e.HasIndex(p => new { p.PropertyId, p.CrawlRunId, p.SourceSite }).IsUnique();
            e.HasOne(p => p.Property)
                .WithMany(pr => pr.PriceHistory)
                .HasForeignKey(p => p.PropertyId)
                .OnDelete(DeleteBehavior.Cascade);
            e.HasOne(p => p.CrawlRun)
                .WithMany(c => c.PriceHistoryEntries)
                .HasForeignKey(p => p.CrawlRunId)
                .OnDelete(DeleteBehavior.Restrict);
        });

        modelBuilder.Entity<CrawlRun>(e =>
        {
            e.HasKey(c => c.Id);
            e.HasIndex(c => c.StartedAt);
        });

        modelBuilder.Entity<SourceRunResult>(e =>
        {
            e.HasKey(s => s.Id);
            e.Property(s => s.ErrorMessage).HasMaxLength(1000);
            e.HasOne(s => s.CrawlRun)
                .WithMany(c => c.SourceResults)
                .HasForeignKey(s => s.CrawlRunId)
                .OnDelete(DeleteBehavior.Cascade);
        });

        modelBuilder.Entity<TrackingCriteria>(e =>
        {
            e.HasKey(t => t.Id);
            e.Property(t => t.MaxTotalPrice).HasPrecision(12, 2);
        });

        modelBuilder.Entity<ScoringConfig>(e =>
        {
            e.HasKey(s => s.Id);
            e.Property(s => s.WeightUnitPrice).HasPrecision(5, 4);
            e.Property(s => s.WeightAge).HasPrecision(5, 4);
            e.Property(s => s.WeightParking).HasPrecision(5, 4);
            e.Property(s => s.WeightLocation).HasPrecision(5, 4);
            e.Property(s => s.BigDropPercent).HasPrecision(5, 4);
            e.Property(s => s.BigDropAmount).HasPrecision(12, 2);
        });

        modelBuilder.Entity<PropertyScore>(e =>
        {
            e.HasKey(s => s.Id);
            e.Property(s => s.Score).HasPrecision(5, 4);
            e.Property(s => s.UnitPriceScore).HasPrecision(5, 4);
            e.Property(s => s.AgeScore).HasPrecision(5, 4);
            e.Property(s => s.ParkingScore).HasPrecision(5, 4);
            e.Property(s => s.LocationScore).HasPrecision(5, 4);
            e.HasOne(s => s.Property)
                .WithMany(p => p.Scores)
                .HasForeignKey(s => s.PropertyId)
                .OnDelete(DeleteBehavior.Cascade);
        });

        modelBuilder.Entity<NotificationLog>(e =>
        {
            e.HasKey(n => n.Id);
            e.Property(n => n.Type).HasMaxLength(50);
            e.Property(n => n.ErrorMessage).HasMaxLength(1000);
        });

        modelBuilder.Entity<DistrictConfig>(e =>
        {
            e.HasKey(d => d.Id);
            e.Property(d => d.City).IsRequired().HasMaxLength(20);
            e.Property(d => d.District).IsRequired().HasMaxLength(20);
            e.Property(d => d.MaxTotalPrice).HasPrecision(12, 2);
            e.HasIndex(d => new { d.City, d.District }).IsUnique();
        });

        modelBuilder.Entity<PlatformFilterConfig>(e =>
        {
            e.HasKey(p => p.Id);
            e.Property(p => p.MinSizePing).HasPrecision(10, 2);
            e.Property(p => p.Rooms).IsRequired().HasMaxLength(50);
            e.Property(p => p.TypeCodes).IsRequired().HasMaxLength(100);
            e.Property(p => p.UseCode).IsRequired().HasMaxLength(10);
            e.HasIndex(p => p.SourceSite).IsUnique();
        });
    }
}
