# HouseLens Frontend

Vue 3 + Vite + TypeScript 前端，提供物件清單、篩選、歷史價格圖表、區域分析與大降價總覽等功能。

## 技術棧

| 分類 | 技術 |
|------|------|
| 語言 | TypeScript 6 |
| UI 框架 | Vue 3.5（`<script setup>`） |
| 打包工具 | Vite 8 |
| 狀態管理 | Pinia |
| 路由 | Vue Router 5 |
| 圖表 | Apache ECharts（vue-echarts） |
| 測試 | Vitest + Vue Test Utils |

## 安裝先決條件

- Node.js 20+

## 安裝與啟動

```bash
cd frontend
npm install
npm run dev       # 開發伺服器（http://localhost:5173）
```

API 請求透過 Vite dev server proxy 轉發至 `http://localhost:5000/api`，後端須同時啟動。

## 頁面功能

| 路由 | 頁面 | 說明 |
|------|------|------|
| `/` | PropertyList | 物件清單、篩選（區域/價格/車位/降價）、分頁 |
| `/properties/:id` | PropertyDetail | 物件詳情 + 歷史價格折線圖 |
| `/analytics` | DistrictAnalytics | 各區平均單價、分布圖、趨勢圖 |
| `/big-drops` | BigDropOverview | 大降價物件總覽（地址、原價、新價、降幅、連結） |
| `/top-properties` | TopProperties | 各區 Top 5 優質物件 |
| `/scoring-config` | ScoringConfig | 評分權重與大降價門檻設定 |

## 執行測試

```bash
npm run test            # 執行一次（CI 模式）
npm run test:watch      # 監聽模式
npm run test:coverage   # 含覆蓋率報告
```

## 設定

前端透過 Vite proxy 連接後端，預設指向 `http://localhost:5000`。若後端 port 不同，修改 `vite.config.ts`：

```ts
proxy: {
  '/api': {
    target: 'http://localhost:你的Port',
    changeOrigin: true,
  },
},
```

## 建置（正式環境）

```bash
npm run build           # 輸出至 frontend/dist/
npm run preview         # 本機預覽建置結果
```
