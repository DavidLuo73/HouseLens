# Phase 1 Data Model: 房屋售價追蹤系統

對應 spec.md「Key Entities」與功能需求，定義持久化資料模型（EF Core 實體）。
所有金額以新台幣「萬元」為單位的整數或小數儲存（實作階段統一），坪數以小數儲存。

## 實體（Entities）

### Property（物件主檔）

代表一個實體房屋標的，可能對應多個平台刊登。

| 欄位 | 型別 | 說明 / 規則 |
|------|------|-------------|
| Id | Guid (PK) | 系統主鍵 |
| City | string | 縣市（新北市 / 桃園市） |
| District | string | 行政區（中和、永和…中壢、桃園） |
| Address | string? | 正規化後地址，可為空（來源未提供時標記未提供） |
| AreaPing | decimal | 坪數 |
| Floor | string? | 樓層（如「5/12」），保留原始呈現 |
| AgeYears | int? | 屋齡（年），來源未提供為 null |
| HasParking | bool | 是否有車位 |
| CurrentTotalPrice | decimal | 目前代表總價（取最新且最低刊登價，見 research §7） |
| CurrentUnitPrice | decimal? | 目前單價（總價 / 坪數，或來源提供） |
| Status | enum PropertyStatus | Active / Delisted / Merged |
| Score | decimal? | 最近一次計算之綜合評分（0–1 正規化後加權） |
| IsNew | bool | 是否為當前批次新上架（次一批次取消，FR-008） |
| FirstSeenAt | DateTime | 首次被記錄時間 |
| LastSeenAt | DateTime | 最近一次被任一來源抓到的時間 |
| MissingCount | int | 連續未被抓到的批次數（用於下架判定，research §8） |
| MergedIntoPropertyId | Guid? (FK→Property) | 若狀態為 Merged，指向主物件 |

關係：1 Property ──< N Listing；1 Property ──< N PriceHistoryEntry。

狀態轉移（PropertyStatus）：
```
Active ──(連續 2 批次未見)──> Delisted
Delisted ──(重新被抓到)──> Active
Active/Delisted ──(被判定為重複)──> Merged（MergedIntoPropertyId 指向主物件）
```

### Listing（刊登來源）

同一物件在某一平台上的一筆刊登。

| 欄位 | 型別 | 說明 / 規則 |
|------|------|-------------|
| Id | Guid (PK) | |
| PropertyId | Guid (FK→Property) | 所屬物件 |
| SourceSite | enum SourceSite | F591 / Rakuya / Sinyi / Yungching / TwHouse …（可增減，FR-003） |
| SourceListingKey | string | 來源站內物件識別（用於跨批次比對同一刊登） |
| Title | string | 標題 |
| Url | string | 物件連結 |
| PostedDate | DateTime? | 刊登日期，可為 null |
| LatestSourcePrice | decimal | 該來源最新回報總價 |
| IsActive | bool | 該刊登於最近批次是否仍存在 |

唯一性：`(SourceSite, SourceListingKey)` 唯一。關係：N Listing ──> 1 Property。

### PriceHistoryEntry（歷史價格記錄）

某物件在某抓取批次時間點的價格與變動標記。

| 欄位 | 型別 | 說明 / 規則 |
|------|------|-------------|
| Id | Guid (PK) | |
| PropertyId | Guid (FK→Property) | |
| CrawlRunId | Guid (FK→CrawlRun) | 來源批次 |
| CapturedAt | DateTime | 抓取時間 |
| TotalPrice | decimal | 當次總價 |
| UnitPrice | decimal? | 當次單價 |
| ChangeFlag | enum PriceChangeFlag | None / Increased / Decreased |
| ChangePercent | decimal? | 相對前次變動百分比 |
| IsBigDrop | bool | 是否達大降價門檻（research §3） |

規則：每物件每批次至多一筆；`ChangeFlag` 由與前一筆比較得出（FR-006、FR-007）。

### CrawlRun（抓取批次）

一次每日排程執行的彙總。

| 欄位 | 型別 | 說明 |
|------|------|------|
| Id | Guid (PK) | |
| StartedAt / FinishedAt | DateTime | 執行起迄 |
| Status | enum RunStatus | Running / Completed / Failed |
| NewCount / DelistedCount / BigDropCount | int | 本批次統計 |

關係：1 CrawlRun ──< N SourceRunResult。

### SourceRunResult（單一來源抓取結果）

記錄某批次中各來源的成敗（FR-014）。

| 欄位 | 型別 | 說明 |
|------|------|------|
| Id | Guid (PK) | |
| CrawlRunId | Guid (FK→CrawlRun) | |
| SourceSite | enum SourceSite | |
| Success | bool | 是否成功 |
| FetchedCount | int | 抓取到的符合條件物件數 |
| ErrorMessage | string? | 失敗原因 |

### TrackingCriteria（追蹤條件）

使用者設定的篩選範圍（單筆設定）。

| 欄位 | 型別 | 說明 |
|------|------|------|
| Id | int (PK) | |
| Districts | string (JSON) | 追蹤行政區清單（預設八區） |
| MaxTotalPrice | decimal | 總價上限（預設 800 萬） |

### ScoringConfig（評分與門檻設定）

驅動評分與降價偵測之可調設定（research §2、§3）。

| 欄位 | 型別 | 說明 |
|------|------|------|
| Id | int (PK) | |
| WeightUnitPrice / WeightAge / WeightParking / WeightLocation | decimal | 評分權重（合計 1.0） |
| BigDropPercent | decimal | 大降價百分比門檻（預設 0.05） |
| BigDropAmount | decimal | 大降價金額門檻（萬元，預設 30） |

## 列舉（Enums）

- **PropertyStatus**: Active, Delisted, Merged
- **SourceSite**: F591, Rakuya, Sinyi, Yungching, TwHouse（可擴充）
- **PriceChangeFlag**: None, Increased, Decreased
- **RunStatus**: Running, Completed, Failed

## 驗證規則（摘自需求）

- Property MUST 落在 TrackingCriteria.Districts 且 CurrentTotalPrice ≤ MaxTotalPrice（FR-002）。
- 必要欄位缺漏時以「未提供」呈現，不可丟棄整筆（FR-004）。
- 去重比對鍵：正規化地址 + 坪數(±0.5) + 樓層(相同) + 屋齡(±1) 皆符 → 合併（FR-005、research §7）。
- 下架判定：MissingCount ≥ 2 → Status=Delisted，保留歷史（FR-007、research §8）。
- ScoringConfig 四項權重 MUST 合計為 1.0。
