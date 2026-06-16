<template>
  <div class="property-detail">
    <RouterLink to="/properties" class="back-link">← 返回清單</RouterLink>

    <div v-if="loading" class="loading">載入中…</div>
    <div v-else-if="error" class="error">{{ error }}</div>

    <template v-else-if="property">
      <!-- Header -->
      <div class="header">
        <h1>{{ property.title }}</h1>
        <div class="tags">
          <span v-if="property.isNew" class="tag tag--new">新上架</span>
          <span v-if="property.status === 'delisted'" class="tag tag--delisted">已下架</span>
          <span v-if="property.latestIsBigDrop" class="tag tag--bigdrop">大降價</span>
          <span v-if="property.hasParking" class="tag tag--parking">有車位</span>
        </div>
      </div>

      <!-- Basic Info -->
      <section class="info-grid">
        <div class="info-item">
          <label>地區</label>
          <span>{{ property.district }}</span>
        </div>
        <div class="info-item">
          <label>地址</label>
          <span>{{ property.address ?? '未提供' }}</span>
        </div>
        <div class="info-item">
          <label>坪數</label>
          <span>{{ property.areaPing }} 坪</span>
        </div>
        <div class="info-item">
          <label>樓層</label>
          <span>{{ property.floor ?? '未提供' }}</span>
        </div>
        <div class="info-item">
          <label>屋齡</label>
          <span>{{ property.ageYears != null ? `${property.ageYears} 年` : '未提供' }}</span>
        </div>
        <div class="info-item">
          <label>總價</label>
          <span class="price">{{ property.currentTotalPrice }} 萬</span>
        </div>
        <div class="info-item">
          <label>單價</label>
          <span>{{ property.currentUnitPrice != null ? `${property.currentUnitPrice.toFixed(1)} 萬/坪` : '未提供' }}</span>
        </div>
        <div class="info-item" v-if="property.score != null">
          <label>綜合評分</label>
          <span class="score">{{ (property.score * 100).toFixed(0) }}</span>
        </div>
        <div class="info-item">
          <label>首見</label>
          <span>{{ formatDate(property.firstSeenAt) }}</span>
        </div>
        <div class="info-item">
          <label>最後更新</label>
          <span>{{ formatDate(property.lastSeenAt) }}</span>
        </div>
      </section>

      <!-- Sources -->
      <section v-if="property.sources.length">
        <h2>來源連結</h2>
        <ul class="sources">
          <li v-for="s in property.sources" :key="s.url">
            <a :href="s.url" target="_blank" rel="noopener noreferrer">
              [{{ s.sourceSite }}] {{ s.title ?? s.url }}
              <span v-if="s.postedDate" class="muted">（{{ formatDate(s.postedDate) }}）</span>
            </a>
          </li>
        </ul>
      </section>

      <!-- Price Trend Chart -->
      <section v-if="property.priceHistory.length">
        <h2>價格趨勢</h2>
        <div class="chart-container">
          <VChart :option="chartOption" autoresize style="height: 280px" />
        </div>
      </section>

      <!-- Price History Table -->
      <section v-if="property.priceHistory.length">
        <h2>歷史紀錄</h2>
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
            <tr v-for="h in property.priceHistory" :key="h.capturedAt"
                :class="{ 'row--bigdrop': h.isBigDrop }">
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
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { api, type PropertyDetail } from '@/services/api'

use([LineChart, GridComponent, TooltipComponent, CanvasRenderer])

const route = useRoute()
const property = ref<PropertyDetail | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)

onMounted(async () => {
  try {
    property.value = await api.properties.get(route.params.id as string)
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '載入失敗'
  } finally {
    loading.value = false
  }
})

const chartOption = computed(() => {
  if (!property.value?.priceHistory.length) return {}
  const sorted = [...property.value.priceHistory].reverse()
  return {
    tooltip: { trigger: 'axis', formatter: (params: { name: string; value: number }[]) => `${params[0].name}<br/>總價：${params[0].value} 萬` },
    xAxis: { type: 'category', data: sorted.map((h) => formatDate(h.capturedAt)) },
    yAxis: { type: 'value', name: '萬', min: 'dataMin' },
    series: [
      {
        name: '總價',
        type: 'line',
        data: sorted.map((h) => h.totalPrice),
        smooth: true,
        markPoint: {
          data: sorted
            .filter((h) => h.isBigDrop)
            .map((h) => ({ name: '大降價', coord: [formatDate(h.capturedAt), h.totalPrice], symbol: 'pin', itemStyle: { color: '#e74c3c' } })),
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
.property-detail { max-width: 900px; margin: 0 auto; padding: 1.5rem; }
.back-link { color: #3b82f6; text-decoration: none; font-size: 0.9rem; }
.back-link:hover { text-decoration: underline; }
.header { display: flex; align-items: flex-start; gap: 1rem; flex-wrap: wrap; margin: 1rem 0; }
.header h1 { margin: 0; font-size: 1.4rem; }
.tags { display: flex; gap: 0.4rem; flex-wrap: wrap; }
.tag { padding: 0.2rem 0.5rem; border-radius: 4px; font-size: 0.75rem; font-weight: 600; }
.tag--new { background: #dbeafe; color: #1d4ed8; }
.tag--delisted { background: #f3f4f6; color: #6b7280; }
.tag--bigdrop { background: #fee2e2; color: #dc2626; }
.tag--parking { background: #d1fae5; color: #065f46; }
.info-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 1rem; margin: 1.5rem 0; }
.info-item label { display: block; font-size: 0.75rem; color: #6b7280; margin-bottom: 0.2rem; }
.price { font-size: 1.2rem; font-weight: 700; color: #dc2626; }
.score { font-size: 1.2rem; font-weight: 700; color: #0891b2; }
h2 { font-size: 1.1rem; margin: 1.5rem 0 0.75rem; border-bottom: 1px solid #e5e7eb; padding-bottom: 0.4rem; }
.sources { list-style: none; padding: 0; }
.sources li { margin-bottom: 0.4rem; }
.sources a { color: #3b82f6; }
.muted { color: #9ca3af; font-size: 0.85rem; }
.chart-container { border: 1px solid #e5e7eb; border-radius: 8px; padding: 0.5rem; }
.history-table { width: 100%; border-collapse: collapse; font-size: 0.9rem; }
.history-table th, .history-table td { padding: 0.5rem 0.75rem; text-align: left; border-bottom: 1px solid #e5e7eb; }
.history-table th { background: #f9fafb; font-weight: 600; }
.row--bigdrop td { background: #fff5f5; }
.change--down { color: #dc2626; }
.change--up { color: #059669; }
.change--none { color: #9ca3af; }
.bigdrop-label { background: #fee2e2; color: #dc2626; border-radius: 3px; padding: 0 0.3rem; font-size: 0.75rem; margin-left: 0.4rem; }
.loading, .error { text-align: center; padding: 3rem; color: #6b7280; }
.error { color: #dc2626; }
</style>
