<template>
  <div class="config-page">
    <div class="page-inner">
      <div class="page-header">
        <h1>評分權重設定</h1>
        <p class="page-subtitle">調整各因子在綜合評分中的影響比重</p>
      </div>

      <div v-if="loading" class="state-box">
        <div class="loader" />
        <p>載入中…</p>
      </div>
      <div v-else-if="loadError" class="state-box state-box--error">{{ loadError }}</div>

      <form v-else class="config-form" @submit.prevent="save">
        <!-- 權重設定卡片 -->
        <div class="config-card">
          <h2 class="card-title">評分因子權重</h2>
          <p class="card-desc">四項因子合計必須為 1.0</p>

          <div class="weight-list">
            <div class="weight-row">
              <label class="weight-label">單價（相對行情）</label>
              <input
                v-model.number="form.scoring.weightUnitPrice"
                type="number" step="0.01" min="0" max="1"
                class="weight-input"
                @input="resetSaveState"
              />
            </div>
            <div class="weight-row">
              <label class="weight-label">屋齡</label>
              <input
                v-model.number="form.scoring.weightAge"
                type="number" step="0.01" min="0" max="1"
                class="weight-input"
                @input="resetSaveState"
              />
            </div>
            <div class="weight-row">
              <label class="weight-label">車位</label>
              <input
                v-model.number="form.scoring.weightParking"
                type="number" step="0.01" min="0" max="1"
                class="weight-input"
                @input="resetSaveState"
              />
            </div>
            <div class="weight-row">
              <label class="weight-label">地段</label>
              <input
                v-model.number="form.scoring.weightLocation"
                type="number" step="0.01" min="0" max="1"
                class="weight-input"
                @input="resetSaveState"
              />
            </div>
          </div>

          <div class="weight-sum" :class="weightSumOk ? 'weight-sum--ok' : 'weight-sum--error'">
            合計：{{ weightSum.toFixed(2) }}
            <span v-if="!weightSumOk" class="sum-hint">（需調整至 1.00）</span>
          </div>
        </div>

        <!-- 大降價門檻卡片 -->
        <div class="config-card">
          <h2 class="card-title">大降價門檻</h2>
          <p class="card-desc">符合任一條件即標記為「大降價」</p>

          <div class="weight-list">
            <div class="weight-row">
              <label class="weight-label">降幅（%）</label>
              <input
                v-model.number="bigDropPercentDisplay"
                type="number" step="0.1" min="0" max="100"
                class="weight-input"
              />
            </div>
            <div class="weight-row">
              <label class="weight-label">降幅（萬）</label>
              <input
                v-model.number="form.scoring.bigDropAmount"
                type="number" step="1" min="0"
                class="weight-input"
              />
            </div>
          </div>
        </div>

        <!-- 操作列 -->
        <div class="form-footer">
          <transition name="fade">
            <span v-if="saveError" class="feedback feedback--error">{{ saveError }}</span>
            <span v-else-if="saveSuccess" class="feedback feedback--ok">✓ 設定已儲存</span>
          </transition>
          <button type="submit" class="btn-save" :disabled="saving || !weightSumOk">
            {{ saving ? '儲存中…' : '儲存設定' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { api, type AppConfig } from '@/services/api'

const loading = ref(true)
const loadError = ref<string | null>(null)
const saving = ref(false)
const saveError = ref<string | null>(null)
const saveSuccess = ref(false)

const form = ref<AppConfig>({
  tracking: { districts: [], maxTotalPrice: 800 },
  scoring: {
    weightUnitPrice: 0.40,
    weightAge: 0.25,
    weightParking: 0.20,
    weightLocation: 0.15,
    bigDropPercent: 0.05,
    bigDropAmount: 30,
  },
})

const bigDropPercentDisplay = computed({
  get: () => +(form.value.scoring.bigDropPercent * 100).toFixed(1),
  set: (v: number) => { form.value.scoring.bigDropPercent = v / 100 },
})

const weightSum = computed(() =>
  form.value.scoring.weightUnitPrice +
  form.value.scoring.weightAge +
  form.value.scoring.weightParking +
  form.value.scoring.weightLocation
)

const weightSumOk = computed(() => Math.abs(weightSum.value - 1.0) < 0.001)

function resetSaveState() {
  saveError.value = null
  saveSuccess.value = false
}

async function save() {
  if (!weightSumOk.value) {
    saveError.value = `權重合計為 ${weightSum.value.toFixed(2)}，必須為 1.00`
    return
  }
  saving.value = true
  saveError.value = null
  saveSuccess.value = false
  try {
    await api.config.put(form.value)
    saveSuccess.value = true
  } catch (e: unknown) {
    saveError.value = e instanceof Error ? e.message : '儲存失敗'
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  try {
    const config = await api.config.get()
    form.value = config
  } catch (e: unknown) {
    loadError.value = e instanceof Error ? e.message : '載入失敗'
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.config-page {
  background: var(--color-bg-soft);
  min-height: 100vh;
}

.page-inner {
  max-width: 560px;
  margin: 0 auto;
  padding: 32px 24px 64px;
}

@media (max-width: 640px) {
  .page-inner { padding: 20px 16px 48px; }
}

.page-header { margin-bottom: 28px; }

h1 {
  font-size: 1.6rem;
  font-weight: 800;
  color: var(--color-fg);
  letter-spacing: -0.3px;
  margin-bottom: 6px;
}

.page-subtitle {
  font-size: 0.88rem;
  color: var(--color-fg-2);
}

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

.config-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.config-card {
  background: #fff;
  border-radius: var(--radius-card);
  border: 1px solid var(--color-border-soft);
  padding: 24px;
}

.card-title {
  font-size: 1rem;
  font-weight: 700;
  color: var(--color-fg);
  margin: 0 0 4px;
}

.card-desc {
  font-size: 0.82rem;
  color: var(--color-fg-2);
  margin: 0 0 20px;
}

.weight-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.weight-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.weight-label {
  font-size: 0.9rem;
  font-weight: 500;
  color: var(--color-fg);
  flex: 1;
}

.weight-input {
  width: 96px;
  padding: 8px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-input);
  font-size: 0.9rem;
  text-align: right;
  color: var(--color-fg);
  outline: none;
  transition: border-color 0.15s;
}

.weight-input:focus {
  border-color: var(--color-fg);
}

.weight-sum {
  margin-top: 16px;
  padding: 10px 14px;
  border-radius: var(--radius-input);
  font-size: 0.88rem;
  font-weight: 700;
}

.weight-sum--ok {
  background: var(--color-badge-parking-bg);
  color: var(--color-badge-parking-text);
}

.weight-sum--error {
  background: var(--color-badge-drop-bg);
  color: var(--color-price-down);
}

.sum-hint {
  font-weight: 400;
  margin-left: 4px;
}

.form-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 16px;
}

.feedback {
  font-size: 0.85rem;
  font-weight: 600;
}

.feedback--error { color: var(--color-price-down); }
.feedback--ok { color: var(--color-badge-parking-text); }

.btn-save {
  padding: 12px 28px;
  background: var(--color-rausch);
  color: #fff;
  border: none;
  border-radius: var(--radius-pill);
  font-size: 0.9rem;
  font-weight: 700;
  cursor: pointer;
  transition: background 0.15s;
}

.btn-save:hover:not(:disabled) {
  background: var(--color-rausch-hover);
}

.btn-save:disabled {
  background: var(--color-border);
  color: var(--color-fg-3);
  cursor: not-allowed;
}

.fade-enter-active, .fade-leave-active { transition: opacity 0.2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
