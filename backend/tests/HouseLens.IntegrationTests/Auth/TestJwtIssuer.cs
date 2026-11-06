using System.IdentityModel.Tokens.Jwt;
using System.Net;
using System.Security.Claims;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using Microsoft.IdentityModel.Tokens;

namespace HouseLens.IntegrationTests.Auth;

public sealed class TestJwtIssuer : HttpMessageHandler
{
    public const string Authority = "https://auth.example.test/auth/v1";
    public const string Member = "10000000-0000-0000-0000-000000000001";
    private readonly RSA _rsa = RSA.Create(2048);
    private string _keyId = Guid.NewGuid().ToString("N");
    public TaskCompletionSource JwksRead { get; private set; } = new(TaskCreationOptions.RunContinuationsAsynchronously);

    public string Token(string subject = Member, string issuer = Authority,
        string audience = "authenticated", bool expired = false, bool wrongKey = false)
    {
        using var other = wrongKey ? RSA.Create(2048) : null;
        var key = new RsaSecurityKey(other ?? _rsa) { KeyId = _keyId };
        var jwt = new JwtSecurityToken(issuer, audience, [new Claim("sub", subject)],
            notBefore: DateTime.UtcNow.AddHours(-2),
            expires: expired ? DateTime.UtcNow.AddHours(-1) : DateTime.UtcNow.AddHours(1),
            signingCredentials: new SigningCredentials(key, SecurityAlgorithms.RsaSha256));
        return new JwtSecurityTokenHandler().WriteToken(jwt);
    }

    public void Rotate()
    {
        _rsa.ImportParameters(RSA.Create(2048).ExportParameters(true));
        _keyId = Guid.NewGuid().ToString("N");
        JwksRead = new(TaskCreationOptions.RunContinuationsAsynchronously);
    }

    protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
    {
        if (request.RequestUri?.AbsoluteUri == Authority + "/.well-known/openid-configuration")
            return Task.FromResult(Json(new { issuer = Authority, jwks_uri = Authority + "/.well-known/jwks.json" }));
        if (request.RequestUri?.AbsoluteUri == Authority + "/.well-known/jwks.json")
        {
            var parameters = _rsa.ExportParameters(false);
            var response = Json(new { keys = new[] { new {
                kty = "RSA", use = "sig", alg = "RS256", kid = _keyId,
                n = Base64UrlEncoder.Encode(parameters.Modulus!), e = Base64UrlEncoder.Encode(parameters.Exponent!)
            } } });
            JwksRead.TrySetResult();
            return Task.FromResult(response);
        }
        throw new InvalidOperationException("Unexpected Auth backchannel URL");
    }

    private static HttpResponseMessage Json(object value) => new(HttpStatusCode.OK)
    { Content = new StringContent(JsonSerializer.Serialize(value), Encoding.UTF8, "application/json") };

    protected override void Dispose(bool disposing)
    {
        if (disposing) _rsa.Dispose();
        base.Dispose(disposing);
    }
}
