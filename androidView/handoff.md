# HouseLens Android View 交接

更新日期：2026-11-07。請以繁體中文溝通。

## 先看這裡

使用者要求在一個可接續的節點停下，將狀態寫在 `F:\SourceCode\HouseLens\androidView\handoff.md`，由下一個 session 繼續。現在停在**架構已改為 Supabase 直連、規格／計畫整理完成、工具已下載**；**尚未建立 Android app，也沒有 APK、SQL views 或 RLS 安裝腳本**。

使用者最後確認的方向是：Android 只瀏覽爬取後的結果，盡量簡單，重點 UI／UX；使用原生 Kotlin＋Compose，直接用 Supabase Kotlin SDK 的 Postgrest＋Auth，不新增或部署 .NET API 層。Google 登入仍只開放事先核准的本人／家人。

## 正確工作目錄

| 項目 | 值 |
| --- | --- |
| 原始 checkout | `F:\SourceCode\HouseLens`，原分支 `android/probe-bootstrap`，有使用者既有未提交內容。 |
| **實際開發 worktree** | **`C:\Users\David\.codex\worktrees\9611\HouseLens`** |
| 開發分支 | **`feature/android-viewer`** |
| 建立方式 | 已使用 Codex 原生 create_worktree 工具建立；不要再建立第二個 worktree。 |
| 執行方式 | 使用者已選 **Native**，由主 agent 依序實作，最後一次獨立 review；不要再次詢問執行方式。 |

後續 exec 必須明確指定上面的 worktree 為 workdir；工具不會自動切換 cwd。若沙箱仍只允許 F: 原目錄寫入，為 C: worktree 的必要寫入使用正常權限申請，不改回原目錄實作。

**原目錄 F: 的 spec／plan 還是先前版本；最新版本在 C: worktree。** 此 handoff 同時保存於兩處，作為進入正確工作區的入口。不要直接依 F: 的舊 API plan 續作。保留原目錄使用者的 android、androidtest 及其他未提交資料；本任務一直排除 android／androidtest 的內容。

## 最新文件（實際內容不在本文件重複）

- [最新版 spec](C:/Users/David/.codex/worktrees/9611/HouseLens/androidView/spec.md)
- [總計畫／順序](C:/Users/David/.codex/worktrees/9611/HouseLens/androidView/plan.md)
- [Supabase 最小讀取權限計畫](C:/Users/David/.codex/worktrees/9611/HouseLens/androidView/plans/01-supabase-plan.md)
- [原生 App 計畫](C:/Users/David/.codex/worktrees/9611/HouseLens/androidView/plans/02-app-plan.md)

UI 的功能範圍、品牌色、四個入口、篩選與返回位置、收藏、圖表、錯誤及無障礙要求已在 spec；不要重新問一輪需求。完整瀏覽仍包含房源／詳情歷史／收藏／排行／大降價／區域分析，使用者沒有要求刪減成只有列表。

## 已完成與已撤回

1. 建立隔離 worktree，複製並提交規格／計畫：`e1894f1`。
2. 曾依舊計畫完成 A1 查詢驗證／分頁（`77969d2`）、A2 公開唯讀 JWT／成員授權（`2aa03e0`）。
3. 使用者明確改為 Supabase 直連後，已用 **`9b771c6`、`ecababb`** 撤回 A2、A1；保留歷史，不 reset／改寫歷史。
4. `git diff e1894f1 -- backend frontend` 無差異。不要重做舊 A1／A2，也不要新增 Caddy、API 主機、網域或 Edge Functions。
5. spec／plan 已更新。舊 `plans/01-api-plan.md` 已改為 `plans/01-supabase-plan.md`。原生計畫 B1–B7、Supabase S1–S2 都仍未實作。

先前後端測試證據：初始單元 159 通過；本機隔離庫上的基準整合 58 通過。舊 A1 的相關測試 60 通過；舊 A2 完成時單元159＋整合117全部通過。**這些不是新 Supabase App 的驗收結果**，A1／A2產品變更及新增測試已撤回。既有 bin／obj 可能仍有撤回前產物；若再跑後端測試，先正常建置，不直接用 --no-build 當目前源碼證據。

## Supabase 現況與用戶提供的設定

已使用原本的 `backend/appsettings.Local.json` 管理連線，以 readonly transaction 只查結構／權限，未查房源內容，也沒有遠端 DDL、DML、RLS 或 Auth 變更。

確認 `houselens` 有12個關聯（含 EF migration 紀錄），現有表未啟用 RLS，沒有 RLS policies；anon／authenticated 對這些表目前沒有 SELECT 或寫入權限。authenticator 的資料庫設定中未查到 exposed-schema 覆寫；這**不能證明** Dashboard 的 exposed schemas 已設定，S2須實查。

使用者已提供 Supabase 官方 Kotlin 範例、Project URL 與 **publishable key**。已保存於：

`C:\Users\David\.codex\worktrees\9611\HouseLens\androidView\supabase.local.properties`

它被 `androidView/.gitignore` 忽略，沒有提交。交接不貼 key／密碼；下一個 session 直接讀此檔供建置使用，不要重新向使用者索取已提供的公開設定。Google provider、callback、核准 user IDs 尚未配置或驗證。資料庫管理連線只作維運，絕不能放進 APK。

SDK 使用 Auth＋Postgrest，`defaultSchema = "houselens"`、明確 SerialName／欄位轉換；不要照範例查 `todos` 或假設 public schema。官方安裝頁支持 BOM＋所需模組，不必安裝範例中的可選 Agent Skills。

最小權限／views 與回呼方向已寫入新 spec：ViewerMembers名單、SELECT-only RLS、security-invoker views、精確 `tw.houselens.viewer://auth/callback`＋PKCE；無需另租App Link網域。RLS通常回空集合，App需先查自身會員列，不能把未核准誤當「沒有房源」。正式變更先備妥可檢閱SQL與隔離測試，依當時授權執行，不照官方通用範例GRANT ALL。

## 可直接重用的工具

所有工具都在 worktree 的 ignored scratch；不用重新下載。

| 工具 | 路徑／狀態 |
| --- | --- |
| JDK21.0.8 | `C:\Program Files\Android\openjdk\jdk-21.0.8`；已以此啟動Gradle。不要使用PATH中原本Java11。 |
| Gradle8.13 | `.superpowers/sdd/02-app-plan/tools/gradle-8.13/bin/gradle.bat`，`--version`成功。 |
| Android CLI tools22.0 | `.superpowers/sdd/02-app-plan/tools/sdk/cmdline-tools/bootstrap/cmdline-tools/bin/sdkmanager.bat`，`--version`成功；提示sdkmanager已deprecated但目前可用。 |
| SDK root | `.superpowers/sdd/02-app-plan/tools/sdk`；**尚未裝platform36／build-tools／system images**。 |
| PostgreSQL17.11可攜版 | `.superpowers/sdd/01-api-plan/pgsql/bin/`；僅作本機隔離測試。 |
| 本機測試PGDATA | `.superpowers/sdd/01-api-plan/pgdata`；127.0.0.1:55437，**交接前已正常停止**。 |

Gradle zip SHA256已核對官方值：`20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78`。Android工具zip SHA256已核對官方值：`90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a`。只驗證工具，尚未解析App依賴／編譯／跑Android測試。

PostgreSQL本機測試連線存於 ignored `.superpowers/sdd/01-api-plan/test-connection.txt`，不要印出內容。Docker daemon當時不可用，因此使用可攜PG；沒有動共用Supabase資料作測試。

## 下一步與已知注意事項

1. 進 C: worktree，讀最新文件與 `git status/log`，確認分支；不要回舊 API 路線。
2. 完成 B1：使用現成工具、補SDK36、建立最小單app module、載入本機Supabase公開設定；用Auth＋Postgrest SDK。版本是計畫基準，尚未實際resolve，若相容性有問題只修必要版本並記錄。
3. 依B2–B7完成手機UI、登入、本機收藏與驗收；UI測試可用fixture，但正式程式不能用假登入／假房源繞過權限。優先把核心看房流程做順，不增加通用框架。
4. S1在本機隔離PG實作並測SQL／RLS／views，S2再處理真Supabase設定。實際登入、RLS、行動網路與APK簽署仍未驗證。
5. 最後依Native流程做獨立全分支review；目前沒有進行產品完成review，因產品尚未建立。

環境經驗：Windows `Start-Process -Wait` 啟動pg_ctl會等到其背景PostgreSQL子程序結束，造成腳本停住；應 `Start-Process -WindowStyle Hidden -PassThru` 後只對pg_ctl物件 `.WaitForExit()`。PowerShell下載的 `.Content` 可能是byte[]，先UTF8解碼再Trim／驗hash；腳本使用 ErrorActionPreference=Stop 防止錯誤後繼續。

既有 Minimal API 測試在無本機設定的新worktree中，ConfigureAppConfiguration在builder.Build後才生效；如需重跑原測試，須給啟動用的 ConnectionStrings__Default 與 HOUSELENS_TEST_CONNECTION，兩者都指本機隔離庫。這是測試環境問題，不是新增API的理由。

## 執行紀錄

- `.superpowers/sdd/01-api-plan/progress.md`：已撤回的舊API工作與測試證據，只供追溯。
- `.superpowers/sdd/01-supabase-plan/progress.md`：新Supabase計畫，S1／S2待做。
- `.superpowers/sdd/02-app-plan/progress.md`：B1–B7待做、介面與工具狀態。

技能的task-brief工具只認數字heading，原計畫使用S1／B1；本輪已記錄以PowerShell擷取對應段落並維持同樣ledger／test證據的做法，不必為工具限制重編所有文件。Scratch為ignored，勿執行git clean -fdx清掉工具、設定與紀錄。

## Suggested skills

- `superpowers:using-git-worktrees`：辨識並重用既有worktree，不新建。
- `superpowers:executing-plans`：已選Native，按最新計畫執行並記錄進度。
- `ponytail:ponytail`（full）：最小完整改動，優先UI／UX，不加後端服務或抽象層。
- `superpowers:test-driven-development`：先驗證失敗案例再實作必要邏輯。
- `superpowers:systematic-debugging`：遇真實建置／測試問題先定位原因。
- `superpowers:verification-before-completion`：完成聲明必須有實際測試證據。
- `superpowers:requesting-code-review`：全部實作後一次獨立review。
- `handoff`：如需再次交接，只記錄差異與指向已存在文件，遮蔽秘密。
