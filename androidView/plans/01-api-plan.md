# HouseLens Public Read API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 讓既有 .NET API 能安全提供 Android 外網唯讀查詢，保留私人 Web 的管理能力。

**Architecture:** 同一個 API binary 用 `Api:Mode` 選 `PublicReadOnly` 或 `PrivateManagement`。公開模式只映射規格允許的讀路由，使用標準 JwtBearer 與伺服器核准 user ID；私人模式沿用既有路由、僅綁 loopback。既有查詢直接小幅修改，不另建 service／repository。

**Tech Stack:** 既有 .NET 10／EF Core／Npgsql／xUnit；新增 `Microsoft.AspNetCore.Authentication.JwtBearer` 10.0.9；Caddy 2 作部署入口。

**Spec:** [../spec.md](../spec.md) §8–11；同讀 [../plan.md](../plan.md)。以下路徑以 repository root 為基準。

## Global Constraints

- 「第一版不刪清單完整歷史、不改降價／排序的既有語意。」
- 「公開 API 驗證 page ≥ 1、pageSize 1–100、合法排序／狀態／平台、非負價格及上下限，無效回 400。」
- 「API 以驗證後的 Supabase `sub` 作穩定使用者 ID。」
- 「公開入口只開放本規格的唯讀 API、必要 Auth callback 靜態檔及最小 health」。
- 「後端直接 port 亦不得對公網繞過入口」；不讀寫 `android/`、`androidtest/`，不重做資料遷移。
- 所有資料庫測試用操作者提供的**獨立測試 PostgreSQL**；不從 `appsettings.Local.json` 取共享房源連線。既有 fixture 會建立及刪除 `hl_test_<guid>` schema。

## Review Focus

1. `(page-1)*pageSize` 溢位或極大值 → A1 的 `OverflowOffset_Returns400`。
2. 數字 enum 值、未知或空來源混在合法來源中 → A1 全部拒絕，不忽略錯值擴大查詢。
3. 相同排序欄位跨三頁 → A1 固定 UUID 次序、不漏不重複。
4. 核准資格移除及簽章金鑰輪替 → A2 每次授權與真實 JWT/JWKS 測試。
5. 已快取圖片／未列入白名單的 GET／直接 port → A2、A3 必須同樣封鎖，不能只測寫方法。

---

## 檔案責任與共同契約

修改 `Program.cs` 組裝與模式、`Endpoints/PropertiesEndpoints.cs` 查詢邊界、`Endpoints/ConfigEndpoints.cs` 拆出單一地區 GET；只新增 `Auth/ApiAuthentication.cs` 集中 Auth 設定／policy。不改 Domain、Infrastructure 或 Worker。現有 Web callers 為 `frontend/src/services/api.ts`、`pages/PropertyList.vue`、`stores/filters.ts`、`components/FilterBar.vue`，其合法 query 不需修改。

公開 API 的完整映射：`GET /api/properties`、`GET /api/properties/{id:guid}`、`GET /api/analytics/districts`、`GET /api/analytics/top-properties`、`GET /api/config/districts`、`GET /api/crawl-runs/latest`、`GET /api/proxy/image`，全受 `ApprovedMember` 保護。`GET /health` 匿名，只回服務是否活著；不查 DB 或洩露環境值。其他 path／method 不映射（404／405），不只在反向代理擋掉。

`Api:Mode` 預設 `PublicReadOnly`，未知值拒絕啟動。私人模式由 launch profile／測試 fixture 明示，伺服器只使用 loopback URL，禁用任意公網 bind；公開模式也以 loopback Kestrel 交給反向代理，與私人實例用不同 process／port，不用 header 判斷模式。

Auth 設定鍵：`Auth:Authority`、`Auth:Audience=authenticated`、`Auth:ApprovedUserIds`（UUID 字串陣列）。公開模式缺少有效 HTTPS authority／audience 拒絕啟動；空核准名單允許啟動但所有有效身份都 403。名單透過可重載設定讀取，每次 policy 判定讀目前值；環境變數更新須重啟後才生效，文件不得宣稱立即熱更新。

### Task A1: 查詢驗證與穩定分頁

**Files:**
- Modify: `backend/src/HouseLens.Api/Endpoints/PropertiesEndpoints.cs`
- Create/Test: `backend/tests/HouseLens.IntegrationTests/Api/PropertyQueryValidationTests.cs`, `backend/tests/HouseLens.IntegrationTests/Api/PropertyPaginationTests.cs`
- Regression: `Api/PropertyFilterTests.cs`, `PropertyOrderingTests.cs`, `PropertyUnitPriceOrderingTests.cs`, `PropertyPriceHistoryTests.cs`（均在同一整合測試專案）。

**Interfaces:** Consumes 現有 `GetProperties` 的 query 與 DbContext；Produces 相同 JSON，非法 query 回 400 ProblemDetails，合法排序最後加 `Id` 升冪。`priceDrop` 仍是總價升冪、`unitPrice` null 在前、`postedDate` 是 FirstSeenAt、`priceDropped=false` 不篩選。

- [x] **Step 1: 寫失敗測試。** 在 `PropertyQueryValidationTests` 使用既有 factory；Theory 的 query 包含 page 0／-1、pageSize 0／101、負價、最低大於最高、空／未知 status 與 sortBy、`sourceSite=0`／999／空／混合合法與未知值、最大 int page。下面是測試的核心斷言；各 theory 使用完整 query 字串。
  ```csharp
  using var client = factory.CreateClient();
  var response = await client.GetAsync("/api/properties?" + query);
  response.StatusCode.Should().Be(HttpStatusCode.BadRequest);
  ```
  另測 `page=1&pageSize=100&minPrice=0&maxPrice=0`、合法大小寫與 repeated keys 為 200。`PropertyPaginationTests` 為四種排序各建立獨立區名、六個固定 UUID、相同排序值且亂序插入；讀三頁（每頁 2 筆）後再讀一次：`ids.Should().Equal(expectedIds)`、`ids.Distinct().Should().HaveCount(6)`，避免 theory 共用 DB 互相污染。
- [x] **Step 2: 確認 RED。** 執行下列命令，預期因非法值未回 400／缺唯一次排序而測試失敗，不能把缺 DB 連線當作 RED。
  ```powershell
  dotnet test backend/tests/HouseLens.IntegrationTests/HouseLens.IntegrationTests.csproj --filter "FullyQualifiedName~PropertyQueryValidationTests|FullyQualifiedName~PropertyPaginationTests"
  ```
- [x] **Step 3: 最小修改原端點。** 保留原簽名，查詢前驗證上述值，平台依 enum 名稱比對而非允許數字；用 long 計 offset，超出 int Skip 範圍回 400。每種既有排序加 `ThenBy(p => p.Id)`；不把查詢搬到未被使用的 `Application/Queries/PropertyQueryService.cs`。
- [x] **Step 4: 驗證 GREEN 與回歸。** 執行 `dotnet test backend/tests/HouseLens.IntegrationTests/HouseLens.IntegrationTests.csproj --filter "FullyQualifiedName~Property"`，全部 PASS，完整歷史與原排序語意測試不可刪改成較弱斷言。
- [x] **Step 5: 提交。** 只 stage 本任務 Files 的變更；`git commit -m "fix: validate property queries and stabilize pagination"`。

### Task A2: 公開唯讀模式與核准成員授權

**Files:**
- Modify: `backend/src/HouseLens.Api/Program.cs`, `backend/src/HouseLens.Api/HouseLens.Api.csproj`, `backend/src/HouseLens.Api/appsettings.json`, `backend/src/HouseLens.Api/Properties/launchSettings.json`, `backend/src/HouseLens.Api/Endpoints/ConfigEndpoints.cs`。
- Create: `backend/src/HouseLens.Api/Auth/ApiAuthentication.cs`
- Modify: `backend/tests/HouseLens.IntegrationTests/TestWebApplicationFactory.cs`
- Create/Test: `backend/tests/HouseLens.IntegrationTests/PublicApiWebApplicationFactory.cs`, `Auth/TestJwtIssuer.cs`, `Api/ApiAuthorizationTests.cs`, `Api/PublicRouteTests.cs`（後三者同整合測試根目錄）。

**Interfaces:**
- `ApiAuthentication.AddPublicApiAuthentication(this IServiceCollection services, IConfiguration configuration): IServiceCollection`；policy 名稱 `ApprovedMember`。
- `ConfigEndpoints.MapDistrictOptionsEndpoint(this IEndpointRouteBuilder app): IEndpointRouteBuilder` 只映射原 GetDistricts；原 `MapConfigEndpoints` 呼叫它，不能重複映射。
- Private fixture 明示 `Api:Mode=PrivateManagement`；Public fixture 採真實 JwtBearer，`TestJwtIssuer` 只在測試組件提供簽章 token 與假的 OIDC/JWKS HTTP handler，不啟動第二個測試網站。

- [ ] **Step 1: 加入測試與必要 JwtBearer 套件。** `ApiAuthorizationTests` 對全部公開資料 GET 驗證匿名 401、合法未核准 sub 403、核准 sub 可到 handler；錯誤 issuer／audience／過期／偽簽章 token 為 401。圖片以假上游／預填 cache 測試，不能連外取圖。
  ```csharp
  response.StatusCode.Should().Be(HttpStatusCode.Unauthorized); // 無 token
  denied.StatusCode.Should().Be(HttpStatusCode.Forbidden);      // 有效、未核准
  allowed.StatusCode.Should().Be(HttpStatusCode.OK);            // 已配置測試資料
  ```
  增加 `RemovedMember_IsRejectedWithSameToken`、`SigningKeyRotation_RefreshesJwks`（沿用 framework backchannel/configuration manager、調整測試 refresh interval，不 sleep）、`CachedImage_StillRequiresMembership`。`PublicRouteTests` 先以 EndpointDataSource 斷言沒有管理／寫入／SSE endpoint，通過後才做對應 HTTP 404／405 驗證，防止未完成的模式切換意外執行 crawler。私人既有 `ScoringEndpointTests` 仍成功。Startup 測試覆蓋無效模式、公開缺 Auth 與私人 non-loopback URL 拒絕，含 URL／Kestrel endpoint 設定覆寫路徑。
- [ ] **Step 2: 確認 RED。** `dotnet test backend/tests/HouseLens.IntegrationTests/HouseLens.IntegrationTests.csproj --filter "FullyQualifiedName~ApiAuthorizationTests|FullyQualifiedName~PublicRouteTests"`；預期目前匿名 GET 可通過、管理路由仍存在。
- [ ] **Step 3: 實作標準 JWT 與模式組裝。** Authority 為 `https://<project-ref>.supabase.co/auth/v1`，`MapInboundClaims=false`，驗 issuer／audience／signature／lifetime；policy 要求 `sub` 為核准 UUID。使用標準 discovery／JWKS refresh，不自己快取金鑰或寫驗章。公開 endpoints 掛 `app.MapGroup("").RequireAuthorization("ApprovedMember")`；只掛共同契約中的 mappings。私人模式保留 crawler registrations／完整 config／admin／SSE／Seed；公開模式不註冊 crawler、不執行 Seed（Seed 會 migration／寫資料）。health 明確匿名。Development launch profile 加模式與 loopback，production 預設不降級成匿名。
- [ ] **Step 4: 驗證 GREEN。** 重跑 Step 2，然後 `dotnet test backend/HouseLens.slnx --filter "Category!=Live"`；新授權測試與既有匿名私人管理測試全部 PASS。只使用獨立測試 DB。
- [ ] **Step 5: 提交。** stage 本任務精確檔案；`git commit -m "feat: expose authenticated read-only API mode"`。

### Task A3: 可驗證的 HTTPS 部署入口

**Files:**
- Create: `androidView/deploy/Caddyfile`, `androidView/deploy/README.md`, `androidView/deploy/assetlinks.json.example`, `androidView/deploy/callback.html`, `androidView/deploy/Verify-PublicApi.ps1`
- Modify: `backend/README.md`, `androidView/.gitignore`（若 A3 先於 B1，只新增 `.artifacts/` 規則）。

**Interfaces:** Consumes A2 公開 route 表、`Api:Mode`／Auth keys；Produces HTTPS origin、`/.well-known/assetlinks.json`、`/auth/callback`，供 B2／B7 使用。部署輸入用 `HOUSELENS_API_HOST` 與 `HOUSELENS_API_UPSTREAM`（loopback URL）；不把真值寫在 example。`Verify-PublicApi.ps1 -SelfTest` 為離線檢查；`-BaseUrl <uri>` 為正式檢查，token 從 `HOUSELENS_APPROVED_TEST_TOKEN`／`HOUSELENS_UNAPPROVED_TEST_TOKEN` 程序環境取得，禁止 echo／query token。

- [ ] **Step 1: 為檢查腳本寫 assert 自測。** 在同一腳本以 `-SelfTest` 驗證路由／方法清單與回應判斷：匿名資料 401、有效未核准 403、已核准清單 200、管理／SSE／寫路由 404／405；意外 200 必須 throw。正式模式只讀取安全 GET，寫方法以不存在寫 handler 的 `/api/properties` 作惰性探測；不得向正式 trigger／purge／config 端點發送修改請求。完整敏感路由／method 封鎖在 A2 隔離環境與 Caddy 設定檢查中驗證，不能拿共用資料試刪除。不建立泛用部署測試框架。先執行 `pwsh -File androidView/deploy/Verify-PublicApi.ps1 -SelfTest`，確認缺檢查邏輯時為 RED。
- [ ] **Step 2: 寫最小入口設定與 runbook。** Caddy 只允許上述七個精確 GET（id 使用 UUID 路徑 regexp）、health 與兩個靜態檔；其他拒絕，不以 `/api/*` 全開。callback 頁僅顯示返回 App 指示、不反射 query。上游 loopback／防火牆不公開 Kestrel。公開端用既存 schema，不執行 Seed；私人管理仍原位置。說明 .NET publish、程序重啟、Google provider、discovery/JWKS、非對稱 signing key、核准 sub、移除成員、日誌不記 token、有效 TLS／CA、回滾只換 binary/config 不清資料。若現有 Supabase 為 legacy HS256／無可用 discovery，先完成 signing-key 前置再上線，不加 insecure fallback。
- [ ] **Step 3: 靜態驗證。** `pwsh -File androidView/deploy/Verify-PublicApi.ps1 -SelfTest` → PASS；`caddy validate --config androidView/deploy/Caddyfile --adapter caddyfile` → valid（先設定非秘密 host/upstream）；`dotnet publish backend/src/HouseLens.Api/HouseLens.Api.csproj -c Release -o androidView/.artifacts/api` → success。`.artifacts` 由 B1 ignore，A3 若先執行須先加 `androidView/.gitignore` 的同一條規則。
- [ ] **Step 4: 取得主計畫部署輸入後做正式驗證。** 啟動公開程序及入口，確認 OIDC discovery 的 issuer/JWKS 與實際 token 一致；執行 Verify-PublicApi 的正式模式（已核准與未核准測試 token），從外部網路確認管理 port 無法連線。B7 再確認 App Link 與真機。沒有主機／網域／核准帳號時，本步保持未完成，不拿本機 200 代替外網驗收。
- [ ] **Step 5: 提交。** 只提交模板、驗證腳本與不含秘密的 runbook；`git commit -m "docs: add verified read-only API deployment"`。簽章金鑰、token、真實部署設定、產物不提交。

## 規畫依據與限制

[Supabase OIDC discovery](https://supabase.com/docs/guides/auth/oauth-server/oauth-flows)、[JWT signing keys](https://supabase.com/docs/guides/auth/signing-keys) 與 [JwtBearerOptions](https://learn.microsoft.com/en-us/dotnet/api/microsoft.aspnetcore.authentication.jwtbearer.jwtbeareroptions?view=aspnetcore-10.0) 作為 A2 依據。這些能力仍須在實際 Supabase 專案驗證；本次沒有連線或改設定。A3 Caddy 只是一份可攜入口設定，不引入主機供應商 SDK、容器編排或 CI/CD。
