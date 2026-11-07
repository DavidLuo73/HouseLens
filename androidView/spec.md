# HouseLens Android 瀏覽 App 規格

> 最新追加需求：參考網站HouseLens房屋／愛心／星號，替App配置一致的現代向量圖示與adaptive launcher；色系與UI/UX延續網站，保留48dp、文字語意與收藏資料。見[圖示計畫](plans/04-icons-plan.md)。

## 第一版最新裁定（2026-11-07，優先於下文舊登入規格）

使用者改為「第一個版本先不用登入這一塊了，有apk就可以進入使用」，並強調 UI/UX 使用體驗。

- 第一版直接開啟找房，保留找房／精選／收藏／區域四入口；不使用 Google、Supabase Auth、成員查核、帳號或登出。
- 使用既有公開 key 以 anon 讀取 Supabase，只開放既有 Viewer 必要欄位／invoker views 的 SELECT；不提供任何房源寫入或管理能力。正式匿名開放需要可檢閱變更及單獨批准。
- 收藏與已套用條件以專案＋本機裝置命名空間保存，不自動匯入先前登入帳號的資料；資訊頁提供版本、資料狀態與解除安裝會遺失收藏的說明。
- 保留原本房源語意、分頁、精度、日期、最小欄位、來源憑證隔離及手機 UI/UX 規格；載入／錯誤／重試、原位返回、鍵盤／大字級／橫向與無障礙仍需驗證。
- 下文 Google、會員 RLS、PKCE、登出／換帳號與其 AC 對應為延後需求，不是此版交付門檻；AC-01 改驗證免登入啟動，AC-13 改驗證 anon 僅唯讀，AC-14 保留背景／旋轉／程序恢復但排除 OAuth。
- Release 仍需正式私有簽署；不使用 debug key 或內嵌管理憑證。使用者已批准建立長期更新身分並承諾安全備份，金鑰已建立於私有 Signing 目錄，未入版控。

執行細節見 [免登入第一版計畫](plans/03-anonymous-v1-plan.md)。既有 B7 未完成測試與失敗證據保留，不能因範圍改變宣稱通過。

日期：2026-11-07
狀態：依使用者最新指示改採 Supabase 直連；Native 實作進行中，優先手機 UI／UX。
規格位置：`F:\SourceCode\HouseLens\androidView\spec.md`

## 1. 目的與已確認決策

建立供本人及家人使用的原生 Android 看房工具，瀏覽 HouseLens 已蒐集的房源、價格紀錄與分析結果。在手機上完成「找房 → 判讀價格 → 收藏 → 查看原刊登」；視覺延續現有網站，操作依 Android 習慣重新安排。

| 項目 | 決策及來源 |
| --- | --- |
| 瀏覽範圍 | 使用者選擇完整瀏覽：房源篩選、詳情／價格歷史、收藏、優質排行、大降價、區域分析。 |
| 技術 | 使用者選擇 Kotlin＋Jetpack Compose，優先 Android 體驗。 |
| 資料操作 | 使用者要求不做爬取；本 App 定位為瀏覽端，不提供觸發爬取、資料清除或共用設定編輯。收藏與查詢條件僅寫入本機。 |
| 連線 | 使用者要求外出也能使用。Android 直接讀取 Supabase 託管的 HTTPS Data API，不另外新增或部署 .NET API。 |
| 登入 | 使用者選擇 Google 登入，僅允許事先核准的本人／家人帳號。 |
| 設計交付 | 使用者選擇先完成文字 spec，包含頁面配置與操作流程，不製作瀏覽器草圖。 |
| 排除範圍 | 本次未讀取、搜尋或沿用 `android/`、`androidtest/` 內容；新 App 與它們分開。 |

以下是本規格提出的設計決定，並非使用者逐項指定：四個主導覽入口、收藏各裝置獨立、Supabase Auth 串接 Google、系統瀏覽器＋PKCE 登入、淺色品牌主題、Android 8.0（API 26）以上、第一版以簽署 APK 供少數成員安裝。這些決定可在設計檢閱時調整。

本文件取代舊共用架構文件中「Android 可管理設定、操作爬蟲與清除資料」的 App 範圍；不代表刪除網站既有管理能力。房源與評分仍共用，收藏維持各裝置獨立。使用者已明確撤銷本 App 依賴 .NET API 的方向；既有網站的 API 仍留給網站使用。

### 第一版不包含

爬蟲、背景抓取排程、管理介面、個人評分規則、跨裝置收藏同步、完整離線房源庫、手機推播、房源比較、地圖搜尋、房仲聯絡功能、iOS、商店上架流程。現有資料未提供的房／廳／衛、完整照片相簿、經緯度，不在 UI 中做成假功能。

## 2. 專案現況與可重用部分

以下以 2026-11-07 靜態閱讀的實際程式為準；舊 README／spec 中的 SQLite、路由及權限敘述不一律代表現況。

```text
HouseLens/
├─ backend/
│  ├─ src/
│  │  ├─ HouseLens.Domain/          實體與列舉
│  │  ├─ HouseLens.Application/     查詢型別、分析與評分規則
│  │  ├─ HouseLens.Infrastructure/  PostgreSQL、資料存取與來源服務
│  │  ├─ HouseLens.Api/             HTTP 查詢與管理端點
│  │  └─ HouseLens.Worker/          既有背景資料產生流程
│  ├─ tests/                       單元與整合測試
│  └─ tools/reset-data/             維運工具；App 不使用
├─ frontend/
│  ├─ src/components/              房源卡片、篩選、歷史圖表
│  ├─ src/pages/                   清單、詳情、排行、降價、分析及管理頁
│  ├─ src/services/api.ts          Web API 型別與圖片轉換
│  ├─ src/stores/filters.ts        Web 查詢狀態
│  └─ src/style.css                品牌顏色、圓角與排版
├─ specs/001-house-price-tracker/   原始產品文件，部分資訊已過時
├─ docs/                          資料模型與共用資料架構設計
├─ handoff/                       既有交接文件
└─ androidView/                   本次規格及未來獨立瀏覽 App
```

| 現況 | 對 Android 的影響 |
| --- | --- |
| Web 為 Vue 3／TypeScript／Pinia／ECharts；後端為 .NET 10、EF Core／Npgsql。 | 重用資料、語意與配色；Vue 元件不能直接作為 Compose 元件使用。 |
| 網站已有清單、詳情、歷史、排行、大降價及區域分析。 | 沿用其畫面語意與資料，App 改由 Supabase 查詢取得。 |
| 網站卡片圖片／標題直接開房仲網站；站內詳情路由存在，但不是主要卡片流程。 | Android 改為先進 App 詳情，避免瀏覽過程反覆離開。 |
| 網站收藏只有 `localStorage` ID 集合與愛心，沒有收藏清單或收藏 API。 | App 新增本機收藏頁；不是移植一個已完成的收藏服務。 |
| 清單 API 已回傳完整歷史；排行及大降價回應較精簡。 | App 清單只查必要摘要與最近變動；完整歷史進詳情才載入，原 Web 契約不變。 |
| 已有 Supabase PostgreSQL 設定，API／Worker 使用同一 schema。 | 不新增第二份房源資料庫，不重做資料遷移。 |
| API 程式尚無 authentication／authorization，預設本機網址。 | App 不依賴它；登入與成員限制改在 Supabase Auth／資料庫權限處理。 |

已安全確認 `backend/appsettings.Local.json` 存在 `ConnectionStrings.Default`，內容包含 Supabase pooler、`Search Path=houselens` 與 `SSL Mode=VerifyFull`。本文件不包含主機識別、密碼、完整連線字串或金鑰；本次已以唯讀方式連線確認資料表與權限：12 個既有關聯、未啟用 RLS，anon／authenticated 尚無這些表的 SELECT 或寫入權限。未查詢房源內容、未改遠端權限；查不到 authenticator 的 exposed-schema 覆寫不等於已確認 Dashboard 設定。

## 3. 最小架構與資料流

使用者已選 Kotlin＋Compose 原生 App，並再次確認只需瀏覽爬取後的結果、以好用的 UI／UX 為重點。Vue＋Capacitor 曾列為省開發量的候選，但未採用。

```text
Android ── Google／PKCE ──> Supabase Auth
Android ── publishable key＋個人 JWT ──> Supabase Data API
                                            │
                                     SELECT 權限＋RLS
                                            │
                                     現有 houselens 資料
既有資料產生端 ───────────────────────────────┘
Android 本機 ──> 收藏 ID／摘要／查詢偏好
```

不新增 API server、反向代理、Edge Function、資料同步或另一份房源資料庫。Supabase 自動提供資料端點；必要的組合查詢用少量唯讀 SQL views，無須獨立服務。App 只持有 Project URL、publishable／anon key 與個人 session，不持有 PostgreSQL 密碼或 service-role key。

先補核准成員名單、SELECT 權限及 RLS，再開放 Data API；不是把資料庫連線字串直接放入 APK。[Supabase custom schema](https://supabase.com/docs/guides/api/using-custom-schemas)、[RLS](https://supabase.com/docs/guides/database/postgres/row-level-security)
## 4. 資訊架構與主流程

### 主導覽

| 底部入口 | 頁面內容 | 次層頁面 |
| --- | --- | --- |
| 找房 | 房源卡片、作用中條件、排序、結果筆數 | 篩選面板、房源詳情 |
| 精選 | 「優質排行／大降價」分段切換 | 房源詳情 |
| 收藏 | 此裝置、此帳號的收藏 | 房源詳情 |
| 區域 | 行政區概覽、單價與價格分布／趨勢 | 該區房源清單 |

帳號與登出位於頂端頭像入口，不另占一個底部 tab。頁面以繁體中文顯示，平台名稱延續網站：591、信義、永慶、樂屋、台灣房屋、住商、中信。

### 主要操作流程

1. 首次啟動 → Google 登入 → 查核 Supabase 成員名單 → 找房首頁；不顯示爬取導引或管理設定。
2. 找房 → 開篩選 → 編輯暫存條件 → 套用 → 更新第 1 頁 → 點卡片 → 詳情 → 返回原清單位置。
3. 在清單或詳情按愛心 → 本機保存 → 收藏頁立即可見 → 重開 App 仍保留。
4. 精選 → 優質排行／大降價 → 點項目 → 同一個詳情頁 → 返回原精選分頁及捲動位置。
5. 區域 → 選一區 → 看分析 →「查看本區房源」→ 找房清單；明確設成該區、在售、其他條件清空，顯示可還原先前找房條件的提示。
6. 詳情 →「查看原始刊登」→ 選來源（多來源時）→ 系統 Custom Tab／瀏覽器 → 返回仍停在原詳情位置。

切換底部 tab 保留各頁條件與位置。系統返回依序關閉鍵盤／面板、離開詳情、回找房首頁；首頁根層返回交由系統離開，不重複建立首頁。支援系統返回手勢；不得攔截所有返回事件來自訂退出確認。

## 5. 頁面配置與互動規格

### 5.1 登入、未授權與帳號

登入頁以 HouseLens 標誌、短說明「隨時查看收藏與房價變化」及標準 Google 登入按鈕構成；不放假房源或必填個資表單。登入使用系統瀏覽器的 OAuth 授權流程與 PKCE，不用內嵌 WebView 收集 Google 密碼。

取消登入回原頁；失敗可重試，不無限跳轉。Google 身分驗證成功後仍需 Supabase 成員名單檢查；未核准顯示「此帳號尚未獲得使用權限」，提供切換帳號／登出，不顯示任何房源快取。帳號頁顯示目前 email、App 版本、收藏僅此裝置保存的說明及登出。

### 5.2 找房首頁

由上至下為：HouseLens／帳號 → 行政區摘要與篩選按鈕 → 作用中條件 chips → 結果筆數／排序 → 房源列表 → 底部導覽。沒有可輸入卻不支援的關鍵字搜尋框。

- 手機單欄，卡片圖片 4:3；圖片下方依序顯示行政區／評分、最多兩行標題、總價／單價、坪數／樓層／屋齡、來源與標記、價格歷史入口。
- 價格為主要視覺焦點；未知單價、屋齡或樓層顯示「未提供」或省略次要欄位，不用 0 代替。
- 愛心獨立操作，不觸發詳情；圖片與正文點擊均進詳情。價格歷史入口進詳情並定位歷史區塊。
- 徽章優先顯示已下架／大降價／新上架；來源放正文，避免與愛心重疊。車位顯示為文字資訊；不把未標示車位提升成已確認沒有車位。
- 評分沿用網站的 `score × 5`、一位小數，標「系統評分」，不暗示為使用者評論；缺值顯示「尚無評分」。
- 每頁 20 筆，接近底部載入下一頁；有底部載入／重試／已到底狀態，並提供「載入更多」可操作入口。依 ID 去重，避免同卡重複。
- 返回詳情不重設列表、不自動跳頂。手動下拉更新第 1 頁並清除過期後續頁；原資料保留至刷新成功。
- 底部狀態區可顯示「本次取得資料時間」；不能把此時間稱為房源更新時間。

初始預設：在售、全部行政區／平台、無價格限制、車位不限、不限定降價、評分優先。記住使用者已套用條件；重新啟動從第 1 頁取最新資料。程序重建可恢復條件與返回目標；僅在對應列表資料仍可恢復時恢復原捲動位置，否則回第一頁並提示重新載入。

### 5.3 篩選與排序

使用可展開至全高的底部面板；大字體或鍵盤出現時內容可捲，底部「重設／套用」不能被遮住。面板編輯暫存值；關閉或返回不套用，套用才送出查詢。重設只重設面板草稿，仍需套用。

| 控制項 | 規則 |
| --- | --- |
| 行政區 | 複選；從地區設定及分析回應取得可用區名，包含停用爬取但可能仍有資料的地區。可確認唯一縣市的選項以縣市分組；跨市同名區只列一個「區名（所有同名行政區）」選項，縣市未知則不猜分組。「全部」是不傳篩選，不表示選項已涵蓋資料庫全部地區。 |
| 平台 | 複選，與網站既有七個 SourceSite 對應；未知來源仍可在卡片顯示原字串。 |
| 總價 | 最低／最高，單位萬元，數字鍵盤。不得負值，最低不得大於最高；錯誤就地顯示且不送查詢。 |
| 車位 | 不限／有車位／未標示車位，對應省略／true／false。 |
| 降價 | 「曾有降價紀錄」開關；語意為歷史曾降價，不是只看最近一次變動。 |
| 狀態 | 在售／已下架單選；第一版採單選，不新增混合狀態模式。 |

作用中 chips 包含地區、平台及所有非預設條件，可單獨移除或清除全部；移除後回第 1 頁。排序為獨立選單，提供已定義的四項排序，詳見第 8 節。第一版不增加房數、屋齡區間、坪數區間、地圖或自由文字篩選。

查詢切換取消或忽略舊請求結果，晚回來的舊查詢不得蓋掉新條件。地區選項讀取失敗不阻擋全部房源瀏覽，提供重新載入選項；已套用但不在最新選項中的地區仍顯示為可移除 chip。

### 5.4 房源詳情與歷史

由上至下：返回／收藏 → 單張主圖片 → 標題、行政區、狀態 → 總價與單價 → 基本資料 → 系統評分 → 價格歷史 → 來源清單與時間。底部保留「查看原始刊登」主要動作，含系統手勢區留白，不遮擋最後一列。

不把各平台的縮圖當成同一刊登的完整相簿。圖片失敗有占位；影片不自動播放、不建立影音播放器，提示至原始刊登查看。地址、坪數、樓層、屋齡、車位、首見與最後觀測時間依實際欄位顯示；資料缺少不得補猜。

價格區必須區分「目前總價」與歷史事件價格。詳情沿用房源摘要，再分開取得完整歷史與來源；不要讓找房列表為每張卡片下載整段歷史。

歷史區呈現折線圖與可展開的紀錄列，最新紀錄在列上方、圖表時間由舊至新。圖使用實際時間間隔及直接線段，不用平滑曲線暗示未觀測價格；同時間多筆不合併或編造先後。點圖顯示日期、價格與變動，TalkBack 可從紀錄列取得相同資訊。

- 零筆：顯示「尚無價格紀錄」，不畫零元圖。
- 一筆：顯示單一觀測點及日期，不宣稱有趨勢或降幅。
- 多筆：可顯示期間最高／最低與變動；基準價為 0 或缺失時不計百分比。
- 第一次顯示最近 20 筆紀錄，按「顯示更多」每次展開 20 筆；資料仍來自同一份完整歷史。
- 圖表標題使用「系統擷取價格紀錄」，說明「可能包含不同平台的觀測價格」。資料表雖有來源欄位，第一版仍不做分平台曲線，也不把跨來源價差說成同一賣方調價。

外部來源列有平台名稱與可辨識的網域；僅開啟合法 HTTP／HTTPS 刊登網址，阻擋 `javascript:`、`file:` 等 scheme。沒有來源網址時停用主要動作並說明。來源不因 App 登入而取得 HouseLens token。

### 5.5 精選：優質排行與大降價

頁頂為兩個分段入口，保留各自的行政區選擇及位置。

優質排行按區顯示 Top 5，卡片包含名次、標題、總價、單價、屋齡、車位與系統評分。沒有資料的區域顯示「尚無可評分房源」。優質排行只取必要欄位，不為每一列額外查詳情；使用一致的白卡、圓角、字級與價格強調即可保持品牌一致。

大降價用縱向事件卡片，呈現行政區、標題、當次原價 → 當次降價後價格、降幅及降額，在 Supabase 查詢按降幅由大至小排序，同值依 ID 固定順序；地區條件一起送到資料查詢，不只過濾當前頁。

此頁固定說明：「曾出現的大降價紀錄；可能包含已下架物件，最新售價與狀態請查看詳情。」顯示實際觀測日期與目前上架狀態，但不將歷史事件宣稱為今日優惠，也不把事件價格標成現價。列表不以當前頁的 `latestIsBigDrop` 代替歷史大降價 view 的集合。

大降價使用 Supabase 原生 limit／offset 分頁（20 筆），以降幅及 ID 固定排序；不在手機下載全部房源或逐列補查詳情。

### 5.6 收藏

收藏依「Supabase Project URL＋登入 user ID＋property ID」隔離，只在本機保存。存入 ID、收藏時間及卡片必要摘要；不下載整個房源庫或完整歷史。預設依最近收藏排列，重新啟動仍在。

收藏頁先呈現可用摘要並標明尚未刷新，再對可見／即將可見項目讀取 Supabase 房源摘要重查，最多 3 個並行；捲動才查下一批。不能只掃當前找房頁來找收藏，否則跨頁收藏會消失。此做法適合少數人的本機收藏；大量收藏造成瓶頸時才新增批次唯讀查詢。

- 取消收藏立即更新所有畫面，提供短暫「復原」；儲存失敗則還原愛心並提示重試。
- 已下架仍保留，標示已下架；房源查無資料時顯示「此物件已移除」，保留摘要及移除收藏操作。
- 網路錯誤保留摘要並標「暫時無法更新」，不得當成已刪除。
- 不提供跨裝置同步、匯出或自動匯入網站 localStorage。
- 登出清除 session 與房源摘要／圖片快取，可保留該 user ID 隔離的收藏 ID；同一帳號登入後重新取得內容。切換帳號不得顯示另一帳號的收藏。
- 解除安裝或清除 App 資料會失去本機收藏，於帳號頁事先說明。

### 5.7 區域分析

以行政區選擇器、摘要卡（物件數／平均單價／總價範圍）、價格分布圖、平均單價趨勢、查看本區房源按鈕構成；不使用需橫向捲動的大型表格。

本頁透過資料庫 view 分析系統目前追蹤的房源樣本，標示「刊登資料分析，非實價登錄」。不自行加入房貸建議或市場漲跌推論。`insufficientData=true` 顯示資料不足說明；物件數為 0 時平均與範圍顯示「—」。

第一版沿用網站依行政區名稱篩選／分組的語意，跨縣市同名區可能合併。篩選與分析皆顯示「依行政區名稱統計，同名區可能包含不同縣市」說明；房源卡片以 city＋district 顯示實際位置。不能從縣市視覺分組推論目前已精確依縣市篩選；未來再一起調整篩選與分析。

分布使用伺服器 bucket 文字與筆數，不在手機重算分箱。趨勢日期為伺服器 DateOnly，照日期顯示，不做 UTC → 台灣日期位移；沒有資料或只有一個點時採與詳情一致的空／單點模式。圖表提供文字摘要及可讀資料列，不只依賴顏色或點選 tooltip。

## 6. 視覺與 Android 體驗

品牌依據為 `frontend/src/style.css` 及 `PropertyCard.vue`；網站像素值轉成適當 dp／sp，而非直接縮小桌面畫面。

| 項目 | App 規範 |
| --- | --- |
| 品牌色 | `#FF385C`；深色互動變體 `#E31C5F`；淡底 `#FFF0F3`。 |
| 底色 | 頁面 `#F7F7F7`、卡片 `#FFFFFF`。 |
| 文字 | 主文 `#222222`、次文 `#717171`；極淡灰只作裝飾／停用，不承載必要資訊。 |
| 邊框 | `#DDDDDD`／`#EBEBEB`，低陰影，不做每張卡浮起的厚重效果。 |
| 資料色 | 降價 `#C13515`、漲價 `#008A05`，沿用網站語意；永遠附箭頭與文字。 |
| 圓角 | 卡片 12dp、輸入框 8dp、chip 膠囊；底部面板採適合觸控的圓角。 |
| 留白 | 手機左右 16dp，卡片間 16dp，基本節奏 4／8／12／16／24dp。 |
| 字級 | 主要內文／卡片標題 16sp、次資訊至少 14sp、頁標題約 22sp、總價 24–28sp。數字使用清晰、對齊穩定的字型。 |
| 字體 | 第一版用 Android 系統中文字體；保留網站字重層級，不為完全複製拉丁字體增加字型下載。 |
| 主題 | 第一版淺色；不套用會改掉品牌主色的動態配色。系統深色時仍保持完整可讀的淺色主題。 |

原品牌紅與白字的組合不自動視為符合對比要求；一般文字目標 4.5:1、大字及必要圖示 3:1，不足時使用深色變體或深色文字。按鈕、愛心、關閉、圖表操作與 chips 點擊區至少 48×48dp，裝飾圖示本體可較小。[Compose 無障礙預設](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)

支援字級放大至 200%、TalkBack 順序與標籤、狀態宣告、鍵盤避讓、系統狀態列／手勢列 insets。手機 360dp 寬不應有整頁橫向捲動；較寬視窗可用兩欄卡片，詳情限制閱讀寬度。橫向與多視窗不鎖死，必要時回單欄。動畫簡短並尊重系統減少動畫設定；載入期間不以無止境閃動吸引注意。

## 7. 載入、連線、快取與錯誤

| 狀態 | 使用者看見的行為 |
| --- | --- |
| 首次載入 | 版面尺寸穩定的 skeleton；取得資料後替換，無大量跳動。 |
| 重新整理 | 保留現有內容；成功後更新，失敗顯示可重試提示。 |
| 載入下一頁失敗 | 只在列表底部顯示重試，不清空已載入頁面。 |
| 查詢無結果 | 顯示目前條件，提供清除條件；不誤報網路錯誤。 |
| 無網路／逾時 | 有記憶體資料時保留並標示舊資料；冷啟動無資料則顯示連線與重試頁。 |
| 401 | 合併同時發生的 session 更新，最多重試一次；仍失敗回登入並保留可恢復的導覽目標。 |
| 未核准／權限撤回 | 成員查核不通過或收到 403 時停止資料請求並清除可見快取；RLS 也獨立限制資料列，不無限更新 token。 |
| 詳情查無資料 | 顯示已移除，允許返回及取消收藏；收藏資料不自動消失。 |
| 429／5xx | 友善錯誤、遵守 Retry-After 或提供手動重試；不持續高速自動重送。 |
| 圖片失敗 | 固定比例占位圖，文字與操作照常使用。 |

正常進入頁面及手動更新時讀 Supabase；返回詳情前的列表不強制重抓。背景不輪詢、不觸發 Worker、不使用 SSE。網路請求需有有限逾時（設計預設 15 秒），取消不顯示為使用者操作失敗。

一般房源資料只作 session 記憶體快取；收藏可有摘要，圖片可用有上限的磁碟快取。第一版需連網，冷啟動須先完成有效成員確認才展示保留資料；不提供登入失效後的離線瀏覽繞過。已載入頁面在短暫斷線時可繼續閱讀，但不宣稱資料即時。

時間區分：資料取得時間是手機這次拿到回應的時間；物件 `lastSeenAt` 是最後觀測；最近輪次只是最近一次資料擷取作業。`completed` 可能仍有來源失敗，也可能只是單平台作業，不能顯示為「所有平台已同步」。可於資料狀態說明呈現最近輪次與來源結果，不呈現爬取控制或即時進度。

## 8. Supabase 查詢契約

正式連線為 `https://<project-ref>.supabase.co`，使用 `/rest/v1/`、`Accept-Profile: houselens`、publishable key 與目前使用者 JWT。所有金額保留十進位精度；萬元、坪、小數比例與 UTC／DateOnly 顯示規則不變。

| 畫面／用途 | 資料來源 | 最小查詢 |
| --- | --- | --- |
| 成員資格 | `ViewerMembers` | RLS 只允許讀自己的 UserId；每次畫面載入／刷新先查資格，無資料即拒絕。 |
| 找房／收藏摘要／詳情主資料 | `viewer_properties` view | 主資料、首張圖、標題、來源代碼陣列、最近變動、曾降價旗標；清單20筆與 exact count，不含完整歷史。 |
| 詳情來源 | `Listings` | 只取 PropertyId 相符的來源、標題、網址、圖片及刊登日期。 |
| 價格歷史 | `PriceHistoryEntries` | 指定 PropertyId，依 CapturedAt／Id 反向排序；分批取得，不能因 Data API 預設上限截斷歷史。 |
| 優質排行 | `viewer_top_properties` view | 已上架且有分數，依區名分組 row_number ≤ 5，取必要欄位。 |
| 大降價 | `viewer_big_drops` view | 每房源最近一次大降價事件，含事件日期與目前狀態，按降幅／ID排序分頁。 |
| 區域分析 | `viewer_district_stats` view | 在售樣本筆數、平均單價、總價範圍、分布及每日趨勢；伺服器聚合，不下載所有房源到手機計算。 |
| 地區選項 | `DistrictConfigs` | 只查 City／District／IsEnabled；含已停用但可能仍有房源的地區。 |
| 資料狀態 | `CrawlRuns`＋`SourceRunResults` | 最近一輪及來源成功／筆數，不授予讀取原始 ErrorMessage 的權限。 |

view 採 `security_invoker=true`，遵守底層表的 SELECT 權限與 RLS；不使用會繞過 RLS 的預設 definer view。[Supabase views](https://supabase.com/docs/guides/database/views)

地區使用區名集合、來源使用整數陣列 overlap；總價／車位／狀態／曾降價用原生 filters。App 先驗證價格範圍與分頁，查詢一律透過 SDK／URL builder 編碼，不串接使用者 SQL。排序如下：

- 系統評分優先：score 降冪、null 在後、總價升冪、ID升冪。
- 總價低到高：總價升冪、ID升冪。
- 單價排序（未提供優先）：null 在前、單價升冪、ID升冪。
- 最近收錄：FirstSeenAt 降冪、ID升冪。

`PropertyStatus` 為 Active=0、Delisted=1、Merged=2；`PriceChangeFlag` 為 None=0、Increased=1、Decreased=2。來源值沿用現有 SourceSite：F591=0、Rakuya=1、Sinyi=2、Yungching=3、TwHouse=4、HBHousing=5、CtHouse=6。DTO 在資料邊界轉成 UI 文案，不把整數直接呈現給使用者。

圖片由 Android 圖片 client 直接取得；信義白名單圖片可附來源網站 Referer，不需要另設圖片 proxy。無法載入則顯示占位與原刊登入口，不下載影片。Supabase Bearer／apikey 絕不傳給第三方圖片或刊登網站。限制 redirect，避免憑證跨 origin；HTTP 圖片在正式版不放寬 cleartext。

## 9. 登入與資料存取權限

Supabase Auth Google 登入＋系統瀏覽器 PKCE；第一版 callback 為 `tw.houselens.viewer://auth/callback`，在 Supabase Redirect URLs 精確登錄，不用 wildcard。不需要為 App 另租網域；未來已有網域再改 verified App Link。PKCE verifier 與 session 用 Android Keystore 保護並排除備份；驗證 callback 的 scheme／host／path／pending transaction，重送只處理一次。

- 新增最小 `houselens.ViewerMembers` 表，僅存核准的 Supabase UserId，由維運者管理；App 無新增／修改／刪除成員權限，也不製作會員管理 UI。
- 事先核對本人／家人 Google 身分，將對應 user ID 加入名單後才能看資料；首次登入產生 Auth 身分不等於開通。
- 所需資料表啟用 RLS，authenticated 只有 SELECT policy：必須在 ViewerMembers 名單內。名單自己的 policy 只允許看自己的列；anon 不授權讀取。
- 僅 grant 所需表／欄位／views 的 SELECT，沒有 INSERT／UPDATE／DELETE；不採 `GRANT ALL` 或全 schema default grant。NotificationLogs、評分設定、平台管理等資料不開放。
- RLS 不使用 FORCE，先確認既有受信任資料產生帳號為 owner／BYPASSRLS，再套政策，避免破壞現有寫入。既有表目前沒有其他 RLS 政策；正式套用前再次檢查漂移，有新政策不可直接覆蓋。
- RLS 拒絕讀取通常回空集合，不能一律期待 HTTP403。每次畫面載入／刷新先查自身會員列；不在名單就清畫面／快取並顯示未核准。即使查核後被移除，後續資料 SELECT 仍由資料庫 RLS 阻擋；已載入離線內容不保證被遠端立即撤回，不另做即時訂閱。

正式上線只需要現有 Supabase Project URL、publishable／anon key、Google provider 設定、核准 user IDs、SQL 權限／view 安裝及 Data API exposed schema 設定。沒有自建 API 主機、Caddy、DNS 或 Google client secret 放入 App 的步驟。現有資料產生端可維持原環境；其停止時手機仍能瀏覽舊資料。

SQL 先在隔離測試庫驗證匿名／未核准／核准／撤權／寫入禁止與既有寫入者相容性，再提供可檢閱腳本供正式套用。此次唯讀調查不代表遠端已配置完成。[Supabase RLS](https://supabase.com/docs/guides/database/postgres/row-level-security)

## 10. 最小實作邊界

Android 維持單一 app module、Compose／Material 3、ViewModel／StateFlow、手動注入、DataStore 收藏與成熟圖片／Auth 套件。資料接點為一個可測的 Supabase client，不增加 Repository／UseCase 層、後端專案、Edge Functions、Room 或背景同步。

必要修改僅在 `androidView/`：原生 App、少量 SQL views／RLS 安裝與測試腳本、建置及使用說明。`backend/`、`frontend/` 的實作保持原樣。已在隔離分支產生的新 API 兩筆提交已用 revert 撤回，留下追蹤紀錄。

先完成 UI 流程與本機 fixture 測試，再接 Supabase；無正式 publishable key／Google 設定時不把 fixture 當已連線。繼續採 Native 執行，測試、驗收與最後獨立 review 不省略。
## 11. 驗收與驗證策略

下列是未來實作驗收要求，不是本次已通過的測試。

| 編號 | 情境與通過條件 |
| --- | --- |
| AC-01 | 核准帳號 Google 登入成功且可讀資料；未核准帳號被成員查核與 RLS 拒絕、無房源或快取洩露；移除資格後下一次請求拒絕。 |
| AC-02 | 在行動網路、開發電腦關機時可開啟清單／詳情，證明直接連的是 Supabase；資料是否新鮮與可否讀取分開顯示。 |
| AC-03 | 篩選面板取消不改結果；套用後回第一頁；最低大於最高不送查詢；快速換條件時舊結果不覆蓋新結果；跨市同名區 fixture 合併成單一明示選項，不假裝已有縣市篩選。 |
| AC-04 | 連續瀏覽至少三頁，無重複 ID；同價／同分 fixture 的順序固定；下一頁失敗不清空列表，重試可續接。 |
| AC-05 | 清單、精選、收藏均可進詳情；返回恢復入口、條件及位置；收藏按鈕不誤觸跳頁。 |
| AC-06 | 收藏跨 App 重啟保留，跨登入帳號隔離；取消可復原，持久化失敗可回復；已下架、查無物件、網路失敗分別呈現。 |
| AC-07 | 0／1／多筆價格紀錄、同時間多筆、null、0 分母、跨午夜 UTC、百分比 -0.05 均正確呈現；DateOnly 不位移日期。 |
| AC-08 | 排行不逐卡請求詳情；大降價標成歷史事件價，可能下架與事件日期與當前狀態可辨識；不以現價或今日優惠誤導。 |
| AC-09 | 區域 0 筆、資料不足、單點趨勢及相同價格分箱可讀；圖表以文字／紀錄列提供等價資訊。 |
| AC-10 | 無網路、逾時、401、403、404、429、5xx、非 JSON 錯誤及失效圖片都有可恢復狀態，沒有永久 spinner 或無限重試。 |
| AC-11 | 只有 Supabase 自有 origin 的資料請求附 token，第三方圖片／刊登請求絕不含 token；非法 scheme 無法開啟；APK／log 不含私密憑證。 |
| AC-12 | 360dp 手機寬、200% 字級、TalkBack、橫向、鍵盤開啟、系統返回及手勢列皆可操作，必要資訊不被裁切。 |
| AC-13 | 所有房源操作為 Supabase SELECT；Auth token 交換等 POST 不屬於房源寫入。以 authenticated／anon 驗證房源、歷史與成員表不可新增、更新或刪除。 |
| AC-14 | 重複從背景返回、旋轉、程序重建與 OAuth 回呼皆不重複導航，不遺失已持久化收藏，不重新要求已有效登入。 |

效能目標：在選定的一台實體 Android 手機、資料服務 RTT 約 100ms、下行 10Mbps、首屏 20 筆且每筆歷史約 100 筆的基準資料下，首屏可讀內容 P95 ≤ 3 秒（圖片可稍後）、已載入頁返回回應 ≤ 300ms；60Hz 捲動基準至少 95% frame 在 16.7ms 內，無 ANR。此為設計目標，需記錄測試機型、網路與樣本量，不能當作已量測數字。資料查詢／歷史回應若先成為瓶頸，列出量測證據後再縮小傳輸契約，不先另建同步系統。

驗證分為：DTO／格式與狀態轉移單元測試、模擬 HTTP 的錯誤／競態測試、Compose 主要流程與無障礙測試、SQL RLS／view 與資料分頁整合測試，以及真機 Google 登入／外網／返回／流暢度驗證。後端測試採獨立測試環境，不能寫入或清空共用房源庫。

## 12. 交付界線與參考

規格已隨使用者最新方向更新，Native 實作繼續進行；App 的 UI、Supabase 權限與實際連線分別驗證，不把設計或模擬測試當成可交付成品。

### 專案依據

- [專案入口與結構](../README.md)
- [網站視覺 token](../frontend/src/style.css)
- [網站卡片與收藏現況](../frontend/src/components/PropertyCard.vue)
- [網站清單](../frontend/src/pages/PropertyList.vue)、[篩選](../frontend/src/components/FilterBar.vue)、[歷史圖表](../frontend/src/components/PriceHistoryPanel.vue)
- [前端 API 型別／圖片轉換](../frontend/src/services/api.ts)、[前端路由](../frontend/src/router/index.ts)
- [房源 API](../backend/src/HouseLens.Api/Endpoints/PropertiesEndpoints.cs)、[分析 API](../backend/src/HouseLens.Api/Endpoints/AnalyticsEndpoints.cs)
- [區域分析規則](../backend/src/HouseLens.Application/Analysis/DistrictAnalyticsService.cs)、[大降價資料計算](../backend/src/HouseLens.Application/Analysis/BigDropQueryService.cs)
- [圖片代理](../backend/src/HouseLens.Api/Endpoints/ProxyEndpoints.cs)、[最近輪次](../backend/src/HouseLens.Api/Endpoints/CrawlRunEndpoints.cs)
- [API 啟動／現有授權缺口](../backend/src/HouseLens.Api/Program.cs)、[PostgreSQL 設定使用方式](../backend/src/HouseLens.Infrastructure/DependencyInjection.cs)
- [資料模型](../docs/er-model.md)、[先前共用架構設計](../docs/superpowers/specs/2026-10-31-android-shared-data-design.md)、[原始交接背景](F:/SourceCode/HouseLens/handoff/1.md)
- [Android Compose 官方介紹](https://developer.android.com/compose)、[Capacitor 官方介紹](https://capacitorjs.com/docs)

現況調查包含靜態程式閱讀及 Supabase 唯讀結構／權限查詢；未啟動網站做視覺驗證，未變更遠端權限、未測試 Google 登入或 Android 真機。所有已知限制已在對應章節保留，不將設計目標寫成現成功能。
