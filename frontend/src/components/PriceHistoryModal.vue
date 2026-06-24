<template>
  <div class="modal-overlay" @click.self="$emit('close')">
      <div class="modal-dialog" role="dialog" aria-modal="true" aria-labelledby="modal-title">
        <!-- Header -->
        <header class="modal-header">
          <h3 id="modal-title" class="modal-title">{{ property.title }}</h3>
          <button class="modal-close modal-close-btn" aria-label="關閉" @click="$emit('close')">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </header>

        <!-- Body -->
        <div class="modal-body">
          <PriceHistoryPanel v-if="property.priceHistory.length" :history="property.priceHistory" />
          <p v-else class="modal-empty">尚無歷史價格紀錄</p>
        </div>

        <!-- Footer -->
        <footer class="modal-footer">
          <a
            v-if="property.listingUrl"
            class="modal-visit btn-visit"
            :href="property.listingUrl"
            target="_blank"
            rel="noopener noreferrer"
          >查看物件 →</a>
          <button class="btn-dismiss" @click="$emit('close')">關閉</button>
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
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 16px;
}

.modal-dialog {
  background: #fff;
  border-radius: var(--radius-card);
  width: min(920px, 100%);
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: var(--shadow-modal);
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid var(--color-border-soft);
  flex-shrink: 0;
}

.modal-title {
  font-size: 1rem;
  font-weight: 700;
  color: var(--color-fg);
  margin: 0;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding-right: 16px;
}

.modal-close-btn {
  width: 36px;
  height: 36px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
  color: var(--color-fg-2);
  transition: all 0.15s;
}

.modal-close-btn:hover {
  background: var(--color-bg-soft);
  color: var(--color-fg);
}

.modal-body {
  padding: 24px;
  overflow-y: auto;
  flex: 1;
}

.modal-empty {
  text-align: center;
  color: var(--color-fg-2);
  padding: 40px 24px;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 10px;
  padding: 16px 24px;
  border-top: 1px solid var(--color-border-soft);
  flex-shrink: 0;
}

.btn-visit {
  padding: 10px 20px;
  border-radius: var(--radius-pill);
  background: var(--color-rausch);
  color: #fff;
  font-size: 0.88rem;
  font-weight: 700;
  text-decoration: none;
  transition: background 0.15s;
  white-space: nowrap;
}

.btn-visit:hover {
  background: var(--color-rausch-hover);
}

.btn-dismiss {
  padding: 10px 20px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--color-border);
  background: #fff;
  color: var(--color-fg);
  font-size: 0.88rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}

.btn-dismiss:hover {
  background: var(--color-bg-soft);
}
</style>
