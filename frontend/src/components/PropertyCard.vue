<template>
  <article class="property-card" :class="{ 'property-card--delisted': property.status === 'delisted' }">
    <!-- 圖片區 -->
    <div class="card-image-wrap">
      <a
        v-if="property.imageUrl && !imgError"
        :href="property.listingUrl"
        target="_blank"
        rel="noopener noreferrer"
        class="card-image-link"
        tabindex="-1"
      >
        <video
          v-if="isVideoUrl(property.imageUrl)"
          :src="property.imageUrl"
          class="card-image"
          muted
          autoplay
          loop
          playsinline
          @error="imgError = true"
        />
        <img
          v-else
          :src="property.imageUrl"
          :alt="property.title"
          class="card-image"
          loading="lazy"
          @error="imgError = true"
        />
      </a>
      <div v-else class="card-image-placeholder">
        <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="#d1d5db" stroke-width="1.5">
          <rect x="3" y="3" width="18" height="18" rx="2"/>
          <circle cx="8.5" cy="8.5" r="1.5"/>
          <polyline points="21 15 16 10 5 21"/>
        </svg>
      </div>

      <!-- 徽章 overlay（左上） -->
      <div class="badge-overlay">
        <span v-if="property.isNew" class="badge badge--new">新上架</span>
        <span v-if="property.latestIsBigDrop" class="badge badge--bigdrop">大降價</span>
        <span v-if="property.hasParking" class="badge badge--parking">有車位</span>
        <span v-if="property.status === 'delisted'" class="badge badge--delisted">已下架</span>
      </div>

      <!-- 收藏愛心（右上） -->
      <button
        class="heart-btn"
        :class="{ 'heart-btn--saved': isSaved }"
        :aria-label="isSaved ? '取消收藏' : '加入收藏'"
        @click.prevent="toggleSave"
      >
        <svg width="20" height="20" viewBox="0 0 24 24" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
          :fill="isSaved ? '#FF385C' : 'none'"
          :stroke="isSaved ? '#FF385C' : 'white'"
        >
          <path d="M20.84 4.61a5.5 5.5 0 00-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 00-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 000-7.78z"/>
        </svg>
      </button>
    </div>

    <!-- 卡片內文 -->
    <div class="card-body">
      <!-- 標題列：地區 + 評分 -->
      <div class="card-top">
        <span class="card-district">{{ property.district }}</span>
        <span v-if="property.score != null" class="card-score">
          <svg width="11" height="11" viewBox="0 0 24 24" fill="#FF385C" stroke="none">
            <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
          </svg>
          {{ (property.score * 5).toFixed(1) }}
        </span>
      </div>

      <!-- 標題 -->
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

      <!-- 物件資訊 -->
      <div class="card-meta">
        <span>{{ property.areaPing }} 坪</span>
        <span v-if="property.floor">{{ property.floor }} 樓</span>
        <span v-if="property.ageYears != null">屋齡 {{ property.ageYears }} 年</span>
      </div>

      <!-- 分隔線 -->
      <div class="card-divider" />

      <!-- 價格列 -->
      <div class="card-price-row">
        <div class="price-main">
          <span class="price-total">{{ property.currentTotalPrice }}<em>萬</em></span>
          <span v-if="property.currentUnitPrice" class="price-unit">
            {{ property.currentUnitPrice.toFixed(1) }} 萬/坪
          </span>
        </div>
        <span
          v-if="property.latestChangeFlag === 'decreased'"
          class="price-change price-change--down"
        >▼ {{ formatPercent(property.latestChangePercent) }}</span>
        <span
          v-else-if="property.latestChangeFlag === 'increased'"
          class="price-change price-change--up"
        >▲ {{ formatPercent(property.latestChangePercent) }}</span>
      </div>

      <!-- 趨勢圖列 -->
      <div class="card-trend">
        <VChart v-if="hasTrend" :option="sparkOption" autoresize class="card-spark" />
        <span v-else class="card-spark-empty" />
        <button type="button" class="spark-btn" @click="emit('open-history', property)">
          價格歷史
        </button>
      </div>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { Property } from '@/services/api'

use([LineChart, GridComponent, CanvasRenderer])

const props = defineProps<{ property: Property }>()
const emit = defineEmits<{ 'open-history': [property: Property] }>()

const imgError = ref(false)

const SAVE_KEY = 'hl_saved_properties'

const isSaved = ref(getSaved().has(String(props.property.id)))

function getSaved(): Set<string> {
  try {
    const raw = localStorage.getItem(SAVE_KEY)
    return new Set(raw ? JSON.parse(raw) : [])
  } catch {
    return new Set()
  }
}

function toggleSave() {
  const saved = getSaved()
  const id = String(props.property.id)
  if (saved.has(id)) {
    saved.delete(id)
    isSaved.value = false
  } else {
    saved.add(id)
    isSaved.value = true
  }
  localStorage.setItem(SAVE_KEY, JSON.stringify([...saved]))
}

function isVideoUrl(url?: string | null): boolean {
  if (!url) return false
  const lower = url.toLowerCase()
  return lower.endsWith('.mp4') || lower.endsWith('.webm') || lower.endsWith('.m3u8') || lower.endsWith('.mov')
}

const hasTrend = computed(() => props.property.priceHistory.length >= 2)

const sparkOption = computed(() => {
  const ascending = [...props.property.priceHistory].reverse()
  return {
    tooltip: { show: false },
    grid: { left: 0, right: 0, top: 2, bottom: 2 },
    xAxis: { type: 'category', show: false, data: ascending.map((_, i) => i) },
    yAxis: { type: 'value', show: false, scale: true },
    series: [
      {
        type: 'line',
        data: ascending.map((h) => h.totalPrice),
        showSymbol: false,
        smooth: true,
        lineStyle: { width: 1.5, color: '#FF385C' },
        areaStyle: { color: 'rgba(255, 56, 92, 0.08)' },
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
  background: #fff;
  border-radius: var(--radius-card);
  overflow: hidden;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  cursor: pointer;
}

.property-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-card-hover);
}

.property-card--delisted {
  opacity: 0.6;
}

/* ===== 圖片區 ===== */
.card-image-wrap {
  position: relative;
  border-radius: var(--radius-card);
  overflow: hidden;
  aspect-ratio: 4 / 3;
  background: var(--color-bg-soft);
}

.card-image-link {
  display: block;
  width: 100%;
  height: 100%;
}

.card-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform 0.3s ease;
}

.property-card:hover .card-image {
  transform: scale(1.03);
}

.card-image-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f3f4f6;
}

/* ===== 徽章 overlay ===== */
.badge-overlay {
  position: absolute;
  top: 10px;
  left: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.badge {
  padding: 3px 8px;
  border-radius: var(--radius-pill);
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.badge--new {
  background: var(--color-badge-new-bg);
  color: var(--color-badge-new-text);
}

.badge--bigdrop {
  background: var(--color-badge-drop-bg);
  color: var(--color-badge-drop-text);
}

.badge--parking {
  background: var(--color-badge-parking-bg);
  color: var(--color-badge-parking-text);
}

.badge--delisted {
  background: var(--color-badge-delisted-bg);
  color: var(--color-badge-delisted-text);
}

/* ===== 愛心按鈕 ===== */
.heart-btn {
  position: absolute;
  top: 10px;
  right: 10px;
  background: none;
  border: none;
  padding: 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  filter: drop-shadow(0 1px 2px rgba(0,0,0,0.3));
  transition: transform 0.15s;
}

.heart-btn:hover {
  transform: scale(1.15);
}

.heart-btn--saved svg {
  filter: drop-shadow(0 1px 3px rgba(255, 56, 92, 0.4));
}

/* ===== 卡片內文 ===== */
.card-body {
  padding: 12px 4px 4px;
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.card-district {
  font-size: 0.82rem;
  color: var(--color-fg-2);
  font-weight: 500;
}

.card-score {
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--color-fg);
}

.card-title {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--color-fg);
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.4;
}

.title-link {
  color: inherit;
  text-decoration: none;
}

.title-link:hover {
  text-decoration: underline;
}

.card-meta {
  display: flex;
  gap: 8px;
  font-size: 0.78rem;
  color: var(--color-fg-2);
  flex-wrap: wrap;
}

.card-divider {
  height: 1px;
  background: var(--color-border-soft);
  margin: 10px 0;
}

/* ===== 價格列 ===== */
.card-price-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 10px;
}

.price-main {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.price-total {
  font-size: 1.25rem;
  font-weight: 800;
  color: var(--color-fg);
  letter-spacing: -0.3px;
}

.price-total em {
  font-style: normal;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--color-fg-2);
  margin-left: 2px;
}

.price-unit {
  font-size: 0.78rem;
  color: var(--color-fg-2);
}

.price-change {
  font-size: 0.78rem;
  font-weight: 700;
}

.price-change--down {
  color: var(--color-price-down);
}

.price-change--up {
  color: var(--color-price-up);
}

/* ===== 趨勢圖列 ===== */
.card-trend {
  display: flex;
  align-items: center;
  gap: 8px;
}

.card-spark {
  flex: 1;
  height: 32px;
}

.card-spark-empty {
  flex: 1;
}

.spark-btn {
  flex-shrink: 0;
  padding: 5px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  background: #fff;
  color: var(--color-fg);
  font-size: 0.75rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}

.spark-btn:hover {
  background: var(--color-fg);
  border-color: var(--color-fg);
  color: #fff;
}
</style>
