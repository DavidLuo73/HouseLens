using FluentAssertions;
using System.Net;
using System.Net.Http.Json;
using System.Text.Json;

namespace HouseLens.IntegrationTests.Api;

// T050: Contract tests for GET /api/properties filter/sort/pagination params
public class PropertyFilterTests(TestWebApplicationFactory factory)
    : IClassFixture<TestWebApplicationFactory>
{
    private readonly HttpClient _client = factory.CreateClient();
    private static readonly JsonSerializerOptions _jsonOpts = new(JsonSerializerDefaults.Web);

    [Fact]
    public async Task GetProperties_Returns200WithPagedStructure()
    {
        var response = await _client.GetAsync("/api/properties");

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>(_jsonOpts);
        body.TryGetProperty("total", out _).Should().BeTrue();
        body.TryGetProperty("page", out _).Should().BeTrue();
        body.TryGetProperty("pageSize", out _).Should().BeTrue();
        body.TryGetProperty("items", out var items).Should().BeTrue();
        items.ValueKind.Should().Be(JsonValueKind.Array);
    }

    [Fact]
    public async Task GetProperties_DefaultPagination_IsPage1Size20()
    {
        var response = await _client.GetAsync("/api/properties");
        var body = await response.Content.ReadFromJsonAsync<JsonElement>(_jsonOpts);

        body.GetProperty("page").GetInt32().Should().Be(1);
        body.GetProperty("pageSize").GetInt32().Should().Be(20);
    }

    [Fact]
    public async Task GetProperties_WithDistrictFilter_Returns200()
    {
        var response = await _client.GetAsync("/api/properties?district=中和區&district=中壢區");
        response.StatusCode.Should().Be(HttpStatusCode.OK);
    }

    [Fact]
    public async Task GetProperties_WithPriceRange_Returns200()
    {
        var response = await _client.GetAsync("/api/properties?minPrice=500&maxPrice=800");
        response.StatusCode.Should().Be(HttpStatusCode.OK);
    }

    [Fact]
    public async Task GetProperties_WithHasParking_Returns200()
    {
        var response = await _client.GetAsync("/api/properties?hasParking=true");
        response.StatusCode.Should().Be(HttpStatusCode.OK);
    }

    [Fact]
    public async Task GetProperties_WithPriceDropped_Returns200()
    {
        var response = await _client.GetAsync("/api/properties?priceDropped=true");
        response.StatusCode.Should().Be(HttpStatusCode.OK);
    }

    [Fact]
    public async Task GetProperties_WithSortByUnitPrice_Returns200()
    {
        var response = await _client.GetAsync("/api/properties?sortBy=unitPrice");
        response.StatusCode.Should().Be(HttpStatusCode.OK);
    }

    [Fact]
    public async Task GetProperties_WithSortByPostedDate_Returns200()
    {
        var response = await _client.GetAsync("/api/properties?sortBy=postedDate");
        response.StatusCode.Should().Be(HttpStatusCode.OK);
    }

    [Fact]
    public async Task GetProperties_WithSortByPriceDrop_Returns200()
    {
        var response = await _client.GetAsync("/api/properties?sortBy=priceDrop");
        response.StatusCode.Should().Be(HttpStatusCode.OK);
    }

    [Fact]
    public async Task GetProperties_WithCustomPageSize_ReturnsThatPageSize()
    {
        var response = await _client.GetAsync("/api/properties?page=1&pageSize=5");
        response.StatusCode.Should().Be(HttpStatusCode.OK);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>(_jsonOpts);

        body.GetProperty("page").GetInt32().Should().Be(1);
        body.GetProperty("pageSize").GetInt32().Should().Be(5);
    }

    [Fact]
    public async Task GetProperties_WithStatusDelisted_Returns200()
    {
        var response = await _client.GetAsync("/api/properties?status=delisted");
        response.StatusCode.Should().Be(HttpStatusCode.OK);
    }

    [Fact]
    public async Task GetProperties_AllFiltersCombo_Returns200()
    {
        var url = "/api/properties?district=中和區&minPrice=400&maxPrice=800"
                + "&hasParking=true&priceDropped=false&status=active"
                + "&sortBy=score&page=1&pageSize=10";

        var response = await _client.GetAsync(url);
        response.StatusCode.Should().Be(HttpStatusCode.OK);
    }

    [Fact]
    public async Task GetProperties_EmptyDb_ReturnsTotalZero()
    {
        var response = await _client.GetAsync("/api/properties");
        var body = await response.Content.ReadFromJsonAsync<JsonElement>(_jsonOpts);

        body.GetProperty("total").GetInt32().Should().Be(0);
        body.GetProperty("items").GetArrayLength().Should().Be(0);
    }
}
