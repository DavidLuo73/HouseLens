# HouseLens 驗證 APK

獨立單一 module 工程。Task13 提供來源／條件、採集／取消、結果／歷史、縮圖、本機自檢與主動 ZIP 匯出；手機驗證留 Task14。交付與操作見 [驗證說明](../validation/README.md)。

## 本機建置

鎖版設定見 `gradle/libs.versions.toml`。需要 JDK 17、Android SDK Platform 37.0、Build Tools 36.0.0；Gradle 9.3.1 由已驗 checksum 的 wrapper 取得。官方來源與本次實際版本見 [Task 1 紀錄](../tasks/task1.md)。

這台開發機的工具位於 `android/.tools/`，不更動系統 Java；在 PowerShell 執行：

```powershell
cd F:\SourceCode\HouseLens\android\probe
$env:JAVA_HOME = 'F:/SourceCode/HouseLens/android/.tools/jdk-17.0.21.0.2+1'
$env:GRADLE_USER_HOME = 'F:/SourceCode/HouseLens/android/.tools/gradle-home'
$env:ANDROID_USER_HOME = 'F:/SourceCode/HouseLens/android/.tools/android-user'
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

`local.properties` 指向本機 SDK，檔案不進版控。另一台機器先從官方安裝對應工具，設定自己的 JAVA_HOME 與 sdk.dir，再執行相同 wrapper 命令。

輸出：`app/build/outputs/apk/debug/app-debug.apk`。僅為開發測試 APK，未經手機安裝驗證；不表示七站採集或背景執行已成功。

SDK、Gradle 快取、build 目錄、APK 與私有簽章均不進版控。後續需沿用同一測試簽章才能直接更新已安裝版本。
