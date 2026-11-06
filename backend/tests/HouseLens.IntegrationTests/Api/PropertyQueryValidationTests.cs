using System.Net;
using FluentAssertions;

namespace HouseLens.IntegrationTests.Api;

public class PropertyQueryValidationTests(TestWebApplicationFactory factory)
    : IClassFixture<TestWebApplicationFactory>
{
    [Theory]
    [InlineData("page=0")]
    [InlineData("page=-1")]
    [InlineData("pageSize=0")]
    [InlineData("pageSize=101")]
    [InlineData("page=2147483647&pageSize=100")]
    [InlineData("minPrice=-1")]
    [InlineData("maxPrice=-1")]
    [InlineData("minPrice=900&maxPrice=800")]
    [InlineData("status=")]
    [InlineData("status=unknown")]
    [InlineData("sortBy=")]
    [InlineData("sortBy=unknown")]
    [InlineData("sourceSite=")]
    [InlineData("sourceSite=unknown")]
    [InlineData("sourceSite=0")]
    [InlineData("sourceSite=999")]
    [InlineData("sourceSite=F591&sourceSite=unknown")]
    [InlineData("sourceSite=F591&sourceSite=")]
    public async Task InvalidQuery_ReturnsBadRequest(string query)
    {
        using var client = factory.CreateClient();
        using var response = await client.GetAsync("/api/properties?" + query);
        response.StatusCode.Should().Be(HttpStatusCode.BadRequest, query);
    }

    [Theory]
    [InlineData("page=1&pageSize=100&minPrice=0&maxPrice=0")]
    [InlineData("status=ACTIVE&sortBy=SCORE")]
    [InlineData("status=delisted&sortBy=priceDrop")]
    [InlineData("sortBy=unitPrice")]
    [InlineData("sortBy=postedDate")]
    [InlineData("district=中和區&district=中壢區&sourceSite=F591&sourceSite=rakuya")]
    [InlineData("sourceSite=F591")]
    [InlineData("sourceSite=Rakuya")]
    [InlineData("sourceSite=Sinyi")]
    [InlineData("sourceSite=Yungching")]
    [InlineData("sourceSite=TwHouse")]
    [InlineData("sourceSite=HBHousing")]
    [InlineData("sourceSite=CtHouse")]
    [InlineData("priceDropped=false")]
    public async Task ValidQuery_PreservesExistingContract(string query)
    {
        using var client = factory.CreateClient();
        using var response = await client.GetAsync("/api/properties?" + query);
        response.StatusCode.Should().Be(HttpStatusCode.OK, query);
    }
}
