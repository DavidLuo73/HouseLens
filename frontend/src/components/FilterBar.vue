<template>
  <div class="filter-bar">
    <div class="filter-row">
      <span class="filter-label">行政區</span>
      <div class="district-chips">
        <button
          v-for="d in availableDistricts"
          :key="d"
          class="chip"
          :class="{ 'chip--active': filters.districts.includes(d) }"
          @click="filters.toggleDistrict(d)"
        >
          {{ d }}
        </button>
      </div>
    </div>

    <div class="filter-row filter-row--wrap">
      <div class="filter-item">
        <span class="filter-label">總價 (萬)</span>
        <div class="price-range">
          <input
            type="number"
            :value="filters.minPrice ?? ''"
            placeholder="最低"
            class="price-input"
            min="0"
            @change="onMinPrice"
          />
          <span class="range-sep">–</span>
          <input
            type="number"
            :value="filters.maxPrice ?? ''"
            placeholder="最高"
            class="price-input"
            min="0"
            @change="onMaxPrice"
          />
        </div>
      </div>

      <button
        class="toggle-chip"
        :class="{ 'toggle-chip--active': filters.hasParking === true }"
        @click="onToggleParking"
      >
        <span class="toggle-icon">{{ filters.hasParking ? '✓' : '+' }}</span> 有車位
      </button>

      <button
        class="toggle-chip"
        :class="{ 'toggle-chip--active': filters.priceDropped === true }"
        @click="onTogglePriceDropped"
      >
        <span class="toggle-icon">{{ filters.priceDropped ? '✓' : '+' }}</span> 近期降價
      </button>

      <div class="filter-item">
        <span class="filter-label">排序</span>
        <select :value="filters.sortBy" class="sort-select" @change="onSortChange">
          <option value="score">評分（預設）</option>
          <option value="unitPrice">單價</option>
          <option value="priceDrop">總價</option>
          <option value="postedDate">上架日期</option>
        </select>
      </div>

      <button
        class="toggle-chip toggle-chip--delisted"
        :class="{ 'toggle-chip--active': filters.status === 'delisted' }"
        @click="onToggleDelisted"
      >
        <span class="toggle-icon">{{ filters.status === 'delisted' ? '✓' : '+' }}</span> 僅顯示下架
      </button>

      <button class="reset-btn" @click="filters.reset()">重設篩選</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useFiltersStore } from '@/stores/filters'

defineProps<{
  availableDistricts: string[]
}>()

const filters = useFiltersStore()

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

function onSortChange(e: Event) {
  filters.sortBy = (e.target as HTMLSelectElement).value
  filters.page = 1
}

function onToggleDelisted() {
  filters.status = filters.status === 'delisted' ? 'active' : 'delisted'
  filters.page = 1
}
</script>

<style scoped>
.filter-bar {
  background: #f8faff;
  border: 1px solid #c7d7f5;
  border-radius: 10px;
  padding: 1rem 1.25rem;
  margin-bottom: 1.25rem;
}
.filter-row {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 0.75rem;
}
.filter-row--wrap {
  flex-wrap: wrap;
  align-items: center;
  margin-bottom: 0;
}
.filter-label {
  font-size: 0.8rem;
  color: #4338ca;
  white-space: nowrap;
  font-weight: 600;
  letter-spacing: 0.02em;
}
.district-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}
.chip {
  padding: 0.25rem 0.75rem;
  border-radius: 20px;
  border: 1.5px solid #c7d2fe;
  background: #ffffff;
  color: #3730a3;
  font-size: 0.8rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}
.chip--active {
  background: #4f46e5;
  border-color: #4f46e5;
  color: #ffffff;
  font-weight: 600;
  box-shadow: 0 1px 4px rgba(79, 70, 229, 0.3);
}
.chip:hover:not(.chip--active) {
  background: #eef2ff;
  border-color: #818cf8;
  color: #3730a3;
}
.filter-item {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}
.price-range {
  display: flex;
  align-items: center;
  gap: 0.3rem;
}
.price-input {
  width: 72px;
  padding: 0.28rem 0.45rem;
  border: 1.5px solid #c7d2fe;
  border-radius: 6px;
  font-size: 0.82rem;
  text-align: right;
  color: #1e1b4b;
  background: #ffffff;
  outline: none;
}
.price-input:focus {
  border-color: #4f46e5;
}
.range-sep {
  color: #6366f1;
  font-size: 0.85rem;
}
.toggle-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.25rem 0.75rem;
  border-radius: 20px;
  border: 1.5px solid #c7d2fe;
  background: #ffffff;
  color: #3730a3;
  font-size: 0.8rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
  user-select: none;
}
.toggle-chip:hover:not(.toggle-chip--active) {
  background: #eef2ff;
  border-color: #818cf8;
}
.toggle-chip--active {
  background: #4f46e5;
  border-color: #4f46e5;
  color: #ffffff;
  font-weight: 600;
  box-shadow: 0 1px 4px rgba(79, 70, 229, 0.3);
}
.toggle-chip--delisted.toggle-chip--active {
  background: #6b7280;
  border-color: #6b7280;
  box-shadow: 0 1px 4px rgba(107, 114, 128, 0.3);
}
.toggle-icon {
  font-size: 0.75rem;
  line-height: 1;
}
.sort-select {
  padding: 0.28rem 0.5rem;
  border: 1.5px solid #c7d2fe;
  border-radius: 6px;
  font-size: 0.82rem;
  background: #ffffff;
  color: #1e1b4b;
  outline: none;
  cursor: pointer;
}
.sort-select:focus {
  border-color: #4f46e5;
}
.reset-btn {
  padding: 0.28rem 0.85rem;
  background: #ffffff;
  border: 1.5px solid #c7d2fe;
  border-radius: 6px;
  font-size: 0.82rem;
  cursor: pointer;
  color: #4338ca;
  font-weight: 500;
  margin-left: auto;
  transition: all 0.15s;
}
.reset-btn:hover {
  background: #eef2ff;
  border-color: #818cf8;
}
</style>
