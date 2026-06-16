using HouseLens.Application.Notification;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;

namespace HouseLens.Infrastructure.Notification;

public class LineMessagingClient(
    IConfiguration config,
    ILogger<LineMessagingClient> logger) : ILineMessagingClient, IDisposable
{
    private readonly HttpClient _http = new();
    private readonly string? _token = config["LINE:ChannelAccessToken"];
    private readonly string? _userId = config["LINE:TargetUserId"];

    public async Task<bool> PushMessageAsync(string text, CancellationToken ct = default)
    {
        if (string.IsNullOrWhiteSpace(_token) || string.IsNullOrWhiteSpace(_userId))
        {
            logger.LogWarning("LINE token or userId not configured; skipping notification");
            return false;
        }

        var payload = JsonSerializer.Serialize(new
        {
            to = _userId,
            messages = new[] { new { type = "text", text } }
        });

        var request = new HttpRequestMessage(HttpMethod.Post, "https://api.line.me/v2/bot/message/push")
        {
            Content = new StringContent(payload, Encoding.UTF8, "application/json")
        };
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", _token);

        var response = await _http.SendAsync(request, ct);
        if (!response.IsSuccessStatusCode)
        {
            var body = await response.Content.ReadAsStringAsync(ct);
            logger.LogError("LINE push failed: {Status} {Body}", response.StatusCode, body);
            return false;
        }

        return true;
    }

    public void Dispose() => _http.Dispose();
}
