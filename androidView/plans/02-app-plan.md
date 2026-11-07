# HouseLens Native Android App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. User selected this execution workflow on 2026-11-07. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成找房、詳情／歷史、精選、收藏、區域分析與 Google 核准帳號登入，交付可安裝 APK。

**Architecture:** 一個 `app` module，Compose screen＋ViewModel／StateFlow，手動建立 Supabase client、Auth 與本機儲存。資料直接使用 Supabase view／table DTO，只有收藏摘要需要獨立型別；不建立通用 Repository／UseCase 階層。Auth、網路與圖片工具使用既有套件，簡單圖表使用 Compose Canvas。

**Tech Stack:** Kotlin、Compose／Material 3／Navigation Compose、Ktor、Supabase Auth＋Postgrest、Coil、DataStore；版本基準見下表。

**Spec:** [../spec.md](../spec.md) 全文；同讀 [../plan.md](../plan.md) 與 [01-supabase-plan.md](01-supabase-plan.md) 的共同契約。所有檔案路徑以 repository root 為基準。

## Global Constraints

- 「Android 8.0（API 26）以上」；applicationId／namespace 定為 `tw.houselens.viewer`，不引用 `android/` 或 `androidtest/`。
- 「每頁 20 筆」；收藏刷新「最多 3 個並行」；請求逾時「15 秒」；401「最多重試一次」。
- 「金額萬元、面積坪，比例小數（`-0.05` → `▼ 5.0%`）」；時間戳顯示 Asia/Taipei，DateOnly 不位移。
- 「第一版淺色」；`#FF385C`／`#E31C5F`／`#FFF0F3`，頁底 `#F7F7F7`、白卡，卡片 12dp、輸入 8dp、邊距 16dp。
- 「按鈕、愛心、關閉、圖表操作與 chips 點擊區至少 48×48dp」；360dp 寬、200% 字級、TalkBack 可操作。
- 正式版 HTTPS；不自動播放影片，不帶 HouseLens token 至第三方，不建立爬取／寫房源／同步／推播功能。
- 清單只取摘要，歷史於詳情分批讀完整；保留網站的降價／排序語意。

## Review Focus

1. 不可取消的舊 HTTP 回應在帳號切換後完成 → B2 session 世代檢查，B3 不寫錯收藏命名空間。
2. `api.example.evil`、userinfo URL、跨 origin redirect → B1／B2 不洩露 Authorization。
3. Keystore invalidation／損壞 session／OAuth callback 重送 → B2 回登入且不清收藏 ID。
4. 同時間價格、零基準、極大十進位數 → B1 不失精度，B5 無 NaN／假趨勢。
5. 背景／程序重建後的 callback、403 與圖像快取 → B2／B7 不重複導航、不離線繞過成員檢查。

---

## 工具鏈與依賴基準

| 用途 | 固定基準 |
| --- | --- |
| 工具鏈 | JDK 21；Gradle wrapper 8.13；AGP 8.13.2；Kotlin／Compose compiler／serialization plugin 2.3.20；minSdk 26、compileSdk 36、targetSdk 36。 |
| UI | Compose BOM 2026.03.00；activity-compose 1.13.0；lifecycle-runtime-compose／viewmodel-compose 2.10.0；navigation-compose 2.9.8；browser 1.9.0。 |
| Auth／HTTP | Supabase BOM 3.5.0，只引入 auth-kt＋postgrest-kt；Ktor 3.4.2（core／okhttp／content-negotiation／serialization-kotlinx-json）；serialization-json 1.10.0；coroutines-android 1.10.2。 |
| 本機／圖片 | datastore-preferences 1.1.7；Coil compose＋network-ktor3 3.4.0。無 Room、Retrofit、第二套 DI 或圖表套件。 |
| 測試 | JUnit 4.13.2、coroutines-test 1.10.2、Ktor client-mock 3.4.2；Compose ui-test-junit4 依 BOM、AndroidX test runner 1.7.0／ext junit 1.3.0。 |

以上是計畫基準，尚未在此環境解析／編譯。B1 先實際驗證 dependency resolution 與 AAR metadata，不允許 `+` 版號或關閉相容性檢查；若有具體衝突，僅修正受影響版本並在本表及 README 記錄原因，不順便升級整套。依賴放 `gradle/libs.versions.toml`，不用另建 convention plugin。

版本依據：[AGP 8.13.2／Gradle／JDK](https://developer.android.com/build/releases/agp-8-13-0-release-notes)、[Android 16 SDK](https://developer.android.com/about/versions/16/setup-sdk)、[Compose 官方 samples 的 2026.03.00](https://github.com/android/compose-samples/releases/tag/v2026.03.00)、[Supabase 3.5.0 自身版本表](https://github.com/supabase-community/supabase-kt/blob/3.5.0/gradle/libs.versions.toml)、[Coil 3.4.0](https://github.com/coil-kt/coil/blob/3.4.0/gradle/libs.versions.toml)。Supabase Kotlin client 是社群維護套件，使用 Auth 與 Postgrest，不引入其他模組。[官方說明](https://supabase.com/docs/reference/kotlin/introduction)

## 檔案結構與測試慣例

以下簡寫是**固定路徑前綴**，不是待決定目錄：`M = androidView/app/src/main/java/tw/houselens/viewer/`、`T = androidView/app/src/test/java/tw/houselens/viewer/`、`I = androidView/app/src/androidTest/java/tw/houselens/viewer/`。例如 `M/data/ApiModels.kt` 即該完整 main 路徑。

| 位置 | 責任 |
| --- | --- |
| `M/MainActivity.kt`, `M/HouseLensApplication.kt`, `M/AppGraph.kt` | 單一入口、callback、手動注入；無業務大函式。 |
| `M/data/ApiModels.kt`, `HouseLensData.kt`, `SupabaseHouseLensData.kt`, `ValueFormats.kt`, `MediaUrls.kt` | DTO、唯一 API 測試接點、HTTP、十進位／時間、URL 規則。 |
| `M/auth/SecureAuthStorage.kt`, `SessionController.kt`, `AuthScreen.kt` | SDK 儲存介接、登入／授權生命週期、登入／帳號畫面。 |
| `M/saved/FavoriteStore.kt`, `SavedScreen.kt`, `SavedViewModel.kt` | 本機收藏持久化與畫面。 |
| `M/find/FindScreen.kt`, `FindViewModel.kt`, `FilterSheet.kt`, `FilterPreferences.kt` | 草稿篩選、列表與翻頁、保存套用條件。 |
| `M/detail/DetailScreen.kt`, `DetailViewModel.kt`, `PriceHistory.kt` | 詳情、歷史資料與顯示。 |
| `M/explore/ExploreScreen.kt`, `ExploreViewModel.kt`, `M/districts/DistrictsScreen.kt`, `DistrictsViewModel.kt` | 精選與區域。 |
| `M/ui/Theme.kt`, `PropertyCard.kt`, `SimpleCharts.kt`, `HouseLensNav.kt` | 品牌、共用卡片、簡單圖、正式導覽。 |

新增檔案只在所屬任務需要時建立，不先建立空目錄／空類別。每個 screen 使用自己的具體 state，不建通用 `BaseViewModel`。測試 FakeApi／fixture 位於 `T/support/FakeApi.kt`、`T/support/Fixtures.kt`；instrumentation 在 `I/support/TestGraph.kt` 使用可控 API 與登入狀態，不能讓 debug bypass 進正式 APK。

下文 `gradlew` 命令一律從 `androidView/` 執行；後端命令從 repository root 執行。測試片段是測試名稱／核心斷言，arrange 放在該測試或上述 test support，禁止為方便測試而加入 production fixture。

### Task B1: 可建置的專案與唯讀資料契約

**Files:** Create `androidView/settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar`, `.gitignore`, `README.md`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`；Create 上述 `M` 的入口、data、`ui/Theme.kt`；Test `T/data/ApiContractTest.kt`, `ValueFormatsTest.kt`, `MediaUrlsTest.kt` 及 test support。

**Interfaces:**
- `PropertyQuery(districts: List<String> = emptyList(), sourceSites: List<Int> = emptyList(), minPrice: BigDecimal? = null, maxPrice: BigDecimal? = null, hasParking: Boolean? = null, priceDropped: Boolean = false, status: Int = 0, sortBy: String = "score")`；`validateQuery(query: PropertyQuery): String?`。
- `HouseLensData` 的 suspend methods：`properties(query: PropertyQuery, page: Int): PropertyPage`、`property(id: String): PropertyDto?`、`topRated(): List<TopGroupDto>`、`bigDrops(page: Int, district: String? = null): DropPage`、`districtStats(): List<DistrictStatsDto>`、`districtOptions(): List<DistrictOptionDto>`、`latestRun(): CrawlRunDto?`（無紀錄回null）；`detail(id: String): PropertyDetails?`（分開讀摘要、來源與分批完整歷史），`PropertyDetails(property: PropertyDto, sources: List<SourceDto>, priceHistory: List<PriceEntryDto>)`。
- `DropPage(total: Int, items: List<DropDto>)`；`PropertyPage(total: Int, page: Int, pageSize: Int, items: List<PropertyDto>)`；同檔定義 `PropertyDto`、`PriceEntryDto`、`SourceDto`、`TopGroupDto`、`TopItemDto`、`DropDto`、`DistrictStatsDto`、`PriceBucketDto(range: String, count: Int)`、`TrendPointDto`、`DistrictOptionDto`、`CrawlRunDto` 與其來源結果。DTO 一對一映射 spec §8 的 SQL views 與底層表；view使用camelCase，表使用@SerialName對應PascalCase，未知欄位忽略；Postgrest結果以decodeList／count轉成畫面需要的集合，來源／狀態整數在邊界轉換。`PropertyDto` 只代表viewer_properties摘要，完整來源與歷史另由PropertyDetails承載；金額／比例／score 使用 BigDecimal＋同檔 serializer，日期仍為原 ISO 字串。不得經 Double 轉入金額。
- `SupabaseHouseLensData(client: SupabaseClient, refreshSession: suspend () -> Unit, onForbidden: suspend () -> Unit, sessionVersion: () -> Long)` 實作 API；`ApiException(status: Int?, retryAfterSeconds: Long?, message: String)` 為明確失敗型別，CancellationException 原樣傳播。
- `formatDrop(value: BigDecimal?): String`、`formatTaipeiTime(isoUtc: String): String`、`mediaUrl(raw: String?): String?`、`externalListingUrl(raw: String?): String?`；非法／不支援媒體回 null。`mergeDistrictOptions(config: List<DistrictOptionDto>, stats: List<DistrictStatsDto>): List<DistrictChoice>`，`DistrictChoice(name: String, cityLabel: String?)` 跨市同名合一且標明「所有同名行政區」。

- [x] **Step 1: 建最小測試宿主並確認工具鏈。** 安裝／選定 JDK 21、SDK 36 與 AGP 所需 build tools；僅設本程序 JAVA_HOME／ANDROID_HOME，wrapper 與 distribution SHA256 取官方發行。建立單 module、INTERNET 權限、讀取被忽略的supabase.local.properties產生BuildConfig；Project URL為HTTPS、Theme 與可啟動 Activity。ignore `.gradle/`、各 `build/`、`local.properties`、簽署檔、`.artifacts/`；不複製排除目錄。執行 `./gradlew.bat --version` 及 `:app:dependencies --configuration debugRuntimeClasspath`，確認版本可解析；此步不是功能 RED。
- [x] **Step 2: 寫失敗的契約／格式測試。** Ktor MockEngine 回傳合成列表／詳情／分析／錯誤 JSON，測資料methods、Postgrest編碼／Accept-Profile=houselens／SELECT-only、limit=20、exact count、null結果、drop事件時間，以及500筆一批的歷史查詢不因REST上限截斷。例：
  ```kotlin
  @Test fun fractional_drop_is_percent() {
      assertEquals("▼ 5.0%", formatDrop(BigDecimal("-0.05")))
  }
  @Test fun unsafe_media_urls_are_rejected() {
      assertNull(externalListingUrl("javascript:alert(1)"))
      assertNull(mediaUrl("http://img.example/a.jpg"))
  }
  ```
  補小數 `9007199254740993.01` round-trip 不失真、null 與 0 分開、台灣跨午夜與 DateOnly 原日期、重複區名、`res.sinyi.com.tw.evil`／userinfo 不得套用信義Referer或取得Supabase憑證；429 Retry-After 秒數與 HTTP-date、500 HTML、空 body 都保留可判斷狀態。
- [x] **Step 3: 確認 RED。** `./gradlew.bat :app:testDebugUnitTest --tests "tw.houselens.viewer.data.*"` → 因未實作契約／格式而 FAIL。
- [x] **Step 4: 最小實作。** 固定 timeout 15 秒、pageSize 20，安裝Postgrest(defaultSchema="houselens", propertyConversionMethod=SERIAL_NAME)，query用SDK的filter／order／range建構；每個資料方法先查會員列，未核准以ApiException(403, null, "此帳號尚未獲得使用權限")通知onForbidden，不把RLS空集合誤當無房源；建立所需 DTO 與上述 helpers，使用 kotlinx JSON。client 不自動追隨重新導向；僅可信 HTTPS Supabase project origin 可附 token；圖片只對精確信義host附Referer，不附Supabase憑證。Theme 使用 spec token、字級與對比，不加動態配色。B2 實作 token provider 之前，入口僅呈現未登入狀態，不用假 token／公開房源代替登入。
- [x] **Step 5: 驗證 GREEN。** 重跑 Step 3，再跑 `./gradlew.bat :app:assembleDebug :app:lintDebug` → PASS／BUILD SUCCESSFUL；記錄確切工具鏈。若 AAR 要求更高 compile SDK，依具體 metadata 做最小相容修正並記錄，不能降低 minSdk 相容要求或略過 lint。
- [x] **Step 6: 提交。** stage 本任務檔案（含 wrapper，排除機器設定／Supabase本機設定／產物）；`git commit -m "feat: add viewer app and read API contract"`。

### Task B2: Google 登入、加密 session 與授權生命週期

B2完成（423c653、2229e6f），登入控制器／callback／UI接線及清理失敗修正通過測試與獨立審查。真實Google驗收仍由B7執行。

**Files:** Create `M/auth/SecureAuthStorage.kt`, `SessionController.kt`, `AuthScreen.kt`, `app/src/main/res/xml/backup_rules.xml`, `data_extraction_rules.xml`；Modify `M/AppGraph.kt`, `MainActivity.kt`, `HouseLensApplication.kt`, `data/SupabaseHouseLensData.kt`, Manifest／strings；Test `T/auth/SessionControllerTest.kt`, `T/data/ApiAuthorizationTest.kt`, `I/auth/SecureAuthStorageTest.kt`, `OAuthCallbackTest.kt`。

**Interfaces:**
- `OwnerKey(projectUrl: String, userId: String)` 定義在 `auth/SessionController.kt`。`AuthState` 為 `Checking`、`SignedOut`、`Denied(email: String?)`、`Authorized(owner: OwnerKey, email: String)`；只有 Authorized 可掛載資料畫面。
- `SessionController` 提供 `state: StateFlow<AuthState>`、`version: Long`，suspend `restore()`、`startGoogleLogin(): Uri`、`handleCallback(uri: Uri)`、`accessToken(forceRefresh: Boolean = false): String`、`deny()`、`signOut()`。建構注入 Supabase client、加密儲存與 `verifyMembership: suspend (accessToken: String) -> Unit`，後者用SDK SELECT ViewerMembers 的自身UserId列，空集合就拒絕；不另建伺服器或auth endpoint。
- `SecureAuthStorage` 以 SDK 原有 `SessionManager`／`CodeVerifierCache` 介面儲存 session／PKCE，另保存單次 pending callback 狀態。以 AndroidKeyStore AES/GCM、每次獨立 IV＋AtomicFile 放 noBackupFilesDir；不自創加密算法。SDK 的 storage interface 見文末參考。

- [x] **Step 1: 寫失敗測試。** `SessionControllerTest` 用假的 SDK 邊界／HTTP 回應驗證取消、冷啟動 membership gate、403、callback path/state 不符、重複 callback、舊 session 慢回應、退出後不可刷新。`ApiAuthorizationTest` 並發兩個 401，只允許一次共用 refresh、每請求最多重送一次；403 不 refresh。測試 assertion 核心如下（counter 與 controlled replies 在 test support 建立）：
  ```kotlin
  assertEquals(1, refreshCalls)
  assertEquals(2, attemptsPerRequest.max())
  assertEquals(AuthState.Denied("member@example.test"), controller.state.value)
  assertFalse(renderedOwners.contains(previousOwner))
  ```
  MockEngine 驗證 API→第三方 302 不追送 token，第三方圖片無 Authorization。instrumentation 驗證重新建 storage 能讀回、密文不含 token、刪除 Keystore key／破壞檔案安全回登入、收藏 ID 未受影響；callback 程序重建後只能消費一次。
- [x] **Step 2: 確認 RED。** `./gradlew.bat :app:testDebugUnitTest --tests "tw.houselens.viewer.auth.*" --tests "tw.houselens.viewer.data.ApiAuthorizationTest"`；裝置上的 `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=tw.houselens.viewer.auth`。預期未完成 session gate／儲存而 FAIL，無裝置不能算測試成功。
- [x] **Step 3: 接 SDK 與 session 控制。** Supabase Auth `FlowType.PKCE`＋自訂兩個安全 storage adapters，透過 Custom Tab 開 URL，驗證 callback 的 scheme／host／path 與本次 pending session；Google↔Supabase state 由 SDK／provider 處理，不自行重寫協定。callback固定 `tw.houselens.viewer://auth/callback`，Supabase精確allowlist並啟用PKCE，不新增App Link網域；debug亦不放寬至wildcard。一次性 callback／PKCE state 持久化後才能開瀏覽器，成功／取消後清除。session refresh 使用 Mutex 與 token/version 雙重檢查，只在前景需要時刷新，不加背景服務。
- [x] **Step 4: 串接權限與清理。** 登入成功及每次資料載入先讀ViewerMembers確認資格；空會員列／403／登出先增加 version、取消舊 session 的資料工作、卸載舊 ViewModel／NavHost、關閉及清除該 session ImageLoader，再進拒絕／登入畫面。HTTP 完成時 version 不符視為取消，不能寫回畫面。B3 提供 purge 後掛入同一清理路徑。圖片使用獨立 ImageLoader 生命週期及 HTTPS／禁止 redirect 規則；本 App 直接載入原刊登公開圖片，不建立需登入的圖片端點，所有圖片請求均不帶 bearer／apikey、不觸發 session refresh。401 第二次失敗回登入；網路離線保留已載入畫面但冷啟動不得繞過 probe。
- [x] **Step 5: 驗證 GREEN。** 重跑 Step 2；`./gradlew.bat :app:lintDebug :app:assembleDebug` 成功。真 Google／PKCE callback 的正式成功驗證保留 B7，不以 mocked callback 當完成證據。
- [x] **Step 6: 提交。** `git commit -m "feat: add approved Google sign-in and secure sessions"`，只 stage 本任務檔案。

### Task B3: 本機收藏與收藏頁

**Files:** Create `M/saved/FavoriteStore.kt`, `SavedViewModel.kt`, `SavedScreen.kt`, `M/ui/PropertyCard.kt`；Modify `M/AppGraph.kt`／Auth 清理接點；Test `T/saved/FavoriteStoreTest.kt`, `SavedViewModelTest.kt`, `I/saved/SavedScreenTest.kt`。

**Interfaces:**
- `PropertySnapshot`（同 `FavoriteStore.kt`）只含 id、title、city、district、總價／單價、坪數、屋齡、樓層、車位、score、status、主圖片及標記，不含完整 priceHistory；`PropertyDto.toSnapshot(): PropertySnapshot`。
- `FavoriteEntry(id: String, savedAtEpochMillis: Long, snapshot: PropertySnapshot?)`；`FavoriteStore.observe(owner: OwnerKey): Flow<List<FavoriteEntry>>`、suspend `put(owner, entry)`、`remove(owner, id: String): FavoriteEntry?`、`purgeSnapshots(owner)`，全部原子更新。put 可還原原收藏時間，無需另建 UndoService。
- `SavedViewModel(owner, api: HouseLensData, store: FavoriteStore)`，`state: StateFlow<SavedState>`、`refreshVisible(ids: List<String>)`、`remove(id)`、`undo()`。SavedState 保留 entries、每項取得狀態（未更新／成功／查無物件／暫時失敗）與單次 undo entry。
- `PropertyCard(snapshot: PropertySnapshot, saved: Boolean, onOpen: () -> Unit, onSave: () -> Unit, onHistory: () -> Unit)` 為 Compose；`SavedScreen(viewModel, onOpen: (id: String, showHistory: Boolean) -> Unit)`。

- [x] **Step 1: 寫失敗測試。** 用 temporary-file DataStore，不建 DB；測重建 store、owner／origin 隔離、同時 put／remove 不遺失其他項目、失敗不得改成成功狀態、損壞資料不靜默覆蓋（顯示儲存失敗，保留可供修復的原檔）。核心斷言：
  ```kotlin
  assertEquals(listOf(propertyId), recreated.observe(owner).first().map { it.id })
  assertTrue(store.observe(otherOwner).first().isEmpty())
  assertTrue(maxConcurrentDetailRequests <= 3)
  assertEquals(savedAt, restoredEntry.savedAtEpochMillis)
  ```
  SavedViewModel fixture 至少 21 個收藏，僅對可見批次查房源摘要；null物件／網路錯誤／下架不同顯示。測 session 切換後的慢刷新不能把摘要寫到新 owner；purge 保留 IDs 並清摘要。Compose 測愛心不觸發 open、移除後可復原。
- [x] **Step 2: 確認 RED。** `./gradlew.bat :app:testDebugUnitTest --tests "tw.houselens.viewer.saved.*"` → FAIL；instrumentation 指定 `tw.houselens.viewer.saved`。
- [x] **Step 3: 實作 DataStore 與收藏畫面。** 每 owner 一個序列化集合，預設 savedAt 降冪；刷新 semaphore=3、只查 visible／near-visible ids，沒有批次 API 或全表掃描。跨頁共用 store Flow，愛心立即回應，失敗回復，undo 用原 entry。圖片比例 4:3，適當文字占位；已移除仍保留移除按鈕。登出／deny 串 purgeSnapshots，收藏檔排除雲端備份以維持裝置獨立。
- [x] **Step 4: 驗證 GREEN。** 重跑單元與指定 instrumentation，驗證重啟持久化及隔離；不得把持久化失敗測試改成只測記憶體 list。
- [x] **Step 5: 提交。** `git commit -m "feat: add device-local saved properties"`。

### Task B4: 找房、草稿篩選與分頁

**Files:** Create `M/find/FindViewModel.kt`, `FindScreen.kt`, `FilterSheet.kt`, `FilterPreferences.kt`；Modify `M/ui/PropertyCard.kt`, `AppGraph.kt`；Test `T/find/FindViewModelTest.kt`, `FilterPreferencesTest.kt`, `I/find/FindScreenTest.kt`。

**Interfaces:**
- `FilterPreferences.observe(owner: OwnerKey): Flow<PropertyQuery>`、suspend `save(owner, query)`，只存已套用條件、不存 page，復用 DataStore。
- `FindViewModel(owner, api, favorites, preferences, savedStateHandle)`，`state: StateFlow<FindState>`、`apply(query: PropertyQuery)`、`refresh()`、`loadNext()`、`setDistrictFromAnalytics(name: String)`、`undoDistrictChange()`。FindState 欄位為 query、items、total、loadedPage、initialLoading、refreshing、appending、error、appendError、districtChoices、optionsError、fetchedAt。
- `FindScreen(viewModel, onOpen: (String, Boolean) -> Unit)`；`FilterSheet(initial: PropertyQuery, choices: List<DistrictChoice>, onApply: (PropertyQuery) -> Unit, onDismiss: () -> Unit)`；事件由已套用 query 推導，不同時維護兩份 filter 真相。

- [x] **Step 1: 寫失敗測試。** controlled FakeApi 先送慢 A 再快 B，只能留下 B；取消草稿／草稿重設不呼叫 API，套用／移除 chip 回 page=1，reset 回 spec 預設。測全平台 chips、同名區、無效價格、options 失敗不擋全清單、三頁重複 ID 去重／loadNext 去重入、append 失敗保留資料、刷新成功才丟後頁、重建 preferences 只保存套用條件。
  ```kotlin
  assertEquals(newQuery, vm.state.value.query)
  assertEquals(expectedIds, vm.state.value.items.map { it.id })
  assertEquals(1, fake.requests.count { it.page == 2 })
  assertEquals(previousItems, vm.state.value.items) // 刷新失敗後
  ```
  Compose 測 360dp／200% 字級時面板可捲到套用、有48dp操作區、愛心／歷史／正文分離、空結果能清條件。區域跳轉覆寫 query 的同時保存單次 undo，復原後回原條件。
- [x] **Step 2: 確認 RED。** `./gradlew.bat :app:testDebugUnitTest --tests "tw.houselens.viewer.find.*"`，以及指定 `tw.houselens.viewer.find` 的 connectedDebugAndroidTest → FAIL。
- [x] **Step 3: 實作查詢與篩選狀態。** query 改動用 generation 或取消 job 排除舊結果；pageSize 固定 20、延伸頁按 ID 去重、最多一個 append 請求。僅成功提交時持久化條件，保留新請求前的可讀內容；loading／空／error／appendError 分開。依 spec 四個排序文案與真實 API 值，不自行按單頁重排。重啟第一頁；SavedStateHandle 保留 query／返回目標，只有資料存在時恢復 scroll。
- [x] **Step 4: 完成找房 UI。** 上方摘要、篩選、chips、結果／排序與 LazyColumn；使用 B3 卡片／收藏。保留骨架比例、圖片失敗占位、手動載入更多及下拉更新；選單關閉不提交。無 keyword、房數、面積區間假控制項。加入本次 fetchedAt 文字與錯誤重試，不把 fetchedAt 稱為房源更新。
- [x] **Step 5: 驗證 GREEN。** 重跑 Step 2；真機／模擬器檢查鍵盤與系統 insets，列表不橫向溢出。此任務交付可獨立測試的 FindScreen；正式詳情導覽由 B5 接，不新增臨時詳情頁。
- [x] **Step 6: 提交。** `git commit -m "feat: add property browsing and draft filters"`。

### Task B5: 詳情、價格歷史與正式返回流程

完成並通過審查（4371de0、f23959f）。初次RED僅編譯失敗，後續mutation／還原GREEN及真正integration RED已在task-B5-report.md如實記錄；不倒稱已達成最初behavioral TDD順序。

**Files:** Create `M/detail/DetailViewModel.kt`, `DetailScreen.kt`, `PriceHistory.kt`, `M/ui/SimpleCharts.kt`, `HouseLensNav.kt`；Modify MainActivity／AppGraph；Test `T/detail/PriceHistoryTest.kt`, `DetailViewModelTest.kt`, `I/detail/DetailScreenTest.kt`, `I/ui/NavigationTest.kt`。

**Interfaces:**
- `DetailViewModel(id: String, api: HouseLensData, owner: OwnerKey, favorites: FavoriteStore)`，呼叫 B1 的 detail(id)，state 的 details 為 PropertyDetails?，另有loading、error、notFound；`refresh()`。
- `PriceStats(high: BigDecimal?, low: BigDecimal?, change: BigDecimal?)`、`priceStats(entries: List<PriceEntryDto>): PriceStats`、`ChartPoint(x: Long, y: BigDecimal)`；`PriceHistory(details: PropertyDetails, initiallyExpanded: Boolean)` 為 Compose，首批紀錄 20 筆、每次加20。
- `TimePriceChart(points: List<ChartPoint>, onSelected: (List<Int>) -> Unit)`：相同 x 選取回所有重疊點索引，不假造 x 間距；`PriceBucketChart(buckets: List<PriceBucketDto>)` 於 B6 實作。
- `HouseLensNav(graph: AppGraph, owner: OwnerKey)` 使用 Navigation Compose；route `find`、`saved`、`property/{id}?history={boolean}`、`account`，B6 再補 `explore`、`districts`。返回依 spec，不用一份自製路由堆疊。

- [x] **Step 1: 寫失敗測試。** `PriceHistoryTest` 測0／1／多筆、首價0、null單價、同timestamp多筆、真實不等距時間、極大相近價格；圖形座標先用 BigDecimal 減去基準再轉螢幕浮點，所有座標有限，水平線也能畫。核心斷言：
  ```kotlin
  assertNull(priceStats(emptyList()).high)
  assertNull(priceStats(oneEntry).change)
  assertNull(priceStats(zeroBaseEntries).change)
  assertEquals(BigDecimal("-0.05"), priceStats(dropEntries).change)
  ```
  同timestamp造成首／末基準不唯一時 change=null，依原順序列出且說明無法判定期間變動；不編造先後。DetailViewModel 測null詳情與斷線分別處理、來源與歷史載入失敗可重試。Compose 測紀錄20→40、圖點有文字替代、來源選擇、非法scheme不開啟、圖／標題進站內。NavigationTest 測找房／收藏進詳情返回保留 scroll 與 query、history=true 定位歷史區、root 返回交系統。
- [x] **Step 2: 確認 RED。** `./gradlew.bat :app:testDebugUnitTest --tests "tw.houselens.viewer.detail.*"`；指定 `tw.houselens.viewer.detail` 及 `tw.houselens.viewer.ui.NavigationTest` 的 instrumentation → FAIL。
- [x] **Step 3: 實作詳情與簡單圖。** 由 API 資料依 spec 順序顯示，固定底部來源按鈕不遮內容；單張圖與影片占位，無圖庫／自動播放。圖 x 用 epoch millis 真實間距、直線、不平滑；同時間僅顯示觀測點／多筆 tooltip，不虛構連線順序。用 Canvas 與可讀資料列，不自建圖表 framework；標題「系統擷取價格紀錄」、跨平台說明、金額及時間沿用 B1。
- [x] **Step 4: 串正式導覽與收藏。** NavHost 按 owner／session version 建立，使用 saveState／restoreState 保留底部頁狀態；詳情返回不重新載入前頁。account 顯示 email、版本、本機收藏說明與登出。來源經 B1 驗證後用 Custom Tab，回 App 保留位置；無網址停用，不能把 token 放 URL。骨架／重試／查無物件／舊資料狀態完整。
- [x] **Step 5: 驗證 GREEN。** 重跑 Step 2，檢查旋轉、返回手勢、200% 字級及長地址。未有精選／區域頁前不建立可點但無內容的 tab；B6 組成最終四個入口。
- [x] **Step 6: 提交。** `git commit -m "feat: add property detail history and navigation"`。

### Task B6: 精選、區域分析與資料狀態

**Files:** Create `M/explore/ExploreViewModel.kt`, `ExploreScreen.kt`, `M/districts/DistrictsViewModel.kt`, `DistrictsScreen.kt`；Modify `M/ui/SimpleCharts.kt`, `HouseLensNav.kt`, `M/auth/AuthScreen.kt`（帳號資料狀態區）；Test `T/explore/ExploreViewModelTest.kt`, `T/districts/DistrictsViewModelTest.kt`, `I/explore/ExploreScreenTest.kt`, `I/districts/DistrictsScreenTest.kt`。

**Interfaces:**
- `ExploreViewModel(api)` → `state: StateFlow<ExploreState>`、`refresh()`、`loadNextDrops()`、`selectTab(tab: ExploreTab)`、`selectDistrict(name: String?)`；`ExploreTab.TopRated/BigDrop`；state 保存兩個獨立載入／error／內容／區域／位置，降價另存page／total及appendError。
- `DistrictsViewModel(api)` → `state: StateFlow<DistrictsState>`、`refresh()`、`selectDistrict(name: String)`；state 保存 stats、selectedDistrict、loading、error。
- `ExploreScreen(viewModel, onOpen: (String, Boolean) -> Unit)`；`DistrictsScreen(viewModel, onBrowseDistrict: (String) -> Unit)`。`PriceBucketDto(range: String, count: Int)` 定義於 B1 的 ApiModels，`PriceBucketChart` 只畫其伺服器分箱，不重算。

- [ ] **Step 1: 寫失敗測試。** 精選 fixture 驗證 Top 5 原順序、null 單價、空區；drop 依降幅降冪、ID tie-break，newPrice 保留為事件價，保留真實capturedAt／status，事件價不冒充現價。計數 `assertEquals(0, fake.detailRequests.size)`，證明不逐卡查詳情。區域測0筆顯示「—」、insufficientData說明、DateOnly不位移、單點／同價分布、同名區限制；全部精選與區域卡可進詳情／找房。
  ```kotlin
  assertEquals(expectedDropIds, vm.state.value.visibleDrops.map { it.id })
  assertEquals(listOf("中正區"), mergedChoices.map { it.name })
  assertEquals("2026-11-07", trendDateLabel) // DateOnly 原值
  ```
  補 latestRun null→「尚無紀錄」、completed且某來源失敗→明示該來源失敗，不呈現全平台已同步。
- [ ] **Step 2: 確認 RED。** `./gradlew.bat :app:testDebugUnitTest --tests "tw.houselens.viewer.explore.*" --tests "tw.houselens.viewer.districts.*"`，以及這兩個 package instrumentation → FAIL。
- [ ] **Step 3: 實作精簡卡片與區域圖。** 沒圖片欄位就不補 N+1；大降價固定歷史事件說明。區域標「刊登資料分析，非實價登錄」，分布採 Canvas 柱＋文字列、趨勢共用 B5 折線；不同日期按 epochDay 表示，非UTC轉日。無資料／錯誤／刷新各自可恢復。大降價以20筆原生分頁，切地區回第1頁並傳入district；資料狀態僅開頁／手動刷新讀 latest，不 SSE／背景輪詢；不顯示原始伺服器 exception。
- [ ] **Step 4: 完成四個主入口。** 加 explore／districts routes，最終順序找房、精選、收藏、區域；各 tab 保留位置。查看本區房源使用 B4 `setDistrictFromAnalytics`，重設其他條件、提供 `undoDistrictChange`；不混用找房與精選的地區狀態。
- [ ] **Step 5: 驗證 GREEN。** 重跑 Step 2；NavigationTest 擴充四 tab 與區域跳轉還原，再 `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 全部成功。
- [ ] **Step 6: 提交。** `git commit -m "feat: complete curated and district browsing"`。
**續作裁定（B5完成後）：**
- 空排行行政區須可呈現。現有topRated只group已回傳TopItem，B6允許最小修改data/SupabaseHouseLensData.kt及其ApiContractTest：額外對viewer_district_stats只SELECT district，與排行group聯集，補空TopGroupDto；不下載完整stats／歷史、不做逐卡查詢，保留既有介面。
- 帳號資料狀態須接入目前真正的HouseLensNav account目的地；可重用AuthScreen.kt中的帳號呈現，但不可只放在未被掛載的Authorized分支。

### Task B7: 真機驗收與簽署 APK

**Files:** Modify `androidView/app/build.gradle.kts`, `README.md`；Create `androidView/app/src/androidTest/java/tw/houselens/viewer/ViewerJourneyTest.kt`, `androidView/validation/acceptance.md`；依量測結果只修改失敗行為的所屬檔案／測試，不預先建立 benchmark module。

**Interfaces:** Consumes S2 Supabase Auth／RLS／exposed-schema設定、B1–B6；Produces release APK（不版控）、SHA256、版本及逐項 AC 證據。簽署檔位置／密碼只從環境或被忽略的本機設定提供；release 缺 Supabase URL／publishable key／callback／signing 值必須 fail build，不 fallback 到 localhost 或 debug key。

- [ ] **Step 1: 寫最小整體旅程測試。** `ViewerJourneyTest` 在 instrumentation test graph 下跑登入已核准 → 找房套用 → 第二頁 → 收藏 → 詳情歷史 → 返回原位置 → 精選 → 區域 → 本區房源 → 還原 → 登出；用具體 testTag／文字斷言已套用區名、收藏 ID、原 scroll index 與登出後無房源。加背景／Activity recreation 後不重複導航，禁用所有業務 POST／PUT／PATCH／DELETE（Auth交換除外）。先跑指定 class，確認能抓到被破壞的導航／清理，再修最小缺口，不只用 screenshot 代替斷言。
- [ ] **Step 2: 執行完整自動檢查。** `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest :app:assembleRelease` → 全部 PASS；S1 SQL／RLS測試已通過。沒有裝置或 signing 輸入就保留未通過項，不宣稱 release 完成。
- [ ] **Step 3: 實際 Google 與外網驗證。** S2 完成後，以已核准／未核准帳號測冷啟動、系統瀏覽器、精確PKCE callback、取消、程序被終止後回呼、token到期／移除資格、登出換帳號。用行動網路且開發電腦關機直接從Supabase開清單／詳情；測斷網／重連／403清快取。測試只讀房源，不觸發既有爬蟲或通知。
- [ ] **Step 4: UI／效能量測。** 一台實機記錄型號與OS，另以 API26 模擬器檢查最低版本；360dp、200%字級、TalkBack、橫向、多視窗、鍵盤、手勢列逐項完成。使用20筆×每筆100筆歷史、RTT約100ms／10Mbps的受控資料：至少20次首屏採P95≤3秒，返回≤300ms；Perfetto／系統frame工具驗證60Hz捲動至少95% frame≤16.7ms、無ANR。未達標先找實際瓶頸，不先加快取DB／batchAPI。記錄量測條件，正式房源測試資料不得灌入共用DB。
- [ ] **Step 5: 產物與證據檢查。** 驗證 release signing、package `tw.houselens.viewer`、minSdk26／target36、無debug bypass／明文token／私密憑證；`Get-FileHash app/build/outputs/apk/release/app-release.apk -Algorithm SHA256` 記錄雜湊。`validation/acceptance.md` 對應14項AC，未測項寫原因；提供APK安裝／更新及本機收藏限制說明，不上傳商店、不提交APK或含個資的錄影。
- [ ] **Step 6: 提交。** `git commit -m "test: verify viewer release journeys and delivery"`。交付APK與證據前做一次整體review；只要必要AC或外網／Google未完成，就不能標完成。

## Auth 與圖片實作參考

- [Supabase SessionManager](https://github.com/supabase-community/supabase-kt/blob/3.5.0/Auth/src/commonMain/kotlin/io/github/jan/supabase/auth/SessionManager.kt)：saveSession／loadSession／deleteSession。
- [CodeVerifierCache](https://github.com/supabase-community/supabase-kt/blob/3.5.0/Auth/src/commonMain/kotlin/io/github/jan/supabase/auth/CodeVerifierCache.kt)：saveCodeVerifier／loadCodeVerifier／deleteCodeVerifier。
- [AuthConfig](https://github.com/supabase-community/supabase-kt/blob/3.5.0/Auth/src/commonMain/kotlin/io/github/jan/supabase/auth/AuthConfig.kt)：明示 PKCE、自訂 sessionManager／codeVerifierCache，不能沿用可能明文的預設儲存。
- [Coil network](https://coil-kt.github.io/coil/network/)：只加入一種 network adapter；HTTP client 的 header 與 redirect 規則須在圖片管線同樣適用。

以上是後續實作／驗收步驟；本次未安裝套件、建置、登入或真機量測。此計畫不承諾未驗證的依賴組合或效能已可用。






