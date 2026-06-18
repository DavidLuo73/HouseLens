using HouseLens.Application.Crawling;
using HouseLens.Application.Notification;
using HouseLens.Infrastructure.Crawling;
using HouseLens.Infrastructure.Notification;
using HouseLens.Infrastructure.Persistence;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;

namespace HouseLens.Infrastructure;

public static class DependencyInjection
{
    public static IServiceCollection AddInfrastructure(
        this IServiceCollection services,
        IConfiguration configuration)
    {
        var connectionString = configuration.GetConnectionString("Default")
            ?? "Data Source=HouseLens.db";

        // BusyTimeout：每次連線等待最多 5 秒再拋錯，防止爬蟲寫入期間讀取立即失敗
        var connectionStringWithTimeout = connectionString.TrimEnd(';') + ";BusyTimeout=5000";

        services.AddDbContext<AppDbContext>(options =>
            options.UseSqlite(connectionStringWithTimeout));

        services.AddScoped<ICrawlRepository, CrawlRepository>();
        services.AddScoped<INotificationRepository, NotificationRepository>();
        services.AddScoped<ILineMessagingClient, LineMessagingClient>();
        services.AddScoped<NotificationService>();

        return services;
    }
}
