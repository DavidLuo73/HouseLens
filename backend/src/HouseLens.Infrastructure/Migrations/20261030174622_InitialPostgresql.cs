using System;
using Microsoft.EntityFrameworkCore.Migrations;
using Npgsql.EntityFrameworkCore.PostgreSQL.Metadata;

#nullable disable

namespace HouseLens.Infrastructure.Migrations
{
    /// <inheritdoc />
    public partial class InitialPostgresql : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "CrawlRuns",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uuid", nullable: false),
                    StartedAt = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    FinishedAt = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    Status = table.Column<int>(type: "integer", nullable: false),
                    NewCount = table.Column<int>(type: "integer", nullable: false),
                    DelistedCount = table.Column<int>(type: "integer", nullable: false),
                    BigDropCount = table.Column<int>(type: "integer", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_CrawlRuns", x => x.Id);
                });

            migrationBuilder.CreateTable(
                name: "DistrictConfigs",
                columns: table => new
                {
                    Id = table.Column<int>(type: "integer", nullable: false)
                        .Annotation("Npgsql:ValueGenerationStrategy", NpgsqlValueGenerationStrategy.IdentityByDefaultColumn),
                    City = table.Column<string>(type: "character varying(20)", maxLength: 20, nullable: false),
                    District = table.Column<string>(type: "character varying(20)", maxLength: 20, nullable: false),
                    MaxTotalPrice = table.Column<decimal>(type: "numeric(12,2)", precision: 12, scale: 2, nullable: false),
                    IsEnabled = table.Column<bool>(type: "boolean", nullable: false),
                    MaxAgeYears = table.Column<int>(type: "integer", nullable: false),
                    ParkingCodes = table.Column<string>(type: "text", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_DistrictConfigs", x => x.Id);
                });

            migrationBuilder.CreateTable(
                name: "NotificationLogs",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uuid", nullable: false),
                    CrawlRunId = table.Column<Guid>(type: "uuid", nullable: false),
                    Type = table.Column<string>(type: "character varying(50)", maxLength: 50, nullable: false),
                    Success = table.Column<bool>(type: "boolean", nullable: false),
                    ErrorMessage = table.Column<string>(type: "character varying(1000)", maxLength: 1000, nullable: true),
                    SentAt = table.Column<DateTime>(type: "timestamp with time zone", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_NotificationLogs", x => x.Id);
                });

            migrationBuilder.CreateTable(
                name: "PlatformFilterConfigs",
                columns: table => new
                {
                    Id = table.Column<int>(type: "integer", nullable: false)
                        .Annotation("Npgsql:ValueGenerationStrategy", NpgsqlValueGenerationStrategy.IdentityByDefaultColumn),
                    SourceSite = table.Column<int>(type: "integer", nullable: false),
                    MinSizePing = table.Column<decimal>(type: "numeric(10,2)", precision: 10, scale: 2, nullable: false),
                    Rooms = table.Column<string>(type: "character varying(50)", maxLength: 50, nullable: false),
                    TypeCodes = table.Column<string>(type: "character varying(100)", maxLength: 100, nullable: false),
                    UseCode = table.Column<string>(type: "character varying(10)", maxLength: 10, nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_PlatformFilterConfigs", x => x.Id);
                });

            migrationBuilder.CreateTable(
                name: "Properties",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uuid", nullable: false),
                    City = table.Column<string>(type: "character varying(20)", maxLength: 20, nullable: false),
                    District = table.Column<string>(type: "character varying(20)", maxLength: 20, nullable: false),
                    Address = table.Column<string>(type: "character varying(200)", maxLength: 200, nullable: true),
                    AreaPing = table.Column<decimal>(type: "numeric(10,2)", precision: 10, scale: 2, nullable: false),
                    Floor = table.Column<string>(type: "character varying(20)", maxLength: 20, nullable: true),
                    AgeYears = table.Column<int>(type: "integer", nullable: true),
                    HasParking = table.Column<bool>(type: "boolean", nullable: false),
                    CurrentTotalPrice = table.Column<decimal>(type: "numeric(12,2)", precision: 12, scale: 2, nullable: false),
                    CurrentUnitPrice = table.Column<decimal>(type: "numeric(12,2)", precision: 12, scale: 2, nullable: true),
                    Status = table.Column<int>(type: "integer", nullable: false),
                    Score = table.Column<decimal>(type: "numeric(5,4)", precision: 5, scale: 4, nullable: true),
                    IsNew = table.Column<bool>(type: "boolean", nullable: false),
                    FirstSeenAt = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    LastSeenAt = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    MissingCount = table.Column<int>(type: "integer", nullable: false),
                    MergedIntoPropertyId = table.Column<Guid>(type: "uuid", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_Properties", x => x.Id);
                });

            migrationBuilder.CreateTable(
                name: "ScoringConfigs",
                columns: table => new
                {
                    Id = table.Column<int>(type: "integer", nullable: false)
                        .Annotation("Npgsql:ValueGenerationStrategy", NpgsqlValueGenerationStrategy.IdentityByDefaultColumn),
                    WeightUnitPrice = table.Column<decimal>(type: "numeric(5,4)", precision: 5, scale: 4, nullable: false),
                    WeightAge = table.Column<decimal>(type: "numeric(5,4)", precision: 5, scale: 4, nullable: false),
                    WeightParking = table.Column<decimal>(type: "numeric(5,4)", precision: 5, scale: 4, nullable: false),
                    WeightLocation = table.Column<decimal>(type: "numeric(5,4)", precision: 5, scale: 4, nullable: false),
                    BigDropPercent = table.Column<decimal>(type: "numeric(5,4)", precision: 5, scale: 4, nullable: false),
                    BigDropAmount = table.Column<decimal>(type: "numeric(12,2)", precision: 12, scale: 2, nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_ScoringConfigs", x => x.Id);
                });

            migrationBuilder.CreateTable(
                name: "TrackingCriteria",
                columns: table => new
                {
                    Id = table.Column<int>(type: "integer", nullable: false)
                        .Annotation("Npgsql:ValueGenerationStrategy", NpgsqlValueGenerationStrategy.IdentityByDefaultColumn),
                    Districts = table.Column<string>(type: "text", nullable: false),
                    MaxTotalPrice = table.Column<decimal>(type: "numeric(12,2)", precision: 12, scale: 2, nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_TrackingCriteria", x => x.Id);
                });

            migrationBuilder.CreateTable(
                name: "SourceRunResults",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uuid", nullable: false),
                    CrawlRunId = table.Column<Guid>(type: "uuid", nullable: false),
                    SourceSite = table.Column<int>(type: "integer", nullable: false),
                    Success = table.Column<bool>(type: "boolean", nullable: false),
                    FetchedCount = table.Column<int>(type: "integer", nullable: false),
                    ErrorMessage = table.Column<string>(type: "character varying(1000)", maxLength: 1000, nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_SourceRunResults", x => x.Id);
                    table.ForeignKey(
                        name: "FK_SourceRunResults_CrawlRuns_CrawlRunId",
                        column: x => x.CrawlRunId,
                        principalTable: "CrawlRuns",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "Listings",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uuid", nullable: false),
                    PropertyId = table.Column<Guid>(type: "uuid", nullable: false),
                    SourceSite = table.Column<int>(type: "integer", nullable: false),
                    SourceListingKey = table.Column<string>(type: "character varying(100)", maxLength: 100, nullable: false),
                    Title = table.Column<string>(type: "character varying(200)", maxLength: 200, nullable: false),
                    Url = table.Column<string>(type: "character varying(500)", maxLength: 500, nullable: false),
                    PostedDate = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    LatestSourcePrice = table.Column<decimal>(type: "numeric(12,2)", precision: 12, scale: 2, nullable: false),
                    IsActive = table.Column<bool>(type: "boolean", nullable: false),
                    ImageUrl = table.Column<string>(type: "character varying(1000)", maxLength: 1000, nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_Listings", x => x.Id);
                    table.ForeignKey(
                        name: "FK_Listings_Properties_PropertyId",
                        column: x => x.PropertyId,
                        principalTable: "Properties",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "PriceHistoryEntries",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uuid", nullable: false),
                    PropertyId = table.Column<Guid>(type: "uuid", nullable: false),
                    CrawlRunId = table.Column<Guid>(type: "uuid", nullable: false),
                    CapturedAt = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    TotalPrice = table.Column<decimal>(type: "numeric(12,2)", precision: 12, scale: 2, nullable: false),
                    UnitPrice = table.Column<decimal>(type: "numeric(12,2)", precision: 12, scale: 2, nullable: true),
                    SourceSite = table.Column<int>(type: "integer", nullable: false),
                    ChangeFlag = table.Column<int>(type: "integer", nullable: false),
                    ChangePercent = table.Column<decimal>(type: "numeric(8,4)", precision: 8, scale: 4, nullable: true),
                    IsBigDrop = table.Column<bool>(type: "boolean", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_PriceHistoryEntries", x => x.Id);
                    table.ForeignKey(
                        name: "FK_PriceHistoryEntries_CrawlRuns_CrawlRunId",
                        column: x => x.CrawlRunId,
                        principalTable: "CrawlRuns",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Restrict);
                    table.ForeignKey(
                        name: "FK_PriceHistoryEntries_Properties_PropertyId",
                        column: x => x.PropertyId,
                        principalTable: "Properties",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "PropertyScores",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uuid", nullable: false),
                    PropertyId = table.Column<Guid>(type: "uuid", nullable: false),
                    CrawlRunId = table.Column<Guid>(type: "uuid", nullable: false),
                    Score = table.Column<decimal>(type: "numeric(5,4)", precision: 5, scale: 4, nullable: false),
                    UnitPriceScore = table.Column<decimal>(type: "numeric(5,4)", precision: 5, scale: 4, nullable: true),
                    AgeScore = table.Column<decimal>(type: "numeric(5,4)", precision: 5, scale: 4, nullable: true),
                    ParkingScore = table.Column<decimal>(type: "numeric(5,4)", precision: 5, scale: 4, nullable: true),
                    LocationScore = table.Column<decimal>(type: "numeric(5,4)", precision: 5, scale: 4, nullable: true),
                    CalculatedAt = table.Column<DateTime>(type: "timestamp with time zone", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_PropertyScores", x => x.Id);
                    table.ForeignKey(
                        name: "FK_PropertyScores_Properties_PropertyId",
                        column: x => x.PropertyId,
                        principalTable: "Properties",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateIndex(
                name: "IX_CrawlRuns_StartedAt",
                table: "CrawlRuns",
                column: "StartedAt");

            migrationBuilder.CreateIndex(
                name: "IX_DistrictConfigs_City_District",
                table: "DistrictConfigs",
                columns: new[] { "City", "District" },
                unique: true);

            migrationBuilder.CreateIndex(
                name: "IX_Listings_PropertyId",
                table: "Listings",
                column: "PropertyId");

            migrationBuilder.CreateIndex(
                name: "IX_Listings_SourceSite_SourceListingKey",
                table: "Listings",
                columns: new[] { "SourceSite", "SourceListingKey" },
                unique: true);

            migrationBuilder.CreateIndex(
                name: "IX_PlatformFilterConfigs_SourceSite",
                table: "PlatformFilterConfigs",
                column: "SourceSite",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "IX_PriceHistoryEntries_CrawlRunId",
                table: "PriceHistoryEntries",
                column: "CrawlRunId");

            migrationBuilder.CreateIndex(
                name: "IX_PriceHistoryEntries_PropertyId_CrawlRunId_SourceSite",
                table: "PriceHistoryEntries",
                columns: new[] { "PropertyId", "CrawlRunId", "SourceSite" },
                unique: true);

            migrationBuilder.CreateIndex(
                name: "IX_Properties_District_Status",
                table: "Properties",
                columns: new[] { "District", "Status" });

            migrationBuilder.CreateIndex(
                name: "IX_Properties_Status",
                table: "Properties",
                column: "Status");

            migrationBuilder.CreateIndex(
                name: "IX_PropertyScores_PropertyId",
                table: "PropertyScores",
                column: "PropertyId");

            migrationBuilder.CreateIndex(
                name: "IX_SourceRunResults_CrawlRunId",
                table: "SourceRunResults",
                column: "CrawlRunId");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "DistrictConfigs");

            migrationBuilder.DropTable(
                name: "Listings");

            migrationBuilder.DropTable(
                name: "NotificationLogs");

            migrationBuilder.DropTable(
                name: "PlatformFilterConfigs");

            migrationBuilder.DropTable(
                name: "PriceHistoryEntries");

            migrationBuilder.DropTable(
                name: "PropertyScores");

            migrationBuilder.DropTable(
                name: "ScoringConfigs");

            migrationBuilder.DropTable(
                name: "SourceRunResults");

            migrationBuilder.DropTable(
                name: "TrackingCriteria");

            migrationBuilder.DropTable(
                name: "Properties");

            migrationBuilder.DropTable(
                name: "CrawlRuns");
        }
    }
}
