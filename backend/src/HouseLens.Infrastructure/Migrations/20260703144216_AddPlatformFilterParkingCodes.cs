using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace HouseLens.Infrastructure.Migrations
{
    /// <inheritdoc />
    public partial class AddPlatformFilterParkingCodes : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<string>(
                name: "ParkingCodes",
                table: "PlatformFilterConfigs",
                type: "TEXT",
                maxLength: 100,
                nullable: false,
                defaultValue: "");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "ParkingCodes",
                table: "PlatformFilterConfigs");
        }
    }
}
