using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace HouseLens.Infrastructure.Migrations
{
    /// <inheritdoc />
    public partial class AddSourceSiteToPriceHistory : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropIndex(
                name: "IX_PriceHistoryEntries_PropertyId_CrawlRunId",
                table: "PriceHistoryEntries");

            migrationBuilder.AddColumn<int>(
                name: "SourceSite",
                table: "PriceHistoryEntries",
                type: "INTEGER",
                nullable: false,
                defaultValue: 0);

            // 依關聯 Listing 回填 SourceSite（近似值；舊歷史無法區分多平台來源）
            migrationBuilder.Sql("""
                UPDATE PriceHistoryEntries
                SET SourceSite = (
                    SELECT MIN(l.SourceSite)
                    FROM Listings l
                    WHERE l.PropertyId = PriceHistoryEntries.PropertyId
                )
                WHERE EXISTS (
                    SELECT 1 FROM Listings l WHERE l.PropertyId = PriceHistoryEntries.PropertyId
                )
                """);

            migrationBuilder.CreateIndex(
                name: "IX_PriceHistoryEntries_PropertyId_CrawlRunId_SourceSite",
                table: "PriceHistoryEntries",
                columns: new[] { "PropertyId", "CrawlRunId", "SourceSite" },
                unique: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropIndex(
                name: "IX_PriceHistoryEntries_PropertyId_CrawlRunId_SourceSite",
                table: "PriceHistoryEntries");

            migrationBuilder.DropColumn(
                name: "SourceSite",
                table: "PriceHistoryEntries");

            migrationBuilder.CreateIndex(
                name: "IX_PriceHistoryEntries_PropertyId_CrawlRunId",
                table: "PriceHistoryEntries",
                columns: new[] { "PropertyId", "CrawlRunId" },
                unique: true);
        }
    }
}
