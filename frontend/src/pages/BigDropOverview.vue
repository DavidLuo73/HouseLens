<template>
  <div class="big-drop-overview">
    <h1>大降價物件總覽</h1>

    <div v-if="loading" class="loading">載入中…</div>
    <div v-else-if="error" class="error">{{ error }}</div>

    <template v-else>
      <p class="subtitle">
        共 {{ items.length }} 筆大降價物件
        <span v-if="!items.length" class="no-data">（目前無符合降幅門檻之物件）</span>
      </p>

      <div v-if="items.length" class="table-wrap">
        <table class="drop-table">
          <thead>
            <tr>
              <th class="col-title">物件名稱</th>
              <th class="col-district">行政區</th>
              <th class="col-price">原價 (萬)</th>
              <th class="col-price">現價 (萬)</th>
              <th class="col-drop">降幅</th>
              <th class="col-amount">降價 (萬)</th>
              <th class="col-link">連結</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in items" :key="item.id" class="drop-row">
              <td class="col-title">
                <span class="item-title">{{ item.title }}</span>
                <span v-if="item.address" class="item-address">{{ item.address }}</span>
              </td>
              <td>{{ item.district }}</td>
              <td class="price-original">{{ item.originalPrice.toFixed(1) }}</td>
              <td class="price-new">{{ item.newPrice.toFixed(1) }}</td>
              <td class="drop-pct">▼ {{ formatPercent(item.dropPercent) }}</td>
              <td class="drop-amount">{{ item.dropAmount.toFixed(1) }}</td>
              <td>
                <a
                  :href="item.url"
                  target="_blank"
                  rel="noopener noreferrer"
                  class="view-link"
                >查看</a>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api, type BigDropItem } from '@/services/api'

const items = ref<BigDropItem[]>([])
const loading = ref(true)
const error = ref<string | null>(null)

onMounted(async () => {
  try {
    const result = await api.analytics.bigDrop()
    items.value = result.items
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '載入失敗'
  } finally {
    loading.value = false
  }
})

function formatPercent(val: number): string {
  return `${(Math.abs(val) * 100).toFixed(1)}%`
}
</script>

<style scoped>
.big-drop-overview {
  max-width: 1100px;
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
  margin-bottom: 1.25rem;
}
.no-data {
  color: #9ca3af;
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
.table-wrap {
  overflow-x: auto;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
}
.drop-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}
.drop-table th {
  background: #f9fafb;
  padding: 0.65rem 0.9rem;
  text-align: left;
  font-size: 0.8rem;
  color: #6b7280;
  font-weight: 600;
  border-bottom: 1px solid #e5e7eb;
  white-space: nowrap;
}
.drop-row td {
  padding: 0.7rem 0.9rem;
  border-bottom: 1px solid #f3f4f6;
  vertical-align: middle;
}
.drop-row:last-child td {
  border-bottom: none;
}
.drop-row:hover {
  background: #fafafa;
}
.col-title {
  min-width: 200px;
}
.item-title {
  display: block;
  font-weight: 500;
  color: #111827;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 260px;
}
.item-address {
  display: block;
  font-size: 0.78rem;
  color: #9ca3af;
  margin-top: 0.15rem;
}
.col-price,
.col-drop,
.col-amount {
  text-align: right;
  white-space: nowrap;
}
.price-original {
  color: #9ca3af;
  text-decoration: line-through;
}
.price-new {
  color: #111827;
  font-weight: 600;
}
.drop-pct {
  color: #dc2626;
  font-weight: 700;
}
.drop-amount {
  color: #dc2626;
}
.view-link {
  color: #2563eb;
  font-size: 0.82rem;
  text-decoration: none;
  padding: 0.2rem 0.5rem;
  border: 1px solid #bfdbfe;
  border-radius: 4px;
}
.view-link:hover {
  background: #eff6ff;
}
</style>
