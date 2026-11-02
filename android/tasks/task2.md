# Task 2 執行紀錄

計畫：`android/plans/2026-11-03-probe-apk-implementation.md` Task2。Base：`73a77e1`；沿用 `android/probe-bootstrap`，依固定路徑在 android/ 下實作。

範圍：新增 ProbeRules.kt、ProbeRulesTest.kt；ProbeModels.kt 加 PageDecision。目前沒有呼叫這三個新函式的生產程式，後續來源／Runner才接入；不改後端、畫面、依賴或設定。

## 決定

- validateQuery先限制probe已規劃的新北中和／永和／新店三區，七站都有既有映射；檢查數值非負、代碼長度及安全字元格式。站別代碼語意仍留來源adapter／Task13 UI驗證，不擅自把各站slug/中文碼改成同一組。
- Ruling: 共同篩選不以areaPing套minSizePing。樂屋優先主建、其他站常為總建，591基準亦未確定；沿用PropertyNormalizer.cs明定的來源端篩選，避免新造漏抓。Task5–11須各自驗證面積參數。
- null排除理由只代表「沒有可證明的共同排除原因」，不是所有條件已核實。屋齡null與unknownFields標示的未知車位不改值、不當false/0已確認；有已知屋齡超限或已知無車位才排除。必要的來源ID、正價格及地區仍須有效。
- 分頁僅使用原始keys與來源結束證據；全被過濾仍可CONTINUE。END須有非空evidence固定代碼；已確認EMPTY/END或RESULTS/END可COMPLETE。未知空頁／重複頁無結束證據為INCONCLUSIVE；仍有MORE/UNKNOWN且達頁數上限為TRUNCATE。
- 挑戰頁與結構異常優先於頁數上限；不以已接受筆數猜末頁。來源parser遇缺ID／價格解析失敗必須回INVALID（共用契約），不得當一般條件排除後宣告完整。

## 狀態

已完成實作、紅綠測試、完整建置及獨立review。Task3尚未開始；Context讀數不可見，依逐Task停點等待繼續。

## 實作及驗證

- 新增 `ProbeRules.kt`（三個純函式），`ProbeModels.kt` 加PageDecision；新增15個規則測試，無新依賴／設定／介面修改。
- RED：`./gradlew.bat :app:testDebugUnitTest --tests '*ProbeRulesTest' --offline --console plain --no-daemon` → exit1，缺少三函式及PageDecision；見 `validation/builds/task2/red.log`。此為預期API尚缺的RED，非下載或SDK失敗。
- GREEN：同一命令 → exit0，15項規則測試通過；見 `green.log`。沙箱拒絕Kotlin daemon預設使用者暫存檔，編譯器使用內建fallback成功。沒有以此錯誤修改產品程式或全域設定。
- 完整驗證以允許的沙箱外命令執行：`./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --console plain --no-daemon` → exit0／BUILD SUCCESSFUL；**16項測試、0失敗／錯誤／略過**，lint0 errors／8 warnings（原鎖版套件新版提示），APK建置成功；見 `full.log`。程式碼仍全部在指定android目錄。
- 本Task不交付新的手機功能或更新Task1留存APK；目前規則待後續來源／Runner接線，無真機、網站或背景執行證據。

## 獨立 review

唯讀reviewer檢查原始keys／有效數量、末頁證據、挑戰及頁限優先序、精確價格、跨區與未知欄位；未發現必修bug。

Minor（暫不另加測試）：重複／EMPTY+MORE遇最後一頁，以及END+空evidence遇最後一頁的交叉優先序未個別鎖定；對應分支已分別測過，現行結果正確，沒有為此改程式。

Ruling: reviewer排除面積基準、站別代碼語意與parser欄位／證據來源驗證，符合現有分工。Task5–11必須遵守INVALID／unknownFields契約及來源代碼驗證；Task13不得讓未知代碼變成可發送請求。不能把本Task的純規則測試當實站成功。

Task 2: complete。Task3未開始；更新handoff/2.md後停下等待繼續。Context實際百分比不可見。
