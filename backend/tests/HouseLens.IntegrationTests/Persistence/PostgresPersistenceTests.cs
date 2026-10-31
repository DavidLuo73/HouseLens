using FluentAssertions;
using HouseLens.Domain.Entities;
using HouseLens.Infrastructure.Persistence;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Npgsql;

namespace HouseLens.IntegrationTests.Persistence;

public class PostgresPersistenceTests(TestWebApplicationFactory factory)
    : IClassFixture<TestWebApplicationFactory>
{
    [Fact]
    public async Task ConcurrentSeeding_CreatesDefaultsOnceAndPreservesExistingSettings()
    {
        using var firstScope = factory.Services.CreateScope();
        using var secondScope = factory.Services.CreateScope();
        var first = firstScope.ServiceProvider.GetRequiredService<AppDbContext>();
        var second = secondScope.ServiceProvider.GetRequiredService<AppDbContext>();

        await Task.WhenAll(SeedData.SeedAsync(first), SeedData.SeedAsync(second));

        var district = await first.DistrictConfigs.SingleAsync(d => d.City == "新北市" && d.District == "中和區");
        district.MaxTotalPrice = 1234.56m;
        await first.SaveChangesAsync();

        using var repeatScope = factory.Services.CreateScope();
        var repeated = repeatScope.ServiceProvider.GetRequiredService<AppDbContext>();
        await SeedData.SeedAsync(repeated);

        (await repeated.DistrictConfigs.CountAsync()).Should().Be(8);
        (await repeated.PlatformFilterConfigs.CountAsync()).Should().Be(3);
        (await repeated.TrackingCriteria.CountAsync()).Should().Be(1);
        (await repeated.ScoringConfigs.CountAsync()).Should().Be(1);
        (await repeated.DistrictConfigs.SingleAsync(d => d.Id == district.Id))
            .MaxTotalPrice.Should().Be(1234.56m);
    }

    [Fact]
    public async Task Database_UsesPostgresWithAppliedMigrations()
    {
        using var scope = factory.Services.CreateScope();
        var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();

        db.Database.ProviderName.Should().Be("Npgsql.EntityFrameworkCore.PostgreSQL");
        (await db.Database.GetAppliedMigrationsAsync()).Should().NotBeEmpty();
        (await db.Database.GetPendingMigrationsAsync()).Should().BeEmpty();
    }

    [Fact]
    public async Task SavedProperty_PreservesUtcDecimalAndGuidAcrossConnections()
    {
        var property = new Property
        {
            City = "新北市",
            District = "中和區",
            AreaPing = 31.25m,
            CurrentTotalPrice = 987.65m,
            CurrentUnitPrice = 31.60m,
            Score = 0.7654m,
            HasParking = true,
            FirstSeenAt = new DateTime(2026, 10, 31, 1, 2, 3, DateTimeKind.Utc),
        };
        using (var writeScope = factory.Services.CreateScope())
        {
            var db = writeScope.ServiceProvider.GetRequiredService<AppDbContext>();
            db.Properties.Add(property);
            await db.SaveChangesAsync();
        }

        using var readScope = factory.Services.CreateScope();
        var reader = readScope.ServiceProvider.GetRequiredService<AppDbContext>();
        var stored = await reader.Properties.SingleAsync(p => p.Id == property.Id);

        stored.CurrentTotalPrice.Should().Be(987.65m);
        stored.CurrentUnitPrice.Should().Be(31.60m);
        stored.AreaPing.Should().Be(31.25m);
        stored.Score.Should().Be(0.7654m);
        stored.HasParking.Should().BeTrue();
        stored.FirstSeenAt.Should().Be(property.FirstSeenAt);
        stored.FirstSeenAt.Kind.Should().Be(DateTimeKind.Utc);
    }

    [Fact]
    public async Task Listing_RejectsMissingProperty()
    {
        using var scope = factory.Services.CreateScope();
        var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
        db.Listings.Add(new Listing
        {
            PropertyId = Guid.NewGuid(),
            SourceListingKey = Guid.NewGuid().ToString(),
            Url = "https://example.com/missing-property",
        });

        var save = () => db.SaveChangesAsync();
        var failure = await save.Should().ThrowAsync<DbUpdateException>();
        failure.Which.InnerException.Should().BeOfType<PostgresException>()
            .Which.SqlState.Should().Be(PostgresErrorCodes.ForeignKeyViolation);
    }

    [Fact]
    public async Task Listing_RejectsDuplicateSourceKey()
    {
        using var scope = factory.Services.CreateScope();
        var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
        var property = new Property { City = "新北市", District = "中和區" };
        var sourceKey = Guid.NewGuid().ToString();
        db.Properties.Add(property);
        db.Listings.AddRange(
            new Listing { PropertyId = property.Id, SourceListingKey = sourceKey, Url = "https://example.com/one" },
            new Listing { PropertyId = property.Id, SourceListingKey = sourceKey, Url = "https://example.com/two" });

        var save = () => db.SaveChangesAsync();
        var failure = await save.Should().ThrowAsync<DbUpdateException>();
        failure.Which.InnerException.Should().BeOfType<PostgresException>()
            .Which.SqlState.Should().Be(PostgresErrorCodes.UniqueViolation);
    }
}
