# Phase 0 Research: 房屋售價追蹤系統

本文件解決 plan.md Technical Context 與使用者「待確認事項」中的未定決策，
並記錄關鍵技術選型的理由與替代方案。

## 1. 資料庫：SQLite vs PostgreSQL/SQL Server

- **Decision**: 初版採 SQLite（單檔）+ EF Core，透過 EF Core 抽象資料存取層。
- **Rationale**: 單一使用者、本機部署，資料量級為數千至數萬物件、數十萬價格點，
  遠在 SQLite 舒適範圍內；單檔便於備份與遷移，符合憲章「簡潔優先」。EF Core 抽象
  使未來若需改用 PostgreSQL，僅需更換 provider 與少量 migration 調整。
- **Alternatives considered**: PostgreSQL/SQL Server — 提供更強併發與分析能力，
  但帶來安裝、服務管理與部署成本，個人單機場景不划算，故初版不採用。

## 2. 物件評分演算法權重（初版建議值）

- **Decision**: 綜合評分 = 加權總和，初版建議權重（各因子先正規化為 0–1 後加權）：
  - 單價相對該區域平均（越低越好）：**0.40**
  - 屋齡（越新越好，線性遞減，>40 年趨近 0）：**0.25**
  - 是否有車位（有=1，無=0）：**0.20**
  - 地段（以區域基礎權重，預留捷運距離因子）：**0.15**
- **Rationale**: 對自住購屋而言，價格相對行情的合理性權重最高；屋齡與車位為次要
  但顯著的實用因子；地段先以區域粗略代理，待資料充足再細化。權重 MUST 以設定檔
  （或 DB 設定表）驅動，評分計算保持單一純函式以利測試與調整（憲章原則 II/IV）。
- **Alternatives considered**: 機器學習估價模型 — 資料量與標註不足，過度複雜，
  違反簡潔原則，初版不採用。

## 3. 降價門檻

- **Decision**: 採「百分比與絕對金額擇一達標」即視為大降價，初版預設：
  - 降幅 ≥ **5%**，或
  - 絕對降幅 ≥ **30 萬元**
  門檻值 MUST 可由設定檔調整。
- **Rationale**: 純百分比對高總價物件不敏感，純金額對低總價物件過嚴，兩者擇一可
  兼顧。5% / 30 萬為房市常見有感降幅的合理起點。
- **Alternatives considered**: 僅百分比或僅金額 — 各有盲區，已說明。

## 4. 部署環境

- **Decision**: 初版鎖定本機 Windows 執行；排程由應用內建 Quartz.NET 負責（非依賴
  Windows Task Scheduler），API 與 Worker 可分別啟動或合併於單一主機程序。
  提供選用的 Docker Compose（後端 + 前端靜態檔）作為日後可攜部署選項，但非初版必要。
- **Rationale**: 應用內建排程使「每日自動執行」不依賴外部排程器設定，降低人工配置；
  本機執行符合單一使用者場景與憲章簡潔原則。
- **Alternatives considered**: 雲端部署 — 牽涉成本、密鑰管理與對外安全，且需處理
  來源平台對雲端 IP 的反爬限制，初版不納入。

## 5. 爬蟲技術：靜態解析 vs 瀏覽器渲染

- **Decision**: 以「能力分級」策略：優先使用 HTTP + HtmlAgilityPack/AngleSharp 做
  靜態解析；僅對確認為 SPA／需 JS 渲染的來源啟用 Playwright（Chromium）。每個來源
  實作獨立 `ISourceScraper`，輸出統一的正規化 DTO。
- **Rationale**: 靜態解析輕量、快速、穩定；Playwright 成本高（資源與維護），僅在
  必要來源啟用以控制複雜度（憲章原則 II）。統一介面使來源可獨立增減而不影響其他
  （對應 FR-003）。
- **Alternatives considered**: 全面 Playwright — 對所有來源過度且耗資源；
  逆向各站內部 JSON API — 較脆弱且更易違反條款，僅在公開且穩定時審慎評估。

## 6. 負責任爬取（憲章附加約束 / FR-015）

- **Decision**:
  - 抓取前讀取並遵循各來源 `robots.txt`；尊重其服務條款，僅作個人購屋研究用途。
  - 每來源套用速率限制（預設每請求間隔 ≥ 3–5 秒、可設定）與隨機抖動，
    並設定合理 User-Agent。
  - 失敗採指數退避重試（上限 3 次），單一來源失敗不中斷其他來源（對應 FR-014）。
  - 每日僅抓取目標區域與條件範圍，避免大範圍掃描。
- **Rationale**: 兼顧資料取得與對來源站點的負責任行為，直接落實憲章與 FR-015。
- **Alternatives considered**: 高頻並行抓取 — 對來源造成負載且易觸發封鎖，違反約束。

## 7. 重複物件比對策略（對應 FR-005、spec 假設）

- **Decision**: 以特徵相似度做模糊比對。比對鍵：正規化地址／區域 + 坪數（±0.5 坪內）
  + 樓層（完全相同）+ 屋齡（±1 年）。四項皆相符視為同一物件並合併為單一 Property，
  各來源刊登保留於 Listing；總價不一致時，Property 目前價以「最新抓取且最低價之刊登」
  為代表，並保留各 Listing 各自價格。比對門檻 MUST 可調整。
- **Rationale**: 地址常因平台格式差異無法完全相等，故輔以坪數／樓層／屋齡的數值容差，
  降低漏合併與誤合併（對應 SC-003：合併率 ≥ 90%、誤合併 < 5%）。
- **Alternatives considered**: 僅以物件連結去重 — 無法跨平台辨識同一物件；
  純文字相似度（編輯距離）— 對地址雜訊敏感、易誤判。

## 8. 「已下架」判定（對應 FR-007、spec 假設）

- **Decision**: 某物件連續 **2 個抓取日**未被任何來源抓到，才標記為「已下架」，
  並保留其全部歷史記錄。期間若重新出現則恢復為「上架中」。
- **Rationale**: 緩衝可吸收來源暫時性異常或單日抓取失敗造成的誤判（對應 SC-004：
  下架後次一抓取日正確標記率 ≥ 95%，緩衝確保不因單日波動誤標）。
- **Alternatives considered**: 單日未見即下架 — 易因暫時性抓取失敗誤判。

## 9. 排程

- **Decision**: 使用 Quartz.NET 於 BackgroundService 中註冊每日 Cron 觸發（預設清晨
  離峰，如 06:00，可設定）。排程執行流程：爬蟲 → 寫入/更新物件與價格歷史 →
  去重合併 → 降價偵測 + 評分 → 產生大降價與各區 Top 5 清單 → LINE 推播。
- **Rationale**: 應用內建排程不依賴外部工具，符合 FR-001「不需人工介入」。
- **Alternatives considered**: 純 `PeriodicTimer` — 缺 Cron 表達力；外部 Task
  Scheduler — 增加人工配置與環境耦合。

## 10. LINE 通知

- **Decision**: 使用 LINE Messaging API（官方帳號 + Channel Access Token，push
  message）。Token 以本機設定／環境變數保存，不入版控。推播兩類訊息：大降價清單、
  各區域 Top 5 優質物件。
- **Rationale**: LINE Notify 已停止服務，Messaging API 為官方現行方案。
- **Alternatives considered**: Email / 其他 IM — 使用者明確指定 LINE。
- **待實作確認**: 確認目標推播對象（個人 userId 或綁定官方帳號之好友）與訊息格式
  （文字 vs Flex Message），於實作階段定案；不影響資料模型與 API 設計。
