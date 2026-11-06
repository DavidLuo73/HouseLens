# Task 12：單一作業、背景服務與中斷收尾

狀態：完成實作、驗證與獨立review，依逐Task規則停下。2026-11-06，分支 `android/probe-bootstrap`；範圍限 `android/`。

## 範圍與決定

- 依既定 Native 計畫由主代理實作，唯讀代理研究 Android 官方限制並獨立審查；本 Task 結束即停，不啟動 Task13。
- 沿用 Store、Report、兩個 transport、七站 adapter；不新增依賴、資料庫、排程器或後端功能。
- 影響檔案：新增 Runner、Service、DeviceSnapshot、Runner/Recovery tests；修改 Store terminal reason、Models/JSON 的選填電量欄位、WebView alive/renderer callback、MainActivity lifecycle 與 manifest。任務／計畫／交接同步保留於 android。
- Ruling：沿用既有任務文件作執行紀錄，不在 android 外建立技能工作目錄；使用者指定固定路徑優先。
- Ruling：未知 robots 記 ROBOTS_UNKNOWN 並保留不確定性，只有明確 disallow 才阻止採集；不能將未知寫成已確認允許。兩種 transport 與 warmup 均先檢查。
- Ruling：背景模式 COMPLETED 只表示觀察流程完成，完整搜尋只由 FULL 與來源 END 判斷；未取得三個窗口的新頁或未持續滿600秒須 INCONCLUSIVE。
- Ruling：同程序同存活 WebView 才能續同頁；人工解驗證後重新導航同頁，沒有聲稱延續原 DOM。人工介入後背景判讀維持 FAILED。

## 驗證紀錄

- RED：`validation/builds/task12/red.log`，缺 ProbeRunner API，`:app:compileDebugUnitTestKotlin` 按預期失敗。
- 初次 GREEN：`green.log`，Runner 13＋Recovery 1項通過，BUILD SUCCESSFUL。
- `full.log`：195項中4項預期失敗，重現立即取消未釋放、集合初態已背景未排程、第4頁空END與觀察未滿便返回前景仍COMPLETED。
- `boundary-green.log`：前4項已綠；追加的24項中3項失敗，重現commit時漏掉生命週期造成FULL假PASS、elapsed已過期仍COMPLETED、缺結束電量。檔名沿當時啟動目的，實際結果以此處為準。
- `verified.log`：上述修正後完整測試／lint／APK已成功；後續review新增的期限／同毫秒競態測試不在這次編譯內，尚不能當最終結果。
- `review-red.log`：Runner 27項中4項預期失敗，重現同毫秒commit競態、REQUEST_STARTED與warmup robots的elapsed期限缺口，以及人工等待過期仍resume。
- `final.log`：203項／0失敗／0錯誤，lint0 errors／5個原有新版提示，APK建置成功。其間最後新增的Store單例同步註記尚未納入該次編譯；`javap`已確認，故另跑`final-current.log`驗證目前完整原始碼。
- **最終 `final-current.log`：exit0／BUILD SUCCESSFUL，203項／0失敗／0錯誤，lint0 errors／5個原有新版提示，assembleDebug成功。** 命令見交接Task12節，工具環境沿probe/README；build-info保存實際hash。沒有依賴變動，未覆蓋Task1留存APK。
- 兩位reviewer已覆核最終必修修正；沒有必修遺留。所有命令已結束，context實際百分比不可見。

## Review 與修正

- 兩個唯讀審查面向：Runner／Store／Report 契約與Android服務／生命週期。
- Service升前景拒絕必須立即stopSelf，不等磁碟；已改為先停止started/foreground狀態，再保存SERVICE_START_DENIED。
- 多Activity交錯時只在可見數歸零才全域detach；Task13應補 `detach(container)` 的parent身分比對，不讓舊容器釋放拔掉新容器。
- cancel後直到舊job/finally結束才釋放作業槽；立即取消仍建立可判讀的取消紀錄。
- lifecycle事件不可因closing丟棄；結束前join已收事件，無法保證時序則FAILED／EVIDENCE_LOST，不能補造背景成功。
- elapsedRealtime與coroutine排程的時間不一定同步；期限以elapsed再檢查，休眠後不可補發已逾期請求。
- 同毫秒的commit競態也不能靠事件排序猜測成功；頁提交期間的環境變化保守FAILED／EVIDENCE_LOST。
- 五分鐘人工等待保存獨立elapsed截止；resume、環境callback與await返回皆檢查。休眠期間不承諾callback會即時執行，但恢復後不能用過期session續跑。
- 起訖電量使用0–100或null，terminal原子保存，舊events無此欄位仍可讀；只是系統回報百分比，不是校準耗電量。

## Android 官方核對

- [FGS 啟動](https://developer.android.com/develop/background-work/services/fgs/launch)：可見使用者操作啟動、立即升前景；拒絕須有固定結果。
- [dataSync timeout](https://developer.android.com/develop/background-work/services/fgs/timeout)：系統 callback 立即停止，不等待磁碟 IO；既有 checkpoint 可恢復。
- [通知權限](https://developer.android.com/develop/ui/compose/notifications/notification-permission)：拒絕通知不等於禁止 FGS，Task13 提供 App 內取消與說明。
- [Doze](https://developer.android.com/training/monitoring-device-state/doze-standby)：wake lock 不代表背景可用；實機結論仍待 Task14。

尚未跑實站、手機、WebView原生callback或背景；不使用adb。當前UI仍是Task1畫面。

## Task13 接線契約

- Activity先bind `ProbeService`取得`LocalBinder`，只由可見畫面的點擊呼叫start/startCollection；BIND本身不抓取、不建WebView，也不升前景。binder提供state／busy／resume／cancel／attach／detach。
- `MainActivity.onStart/onStop`已記可見狀態；stop不取消採集。Task13有真正容器後，補parent身分比對的detach；重建Activity attach同一session。不可為attach新建第二個WebView。
- 每run新的query/device/初始螢幕快照；Store和processId是同程序單例。Store所有讀寫／匯出在IO dispatcher，不能另建第二個writer。
- startCollection只接受明確七站集合：591→樂屋→永慶→住商→信義→中信→台灣房屋。WAITING暫停集合，取消／失敗／中斷停止集合；COMPLETED／TRUNCATED／INCONCLUSIVE才進下一站。衝突start直接false，不加入集合。
- busy要等舊transport、頁寫入、lifecycle事件與terminal收尾完才釋放。不要只看state變成終止便立即發另一個作業。
- FOREGROUND最多3頁；FULL用來源cap；背景觀察先第1頁，再於首次符合背景條件的+60/+300/+540秒取頁，+600秒評估。三窗口缺新頁／缺網路證據／未滿時間不算成功。
- 原生服務使用dataSync、START_NOT_STICKY、通知返回／取消與有限wake lock；runner和wake lock同一總截止（背景15分鐘，其餘90分鐘）。系統timeout立即stopSelf；最終IO若來不及完成，重開以完整checkpoint和新processId呈INTERRUPTED。
- WAITING最多5分鐘（依elapsed），不自動重試、不持wake lock。只有同run、同程序、同存活browser session才能resume；HTTP challenge不承諾可在WebView解決，需取消／新開。renderer死亡一律固定RENDERER_GONE。
- 磁碟壞掉時若terminal也無法保存，state仍為FAILED／STORAGE_ERROR，不能提示「已保存」；歷史資料只以磁碟實際checkpoint為準。
- `ProbeEvent.batteryPercent`是選填0–100；舊JSON缺欄位仍讀null。terminal存結束值，report列起訖值；真機仍需記錄充電與省電情境，不能把百分比差當精密耗電量。
- 通知拒絕提示、App內取消、歷史／匯出、圖片、自檢與完整畫面均留Task13；未建立可交付採集APK的操作入口。
