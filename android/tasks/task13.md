# Task13 執行紀錄：操作畫面、自檢與 APK

日期：2026-11-06。基底 `c864844227e654266d364d1551fa2eda3454058c`，分支 `android/probe-bootstrap`。依使用者指示主代理實作，代理只做唯讀研究／review；不啟動 Task14。

## 範圍與接線

- 新增 `ProbeInput.kt`、`ProbeScreen.kt`、`ProbePresentation.kt`、`ProbeSelfCheck.kt` 與 Input／Export／Presentation 三個 JVM 測試檔。
- 修改 MainActivity／Service 的 binder、忙碌互斥、WebView attach／identity-matched detach；Store 新增歷史 ID 列舉；Models／JSON／Runner 保存執行版本；build 版本升為 0.2.0／code2。更新本機 assets／Node 檢查、strings、README／計畫／交接。
- Scope：呼叫端 Activity／Service／Runner、Store與JSON相容、橋接assets、JVM／Node測試、APK交付。無新依賴、SDK變更、來源解析器修改、Web／後端／Supabase變更。既有忽略規則已覆蓋產物與簽章，無需重寫。
- Ruling：沿既有逐Task紀錄及 `validation/builds/task13/` 保存證據，不另使用會在 android 外建立目錄的技能腳本；使用者指定路徑優先。
- Ruling：沿現有核准設計接畫面，不重新詢問設計或開新worktree；所有Android檔案固定在原工作區 android 下。

## 實作行為

輸入預設新北中和／2000萬／20坪／R1,R2／住宅1；可選永和、新店及四種模式。價格／坪數最多八位整數與兩位小數，屋齡0–999；不接受負值、NaN、科學記號、未知來源／區域／代碼或任意URL。先跑共同驗證與來源 request；七站集合在啟動前驗全部站別。

畫面是可捲動繁中 Compose LazyColumn，含文字按鈕、開始／集合／取消、通知拒絕說明、網站驗證區、結果／未知欄位、歷史與匯出。Service 單例Store仍是唯一 writer，磁碟讀寫走IO；selfcheck／圖片／匯出等本機作業也占用服務忙碌槽，不會與LIVE session同跑。沒有自動排隊。WebView由服務持有，Activity只借容器；同一容器不重掛，釋放只匹配自己的容器。

結果保存每次執行版本，舊JSON缺 appVersion 則明示UNKNOWN／未知，不能用目前安裝版本補造歷史。匯出另列 exportedWith。CreateDocument ZIP仍只有result.md/events.jsonl/sample.json，沒有新增廣泛儲存權限或自動上傳。選擇器返回早於bind時保留URI及runId，服務連接後才匯出。旋轉會取消Activity本機作業，重建後提示可能中斷、檢查／刪除不完整ZIP再重試；沒有宣稱跨重建續傳。

圖片由明確按鈕一次下載一張，沿HttpPageTransport精確HTTPS/CDN／redirect／Referer規則及8MiB限制；先讀bitmap bounds，拒絕無效或超過5000萬像素，縮採樣至最長邊512，再顯示成功／失敗。圖片失敗不動房源；圖片結果只在本次畫面，需人工回報。

本機自檢使用selfCheck=true的固定HTTPS assets與外網拒絕。原生capture以callback非阻塞等待；整段WebView自檢60秒上限，注入各10秒上限。fetch／XHR比對固定本機JSON；iframe及延遲舊ID必須先有送出marker，再確認未收進capture；bridge ready等測項完成才成立。這是舊ID重放測試，不聲稱真實網站舊頁網路callback已驗證。

TIMEOUT／RENDERER_GONE只在selfCheck允許注入，走實際fail／rendererGone收尾，標SIMULATED。結果均不送進LIVE計數。另以LOCAL_SELF_CHECK保存LOCAL1／798.00萬／28.50坪、重讀與產生ZIP，歷史可再選取主動匯出。自檢文字結果留畫面，未另建無需求的報告格式。

## 驗證與審查

- `red.log`：Input／Export缺少API的有效RED，非工具鏈錯誤；`green.log`：5項通過。
- `presentation-red.log`：缺背景頁摘要API的有效RED。
- Node新增ready gate測試先觀察到 `true !== false`，實作後 `bridge-final.log` PASS。另一次從repo root執行Node因工作目錄錯誤ENOENT，不算功能RED；從probe目錄重跑成功。
- 第一輪 `full.log`：211項、0失敗／0錯誤、BUILD SUCCESSFUL，lint0 errors／7 warnings，其中2個unused strings已修為畫面沿用，另外5個為原鎖版新版提示。
- `count-red.log`：4項中1項失敗，重現重用navigationId會灌高背景新頁數。修正為每page／nav只允許一個請求，EVIDENCE_LOST直接不計；不影響原背景總判讀。
- 獨立review兩份：UI／生命週期與自檢／匯出。補了選擇器返回早於bind、旋轉中斷提示；將歷史版本測試改成0.1.7，精確檢查標題，避免exportedWith的目前版掩蓋回歸。原生改動只有編譯／lint／靜態審查，未假稱裝置測試。
- 最終 `final.log`：完整 testDebugUnitTest／lintDebug／assembleDebug exit0，**212 tests／0失敗／0錯誤，lint0 errors／5個原有新版提示**。`dependencies.log` exit0保存實際解析版本。APK簽章驗證exit0，憑證SHA-256與Task1相同；APK SHA-256為 `110E1E31CECDFB43E665595A8C3A448A189C7770C8A3670B94C8C21558AAEE18`，建置與逐檔source hashes在build-info.json。

## 限制與停點

未跑手機／adb／模擬器／網站／背景；Compose實際排版、放大字體、通知拒絕、檔案選擇器、WebView原生callback、縮圖、更新保留資料均留Task14。自檢PASS也不能證明網站或Android系統故障行為。沒有真機七站成功結論。

固定交接仍為 `android/handoff/handoff.md`。Task13完成後停止，等待使用者繼續才啟動Task14；全部14 Task完成後仍要獨立整體review。Context實際百分比不可見。
