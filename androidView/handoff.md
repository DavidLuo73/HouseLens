# HouseLens Android View 交接

更新日期：2026-11-07。使用繁體中文溝通。

## 文件維護規則
每次更新進度時，整份覆蓋 F:/SourceCode/HouseLens/androidView/handoff.md，並同步開發 worktree 的同名檔案。只保留目前有效規格、實際進度、操作方式與接續流程；完整細節以連結引用，未驗證項目不得標完成，不寫入金鑰、密碼或個資。

## 工作區與規格
- 開發 worktree：C:/Users/David/.codex/worktrees/9611/HouseLens，分支 feature/android-viewer。所有開發命令指定此 workdir，重用既有 worktree。
- F:/SourceCode/HouseLens 為交接入口，其 spec／plan 不是最新版；保留原目錄使用者既有未提交內容。
- 範圍 androidView/；不讀取或沿用 android/、androidtest/，不修改 backend／frontend。
- 使用者本次明確選擇 subagent-driven-development，取代舊 Native 執行方式：逐項新實作者、逐項獨立審查、最後整體審查。Ponytail full。
- 權威文件：[spec.md](C:/Users/David/.codex/worktrees/9611/HouseLens/androidView/spec.md)、[plan.md](C:/Users/David/.codex/worktrees/9611/HouseLens/androidView/plan.md)、[App計畫](C:/Users/David/.codex/worktrees/9611/HouseLens/androidView/plans/02-app-plan.md)、[Supabase計畫](C:/Users/David/.codex/worktrees/9611/HouseLens/androidView/plans/01-supabase-plan.md)、[README](C:/Users/David/.codex/worktrees/9611/HouseLens/androidView/README.md)。
- Kotlin＋Compose，直接使用 Supabase Auth＋Postgrest；Google登入限核准成員，只瀏覽，不新增 API 服務。

## 實際進度
| 任務 | 狀態 |
| --- | --- |
| B1 | 完成：專案、唯讀契約、格式／URL與獨立圖片client。 |
| B2 | 完成並通過獨立審查：Google／PKCE控制器、安全儲存、會員gate、callback、AppGraph／Application／Activity與登入畫面；提交423c653、清理修正2229e6f。真實Google驗收仍屬B7。 |
| B3 | 完成並通過審查：本機收藏／摘要、收藏畫面、PropertyCard、登出清摘要；提交c765200、3a5954a、b22e4cf。 |
| B4 | 完成並通過審查：找房篩選／分頁／條件保存與共用卡片補齊；提交6e842f9、b9aa7f5。 |
| B5 | 完成並通過審查：詳情／歷史、找房／收藏／帳號導覽與返回位置；提交4371de0、f23959f。 |
| B6 | 完成並通過審查：精選／區域分析、四主入口與帳號資料狀態；提交5f9fd9e。 |
| B7 | 整體旅程、真機／外網／無障礙效能及release交付待做。 |
| S1 | 完成並通過審查：四個invoker views／成員RLS／最小欄位權限與本機測試；提交a65a308。 |
| S2 | 正式Supabase安全設定與真實連線待做；不得未經檢閱即套用。 |

B2已把SecureAuthStorage接入SDK，使用精確 tw.houselens.viewer://auth/callback、持久pending單次消費與編碼後參數唯一性檢查。登入URL等待verifier持久化後才開Custom Tab，Google帳號選擇器明確設定。exchange／refresh先取得未import session，確認世代後才import；SDK背景刷新停用。

冷啟動先驗會員，離線可重試但不展示快取；401共用一次刷新，再次401回登入；403／未核准回拒絕。登出先增加世代、卸載session資源；儲存刪除失敗仍清SDK記憶體與嘗試銷毀金鑰，顯示StorageError供重試，不將錯誤外拋造成畫面崩潰。

圖片皆直接來自原刊登公開URL，不帶bearer／apikey、不觸發登入刷新；目前關閉圖片memory／disk cache以避免晚回填，效能取捨留B7量測。SDK清記憶體使用3.5.0 internal status API，升級時須重新核對。

目前已接上找房、精選、收藏、區域四主入口、詳情／歷史與帳號頁；使用fixture已驗證主要流程。正式Supabase／Google／RLS未驗收，不能視為可交付成品。

## 驗證與接續
- B2完整檢查：34個JVM、8個API26登入裝置測試、lint及Debug APK成功。後續callback／帳號選擇器修改經focused auth JVM＋8裝置測試驗證。
- B2審查修正：13個SessionController JVM＋4個API26儲存測試通過（含實際刪除失敗），複查規格及品質通過。沒有重跑未受影響的整套測試；lint最近結果0 errors／21 warnings，版本／icon／UseKtx提示。
- [完整B2報告](C:/Users/David/.codex/worktrees/9611/HouseLens/.superpowers/sdd/02-app-plan/task-B2-report.md)、[執行ledger](C:/Users/David/.codex/worktrees/9611/HouseLens/.superpowers/sdd/02-app-plan/progress.md)。不要重做已完成任務。
- B3完整檢查48個JVM、2個API26收藏操作、lint／Debug APK成功；後續兩輪刷新修正經10個SavedViewModel測試及test-host編譯通過。詳見 .superpowers/sdd/02-app-plan/task-B3-report.md。
- B3使用已安裝OkioStorage處理Windows原子檔案替換，維持真實檔案持久化測試；帳號命名空間、generation／寫入鎖防止登出後回填。refreshVisible更新可見集合，retry(id)僅重試單卡。
- B4完整檢查61個JVM、API26三項360dp／200%字級＋一項48dp觸控檢查、lint／Debug APK通過；後續初始化刷新修正經12個FindViewModel測試通過。報告 .superpowers/sdd/02-app-plan/task-B4-report.md。
- B5完整驗證71個JVM、5個API26詳情／導航測試、lint0 errors／22 warnings、Debug APK成功；帳號說明修正另跑指定測試1/1。搬至固定ui／support路徑後另驗證Kotlin編譯，未重跑整套。報告task-B5-report.md記錄初始compile-only RED偏差與後續行為驗證。
- 正式AppGraph介面由DefaultAppGraph實作；SessionResources提供session ViewModelStore、saved及find工廠。HouseLensNav位於ui/，使用NavHost entry生命週期與createSavedStateHandle；TestGraph僅在androidTest/support。收藏返回不自動重抓已有狀態項目，仍可明確retry。
- B6完整驗證81個JVM、9個API26相關畫面／導覽、lint0 errors／22 warnings與Debug APK通過；最後callback獨立性調整另跑focused1/1。task-B6-report.md已揭露初始完整JVM RED log誤覆寫，只剩實際觀察摘錄，後續null RED等log完整保留。
- 契約已銜接：topRated額外只讀viewer_district_stats.district補空區；DropDto.dropPercent可null並顯示未提供、NULL排序在後；區域跳找房使用PropertyQuery(districts=listOf(name))重設其他條件／在售，保留單次undo。
- S1五個檔案位於androidView/supabase。PostgreSQL17.11驗證47項角色／資料斷言、12項漂移拒絕、重跑安裝／回滾／停止讀權與writer相容性；合成設定獨立App複本81 JVM通過。報告 .superpowers/sdd/01-supabase-plan/task-S1-report.md。
- S1每次建立獨立loopback cluster（55439），已全部停止，資料／logs留在該plan workspace；原01-api-plan工具／資料未改。安裝腳本會拒絕未知權限／policy／owner／default grants等漂移，正式前仍須重新清查。安裝會短暫取得表鎖（10秒鎖等待）。
- B4待整體review：成功保存後舊錯誤提示未清、共用Theme預設surface角色偏紫；另一次既有收藏快速連點測試逾時，原碼重跑通過，可能是Flow先發布而in-flight guard未釋放，紀錄b4-fix1-unrelated-favorite-failure.log。不可把該失敗run當通過。
- 待整體review：B3 undo失敗後缺重試入口；B4保存錯誤未清／主題淡紫／收藏連點偶發逾時；B5 DetailScreenTest的retry測試直接呼叫vm.refresh掩蓋callback，另有Uri.parse提示。完整ledger保留證據，沒有默認已修復。
- 下一步B7：讀task-B7-brief.md，完成本機整體旅程／release輸入guard／交付檢查與可檢閱S2部署內容；正式安全設定、Google／外網、release簽署及實機量測不足仍明列未完成。整體branch review尚未執行。

## 環境
| 設定 | 值（相對worktree） |
| --- | --- |
| JAVA_HOME | C:/Program Files/Android/openjdk/jdk-21.0.8 |
| ANDROID_HOME | .superpowers/sdd/02-app-plan/tools/sdk |
| GRADLE_USER_HOME | .superpowers/sdd/02-app-plan/gradle-home |
| ANDROID_USER_HOME | .superpowers/sdd/02-app-plan/android-user |
| ANDROID_AVD_HOME | ANDROID_USER_HOME/avd |
| SDK／AVD | platform36、build-tools35.0.0、API26 google_apis x86_64；viewer-api26、emulator-5580 |
| 本機PostgreSQL | .superpowers/sdd/01-api-plan/pgsql/bin；隔離庫127.0.0.1:55437，未啟動 |

API26模擬器與Gradle daemon目前正在使用；保留ignored工具／本機設定。模擬器Hidden啟動參數：-avd viewer-api26 -no-window -no-audio -no-snapshot -no-boot-anim -gpu swiftshader_indirect -port 5580；須確認sys.boot_completed=1及package service。手動UI驗證前明確安裝APK。

完整檢查：androidView/gradlew.bat -p androidView :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug :app:assembleDebug --max-workers=2。PowerShell的-Pandroid.testInstrumentationRunnerArguments.class=...須整段加引號，指定method用Class#method。

本次sandbox只可寫F:/SourceCode/HouseLens；開發worktree寫入／build需require_escalated。曾有子代理授權請求停滯，必要時由實作者將精確腳本暫存F:/SourceCode/HouseLens/.superpowers/sdd/02-app-plan/transfer，主agent檢閱後仍以require_escalated在指定worktree執行；不得繞過授權。

Supabase公開設定已在ignored androidView/supabase.local.properties（supabase.url／supabase.publishableKey），重用不重新索取；DB管理憑證不得放APK。S1腳本已就緒，但正式RLS／exposed schema／Google provider／callback allowlist與核准成員尚待S2；正式變更前重新核對owner／grants／policies。真實Google登入、行動網路、真機效能、release簽署與整體review均未完成。





## 使用者提供的實機與簽署資訊
- Samsung S25+，One UI9.0、Android17（使用者提供，待ADB實際核對）。驗收前需USB連線並允許USB偵錯；不記錄實機序號於交接。
- 目前沒有release簽署設定。須準備可檢閱的新金鑰／本機設定建立流程，保留長期更新身分；不得以debug key當release交付。

