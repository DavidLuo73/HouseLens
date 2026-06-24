<template>
  <div class="filter-section">
    <!-- 膠囊搜尋列（Airbnb 招牌記憶點） -->
    <div class="search-capsule" :class="{ 'search-capsule--focused': panelOpen }">
      <!-- 行政區段 -->
      <button class="capsule-seg" @click="togglePanel('district')">
        <span class="seg-label">地區</span>
        <span class="seg-value">{{ districtSummary }}</span>
      </button>

      <div class="capsule-divider" />

      <!-- 總價段 -->
      <button class="capsule-seg" @click="togglePanel('price')">
        <span class="seg-label">總價</span>
        <span class="seg-value">{{ priceSummary }}</span>
      </button>

      <div class="capsule-divider" />

      <!-- 條件段 -->
      <button class="capsule-seg capsule-seg--wide" @click="togglePanel('more')">
        <span class="seg-label">條件</span>
        <span class="seg-value">{{ condSummary }}</span>
      </button>

      <!-- 搜尋鈕 -->
      <button class="capsule-search-btn" aria-label="套用篩選" @click="closePanel">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
        </svg>
      </button>
    </div>

    <!-- 展開面板 -->
    <transition name="panel-fade">
      <div v-if="panelOpen" class="filter-panel">
        <!-- 地區面板 -->
        <div v-if="activePanel === 'district'" class="panel-section">
          <h3 class="panel-title">行政區</h3>
          <div class="chip-grid">
            <button
              v-for="d in availableDistricts"
              :key="d"
              class="chip"
              :class="{ 'chip--active': filters.districts.includes(d) }"
              @click="filters.toggleDistrict(d)"
            >{{ d }}</button>
          </div>

          <h3 class="panel-title mt-4">平台</h3>
          <div class="chip-grid">
            <button
              v-for="site in SOURCE_SITES"
              :key="site.value"
              class="chip"
              :class="{ 'chip--active': filters.sourceSites.includes(site.value) }"
              @click="filters.toggleSourceSite(site.value)"
            >{{ site.label }}</button>
          </div>
        </div>

        <!-- 價格面板 -->
        <div v-if="activePanel === 'price'" class="panel-section">
          <h3 class="panel-title">總價範圍（萬）</h3>
          <div class="price-inputs">
            <div class="price-input-wrap">
              <label class="input-label">最低</label>
              <input
                type="number"
                :value="filters.minPrice ?? ''"
                placeholder="不限"
                class="price-input"
                min="0"
                @change="onMinPrice"
              />
            </div>
            <span class="price-dash">—</span>
            <div class="price-input-wrap">
              <label class="input-label">最高</label>
              <input
                type="number"
                :value="filters.maxPrice ?? ''"
                placeholder="不限"
                class="price-input"
                min="0"
                @change="onMaxPrice"
              />
            </div>
          </div>

          <h3 class="panel-title mt-4">排序方式</h3>
          <div class="chip-grid">
            <button
              v-for="opt in SORT_OPTIONS"
              :key="opt.value"
              class="chip"
              :class="{ 'chip--active': filters.sortBy === opt.value }"
              @click="filters.sortBy = opt.value; filters.page = 1"
            >{{ opt.label }}</button>
          </div>
        </div>

        <!-- 更多條件面板 -->
        <div v-if="activePanel === 'more'" class="panel-section">
          <h3 class="panel-title">物件條件</h3>
          <div class="toggle-list">
            <button
              class="toggle-item"
              :class="{ 'toggle-item--active': filters.hasParking === true }"
              @click="onToggleParking"
            >
              <span class="toggle-icon">
                <svg v-if="filters.hasParking" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>
                <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
              </span>
              有車位
            </button>
            <button
              class="toggle-item"
              :class="{ 'toggle-item--active': filters.priceDropped === true }"
              @click="onTogglePriceDropped"
            >
              <span class="toggle-icon">
                <svg v-if="filters.priceDropped" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>
                <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
              </span>
              近期降價
            </button>
            <button
              class="toggle-item toggle-item--delisted"
              :class="{ 'toggle-item--active': filters.status === 'delisted' }"
              @click="onToggleDelisted"
            >
              <span class="toggle-icon">
                <svg v-if="filters.status === 'delisted'" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>
                <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
              </span>
              僅顯示下架
            </button>
          </div>
        </div>

        <!-- 面板底部操作 -->
        <div class="panel-footer">
          <button class="btn-reset" @click="filters.reset(); closePanel()">重設所有篩選</button>
          <button class="btn-apply" @click="closePanel">套用</button>
        </div>
      </div>
    </transition>

    <!-- 遮罩 -->
    <transition name="fade">
      <div v-if="panelOpen" class="panel-overlay" @click="closePanel" />
    </transition>

    <!-- 作用中篩選標籤列 -->
    <div v-if="hasActiveFilters" class="active-filters">
      <template v-if="filters.districts.length">
        <span v-for="d in filters.districts" :key="d" class="active-tag">
          {{ d }}
          <button class="tag-remove" @click="filters.toggleDistrict(d)">×</button>
        </span>
      </template>
      <span v-if="filters.hasParking" class="active-tag">
        有車位 <button class="tag-remove" @click="filters.hasParking = null; filters.page = 1">×</button>
      </span>
      <span v-if="filters.priceDropped" class="active-tag">
        近期降價 <button class="tag-remove" @click="filters.priceDropped = null; filters.page = 1">×</button>
      </span>
      <span v-if="filters.status === 'delisted'" class="active-tag">
        已下架 <button class="tag-remove" @click="filters.status = 'active'; filters.page = 1">×</button>
      </span>
      <span v-if="filters.minPrice || filters.maxPrice" class="active-tag">
        {{ filters.minPrice ?? '' }} – {{ filters.maxPrice ?? '' }} 萬
        <button class="tag-remove" @click="filters.minPrice = null; filters.maxPrice = null; filters.page = 1">×</button>
      </span>
      <button class="clear-all" @click="filters.reset()">清除全部</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useFiltersStore } from '@/stores/filters'

defineProps<{ availableDistricts: string[] }>()

const filters = useFiltersStore()

const SOURCE_SITES = [
  { value: 'F591', label: '591' },
  { value: 'Sinyi', label: '信義' },
  { value: 'Yungching', label: '永慶' },
  { value: 'Rakuya', label: '樂屋' },
  { value: 'TwHouse', label: '台灣好屋' },
]

const SORT_OPTIONS = [
  { value: 'score', label: '評分（預設）' },
  { value: 'unitPrice', label: '單價' },
  { value: 'priceDrop', label: '總價' },
  { value: 'postedDate', label: '上架日期' },
]

type PanelType = 'district' | 'price' | 'more'
const panelOpen = ref(false)
const activePanel = ref<PanelType>('district')

function togglePanel(panel: PanelType) {
  if (panelOpen.value && activePanel.value === panel) {
    closePanel()
  } else {
    activePanel.value = panel
    panelOpen.value = true
  }
}

function closePanel() {
  panelOpen.value = false
}

const districtSummary = computed(() => {
  const n = filters.districts.length
  return n === 0 ? '全部地區' : n === 1 ? filters.districts[0] : `${n} 個地區`
})

const priceSummary = computed(() => {
  const { minPrice, maxPrice } = filters
  if (!minPrice && !maxPrice) return '不限總價'
  if (minPrice && maxPrice) return `${minPrice}–${maxPrice} 萬`
  if (minPrice) return `${minPrice} 萬起`
  return `${maxPrice} 萬以下`
})

const condSummary = computed(() => {
  const parts: string[] = []
  if (filters.hasParking) parts.push('有車位')
  if (filters.priceDropped) parts.push('降價')
  if (filters.status === 'delisted') parts.push('下架')
  return parts.length ? parts.join('・') : '全部條件'
})

const hasActiveFilters = computed(() =>
  filters.districts.length > 0 ||
  filters.hasParking !== null ||
  filters.priceDropped !== null ||
  filters.status === 'delisted' ||
  filters.minPrice !== null ||
  filters.maxPrice !== null
)

function onMinPrice(e: Event) {
  const val = (e.target as HTMLInputElement).valueAsNumber
  filters.minPrice = isNaN(val) ? null : val
  filters.page = 1
}

function onMaxPrice(e: Event) {
  const val = (e.target as HTMLInputElement).valueAsNumber
  filters.maxPrice = isNaN(val) ? null : val
  filters.page = 1
}

function onToggleParking() {
  filters.hasParking = filters.hasParking === true ? null : true
  filters.page = 1
}

function onTogglePriceDropped() {
  filters.priceDropped = filters.priceDropped === true ? null : true
  filters.page = 1
}

function onToggleDelisted() {
  filters.status = filters.status === 'delisted' ? 'active' : 'delisted'
  filters.page = 1
}
</script>

<style scoped>
.filter-section {
  margin-bottom: 24px;
  position: relative;
}

/* ===== 膠囊搜尋列 ===== */
.search-capsule {
  display: flex;
  align-items: center;
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  box-shadow: var(--shadow-search);
  overflow: hidden;
  transition: box-shadow 0.2s, border-color 0.2s;
  max-width: 680px;
  margin: 0 auto;
}

.search-capsule--focused {
  border-color: var(--color-fg);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.capsule-seg {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  padding: 10px 20px;
  background: transparent;
  border: none;
  cursor: pointer;
  transition: background 0.15s;
  min-width: 0;
  flex: 1;
}

.capsule-seg:hover {
  background: var(--color-bg-soft);
}

.capsule-seg--wide {
  flex: 1.2;
}

.seg-label {
  font-size: 0.68rem;
  font-weight: 700;
  color: var(--color-fg);
  letter-spacing: 0.04em;
  text-transform: uppercase;
  white-space: nowrap;
}

.seg-value {
  font-size: 0.82rem;
  color: var(--color-fg-2);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}

.capsule-divider {
  width: 1px;
  height: 32px;
  background: var(--color-border);
  flex-shrink: 0;
}

.capsule-search-btn {
  width: 40px;
  height: 40px;
  margin: 4px;
  flex-shrink: 0;
  background: var(--color-rausch);
  color: #fff;
  border: none;
  border-radius: var(--radius-pill);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s, transform 0.1s;
}

.capsule-search-btn:hover {
  background: var(--color-rausch-hover);
}

.capsule-search-btn:active {
  transform: scale(0.93);
}

/* ===== 展開面板 ===== */
.filter-panel {
  position: absolute;
  top: calc(100% + 12px);
  left: 50%;
  transform: translateX(-50%);
  width: min(680px, calc(100vw - 32px));
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: 16px;
  box-shadow: var(--shadow-modal);
  z-index: 50;
  overflow: hidden;
}

.panel-section {
  padding: 20px 24px 0;
}

.panel-title {
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--color-fg);
  letter-spacing: 0.04em;
  text-transform: uppercase;
  margin-bottom: 12px;
}

.mt-4 {
  margin-top: 20px;
}

/* ===== Chip ===== */
.chip-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chip {
  padding: 8px 16px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--color-border);
  background: #fff;
  color: var(--color-fg);
  font-size: 0.82rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}

.chip:hover:not(.chip--active) {
  border-color: var(--color-fg);
}

.chip--active {
  background: var(--color-fg);
  border-color: var(--color-fg);
  color: #fff;
  font-weight: 600;
}

/* ===== 價格輸入 ===== */
.price-inputs {
  display: flex;
  align-items: flex-end;
  gap: 12px;
}

.price-input-wrap {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.input-label {
  font-size: 0.75rem;
  color: var(--color-fg-2);
  font-weight: 500;
}

.price-input {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-input);
  font-size: 0.9rem;
  color: var(--color-fg);
  outline: none;
  transition: border-color 0.15s;
}

.price-input:focus {
  border-color: var(--color-fg);
}

.price-dash {
  color: var(--color-fg-3);
  padding-bottom: 10px;
  font-size: 1.1rem;
}

/* ===== 條件開關 ===== */
.toggle-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.toggle-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-radius: var(--radius-input);
  border: 1px solid var(--color-border);
  background: #fff;
  color: var(--color-fg);
  font-size: 0.9rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
  text-align: left;
}

.toggle-item:hover:not(.toggle-item--active) {
  background: var(--color-bg-soft);
  border-color: var(--color-fg-3);
}

.toggle-item--active {
  background: var(--color-bg-soft);
  border-color: var(--color-fg);
}

.toggle-icon {
  width: 24px;
  height: 24px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: #fff;
}

.toggle-item--active .toggle-icon {
  background: var(--color-fg);
  border-color: var(--color-fg);
  color: #fff;
}

.toggle-item--active .toggle-icon svg {
  stroke: #fff;
}

/* ===== 面板底部 ===== */
.panel-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  border-top: 1px solid var(--color-border-soft);
  margin-top: 20px;
}

.btn-reset {
  background: transparent;
  border: none;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--color-fg);
  text-decoration: underline;
  cursor: pointer;
  padding: 0;
}

.btn-reset:hover {
  color: var(--color-fg-2);
}

.btn-apply {
  padding: 10px 24px;
  background: var(--color-fg);
  color: #fff;
  border: none;
  border-radius: var(--radius-pill);
  font-size: 0.88rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s;
}

.btn-apply:hover {
  background: #444;
}

/* ===== 遮罩 ===== */
.panel-overlay {
  position: fixed;
  inset: 0;
  z-index: 40;
  background: rgba(0, 0, 0, 0.15);
}

/* ===== 作用中篩選標籤列 ===== */
.active-filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
}

.active-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  background: var(--color-bg-soft);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  font-size: 0.8rem;
  font-weight: 500;
  color: var(--color-fg);
}

.tag-remove {
  background: none;
  border: none;
  padding: 0;
  font-size: 1rem;
  line-height: 1;
  color: var(--color-fg-2);
  cursor: pointer;
  display: flex;
  align-items: center;
}

.tag-remove:hover {
  color: var(--color-fg);
}

.clear-all {
  background: transparent;
  border: none;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--color-fg);
  text-decoration: underline;
  cursor: pointer;
  padding: 4px 0;
  margin-left: 4px;
}

/* ===== 動畫 ===== */
.panel-fade-enter-active,
.panel-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.panel-fade-enter-from,
.panel-fade-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(-8px);
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
