using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace HouseLens.Infrastructure.Migrations
{
    /// <inheritdoc />
    public partial class AddPlatformFilterConfig : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "PlatformFilterConfigs",
                columns: table => new
                {
                    Id = table.Column<int>(type: "INTEGER", nullable: false)
                        .Annotation("Sqlite:Autoincrement", true),
                    SourceSite = table.Column<int>(type: "INTEGER", nullable: false),
                    MinSizePing = table.Column<decimal>(type: "TEXT", precision: 10, scale: 2, nullable: false),
                    Rooms = table.Column<string>(type: "TEXT", maxLength: 50, nullable: false),
                    TypeCodes = table.Column<string>(type: "TEXT", maxLength: 100, nullable: false),
                    UseCode = table.Column<string>(type: "TEXT", maxLength: 10, nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_PlatformFilterConfigs", x => x.Id);
                });

            migrationBuilder.CreateIndex(
                name: "IX_PlatformFilterConfigs_SourceSite",
                table: "PlatformFilterConfigs",
                column: "SourceSite",
                unique: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "PlatformFilterConfigs");
        }
    }
}
