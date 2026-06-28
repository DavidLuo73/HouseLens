<template>
  <div class="platform-page">
    <div class="page-inner">
      <div class="page-header">
        <h1 class="page-title">平台資料管理</h1>
        <p class="page-desc">清空特定平台的刊登、物件、價格歷史與抓取統計，讓該平台的資料可以乾淨重建。</p>
      </div>

      <div v-if="loading" class="state-box">
        <div class="loader"></div>
        <p>載入中…</p>
      </div>

      <div v-else-if="error" class="state-box state-box--error">{{ error }}</div>

      <div v-else class="cards-grid">
        <div v-for="p in platforms" :key="p.sourceSite" class="platform-card">
          <div class="card-header">
            <h2 class="platform-name">{{ p.displayName }}</h2>
            <span class="platform-badge">{{ p.sourceSite }}</span>
          </div>

          <dl class="stats">
            <div class="stat-item">
              <dt>刊登筆數</dt>
              <dd>{{ p.listingCount.toLocaleString() }}</dd>
            </div>
            <div class="stat-item">
              <dt>關聯物件</dt>
              <dd>{{ p.propertyCount.toLocaleString() }}</dd>
            </div>
            <div class="stat-item">
              <dt>價格歷史</dt>
              <dd>{{ p.priceHistoryCount.toLocaleString() }}</dd>
            </div>
            <div class="stat-item stat-full">
              <dt>最後抓取</dt>
              <dd>
                <template v-if="p.lastCrawlAt">
                  <span :class="['crawl-status', p.lastCrawlSuccess ? 'ok' : 'fail']">
                    {{ p.lastCrawlSuccess ? '成功' : '失敗' }}
                  </span>
                  {{ formatDate(p.lastCrawlAt) }}
                  <span v-if="p.lastCrawlSuccess" class="fetched-count">（{{ p.lastCrawlFetchedCount }} 筆）</span>
                </template>
                <span v-else class="no-crawl">尚未抓取</span>
              </dd>
            </div>
          </dl>

          <div class="card-actions">
            <button
              class="btn-purge"
              :disabled="isBusy()"
              @click="startPurge(p)"
            >
              <span v-if="purging === p.sourceSite" class="btn-spinner"></span>
              {{ purging === p.sourceSite ? '清空中…' : '清空此平台資料' }}
            </button>

            <button
              class="btn-recrawl"
              :disabled="isBusy()"
              @click="startRecrawl(p)"
            >
              <span v-if="recrawling === p.sourceSite" class="btn-spinner btn-spinner--light"></span>
              {{ recrawling === p.sourceSite ? '已觸發…' : '重新抓取' }}
            </button>
          </div>

          <p v-if="successMsg[p.sourceSite]" class="action-msg action-msg--ok">{{ successMsg[p.sourceSite] }}</p>
          <p v-if="actionError[p.sourceSite]" class="action-msg action-msg--err">{{ actionError[p.sourceSite] }}</p>
        </div>
      </div>
    </div>

    <ConfirmDialog
      v-if="confirmTarget"
      :title="`清空「${confirmTarget.displayName}」的所有資料`"
      :message="`此操作將刪除該平台的所有刊登（${confirmTarget.listingCount} 筆）、無其他平台共享的物件（${confirmTarget.propertyCount} 筆）、價格歷史及抓取統計。此動作不可撤銷，若要重建資料請之後手動點選「重新抓取」。`"
      confirm-label="確認清空"
      :require-text="confirmTarget.displayName"
      @confirm="doPurge"
      @cancel="confirmTarget = null"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api, type PlatformStats } from '@/services/api'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const platforms = ref<PlatformStats[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

const purging = ref<string | null>(null)
const recrawling = ref<string | null>(null)
const confirmTarget = ref<PlatformStats | null>(null)
const successMsg = ref<Record<string, string>>({})
const actionError = ref<Record<string, string>>({})

function isBusy() {
  return purging.value !== null || recrawling.value !== null
}

function formatDate(iso: string) {
  const d = new Date(iso)
  return d.toLocaleString('zh-TW', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

async function loadStats() {
  loading.value = true
  error.value = null
  try {
    platforms.value = await api.admin.platformStats()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '載入失敗'
  } finally {
    loading.value = false
  }
}

function startPurge(p: PlatformStats) {
  successMsg.value[p.sourceSite] = ''
  actionError.value[p.sourceSite] = ''
  confirmTarget.value = p
}

async function doPurge() {
  if (!confirmTarget.value) return
  const site = confirmTarget.value.sourceSite
  confirmTarget.value = null
  purging.value = site
  successMsg.value[site] = ''
  actionError.value[site] = ''
  try {
    const result = await api.admin.purgePlatform(site)
    successMsg.value[site] =
      `已清空：刊登 ${result.listingsDeleted} 筆、物件 ${result.propertiesDeleted} 筆、` +
      `價格歷史 ${result.priceHistoryDeleted} 筆、抓取統計 ${result.sourceRunResultsDeleted} 筆`
    await loadStats()
  } catch (e) {
    actionError.value[site] = e instanceof Error ? e.message : '清空失敗'
  } finally {
    purging.value = null
  }
}

async function startRecrawl(p: PlatformStats) {
  successMsg.value[p.sourceSite] = ''
  actionError.value[p.sourceSite] = ''
  recrawling.value = p.sourceSite
  try {
    await api.admin.recrawlPlatform(p.sourceSite)
    successMsg.value[p.sourceSite] = '重新抓取已觸發'
    window.dispatchEvent(new CustomEvent('houselens:crawl-triggered'))
  } catch (e) {
    actionError.value[p.sourceSite] = e instanceof Error ? e.message : '觸發失敗'
  } finally {
    recrawling.value = null
  }
}

onMounted(loadStats)
</script>

<style scoped>
.platform-page {
  background: var(--color-bg-soft);
  min-height: 100vh;
}

.page-inner {
  max-width: 1120px;
  margin: 0 auto;
  padding: 40px 24px 80px;
}

.page-header {
  margin-bottom: 32px;
}

.page-title {
  font-size: 1.5rem;
  font-weight: 800;
  color: var(--color-fg);
  margin: 0 0 8px;
}

.page-desc {
  color: var(--color-fg-2);
  font-size: 0.92rem;
  margin: 0;
  line-height: 1.6;
}

.state-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 80px 24px;
  color: var(--color-fg-2);
}

.state-box--error {
  color: var(--color-price-down);
}

.loader {
  width: 32px;
  height: 32px;
  border: 3px solid var(--color-border);
  border-top-color: var(--color-rausch);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

.cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
}

.platform-card {
  background: #fff;
  border-radius: var(--radius-card);
  border: 1px solid var(--color-border-soft);
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.platform-name {
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--color-fg);
  margin: 0;
}

.platform-badge {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--color-fg-2);
  background: var(--color-bg-soft);
  border: 1px solid var(--color-border-soft);
  border-radius: var(--radius-pill);
  padding: 2px 10px;
}

.stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 16px;
  margin: 0;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.stat-full {
  grid-column: 1 / -1;
}

.stat-item dt {
  font-size: 0.76rem;
  color: var(--color-fg-2);
  font-weight: 500;
}

.stat-item dd {
  font-size: 1rem;
  font-weight: 700;
  color: var(--color-fg);
  margin: 0;
}

.crawl-status {
  font-size: 0.78rem;
  font-weight: 700;
  border-radius: var(--radius-pill);
  padding: 1px 7px;
  margin-right: 4px;
}

.crawl-status.ok {
  background: #e6f9ee;
  color: var(--color-price-up);
}

.crawl-status.fail {
  background: #fdf0ef;
  color: var(--color-price-down);
}

.fetched-count {
  font-size: 0.84rem;
  color: var(--color-fg-2);
  font-weight: 400;
}

.no-crawl {
  font-size: 0.88rem;
  color: var(--color-fg-3);
  font-weight: 400;
}

.card-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.btn-purge {
  flex: 1;
  min-width: 120px;
  padding: 9px 16px;
  border-radius: var(--radius-pill);
  border: 1.5px solid var(--color-price-down);
  background: #fff;
  color: var(--color-price-down);
  font-size: 0.86rem;
  font-weight: 700;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  transition: all 0.15s;
}

.btn-purge:hover:not(:disabled) {
  background: var(--color-price-down);
  color: #fff;
}

.btn-purge:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.btn-recrawl {
  flex: 1;
  min-width: 100px;
  padding: 9px 16px;
  border-radius: var(--radius-pill);
  border: none;
  background: var(--color-rausch);
  color: #fff;
  font-size: 0.86rem;
  font-weight: 700;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  transition: all 0.15s;
}

.btn-recrawl:hover:not(:disabled) {
  background: var(--color-rausch-hover);
}

.btn-recrawl:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid var(--color-price-down);
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  flex-shrink: 0;
}

.btn-spinner--light {
  border-color: rgba(255,255,255,0.5);
  border-top-color: transparent;
}

.action-msg {
  font-size: 0.84rem;
  margin: 0;
  line-height: 1.5;
}

.action-msg--ok {
  color: var(--color-price-up);
}

.action-msg--err {
  color: var(--color-price-down);
}
</style>
