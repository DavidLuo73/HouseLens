<template>
  <div class="top-page">
    <div class="page-inner">
      <div class="page-header">
        <h1>各區優質排行</h1>
        <p class="page-subtitle">依綜合評分排列，每區前 5 名</p>
      </div>

      <div v-if="loading" class="state-box">
        <div class="loader" />
        <p>載入中…</p>
      </div>
      <div v-else-if="error" class="state-box state-box--error">{{ error }}</div>

      <template v-else>
        <div v-if="!byDistrict.length" class="state-box">
          <p>尚無評分資料，請先執行爬取。</p>
        </div>

        <div v-else class="groups-grid">
          <div v-for="group in byDistrict" :key="group.district" class="group-card">
            <h2 class="group-title">{{ group.district }}</h2>

            <div v-if="!group.items.length" class="no-data">目前無評分物件</div>

            <ul v-else class="rank-list">
              <li v-for="(item, i) in group.items" :key="item.id" class="rank-item">
                <div class="rank-badge" :class="rankClass(i)">{{ i + 1 }}</div>

                <div class="rank-info">
                  <div class="rank-title">{{ item.title }}</div>
                  <div class="rank-meta">
                    <span class="rank-price">{{ item.totalPrice.toFixed(0) }} 萬</span>
                    <span v-if="item.unitPrice">· {{ item.unitPrice.toFixed(1) }} 萬/坪</span>
                    <span v-if="item.ageYears != null">· {{ item.ageYears }} 年</span>
                    <span v-if="item.hasParking" class="parking-chip">P</span>
                  </div>
                </div>

                <div class="rank-score">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="#FF385C" stroke="none">
                    <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
                  </svg>
                  <span class="score-num">{{ (item.score * 5).toFixed(1) }}</span>
                </div>
              </li>
            </ul>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api, type TopPropertyItem } from '@/services/api'

interface DistrictGroup {
  district: string
  items: TopPropertyItem[]
}

const byDistrict = ref<DistrictGroup[]>([])
const loading = ref(true)
const error = ref<string | null>(null)

function rankClass(index: number): string {
  if (index === 0) return 'rank-badge--gold'
  if (index === 1) return 'rank-badge--silver'
  if (index === 2) return 'rank-badge--bronze'
  return ''
}

onMounted(async () => {
  try {
    const result = await api.analytics.topRated()
    byDistrict.value = result.byDistrict
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '載入失敗'
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.top-page {
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

.groups-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
}

@media (max-width: 480px) {
  .groups-grid { grid-template-columns: 1fr; }
}

.group-card {
  background: #fff;
  border-radius: var(--radius-card);
  border: 1px solid var(--color-border-soft);
  padding: 20px;
}

.group-title {
  font-size: 1rem;
  font-weight: 700;
  color: var(--color-fg);
  margin: 0 0 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--color-border-soft);
}

.no-data {
  font-size: 0.85rem;
  color: var(--color-fg-3);
  padding: 12px 0;
}

.rank-list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.rank-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 8px;
  border-radius: var(--radius-input);
  transition: background 0.1s;
}

.rank-item:hover {
  background: var(--color-bg-soft);
}

.rank-badge {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.8rem;
  font-weight: 800;
  flex-shrink: 0;
  background: var(--color-bg-soft);
  color: var(--color-fg-2);
  border: 1px solid var(--color-border);
}

.rank-badge--gold {
  background: #FEF3C7;
  color: #D97706;
  border-color: #FDE68A;
}

.rank-badge--silver {
  background: #F3F4F6;
  color: #374151;
  border-color: #E5E7EB;
}

.rank-badge--bronze {
  background: #FEF0E5;
  color: #B45309;
  border-color: #FDE4C8;
}

.rank-info {
  flex: 1;
  min-width: 0;
}

.rank-title {
  font-size: 0.88rem;
  font-weight: 600;
  color: var(--color-fg);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.rank-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 0.78rem;
  color: var(--color-fg-2);
  margin-top: 2px;
  flex-wrap: wrap;
}

.rank-price {
  font-weight: 600;
  color: var(--color-fg);
}

.parking-chip {
  padding: 1px 6px;
  background: var(--color-badge-parking-bg);
  color: var(--color-badge-parking-text);
  border-radius: var(--radius-pill);
  font-size: 0.7rem;
  font-weight: 700;
}

.rank-score {
  display: flex;
  align-items: center;
  gap: 3px;
  flex-shrink: 0;
}

.score-num {
  font-size: 0.88rem;
  font-weight: 700;
  color: var(--color-fg);
}
</style>
