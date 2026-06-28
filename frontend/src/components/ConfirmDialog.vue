<template>
  <div class="modal-overlay" @click.self="$emit('cancel')">
    <div class="modal-dialog" role="alertdialog" aria-modal="true" :aria-labelledby="'dlg-title-' + _uid">
      <header class="modal-header">
        <span class="warn-icon" aria-hidden="true">⚠</span>
        <h3 :id="'dlg-title-' + _uid" class="modal-title">{{ title }}</h3>
        <button class="modal-close-btn" aria-label="取消" @click="$emit('cancel')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
        </button>
      </header>

      <div class="modal-body">
        <p class="message">{{ message }}</p>

        <div v-if="requireText" class="confirm-input-group">
          <label class="confirm-label">請輸入「<strong>{{ requireText }}</strong>」以確認</label>
          <input
            v-model="typed"
            class="confirm-input"
            :placeholder="requireText"
            autocomplete="off"
          />
        </div>
      </div>

      <footer class="modal-footer">
        <button class="btn-cancel" @click="$emit('cancel')">取消</button>
        <button
          class="btn-confirm"
          :disabled="requireText ? typed !== requireText : false"
          @click="$emit('confirm')"
        >{{ confirmLabel }}</button>
      </footer>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, getCurrentInstance } from 'vue'

const props = withDefaults(defineProps<{
  title: string
  message: string
  confirmLabel?: string
  requireText?: string
}>(), {
  confirmLabel: '確認',
})

defineEmits<{ confirm: []; cancel: [] }>()

const _uid = getCurrentInstance()?.uid ?? 0
const typed = ref('')
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
  width: min(480px, 100%);
  display: flex;
  flex-direction: column;
  box-shadow: var(--shadow-modal);
}

.modal-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 24px;
  border-bottom: 1px solid var(--color-border-soft);
}

.warn-icon {
  font-size: 1.25rem;
  color: var(--color-price-down);
  flex-shrink: 0;
}

.modal-title {
  font-size: 1rem;
  font-weight: 700;
  color: var(--color-fg);
  margin: 0;
  flex: 1;
}

.modal-close-btn {
  width: 32px;
  height: 32px;
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
}

.modal-body {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.message {
  color: var(--color-fg);
  line-height: 1.6;
  margin: 0;
  font-size: 0.92rem;
}

.confirm-input-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.confirm-label {
  font-size: 0.84rem;
  color: var(--color-fg-2);
}

.confirm-input {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-input);
  padding: 9px 12px;
  font-size: 0.92rem;
  font-family: inherit;
  outline: none;
  transition: border-color 0.15s;
}

.confirm-input:focus {
  border-color: var(--color-price-down);
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 16px 24px;
  border-top: 1px solid var(--color-border-soft);
}

.btn-cancel {
  padding: 9px 18px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--color-border);
  background: #fff;
  color: var(--color-fg);
  font-size: 0.88rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}

.btn-cancel:hover {
  background: var(--color-bg-soft);
}

.btn-confirm {
  padding: 9px 18px;
  border-radius: var(--radius-pill);
  border: none;
  background: var(--color-price-down);
  color: #fff;
  font-size: 0.88rem;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.15s;
}

.btn-confirm:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.btn-confirm:not(:disabled):hover {
  filter: brightness(0.88);
}
</style>
