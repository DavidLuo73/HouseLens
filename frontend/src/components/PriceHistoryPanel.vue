<template>
  <div class="price-panel">
    <div class="chart-area">
      <VChart :option="chartOption" autoresize class="chart" />
      <div class="stats-row">
        <div class="stat stat--high">
          <span class="stat-label">最高價</span>
          <span class="stat-value stat-value--high">{{ highPrice }} <em>萬</em></span>
        </div>
        <div class="stat stat--low">
          <span class="stat-label">最低價</span>
          <span class="stat-value stat-value--low">{{ lowPrice }} <em>萬</em></span>
        </div>
        <div class="stat">
          <span class="stat-label">變動幅度</span>
          <span class="stat-value" :class="priceChangeClass">{{ priceChangeText }}</span>
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
          <tr
            v-for="(h, i) in history"
            :key="`${h.capturedAt}-${i}`"
            :class="{ 'row--bigdrop': h.isBigDrop }"
          >
            <td>{{ formatDate(h.capturedAt) }}</td>
            <td>{{ h.totalPrice }}</td>
            <td>{{ h.unitPrice != null ? h.unitPrice.toFixed(1) : '—' }}</td>
            <td>
              <span v-if="h.changeFlag === 'none'" class="change--none">—</span>
              <span v-else-if="h.changeFlag === 'decreased'" class="change--down">
                ▼ {{ formatPercent(h.changePercent) }}
                <span v-if="h.isBigDrop" class="bigdrop-badge">大降價</span>
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
import { formatDate } from '@/utils/date'

use([LineChart, GridComponent, TooltipComponent, MarkPointComponent, CanvasRenderer])

const props = defineProps<{ history: PriceHistoryEntry[] }>()

const ascending = computed(() => [...props.history].reverse())

const highPrice = computed(() =>
  props.history.length ? Math.max(...props.history.map((h) => h.totalPrice)) : 0
)
const lowPrice = computed(() =>
  props.history.length ? Math.min(...props.history.map((h) => h.totalPrice)) : 0
)

const priceChangeText = computed(() => {
  if (props.history.length < 2) return '—'
  const first = ascending.value[0].totalPrice
  const last = ascending.value[ascending.value.length - 1].totalPrice
  const diff = last - first
  const pct = ((Math.abs(diff) / first) * 100).toFixed(1)
  if (diff < 0) return `▼ ${pct}%`
  if (diff > 0) return `▲ ${pct}%`
  return '持平'
})

const priceChangeClass = computed(() => {
  if (props.history.length < 2) return ''
  const first = ascending.value[0].totalPrice
  const last = ascending.value[ascending.value.length - 1].totalPrice
  if (last < first) return 'stat-value--down'
  if (last > first) return 'stat-value--up'
  return ''
})

const chartOption = computed(() => {
  const sorted = ascending.value
  return {
    tooltip: {
      trigger: 'axis',
      formatter: (params: { name: string; value: number }[]) =>
        `${params[0].name}<br/>總價：${params[0].value} 萬`,
    },
    grid: { left: 48, right: 16, top: 16, bottom: 28 },
    xAxis: {
      type: 'category',
      data: sorted.map((h) => formatDate(h.capturedAt)),
      axisLabel: { fontSize: 10, color: '#717171' },
      axisLine: { lineStyle: { color: '#EBEBEB' } },
    },
    yAxis: {
      type: 'value',
      name: '萬',
      min: 'dataMin',
      axisLabel: { fontSize: 10, color: '#717171' },
      splitLine: { lineStyle: { color: '#F7F7F7' } },
    },
    series: [{
      name: '總價',
      type: 'line',
      data: sorted.map((h) => h.totalPrice),
      smooth: true,
      lineStyle: { color: '#FF385C', width: 2.5 },
      itemStyle: { color: '#FF385C' },
      areaStyle: { color: 'rgba(255,56,92,0.06)' },
      symbol: 'circle',
      symbolSize: 5,
      markPoint: {
        data: sorted
          .filter((h) => h.isBigDrop)
          .map((h) => ({
            name: '大降價',
            coord: [formatDate(h.capturedAt), h.totalPrice],
            symbol: 'pin',
            symbolSize: 36,
            itemStyle: { color: '#C13515' },
          })),
      },
    }],
  }
})


function formatPercent(val?: number): string {
  if (val == null) return ''
  return `${(Math.abs(val) * 100).toFixed(1)}%`
}
</script>

<style scoped>
.price-panel {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
}

.chart-area {
  flex: 1 1 360px;
  min-width: 300px;
}

.chart {
  height: 260px;
}

.stats-row {
  display: flex;
  gap: 24px;
  margin-top: 12px;
  flex-wrap: wrap;
}

.stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-label {
  font-size: 0.72rem;
  font-weight: 700;
  color: var(--color-fg-2);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.stat-value {
  font-size: 1rem;
  font-weight: 700;
  color: var(--color-fg);
}

.stat-value em {
  font-style: normal;
  font-size: 0.78rem;
  font-weight: 500;
  color: var(--color-fg-2);
}

.stat-value--high { color: var(--color-price-down); }
.stat-value--low  { color: var(--color-price-up); }
.stat-value--down { color: var(--color-price-down); }
.stat-value--up   { color: var(--color-price-up); }

/* ===== 表格 ===== */
.table-area {
  flex: 1 1 300px;
  min-width: 280px;
  max-height: 320px;
  overflow-y: auto;
  border: 1px solid var(--color-border-soft);
  border-radius: var(--radius-input);
}

.history-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.85rem;
}

.history-table th {
  background: var(--color-bg-soft);
  padding: 9px 12px;
  text-align: left;
  font-size: 0.72rem;
  font-weight: 700;
  color: var(--color-fg-2);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  border-bottom: 1px solid var(--color-border-soft);
  position: sticky;
  top: 0;
}

.history-table td {
  padding: 9px 12px;
  border-bottom: 1px solid var(--color-border-soft);
  color: var(--color-fg);
}

.history-table tr:last-child td { border-bottom: none; }

.row--bigdrop td { background: #FFF8F7; }

.change--down { color: var(--color-price-down); font-weight: 700; }
.change--up   { color: var(--color-price-up); font-weight: 700; }
.change--none { color: var(--color-fg-3); }

.bigdrop-badge {
  display: inline-block;
  margin-left: 4px;
  padding: 1px 6px;
  background: var(--color-badge-drop-bg);
  color: var(--color-badge-drop-text);
  border-radius: var(--radius-pill);
  font-size: 0.7rem;
  font-weight: 700;
}
</style>
