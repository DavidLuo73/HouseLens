# HouseLens Android View 交接

更新：2026-11-07。繁體中文；每次整份覆蓋並同步F入口／C worktree。不保存密碼、私鑰、個資或手機序號。

## 當前結果與最新要求

**v0.1.0免登入正式簽署APK已交付並安裝S25+，手機實際找房顯示687筆。** 使用者另要求「參考網站icon、色系UI/UX相同、時尚一點」，目前正在做v0.1.1圖示更新，不能把0.1.0當作此新需求已完成。

- Android只view，Supabase anon SELECT，不登入、不爬取、不管理、不寫房源；目前沒有Worker。
- 使用者已明確批准正式匿名最小欄位／Data API，並在Dashboard勾houselens保留graphql_public/public。資料對持公開key者可讀，此界線已告知且批准。
- 長期簽署金鑰已明確批准建立，使用者承諾安全備份；不得重建或覆寫。
- Supabase Kotlin sample已真正連線驗證，再以實際資料層完成11GET／9端點業務DTO解碼。

## 工作區與恢復入口

唯一開發worktree：`C:/Users/David/.codex/worktrees/9611/HouseLens`；分支`feature/android-viewer`。以`git status`／`git log -1`核對本機提交狀態；不要以舊aee821c當最新HEAD。所有開發git/build/test明確使用C workdir。F原目錄僅交接與transfer，保留其他使用者變更。不讀取／沿用android/、androidtest/，不修改backend/frontend。最新icon需求可唯讀參考網站SVG，不能移植其爬蟲／管理操作。

本環境直接write root是F，C寫入／build需sandbox escalation；先把精確腳本置於F `.superpowers/sdd/.../transfer`讓Root檢閱執行。不刪SDK、Gradle/cache、logs、clusters或plan workspace，不用git clean。沿用SDD＋Ponytail full，逐項與最後審查。

- C `androidView/spec.md`頂端最新裁定優先於舊Google／會員段落。
- `androidView/plans/03-anonymous-v1-plan.md`：已完成匿名v1交付；完整人工B7仍OPEN。
- `androidView/plans/04-icons-plan.md`：最新圖示工作；F transfer `task-icons-brief.md`／`task-icons-report.md`（後者進行中）。
- `androidView/validation/acceptance.md`：詳細實際結果、APK hash、14 AC與未完成範圍。
- F transfer `anonymous-final-review.md`：最後來源與UX修正通過；`anonymous-app-report.md`／`anonymous-final-fix-report.md`及SQL對應report保留歷史。
- C `.superpowers/sdd/03-anonymous-v1-plan/progress.md`與04-icons-plan ledger；舊02-app-plan/progress.md保留B1–B7與原始591失敗，不能重做已完成項。

## 交付與裝置保護

APK：`C:/Users/David/Downloads/HouseLens/HouseLens-0.1.0.apk`（12,333,745bytes）；同目錄.sha256。
SHA256：`7776FD77EBEA3BC454C964D8160A8F0CEF464380053753B5CD433412BDA9C5E5`。
package tw.houselens.viewer，versionCode1／name0.1.0，min26／target36，非debug；正式憑證SHA256 `de6fe3b9729224098250683aed7c1a2cdb9f3d4f28ac4a19848903b51c4d27ee`。

**S25+現在已安裝正式App，可能已有使用者收藏。不得再對此手機跑connectedDebugAndroidTest、解除安裝或清資料。** v0.1.1需versionCode2、同一簽章，以install-r更新保留資料；簽章衝突就停止，不能用解除安裝解決。Root已告知使用者可拔USB，更新前重新確認是否連線。

私有身分位於`C:/Users/David/AppData/Local/HouseLens/Signing`，owner-only ACL已核對；keystore繼承受保護父目錄，recovery／ignored signing.local.properties只有目前Windows使用者。不要讀出密碼／備份內容。發佈檔放Downloads，不進Git；F原分支沒有忽略`.superpowers/delivery`，不要把APK改放那裡。

目前AppGraph／ViewerContent是真正匿名瀏覽root，沒有Auth/member/fakeAuthorized/bypass；資訊取代帳號。本機namespace project＋local-device-v1，舊登入owner檔不匯入、不刪除。圖片client與Supabase憑證隔離，清單20筆、歷史分批、收藏最多3並行。

## 驗證與正式環境

C `.superpowers/sdd/02-app-plan/`：
- 最終Full `b7-resume-anonymous-Full-20261107-204758-993`：67 JVM＋23 API26UI PASS，lint0errors／21warnings；360dp/font2。
- 最終乾淨Landscape `b7-resume-anonymous-Landscape-20261107-205053-037`：3／3 PASS，IME、完整標籤/editor、clipped48dp；對應PNG已Root及reviewer看過。
- S25+ SM-S9360 Android17/API37 OneUI9：`anonymous-phone-ui-20261107-204103-480`3／3 PASS，未改顯示設定／不擷取手機截圖；Espresso3.5曾API37反射失敗，現test-only明確3.7已解決。
- 真實SDK sample `anonymous-live-sdk-20261107-191008-256`；完整業務DTO `anonymous-live-business-20261107-204455-790`：11GET／9端點，properties20/687、top8區37、drops20/28、stats8、latest7來源、detail1來源3歷史；不輸出房源內容。臨時測試已刪且src hashes一致。
- guard8／8：`anonymous-Guards-20261107-190200-239`。
- signed artifact：`anonymous-release-20261107-205448-344`；批准憑證／APK hash匹配。
- 正式手機安裝／啟動：`anonymous-release-phone-20261107-210035-110`。原/dev/tty未提供XML，後續受控暫存XML只抽App節點／count687且已刪；最終證據F transfer `anonymous-release-phone-live.json`。

正式匿名SQL SHA `C1DDA8912FB1D362E11E3608FEA773BAA266E47546A9A4100A845A60F12F2D09`已審查部署；六非FORCE RLS／六anon SELECT／四invoker，API寫權0、houselens routines0，未改房源或Auth列。F SQL transfer `anonymous-deploy-runs/20261107T105812075Z-3bc820570427`為Apply；`20261107T110749839Z-d77ab82493b7`為exposed REST200且metadata仍最小權限。SQL隔離測試39notice／19漂移／rollback／revoke／restore通過，cluster停止並保留。停用讀權只用已審查revoke.sql，不能全schema REVOKE或DROP。

目前沒有active build／test／remote部署操作；接續時仍先看process/log。JDK21 `C:/Program Files/Android/openjdk/jdk-21.0.8`；SDK／Gradle cache／Android user dir在C `.superpowers/sdd/02-app-plan/`，emulator-5580/API26。所有模擬器runner明確限定device並還原size/font/orientation/IME。不要無serial的connected測試。

## 最新圖示工作

實作者`/root/viewer_icons`，要求原生vector、不加套件；參考網站App.vue的house、PropertyCard.vue的heart/star。品牌#FF385C／white／#222222／#717171；文字互動保留可讀#E31C5F。配置四tab、品牌標誌、資訊、篩選／排序／刷新、收藏／評分／歷史、返回與外連、adaptive launcher。保留文字/語意、48dp與既有callbacks/pending state，避免glyph/emoji。versionCode2／name0.1.1；不改資料或登入。

Root保管docs/ledger/release/install；新agent只產生F精確patch，C mutation/build由Root escalation。先正常字級與200%／橫向檢視PNG，再獨立review、同key簽署／hash、install-r。手機上已有正式App，所以只用模擬器instrumentation。

## 限制／不可抹掉的紀錄

最後review1–7（來源4、merged、undo重試、篩選舊error、pending愛心、palette、真正retrycallback）與實際橫向雙重IMEpadding／欄位裁切都已關閉。焦點已取得時避免在IME重排中重複tap是測試準備，不改產品行為；無固定sleep／圖片同步作弊。

舊591漏選原`b7-full-green.log`仍FAIL且原因未證實；後續全通過＋逐chip斷言不等於因果修復。所有失敗與首次測試compile錯誤／簽署wrapper未引用-J參數失敗均保留；不要把失敗run重命名成PASS。

完整B7仍OPEN：真TalkBack、手勢／多視窗、程序真正終止、手機200%字級、行動網路／電腦關機，以及受控P95／frame／ANR未全部完成。不能用fixture、Activity recreation或220ms Activity launch冒充這些驗收。可用v0.1.0試用交付已完成；接續做最新icon需求與未驗收項，不重做Auth或S1–B6。

Computer Use瀏覽器host與原生pipe在本session皆不可用；使用者已手動完成Dashboard，沒有持續設定阻擋。Supabase管理plugin未連接。不要重複要求URL/key或重啟整個設置流程。
