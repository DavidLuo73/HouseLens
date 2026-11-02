# Task 1 執行紀錄

計畫：`android/plans/2026-11-03-probe-apk-implementation.md`。Base：`7ead221`；分支：`android/probe-bootstrap`。

範圍：獨立 Gradle app、manifest/resources、最小 MainActivity、共用模型與來源碼契約測試。Task 2–14 尚未開始。

## 決定與介面核對

- Ruling: 使用者指定所有 Android 程式碼在既有 `F:/SourceCode/HouseLens/android/`，因此在原目錄建立功能分支，不另外建立會搬移路徑的 managed worktree。保留既有未追蹤文件；只 stage 明列檔案。
- Ruling: 任務紀錄放 `android/tasks/`，建置紀錄放 `android/validation/`，優先於技能的 `.superpowers/` 預設路徑。
- Task 1 → 2/3/4/5–11/12/13：模型欄位已比對 C# PropertyDto、DistrictCriteria、SourceSite；金額與坪數用 BigDecimal，nullable 不變，未知資訊由 unknownFields 標記。模型不先實作 Task2 驗證或 Task3 白名單序列化。
- Ruling: Task3/13 的 LOCAL_SELF_CHECK 是證據來源，與四種 RunMode 分開；EvidenceSource 包含 LIVE、LOCAL_SELF_CHECK、SIMULATED，RunRecord/ProbeEvent 必須明確提供。Task3 判讀須拒絕非 LIVE 證據。
- Task 3 → 12/13：RunRecord 裝置快照只建純資料型別；Android 裝置讀取留 Task12。持久化、恢復、匯出留 Task3。
- Task 4 → 5–11/12/13：採集與來源介面留 Task4，RAW 只供記憶體解析。
- Task 5–11 → 12 → 13：單站 request/parse、單作業 runner、操作畫面照原計畫，未發現 Task1 必須先做的實作。
- Ruling: Task14 交接路徑修正為 handoff/2.md，遵守最新使用者指定。
- Ruling: SDK 官方套件名是 platforms;android-37.0，使用其 revision 2；compile/target 仍為 API37。

## 驗證進度

- 已核對 JDK、Gradle 發行檔與 wrapper 官方 checksum；SDK Manager 安裝 API37.0 / Build Tools36。
- 首次 wrapper 網址驗證遭沙箱網路限制；提升權限重跑成功。這不是有效 RED。
- 有效 RED：缺少 Site 模型導致 compileDebugUnitTestKotlin 失敗；依賴及SDK已通過。
- Task1 已完成；未做手機安裝或外站請求。

## 官方工具鏈與實際依賴

- JDK：Microsoft OpenJDK 17.0.21.0.2+1；[官方 ZIP](https://aka.ms/download-jdk/microsoft-jdk-17.0.21.0.2-windows-x64.zip) 與同網址 `.sha256sum.txt`。SHA256 `49154292866386B2F4F09A3ECF3BE07AB80A419149C84EC1D4419B9EEC9E4B64`。
- Gradle：9.3.1；[官方發行](https://services.gradle.org/distributions/gradle-9.3.1-bin.zip)，SHA256 `b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06`；產生的 wrapper JAR SHA256 `b3a875ddc1f044746e1b1a55f645584505f4a10438c1afea9f15e92a7c42ec13`，與[官方 checksum](https://gradle.org/release-checksums/)相符。
- SDK：platforms;android-37.0 revision 2、Build Tools36.0.0；Command-line Tools19.0，官方 ZIP 為 `https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip`，SHA1 `54a582f3bf73e04253602f2d1c80bd5868aac115`，已核對[官方 repository](https://dl.google.com/android/repository/repository2-3.xml)。SDK Manager 安裝 platform/build-tools 時驗證其官方 checksum。AGP 另自動安裝 Platform Tools37.0.1，未使用 adb。
- 版本相容性依[AGP9.1.1 官方表](https://developer.android.com/build/releases/agp-9-1-0-release-notes)，實際解析未需更動候選版：AGP9.1.1、內建 Kotlin／Compose compiler2.2.10、Compose BOM2026.09.00（UI1.12.1、Material3 1.4.0）、Activity1.10.1、WebKit1.14.0、Coroutines1.10.2、Serialization1.9.0、jsoup1.23.2、JUnit4.13.2。
- 工具與使用者快取都在 `android/.tools/`；沒有變更全域 Java 設定。建置指令見 [probe README](../probe/README.md)。

## Review 與修正

獨立唯讀 reviewer 核對契約、配置、啟動畫面與測試證據。兩項發現均已處理：

1. P1：local.properties 的 Windows drive colon 未跳脫，造成首次完整驗證 Lint 失敗。改成 `sdk.dir=F\:/SourceCode/HouseLens/android/.tools/sdk`。
2. P2：只有 allowBackup=false 不足以排除新版 OEM 裝置轉移。加上 dataExtractionRules 排除所有資料域的雲端備份／裝置轉移，並加 fullBackupContent=false 涵蓋舊版配置；依[官方備份規則](https://developer.android.com/identity/data/autobackup)。未擴張到資料儲存功能。

Ruling: 保留鎖版依賴的新版提示，不為消除提示升級工具鏈。AndroidX 合併的 signature permission 與 DUMP 保護 receiver 為套件配置，不是新增危險權限。

排除事項裁決：輸入驗證、序列化／持久化、網路、背景服務依序留 Task2/3/4/12；目前畫面沒有入口使用這些功能，因此不是 Task1 未修缺陷。手機安裝與視覺結果未取得，不以本機 build 取代。

初次完整 build 的 androidx graphics native library 無法 strip symbols，debug APK 直接打包原檔；不為驗證 APK 增加 NDK 安裝。

## 完成證據

- Task 1: complete；`./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console plain --no-daemon` → exit0／BUILD SUCCESSFUL，1個測試，0失敗／0錯誤／0略過。最後完整建置為 `validation/builds/task1/verified.log`。
- Lint報告曾保留前次快取，另跑 `:app:lintDebug --rerun-tasks --offline --console plain --no-daemon` → exit0，**0 errors／8 warnings**，全部為鎖版套件新版提示；見 `lint-fresh.log`，未加baseline或停用規則。
- `:app:dependencies --configuration debugRuntimeClasspath --offline` → exit0；`apksigner verify --verbose --print-certs` → exit0／v2 signature verified。
- APK：`validation/builds/task1/HouseLens-probe.apk`；SHA256 `F45778E8455D5EC621FE8FDF9F4EE3F349722B6B79AEE5BF0C7C284775DB5967`。
- 憑證SHA256 `e0f8077af7100ea744cad3692768b35072504e29af8533e82fe3c438b3dc5855`。私鑰留在忽略的 `android/.tools/android-user/debug.keystore`，不提交。
- 未開始Task2；本輪停下等待使用者繼續。Context使用比例不可見。未做七站／背景／手機驗證。
