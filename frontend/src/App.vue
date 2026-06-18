<template>
  <div id="layout">
    <nav class="nav-bar">
      <span class="app-title">HouseLens 房屋售價追蹤</span>
      <router-link to="/">物件清單</router-link>
      <router-link to="/analytics">區域分析</router-link>
      <router-link to="/top-properties">優質排行</router-link>
      <router-link to="/big-drop">大降價總覽</router-link>
      <router-link to="/config">評分設定</router-link>
      <router-link to="/config/districts">地區設定</router-link>
      <div class="crawl-trigger">
        <button
          class="btn-crawl"
          :disabled="isCrawling"
          @click="triggerCrawl"
          :title="isCrawling ? '爬取中，請稍候…' : '立即抓取所有平台最新資料'"
        >
          {{ isCrawling ? '爬取中' : '立即抓取' }}
        </button>
        <span v-if="isCrawling" class="spinner" aria-label="爬取中" />
      </div>
    </nav>

    <div v-if="isCrawling" class="crawl-progress-panel">
      <template v-if="progressData">
        <div class="progress-header">
          正在抓取：{{ progressData.currentPlatformName ?? '初始化中...' }}
          <span class="platform-index">
            ({{ progressData.currentPlatformIndex + 1 }}/{{ progressData.totalPlatforms }} 平台)
          </span>
        </div>
        <div class="progress-bar-row">
          <div class="progress-bar">
            <div class="progress-fill" :style="{ width: districtProgressPct + '%' }"></div>
          </div>
          <span class="progress-label">
            {{ progressData.completedDistricts.length }}/{{ progressData.totalDistricts }} 行政區
          </span>
        </div>
        <div class="district-list">
          <div v-for="d in progressData.completedDistricts" :key="d.districtName" class="district-done">
            ✓ {{ d.districtName }}：{{ d.fetchedCount }} 筆
          </div>
          <div v-if="progressData.currentDistrictName" class="district-current">
            ◎ {{ progressData.currentDistrictName }}：進行中...
          </div>
        </div>
        <div class="progress-footer">
          已耗時：{{ elapsed }} ｜ 已抓取 {{ progressData.platformFetchedCount }} 筆
        </div>
      </template>
      <div v-else class="progress-initializing">準備中...</div>
    </div>

    <main class="main-content">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { api } from '@/services/api'

interface ProgressData {
  isRunning: boolean
  currentPlatformName: string | null
  currentPlatformIndex: number
  totalPlatforms: number
  currentDistrictName: string | null
  currentDistrictIndex: number
  totalDistricts: number
  platformFetchedCount: number
  platformStartedAt: string | null
  completedDistricts: { districtName: string; fetchedCount: number }[]
}

const isCrawling = ref(false)
const progressData = ref<ProgressData | null>(null)
const elapsedSeconds = ref(0)

let eventSource: EventSource | null = null
let elapsedTimer: ReturnType<typeof setInterval> | null = null

const districtProgressPct = computed(() => {
  if (!progressData.value || progressData.value.totalDistricts === 0) return 0
  return Math.round(
    (progressData.value.completedDistricts.length / progressData.value.totalDistricts) * 100
  )
})

const elapsed = computed(() => {
  const m = Math.floor(elapsedSeconds.value / 60)
  const s = elapsedSeconds.value % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
})

watch(isCrawling, (running) => {
  if (running) {
    elapsedSeconds.value = 0
    elapsedTimer = setInterval(() => {
      if (progressData.value?.platformStartedAt) {
        elapsedSeconds.value = Math.floor(
          (Date.now() - new Date(progressData.value.platformStartedAt).getTime()) / 1000
        )
      } else {
        elapsedSeconds.value++
      }
    }, 1000)
  } else {
    if (elapsedTimer !== null) {
      clearInterval(elapsedTimer)
      elapsedTimer = null
    }
  }
})

function startProgressStream() {
  stopProgressStream()
  let hasSeenRunning = false
  const es = new EventSource('/api/crawl-runs/stream')
  es.onmessage = (e: MessageEvent) => {
    const data: ProgressData = JSON.parse(e.data)
    if (data.isRunning) {
      hasSeenRunning = true
      progressData.value = data
    } else if (hasSeenRunning) {
      // 確認爬取曾啟動後才視為完成，避免初始快照（isRunning=false）誤觸關閉
      isCrawling.value = false
      progressData.value = null
      stopProgressStream()
    }
  }
  es.onerror = () => {
    stopProgressStream()
    isCrawling.value = false
  }
  eventSource = es
}

function stopProgressStream() {
  eventSource?.close()
  eventSource = null
}

async function checkCrawlStatus() {
  try {
    const run = await api.crawlRuns.latest()
    if (run.status === 'running') {
      isCrawling.value = true
      startProgressStream()
    } else {
      isCrawling.value = false
    }
  } catch {
    // 後端暫時不可用，不更新狀態
  }
}

async function triggerCrawl() {
  if (isCrawling.value) return
  try {
    await api.admin.triggerCrawl()
    isCrawling.value = true
    startProgressStream()
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : String(e)
    // 409 = 已有爬蟲在跑，當作正在爬取處理
    if (msg.includes('409') || msg.toLowerCase().includes('conflict')) {
      isCrawling.value = true
      startProgressStream()
    }
  }
}

onMounted(async () => {
  // 頁面載入時同步目前爬取狀態（背景已在跑時同步顯示進度）
  await checkCrawlStatus()
})

onUnmounted(stopProgressStream)
</script>

<style>
* { box-sizing: border-box; margin: 0; padding: 0; }
body { font-family: system-ui, sans-serif; background: #f5f5f5; }
#layout { display: flex; flex-direction: column; min-height: 100vh; }
.nav-bar {
  display: flex; align-items: center; gap: 1.5rem;
  padding: 0.75rem 1.5rem; background: #1a1a2e; color: #fff;
}
.app-title { font-weight: bold; margin-right: auto; }
.nav-bar a { color: #ccc; text-decoration: none; }
.nav-bar a:hover, .nav-bar a.router-link-active { color: #fff; text-decoration: underline; }
.main-content { padding: 1.5rem; flex: 1; }

.crawl-trigger {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}
.btn-crawl {
  padding: 0.3rem 0.75rem;
  background: #2563eb;
  color: #fff;
  border: none;
  border-radius: 5px;
  font-size: 0.82rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s;
  white-space: nowrap;
}
.btn-crawl:hover:not(:disabled) { background: #1d4ed8; }
.btn-crawl:disabled { background: #475569; cursor: not-allowed; }

.spinner {
  display: inline-block;
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255,255,255,0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
  flex-shrink: 0;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}

.crawl-progress-panel {
  background: #1e293b;
  color: #e2e8f0;
  padding: 0.75rem 1.5rem;
  font-size: 0.82rem;
  border-bottom: 1px solid #334155;
}
.progress-header { font-weight: 600; margin-bottom: 0.4rem; }
.platform-index { color: #94a3b8; font-weight: normal; }
.progress-bar-row {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 0.5rem;
}
.progress-bar {
  flex: 1;
  height: 6px;
  background: #334155;
  border-radius: 3px;
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  background: #3b82f6;
  border-radius: 3px;
  transition: width 0.3s ease;
}
.progress-label { color: #94a3b8; white-space: nowrap; }
.district-list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.3rem 1.2rem;
  margin-bottom: 0.4rem;
}
.district-done { color: #86efac; }
.district-current { color: #fbbf24; }
.progress-footer { color: #94a3b8; }
.progress-initializing { color: #94a3b8; }
</style>
