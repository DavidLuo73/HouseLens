# v0.1.0 試用交付與驗收紀錄

更新：2026-11-07。**免登入簽署 APK 已交付並安裝到 S25+；完整 B7 人工／受控效能驗收仍 OPEN。** 網站風格圖示已於v0.1.1交付；目前v0.1.5含篩選抖動修正、雙向總價／單價排序及預設單價低到高，已同簽章更新手機。最新版本與證據見[handoff](../handoff.md)；本頁以下保留v0.1.0驗收數據。

App 直接使用 Supabase Postgrest anon SELECT，沒有 Google／會員／Auth token／callback。收藏與已套用條件以 project＋local-device-v1 儲存在此裝置，不匯入或刪除原登入 owner 檔。只有公開 key 進 APK；原資料與手機顯示設定保留。

## 產物

| 項目 | 已確認結果 |
| --- | --- |
| APK | `C:/Users/David/Downloads/HouseLens/HouseLens-0.1.0.apk`，12,333,745 bytes |
| 版本／package | versionName0.1.0／versionCode1；tw.houselens.viewer |
| 平台 | minSdk26／targetSdk36；非 debuggable |
| APK SHA256 | `7776FD77EBEA3BC454C964D8160A8F0CEF464380053753B5CD433412BDA9C5E5` |
| 憑證 SHA256 | `de6fe3b9729224098250683aed7c1a2cdb9f3d4f28ac4a19848903b51c4d27ee`；CN=HouseLens Viewer |
| 簽署 | 成功載入使用者批准的長期身分；APK signer 與該身分吻合；交付副本 hash 相同 |
| 實機 | SM-S9360／Android17 API37；正式 APK 安裝／冷啟動成功，App UI 讀到共687筆。只保留 App 節點數／房源數，不保留手機原始 hierarchy或序號 |

簽署設定與 recovery 留在 private Signing 目錄，使用者已承諾安全備份。金鑰不入 Git；不要重建。更新須使用同一簽章及較高 versionCode，遇到衝突不可解除安裝／清資料。手機現在已是使用者安裝，不能再直接跑會卸載 target 的 connectedDebugAndroidTest。

## 實際驗證

下列 logs 除另註外位於 C worktree `.superpowers/sdd/02-app-plan/`；每次有獨立 log／XML，不覆寫失敗。

| 檢查 | 結果與證據 |
| --- | --- |
| 最終 JVM／lint／API26完整 UI | 67 JVM、23 UI均0失敗；lint0errors／21warnings。360dp／200%字級。`b7-resume-anonymous-Full-20261107-204758-993` |
| 最終乾淨橫向／IME | 3／3 PASS；實際橫向、200%字級、真實鍵盤、完整輸入欄與浮動標籤、clipped48dp按鈕、套用與返回。`b7-resume-anonymous-Landscape-20261107-205053-037`。PNG已由Root與獨立reviewer檢視 |
| S25+合成主要旅程 | 3／3 PASS，原顯示／字級／旋轉／IME設定未改，未擷取手機截圖。`anonymous-phone-ui-20261107-204103-480` |
| 真實SDK sample | 公開key、無Auth，GET select=id limit1→HTTP200、JsonObject解碼。`anonymous-live-sdk-20261107-191008-256` |
| 真實業務資料層 | 11個GET／9端點全部200或206；找房20/687、選項8、排行8區37項、降價20/28、區域8、最近輪次7來源、首筆詳情1來源3歷史全部DTO解碼。`anonymous-live-business-20261107-204455-790`；未輸出IDs／房源內容，臨時測試移除且src hashes一致 |
| Release拒絕案例 | 8／8 PASS；缺URL/key/signing、錯origin、secretkey、debugalias、無效keystore被拒絕。`anonymous-Guards-20261107-190200-239` |
| 真正簽署與artifact | PASS，package／SDK／debuggable／signature／敏感檔名、批准憑證比對與APK hash。`anonymous-release-20261107-205448-344`。檔名掃描不冒充完整憑證稽核 |
| 正式APK手機畫面 | 安裝／啟動log於`anonymous-release-phone-20261107-210035-110`；原/dev/tty未回XML，後續受控暫存hierarchy確認687並刪檔。最終摘要F transfer `anonymous-release-phone-live.json`；不把220ms Activity launch時間稱為首屏效能 |
| SQL／正式權限 | 匿名SQL隔離39項notice／19漂移拒絕、reinstall／rollback／revoke／restore通過；已獨立審查、依明確批准正式套用，六RLS／四invoker／API寫權0／houselens RPC0。詳細證據見supabase/README.md |

正式schema由使用者Dashboard加入houselens，保留graphql_public/public；重新核對後仍只有六表必要欄位與四views可匿名讀，沒有API寫權或default grant擴大。不在正式庫試寫／撤權。現無Worker，沒有啟動爬蟲。

## AC 對照與剩餘人工驗收

| AC | 已完成範圍 | 仍未由此證明 |
| --- | --- | --- |
| 01 | 正式簽署App無登入直接顯示房源，anon最小SELECT／無會員API | Google與會員限制已依使用者要求延後，不屬本版門檻 |
| 02 | 直接Supabase HTTPS、無本機API依賴、正式手機載入687筆 | 行動網路且電腦實際關機的操作 |
| 03 | 取消／套用／價格／查詢競態／同名區、來源4正名、儲存重試清錯誤 | 真實資料各種篩選的完整人工操作 |
| 04 | 合成3頁／去重／排序／續接、正式第一頁20/687與count | 正式多頁完整巡覽與網路失敗續接 |
| 05 | 找房／精選／收藏詳情、精確位置與背景／recreation返回；S25旅程 | 人工實際返回手勢 |
| 06 | 真實本機檔案持久／隔離／失敗、復原重試、pending愛心停用；新graph讀回收藏 | 正式使用者長期資料／實際程序終止後操作 |
| 07 | 數值／UTC／DateOnly／歷史邊界及SQL契約；正式詳情解碼 | 真實所有歷史邊界逐筆人工核對 |
| 08 | 歷史事件語意、Source4與merged狀態正確、不逐卡詳情；正式排行／降價解碼 | 正式資料全量語意核對 |
| 09 | 空／資料不足／單點／分箱／文字等價與正式區域解碼 | 真實TalkBack朗讀與焦點次序 |
| 10 | 合成網路錯誤／取消／重試、callback-only詳情重試 | 真實斷網／重連／限流等所有情境 |
| 11 | 憑證origin隔離、URL驗證、簽章與artifact檢查 | 全APK逐位元secret稽核未做；未將檔名掃描擴大解讀 |
| 12 | API26 360dp／200%、橫向／IME／完整欄位標籤／48dp、S25原設定、PNG人工檢視 | TalkBack、多視窗、手勢列人工檢查、手機200%字級 |
| 13 | 真實GET-only SDK與app、隔離DB寫入拒絕、正式metadata僅SELECT | 不做正式房源試寫；無正在執行Worker可測 |
| 14 | 背景／Activity重建／原位返回／持久化收藏；無OAuth路徑 | 真正程序被終止後恢復；recreation不冒充程序死亡 |

受控實體手機RTT100ms／10Mbps、20筆×100歷史、至少20次首屏P95≤3秒、返回≤300ms、60Hz95%frame≤16.7ms與ANR標準尚未量測。v0.1.0是可用試用交付，不等於完整B7效能／無障礙驗收完成。

## 審查與失敗紀錄

A1／A2各自獨立審查；最後整體review的1–7項與實際橫向裁切已修正並scoped re-review通過，見F transfer `anonymous-final-review.md`。Source4改台灣房屋、merged標示、undo重試、篩選清錯誤、pending愛心、品牌色與真正重試callback已關閉。

原始591漏選`b7-full-green.log`仍為FAIL；當時原因未證實，後來全部通過及逐chip selected斷言不算因果修復。保留此殘餘觀察。橫向曾有測試宿主重建、IME過渡重複點擊、雙重IME padding與固定header壓縮；現用正規debug-only生命週期宿主、條件式焦點準備、scrollable內容與無拖曳把手，保留實際IME/fullbounds斷言。首次label新斷言有compile錯誤，後修為DpRect邊界才得到88dp/60px的真正RED。

S25首次2/2在Espresso3.5反射InputManager.getInstance失敗，尚未執行App操作；明確選test-only3.7後3/3通過。第一次簽署wrapper在未加引號-J參數失敗，尚未建置；安全help-only定位後修正，原金鑰未變。所有失敗證據保留，不將重跑冒稱修復。
