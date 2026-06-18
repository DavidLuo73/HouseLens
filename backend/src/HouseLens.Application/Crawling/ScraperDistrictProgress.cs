namespace HouseLens.Application.Crawling;

public record ScraperDistrictProgress(
    string DistrictName,
    int DistrictIndex,   // 0-based
    int TotalDistricts,
    bool IsStarting,     // true=開始處理此行政區, false=已完成
    int FetchedCount     // 僅 IsStarting=false 時有意義
);
