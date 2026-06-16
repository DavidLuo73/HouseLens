using HouseLens.Domain.Entities;
using HouseLens.Domain.Enums;

namespace HouseLens.Application.Queries;

public record PropertyFilter(
    string[]? Districts = null,
    decimal? MinPrice = null,
    decimal? MaxPrice = null,
    bool? HasParking = null,
    bool? PriceDropped = null,
    PropertyStatus Status = PropertyStatus.Active,
    string SortBy = "score",
    int Page = 1,
    int PageSize = 20
);

public record PropertyListItem(
    Guid Id,
    string Title,
    string City,
    string District,
    decimal AreaPing,
    string? Floor,
    int? AgeYears,
    bool HasParking,
    decimal CurrentTotalPrice,
    decimal? CurrentUnitPrice,
    string Status,
    decimal? Score,
    bool IsNew,
    string? LatestChangeFlag,
    decimal? LatestChangePercent,
    bool LatestIsBigDrop,
    IReadOnlyList<PropertySourceLink> Sources
);

public record PropertySourceLink(string SourceSite, string Url);

public record PagedResult<T>(int Total, int Page, int PageSize, IReadOnlyList<T> Items);
