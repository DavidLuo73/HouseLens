using HouseLens.Infrastructure.Persistence;
using Microsoft.EntityFrameworkCore;
using Npgsql;

namespace HouseLens.IntegrationTests;

internal sealed class PostgresTestDatabase : IAsyncDisposable
{
    private readonly string _schema = $"hl_test_{Guid.NewGuid():N}";
    private bool _created;

    public string ConnectionString { get; }

    public PostgresTestDatabase()
    {
        var connectionString = Environment.GetEnvironmentVariable("HOUSELENS_TEST_CONNECTION");
        if (string.IsNullOrWhiteSpace(connectionString))
            throw new InvalidOperationException(
                "Set HOUSELENS_TEST_CONNECTION to a PostgreSQL connection string. Tests create and remove their own hl_test_<guid> schemas.");

        ConnectionString = new NpgsqlConnectionStringBuilder(connectionString)
        {
            SearchPath = _schema,
            Pooling = true,
            MinPoolSize = 0,
            MaxPoolSize = 2,
        }.ConnectionString;
    }

    public AppDbContext CreateDbContext() => new(
        new DbContextOptionsBuilder<AppDbContext>().UseNpgsql(ConnectionString).Options);

    public async Task InitializeAsync()
    {
        await using (var connection = new NpgsqlConnection(ConnectionString))
        {
            await connection.OpenAsync();
            await using var command = new NpgsqlCommand($"CREATE SCHEMA \"{_schema}\"", connection);
            await command.ExecuteNonQueryAsync();
            _created = true;
        }

        try
        {
            await using var db = CreateDbContext();
            await db.Database.MigrateAsync();
        }
        catch (Exception initializationError)
        {
            try { await DisposeAsync(); }
            catch (Exception cleanupError)
            {
                throw new AggregateException("PostgreSQL test initialization and schema cleanup failed.",
                    initializationError, cleanupError);
            }
            throw;
        }
    }

    public async ValueTask DisposeAsync()
    {
        if (!_created) return;

        await using var connection = new NpgsqlConnection(ConnectionString);
        try
        {
            await connection.OpenAsync();
            // The schema name is generated here, never taken from connection settings.
            await using var command = new NpgsqlCommand($"DROP SCHEMA IF EXISTS \"{_schema}\" CASCADE", connection);
            await command.ExecuteNonQueryAsync();
            _created = false;
        }
        finally
        {
            NpgsqlConnection.ClearPool(connection);
        }
    }
}
