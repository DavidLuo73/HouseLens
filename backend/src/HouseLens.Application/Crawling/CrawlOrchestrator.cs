using HouseLens.Application.Analysis;
using HouseLens.Application.Dedup;
using HouseLens.Application.Scoring;
using HouseLens.Domain.Entities;
using HouseLens.Domain.Enums;
using Microsoft.Extensions.Logging;

namespace HouseLens.Application.Crawling;

public interface ICrawlRepository
{
    Task<TrackingCriteria> GetTrackingCriteriaAsync(CancellationToken ct = default);
    Task<IReadOnlyList<DistrictConfig>> GetEnabledDistrictConfigsAsync(CancellationToken ct = default);
    Task<IReadOnlyList<PlatformFilterConfig>> GetPlatformFilterConfigsAsync(CancellationToken ct = default);
    Task<Property?> FindExistingPropertyAsync(string sourceListingKey, SourceSite sourceSite, CancellationToken ct = default);
    Task<CrawlRun> CreateCrawlRunAsync(CancellationToken ct = default);
    Task SavePropertyAsync(Property property, CancellationToken ct = default);
    Task SaveListingAsync(Listing listing, CancellationToken ct = default);
    Task SavePriceHistoryAsync(PriceHistoryEntry entry, CancellationToken ct = default);
    Task SaveSourceRunResultAsync(SourceRunResult result, CancellationToken ct = default);
    Task CompleteCrawlRunAsync(CrawlRun run, CancellationToken ct = default);
    Task<decimal?> GetPreviousPriceAsync(Guid propertyId, CancellationToken ct = default);
    Task<ScoringConfig> GetScoringConfigAsync(CancellationToken ct = default);
    Task<IReadOnlyList<Property>> GetActivePropertiesAsync(CancellationToken ct = default);
    Task UpdatePropertyAsync(Property property, CancellationToken ct = default);
}

public class CrawlOrchestrator(
    IEnumerable<ISourceScraper> scrapers,
    ICrawlRepository repository,
    CrawlProgressState progressState,
    ILogger<CrawlOrchestrator> logger)
{
    private static string GetPlatformDisplayName(SourceSite site) => site switch
    {
        SourceSite.F591      => "591 房屋",
        SourceSite.Sinyi     => "信義房屋",
        SourceSite.Rakuya    => "樂屋",
        SourceSite.Yungching => "永慶不動產",
        SourceSite.TwHouse   => "台灣房屋",
        SourceSite.HBHousing => "住商不動產",
        SourceSite.CtHouse   => "中信房屋",
        _                    => site.ToString()
    };

    public async Task<Guid> RunAsync(SourceSite? onlySource = null, CancellationToken ct = default)
    {
        var districtConfigs = await repository.GetEnabledDistrictConfigsAsync(ct);
        IReadOnlyList<DistrictConfig> effectiveDistricts;

        if (districtConfigs.Count > 0)
        {
            effectiveDistricts = districtConfigs;
        }
        else
        {
            // 沒有 DistrictConfig 時，回退到舊版 TrackingCriteria（屋齡/停車位使用預設值＝不限）
            var criteria = await repository.GetTrackingCriteriaAsync(ct);
            var legacyDistricts = System.Text.Json.JsonSerializer.Deserialize<string[]>(criteria.Districts) ?? [];
            effectiveDistricts = legacyDistricts
                .Select(d => new DistrictConfig { District = d, MaxTotalPrice = criteria.MaxTotalPrice })
                .ToList();
        }

        // 平台專屬篩選（目前僅樂屋網）：跑到該平台時合併進每個地區的 DistrictCriteria
        var platformFilters = (await repository.GetPlatformFilterConfigsAsync(ct))
            .ToDictionary(f => f.SourceSite);

        var config = await repository.GetScoringConfigAsync(ct);

        var scraperList = onlySource.HasValue
            ? scrapers.Where(s => s.SourceSite == onlySource.Value).ToList()
            : scrapers.ToList();
        progressState.StartRun(scraperList.Count);

        var run = await repository.CreateCrawlRunAsync(ct);
        logger.LogInformation("CrawlRun {RunId} started for {DistrictCount} districts", run.Id, effectiveDistricts.Count);

        var seenPropertyIds = new HashSet<Guid>();
        var knownProperties = (await repository.GetActivePropertiesAsync(ct)).ToList();

        var previouslyNew = knownProperties.Where(p => p.IsNew).ToList();
        NewListingMarker.ClearNewFlag(previouslyNew);
        foreach (var p in previouslyNew)
            await repository.UpdatePropertyAsync(p, ct);

        for (var i = 0; i < scraperList.Count; i++)
        {
            // 每個平台用「共用地區＋該平台篩選」組出自己的 DistrictCriteria
            var scraperCriteria = BuildCriteriaForScraper(scraperList[i].SourceSite, effectiveDistricts, platformFilters);
            await RunScraperAsync(scraperList[i], i, run, scraperCriteria, config, seenPropertyIds, knownProperties, ct);
        }

        // 單平台模式跳過 missing 標記：其他平台的物件本次未被掃到，不應誤判為下架
        if (!onlySource.HasValue)
            await MarkMissingPropertiesAsync(run, seenPropertyIds, ct);

        await ScoreActivePropertiesAsync(config, ct);

        run.FinishedAt = DateTime.UtcNow;
        run.Status = RunStatus.Completed;
        await repository.CompleteCrawlRunAsync(run, ct);
        progressState.EndRun();

        logger.LogInformation("CrawlRun {RunId} completed. New={New}, Delisted={Del}, BigDrop={Drop}",
            run.Id, run.NewCount, run.DelistedCount, run.BigDropCount);

        return run.Id;
    }

    private static IReadOnlyDictionary<string, DistrictCriteria> BuildCriteriaForScraper(
        SourceSite site,
        IReadOnlyList<DistrictConfig> districts,
        IReadOnlyDictionary<SourceSite, PlatformFilterConfig> platformFilters)
    {
        platformFilters.TryGetValue(site, out var filter);
        return districts.ToDictionary(
            d => d.District,
            d => filter is null
                ? new DistrictCriteria(d.MaxTotalPrice, MaxAgeYears: d.MaxAgeYears, ParkingCodes: d.ParkingCodes)
                : new DistrictCriteria(d.MaxTotalPrice, filter.MinSizePing, filter.Rooms, filter.TypeCodes, filter.UseCode,
                    d.MaxAgeYears, d.ParkingCodes));
    }

    private async Task ScoreActivePropertiesAsync(ScoringConfig config, CancellationToken ct)
    {
        var active = await repository.GetActivePropertiesAsync(ct);

        var districtAvgPrices = active
            .Where(p => p.CurrentUnitPrice is > 0)
            .GroupBy(p => p.District)
            .ToDictionary(
                g => g.Key,
                g => g.Average(p => p.CurrentUnitPrice!.Value));

        foreach (var property in active)
        {
            var avgPrice = districtAvgPrices.GetValueOrDefault(property.District, 0m);
            property.Score = ScoreCalculator.Calculate(property, config, avgPrice);
            await repository.UpdatePropertyAsync(property, ct);
        }

        logger.LogInformation("Scored {Count} active properties", active.Count);
    }

    private async Task MarkMissingPropertiesAsync(CrawlRun run, HashSet<Guid> seenPropertyIds, CancellationToken ct)
    {
        var activeProperties = await repository.GetActivePropertiesAsync(ct);
        foreach (var property in activeProperties.Where(p => !seenPropertyIds.Contains(p.Id)))
        {
            StatusUpdater.IncrementMissing(property);
            if (property.Status == PropertyStatus.Delisted)
            {
                run.DelistedCount++;
                logger.LogInformation("Property {Id} delisted after {Count} missing batches",
                    property.Id, property.MissingCount);
            }
            await repository.UpdatePropertyAsync(property, ct);
        }
    }

    private async Task RunScraperAsync(
        ISourceScraper scraper,
        int platformIndex,
        CrawlRun run,
        IReadOnlyDictionary<string, DistrictCriteria> districtCriteria,
        ScoringConfig config,
        HashSet<Guid> seenPropertyIds,
        List<Property> knownProperties,
        CancellationToken ct)
    {
        progressState.StartPlatform(GetPlatformDisplayName(scraper.SourceSite), platformIndex);

        var sourceResult = new SourceRunResult
        {
            CrawlRunId = run.Id,
            SourceSite = scraper.SourceSite
        };

        try
        {
            using var scraperCts = CancellationTokenSource.CreateLinkedTokenSource(ct);
            scraperCts.CancelAfter(TimeSpan.FromMinutes(45)); // 單一爬蟲最多 45 分鐘，防止卡住整個排程

            var progress = new Progress<ScraperDistrictProgress>(p => progressState.UpdateDistrict(p));

            // 逐行政區抓完就立即處理/存檔，即使後面整體逾時被取消，
            // 已完成行政區的資料也不會隨例外一起遺失。
            async Task ProcessDistrictBatchAsync(IReadOnlyList<PropertyDto> districtDtos)
            {
                var validDtos = districtDtos.Where(d => PropertyNormalizer.MeetsTrackingCriteria(d, districtCriteria)).ToList();
                foreach (var dto in validDtos)
                {
                    var propertyId = await ProcessPropertyAsync(dto, run, config, knownProperties, ct);
                    if (propertyId.HasValue)
                        seenPropertyIds.Add(propertyId.Value);
                }
                sourceResult.FetchedCount += validDtos.Count;
            }

            await scraper.FetchAsync(districtCriteria, progress, ProcessDistrictBatchAsync, scraperCts.Token);

            sourceResult.Success = true;
        }
        catch (OperationCanceledException) when (!ct.IsCancellationRequested)
        {
            // scraperCts 超時，不是外部取消；已完成行政區的資料已透過 ProcessDistrictBatchAsync 存檔，
            // 僅卡住當下的那個行政區未完成。
            logger.LogError("Scraper {Source} exceeded 45-minute time limit and was cancelled ({Count} listings already saved)",
                scraper.SourceSite, sourceResult.FetchedCount);
            sourceResult.Success = false;
            sourceResult.ErrorMessage = "執行超時（45 分鐘）";
        }
        catch (Exception ex)
        {
            logger.LogError(ex, "Scraper {Source} failed", scraper.SourceSite);
            sourceResult.Success = false;
            sourceResult.ErrorMessage = ex.Message;
        }

        await repository.SaveSourceRunResultAsync(sourceResult, ct);
    }

    private async Task<Guid?> ProcessPropertyAsync(
        PropertyDto dto,
        CrawlRun run,
        ScoringConfig config,
        List<Property> knownProperties,
        CancellationToken ct)
    {
        try
        {
            var normalized = PropertyNormalizer.Normalize(dto);
            var existing = await repository.FindExistingPropertyAsync(dto.SourceListingKey, dto.SourceSite, ct);

            Property property;
            bool isNew;

            if (existing is null)
            {
                // Check for cross-platform duplicate among known active properties
                var duplicate = knownProperties.FirstOrDefault(p => DuplicateMatcher.IsDuplicate(p, dto));
                if (duplicate is not null)
                {
                    // Add this listing to the existing property instead of creating a new one
                    var dupListing = new Listing
                    {
                        PropertyId = duplicate.Id,
                        SourceSite = dto.SourceSite,
                        SourceListingKey = dto.SourceListingKey,
                        Title = normalized.Title,
                        Url = dto.Url,
                        PostedDate = dto.PostedDate,
                        LatestSourcePrice = dto.TotalPrice,
                        IsActive = true,
                        ImageUrl = dto.ImageUrl,
                    };
                    await repository.SaveListingAsync(dupListing, ct);

                    if (dto.TotalPrice < duplicate.CurrentTotalPrice)
                    {
                        duplicate.CurrentTotalPrice = dto.TotalPrice;
                        duplicate.CurrentUnitPrice = dto.UnitPrice;
                    }
                    duplicate.MissingCount = 0;
                    duplicate.LastSeenAt = DateTime.UtcNow;
                    await repository.UpdatePropertyAsync(duplicate, ct);

                    logger.LogInformation("Merged duplicate {Key} from {Source} into property {Id}",
                        dto.SourceListingKey, dto.SourceSite, duplicate.Id);
                    return duplicate.Id;
                }

                property = new Property
                {
                    City = normalized.City,
                    District = normalized.District,
                    Address = normalized.Address,
                    AreaPing = normalized.AreaPing,
                    Floor = normalized.Floor,
                    AgeYears = normalized.AgeYears,
                    HasParking = normalized.HasParking,
                    CurrentTotalPrice = normalized.TotalPrice,
                    CurrentUnitPrice = normalized.UnitPrice,
                    MissingCount = 0,
                    Status = PropertyStatus.Active
                };
                NewListingMarker.MarkAsNew(property);
                isNew = true;
                run.NewCount++;
                await repository.SavePropertyAsync(property, ct);
                knownProperties.Add(property); // track for subsequent cross-platform dedup

                var listing = new Listing
                {
                    PropertyId = property.Id,
                    SourceSite = dto.SourceSite,
                    SourceListingKey = dto.SourceListingKey,
                    Title = normalized.Title,
                    Url = dto.Url,
                    PostedDate = dto.PostedDate,
                    LatestSourcePrice = dto.TotalPrice,
                    IsActive = true,
                    ImageUrl = dto.ImageUrl,
                };
                await repository.SaveListingAsync(listing, ct);
            }
            else
            {
                property = existing;
                isNew = false;

                if (property.Status == PropertyStatus.Delisted)
                    StatusUpdater.ReactivateProperty(property);
                else
                    property.LastSeenAt = DateTime.UtcNow;

                property.MissingCount = 0;
                property.CurrentTotalPrice = dto.TotalPrice;
                property.CurrentUnitPrice = dto.UnitPrice;
                await repository.SavePropertyAsync(property, ct);

                // 補填 Listing.ImageUrl（遷移前的舊記錄值為 null）
                var existingListing = property.Listings
                    .FirstOrDefault(l => l.SourceListingKey == dto.SourceListingKey
                                      && l.SourceSite == dto.SourceSite);
                if (existingListing is not null && existingListing.ImageUrl is null && dto.ImageUrl is not null)
                    existingListing.ImageUrl = dto.ImageUrl;
            }

            // Use PriceChangeDetector for price comparison
            var prevPrice = isNew ? null : await repository.GetPreviousPriceAsync(property.Id, ct);
            var priceResult = PriceChangeDetector.Detect(
                prevPrice, dto.TotalPrice, config.BigDropPercent, config.BigDropAmount);

            if (priceResult.IsBigDrop) run.BigDropCount++;

            var historyEntry = new PriceHistoryEntry
            {
                PropertyId = property.Id,
                CrawlRunId = run.Id,
                SourceSite = dto.SourceSite,
                TotalPrice = dto.TotalPrice,
                UnitPrice = dto.UnitPrice,
                ChangeFlag = priceResult.Flag,
                ChangePercent = priceResult.ChangePercent,
                IsBigDrop = priceResult.IsBigDrop
            };
            await repository.SavePriceHistoryAsync(historyEntry, ct);

            return property.Id;
        }
        catch (Exception ex)
        {
            logger.LogError(ex, "Failed to process property {Key} from {Source}",
                dto.SourceListingKey, dto.SourceSite);
            return null;
        }
    }
}
