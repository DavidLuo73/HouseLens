<template>
  <div class="top-properties">
    <h1>各區優質排行</h1>

    <div v-if="loading" class="loading">載入中…</div>
    <div v-else-if="error" class="error">{{ error }}</div>

    <template v-else>
      <div v-if="!byDistrict.length" class="empty">尚無評分資料，請先執行爬取。</div>

      <div v-for="group in byDistrict" :key="group.district" class="district-group">
        <h2 class="district-name">{{ group.district }}</h2>

        <div v-if="!group.items.length" class="no-data">目前無評分物件</div>

        <div v-else class="items-list">
          <div v-for="(item, i) in group.items" :key="item.id" class="rank-item">
            <div class="rank-badge" :class="rankClass(i)">
              {{ i + 1 }}
            </div>
            <div class="item-info">
              <div class="item-title">{{ item.title }}</div>
              <div class="item-meta">
                <span class="price">{{ item.totalPrice.toFixed(0) }} 萬</span>
                <span v-if="item.unitPrice" class="unit-price">
                  ・{{ item.unitPrice.toFixed(1) }} 萬/坪
                </span>
                <span v-if="item.ageYears != null" class="age">
                  ・{{ item.ageYears }} 年
                </span>
                <span v-if="item.hasParking" class="parking-tag">P</span>
              </div>
            </div>
            <div class="score-badge">
              <span class="score-value">{{ Math.round(item.score * 100) }}</span>
              <span class="score-label">分</span>
            </div>
          </div>
        </div>
      </div>
    </template>
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
.top-properties {
  max-width: 1100px;
  margin: 0 auto;
  padding: 1.5rem;
}
h1 {
  font-size: 1.6rem;
  margin-bottom: 1.5rem;
}
.loading,
.error,
.empty {
  text-align: center;
  padding: 3rem;
  color: #6b7280;
}
.error {
  color: #dc2626;
}
.district-group {
  margin-bottom: 2rem;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 1.25rem;
}
.district-name {
  font-size: 1.1rem;
  font-weight: 700;
  color: #111827;
  margin: 0 0 1rem;
  padding-bottom: 0.6rem;
  border-bottom: 2px solid #f3f4f6;
}
.no-data {
  color: #9ca3af;
  font-size: 0.88rem;
  padding: 0.5rem 0;
}
.items-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
.rank-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.6rem 0.5rem;
  border-radius: 8px;
  transition: background 0.1s;
}
.rank-item:hover {
  background: #f9fafb;
}
.rank-badge {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #e5e7eb;
  color: #6b7280;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.82rem;
  font-weight: 700;
  flex-shrink: 0;
}
.rank-badge--gold {
  background: #fef3c7;
  color: #d97706;
}
.rank-badge--silver {
  background: #f3f4f6;
  color: #4b5563;
}
.rank-badge--bronze {
  background: #fef0e5;
  color: #b45309;
}
.item-info {
  flex: 1;
  min-width: 0;
}
.item-title {
  font-size: 0.9rem;
  font-weight: 500;
  color: #111827;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.item-meta {
  font-size: 0.8rem;
  color: #6b7280;
  margin-top: 0.15rem;
}
.price {
  color: #111827;
  font-weight: 600;
}
.parking-tag {
  display: inline-block;
  margin-left: 0.35rem;
  padding: 0.05rem 0.35rem;
  background: #dbeafe;
  color: #1d4ed8;
  border-radius: 4px;
  font-size: 0.72rem;
  font-weight: 700;
}
.score-badge {
  flex-shrink: 0;
  text-align: right;
}
.score-value {
  font-size: 1.2rem;
  font-weight: 700;
  color: #059669;
}
.score-label {
  font-size: 0.72rem;
  color: #6b7280;
  margin-left: 1px;
}
</style>
