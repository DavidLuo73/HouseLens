<template>
  <div class="detail-page">
    <div class="page-inner">
      <RouterLink to="/" class="back-link">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="19" y1="12" x2="5" y2="12"/><polyline points="12 19 5 12 12 5"/></svg>
        返回清單
      </RouterLink>

      <div v-if="loading" class="state-box">
        <div class="loader" />
        <p>載入中…</p>
      </div>
      <div v-else-if="error" class="state-box state-box--error">{{ error }}</div>

      <template v-else-if="property">
        <!-- 標題區 -->
        <div class="detail-header">
          <div class="detail-badges">
            <span v-if="property.isNew" class="badge badge--new">新上架</span>
            <span v-if="property.latestIsBigDrop" class="badge badge--bigdrop">大降價</span>
            <span v-if="property.hasParking" class="badge badge--parking">有車位</span>
            <span v-if="property.status === 'delisted'" class="badge badge--delisted">已下架</span>
          </div>
          <h1 class="detail-title">{{ property.title }}</h1>
          <div class="detail-score" v-if="property.score != null">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="#FF385C" stroke="none">
              <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
            </svg>
            {{ (property.score * 5).toFixed(1) }} 綜合評分
          </div>
        </div>

        <!-- 資訊卡片 -->
        <div class="info-card">
          <div class="info-grid">
            <div class="info-item">
              <span class="info-label">地區</span>
              <span class="info-value">{{ property.district }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">地址</span>
              <span class="info-value">{{ property.address ?? '未提供' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">坪數</span>
              <span class="info-value">{{ property.areaPing }} 坪</span>
            </div>
            <div class="info-item">
              <span class="info-label">樓層</span>
              <span class="info-value">{{ property.floor ?? '未提供' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">屋齡</span>
              <span class="info-value">{{ property.ageYears != null ? `${property.ageYears} 年` : '未提供' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">首見</span>
              <span class="info-value">{{ formatDate(property.firstSeenAt) }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">最後更新</span>
              <span class="info-value">{{ formatDate(property.lastSeenAt) }}</span>
            </div>
          </div>

          <div class="info-divider" />

          <div class="price-hero">
            <div>
              <span class="price-label">總價</span>
              <div class="price-big">{{ property.currentTotalPrice }}<em>萬</em></div>
            </div>
            <div v-if="property.currentUnitPrice">
              <span class="price-label">單價</span>
              <div class="price-unit">{{ property.currentUnitPrice.toFixed(1) }} 萬/坪</div>
            </div>
          </div>
        </div>

        <!-- 來源連結 -->
        <section v-if="property.sources.length" class="section-card">
          <h2 class="section-title">來源連結</h2>
          <ul class="sources-list">
            <li v-for="s in property.sources" :key="s.url" class="source-item">
              <span class="source-site">{{ s.sourceSite }}</span>
              <a :href="s.url" target="_blank" rel="noopener noreferrer" class="source-link">
                {{ s.title ?? s.url }}
              </a>
              <span v-if="s.postedDate" class="source-date">{{ formatDate(s.postedDate) }}</span>
            </li>
          </ul>
        </section>

        <!-- 價格趨勢 -->
        <section v-if="property.priceHistory.length" class="section-card">
          <h2 class="section-title">價格趨勢與歷史紀錄</h2>
          <PriceHistoryPanel :history="property.priceHistory" />
        </section>
      </template>
    </div>
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
.detail-page {
  background: var(--color-bg-soft);
  min-height: 100vh;
}

.page-inner {
  max-width: 880px;
  margin: 0 auto;
  padding: 32px 24px 64px;
}

@media (max-width: 640px) {
  .page-inner { padding: 20px 16px 48px; }
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--color-fg);
  text-decoration: none;
  margin-bottom: 24px;
}

.back-link:hover { text-decoration: underline; }

/* ===== 狀態 ===== */
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

/* ===== 標題區 ===== */
.detail-header {
  margin-bottom: 24px;
}

.detail-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 12px;
}

.badge {
  padding: 4px 10px;
  border-radius: var(--radius-pill);
  font-size: 0.72rem;
  font-weight: 700;
}
.badge--new    { background: var(--color-badge-new-bg); color: var(--color-badge-new-text); }
.badge--bigdrop { background: var(--color-badge-drop-bg); color: var(--color-badge-drop-text); }
.badge--parking { background: var(--color-badge-parking-bg); color: var(--color-badge-parking-text); }
.badge--delisted { background: var(--color-badge-delisted-bg); color: var(--color-badge-delisted-text); }

.detail-title {
  font-size: 1.5rem;
  font-weight: 800;
  color: var(--color-fg);
  line-height: 1.3;
  letter-spacing: -0.3px;
  margin-bottom: 8px;
}

@media (max-width: 640px) { .detail-title { font-size: 1.2rem; } }

.detail-score {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--color-fg);
}

/* ===== 資訊卡片 ===== */
.info-card {
  background: #fff;
  border-radius: var(--radius-card);
  border: 1px solid var(--color-border-soft);
  padding: 24px;
  margin-bottom: 20px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 16px 24px;
  margin-bottom: 20px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-label {
  font-size: 0.72rem;
  font-weight: 700;
  color: var(--color-fg-2);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.info-value {
  font-size: 0.9rem;
  font-weight: 500;
  color: var(--color-fg);
}

.info-divider {
  height: 1px;
  background: var(--color-border-soft);
  margin-bottom: 20px;
}

.price-hero {
  display: flex;
  gap: 40px;
  flex-wrap: wrap;
}

.price-label {
  font-size: 0.72rem;
  font-weight: 700;
  color: var(--color-fg-2);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  display: block;
  margin-bottom: 4px;
}

.price-big {
  font-size: 2rem;
  font-weight: 800;
  color: var(--color-fg);
  letter-spacing: -0.5px;
  line-height: 1;
}

.price-big em {
  font-style: normal;
  font-size: 1rem;
  font-weight: 600;
  color: var(--color-fg-2);
  margin-left: 4px;
}

.price-unit {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--color-fg);
}

/* ===== Section 卡片 ===== */
.section-card {
  background: #fff;
  border-radius: var(--radius-card);
  border: 1px solid var(--color-border-soft);
  padding: 24px;
  margin-bottom: 20px;
}

.section-title {
  font-size: 1rem;
  font-weight: 700;
  color: var(--color-fg);
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--color-border-soft);
}

/* ===== 來源連結 ===== */
.sources-list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.source-item {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.source-site {
  display: inline-block;
  padding: 2px 8px;
  border-radius: var(--radius-pill);
  background: var(--color-bg-soft);
  border: 1px solid var(--color-border);
  font-size: 0.72rem;
  font-weight: 700;
  color: var(--color-fg-2);
  white-space: nowrap;
}

.source-link {
  font-size: 0.88rem;
  color: var(--color-rausch);
  text-decoration: none;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-link:hover { text-decoration: underline; }

.source-date {
  font-size: 0.78rem;
  color: var(--color-fg-3);
  white-space: nowrap;
}
</style>
