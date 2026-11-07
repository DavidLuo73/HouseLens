# HouseLens Android View 交接

更新日期：2026-11-07。請以繁體中文溝通。

## 接續點與工作區

本輪已完成 B1，並完成 B2 的加密儲存與401重試子步驟。B2登入控制器／畫面尚未接線；S1／S2、B3–B7尚未實作。已有Debug APK，但畫面仍是未登入說明，不是完成的看房App。

- 實際開發 worktree：C:/Users/David/.codex/worktrees/9611/HouseLens
- 分支：feature/android-viewer；exec 必須明確指定此 workdir，不另建worktree。
- 原目錄 F:/SourceCode/HouseLens 有使用者既有未提交內容；本任務只同步此handoff，不在原目錄開發。
- 最新 spec／plan 都在 C: worktree；F: 的 spec／plan 仍是舊版。
- Native 執行已選定：主agent依序實作，產品完成後一次獨立review，不再詢問執行方式。
- 不讀取／沿用 android/、androidtest/；不改既有 backend／frontend。

## 文件與既定方向

- [spec](C:/Users/David/.codex/worktrees/9611/HouseLens/androidView/spec.md)
- [總計畫](C:/Users/David/.codex/worktrees/9611/HouseLens/androidView/plan.md)
- [App計畫](C:/Users/David/.codex/worktrees/9611/HouseLens/androidView/plans/02-app-plan.md)
- [Supabase計畫](C:/Users/David/.codex/worktrees/9611/HouseLens/androidView/plans/01-supabase-plan.md)
- [建置說明](C:/Users/David/.codex/worktrees/9611/HouseLens/androidView/README.md)

原生Kotlin＋Compose，直接用Supabase Auth＋Postgrest，只瀏覽資料；不新增.NET API、Edge Function、主機或同步服務。完整範圍仍含找房、詳情歷史、收藏、排行、大降價、區域分析。Google登入只開放核准本人／家人。UI／UX、品牌與返回行為依spec，不重問需求。

## 提交與驗證

- 設計基準e1894f1；舊A1／A2已以9b771c6、ecababb撤回，不重做。
- ff2f197：B1 App骨架、wrapper、DTO／BigDecimal、唯讀SDK接點、URL／圖片隔離、契約測試。
- 62232d8：B2子步驟，AndroidKeyStore AES/GCM＋AtomicFile儲存session/verifier/pending，備份排除；401共用刷新／最多重試一次／登出世代取消及測試。
- git diff e1894f1 -- backend frontend 仍無差異。
- B1計畫已勾選；B2保持未完成。AppGraph／HouseLensApplication延至B2有實際生命週期用途時建立，此判斷已記錄ledger。

最後完整命令：androidView/gradlew.bat -p androidView :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug :app:assembleDebug --max-workers=2

結果：JVM 22／22、Android 8.0 API26模擬器3／3通過，BUILD SUCCESSFUL。裝置測試含密文無token、重建讀回、一次性callback、key刪除／密文損壞、重設不碰收藏檔。lint為0 errors／20 warnings（19項新版依賴提示、1項launcher icon）。未關閉檢查或為提示升級版本。

TDD：B1 17測試stub RED→GREEN，再加圖片傳輸RED；B2授權4項中3項RED→GREEN；儲存3項在真正開機後RED→GREEN。環境錯誤未算RED。

Debug APK：androidView/app/build/outputs/apk/debug/app-debug.apk（ignored，非release交付）。aapt確認package tw.houselens.viewer、minSdk26／targetSdk36、MainActivity存在。最終log：.superpowers/sdd/02-app-plan/checkpoint-verification.log。

尚未視覺／導覽驗收。connected tests後嘗試直接啟動Activity得到未安裝／Activity不存在，未算啟動成功；下次手動測UI須先adb install APK，不假設runner留下App。真Google、正式RLS、行動網路、真機、效能、release簽署與整體review都未完成。

## B2下一步

現有createViewerClient仍用Auth minimalConfig的記憶體儲存；SecureAuthStorage尚未接入production client。主Activity沒有Google登入按鈕，不會查正式房源。

1. 讀B2完整brief，先寫SessionController／OAuthCallback測試。
2. 建OwnerKey／AuthState／SessionController、AuthScreen、AppGraph與Application，接安全storage到SDK兩個adapter。
3. 第二次401目前回ApiException(401)，接控制器後仍須回SignedOut；403已有onForbidden hook，須串版本遞增、清畫面／圖片快取，B3再串收藏摘要清除。
4. 冷啟動／資料載入查ViewerMembers；不能因本機有session就顯示資料。
5. 裝置callback／程序重建驗證完成才勾B2；正式Google成功仍留B7。

已查證Supabase Kotlin 3.5.0原始碼，避免重踩：

- getOAuthUrl(Google, redirectUrl=...)不是suspend，內部launch非同步保存verifier。先storage.beginLogin()，呼叫SDK，再以有限timeout等待本次storage.awaitVerifier()持久化成功，才開Custom Tab。
- exchangeCodeForSession(code, saveSession=false)及refreshSession(refreshToken)只回UserSession；先驗session version，再importSession(..., autoRefresh=false)。不要用會立即import的refreshCurrentSession處理登出競態。
- 不直接用handleDeeplinks：只驗scheme／host且立即import。自行驗完整tw.houselens.viewer://auth/callback、userinfo／port、唯一code、pending，以claimCallback原子消費；重送不交換。
- 正常App callback只有code；Google↔Supabase的state由server/provider管理。計畫的state不符應驗本機pending，不要求SDK未提供的state query、不覆寫provider state。
- 一次只允許一個PKCE交換；舊交換可能刪共用verifier，須完成／取消後才能開始新交易。
- 冷啟動有callback先處理pending，不可先clearSession（它會刪verifier）。signOut網路失敗仍要finally清本機；先增加version，拒絕晚回來的交換／refresh。
- minimalConfig後保持autoSetupPlatform預設值；設false可能讓awaitInitialization一直等待。
- Postgrest自己的timeout要設15秒，目前已設。價格上下限須包and，否則3.5.0丟掉同欄位第二條件，已有測試。
- URLBuilder預設port尚未解析，不能直接比Url.port；先build再比origin，目前已修正及測試。

參考：[AuthImpl](https://github.com/supabase-community/supabase-kt/blob/3.5.0/Auth/src/commonMain/kotlin/io/github/jan/supabase/auth/AuthImpl.kt)、[Auth API](https://github.com/supabase-community/supabase-kt/blob/3.5.0/Auth/src/commonMain/kotlin/io/github/jan/supabase/auth/Auth.kt)、[PKCE](https://supabase.com/docs/guides/auth/sessions/pkce-flow)。

## 工具與環境

全部在ignored scratch，不重新下載，不git clean -fdx。

- JAVA_HOME：C:/Program Files/Android/openjdk/jdk-21.0.8，不用PATH Java11。
- ANDROID_HOME：<worktree>/.superpowers/sdd/02-app-plan/tools/sdk
- GRADLE_USER_HOME：<worktree>/.superpowers/sdd/02-app-plan/gradle-home
- Gradle8.13 wrapper已獨立下載、校驗、啟動；SHA256為20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78。
- CLI已移至標準tools/sdk/cmdline-tools/latest/bin，舊bootstrap/cmdline-tools位置已無工具。此修正讓avdmanager能找到正確SDK根目錄。
- 已裝platform36、build-tools35.0.0、platform-tools、emulator37.2.13、API26 google_apis x86_64映像。
- AVD：viewer-api26；ANDROID_USER_HOME=<worktree>/.superpowers/sdd/02-app-plan/android-user；ANDROID_AVD_HOME為其avd子目錄。
- WHPX可用。啟動參數：-avd viewer-api26 -no-window -no-audio -no-snapshot -no-boot-anim -gpu swiftshader_indirect -port 5580。Windows Start-Process必須Hidden。
- 等adb -s emulator-5580 shell getprop sys.boot_completed為1、package service就緒才跑connected tests；僅出現adb device仍可能太早。
- PowerShell的-Pandroid.testInstrumentationRunnerArguments.class=...整個參數須加引號。
- 模擬器與本專案Gradle daemon已正常停止；adb server可能仍在。
- PostgreSQL17.11：.superpowers/sdd/01-api-plan/pgsql/bin；PGDATA在同plan目錄，127.0.0.1:55437，仍停止。本輪未使用。測試連線在同處test-connection.txt，不印出。

## 正式Supabase狀態

本輪未存取／變更正式資料庫或Auth。前次唯讀調查：houselens有12關聯，無RLS/policies，anon/authenticated無表讀寫權限；exposed schemas未確認。

Project URL／publishable key已存ignored androidView/supabase.local.properties；實際key名為supabase.url、supabase.publishableKey，不要重索取或貼出。DB管理連線仍僅在原本機backend設定，絕不放APK。

S1 SQL／views／RLS未寫；S2 Google provider、callback、核准user IDs、exposed schema待完成。正式變更須先有可檢閱SQL及隔離測試。

Ledger：.superpowers/sdd/02-app-plan/progress.md。舊API ledger只供追溯；scratch工具需保留。未測項保持未完成，產品整體完成後才做一次獨立review。
