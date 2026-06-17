<template>
  <div class="property-card" :class="{ 'property-card--delisted': property.status === 'delisted' }">
    <!-- 圖片縮圖 -->
    <a
      v-if="property.imageUrl"
      :href="property.listingUrl"
      target="_blank"
      rel="noopener noreferrer"
      class="card-image-link"
    >
      <img
        :src="property.imageUrl"
        :alt="property.title"
        class="card-image"
        loading="lazy"
        @error="(e) => ((e.target as HTMLImageElement).style.display = 'none')"
      />
    </a>

    <div class="card-body">
      <div class="card-header">
        <div class="card-badges">
          <span v-if="property.isNew" class="badge badge--new">新上架</span>
          <span v-if="property.latestIsBigDrop" class="badge badge--bigdrop">大降價</span>
          <span v-if="property.hasParking" class="badge badge--parking">有車位</span>
          <span v-if="property.status === 'delisted'" class="badge badge--delisted">已下架</span>
        </div>
        <span v-if="property.score != null" class="card-score">
          評分 {{ (property.score * 100).toFixed(0) }}
        </span>
      </div>

      <h3 class="card-title">
        <a
          v-if="property.listingUrl"
          :href="property.listingUrl"
          target="_blank"
          rel="noopener noreferrer"
          class="title-link"
        >{{ property.title }}</a>
        <span v-else>{{ property.title }}</span>
      </h3>

      <div class="card-meta">
        <span class="meta-district">{{ property.district }}</span>
        <span>{{ property.areaPing }} 坪</span>
        <span v-if="property.floor">{{ property.floor }} 樓</span>
        <span v-if="property.ageYears != null">屋齡 {{ property.ageYears }} 年</span>
      </div>

      <div class="card-price">
        <span class="price-total">{{ property.currentTotalPrice }} 萬</span>
        <span v-if="property.currentUnitPrice" class="price-unit">
          {{ property.currentUnitPrice.toFixed(1) }} 萬/坪
        </span>
        <span
          v-if="property.latestChangeFlag === 'decreased'"
          class="price-change price-change--down"
        >
          ▼ {{ formatPercent(property.latestChangePercent) }}
        </span>
        <span
          v-else-if="property.latestChangeFlag === 'increased'"
          class="price-change price-change--up"
        >
          ▲ {{ formatPercent(property.latestChangePercent) }}
        </span>
      </div>

      <div class="card-trend">
        <VChart v-if="hasTrend" :option="sparkOption" autoresize class="card-spark" />
        <span v-else class="card-spark-empty">—</span>
        <button type="button" class="spark-btn" @click="emit('open-history', property)">看明細</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
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
    tooltip: { show: false },
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
</script>

<style scoped>
.property-card {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
  transition: box-shadow 0.15s;
}
.property-card:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}
.property-card--delisted {
  opacity: 0.6;
  background: #f9fafb;
}
.card-image-link {
  display: block;
}
.card-image {
  width: 100%;
  height: 160px;
  object-fit: cover;
  display: block;
}
.card-body {
  padding: 1rem;
}
.title-link {
  color: inherit;
  text-decoration: none;
}
.title-link:hover {
  text-decoration: underline;
  color: #1d4ed8;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 0.4rem;
}
.card-badges {
  display: flex;
  gap: 0.3rem;
  flex-wrap: wrap;
}
.badge {
  padding: 0.15rem 0.45rem;
  border-radius: 4px;
  font-size: 0.7rem;
  font-weight: 600;
}
.badge--new {
  background: #dbeafe;
  color: #1d4ed8;
}
.badge--bigdrop {
  background: #fee2e2;
  color: #dc2626;
}
.badge--parking {
  background: #d1fae5;
  color: #065f46;
}
.badge--delisted {
  background: #f3f4f6;
  color: #6b7280;
}
.card-score {
  font-size: 0.8rem;
  color: #0891b2;
  font-weight: 600;
  white-space: nowrap;
}
.card-title {
  font-size: 0.95rem;
  font-weight: 600;
  margin: 0 0 0.4rem;
  color: #111827;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-meta {
  display: flex;
  gap: 0.6rem;
  font-size: 0.8rem;
  color: #6b7280;
  margin-bottom: 0.6rem;
  flex-wrap: wrap;
}
.meta-district {
  color: #374151;
  font-weight: 500;
}
.card-price {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
  flex-wrap: wrap;
}
.price-total {
  font-size: 1.1rem;
  font-weight: 700;
  color: #dc2626;
}
.price-unit {
  font-size: 0.85rem;
  color: #6b7280;
}
.price-change {
  font-size: 0.8rem;
  font-weight: 600;
}
.price-change--down {
  color: #dc2626;
}
.price-change--up {
  color: #059669;
}
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
</style>
