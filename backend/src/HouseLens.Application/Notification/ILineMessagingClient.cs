namespace HouseLens.Application.Notification;

public interface ILineMessagingClient
{
    Task<bool> PushMessageAsync(string text, CancellationToken ct = default);
}
