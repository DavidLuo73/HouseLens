<template>
  <div class="property-detail">
    <RouterLink to="/properties" class="back-link">← 返回清單</RouterLink>

    <div v-if="loading" class="loading">載入中…</div>
    <div v-else-if="error" class="error">{{ error }}</div>

    <template v-else-if="property">
      <!-- Header -->
      <div class="header">
        <h1>{{ property.title }}</h1>
        <div class="tags">
          <span v-if="property.isNew" class="tag tag--new">新上架</span>
          <span v-if="property.status === 'delisted'" class="tag tag--delisted">已下架</span>
          <span v-if="property.latestIsBigDrop" class="tag tag--bigdrop">大降價</span>
          <span v-if="property.hasParking" class="tag tag--parking">有車位</span>
        </div>
      </div>

      <!-- Basic Info -->
      <section class="info-grid">
        <div class="info-item">
          <label>地區</label>
          <span>{{ property.district }}</span>
        </div>
        <div class="info-item">
          <label>地址</label>
          <span>{{ property.address ?? '未提供' }}</span>
        </div>
        <div class="info-item">
          <label>坪數</label>
          <span>{{ property.areaPing }} 坪</span>
        </div>
        <div class="info-item">
          <label>樓層</label>
          <span>{{ property.floor ?? '未提供' }}</span>
        </div>
        <div class="info-item">
          <label>屋齡</label>
          <span>{{ property.ageYears != null ? `${property.ageYears} 年` : '未提供' }}</span>
        </div>
        <div class="info-item">
          <label>總價</label>
          <span class="price">{{ property.currentTotalPrice }} 萬</span>
        </div>
        <div class="info-item">
          <label>單價</label>
          <span>{{ property.currentUnitPrice != null ? `${property.currentUnitPrice.toFixed(1)} 萬/坪` : '未提供' }}</span>
        </div>
        <div class="info-item" v-if="property.score != null">
          <label>綜合評分</label>
          <span class="score">{{ (property.score * 100).toFixed(0) }}</span>
        </div>
        <div class="info-item">
          <label>首見</label>
          <span>{{ formatDate(property.firstSeenAt) }}</span>
        </div>
        <div class="info-item">
          <label>最後更新</label>
          <span>{{ formatDate(property.lastSeenAt) }}</span>
        </div>
      </section>

      <!-- Sources -->
      <section v-if="property.sources.length">
        <h2>來源連結</h2>
        <ul class="sources">
          <li v-for="s in property.sources" :key="s.url">
            <a :href="s.url" target="_blank" rel="noopener noreferrer">
              [{{ s.sourceSite }}] {{ s.title ?? s.url }}
              <span v-if="s.postedDate" class="muted">（{{ formatDate(s.postedDate) }}）</span>
            </a>
          </li>
        </ul>
      </section>

      <!-- Price History -->
      <section v-if="property.priceHistory.length">
        <h2>價格趨勢與歷史紀錄</h2>
        <PriceHistoryPanel :history="property.priceHistory" />
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { api, type PropertyDetail } from '@/services/api'
import PriceHistoryPanel from '@/components/PriceHistoryPanel.vue'

const route = useRoute()
const property = ref<PropertyDetail | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)

onMounted(async () => {
  try {
    property.value = await api.properties.get(route.params.id as string)
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '載入失敗'
  } finally {
    loading.value = false
  }
})

function formatDate(iso?: string): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('zh-TW', { year: 'numeric', month: '2-digit', day: '2-digit' })
}
</script>

<style scoped>
.property-detail { max-width: 900px; margin: 0 auto; padding: 1.5rem; }
.back-link { color: #3b82f6; text-decoration: none; font-size: 0.9rem; }
.back-link:hover { text-decoration: underline; }
.header { display: flex; align-items: flex-start; gap: 1rem; flex-wrap: wrap; margin: 1rem 0; }
.header h1 { margin: 0; font-size: 1.4rem; }
.tags { display: flex; gap: 0.4rem; flex-wrap: wrap; }
.tag { padding: 0.2rem 0.5rem; border-radius: 4px; font-size: 0.75rem; font-weight: 600; }
.tag--new { background: #dbeafe; color: #1d4ed8; }
.tag--delisted { background: #f3f4f6; color: #6b7280; }
.tag--bigdrop { background: #fee2e2; color: #dc2626; }
.tag--parking { background: #d1fae5; color: #065f46; }
.info-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 1rem; margin: 1.5rem 0; }
.info-item label { display: block; font-size: 0.75rem; color: #6b7280; margin-bottom: 0.2rem; }
.price { font-size: 1.2rem; font-weight: 700; color: #dc2626; }
.score { font-size: 1.2rem; font-weight: 700; color: #0891b2; }
h2 { font-size: 1.1rem; margin: 1.5rem 0 0.75rem; border-bottom: 1px solid #e5e7eb; padding-bottom: 0.4rem; }
.sources { list-style: none; padding: 0; }
.sources li { margin-bottom: 0.4rem; }
.sources a { color: #3b82f6; }
.muted { color: #9ca3af; font-size: 0.85rem; }
.loading, .error { text-align: center; padding: 3rem; color: #6b7280; }
.error { color: #dc2626; }
</style>
