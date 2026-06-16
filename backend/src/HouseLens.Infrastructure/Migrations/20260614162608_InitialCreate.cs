using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace HouseLens.Infrastructure.Migrations
{
    /// <inheritdoc />
    public partial class InitialCreate : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "CrawlRuns",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "TEXT", nullable: false),
                    StartedAt = table.Column<DateTime>(type: "TEXT", nullable: false),
                    FinishedAt = table.Column<DateTime>(type: "TEXT", nullable: true),
                    Status = table.Column<int>(type: "INTEGER", nullable: false),
                    NewCount = table.Column<int>(type: "INTEGER", nullable: false),
                    DelistedCount = table.Column<int>(type: "INTEGER", nullable: false),
                    BigDropCount = table.Column<int>(type: "INTEGER", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_CrawlRuns", x => x.Id);
                });

            migrationBuilder.CreateTable(
                name: "NotificationLogs",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "TEXT", nullable: false),
                    CrawlRunId = table.Column<Guid>(type: "TEXT", nullable: false),
                    Type = table.Column<string>(type: "TEXT", maxLength: 50, nullable: false),
                    Success = table.Column<bool>(type: "INTEGER", nullable: false),
                    ErrorMessage = table.Column<string>(type: "TEXT", maxLength: 1000, nullable: true),
                    SentAt = table.Column<DateTime>(type: "TEXT", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_NotificationLogs", x => x.Id);
                });

            migrationBuilder.CreateTable(
                name: "Properties",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "TEXT", nullable: false),
                    City = table.Column<string>(type: "TEXT", maxLength: 20, nullable: false),
                    District = table.Column<string>(type: "TEXT", maxLength: 20, nullable: false),
                    Address = table.Column<string>(type: "TEXT", maxLength: 200, nullable: true),
                    AreaPing = table.Column<decimal>(type: "TEXT", precision: 10, scale: 2, nullable: false),
                    Floor = table.Column<string>(type: "TEXT", maxLength: 20, nullable: true),
                    AgeYears = table.Column<int>(type: "INTEGER", nullable: true),
                    HasParking = table.Column<bool>(type: "INTEGER", nullable: false),
                    CurrentTotalPrice = table.Column<decimal>(type: "TEXT", precision: 12, scale: 2, nullable: false),
                    CurrentUnitPrice = table.Column<decimal>(type: "TEXT", precision: 12, scale: 2, nullable: true),
                    Status = table.Column<int>(type: "INTEGER", nullable: false),
                    Score = table.Column<decimal>(type: "TEXT", precision: 5, scale: 4, nullable: true),
                    IsNew = table.Column<bool>(type: "INTEGER", nullable: false),
                    FirstSeenAt = table.Column<DateTime>(type: "TEXT", nullable: false),
                    LastSeenAt = table.Column<DateTime>(type: "TEXT", nullable: false),
                    MissingCount = table.Column<int>(type: "INTEGER", nullable: false),
                    MergedIntoPropertyId = table.Column<Guid>(type: "TEXT", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_Properties", x => x.Id);
                });

            migrationBuilder.CreateTable(
                name: "ScoringConfigs",
                columns: table => new
                {
                    Id = table.Column<int>(type: "INTEGER", nullable: false)
                        .Annotation("Sqlite:Autoincrement", true),
                    WeightUnitPrice = table.Column<decimal>(type: "TEXT", precision: 5, scale: 4, nullable: false),
                    WeightAge = table.Column<decimal>(type: "TEXT", precision: 5, scale: 4, nullable: false),
                    WeightParking = table.Column<decimal>(type: "TEXT", precision: 5, scale: 4, nullable: false),
                    WeightLocation = table.Column<decimal>(type: "TEXT", precision: 5, scale: 4, nullable: false),
                    BigDropPercent = table.Column<decimal>(type: "TEXT", precision: 5, scale: 4, nullable: false),
                    BigDropAmount = table.Column<decimal>(type: "TEXT", precision: 12, scale: 2, nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_ScoringConfigs", x => x.Id);
                });

            migrationBuilder.CreateTable(
                name: "TrackingCriteria",
                columns: table => new
                {
                    Id = table.Column<int>(type: "INTEGER", nullable: false)
                        .Annotation("Sqlite:Autoincrement", true),
                    Districts = table.Column<string>(type: "TEXT", nullable: false),
                    MaxTotalPrice = table.Column<decimal>(type: "TEXT", precision: 12, scale: 2, nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_TrackingCriteria", x => x.Id);
                });

            migrationBuilder.CreateTable(
                name: "SourceRunResults",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "TEXT", nullable: false),
                    CrawlRunId = table.Column<Guid>(type: "TEXT", nullable: false),
                    SourceSite = table.Column<int>(type: "INTEGER", nullable: false),
                    Success = table.Column<bool>(type: "INTEGER", nullable: false),
                    FetchedCount = table.Column<int>(type: "INTEGER", nullable: false),
                    ErrorMessage = table.Column<string>(type: "TEXT", maxLength: 1000, nullable: true)
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
                    Id = table.Column<Guid>(type: "TEXT", nullable: false),
                    PropertyId = table.Column<Guid>(type: "TEXT", nullable: false),
                    SourceSite = table.Column<int>(type: "INTEGER", nullable: false),
                    SourceListingKey = table.Column<string>(type: "TEXT", maxLength: 100, nullable: false),
                    Title = table.Column<string>(type: "TEXT", maxLength: 200, nullable: false),
                    Url = table.Column<string>(type: "TEXT", maxLength: 500, nullable: false),
                    PostedDate = table.Column<DateTime>(type: "TEXT", nullable: true),
                    LatestSourcePrice = table.Column<decimal>(type: "TEXT", precision: 12, scale: 2, nullable: false),
                    IsActive = table.Column<bool>(type: "INTEGER", nullable: false)
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
                    Id = table.Column<Guid>(type: "TEXT", nullable: false),
                    PropertyId = table.Column<Guid>(type: "TEXT", nullable: false),
                    CrawlRunId = table.Column<Guid>(type: "TEXT", nullable: false),
                    CapturedAt = table.Column<DateTime>(type: "TEXT", nullable: false),
                    TotalPrice = table.Column<decimal>(type: "TEXT", precision: 12, scale: 2, nullable: false),
                    UnitPrice = table.Column<decimal>(type: "TEXT", precision: 12, scale: 2, nullable: true),
                    ChangeFlag = table.Column<int>(type: "INTEGER", nullable: false),
                    ChangePercent = table.Column<decimal>(type: "TEXT", precision: 8, scale: 4, nullable: true),
                    IsBigDrop = table.Column<bool>(type: "INTEGER", nullable: false)
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
                    Id = table.Column<Guid>(type: "TEXT", nullable: false),
                    PropertyId = table.Column<Guid>(type: "TEXT", nullable: false),
                    CrawlRunId = table.Column<Guid>(type: "TEXT", nullable: false),
                    Score = table.Column<decimal>(type: "TEXT", precision: 5, scale: 4, nullable: false),
                    UnitPriceScore = table.Column<decimal>(type: "TEXT", precision: 5, scale: 4, nullable: true),
                    AgeScore = table.Column<decimal>(type: "TEXT", precision: 5, scale: 4, nullable: true),
                    ParkingScore = table.Column<decimal>(type: "TEXT", precision: 5, scale: 4, nullable: true),
                    LocationScore = table.Column<decimal>(type: "TEXT", precision: 5, scale: 4, nullable: true),
                    CalculatedAt = table.Column<DateTime>(type: "TEXT", nullable: false)
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
                name: "IX_Listings_PropertyId",
                table: "Listings",
                column: "PropertyId");

            migrationBuilder.CreateIndex(
                name: "IX_Listings_SourceSite_SourceListingKey",
                table: "Listings",
                columns: new[] { "SourceSite", "SourceListingKey" },
                unique: true);

            migrationBuilder.CreateIndex(
                name: "IX_PriceHistoryEntries_CrawlRunId",
                table: "PriceHistoryEntries",
                column: "CrawlRunId");

            migrationBuilder.CreateIndex(
                name: "IX_PriceHistoryEntries_PropertyId_CrawlRunId",
                table: "PriceHistoryEntries",
                columns: new[] { "PropertyId", "CrawlRunId" },
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
                name: "Listings");

            migrationBuilder.DropTable(
                name: "NotificationLogs");

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
