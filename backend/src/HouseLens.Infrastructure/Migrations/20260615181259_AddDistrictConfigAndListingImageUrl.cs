using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace HouseLens.Infrastructure.Migrations
{
    /// <inheritdoc />
    public partial class AddDistrictConfigAndListingImageUrl : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<string>(
                name: "ImageUrl",
                table: "Listings",
                type: "TEXT",
                maxLength: 1000,
                nullable: true);

            migrationBuilder.CreateTable(
                name: "DistrictConfigs",
                columns: table => new
                {
                    Id = table.Column<int>(type: "INTEGER", nullable: false)
                        .Annotation("Sqlite:Autoincrement", true),
                    City = table.Column<string>(type: "TEXT", maxLength: 20, nullable: false),
                    District = table.Column<string>(type: "TEXT", maxLength: 20, nullable: false),
                    MaxTotalPrice = table.Column<decimal>(type: "TEXT", precision: 12, scale: 2, nullable: false),
                    IsEnabled = table.Column<bool>(type: "INTEGER", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_DistrictConfigs", x => x.Id);
                });

            migrationBuilder.CreateIndex(
                name: "IX_DistrictConfigs_City_District",
                table: "DistrictConfigs",
                columns: new[] { "City", "District" },
                unique: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "DistrictConfigs");

            migrationBuilder.DropColumn(
                name: "ImageUrl",
                table: "Listings");
        }
    }
}
