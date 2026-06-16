<template>
  <div class="scoring-config">
    <h1>評分權重設定</h1>

    <div v-if="loading" class="loading">載入中…</div>
    <div v-else-if="loadError" class="error">{{ loadError }}</div>

    <template v-else>
      <form class="config-form" @submit.prevent="save">
        <section class="section">
          <h2 class="section-title">評分因子權重（合計必須為 1.0）</h2>

          <div class="weight-grid">
            <div class="weight-row">
              <label class="weight-label">單價（相對行情）</label>
              <input
                v-model.number="form.scoring.weightUnitPrice"
                type="number"
                step="0.01"
                min="0"
                max="1"
                class="weight-input"
                @input="resetSaveError"
              />
            </div>
            <div class="weight-row">
              <label class="weight-label">屋齡</label>
              <input
                v-model.number="form.scoring.weightAge"
                type="number"
                step="0.01"
                min="0"
                max="1"
                class="weight-input"
                @input="resetSaveError"
              />
            </div>
            <div class="weight-row">
              <label class="weight-label">車位</label>
              <input
                v-model.number="form.scoring.weightParking"
                type="number"
                step="0.01"
                min="0"
                max="1"
                class="weight-input"
                @input="resetSaveError"
              />
            </div>
            <div class="weight-row">
              <label class="weight-label">地段</label>
              <input
                v-model.number="form.scoring.weightLocation"
                type="number"
                step="0.01"
                min="0"
                max="1"
                class="weight-input"
                @input="resetSaveError"
              />
            </div>
          </div>

          <div class="weight-sum" :class="weightSumClass">
            合計：{{ weightSum.toFixed(2) }}
            <span v-if="!weightSumOk" class="sum-error">（必須為 1.00）</span>
          </div>
        </section>

        <section class="section">
          <h2 class="section-title">大降價門檻</h2>
          <div class="threshold-grid">
            <div class="threshold-row">
              <label class="weight-label">降幅（%）</label>
              <input
                v-model.number="bigDropPercentDisplay"
                type="number"
                step="0.1"
                min="0"
                max="100"
                class="weight-input"
              />
            </div>
            <div class="threshold-row">
              <label class="weight-label">降幅（萬）</label>
              <input
                v-model.number="form.scoring.bigDropAmount"
                type="number"
                step="1"
                min="0"
                class="weight-input"
              />
            </div>
          </div>
        </section>

        <div v-if="saveError" class="save-error">{{ saveError }}</div>
        <div v-if="saveSuccess" class="save-success">設定已儲存</div>

        <div class="form-actions">
          <button type="submit" class="save-btn" :disabled="saving || !weightSumOk">
            {{ saving ? '儲存中…' : '儲存設定' }}
          </button>
        </div>
      </form>
    </template>
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

const weightSumClass = computed(() =>
  weightSumOk.value ? 'weight-sum--ok' : 'weight-sum--error'
)

function resetSaveError() {
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
.scoring-config {
  max-width: 600px;
  margin: 0 auto;
  padding: 1.5rem;
}
h1 {
  font-size: 1.6rem;
  margin-bottom: 1.5rem;
}
.loading,
.error {
  text-align: center;
  padding: 3rem;
  color: #6b7280;
}
.error {
  color: #dc2626;
}
.section {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 1.25rem;
  margin-bottom: 1.25rem;
}
.section-title {
  font-size: 0.95rem;
  font-weight: 600;
  color: #374151;
  margin: 0 0 1rem;
}
.weight-grid,
.threshold-grid {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
}
.weight-row,
.threshold-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}
.weight-label {
  font-size: 0.9rem;
  color: #374151;
  flex: 1;
}
.weight-input {
  width: 90px;
  padding: 0.3rem 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 0.9rem;
  text-align: right;
}
.weight-sum {
  margin-top: 0.85rem;
  padding: 0.5rem 0.75rem;
  border-radius: 6px;
  font-size: 0.88rem;
  font-weight: 600;
}
.weight-sum--ok {
  background: #d1fae5;
  color: #065f46;
}
.weight-sum--error {
  background: #fee2e2;
  color: #991b1b;
}
.sum-error {
  margin-left: 0.4rem;
  font-weight: 400;
}
.form-actions {
  display: flex;
  justify-content: flex-end;
}
.save-btn {
  padding: 0.5rem 1.5rem;
  background: #2563eb;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
}
.save-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.save-btn:not(:disabled):hover {
  background: #1d4ed8;
}
.save-error {
  color: #dc2626;
  font-size: 0.88rem;
  margin-bottom: 0.75rem;
}
.save-success {
  color: #059669;
  font-size: 0.88rem;
  margin-bottom: 0.75rem;
  font-weight: 500;
}
</style>
