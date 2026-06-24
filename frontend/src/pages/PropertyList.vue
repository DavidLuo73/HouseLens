<template>
  <div class="property-list-page">
    <div class="page-inner">
      <FilterBar :available-districts="TRACKED_DISTRICTS" />

      <div v-if="loading" class="state-box">
        <div class="loader" />
        <p>載入中…</p>
      </div>

      <div v-else-if="error" class="state-box state-box--error">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
        </svg>
        <p>{{ error }}</p>
      </div>

      <template v-else>
        <div v-if="!items.length" class="state-box">
          <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#d1d5db" stroke-width="1.5">
            <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
          </svg>
          <p class="empty-title">找不到符合條件的物件</p>
          <p class="empty-sub">試著調整篩選條件，或展開搜尋地區</p>
        </div>

        <template v-else>
          <p class="result-count">共 {{ total }} 筆{{ statusLabel }}物件</p>

          <div class="properties-grid">
            <PropertyCard
              v-for="p in items"
              :key="p.id"
              :property="p"
              @open-history="openHistory"
            />
          </div>

          <div v-if="totalPages > 1" class="pagination">
            <button
              class="page-btn"
              :disabled="filters.page <= 1"
              @click="filters.setPage(filters.page - 1)"
            >
              ← 上一頁
            </button>
            <div class="page-dots">
              <button
                v-for="p in pageRange"
                :key="p"
                class="page-dot"
                :class="{ 'page-dot--active': p === filters.page }"
                @click="p !== '…' && filters.setPage(Number(p))"
              >{{ p }}</button>
            </div>
            <button
              class="page-btn"
              :disabled="filters.page >= totalPages"
              @click="filters.setPage(filters.page + 1)"
            >
              下一頁 →
            </button>
          </div>
        </template>
      </template>
    </div>

    <PriceHistoryModal
      v-if="historyTarget"
      :property="historyTarget"
      @close="closeHistory"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { api, type Property } from '@/services/api'
import { useFiltersStore, TRACKED_DISTRICTS } from '@/stores/filters'
import FilterBar from '@/components/FilterBar.vue'
import PropertyCard from '@/components/PropertyCard.vue'
import PriceHistoryModal from '@/components/PriceHistoryModal.vue'

const filters = useFiltersStore()

const items = ref<Property[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref<string | null>(null)

const historyTarget = ref<Property | null>(null)
function openHistory(p: Property) { historyTarget.value = p }
function closeHistory() { historyTarget.value = null }

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / filters.pageSize)))

const statusLabel = computed(() => filters.status === 'delisted' ? '（已下架）' : '')

const pageRange = computed(() => {
  const total = totalPages.value
  const cur = filters.page
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1)
  const pages: (number | '…')[] = [1]
  if (cur > 3) pages.push('…')
  for (let i = Math.max(2, cur - 1); i <= Math.min(total - 1, cur + 1); i++) pages.push(i)
  if (cur < total - 2) pages.push('…')
  pages.push(total)
  return pages
})

async function fetchProperties() {
  loading.value = true
  error.value = null
  try {
    const params: Record<string, unknown> = {
      sortBy: filters.sortBy,
      page: filters.page,
      pageSize: filters.pageSize,
      status: filters.status,
    }
    if (filters.districts.length) params.district = filters.districts
    if (filters.sourceSites.length) params.sourceSite = filters.sourceSites
    if (filters.minPrice !== null) params.minPrice = filters.minPrice
    if (filters.maxPrice !== null) params.maxPrice = filters.maxPrice
    if (filters.hasParking !== null) params.hasParking = filters.hasParking
    if (filters.priceDropped !== null) params.priceDropped = filters.priceDropped

    const result = await api.properties.list(params)
    items.value = result.items
    total.value = result.total
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '載入失敗'
  } finally {
    loading.value = false
  }
}

watch(
  () => [
    filters.districts, filters.sourceSites, filters.minPrice, filters.maxPrice,
    filters.hasParking, filters.priceDropped, filters.status, filters.sortBy, filters.page,
  ],
  () => fetchProperties(),
  { deep: true }
)

onMounted(() => fetchProperties())
</script>

<style scoped>
.property-list-page {
  background: var(--color-bg-soft);
  min-height: 100vh;
}

.page-inner {
  max-width: 1280px;
  margin: 0 auto;
  padding: 32px 24px 64px;
}

@media (max-width: 640px) {
  .page-inner {
    padding: 20px 16px 48px;
  }
}

/* ===== 狀態盒 ===== */
.state-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 80px 24px;
  text-align: center;
  color: var(--color-fg-2);
}

.state-box--error {
  color: var(--color-price-down);
}

.empty-title {
  font-size: 1.1rem;
  font-weight: 700;
  color: var(--color-fg);
}

.empty-sub {
  font-size: 0.88rem;
  color: var(--color-fg-2);
}

/* ===== 載入動畫 ===== */
.loader {
  width: 32px;
  height: 32px;
  border: 3px solid var(--color-border-soft);
  border-top-color: var(--color-rausch);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ===== 結果計數 ===== */
.result-count {
  font-size: 0.85rem;
  color: var(--color-fg-2);
  margin-bottom: 16px;
}

/* ===== 物件網格 ===== */
.properties-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 24px;
}

@media (max-width: 480px) {
  .properties-grid {
    grid-template-columns: 1fr;
    gap: 16px;
  }
}

/* ===== 分頁 ===== */
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 48px;
}

.page-btn {
  padding: 10px 20px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  background: #fff;
  color: var(--color-fg);
  font-size: 0.85rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}

.page-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.page-btn:not(:disabled):hover {
  background: var(--color-fg);
  border-color: var(--color-fg);
  color: #fff;
}

.page-dots {
  display: flex;
  gap: 4px;
}

.page-dot {
  min-width: 36px;
  height: 36px;
  padding: 0 8px;
  border-radius: var(--radius-pill);
  border: 1px solid transparent;
  background: transparent;
  color: var(--color-fg);
  font-size: 0.85rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}

.page-dot:hover:not(.page-dot--active) {
  background: var(--color-bg-soft);
  border-color: var(--color-border);
}

.page-dot--active {
  background: var(--color-fg);
  color: #fff;
  font-weight: 700;
}
</style>
