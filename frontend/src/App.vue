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
    <main class="main-content">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { api } from '@/services/api'

const isCrawling = ref(false)
let pollTimer: ReturnType<typeof setInterval> | null = null

async function checkCrawlStatus() {
  try {
    const run = await api.crawlRuns.latest()
    isCrawling.value = run.status === 'running'
    if (!isCrawling.value) stopPolling()
  } catch {
    // 後端暫時不可用，停止輪詢以免console爆紅
    stopPolling()
  }
}

function startPolling() {
  stopPolling()
  pollTimer = setInterval(checkCrawlStatus, 4000)
}

function stopPolling() {
  if (pollTimer !== null) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function triggerCrawl() {
  if (isCrawling.value) return
  try {
    await api.admin.triggerCrawl()
    isCrawling.value = true
    startPolling()
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : String(e)
    // 409 = 已有爬蟲在跑，當作正在爬取處理
    if (msg.includes('409') || msg.toLowerCase().includes('conflict')) {
      isCrawling.value = true
      startPolling()
    }
  }
}

onMounted(async () => {
  // 頁面載入時同步目前爬取狀態（背景已在跑時同步顯示 spinner）
  await checkCrawlStatus()
  if (isCrawling.value) startPolling()
})

onUnmounted(stopPolling)
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
</style>
