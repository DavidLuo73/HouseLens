using Npgsql;
using System.Text.RegularExpressions;

var mode = args.Length > 0 ? args[0] : "query";
if (mode is not ("query" or "reset" or "query-yungching" or "fix-yungching-images")
    || args.Length > 2 || (args.Length == 2 && args[1] != "--confirm"))
{
    Console.Error.WriteLine("用法：reset-data [query|query-yungching|reset|fix-yungching-images] [--confirm]");
    return 2;
}

if (mode is "reset" or "fix-yungching-images" && !args.Contains("--confirm"))
{
    Console.Error.WriteLine("此操作會修改遠端資料庫，請加上 --confirm。");
    return 2;
}

var connectionString = Environment.GetEnvironmentVariable("ConnectionStrings__Default");
if (string.IsNullOrWhiteSpace(connectionString))
{
    Console.Error.WriteLine("請先設定 ConnectionStrings__Default PostgreSQL 連線字串。");
    return 2;
}
var settings = new NpgsqlConnectionStringBuilder(connectionString);
if (string.IsNullOrWhiteSpace(settings.SearchPath)) settings.SearchPath = "houselens";

using var conn = new NpgsqlConnection(settings.ConnectionString);
conn.Open();

if (mode == "query")
{
    using var cmd = conn.CreateCommand();
    cmd.CommandText = """SELECT "Title", "Url", "ImageUrl" FROM "Listings" LIMIT 10""";
    using var reader = cmd.ExecuteReader();
    int count = 0;
    while (reader.Read())
    {
        count++;
        Console.WriteLine($"[{count}] {reader["Title"]}");
        Console.WriteLine($"  Url:      {reader["Url"]}");
        Console.WriteLine($"  ImageUrl: {(reader["ImageUrl"] == DBNull.Value ? "(null)" : reader["ImageUrl"])}");
    }
    if (count == 0) Console.WriteLine("（資料庫無 Listings 資料）");
}
else if (mode == "reset")
{
    var sqls = new[]
    {
        "DELETE FROM \"PriceHistoryEntries\"",
        "DELETE FROM \"PropertyScores\"",
        "DELETE FROM \"NotificationLogs\"",
        "DELETE FROM \"Listings\"",
        "DELETE FROM \"Properties\"",
        "DELETE FROM \"SourceRunResults\"",
        "DELETE FROM \"CrawlRuns\"",
    };
    using var tx = conn.BeginTransaction();
    foreach (var sql in sqls)
    {
        using var cmd = conn.CreateCommand();
        cmd.Transaction = tx;
        cmd.CommandText = sql;
        var affected = cmd.ExecuteNonQuery();
        Console.WriteLine($"  {sql}: {affected} 筆");
    }
    tx.Commit();
    Console.WriteLine("完成！");
}
else if (mode == "query-yungching")
{
    const int yungchingSite = 3;
    using var cmd = conn.CreateCommand();
    cmd.CommandText = """SELECT "Id", "ImageUrl" FROM "Listings" WHERE "SourceSite" = @site AND "ImageUrl" IS NOT NULL LIMIT 5""";
    cmd.Parameters.AddWithValue("site", yungchingSite);
    using var reader = cmd.ExecuteReader();
    int count = 0;
    while (reader.Read())
    {
        count++;
        Console.WriteLine($"[{reader.GetGuid(0)}] {reader.GetString(1)}");
    }
    if (count == 0) Console.WriteLine("（無永慶圖片資料）");
}
else if (mode == "fix-yungching-images")
{
    // SourceSite enum: F591=0, Rakuya=1, Sinyi=2, Yungching=3, TwHouse=4
    const int yungchingSite = 3;

    // 撈出所有永慶且含 yccdn CDN URL 的 Listing
    var rows = new List<(Guid Id, string ImageUrl)>();
    using var tx = conn.BeginTransaction();
    using (var selectCmd = conn.CreateCommand())
    {
        selectCmd.Transaction = tx;
        selectCmd.CommandText = """
            SELECT "Id", "ImageUrl" FROM "Listings"
            WHERE "SourceSite" = @site
              AND "ImageUrl" LIKE '%yccdn.yungching.com.tw%'
              AND "ImageUrl" LIKE '%width=%'
            FOR UPDATE
            """;
        selectCmd.Parameters.AddWithValue("site", yungchingSite);
        using var reader = selectCmd.ExecuteReader();
        while (reader.Read())
            rows.Add((reader.GetGuid(0), reader.GetString(1)));
    }

    Console.WriteLine($"找到 {rows.Count} 筆永慶圖片 URL 需要升級...");

    var widthRegex = new Regex(@"width=\d+", RegexOptions.Compiled);
    int updated = 0;
    int skipped = 0;

    foreach (var (id, imageUrl) in rows)
    {
        var newUrl = widthRegex.Replace(imageUrl, "width=1200");
        if (newUrl == imageUrl) { skipped++; continue; }

        using var updateCmd = conn.CreateCommand();
        updateCmd.Transaction = tx;
        updateCmd.CommandText = """UPDATE "Listings" SET "ImageUrl" = @url WHERE "Id" = @id""";
        updateCmd.Parameters.AddWithValue("url", newUrl);
        updateCmd.Parameters.AddWithValue("id", id);
        var affected = updateCmd.ExecuteNonQuery();
        if (affected > 0) updated++;
        else Console.WriteLine($"⚠️ Id={id} 無對應列");
    }
    tx.Commit();

    // 驗證：確認寫入生效
    using (var verifyCmd = conn.CreateCommand())
    {
        verifyCmd.CommandText = """SELECT COUNT(*) FROM "Listings" WHERE "SourceSite" = @site AND "ImageUrl" LIKE '%width=480%'""";
        verifyCmd.Parameters.AddWithValue("site", yungchingSite);
        var remaining = (long)verifyCmd.ExecuteScalar()!;
        Console.WriteLine($"驗證：commit 後仍有 {remaining} 筆 width=480（應為 0）");
    }

    Console.WriteLine($"完成！已更新 {updated} 筆，略過（已是 1200）{skipped} 筆。");
}

return 0;
