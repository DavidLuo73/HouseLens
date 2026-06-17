<template>
  <div class="modal-overlay" @click.self="$emit('close')">
    <div class="modal-dialog" role="dialog" aria-modal="true">
      <header class="modal-header">
        <h3 class="modal-title">{{ property.title }}</h3>
        <button class="modal-x" aria-label="關閉" @click="$emit('close')">×</button>
      </header>

      <div class="modal-body">
        <PriceHistoryPanel v-if="property.priceHistory.length" :history="property.priceHistory" />
        <p v-else class="modal-empty">尚無歷史價格紀錄</p>
      </div>

      <footer class="modal-footer">
        <a
          v-if="property.listingUrl"
          class="modal-visit"
          :href="property.listingUrl"
          target="_blank"
          rel="noopener noreferrer"
        >查看商品</a>
        <button class="modal-close" @click="$emit('close')">關閉</button>
      </footer>
    </div>
  </div>
</template>

<script setup lang="ts">
import PriceHistoryPanel from '@/components/PriceHistoryPanel.vue'
import type { Property } from '@/services/api'

defineProps<{ property: Property }>()
defineEmits<{ close: [] }>()
</script>

<style scoped>
.modal-overlay {
  position: fixed; inset: 0; background: rgba(0, 0, 0, 0.45);
  display: flex; align-items: center; justify-content: center; z-index: 1000; padding: 1rem;
}
.modal-dialog {
  background: #fff; border-radius: 12px; width: min(920px, 100%);
  max-height: 90vh; display: flex; flex-direction: column; overflow: hidden;
}
.modal-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 1rem 1.25rem; border-bottom: 1px solid #e5e7eb;
}
.modal-title { margin: 0; font-size: 1.1rem; }
.modal-x { border: none; background: none; font-size: 1.5rem; line-height: 1; cursor: pointer; color: #6b7280; }
.modal-body { padding: 1.25rem; overflow-y: auto; }
.modal-empty { text-align: center; color: #6b7280; padding: 2rem; }
.modal-footer {
  display: flex; justify-content: flex-end; gap: 0.75rem;
  padding: 0.85rem 1.25rem; border-top: 1px solid #e5e7eb;
}
.modal-visit {
  padding: 0.45rem 1.1rem; border-radius: 8px; background: #06b6d4; color: #fff;
  font-size: 0.9rem; font-weight: 600; text-decoration: none;
}
.modal-close {
  padding: 0.45rem 1.1rem; border-radius: 8px; border: 1.5px solid #d1d5db;
  background: #fff; color: #374151; font-size: 0.9rem; font-weight: 600; cursor: pointer;
}
</style>
