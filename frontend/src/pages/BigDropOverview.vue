<template>
  <div class="bigdrop-page">
    <div class="page-inner">
      <div class="page-header">
        <h1>大降價物件總覽</h1>
        <p class="page-subtitle" v-if="!loading && !error">共 {{ items.length }} 筆大降價物件</p>
      </div>

      <div v-if="loading" class="state-box">
        <div class="loader" />
        <p>載入中…</p>
      </div>
      <div v-else-if="error" class="state-box state-box--error">{{ error }}</div>

      <template v-else>
        <div v-if="!items.length" class="state-box">
          <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#d1d5db" stroke-width="1.5">
            <polyline points="23 6 13.5 15.5 8.5 10.5 1 18"/>
          </svg>
          <p>目前無符合降幅門檻之物件</p>
        </div>

        <div v-else class="table-card">
          <div class="table-scroll">
            <table class="drop-table">
              <thead>
                <tr>
                  <th class="col-title">物件名稱</th>
                  <th class="col-district">行政區</th>
                  <th class="col-price">原價（萬）</th>
                  <th class="col-price">現價（萬）</th>
                  <th class="col-drop">降幅</th>
                  <th class="col-amount">降價（萬）</th>
                  <th class="col-link">連結</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in items" :key="item.id" class="drop-row">
                  <td class="col-title">
                    <span class="item-title">{{ item.title }}</span>
                    <span v-if="item.address" class="item-addr">{{ item.address }}</span>
                  </td>
                  <td class="td-district">{{ item.district }}</td>
                  <td class="td-price td-original">{{ item.originalPrice.toFixed(1) }}</td>
                  <td class="td-price td-new">{{ item.newPrice.toFixed(1) }}</td>
                  <td class="td-drop">▼ {{ formatPercent(item.dropPercent) }}</td>
                  <td class="td-amount">{{ item.dropAmount.toFixed(1) }}</td>
                  <td>
                    <a :href="item.url" target="_blank" rel="noopener noreferrer" class="view-link">查看</a>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </template>
    </div>
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
.bigdrop-page {
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

.table-card {
  background: #fff;
  border-radius: var(--radius-card);
  border: 1px solid var(--color-border-soft);
  overflow: hidden;
}

.table-scroll {
  overflow-x: auto;
}

.drop-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.88rem;
}

.drop-table th {
  background: var(--color-bg-soft);
  padding: 12px 16px;
  text-align: left;
  font-size: 0.72rem;
  font-weight: 700;
  color: var(--color-fg-2);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  border-bottom: 1px solid var(--color-border-soft);
  white-space: nowrap;
}

.col-price,
.col-drop,
.col-amount {
  text-align: right;
}

.drop-row td {
  padding: 14px 16px;
  border-bottom: 1px solid var(--color-border-soft);
  vertical-align: middle;
}

.drop-row:last-child td {
  border-bottom: none;
}

.drop-row:hover {
  background: var(--color-bg-soft);
}

.col-title { min-width: 200px; }

.item-title {
  display: block;
  font-weight: 600;
  color: var(--color-fg);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 280px;
}

.item-addr {
  display: block;
  font-size: 0.75rem;
  color: var(--color-fg-3);
  margin-top: 2px;
}

.td-district { color: var(--color-fg-2); white-space: nowrap; }

.td-price { text-align: right; white-space: nowrap; }

.td-original {
  color: var(--color-fg-3);
  text-decoration: line-through;
}

.td-new {
  font-weight: 700;
  color: var(--color-fg);
}

.td-drop {
  text-align: right;
  color: var(--color-price-down);
  font-weight: 800;
  white-space: nowrap;
}

.td-amount {
  text-align: right;
  color: var(--color-price-down);
  font-weight: 600;
}

.view-link {
  display: inline-flex;
  align-items: center;
  padding: 5px 12px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--color-border);
  background: #fff;
  color: var(--color-fg);
  font-size: 0.78rem;
  font-weight: 600;
  text-decoration: none;
  white-space: nowrap;
  transition: all 0.15s;
}

.view-link:hover {
  background: var(--color-fg);
  border-color: var(--color-fg);
  color: #fff;
}
</style>
