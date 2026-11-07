# HouseLens Android Viewer

獨立 Kotlin／Compose 瀏覽 App，第一版開啟即進入找房，直接使用 Supabase publishable key 讀取 Postgrest，不需登入。既有網站與爬取程式不需修改。

## 建置

使用 JDK 21、Android SDK 36、build-tools 35.0.0。在 androidView 執行：

    .\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug

設定 JAVA_HOME、ANDROID_HOME（或 local.properties 的 sdk.dir），不要提交機器路徑。公開設定由環境 SUPABASE_URL／SUPABASE_PUBLISHABLE_KEY，或被忽略的 supabase.local.properties 提供（環境優先）：

    supabase.url=https://YOUR_PROJECT.supabase.co
    supabase.publishableKey=sb_publishable_YOUR_PUBLIC_KEY

APK 僅放公開 publishable key，不放 PostgreSQL 密碼、service-role／secret key。沒有 Google provider、會員列、Auth session 或 callback 前置要求。Gradle wrapper 固定 8.13 並驗證官方 SHA256；依賴集中於 gradle/libs.versions.toml。

## 瀏覽與本機資料

四個主入口為找房、精選、收藏、區域；資訊頁提供版本、本機收藏限制及可手動更新的資料狀態。保留草稿篩選／取消／套用、原位返回、分頁及失敗重試。

收藏與套用條件以 Supabase project URL＋保留的 local-device-v1 命名空間儲存在此裝置，重新啟動仍保留，不跨裝置同步。先前登入帳號的收藏不會自動匯入或刪除。清除 App 資料或解除安裝會失去本機收藏；更新時保留同一 package／簽章，勿為排除簽章錯誤解除安裝。

資料採 houselens schema；views 為 camelCase，底層表以 SerialName 對應 PascalCase。所有 business requests 只有 GET，不查 ViewerMembers，不交換或更新使用者 token。401／403 顯示有限、可重試的讀取錯誤；資料公開權限與 RLS 必須在服務端另行完成。

清單每頁20筆；歷史每次請求500筆並依實際回傳數續取。金額用 BigDecimal，時間戳顯示 Asia/Taipei，DateOnly 不位移。Supabase 與圖片使用獨立 client；redirect 關閉，圖片不帶 Supabase 憑證。HTTPS 圖片／HTTP(S) 刊登網址經 URL 檢查；信義 Referer 僅附至精確 res.sinyi.com.tw host。

## 驗證與交付界線

規格與最新匿名版本方向見 spec.md；逐項證據及未完成項見 [validation/acceptance.md](validation/acceptance.md)。合成 HTTP 不代表正式 Supabase 權限、外網或效能驗收通過。

    .\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest :app:assembleDebug --max-workers=2

多裝置時必須以 ANDROID_SERIAL 指定測試裝置。先核對精確 viewer package／資料；Gradle instrumentation 可能移除 target app，有既有資料時不可直接執行。API26 UI 檢查使用360dp／200%字級並於測後還原設定。

ViewerJourneyTest 掛載正式 ViewerContent／DefaultAppGraph／唯讀資料層，只替換 HTTP transport 與儲存目錄。測試匿名進入、三頁、收藏、100筆歷史、背景／Activity recreation、精確位置返回、精選／區域／復原、新 graph 保留收藏與實際 IME／系統返回。Activity recreation 不冒充程序終止測試。

## Release

Release 強制 HTTPS origin、公開 key 及長期 signing identity，沒有 unsigned／debug key fallback，不要求 callback。私鑰／密碼只由環境或被忽略的 signing.local.properties 提供：

| 環境 | signing.local.properties |
| --- | --- |
| VIEWER_SIGNING_STORE_FILE | signing.storeFile |
| VIEWER_SIGNING_STORE_PASSWORD | signing.storePassword |
| VIEWER_SIGNING_KEY_ALIAS | signing.keyAlias |
| VIEWER_SIGNING_KEY_PASSWORD | signing.keyPassword |

金鑰放在 git／worktree 外的私人持久資料夾並安全備份；遺失後無法更新既有安裝。不要提交 APK、keystore、私有設定或含個資錄影。

    .\validation\Test-ReleaseConfiguration.ps1
    .\gradlew.bat :app:assembleRelease
    .\validation\Test-ReleaseArtifact.ps1 -Sdk $env:ANDROID_HOME

第一個工具實際執行8個 release 拒絕案例並還原環境，不生成身分。後兩個需要真正 signing／公開設定。產物工具驗證 package、minSdk26／target36、非 debuggable／非 debug 簽章及敏感檔名，輸出版本、憑證與APK SHA256；檔名掃描不代表完整私密資訊稽核。
