<template>
  <div class="property-list">
    <div class="page-header">
      <h1>物件清單</h1>
      <p class="subtitle">共 {{ total }} 筆{{ statusLabel }}物件</p>
    </div>

    <FilterBar :available-districts="TRACKED_DISTRICTS" />

    <div v-if="loading" class="loading">載入中…</div>
    <div v-else-if="error" class="error">{{ error }}</div>

    <template v-else>
      <div v-if="!items.length" class="empty">
        <p>目前無符合條件的物件</p>
      </div>

      <div v-else class="grid">
        <PropertyCard v-for="p in items" :key="p.id" :property="p" />
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
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { api, type Property } from '@/services/api'
import { useFiltersStore, TRACKED_DISTRICTS } from '@/stores/filters'
import FilterBar from '@/components/FilterBar.vue'
import PropertyCard from '@/components/PropertyCard.vue'

const filters = useFiltersStore()

const items = ref<Property[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref<string | null>(null)

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
.page-header {
  margin-bottom: 1rem;
}
h1 {
  font-size: 1.6rem;
  margin-bottom: 0.25rem;
}
.subtitle {
  color: #6b7280;
  font-size: 0.9rem;
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
  padding: 0.4rem 1rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  font-size: 0.9rem;
}
.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.page-btn:not(:disabled):hover {
  background: #f3f4f6;
}
.page-info {
  font-size: 0.9rem;
  color: #6b7280;
}
</style>
