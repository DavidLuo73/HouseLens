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
