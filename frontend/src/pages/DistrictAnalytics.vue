<template>
  <div class="district-analytics">
    <h1>區域分析</h1>

    <div v-if="loading" class="loading">載入中…</div>
    <div v-else-if="error" class="error">{{ error }}</div>

    <template v-else>
      <p class="subtitle">共 {{ districts.length }} 個追蹤區域，有效資料 {{ activeCount }} 區</p>

      <div class="district-grid">
        <div
          v-for="d in districts"
          :key="d.district"
          class="district-card"
          :class="{ 'district-card--insufficient': d.insufficientData }"
        >
          <div class="card-header">
            <h2 class="district-name">{{ d.district }}</h2>
            <span v-if="d.insufficientData" class="badge badge--insufficient">資料不足</span>
            <span v-else class="badge badge--ok">{{ d.propertyCount }} 筆</span>
          </div>

          <div v-if="!d.insufficientData" class="stats-row">
            <div class="stat">
              <span class="stat-label">平均單價</span>
              <span class="stat-value stat-value--primary">{{ d.avgUnitPrice.toFixed(1) }} 萬/坪</span>
            </div>
            <div class="stat">
              <span class="stat-label">總價範圍</span>
              <span class="stat-value">{{ d.minTotalPrice }}–{{ d.maxTotalPrice }} 萬</span>
            </div>
          </div>

          <div v-if="!d.insufficientData && d.priceBuckets.length" class="chart-section">
            <p class="chart-label">總價分布</p>
            <div class="chart-container">
              <VChart :option="bucketChartOption(d)" autoresize style="height: 180px" />
            </div>
          </div>

          <div v-if="!d.insufficientData && d.trend.length" class="chart-section">
            <p class="chart-label">單價趨勢</p>
            <div class="chart-container">
              <VChart :option="trendChartOption(d)" autoresize style="height: 160px" />
            </div>
          </div>

          <div v-if="d.insufficientData" class="insufficient-msg">
            此區域物件數量不足，無法計算統計數據
          </div>
        </div>
      </div>
    </template>
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
      axisLabel: { fontSize: 10, rotate: 20 },
    },
    yAxis: { type: 'value', name: '筆', minInterval: 1 },
    series: [
      {
        type: 'bar',
        data: d.priceBuckets.map((b) => b.count),
        itemStyle: { color: '#3b82f6' },
      },
    ],
    grid: { left: 40, right: 10, top: 10, bottom: 50 },
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
      axisLabel: { fontSize: 10 },
    },
    yAxis: { type: 'value', name: '萬/坪', min: 'dataMin' },
    series: [
      {
        type: 'line',
        data: d.trend.map((t) => t.avgUnitPrice),
        smooth: true,
        itemStyle: { color: '#0891b2' },
        lineStyle: { width: 2 },
        symbol: 'circle',
        symbolSize: 5,
      },
    ],
    grid: { left: 50, right: 10, top: 10, bottom: 30 },
  }
}
</script>

<style scoped>
.district-analytics {
  max-width: 1200px;
  margin: 0 auto;
  padding: 1.5rem;
}
h1 {
  font-size: 1.6rem;
  margin-bottom: 0.25rem;
}
.subtitle {
  color: #6b7280;
  font-size: 0.9rem;
  margin-bottom: 1.5rem;
}
.district-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 1.25rem;
}
.district-card {
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 1rem 1.25rem;
  background: #fff;
}
.district-card--insufficient {
  background: #f9fafb;
  opacity: 0.8;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.75rem;
}
.district-name {
  font-size: 1.05rem;
  font-weight: 700;
  margin: 0;
}
.badge {
  padding: 0.15rem 0.5rem;
  border-radius: 12px;
  font-size: 0.75rem;
  font-weight: 600;
}
.badge--ok {
  background: #dbeafe;
  color: #1d4ed8;
}
.badge--insufficient {
  background: #f3f4f6;
  color: #6b7280;
}
.stats-row {
  display: flex;
  gap: 1.5rem;
  margin-bottom: 0.75rem;
}
.stat {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
}
.stat-label {
  font-size: 0.75rem;
  color: #6b7280;
}
.stat-value {
  font-size: 0.95rem;
  font-weight: 600;
}
.stat-value--primary {
  color: #0891b2;
  font-size: 1.05rem;
}
.chart-section {
  margin-top: 0.75rem;
}
.chart-label {
  font-size: 0.78rem;
  color: #6b7280;
  margin: 0 0 0.25rem;
}
.chart-container {
  border: 1px solid #f3f4f6;
  border-radius: 6px;
  background: #fafafa;
}
.insufficient-msg {
  color: #9ca3af;
  font-size: 0.85rem;
  text-align: center;
  padding: 1.5rem 0;
}
.loading,
.error {
  text-align: center;
  padding: 3rem;
  color: #6b7280;
}
.error {
  color: #dc2626;
}
</style>
