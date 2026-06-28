<template>
  <div id="layout">
    <!-- 頂部 Header -->
    <header class="app-header">
      <div class="header-inner">
        <!-- Logo -->
        <router-link to="/" class="logo" @click="closeMenu">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M3 9.5L12 3l9 6.5V21a1 1 0 01-1 1H4a1 1 0 01-1-1V9.5z" fill="#FF385C"/>
            <path d="M9 21V12h6v9" fill="white"/>
          </svg>
          <span class="logo-text">HouseLens</span>
        </router-link>

        <!-- 桌機導覽列 -->
        <nav class="desktop-nav" aria-label="主導覽">
          <router-link to="/">物件清單</router-link>
          <router-link to="/analytics">區域分析</router-link>
          <router-link to="/top-properties">優質排行</router-link>
          <router-link to="/big-drop">大降價</router-link>
          <router-link to="/config">評分設定</router-link>
          <router-link to="/config/districts">地區設定</router-link>
          <router-link to="/admin/platforms">平台管理</router-link>
        </nav>

        <!-- 右側操作區 -->
        <div class="header-actions">
          <div class="crawl-trigger">
            <button
              class="btn-crawl"
              :class="{ 'btn-crawl--loading': isCrawling }"
              :disabled="isCrawling"
              :title="isCrawling ? '爬取中，請稍候…' : '立即抓取所有平台最新資料'"
              @click="triggerCrawl"
            >
              <span v-if="isCrawling" class="spinner" aria-hidden="true" />
              {{ isCrawling ? '爬取中…' : '立即抓取' }}
            </button>
          </div>

          <!-- 漢堡選單按鈕（僅手機顯示） -->
          <button
            class="hamburger-btn"
            :aria-expanded="menuOpen"
            aria-controls="mobile-menu"
            aria-label="開關選單"
            @click="toggleMenu"
          >
            <span class="hamburger-line" :class="{ open: menuOpen }" />
            <span class="hamburger-line" :class="{ open: menuOpen }" />
            <span class="hamburger-line" :class="{ open: menuOpen }" />
          </button>
        </div>
      </div>
    </header>

    <!-- 手機選單抽屜 -->
    <transition name="slide-down">
      <nav
        v-if="menuOpen"
        id="mobile-menu"
        class="mobile-menu"
        aria-label="手機導覽"
      >
        <router-link to="/" @click="closeMenu">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/></svg>
          物件清單
        </router-link>
        <router-link to="/analytics" @click="closeMenu">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>
          區域分析
        </router-link>
        <router-link to="/top-properties" @click="closeMenu">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
          優質排行
        </router-link>
        <router-link to="/big-drop" @click="closeMenu">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="23 6 13.5 15.5 8.5 10.5 1 18"/><polyline points="17 6 23 6 23 12"/></svg>
          大降價
        </router-link>
        <router-link to="/config" @click="closeMenu">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M19.07 4.93a10 10 0 010 14.14M4.93 4.93a10 10 0 000 14.14"/></svg>
          評分設定
        </router-link>
        <router-link to="/config/districts" @click="closeMenu">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 10c0 7-9 13-9 13S3 17 3 10a9 9 0 0118 0z"/><circle cx="12" cy="10" r="3"/></svg>
          地區設定
        </router-link>
        <router-link to="/admin/platforms" @click="closeMenu">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 013 3L7 19l-4 1 1-4L16.5 3.5z"/></svg>
          平台管理
        </router-link>
      </nav>
    </transition>

    <!-- 遮罩（手機選單開啟時） -->
    <transition name="fade">
      <div v-if="menuOpen" class="menu-overlay" @click="closeMenu" aria-hidden="true" />
    </transition>

    <!-- 爬取進度面板 -->
    <transition name="slide-down">
      <div v-if="isCrawling" class="crawl-progress-panel">
        <template v-if="progressData">
          <div class="progress-header">
            <span class="progress-platform">{{ progressData.currentPlatformName ?? '初始化中…' }}</span>
            <span class="progress-index">({{ progressData.currentPlatformIndex + 1 }}/{{ progressData.totalPlatforms }} 平台)</span>
            <span class="progress-elapsed">{{ elapsed }}</span>
          </div>
          <div class="progress-bar-wrap">
            <div class="progress-bar">
              <div class="progress-fill" :style="{ width: districtProgressPct + '%' }" />
            </div>
            <span class="progress-count">{{ progressData.completedDistricts.length }}/{{ progressData.totalDistricts }} 行政區</span>
          </div>
          <div class="district-tags">
            <span v-for="d in progressData.completedDistricts" :key="d.districtName" class="district-tag district-tag--done">
              ✓ {{ d.districtName }}
            </span>
            <span v-if="progressData.currentDistrictName" class="district-tag district-tag--current">
              ◎ {{ progressData.currentDistrictName }}
            </span>
          </div>
        </template>
        <div v-else class="progress-init">正在準備抓取…</div>
      </div>
    </transition>

    <!-- 主內容 -->
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
const menuOpen = ref(false)

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

function toggleMenu() {
  menuOpen.value = !menuOpen.value
}

function closeMenu() {
  menuOpen.value = false
}

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
    if (msg.includes('409') || msg.toLowerCase().includes('conflict')) {
      isCrawling.value = true
      startProgressStream()
    }
  }
}

function onCrawlTriggered() {
  if (!isCrawling.value) {
    isCrawling.value = true
    startProgressStream()
  }
}

onMounted(async () => {
  await checkCrawlStatus()
  window.addEventListener('houselens:crawl-triggered', onCrawlTriggered)
})

onUnmounted(() => {
  stopProgressStream()
  window.removeEventListener('houselens:crawl-triggered', onCrawlTriggered)
})
</script>

<style scoped>
/* ===== Header ===== */
.app-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: #fff;
  border-bottom: 1px solid var(--color-border-soft);
  box-shadow: 0 1px 0 var(--color-border-soft);
}

.header-inner {
  max-width: 1280px;
  margin: 0 auto;
  padding: 0 24px;
  height: 64px;
  display: flex;
  align-items: center;
  gap: 24px;
}

/* ===== Logo ===== */
.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  text-decoration: none;
  flex-shrink: 0;
}

.logo-text {
  font-size: 1.1rem;
  font-weight: 800;
  color: var(--color-fg);
  letter-spacing: -0.3px;
}

/* ===== 桌機導覽 ===== */
.desktop-nav {
  display: none;
  align-items: center;
  gap: 4px;
  flex: 1;
  justify-content: center;
}

@media (min-width: 768px) {
  .desktop-nav {
    display: flex;
  }
}

.desktop-nav a {
  padding: 6px 12px;
  border-radius: var(--radius-pill);
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--color-fg-2);
  text-decoration: none;
  transition: color 0.15s, background 0.15s;
  white-space: nowrap;
}

.desktop-nav a:hover {
  color: var(--color-fg);
  background: var(--color-bg-soft);
}

.desktop-nav a.router-link-active {
  color: var(--color-fg);
  font-weight: 600;
}

.desktop-nav a.router-link-exact-active {
  color: var(--color-rausch);
}

/* ===== 右側操作區 ===== */
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

/* ===== 立即抓取按鈕 ===== */
.btn-crawl {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: var(--color-rausch);
  color: #fff;
  border: none;
  border-radius: var(--radius-pill);
  font-size: 0.82rem;
  font-weight: 600;
  white-space: nowrap;
  transition: background 0.15s, transform 0.1s;
  letter-spacing: 0.01em;
}

.btn-crawl:hover:not(:disabled) {
  background: var(--color-rausch-hover);
}

.btn-crawl:active:not(:disabled) {
  transform: scale(0.97);
}

.btn-crawl--loading,
.btn-crawl:disabled {
  background: #e0e0e0;
  color: #999;
  cursor: not-allowed;
}

/* ===== 漢堡按鈕 ===== */
.hamburger-btn {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 5px;
  width: 40px;
  height: 40px;
  padding: 8px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-input);
  background: #fff;
  cursor: pointer;
  transition: border-color 0.15s;
}

@media (min-width: 768px) {
  .hamburger-btn {
    display: none;
  }
}

.hamburger-btn:hover {
  border-color: var(--color-fg);
}

.hamburger-line {
  display: block;
  width: 100%;
  height: 1.5px;
  background: var(--color-fg);
  border-radius: 2px;
  transform-origin: center;
  transition: transform 0.25s ease, opacity 0.25s ease;
}

.hamburger-line.open:nth-child(1) {
  transform: translateY(6.5px) rotate(45deg);
}
.hamburger-line.open:nth-child(2) {
  opacity: 0;
  transform: scaleX(0);
}
.hamburger-line.open:nth-child(3) {
  transform: translateY(-6.5px) rotate(-45deg);
}

/* ===== 手機選單 ===== */
.mobile-menu {
  position: fixed;
  top: 64px;
  left: 0;
  right: 0;
  z-index: 99;
  background: #fff;
  border-bottom: 1px solid var(--color-border-soft);
  padding: 8px 0 16px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
}

.mobile-menu a {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 24px;
  font-size: 0.95rem;
  font-weight: 500;
  color: var(--color-fg);
  text-decoration: none;
  transition: background 0.1s;
}

.mobile-menu a:hover {
  background: var(--color-bg-soft);
}

.mobile-menu a.router-link-exact-active {
  color: var(--color-rausch);
  font-weight: 600;
}

.mobile-menu a.router-link-exact-active svg {
  stroke: var(--color-rausch);
}

/* ===== 遮罩 ===== */
.menu-overlay {
  position: fixed;
  inset: 64px 0 0 0;
  z-index: 98;
  background: rgba(0, 0, 0, 0.3);
}

/* ===== 爬取進度面板 ===== */
.crawl-progress-panel {
  background: var(--color-bg-soft);
  border-bottom: 1px solid var(--color-border-soft);
  padding: 12px 24px;
  font-size: 0.82rem;
}

.progress-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.progress-platform {
  font-weight: 600;
  color: var(--color-fg);
}

.progress-index {
  color: var(--color-fg-2);
}

.progress-elapsed {
  margin-left: auto;
  font-variant-numeric: tabular-nums;
  color: var(--color-fg-2);
}

.progress-bar-wrap {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.progress-bar {
  flex: 1;
  height: 4px;
  background: var(--color-border-soft);
  border-radius: var(--radius-pill);
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: var(--color-rausch);
  border-radius: var(--radius-pill);
  transition: width 0.4s ease;
}

.progress-count {
  white-space: nowrap;
  color: var(--color-fg-2);
  font-variant-numeric: tabular-nums;
}

.district-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 8px;
}

.district-tag {
  font-size: 0.75rem;
  padding: 2px 8px;
  border-radius: var(--radius-pill);
}

.district-tag--done {
  background: #ECFDF5;
  color: #065F46;
}

.district-tag--current {
  background: #FFF7ED;
  color: #C2410C;
  font-weight: 600;
}

.progress-init {
  color: var(--color-fg-2);
}

/* ===== 主內容 ===== */
.main-content {
  flex: 1;
  min-height: calc(100vh - 64px);
}

/* ===== spinner ===== */
.spinner {
  display: inline-block;
  width: 12px;
  height: 12px;
  border: 1.5px solid rgba(255, 255, 255, 0.4);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ===== 動畫 ===== */
.slide-down-enter-active,
.slide-down-leave-active {
  transition: transform 0.25s ease, opacity 0.25s ease;
}
.slide-down-enter-from,
.slide-down-leave-to {
  transform: translateY(-8px);
  opacity: 0;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
