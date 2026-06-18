<template>
  <div class="property-list">
    <FilterBar :available-districts="TRACKED_DISTRICTS" />

    <div v-if="loading" class="loading">載入中…</div>
    <div v-else-if="error" class="error">{{ error }}</div>

    <template v-else>
      <div v-if="!items.length" class="empty">
        <p>目前無符合條件的物件</p>
      </div>

      <div v-else class="grid">
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
        <span class="page-info">第 {{ filters.page }} / {{ totalPages }} 頁</span>
        <button
          class="page-btn"
          :disabled="filters.page >= totalPages"
          @click="filters.setPage(filters.page + 1)"
        >
          下一頁 →
        </button>
      </div>

      <p class="total-count">共 {{ total }} 筆{{ statusLabel }}物件</p>
    </template>

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
function openHistory(p: Property) {
  historyTarget.value = p
}
function closeHistory() {
  historyTarget.value = null
}

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / filters.pageSize)))

const statusLabel = computed(() =>
  filters.status === 'delisted' ? '（已下架）' : ''
)

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
    filters.districts,
    filters.sourceSites,
    filters.minPrice,
    filters.maxPrice,
    filters.hasParking,
    filters.priceDropped,
    filters.status,
    filters.sortBy,
    filters.page,
  ],
  () => fetchProperties(),
  { deep: true }
)

onMounted(() => fetchProperties())
</script>

<style scoped>
.property-list {
  max-width: 1200px;
  margin: 0 auto;
  padding: 1.5rem;
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
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 1rem;
}
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  margin-top: 2rem;
}
.page-btn {
  padding: 0.4rem 1.1rem;
  border: 1.5px solid #a5b4fc;
  border-radius: 20px;
  background: #eef2ff;
  color: #3730a3;
  font-size: 0.85rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}
.page-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}
.page-btn:not(:disabled):hover {
  background: #4f46e5;
  border-color: #4f46e5;
  color: #fff;
}
.page-info {
  font-size: 0.9rem;
  color: #6b7280;
}
.total-count {
  text-align: center;
  margin-top: 1rem;
  font-size: 0.85rem;
  color: #9ca3af;
}
</style>
