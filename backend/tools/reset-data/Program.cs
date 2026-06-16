using Microsoft.Data.Sqlite;

var dbPath = args.Length > 0 ? args[0] : "HouseLens.db";
var mode = args.Length > 1 ? args[1] : "query";

using var conn = new SqliteConnection($"Data Source={dbPath}");
conn.Open();

if (mode == "query")
{
    using var cmd = conn.CreateCommand();
    cmd.CommandText = "SELECT Title, Url, ImageUrl FROM Listings LIMIT 10";
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
        ("PRAGMA foreign_keys = OFF", false),
        ("DELETE FROM PriceHistoryEntries", true),
        ("DELETE FROM PropertyScores", true),
        ("DELETE FROM NotificationLogs", true),
        ("DELETE FROM Listings", true),
        ("DELETE FROM Properties", true),
        ("DELETE FROM SourceRunResults", true),
        ("DELETE FROM CrawlRuns", true),
        ("PRAGMA foreign_keys = ON", false),
    };
    foreach (var (sql, showCount) in sqls)
    {
        using var cmd = conn.CreateCommand();
        cmd.CommandText = sql;
        var affected = cmd.ExecuteNonQuery();
        if (showCount) Console.WriteLine($"  {sql}: 已刪除 {affected} 筆");
    }
    Console.WriteLine("完成！");
}
