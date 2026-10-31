using HouseLens.Application.Crawling;
using HouseLens.Application.Notification;
using HouseLens.Infrastructure.Crawling;
using HouseLens.Infrastructure.Notification;
using HouseLens.Infrastructure.Persistence;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Npgsql;

namespace HouseLens.Infrastructure;

public static class DependencyInjection
{
    public static IServiceCollection AddInfrastructure(
        this IServiceCollection services,
        IConfiguration configuration)
    {
        var connectionString = configuration.GetConnectionString("Default");
        if (string.IsNullOrWhiteSpace(connectionString))
            throw new InvalidOperationException(
                "Set ConnectionStrings:Default to the Supabase PostgreSQL connection string.");

        var postgres = new NpgsqlConnectionStringBuilder(connectionString);
        // Keep application tables outside Supabase's public Data API schema.
        if (string.IsNullOrWhiteSpace(postgres.SearchPath)) postgres.SearchPath = "houselens";
        services.AddDbContext<AppDbContext>(options => options.UseNpgsql(postgres.ConnectionString));

        services.AddScoped<ICrawlRepository, CrawlRepository>();
        services.AddScoped<INotificationRepository, NotificationRepository>();
        services.AddScoped<ILineMessagingClient, LineMessagingClient>();
        services.AddScoped<NotificationService>();

        return services;
    }
}
