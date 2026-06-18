namespace HouseLens.Application.Crawling;

public record CompletedDistrictResult(string DistrictName, int FetchedCount);

public record CrawlProgressSnapshot(
    bool IsRunning,
    string? CurrentPlatformName,
    int CurrentPlatformIndex,
    int TotalPlatforms,
    string? CurrentDistrictName,
    int CurrentDistrictIndex,
    int TotalDistricts,
    int PlatformFetchedCount,
    DateTime? PlatformStartedAt,
    IReadOnlyList<CompletedDistrictResult> CompletedDistricts
);

public class CrawlProgressState
{
    private readonly object _lock = new();

    private bool _isRunning;
    private string? _currentPlatformName;
    private int _currentPlatformIndex;
    private int _totalPlatforms;
    private string? _currentDistrictName;
    private int _currentDistrictIndex;
    private int _totalDistricts;
    private int _platformFetchedCount;
    private DateTime? _platformStartedAt;
    private List<CompletedDistrictResult> _completedDistricts = [];

    public void StartRun(int totalPlatforms)
    {
        lock (_lock)
        {
            _isRunning = true;
            _totalPlatforms = totalPlatforms;
            _currentPlatformIndex = 0;
        }
    }

    public void StartPlatform(string platformName, int platformIndex)
    {
        lock (_lock)
        {
            _currentPlatformName = platformName;
            _currentPlatformIndex = platformIndex;
            _currentDistrictName = null;
            _currentDistrictIndex = 0;
            _totalDistricts = 0;
            _platformFetchedCount = 0;
            _platformStartedAt = DateTime.UtcNow;
            _completedDistricts = [];
        }
    }

    public void UpdateDistrict(ScraperDistrictProgress p)
    {
        lock (_lock)
        {
            _totalDistricts = p.TotalDistricts;
            if (p.IsStarting)
            {
                _currentDistrictName = p.DistrictName;
                _currentDistrictIndex = p.DistrictIndex;
            }
            else
            {
                _completedDistricts.Add(new(p.DistrictName, p.FetchedCount));
                _platformFetchedCount += p.FetchedCount;
                _currentDistrictName = null;
            }
        }
    }

    public void EndRun()
    {
        lock (_lock)
        {
            _isRunning = false;
            _currentPlatformName = null;
            _currentDistrictName = null;
        }
    }

    public CrawlProgressSnapshot GetSnapshot()
    {
        lock (_lock)
        {
            return new(
                _isRunning,
                _currentPlatformName,
                _currentPlatformIndex,
                _totalPlatforms,
                _currentDistrictName,
                _currentDistrictIndex,
                _totalDistricts,
                _platformFetchedCount,
                _platformStartedAt,
                _completedDistricts.AsReadOnly()
            );
        }
    }
}
