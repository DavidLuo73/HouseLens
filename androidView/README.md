# HouseLens Android Viewer

獨立 Kotlin／Compose 瀏覽 App，直連 Supabase Auth＋Postgrest。
開發工作區為 feature/android-viewer；既有網站與爬取程式不需修改。

## 建置

使用 JDK 21、Android SDK 36、build-tools 35.0.0。從 androidView 目錄執行：

    .\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug

在本機設定 JAVA_HOME、ANDROID_HOME（或 local.properties 的 sdk.dir），不要提交機器路徑。
supabase.local.properties 必須存在，格式如下：

    supabase.url=https://YOUR_PROJECT.supabase.co
    supabase.publishableKey=sb_publishable_YOUR_PUBLIC_KEY

此檔已忽略。APK 僅容納公開 publishable key，不可放 PostgreSQL 密碼、service-role／secret key。
Gradle wrapper 固定 8.13 並驗證官方 SHA256。依賴版本集中於 gradle/libs.versions.toml。

## 開發狀態

B1 已通過 18 個單元測試、lint（0 errors）及 Debug APK 建置：可啟動的未登入畫面、唯讀資料契約與合成 HTTP 測試。
B2登入控制器／畫面／callback已接線並通過獨立審查。完整檢查34個JVM／8個API26測試，後續清理失敗修正13個JVM／4個API26儲存測試通過；詳見handoff.md與B2報告。B3本機收藏與可重用卡片／畫面已完成並通過審查；四頁導覽、找房／詳情、SQL views／RLS、簽署APK與真機驗收尚未完成。
目前提供登入與帳號狀態，核准前會驗證會員資格；尚無房源瀏覽畫面，不能當作已完成的看房 App。

## 資料契約

資料使用 houselens schema；views 為 camelCase，底層表以 SerialName 對應 PascalCase。
每個資料方法先查 ViewerMembers；空會員列視為未核准，而非空房源。
清單每頁 20 筆，歷史以 500 筆請求並依實際回傳數繼續，避免伺服器上限截斷。
金額使用 BigDecimal；時間戳轉 Asia/Taipei，DateOnly 保持原日期。

Supabase 與圖片使用獨立 client。所有 redirect 預設關閉；圖片不附 Supabase 憑證。
HTTPS 圖片、HTTP(S) 刊登網址須通過 URL 檢查；影片交由原刊登網站。
信義 Referer 只附至精確 res.sinyi.com.tw host。

## 計畫與驗證

規格見 spec.md，工作順序見 plan.md。個別任務未通過不標成完成。
測試／工具產物位於 worktree 的 .superpowers/sdd/02-app-plan，均不提交。
正式 SQL 必須先通過本機隔離測試；Google／核准成員及行動網路驗收仍需 S2／B7。

Lint最近為0 errors／21 warnings，保留依賴新版／圖示及UseKtx提示；備份排除規則已加入。不為消除版本提示升級已驗證組合。


