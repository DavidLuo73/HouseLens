using HouseLens.Domain.Enums;

namespace HouseLens.Application.Crawling;

public record PropertyDto(
    string City,
    string District,
    string? Address,
    decimal AreaPing,
    string? Floor,
    int? AgeYears,
    bool HasParking,
    decimal TotalPrice,
    decimal? UnitPrice,
    SourceSite SourceSite,
    string SourceListingKey,
    string Title,
    string Url,
    DateTime? PostedDate,
    string? ImageUrl = null
);
