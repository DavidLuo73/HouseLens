# Supabase 匿名唯讀查詢（第一版免登入）

`viewer.sql` 的匿名 v1 已於2026-11-07取得使用者明確核准後部署；使用者另在 Dashboard 開放 `houselens`，保留原有 `graphql_public`／`public`。它沿用已檢閱的四個 invoker views 與六張表的最小欄位契約，不建立 Auth 身分、不建立／讀取會員名單、不要求 Google 登入。正式 REST 零列讀取與實際 Kotlin SDK 的 `limit=1` 匿名 GET／解碼已通過；完整實機使用與效能驗收仍另行追蹤。

**這是公開讀取權限。** 安裝並開放 Data API 後，任何持有可公開的 publishable key 的客戶端都能讀取下列全部列，不限 APK 持有人，不能用包在 APK 的 key 當存取秘密。App 不需要使用者 bearer JWT。未授予匿名寫入、ErrorMessage、會員身分、NotificationLogs 或管理設定權限。

## 檔案與本機驗證

- `viewer.sql`：原 S1 的 coherent v1 replacement；保留 view 查詢內容，權限改為匿名 SELECT。舊 authenticated-v1 marker 明確拒絕，不能當成舊版正式庫的自動升級。
- `revoke.sql`：精確撤銷本功能匿名欄位／views SELECT 與 schema USAGE；保留所有資料、views、SELECT policies 與非 FORCE RLS。
- `tests/bootstrap.sql`：只有合成 EF 表結構與本機角色；刻意不建立 Auth schema。私有 ViewerMembers 空表僅供拒絕列舉測試。
- `tests/verify.sql`：真正 `SET ROLE anon` 的無身分查詢、分頁／數值／日期契約、私有資料／寫入拒絕與 owner／BYPASSRLS 相容性；fixture 最後 ROLLBACK。
- `tests/verify-revoke.sql`：實際查詢被拒絕、owner insert 與 BYPASSRLS update 保留。
- `Test-ViewerSql.ps1`：新建獨立 cluster，安裝／重裝、權限漂移拒絕、原子回復、撤權／重複撤權、重新安裝還原，最後停止並保留 cluster。

在 repository root 執行（PowerShell 7；使用既有 `.superpowers/sdd/01-api-plan/pgsql/bin`，不安裝服務）：

```powershell
pwsh -NoProfile -File androidView/supabase/Test-ViewerSql.ps1 -SelfTest
pwsh -NoProfile -File androidView/supabase/Test-ViewerSql.ps1
```

runner 僅接受字面值 `127.0.0.1`、非特權未占用埠（預設55439）、本次產生的 `s1_test_<12 hex>`，SQL 寫入前核對 `data_directory`。清除並於 finally 還原 process 的 PG 環境變數；`psql -X -w -v ON_ERROR_STOP=1` 不讀啟動檔、不提示密碼。每次 log 與資料保留在 `.superpowers/sdd/01-supabase-plan/runs/<UTC>-<id>/`，不覆寫失敗證據、不刪 cluster、不 drop database。loopback trust 僅用於合成資料；同機程序測試期間可連線，勿在不受信任共用主機執行。

## 安裝範圍與安全檢查

前提：PostgreSQL15+、既有 `houselens`、`anon`／`authenticated` 角色與下列六張 ordinary tables。以 schema／六表共同既有 owner 執行，不更換 owner。只授予 `anon` schema USAGE、明列欄位 SELECT 及四個 view SELECT；`authenticated` 沒有新權限。

| 表 | 唯一公開欄位 |
| --- | --- |
| Properties | Id, City, District, Address, AreaPing, Floor, AgeYears, HasParking, CurrentTotalPrice, CurrentUnitPrice, Status, Score, IsNew, FirstSeenAt, LastSeenAt |
| Listings | Id, PropertyId, SourceSite, Title, Url, ImageUrl, PostedDate |
| PriceHistoryEntries | Id, PropertyId, CapturedAt, TotalPrice, UnitPrice, SourceSite, ChangeFlag, ChangePercent, IsBigDrop |
| DistrictConfigs | City, District, IsEnabled |
| CrawlRuns | Id, StartedAt, FinishedAt, Status, NewCount, DelistedCount, BigDropCount |
| SourceRunResults | CrawlRunId, SourceSite, Success, FetchedCount |

四個公開 views 為 `viewer_properties`、`viewer_top_properties`、`viewer_big_drops`、`viewer_district_stats`，全部 `security_invoker=true`。六表啟用非 FORCE RLS，各自只有 `FOR SELECT TO anon USING (true)` policy，名稱 `viewer_public_read`。既有 owner 與 BYPASSRLS writer 的寫入路徑保留；其他 writer role 必須另行檢閱。

交易要求六表 ACCESS EXCLUSIVE lock（等候最多10秒），需短暫維護時段。整包成功才 COMMIT，錯誤須中止連線／ROLLBACK。沒有 housing row、Auth identity、會員列、管理資料修改，也沒有 default grants、全表 SELECT 或 API 寫入授權。

同名 views 與 policies 以 `houselens-viewer:anonymous-v1` 識別，policy 另比對 PostgreSQL 解析後的命令／角色／permissive／條件／WITH CHECK。重跑可還原缺少的受管 grants。未知 table／column／default grants、API sequence access、未知或改變的 policy、危險角色 membership（含 NOINHERIT）、FORCE RLS、definer view、物件碰撞都拒絕。舊 `houselens-viewer:v1` 物件拒絕並要求明確 migration review。這不是全 project RPC／extension 安全稽核。

## 查詢語意

| View | 契約 |
| --- | --- |
| `viewer_properties` | camelCase；主刊登依 Listing.Id、首個非 null 圖片獨立選取；來源為去重排序 int[]；最近歷史依 CapturedAt／Id 降冪；曾降價不等於最近降價。 |
| `viewer_top_properties` | Active=0 且 score 非 null；依區名分組，score DESC／id ASC，前五名。 |
| `viewer_big_drops` | 各房源最近 IsBigDrop 事件（含下架／合併狀態）；事件價獨立於現價；比例是小數，未知比例保持 null；除數未知或0時原價／降價金額 null。 |
| `viewer_district_stats` | DistrictConfigs 名稱 UNION 實際房源區名；同名跨城市合併；在售且正總價是現況樣本。有效正單價優先，否則以總價／正坪數推算；無有效單價是 null。 |

統計0筆的 min／max／average 為 null、buckets 為空；少於2筆 `insufficientData=true`。價格分布使用精確 numeric 五等分、左閉右開且最後含最大值，保留0筆分箱；相同價格僅一箱。分箱標籤取0位小數；事件原價與降價金額各自取1位，保留 .NET midpoint-to-even，沒有新增公共 RPC helper。PostgreSQL numeric 的內建 round 是 midpoint-away，因此 SQL 對精確中點明確處理。[PostgreSQL numeric round](https://www.postgresql.org/docs/17/functions-math.html)

趨勢按 UTC 日期聚合目前在售房源的歷史；包含當前總價非正的在售房源，保留每個來源觀測，不做每房每日去重或補值。歷史單價缺失時使用歷史總價／目前坪數；無效觀測排除。日期順序固定；未新增索引／物化表，正式資料量的執行計畫與延遲留 S2／B7。

## 正式部署狀態與回復（2026-11-07）

使用者已明確核准上述最小欄位與全部列的匿名讀取及 schema 開放，並確認目前沒有執行中的 Worker。沒有啟動爬蟲，也沒有正式庫試寫。

1. 部署前以 VerifyFull TLS 執行 catalog-only preflight，確認既有 `postgres` 為 schema／六表共同 owner，沒有衝突 ACL／RLS／policies；`houselens` 沒有 routines，沒有新暴露 RPC。證據：F transfer `anonymous-deploy-runs/20261107T105551072Z-8641f71fb8e5`。
2. Root 以明確執行開關及「無執行中 Worker」確認，執行 SHA256 鎖定的已檢閱 SQL：`C1DDA8912FB1D362E11E3608FEA773BAA266E47546A9A4100A845A60F12F2D09`。Apply 成功；後置 metadata 確認六張非 FORCE RLS 表、六個 anon SELECT policies、四個 invoker views，API 寫入權限與 houselens routines 都為0。helper 沒有讀取 housing rows／Auth identities，也沒有改 exposed-schema 設定。證據：F transfer `anonymous-deploy-runs/20261107T105812075Z-3bc820570427`。
3. 當時 REST 只回覆 `PGRST106 / Invalid schema: houselens`，無法取得原有 active schema 清單，因此沒有猜測或寫入 database／role override。使用者在 Dashboard 手動加入 `houselens`，畫面保留 `graphql_public`／`public`；後續匿名 `Accept-Profile: houselens`、`limit=0` GET 為 HTTP200。證據：F transfer `anonymous-deploy-runs/20261107T110749839Z-d77ab82493b7`。
4. 已用安裝版本的 Kotlin SDK 對正式服務執行匿名 `limit=1` GET，HTTP200 且 `JsonObject` 解碼通過。證據：`.superpowers/sdd/02-app-plan/anonymous-live-sdk-20261107-191008-256`。這是一次受限的真實讀取，與本機合成 PostgreSQL 權限／數值契約套件分開計算，不能視為完整業務 DTO、四頁查詢、所有分頁／錯誤／撤權或實機效能驗收。

本機 suite 已驗證 owner／BYPASSRLS writer 相容性；正式環境目前沒有 Worker，因此未宣稱正式 writer 執行驗證。未來若新增其他 writer role，須重新檢閱 RLS／grants。後續重裝或擴大公開範圍仍須重新檢查當時狀態與核准範圍。

暫停匿名讀取，以相同 owner 執行 `psql -X --set ON_ERROR_STOP=1 --file androidView/supabase/revoke.sql`。它只撤銷前述明列欄位、四 views 的 anon SELECT 及 houselens USAGE，不刪資料／物件／政策、不關閉 RLS、不改 writer grants。最後檢查 anonymous 沒有剩餘有效 table／column／sequence／schema 權限；若另有 PUBLIC／繼承 grant，整個撤權交易會拒絕，必須檢閱並處理漂移，不能宣稱撤權成功。

撤權可重跑；重新檢閱後跑 `viewer.sql` 恢復。若也要還原 exposed-schema 設定，只移除此次新增的 houselens entry，保留原本設定。完整移除 views／policies／RLS 屬另外的 reviewed rollback，不使用 CASCADE 或全 schema DROP／REVOKE。

已完成的正式驗證限上述 metadata、零列 REST 與 SDK 單次受限讀取；未執行正式寫入／撤權試驗。完整實機互動與網路情境、所有正式查詢／分頁／錯誤契約、真實資料量的執行計畫／延遲及整個 project 的 RPC／extension 稽核仍未由此證明；整體交付與驗收狀態見 app handoff／acceptance。
