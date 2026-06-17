# 物件清單歷史價格圖表 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 讓物件清單的每張卡片顯示常駐迷你價格走勢圖，並可點「看明細」彈出含折線圖與歷史明細表的 Modal。

**Architecture:** 後端清單端點補回完整 `priceHistory` 陣列（一次載入）；前端抽出共用的 `PriceHistoryPanel`（圖＋統計＋表），由新的 `PriceHistoryModal` 與既有 `PropertyDetail` 共用；`PropertyCard` 加迷你 sparkline 與觸發按鈕，`PropertyList` 協調 Modal 開關。

**Tech Stack:** 後端 ASP.NET Core Minimal API + EF Core 10（SQLite）、xUnit + FluentAssertions；前端 Vue 3 + TypeScript + ECharts（vue-echarts）、Vitest + Vue Test Utils。

## Global Constraints

- 後端語言：C# / .NET 10；前端：TypeScript 5.x + Vue 3.5。
- 不新增後端 API 端點；複用既有 `GET /api/properties` 與 `GET /api/properties/{id}`。
- 不實作「最近 90 天」時間範圍下拉（明確非目標）。
- 不更動清單既有的篩選／排序／分頁邏輯。
- 前端元件樣式用 `<style scoped>`，沿用既有色系（主色青 `#06b6d4` / `#0891b2`，降價紅 `#dc2626`，漲價綠 `#059669`）。
- 前端測試以 jsdom 執行，無 canvas；測試中需 stub `VChart` 元件，避免 ECharts 實際渲染。
- 後端測試：每個會寫入資料的測試類別需各自持有 `TestWebApplicationFactory`（`IClassFixture`），避免污染其他類別的空資料庫斷言。

---

### Task 1: 後端清單端點回傳完整 priceHistory

**Files:**
- Modify: `backend/src/HouseLens.Api/Endpoints/PropertiesEndpoints.cs:70-94`（`GetProperties` 的 `.Select(...)` 投影）
- Test: `backend/tests/HouseLens.IntegrationTests/Api/PropertyPriceHistoryTests.cs`（新建）

**Interfaces:**
- Produces：`GET /api/properties` 回應的每個 item 新增欄位 `priceHistory`，為物件陣列，每筆含 `capturedAt`（ISO 時間）、`totalPrice`（number）、`unitPrice`（number?）、`changeFlag`（`"none"|"increased"|"decreased"`）、`changePercent`（number?）、`isBigDrop`（bool），依 `capturedAt` 倒序（最新在前）。形狀與 `GET /api/properties/{id}` 的 `priceHistory` 一致。

- [ ] **Step 1: Write the failing test**

建立 `backend/tests/HouseLens.IntegrationTests/Api/PropertyPriceHistoryTests.cs`：

```csharp
using FluentAssertions;
using HouseLens.Domain.Entities;
using HouseLens.Domain.Enums;
using HouseLens.Infrastructure.Persistence;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using System.Net;
using System.Net.Http.Json;
using System.Text.Json;

namespace HouseLens.IntegrationTests.Api;

// 清單端點需回傳每個物件的完整歷史價格序列（供 sparkline 與明細 Modal）
public class PropertyPriceHistoryTests(TestWebApplicationFactory factory)
    : IClassFixture<TestWebApplicationFactory>
{
    private readonly TestWebApplicationFactory _factory = factory;
    private readonly HttpClient _client = factory.CreateClient();
    private static readonly JsonSerializerOptions _jsonOpts = new(JsonSerializerDefaults.Web);

    [Fact]
    public async Task GetProperties_ItemIncludesPriceHistoryOrderedDesc()
    {
        var propId = Guid.NewGuid();
        using (var scope = _factory.Services.CreateScope())
        {
            var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
            db.Properties.Add(new Property
            {
                Id = propId,
                City = "新北市",
                District = "中和區",
                AreaPing = 30m,
                CurrentTotalPrice = 1280m,
                Status = PropertyStatus.Active,
            });
            db.PriceHistoryEntries.AddRange(
                new PriceHistoryEntry
                {
                    PropertyId = propId,
                    TotalPrice = 1300m,
                    CapturedAt = new DateTime(2026, 6, 1, 0, 0, 0, DateTimeKind.Utc),
                    ChangeFlag = PriceChangeFlag.None,
                },
                new PriceHistoryEntry
                {
                    PropertyId = propId,
                    TotalPrice = 1280m,
                    CapturedAt = new DateTime(2026, 6, 11, 0, 0, 0, DateTimeKind.Utc),
                    ChangeFlag = PriceChangeFlag.Decreased,
                    ChangePercent = -0.015m,
                    IsBigDrop = false,
                });
            await db.SaveChangesAsync();
        }

        var response = await _client.GetAsync("/api/properties");
        response.StatusCode.Should().Be(HttpStatusCode.OK);

        var body = await response.Content.ReadFromJsonAsync<JsonElement>(_jsonOpts);
        var item = body.GetProperty("items").EnumerateArray()
            .Single(i => i.GetProperty("id").GetGuid() == propId);

        item.TryGetProperty("priceHistory", out var history).Should().BeTrue();
        history.ValueKind.Should().Be(JsonValueKind.Array);
        history.GetArrayLength().Should().Be(2);

        var first = history[0];
        first.GetProperty("capturedAt").GetDateTime()
            .Should().Be(new DateTime(2026, 6, 11, 0, 0, 0, DateTimeKind.Utc));
        first.GetProperty("totalPrice").GetDecimal().Should().Be(1280m);
        first.GetProperty("changeFlag").GetString().Should().Be("decreased");
        first.TryGetProperty("unitPrice", out _).Should().BeTrue();
        first.TryGetProperty("changePercent", out _).Should().BeTrue();
        first.TryGetProperty("isBigDrop", out _).Should().BeTrue();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `dotnet test backend/tests/HouseLens.IntegrationTests --filter PropertyPriceHistoryTests`
Expected: FAIL — 斷言 `item.TryGetProperty("priceHistory", ...)` 為 false（清單投影目前不含此欄位）。

- [ ] **Step 3: 在清單投影加入 priceHistory**

於 `PropertiesEndpoints.cs` 的 `GetProperties` 內，將 `Sources = ...` 之後（`.Select(p => new { ... }` 物件結尾、`})` 之前）新增欄位：

```csharp
                Sources = p.Listings.Select(l => new { SourceSite = l.SourceSite.ToString(), l.Url, l.ImageUrl }),
                PriceHistory = p.PriceHistory
                    .OrderByDescending(h => h.CapturedAt)
                    .Select(h => new
                    {
                        h.CapturedAt,
                        h.TotalPrice,
                        h.UnitPrice,
                        ChangeFlag = h.ChangeFlag.ToString().ToLower(),
                        h.ChangePercent,
                        h.IsBigDrop
                    })
```

（注意：原 `Sources = ...` 該行行尾需保留逗號；其餘欄位與 `.Include(p => p.PriceHistory.OrderByDescending(h => h.CapturedAt).Take(1))` 維持不變——`Take(1)` 僅影響 `LatestXxx` 取值，投影內的 `p.PriceHistory.OrderByDescending(...)` 會獨立查出完整序列。）

- [ ] **Step 4: Run test to verify it passes**

Run: `dotnet test backend/tests/HouseLens.IntegrationTests --filter PropertyPriceHistoryTests`
Expected: PASS。

- [ ] **Step 5: 確認既有清單測試未被破壞**

Run: `dotnet test backend/tests/HouseLens.IntegrationTests --filter PropertyFilterTests`
Expected: PASS（13 個測試全綠，含 `EmptyDb_ReturnsTotalZero`）。

- [ ] **Step 6: Commit**

```bash
git add backend/src/HouseLens.Api/Endpoints/PropertiesEndpoints.cs backend/tests/HouseLens.IntegrationTests/Api/PropertyPriceHistoryTests.cs
git commit -m "feat(api): 清單端點回傳完整 priceHistory 供卡片走勢圖使用"
```

---

### Task 2: 前端型別與共用 PriceHistoryPanel 元件

**Files:**
- Modify: `frontend/src/services/api.ts:42-69`（`Property` 加 `priceHistory`；`PropertyDetail` 移除重複宣告）
- Create: `frontend/src/components/PriceHistoryPanel.vue`
- Modify: `frontend/src/pages/PropertyDetail.vue`（改用 `PriceHistoryPanel`）
- Test: `frontend/tests/PriceHistoryPanel.spec.ts`（新建）

**Interfaces:**
- Consumes：`PriceHistoryEntry`（已存在於 `api.ts`）。
- Produces：
  - `Property` 介面新增 `priceHistory: PriceHistoryEntry[]`。
  - `PriceHistoryPanel.vue` 預設匯出元件，props：`history: PriceHistoryEntry[]`。內含折線圖、最高/最低價統計、歷史明細表。對外無 emit。

- [ ] **Step 1: 調整型別**

`frontend/src/services/api.ts`：在 `Property` 介面末端（`sources: PropertySource[]` 之後）新增一行：

```ts
  sources: PropertySource[]
  priceHistory: PriceHistoryEntry[]
}
```

並將 `PropertyDetail` 介面中重複的 `priceHistory: PriceHistoryEntry[]` 移除（保留 `address` / `firstSeenAt` / `lastSeenAt`）：

```ts
export interface PropertyDetail extends Property {
  address?: string
  firstSeenAt: string
  lastSeenAt: string
}
```

- [ ] **Step 2: Write the failing test**

建立 `frontend/tests/PriceHistoryPanel.spec.ts`：

```ts
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import PriceHistoryPanel from '../src/components/PriceHistoryPanel.vue'
import type { PriceHistoryEntry } from '../src/services/api'

const history: PriceHistoryEntry[] = [
  { capturedAt: '2026-06-11T00:00:00Z', totalPrice: 1280, unitPrice: 42.6, changeFlag: 'decreased', changePercent: -0.015, isBigDrop: false },
  { capturedAt: '2026-06-01T00:00:00Z', totalPrice: 1300, unitPrice: 43.3, changeFlag: 'none', isBigDrop: false },
  { capturedAt: '2026-05-20T00:00:00Z', totalPrice: 1250, unitPrice: 41.6, changeFlag: 'increased', changePercent: 0.04, isBigDrop: false },
]

function mountPanel() {
  return mount(PriceHistoryPanel, {
    props: { history },
    global: { stubs: { VChart: true } },
  })
}

describe('PriceHistoryPanel', () => {
  it('renders one table row per history entry', () => {
    const wrapper = mountPanel()
    expect(wrapper.findAll('.history-table tbody tr')).toHaveLength(3)
  })

  it('shows highest and lowest total price', () => {
    const wrapper = mountPanel()
    expect(wrapper.find('.stat--high').text()).toContain('1300')
    expect(wrapper.find('.stat--low').text()).toContain('1250')
  })
})
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd frontend && npx vitest run tests/PriceHistoryPanel.spec.ts`
Expected: FAIL — 無法解析 `../src/components/PriceHistoryPanel.vue`（檔案尚未建立）。

- [ ] **Step 4: 建立 PriceHistoryPanel.vue**

建立 `frontend/src/components/PriceHistoryPanel.vue`：

```vue
<template>
  <div class="price-panel">
    <div class="chart-area">
      <VChart :option="chartOption" autoresize class="chart" />
      <div class="stats">
        <div class="stat stat--high">
          <span class="stat-label">最高價</span>
          <span class="stat-value">{{ highPrice }} 萬</span>
        </div>
        <div class="stat stat--low">
          <span class="stat-label">最低價</span>
          <span class="stat-value">{{ lowPrice }} 萬</span>
        </div>
      </div>
    </div>

    <div class="table-area">
      <table class="history-table">
        <thead>
          <tr>
            <th>日期</th>
            <th>總價（萬）</th>
            <th>單價（萬/坪）</th>
            <th>變動</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="h in history" :key="h.capturedAt" :class="{ 'row--bigdrop': h.isBigDrop }">
            <td>{{ formatDate(h.capturedAt) }}</td>
            <td>{{ h.totalPrice }}</td>
            <td>{{ h.unitPrice != null ? h.unitPrice.toFixed(1) : '—' }}</td>
            <td>
              <span v-if="h.changeFlag === 'none'" class="change--none">—</span>
              <span v-else-if="h.changeFlag === 'decreased'" class="change--down">
                ▼ {{ formatPercent(h.changePercent) }}
                <span v-if="h.isBigDrop" class="bigdrop-label">大降價</span>
              </span>
              <span v-else class="change--up">▲ {{ formatPercent(h.changePercent) }}</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, MarkPointComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { PriceHistoryEntry } from '@/services/api'

use([LineChart, GridComponent, TooltipComponent, MarkPointComponent, CanvasRenderer])

const props = defineProps<{ history: PriceHistoryEntry[] }>()

const ascending = computed(() => [...props.history].reverse())

const highPrice = computed(() =>
  props.history.length ? Math.max(...props.history.map((h) => h.totalPrice)) : 0
)
const lowPrice = computed(() =>
  props.history.length ? Math.min(...props.history.map((h) => h.totalPrice)) : 0
)

const chartOption = computed(() => {
  const sorted = ascending.value
  return {
    tooltip: {
      trigger: 'axis',
      formatter: (params: { name: string; value: number }[]) =>
        `${params[0].name}<br/>總價：${params[0].value} 萬`,
    },
    grid: { left: 48, right: 16, top: 16, bottom: 28 },
    xAxis: { type: 'category', data: sorted.map((h) => formatDate(h.capturedAt)) },
    yAxis: { type: 'value', name: '萬', min: 'dataMin' },
    series: [
      {
        name: '總價',
        type: 'line',
        data: sorted.map((h) => h.totalPrice),
        smooth: true,
        lineStyle: { color: '#06b6d4' },
        itemStyle: { color: '#0891b2' },
        markPoint: {
          data: sorted
            .filter((h) => h.isBigDrop)
            .map((h) => ({
              name: '大降價',
              coord: [formatDate(h.capturedAt), h.totalPrice],
              symbol: 'pin',
              itemStyle: { color: '#e74c3c' },
            })),
        },
      },
    ],
  }
})

function formatDate(iso?: string): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('zh-TW', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

function formatPercent(val?: number): string {
  if (val == null) return ''
  return `${(Math.abs(val) * 100).toFixed(1)}%`
}
</script>

<style scoped>
.price-panel { display: flex; gap: 1rem; flex-wrap: wrap; }
.chart-area { flex: 1 1 360px; min-width: 300px; }
.chart { height: 280px; }
.stats { display: flex; gap: 1.5rem; margin-top: 0.5rem; }
.stat { display: flex; flex-direction: column; }
.stat-label { font-size: 0.75rem; color: #6b7280; }
.stat-value { font-size: 1.1rem; font-weight: 700; }
.stat--high .stat-value { color: #dc2626; }
.stat--low .stat-value { color: #059669; }
.table-area { flex: 1 1 320px; min-width: 280px; max-height: 320px; overflow-y: auto; }
.history-table { width: 100%; border-collapse: collapse; font-size: 0.9rem; }
.history-table th, .history-table td { padding: 0.5rem 0.75rem; text-align: left; border-bottom: 1px solid #e5e7eb; }
.history-table th { background: #f9fafb; font-weight: 600; position: sticky; top: 0; }
.row--bigdrop td { background: #fff5f5; }
.change--down { color: #dc2626; }
.change--up { color: #059669; }
.change--none { color: #9ca3af; }
.bigdrop-label { background: #fee2e2; color: #dc2626; border-radius: 3px; padding: 0 0.3rem; font-size: 0.75rem; margin-left: 0.4rem; }
</style>
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd frontend && npx vitest run tests/PriceHistoryPanel.spec.ts`
Expected: PASS（2 個測試）。

- [ ] **Step 6: 重構 PropertyDetail 改用 PriceHistoryPanel**

在 `frontend/src/pages/PropertyDetail.vue`：

將「Price Trend Chart」與「Price History Table」兩個 `<section>`（模板第 77–114 行）整段取代為：

```vue
      <!-- Price History -->
      <section v-if="property.priceHistory.length">
        <h2>價格趨勢與歷史紀錄</h2>
        <PriceHistoryPanel :history="property.priceHistory" />
      </section>
```

`<script setup>` 內移除 ECharts 相關匯入與 `chartOption`（不再於本頁使用），改為匯入面板元件。具體：刪除這些行：

```ts
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
```
```ts
use([LineChart, GridComponent, TooltipComponent, CanvasRenderer])
```
以及整個 `const chartOption = computed(() => { ... })` 區塊。

新增匯入：

```ts
import PriceHistoryPanel from '@/components/PriceHistoryPanel.vue'
```

保留 `formatDate`（其他資訊欄位仍使用）；若 `formatPercent` 在移除表格後已無人使用，一併刪除以免 lint 報未使用變數。對應地，移除 `<style scoped>` 中只服務舊圖表/表格的樣式（`.chart-container`、`.history-table`、`.row--bigdrop`、`.change--*`、`.bigdrop-label`），保留版面其他樣式。

- [ ] **Step 7: 驗證型別與 lint**

Run: `cd frontend && npx vue-tsc --noEmit && npm run lint`
Expected: 無型別錯誤、無未使用變數警告。

- [ ] **Step 8: Commit**

```bash
git add frontend/src/services/api.ts frontend/src/components/PriceHistoryPanel.vue frontend/src/pages/PropertyDetail.vue frontend/tests/PriceHistoryPanel.spec.ts
git commit -m "refactor(frontend): 抽出共用 PriceHistoryPanel 並由詳情頁複用"
```

---

### Task 3: PriceHistoryModal 彈窗元件

**Files:**
- Create: `frontend/src/components/PriceHistoryModal.vue`
- Test: `frontend/tests/PriceHistoryModal.spec.ts`（新建）

**Interfaces:**
- Consumes：`Property`（含 `priceHistory`、`title`、`listingUrl`）；`PriceHistoryPanel`（props `history`）。
- Produces：`PriceHistoryModal.vue` 預設匯出元件，props：`property: Property`；emit：`close`（無參數）。底部「查看商品」連結指向 `property.listingUrl`，「關閉」按鈕與背景遮罩點擊皆 emit `close`。

- [ ] **Step 1: Write the failing test**

建立 `frontend/tests/PriceHistoryModal.spec.ts`：

```ts
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import PriceHistoryModal from '../src/components/PriceHistoryModal.vue'
import type { Property } from '../src/services/api'

const property = {
  id: 'p1',
  title: '測試物件',
  city: '新北市',
  district: '中和區',
  areaPing: 30,
  hasParking: false,
  currentTotalPrice: 1280,
  status: 'active',
  isNew: false,
  latestIsBigDrop: false,
  listingUrl: 'https://example.com/p1',
  sources: [],
  priceHistory: [
    { capturedAt: '2026-06-11T00:00:00Z', totalPrice: 1280, changeFlag: 'decreased', changePercent: -0.015, isBigDrop: false },
    { capturedAt: '2026-06-01T00:00:00Z', totalPrice: 1300, changeFlag: 'none', isBigDrop: false },
  ],
} as unknown as Property

function mountModal() {
  return mount(PriceHistoryModal, {
    props: { property },
    global: { stubs: { VChart: true } },
  })
}

describe('PriceHistoryModal', () => {
  it('renders the property title and embeds the panel', () => {
    const wrapper = mountModal()
    expect(wrapper.text()).toContain('測試物件')
    expect(wrapper.findComponent({ name: 'PriceHistoryPanel' }).exists()).toBe(true)
  })

  it('emits close when the close button is clicked', async () => {
    const wrapper = mountModal()
    await wrapper.find('.modal-close').trigger('click')
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('emits close when the overlay is clicked', async () => {
    const wrapper = mountModal()
    await wrapper.find('.modal-overlay').trigger('click.self')
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('links 查看商品 to the listing url', () => {
    const wrapper = mountModal()
    expect(wrapper.find('.modal-visit').attributes('href')).toBe('https://example.com/p1')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run tests/PriceHistoryModal.spec.ts`
Expected: FAIL — 無法解析 `PriceHistoryModal.vue`。

- [ ] **Step 3: 建立 PriceHistoryModal.vue**

建立 `frontend/src/components/PriceHistoryModal.vue`：

```vue
<template>
  <div class="modal-overlay" @click.self="$emit('close')">
    <div class="modal-dialog" role="dialog" aria-modal="true">
      <header class="modal-header">
        <h3 class="modal-title">{{ property.title }}</h3>
        <button class="modal-x" aria-label="關閉" @click="$emit('close')">×</button>
      </header>

      <div class="modal-body">
        <PriceHistoryPanel v-if="property.priceHistory.length" :history="property.priceHistory" />
        <p v-else class="modal-empty">尚無歷史價格紀錄</p>
      </div>

      <footer class="modal-footer">
        <a
          v-if="property.listingUrl"
          class="modal-visit"
          :href="property.listingUrl"
          target="_blank"
          rel="noopener noreferrer"
        >查看商品</a>
        <button class="modal-close" @click="$emit('close')">關閉</button>
      </footer>
    </div>
  </div>
</template>

<script setup lang="ts">
import PriceHistoryPanel from '@/components/PriceHistoryPanel.vue'
import type { Property } from '@/services/api'

defineProps<{ property: Property }>()
defineEmits<{ close: [] }>()
</script>

<style scoped>
.modal-overlay {
  position: fixed; inset: 0; background: rgba(0, 0, 0, 0.45);
  display: flex; align-items: center; justify-content: center; z-index: 1000; padding: 1rem;
}
.modal-dialog {
  background: #fff; border-radius: 12px; width: min(920px, 100%);
  max-height: 90vh; display: flex; flex-direction: column; overflow: hidden;
}
.modal-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 1rem 1.25rem; border-bottom: 1px solid #e5e7eb;
}
.modal-title { margin: 0; font-size: 1.1rem; }
.modal-x { border: none; background: none; font-size: 1.5rem; line-height: 1; cursor: pointer; color: #6b7280; }
.modal-body { padding: 1.25rem; overflow-y: auto; }
.modal-empty { text-align: center; color: #6b7280; padding: 2rem; }
.modal-footer {
  display: flex; justify-content: flex-end; gap: 0.75rem;
  padding: 0.85rem 1.25rem; border-top: 1px solid #e5e7eb;
}
.modal-visit {
  padding: 0.45rem 1.1rem; border-radius: 8px; background: #06b6d4; color: #fff;
  font-size: 0.9rem; font-weight: 600; text-decoration: none;
}
.modal-close {
  padding: 0.45rem 1.1rem; border-radius: 8px; border: 1.5px solid #d1d5db;
  background: #fff; color: #374151; font-size: 0.9rem; font-weight: 600; cursor: pointer;
}
</style>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx vitest run tests/PriceHistoryModal.spec.ts`
Expected: PASS（4 個測試）。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/PriceHistoryModal.vue frontend/tests/PriceHistoryModal.spec.ts
git commit -m "feat(frontend): 新增歷史價格 Modal 元件"
```

---

### Task 4: PropertyCard 迷你走勢圖與「看明細」按鈕

**Files:**
- Modify: `frontend/src/components/PropertyCard.vue`
- Test: `frontend/tests/PropertyCard.spec.ts`（新建）

**Interfaces:**
- Consumes：`Property`（含 `priceHistory`）。
- Produces：`PropertyCard` 新增 emit `open-history`（參數為 `Property`）。卡片在 `priceHistory.length >= 2` 時顯示 `.card-spark` sparkline；否則不顯示。永遠顯示 `.spark-btn`（看明細）按鈕，點擊 emit `open-history`。

- [ ] **Step 1: Write the failing test**

建立 `frontend/tests/PropertyCard.spec.ts`：

```ts
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import PropertyCard from '../src/components/PropertyCard.vue'
import type { Property } from '../src/services/api'

function makeProperty(overrides: Partial<Property> = {}): Property {
  return {
    id: 'p1',
    title: '測試物件',
    city: '新北市',
    district: '中和區',
    areaPing: 30,
    hasParking: false,
    currentTotalPrice: 1280,
    status: 'active',
    isNew: false,
    latestIsBigDrop: false,
    sources: [],
    priceHistory: [
      { capturedAt: '2026-06-11T00:00:00Z', totalPrice: 1280, changeFlag: 'decreased', changePercent: -0.015, isBigDrop: false },
      { capturedAt: '2026-06-01T00:00:00Z', totalPrice: 1300, changeFlag: 'none', isBigDrop: false },
    ],
    ...overrides,
  } as Property
}

function mountCard(property: Property) {
  return mount(PropertyCard, { props: { property }, global: { stubs: { VChart: true } } })
}

describe('PropertyCard', () => {
  it('shows the sparkline when there are at least two history points', () => {
    const wrapper = mountCard(makeProperty())
    expect(wrapper.find('.card-spark').exists()).toBe(true)
  })

  it('hides the sparkline when history has fewer than two points', () => {
    const wrapper = mountCard(makeProperty({ priceHistory: [
      { capturedAt: '2026-06-11T00:00:00Z', totalPrice: 1280, changeFlag: 'none', isBigDrop: false },
    ] }))
    expect(wrapper.find('.card-spark').exists()).toBe(false)
  })

  it('emits open-history with the property when 看明細 is clicked', async () => {
    const property = makeProperty()
    const wrapper = mountCard(property)
    await wrapper.find('.spark-btn').trigger('click')
    expect(wrapper.emitted('open-history')?.[0]).toEqual([property])
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run tests/PropertyCard.spec.ts`
Expected: FAIL — 找不到 `.card-spark` / `.spark-btn`（尚未加入）。

- [ ] **Step 3: 在 PropertyCard 模板加入 sparkline 與按鈕**

在 `frontend/src/components/PropertyCard.vue` 模板中，`<div class="card-price"> ... </div>`（第 51–68 行）之後、`</div>`（`.card-body` 結尾）之前，新增：

```vue
      <div class="card-trend">
        <VChart v-if="hasTrend" :option="sparkOption" autoresize class="card-spark" />
        <span v-else class="card-spark-empty">—</span>
        <button type="button" class="spark-btn" @click="emit('open-history', property)">看明細</button>
      </div>
```

- [ ] **Step 4: 在 PropertyCard script 加入 sparkline 邏輯與 emit**

將 `<script setup lang="ts">` 區塊改為：

```ts
import { computed } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { Property } from '@/services/api'

use([LineChart, GridComponent, CanvasRenderer])

const props = defineProps<{ property: Property }>()
const emit = defineEmits<{ 'open-history': [property: Property] }>()

const hasTrend = computed(() => props.property.priceHistory.length >= 2)

const sparkOption = computed(() => {
  const ascending = [...props.property.priceHistory].reverse()
  return {
    grid: { left: 2, right: 2, top: 4, bottom: 4 },
    xAxis: { type: 'category', show: false, data: ascending.map((_, i) => i) },
    yAxis: { type: 'value', show: false, scale: true },
    series: [
      {
        type: 'line',
        data: ascending.map((h) => h.totalPrice),
        showSymbol: false,
        smooth: true,
        lineStyle: { width: 1.5, color: '#06b6d4' },
        areaStyle: { color: 'rgba(6, 182, 212, 0.12)' },
      },
    ],
  }
})

function formatPercent(val?: number): string {
  if (val == null) return ''
  return `${(Math.abs(val) * 100).toFixed(1)}%`
}
```

（保留既有 `formatPercent`，因為價格變動標籤仍使用；其餘模板不變。）

- [ ] **Step 5: 加入對應樣式**

在 `<style scoped>` 末端新增：

```css
.card-trend {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.6rem;
}
.card-spark {
  flex: 1;
  height: 36px;
}
.card-spark-empty {
  flex: 1;
  color: #d1d5db;
  font-size: 0.85rem;
}
.spark-btn {
  flex-shrink: 0;
  padding: 0.25rem 0.7rem;
  border: 1.5px solid #a5f3fc;
  border-radius: 14px;
  background: #ecfeff;
  color: #0891b2;
  font-size: 0.78rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}
.spark-btn:hover {
  background: #06b6d4;
  border-color: #06b6d4;
  color: #fff;
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd frontend && npx vitest run tests/PropertyCard.spec.ts`
Expected: PASS（3 個測試）。

- [ ] **Step 7: Commit**

```bash
git add frontend/src/components/PropertyCard.vue frontend/tests/PropertyCard.spec.ts
git commit -m "feat(frontend): 卡片新增迷你走勢圖與看明細按鈕"
```

---

### Task 5: PropertyList 串接 Modal

**Files:**
- Modify: `frontend/src/pages/PropertyList.vue`
- Test: `frontend/tests/PropertyList.spec.ts`（新建）

**Interfaces:**
- Consumes：`PropertyCard`（emit `open-history`）、`PriceHistoryModal`（props `property`、emit `close`）。
- Produces：清單頁監聽卡片 `open-history` 後渲染 `PriceHistoryModal`；收到 `close` 後關閉。

- [ ] **Step 1: Write the failing test**

建立 `frontend/tests/PropertyList.spec.ts`（以 stub 隔離 API 與子元件，聚焦 Modal 開關協調）：

```ts
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Property } from '../src/services/api'

const sampleProperty = {
  id: 'p1', title: '測試物件', city: '新北市', district: '中和區', areaPing: 30,
  hasParking: false, currentTotalPrice: 1280, status: 'active', isNew: false,
  latestIsBigDrop: false, sources: [], priceHistory: [],
} as unknown as Property

vi.mock('../src/services/api', () => ({
  api: {
    properties: {
      list: vi.fn().mockResolvedValue({ total: 1, page: 1, pageSize: 20, items: [sampleProperty] }),
    },
  },
}))

import PropertyList from '../src/pages/PropertyList.vue'

const cardStub = {
  name: 'PropertyCard',
  props: ['property'],
  emits: ['open-history'],
  template: '<button class="card-stub" @click="$emit(\'open-history\', property)">card</button>',
}
const modalStub = {
  name: 'PriceHistoryModal',
  props: ['property'],
  emits: ['close'],
  template: '<div class="modal-stub" @click="$emit(\'close\')">modal</div>',
}

function mountList() {
  return mount(PropertyList, {
    global: {
      stubs: { FilterBar: true, PropertyCard: cardStub, PriceHistoryModal: modalStub, RouterLink: true },
    },
  })
}

describe('PropertyList modal wiring', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('opens the modal when a card emits open-history and closes it again', async () => {
    const wrapper = mountList()
    await flushPromises()

    expect(wrapper.find('.modal-stub').exists()).toBe(false)

    await wrapper.find('.card-stub').trigger('click')
    expect(wrapper.find('.modal-stub').exists()).toBe(true)

    await wrapper.find('.modal-stub').trigger('click') // stub emits close
    expect(wrapper.find('.modal-stub').exists()).toBe(false)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run tests/PropertyList.spec.ts`
Expected: FAIL — `.modal-stub` 在點擊後仍不存在（清單頁尚未渲染 Modal）。

- [ ] **Step 3: 在 PropertyList 串接 Modal**

`frontend/src/pages/PropertyList.vue`：

模板中，`<PropertyCard v-for=... />` 改為監聽事件：

```vue
        <PropertyCard
          v-for="p in items"
          :key="p.id"
          :property="p"
          @open-history="openHistory"
        />
```

在最外層 `<div class="property-list">` 結尾 `</div>` 之前（`</template>` 內最後），新增 Modal：

```vue
    <PriceHistoryModal
      v-if="historyTarget"
      :property="historyTarget"
      @close="closeHistory"
    />
```

`<script setup>` 內新增匯入與狀態（與既有 import 並列）：

```ts
import PriceHistoryModal from '@/components/PriceHistoryModal.vue'
```
```ts
const historyTarget = ref<Property | null>(null)
function openHistory(p: Property) {
  historyTarget.value = p
}
function closeHistory() {
  historyTarget.value = null
}
```

（`ref` 與 `Property` 型別皆已在現有 import 中。）

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx vitest run tests/PropertyList.spec.ts`
Expected: PASS。

- [ ] **Step 5: 全部前端測試與型別檢查**

Run: `cd frontend && npx vitest run && npx vue-tsc --noEmit && npm run lint`
Expected: 全部測試通過、無型別錯誤、無 lint 錯誤。

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/PropertyList.vue frontend/tests/PropertyList.spec.ts
git commit -m "feat(frontend): 清單頁串接歷史價格 Modal"
```

---

## 完成後手動驗收（非自動化）

1. 啟動後端 API 與前端 dev server。
2. 開啟物件清單頁：每張卡片價格下方應有青色迷你走勢圖（歷史 ≥2 筆時）與「看明細」按鈕。
3. 點「看明細」：彈出 Modal，左側折線圖＋最高/最低價，右側可捲動歷史明細表；「查看商品」開新分頁原始連結，「關閉」或點遮罩關閉。
4. 進入物件詳情頁：價格趨勢與歷史紀錄改用同一面板呈現，外觀與行為正常。
