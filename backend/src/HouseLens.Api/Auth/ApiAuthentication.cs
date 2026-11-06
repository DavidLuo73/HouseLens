using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.IdentityModel.Tokens;

namespace HouseLens.Api.Auth;

public static class ApiAuthentication
{
    public static IServiceCollection AddPublicApiAuthentication(this IServiceCollection services, IConfiguration configuration)
    {
        var authority = configuration["Auth:Authority"];
        var audience = configuration["Auth:Audience"];
        if (!Uri.TryCreate(authority, UriKind.Absolute, out var uri) || uri.Scheme != "https" ||
            uri.UserInfo.Length > 0 || uri.Query.Length > 0 || uri.Fragment.Length > 0 ||
            string.IsNullOrWhiteSpace(audience))
            throw new InvalidOperationException("PublicReadOnly requires a HTTPS Auth:Authority and Auth:Audience.");
        if (configuration.GetSection("Auth:ApprovedUserIds").GetChildren()
            .Any(c => !string.IsNullOrWhiteSpace(c.Value) && !Guid.TryParse(c.Value, out _)))
            throw new InvalidOperationException("Auth:ApprovedUserIds must contain UUIDs.");

        services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme).AddJwtBearer(options =>
        {
            options.Authority = authority!.TrimEnd('/');
            options.Audience = audience;
            options.MapInboundClaims = false;
            options.TokenValidationParameters = new TokenValidationParameters
            {
                ValidateIssuer = true, ValidateAudience = true,
                ValidateIssuerSigningKey = true, ValidateLifetime = true,
                RequireSignedTokens = true, RequireExpirationTime = true,
                ClockSkew = TimeSpan.FromSeconds(30), NameClaimType = "sub"
            };
        });
        services.AddAuthorization(options => options.AddPolicy("ApprovedMember", policy =>
            policy.RequireAuthenticatedUser().RequireAssertion(context =>
                Guid.TryParse(context.User.FindFirst("sub")?.Value, out var userId) &&
                configuration.GetSection("Auth:ApprovedUserIds").GetChildren()
                    .Any(c => Guid.TryParse(c.Value, out var approved) && approved == userId))));
        return services;
    }

    public static string RequireLoopbackBindings(IConfiguration configuration)
    {
        if (!string.IsNullOrWhiteSpace(configuration["HTTP_PORTS"]) ||
            !string.IsNullOrWhiteSpace(configuration["HTTPS_PORTS"]))
            throw new InvalidOperationException("Use explicit loopback URLs instead of HTTP_PORTS/HTTPS_PORTS.");
        var urls = configuration["urls"] ?? "http://localhost:5000";
        var configured = urls.Split(';').Concat(configuration.GetSection("Kestrel:Endpoints")
            .GetChildren().Select(endpoint => endpoint["Url"] ?? ""));
        foreach (var value in configured)
            if (!Uri.TryCreate(value, UriKind.Absolute, out var uri) ||
                uri.Scheme is not ("http" or "https") || !uri.IsLoopback ||
                uri.UserInfo.Length > 0 || uri.Query.Length > 0 || uri.Fragment.Length > 0)
                throw new InvalidOperationException("API listeners must bind to loopback; expose only the HTTPS proxy.");
        return urls;
    }
}
