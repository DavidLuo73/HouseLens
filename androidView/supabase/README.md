# Supabase 唯讀查詢

`viewer.sql` 提供 Android Viewer 的四個 views、`ViewerMembers` 與最小 SELECT 權限。此目錄不含正式資料、使用者 ID、密碼或部署動作。S1 已在 PostgreSQL 17.11 隔離庫驗證；正式 Supabase／Google 登入與 Data API 開放仍屬 S2。

## 檔案與本機驗證

- `viewer.sql`：單一交易的正式候選安裝 SQL。
- `tests/bootstrap.sql`：僅建立本機合成 EF 表結構、假 Auth identity plumbing 與測試角色。
- `tests/verify.sql`：實際 `SET ROLE`／JWT claim 的權限與資料斷言，最後 `ROLLBACK`。
- `Test-ViewerSql.ps1`：建立全新 cluster／database、安裝兩次、測試、注入權限漂移並確認拒絕與原子回復、停止 cluster。

在 repository root 執行（Windows PowerShell 7；使用既有 `.superpowers/sdd/01-api-plan/pgsql/bin`，不安裝服務）：

```powershell
pwsh -NoProfile -File androidView/supabase/Test-ViewerSql.ps1 -SelfTest
pwsh -NoProfile -File androidView/supabase/Test-ViewerSql.ps1
```

預設僅綁定 `127.0.0.1:55439`。`-Port` 可選其他未使用的非特權埠；`-DatabaseHost` 只接受字面值 `127.0.0.1`，不接受 DNS、localhost 或其他主機。runner 拒絕占用埠，產生 `s1_test_<12 hex>` 名稱，並在任何 SQL 寫入前核對連線的 `data_directory` 是本次新建目錄。清除並於結束還原 process 的 `PG*` 環境設定；`psql -X -w -v ON_ERROR_STOP=1` 不讀啟動檔、不提示密碼。

每次輸出保留在 `.superpowers/sdd/01-supabase-plan/runs/<UTC>-<id>/`；bootstrap／安裝／重裝／驗證／漂移／rollback／stop 有各自 log。失敗的 RED 不會被後續 GREEN 覆寫。cluster 以 `finally` 停止，資料目錄保留供查證；runner 不刪檔、不 drop 任何 database，不碰舊 `01-api-plan` cluster。測試庫使用 loopback trust，僅有合成資料；同機程序在測試期間可連入，勿在共用不受信任主機執行。

## 安裝條件與權限

只在 S2 檢閱實際 owner／grants／policies、worker 身分與差異後，才將 `viewer.sql` 交給已核准的目標連線執行；使用 `psql -X --set ON_ERROR_STOP=1 --file androidView/supabase/viewer.sql`，連線憑證留在維運環境。不能把 bootstrap／verify SQL 用於正式庫。

前提：既有 `houselens`、`auth.users`、`auth.uid()`、`anon`／`authenticated`，以及 EF 的六張資料表。以六張表既有的共同 owner 執行；不更換 owner。owner 必須能在 `houselens` 建物件、參照 `auth.users` 與建立暫存物件。若實際 owner 不同，安裝會拒絕，先檢閱差異。

安裝鎖住六張表（`ACCESS EXCLUSIVE`，等待上限10秒），所以應安排短暫維護時段。整包成功才 COMMIT；錯誤須中止連線／ROLLBACK。已測試後段 policy 錯誤時，前段建立的 policy／RLS 變更完全回復。

`authenticated` 僅取得 `houselens` USAGE、明列欄位 SELECT 與四個 view SELECT；沒有 INSERT／UPDATE／DELETE、全表或 default grants。`ViewerMembers` 只見自己的 `UserId`，其餘表必須有會員列。匿名不授權；沒有 claim、未核准或撤權後看不到資料。會員新增／刪除由既有維運者操作，SQL 不預填任何真實身分。`SourceRunResults.ErrorMessage`、管理設定、`NotificationLogs` 不開放。

四個 view 都使用 `security_invoker=true`；RLS 不 FORCE，既有 owner 與 BYPASSRLS writer 的寫入保留。這兩項行為使用真正 PostgreSQL 角色驗證，沒有以 superuser 讀取當成 RLS 通過。[PostgreSQL invoker views](https://www.postgresql.org/docs/17/sql-createview.html)、[RLS 與 owner／BYPASSRLS](https://www.postgresql.org/docs/17/ddl-rowsecurity.html)。

同名受管物件用 `houselens-viewer:v1` comment 辨識，policy 同時比對 PostgreSQL 解析後的條件、角色與命令。重跑可恢復缺少的受管 grants，不 drop/recreate 既有資料。未知 policy／table或column grant／default grant、危險 API role membership（含 NOINHERIT）、FORCE RLS、definer view 或物件碰撞都會拒絕。既有 owner／superuser／BYPASSRLS grants 視為受信任；其他角色的未辨識資料 grants 必須人工檢閱。這不是整個 Supabase project 的 RPC／extension 安全稽核。

## 查詢語意

| View | 契約 |
| --- | --- |
| `viewer_properties` | camelCase；主刊登依 Listing.Id、首個非 null 圖片獨立選取；來源為去重排序 int[]；最近歷史依 CapturedAt／Id 降冪；曾降價不等於最近降價。 |
| `viewer_top_properties` | Active=0 且 score 非 null；依區名分組，score DESC／id ASC，前五名。 |
| `viewer_big_drops` | 各房源最近 IsBigDrop 事件（含下架／合併狀態）；事件價獨立於現價；比例是小數，未知比例保持 null；除數未知或0時原價／降價金額 null。 |
| `viewer_district_stats` | DistrictConfigs 名稱 UNION 實際房源區名；同名跨城市合併；在售且正總價是現況樣本。有效正單價優先，否則以總價／正坪數推算；無有效單價是 null。 |

統計0筆的 min／max／average 為 null、buckets 為空；少於2筆 `insufficientData=true`。價格分布使用精確 numeric 五等分、左閉右開且最後含最大值，保留0筆分箱；相同價格僅一箱。分箱標籤取0位小數；事件原價與降價金額各自取1位，保留 .NET midpoint-to-even，沒有新增公共 RPC helper。PostgreSQL numeric 的內建 round 是 midpoint-away，因此 SQL 對精確中點明確處理。[PostgreSQL numeric round](https://www.postgresql.org/docs/17/functions-math.html)

趨勢按 UTC 日期聚合目前在售房源的歷史；包含當前總價非正的在售房源，保留每個來源觀測，不做每房每日去重或補值。歷史單價缺失時使用歷史總價／目前坪數；無效觀測排除。日期順序固定；未新增索引／物化表，正式資料量的執行計畫與延遲留 S2／B7。

## REST／SDK 邊界與回復

SQL 安裝不會自行把 schema 加進 Data API。S2 需把 `houselens` 列為 exposed schema，再驗證 JWT／會員撤權、大小寫欄位、exact count／範圍、錯誤與 worker 相容性。App 使用 `Accept-Profile: houselens`；不要複製文件範例的 broad grants。[Supabase custom schemas](https://supabase.com/docs/guides/api/using-custom-schemas)

本機已執行既有 SDK `ApiContractTest`／`ApiAuthorizationTest` 與完整81個 JVM tests：由同一 HEAD 的 tracked app 建立隔離副本，寫入合成 `https://project.supabase.co`／`sb_publishable_test`，使用現有 SDK／Gradle cache offline 執行。正式 `supabase.local.properties` 未讀取、未更動。此 fixture 證明 SDK header／camelCase／count／pagination 契約，不能證明正式 REST 已開放。

若成功安裝後要暫停 Viewer，可先以 owner 撤銷此功能的會員查詢權限：

```sql
BEGIN;
REVOKE SELECT ("UserId") ON houselens."ViewerMembers" FROM authenticated;
REVOKE SELECT ON houselens.viewer_properties, houselens.viewer_top_properties,
 houselens.viewer_big_drops, houselens.viewer_district_stats FROM authenticated;
COMMIT;
```

在沒有額外權限漂移的已驗證配置中，基礎表 SELECT 也因 membership policy 無法讀名單而拒絕；worker 不受影響。此操作是暫停存取，不刪會員或資料，不關閉 RLS。重新檢閱後重跑 installer 可還原權限。若需完整移除物件／政策，先保存會員名單及原始 ACL／RLS snapshot，另外檢閱精確回復 SQL；不要以 CASCADE 或全 schema REVOKE／DROP 代替。
