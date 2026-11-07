# HouseLens Android View Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. The user selected Native execution; do not ask them to choose again. Steps use checkbox (`- [ ]`) syntax.

**Goal:** 建立本人／家人可用、直接讀取 Supabase 爬取結果的原生 Android 瀏覽 App，以 UI／UX 為優先。

**Architecture:** 一個 Compose app module，以 Supabase Kotlin SDK 的 Postgrest＋Auth 查詢現有 houselens 資料；本機收藏。少量資料庫 views／SELECT-only RLS 提供必要讀取，不新增 API server、Edge Function、同步或另一份資料庫。

**Tech Stack:** Kotlin／Compose／Material 3、Supabase Auth＋Postgrest、Ktor engine、Coil、DataStore、PostgreSQL SQL views。

**Spec:** [spec.md](spec.md)，已依使用者最新的「直連 Supabase、簡單設計、UI／UX好用」更新。

## Global Constraints

- 不讀取／沿用 `android/`、`androidtest/`，不修改既有 backend／frontend 實作。
- 第一版完整瀏覽：找房、詳情／歷史、收藏、精選（排行／降價）、區域分析。
- 不爬取、不管理共用設定、不寫房源、不做跨裝置收藏同步。
- Google登入僅本人／家人核准名單；APK只含公開用戶端key與個人session，沒有DB密碼／service-role key。
- Android最低API26、單一app module、手動注入，沒有通用Repository／UseCase／DI框架。
- 每頁20筆、收藏最多3個並行刷新、15秒逾時、觸控至少48dp、360dp寬與200%字級可用。

## Review Focus

1. 帳號切換後舊請求不能回填畫面或收藏（B2／B3）。
2. Bearer／apikey不能傳到第三方圖片／來源網址（B1／B2）。
3. Session／PKCE損壞或重複callback應安全回登入（B2）。
4. 歷史超過REST預設上限、同時間／同價資料不能遺漏或編造（S1／B1／B5）。
5. RLS回空集合不一定是沒有房源，須先確認成員資格（S1／B2）。

---

## 現況與後續順序

使用者原先選Native實作，後來明確改為Android直連Supabase。先前完成的A1／A2已用revert撤回，backend／frontend相對設計基準無差異；不要重做舊API計畫。

| 計畫 | 工作 | 狀態 |
| --- | --- | --- |
| [01-supabase-plan.md](plans/01-supabase-plan.md) | S1 本機SQL／RLS／views與測試；S2正式Supabase設定與驗證 | 尚未實作；只有遠端唯讀結構檢查完成。 |
| [02-app-plan.md](plans/02-app-plan.md) | B1 SDK／專案；B2登入；B3收藏；B4找房；B5詳情；B6精選／區域；B7驗收APK | B1已完成（ff2f197）；B2安全儲存／重試子步驟完成（62232d8），其餘待做。 |

下一個session從B2登入控制器／callback／AppGraph接線繼續，再按B3→B7；S1可在資料fixture確定後完成，S2與B7的真實連線需正式RLS／Google設定。先把手機核心流程做順，不在準備階段再擴充後端。

執行採Native，由同一實作者依TDD完成；每個任務保留測試與提交紀錄，最後一次獨立整體review。必要的變更判斷寫入plan-scoped ledger，沒有完成的測試不勾選。

## Ponytail 邊界

使用現有表與SDK能力，只安裝Auth＋Postgrest，不裝Realtime／Storage／Functions。圖表只做直線／柱狀與可讀資料列；收藏用DataStore，不加收藏API或Room。照片直接載入，不另設proxy；失敗有占位及原刊登入口。查詢用必要SQL views，不複製整套.NET API到新服務。

## 連線設定與部署輸入

使用者已提供Project URL與publishable key，保存在此worktree被忽略的 `androidView/supabase.local.properties`；交接／版控不重複貼key。DB管理連線仍只在原專案本機設定中，App不讀它。

Supabase唯讀檢查確認12個既有關聯、RLS尚未啟用、anon／authenticated目前沒有表的讀寫權限。正式尚需S1腳本、exposed schema確認、Google provider與核准user IDs；不再需要API主機／網域／Caddy。

工具快取位於 `.superpowers/sdd/02-app-plan/tools`：Gradle8.13、Android command-line tools22.0已下載且核對SHA256，JDK21.0.8可用；SDK36／build-tools35.0.0／API26模擬器已安裝，依賴解析與Debug APK建置通過。下一個session先閱讀 [handoff.md](handoff.md) 的實際狀態。

## 驗收對應

- AC01／02／13：S1／S2／B2／B7的成員、RLS、外網與禁止寫入。
- AC03／04／05：B1／B4／B5的篩選、分頁、詳情返回位置。
- AC06：B3收藏持久化／帳號隔離／移除與失敗狀態。
- AC07／08／09：S1／B1／B5／B6的歷史、降價事件、區域分析。
- AC10／11／14：B1／B2／B7的網路／session／圖片／程序恢復。
- AC12與效能：B4–B7真機、無障礙與量測。
