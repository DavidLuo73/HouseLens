<template>
  <div class="analytics-page">
    <div class="page-inner">
      <div class="page-header">
        <h1>區域分析</h1>
        <p class="page-subtitle" v-if="!loading && !error">
          共 {{ districts.length }} 個追蹤區域，{{ activeCount }} 區有效資料
        </p>
      </div>

      <div v-if="loading" class="state-box">
        <div class="loader" />
        <p>載入中…</p>
      </div>
      <div v-else-if="error" class="state-box state-box--error">{{ error }}</div>

      <div v-else class="district-grid">
        <div
          v-for="d in districts"
          :key="d.district"
          class="district-card"
          :class="{ 'district-card--insufficient': d.insufficientData }"
        >
          <div class="card-top">
            <h2 class="district-name">{{ d.district }}</h2>
            <span v-if="d.insufficientData" class="tag tag--muted">資料不足</span>
            <span v-else class="tag tag--count">{{ d.propertyCount }} 筆</span>
          </div>

          <template v-if="!d.insufficientData">
            <div class="stats-row">
              <div class="stat">
                <span class="stat-label">平均單價</span>
                <span class="stat-value stat-value--accent">{{ d.avgUnitPrice.toFixed(1) }} <em>萬/坪</em></span>
              </div>
              <div class="stat">
                <span class="stat-label">總價範圍</span>
                <span class="stat-value">{{ d.minTotalPrice }}–{{ d.maxTotalPrice }} <em>萬</em></span>
              </div>
            </div>

            <div v-if="d.priceBuckets.length" class="chart-block">
              <p class="chart-label">總價分布</p>
              <VChart :option="bucketChartOption(d)" autoresize style="height: 160px" />
            </div>

            <div v-if="d.trend.length" class="chart-block">
              <p class="chart-label">單價趨勢</p>
              <VChart :option="trendChartOption(d)" autoresize style="height: 140px" />
            </div>
          </template>

          <div v-else class="insufficient-msg">此區域物件數量不足，無法計算統計數據</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { api, type DistrictStats } from '@/services/api'

use([BarChart, LineChart, GridComponent, TooltipComponent, CanvasRenderer])

const districts = ref<DistrictStats[]>([])
const loading = ref(true)
const error = ref<string | null>(null)

const activeCount = computed(() => districts.value.filter((d) => !d.insufficientData).length)

onMounted(async () => {
  try {
    const result = await api.analytics.districts()
    districts.value = result.districts
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '載入失敗'
  } finally {
    loading.value = false
  }
})

function bucketChartOption(d: DistrictStats) {
  return {
    tooltip: {
      trigger: 'axis',
      formatter: (params: { name: string; value: number }[]) =>
        `${params[0].name} 萬：${params[0].value} 筆`,
    },
    xAxis: {
      type: 'category',
      data: d.priceBuckets.map((b) => b.range),
      axisLabel: { fontSize: 10, rotate: 20, color: '#717171' },
      axisLine: { lineStyle: { color: '#EBEBEB' } },
    },
    yAxis: {
      type: 'value',
      name: '筆',
      minInterval: 1,
      axisLabel: { fontSize: 10, color: '#717171' },
      splitLine: { lineStyle: { color: '#F7F7F7' } },
    },
    series: [{
      type: 'bar',
      data: d.priceBuckets.map((b) => b.count),
      itemStyle: { color: '#FF385C', borderRadius: [3, 3, 0, 0] },
      barMaxWidth: 32,
    }],
    grid: { left: 36, right: 10, top: 12, bottom: 52 },
  }
}

function trendChartOption(d: DistrictStats) {
  return {
    tooltip: {
      trigger: 'axis',
      formatter: (params: { name: string; value: number }[]) =>
        `${params[0].name}<br/>平均單價：${params[0].value.toFixed(1)} 萬/坪`,
    },
    xAxis: {
      type: 'category',
      data: d.trend.map((t) => t.date),
      axisLabel: { fontSize: 10, color: '#717171' },
      axisLine: { lineStyle: { color: '#EBEBEB' } },
    },
    yAxis: {
      type: 'value',
      name: '萬/坪',
      min: 'dataMin',
      axisLabel: { fontSize: 10, color: '#717171' },
      splitLine: { lineStyle: { color: '#F7F7F7' } },
    },
    series: [{
      type: 'line',
      data: d.trend.map((t) => t.avgUnitPrice),
      smooth: true,
      lineStyle: { color: '#222222', width: 2 },
      itemStyle: { color: '#222222' },
      areaStyle: { color: 'rgba(34,34,34,0.05)' },
      symbol: 'circle',
      symbolSize: 4,
    }],
    grid: { left: 48, right: 10, top: 12, bottom: 28 },
  }
}
</script>

<style scoped>
.analytics-page {
  background: var(--color-bg-soft);
  min-height: 100vh;
}

.page-inner {
  max-width: 1280px;
  margin: 0 auto;
  padding: 32px 24px 64px;
}

@media (max-width: 640px) {
  .page-inner { padding: 20px 16px 48px; }
}

.page-header { margin-bottom: 28px; }

h1 {
  font-size: 1.6rem;
  font-weight: 800;
  color: var(--color-fg);
  letter-spacing: -0.3px;
  margin-bottom: 6px;
}

.page-subtitle {
  font-size: 0.88rem;
  color: var(--color-fg-2);
}

.state-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 80px 24px;
  color: var(--color-fg-2);
}
.state-box--error { color: var(--color-price-down); }
.loader {
  width: 32px; height: 32px;
  border: 3px solid var(--color-border-soft);
  border-top-color: var(--color-rausch);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.district-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
}

@media (max-width: 480px) {
  .district-grid { grid-template-columns: 1fr; }
}

.district-card {
  background: #fff;
  border-radius: var(--radius-card);
  border: 1px solid var(--color-border-soft);
  padding: 20px;
}

.district-card--insufficient {
  opacity: 0.7;
}

.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.district-name {
  font-size: 1rem;
  font-weight: 700;
  color: var(--color-fg);
  margin: 0;
}

.tag {
  padding: 3px 10px;
  border-radius: var(--radius-pill);
  font-size: 0.72rem;
  font-weight: 700;
}

.tag--count {
  background: var(--color-bg-soft);
  color: var(--color-fg-2);
  border: 1px solid var(--color-border);
}

.tag--muted {
  background: var(--color-bg-soft);
  color: var(--color-fg-3);
  border: 1px solid var(--color-border-soft);
}

.stats-row {
  display: flex;
  gap: 24px;
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--color-border-soft);
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

.stat-value--accent {
  font-size: 1.1rem;
  color: var(--color-rausch);
}

.stat-value em {
  font-style: normal;
  font-size: 0.78rem;
  font-weight: 500;
  color: var(--color-fg-2);
}

.chart-block {
  margin-top: 12px;
}

.chart-label {
  font-size: 0.72rem;
  font-weight: 700;
  color: var(--color-fg-2);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  margin: 0 0 6px;
}

.insufficient-msg {
  font-size: 0.85rem;
  color: var(--color-fg-3);
  text-align: center;
  padding: 24px 0;
}
</style>
