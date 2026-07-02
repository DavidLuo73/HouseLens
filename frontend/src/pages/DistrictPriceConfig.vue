<template>
  <div class="dconfig-page">
    <div class="page-inner">
      <div class="page-header">
        <h1>地區價格設定</h1>
        <p class="page-subtitle">設定各縣市地區的撈取價格上限，啟用 / 停用追蹤</p>
      </div>

      <div v-if="loading" class="state-box">
        <div class="loader" />
        <p>載入中…</p>
      </div>
      <div v-else-if="error" class="state-box state-box--error">{{ error }}</div>

      <template v-else>
        <!-- 新增表單 -->
        <div class="add-card">
          <h2 class="card-title">新增地區</h2>
          <div class="add-row">
            <div class="field">
              <label class="field-label">縣市</label>
              <select v-model="form.city" class="field-input">
                <option value="新北市">新北市</option>
                <option value="桃園市">桃園市</option>
                <option value="台北市">台北市</option>
                <option value="基隆市">基隆市</option>
                <option value="新竹市">新竹市</option>
                <option value="新竹縣">新竹縣</option>
              </select>
            </div>
            <div class="field">
              <label class="field-label">地區</label>
              <input v-model="form.district" placeholder="例：永和區" class="field-input" />
            </div>
            <div class="field">
              <label class="field-label">最高總價（萬）</label>
              <input v-model.number="form.maxTotalPrice" type="number" min="100" step="50" class="field-input field-input--price" />
            </div>
            <button class="btn-add" :disabled="saving" @click="addDistrict">
              {{ saving ? '儲存中…' : '+ 新增' }}
            </button>
          </div>
          <p v-if="addError" class="field-error">{{ addError }}</p>
        </div>

        <!-- 地區列表 -->
        <div class="table-card">
          <div class="table-scroll">
            <table class="config-table">
              <thead>
                <tr>
                  <th>縣市</th>
                  <th>地區</th>
                  <th>最高總價（萬）</th>
                  <th>追蹤狀態</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="!districts.length">
                  <td colspan="5" class="td-empty">尚未設定任何地區</td>
                </tr>
                <template v-for="d in districts" :key="d.id">
                  <tr :class="{ 'row--disabled': !d.isEnabled }">
                    <td>
                      <span v-if="editingId !== d.id">{{ d.city }}</span>
                      <select v-else v-model="editForm.city" class="inline-input">
                        <option value="新北市">新北市</option>
                        <option value="桃園市">桃園市</option>
                        <option value="台北市">台北市</option>
                        <option value="基隆市">基隆市</option>
                        <option value="新竹市">新竹市</option>
                        <option value="新竹縣">新竹縣</option>
                      </select>
                    </td>
                    <td>
                      <span v-if="editingId !== d.id">{{ d.district }}</span>
                      <input v-else v-model="editForm.district" class="inline-input" />
                    </td>
                    <td>
                      <span v-if="editingId !== d.id">{{ d.maxTotalPrice }} 萬</span>
                      <input v-else v-model.number="editForm.maxTotalPrice" type="number" min="100" step="50" class="inline-input inline-input--price" />
                    </td>
                    <td>
                      <button class="toggle-btn" :class="d.isEnabled ? 'toggle--on' : 'toggle--off'" @click="toggle(d)">
                        {{ d.isEnabled ? '追蹤中' : '已停用' }}
                      </button>
                    </td>
                    <td class="td-actions">
                      <template v-if="editingId === d.id">
                        <button class="action-btn action-btn--save" @click="saveEdit(d)">儲存</button>
                        <button class="action-btn" @click="editingId = null">取消</button>
                      </template>
                      <template v-else>
                        <button class="action-btn" @click="startEdit(d)">編輯</button>
                        <button class="action-btn action-btn--danger" @click="remove(d)">刪除</button>
                      </template>
                    </td>
                  </tr>
                </template>
              </tbody>
            </table>
          </div>
        </div>

        <!-- 平台篩選設定：上方地區＋價格為全平台共用，這裡是各平台自己的額外條件 -->
        <div class="add-card platform-card">
          <h2 class="card-title">平台篩選設定</h2>
          <p class="card-subtitle">地區與最高總價為所有平台共用；以下為各平台專屬的額外篩選條件</p>

          <div class="platform-tabs">
            <button
              v-for="s in SOURCE_SITES"
              :key="s.value"
              class="platform-tab"
              :class="{ 'platform-tab--active': activePlatform === s.value }"
              @click="activePlatform = s.value"
            >
              {{ s.label }}
              <span v-if="s.value === 'Rakuya'" class="tab-dot" title="支援額外篩選" />
            </button>
          </div>

          <!-- 樂屋網：完整篩選選項 -->
          <div v-if="activePlatform === 'Rakuya'" class="platform-panel">
            <div class="add-row">
              <div class="field">
                <label class="field-label">最小坪數（0 = 不限）</label>
                <input v-model.number="rakuyaForm.minSizePing" type="number" min="0" step="1" class="field-input field-input--price" />
              </div>
              <div class="field">
                <label class="field-label">用途</label>
                <select v-model="rakuyaForm.useCode" class="field-input">
                  <option v-for="u in USE_OPTIONS" :key="u.code" :value="u.code">{{ u.label }}</option>
                </select>
              </div>
            </div>
            <div class="add-row">
              <div class="field">
                <label class="field-label">建物型態</label>
                <div class="check-group">
                  <label v-for="t in TYPE_OPTIONS" :key="t.code" class="check-item">
                    <input type="checkbox" :value="t.code" v-model="rakuyaForm.typeCodes" />
                    {{ t.label }}
                  </label>
                </div>
              </div>
              <div class="field">
                <label class="field-label">房數（不勾 = 不限）</label>
                <div class="check-group">
                  <label v-for="r in ROOM_OPTIONS" :key="r.code" class="check-item">
                    <input type="checkbox" :value="r.code" v-model="rakuyaForm.rooms" />
                    {{ r.label }}
                  </label>
                </div>
              </div>
            </div>
            <div class="platform-actions">
              <button class="btn-add" :disabled="filterSaving" @click="savePlatformFilter">
                {{ filterSaving ? '儲存中…' : '儲存樂屋網篩選' }}
              </button>
              <span v-if="filterSavedAt" class="save-hint">已儲存 ✓</span>
            </div>
            <p v-if="filterError" class="field-error">{{ filterError }}</p>
          </div>

          <!-- 其他平台：目前無額外篩選 -->
          <div v-else class="platform-panel platform-panel--empty">
            {{ SOURCE_LABELS[activePlatform] }} 目前不支援額外篩選，使用上方共用的地區與最高總價設定。
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api, type DistrictConfig } from '@/services/api'
import { SOURCE_LABELS, SOURCE_SITES } from '@/constants/sources'

const districts = ref<DistrictConfig[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const saving = ref(false)
const addError = ref<string | null>(null)
const editingId = ref<number | null>(null)

const form = ref({ city: '新北市', district: '', maxTotalPrice: 800 })
const editForm = ref({ city: '', district: '', maxTotalPrice: 800 })

// ===== 平台篩選（樂屋網 URL 參數代碼，取自其搜尋頁篩選器）=====
const TYPE_OPTIONS = [
  { code: 'R1', label: '公寓' },
  { code: 'R2', label: '大樓/華廈' },
  { code: 'R3', label: '套房' },
  { code: 'R4', label: '別墅' },
  { code: 'R5', label: '透天厝' },
  { code: 'R6', label: '樓中樓' },
]
const USE_OPTIONS = [
  { code: '1', label: '住宅' },
  { code: '2', label: '商用' },
  { code: '6', label: '住辦' },
  { code: '3', label: '車位' },
]
const ROOM_OPTIONS = [
  { code: '1', label: '1房' },
  { code: '2', label: '2房' },
  { code: '3', label: '3房' },
  { code: '4', label: '4房' },
  { code: '5~', label: '5房以上' },
]

const activePlatform = ref('Rakuya')
const filterSaving = ref(false)
const filterError = ref<string | null>(null)
const filterSavedAt = ref(false)

const rakuyaForm = ref({
  minSizePing: 0,
  useCode: '1',
  typeCodes: ['R1', 'R2'] as string[],
  rooms: [] as string[],
})

const splitCodes = (s: string) => (s ? s.split(',').map((x) => x.trim()).filter(Boolean) : [])

async function load() {
  loading.value = true
  error.value = null
  try {
    const [ds, filters] = await Promise.all([api.districts.list(), api.platformFilters.list()])
    districts.value = ds
    const rakuya = filters.find((f) => f.sourceSite === 'Rakuya')
    if (rakuya) {
      rakuyaForm.value = {
        minSizePing: rakuya.minSizePing ?? 0,
        useCode: rakuya.useCode || '1',
        typeCodes: splitCodes(rakuya.typeCodes || 'R1,R2'),
        rooms: splitCodes(rakuya.rooms || ''),
      }
    }
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '載入失敗'
  } finally {
    loading.value = false
  }
}

async function savePlatformFilter() {
  filterError.value = null
  filterSavedAt.value = false
  filterSaving.value = true
  try {
    await api.platformFilters.update('Rakuya', {
      minSizePing: rakuyaForm.value.minSizePing,
      rooms: rakuyaForm.value.rooms.join(','),
      typeCodes: rakuyaForm.value.typeCodes.join(','),
      useCode: rakuyaForm.value.useCode,
    })
    filterSavedAt.value = true
  } catch (e: unknown) {
    filterError.value = e instanceof Error ? e.message : '儲存失敗'
  } finally {
    filterSaving.value = false
  }
}

async function addDistrict() {
  addError.value = null
  if (!form.value.district.trim()) { addError.value = '請輸入地區名稱'; return }
  if (form.value.maxTotalPrice <= 0) { addError.value = '總價上限必須大於 0'; return }
  saving.value = true
  try {
    const created = await api.districts.create({
      city: form.value.city,
      district: form.value.district.trim(),
      maxTotalPrice: form.value.maxTotalPrice,
      isEnabled: true,
    })
    districts.value.push(created)
    form.value.district = ''
    form.value.maxTotalPrice = 800
  } catch (e: unknown) {
    addError.value = e instanceof Error ? e.message : '新增失敗'
  } finally {
    saving.value = false
  }
}

function startEdit(d: DistrictConfig) {
  editingId.value = d.id
  editForm.value = { city: d.city, district: d.district, maxTotalPrice: d.maxTotalPrice }
}

async function saveEdit(d: DistrictConfig) {
  try {
    const updated = await api.districts.update(d.id, {
      city: editForm.value.city,
      district: editForm.value.district,
      maxTotalPrice: editForm.value.maxTotalPrice,
      isEnabled: d.isEnabled,
    })
    const idx = districts.value.findIndex((x) => x.id === d.id)
    if (idx !== -1) districts.value[idx] = updated
    editingId.value = null
  } catch (e: unknown) {
    alert(e instanceof Error ? e.message : '儲存失敗')
  }
}

async function toggle(d: DistrictConfig) {
  try {
    const result = await api.districts.toggle(d.id)
    d.isEnabled = result.isEnabled
  } catch (e: unknown) {
    alert(e instanceof Error ? e.message : '切換失敗')
  }
}

async function remove(d: DistrictConfig) {
  if (!confirm(`確定刪除「${d.city}${d.district}」？`)) return
  try {
    await api.districts.delete(d.id)
    districts.value = districts.value.filter((x) => x.id !== d.id)
  } catch (e: unknown) {
    alert(e instanceof Error ? e.message : '刪除失敗')
  }
}

onMounted(load)
</script>

<style scoped>
.dconfig-page {
  background: var(--color-bg-soft);
  min-height: 100vh;
}

.page-inner {
  max-width: 960px;
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

/* ===== 新增卡片 ===== */
.add-card {
  background: #fff;
  border-radius: var(--radius-card);
  border: 1px solid var(--color-border-soft);
  padding: 24px;
  margin-bottom: 20px;
}

.card-title {
  font-size: 1rem;
  font-weight: 700;
  color: var(--color-fg);
  margin: 0 0 16px;
}

.card-subtitle {
  font-size: 0.82rem;
  color: var(--color-fg-2);
  margin: -10px 0 16px;
}

.add-row {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  flex-wrap: wrap;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-label {
  font-size: 0.72rem;
  font-weight: 700;
  color: var(--color-fg-2);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.field-input {
  padding: 9px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-input);
  font-size: 0.9rem;
  color: var(--color-fg);
  background: #fff;
  outline: none;
  min-width: 120px;
  transition: border-color 0.15s;
}

.field-input--price { width: 120px; }

.field-input:focus { border-color: var(--color-fg); }

.field-error {
  margin-top: 8px;
  font-size: 0.82rem;
  color: var(--color-price-down);
}

.btn-add {
  padding: 9px 20px;
  background: var(--color-fg);
  color: #fff;
  border: none;
  border-radius: var(--radius-pill);
  font-size: 0.88rem;
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.15s;
  align-self: flex-end;
}

.btn-add:hover:not(:disabled) { background: #444; }
.btn-add:disabled { background: var(--color-border); color: var(--color-fg-3); cursor: not-allowed; }

/* ===== 平台篩選 ===== */
.platform-card { margin-top: 4px; }

.platform-tabs {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.platform-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  background: #fff;
  color: var(--color-fg);
  font-size: 0.82rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}

.platform-tab:hover { background: var(--color-bg-soft); }

.platform-tab--active {
  background: var(--color-fg);
  color: #fff;
  border-color: var(--color-fg);
}

.tab-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-rausch, #FF5A5F);
  display: inline-block;
}

.platform-panel { display: flex; flex-direction: column; gap: 12px; }

.platform-panel--empty {
  font-size: 0.86rem;
  color: var(--color-fg-2);
  padding: 20px 0 8px;
}

.platform-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.save-hint {
  font-size: 0.82rem;
  color: var(--color-badge-parking-text, #1a7f4b);
  font-weight: 600;
}

.check-group {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
  padding: 6px 0;
}

.check-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 0.85rem;
  color: var(--color-fg);
  cursor: pointer;
  white-space: nowrap;
}

.check-item input { accent-color: var(--color-rausch, #FF5A5F); cursor: pointer; }

/* ===== 表格卡片 ===== */
.table-card {
  background: #fff;
  border-radius: var(--radius-card);
  border: 1px solid var(--color-border-soft);
  overflow: hidden;
  margin-bottom: 20px;
}

.table-scroll { overflow-x: auto; }

.config-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.88rem;
}

.config-table th {
  background: var(--color-bg-soft);
  padding: 12px 16px;
  text-align: left;
  font-size: 0.72rem;
  font-weight: 700;
  color: var(--color-fg-2);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  border-bottom: 1px solid var(--color-border-soft);
  white-space: nowrap;
}

.config-table td {
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-border-soft);
  vertical-align: middle;
  color: var(--color-fg);
}

.config-table tr:last-child td { border-bottom: none; }
.config-table tr:hover td { background: var(--color-bg-soft); }

.td-empty {
  text-align: center;
  color: var(--color-fg-3);
  padding: 32px !important;
}

.row--disabled td { color: var(--color-fg-3); }

.inline-input {
  padding: 6px 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-input);
  font-size: 0.88rem;
  color: var(--color-fg);
  outline: none;
  width: 100%;
}

.inline-input--price { width: 90px; }

.toggle-btn {
  padding: 4px 12px;
  border-radius: var(--radius-pill);
  border: none;
  font-size: 0.75rem;
  font-weight: 700;
  cursor: pointer;
  transition: opacity 0.15s;
  white-space: nowrap;
}

.toggle--on { background: var(--color-badge-parking-bg); color: var(--color-badge-parking-text); }
.toggle--off { background: var(--color-badge-delisted-bg); color: var(--color-badge-delisted-text); }
.toggle-btn:hover { opacity: 0.75; }

.td-actions { display: flex; gap: 6px; align-items: center; }

.action-btn {
  padding: 5px 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  background: #fff;
  color: var(--color-fg);
  font-size: 0.78rem;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s;
}

.action-btn:hover { background: var(--color-bg-soft); }
.action-btn--save { background: var(--color-fg); color: #fff; border-color: var(--color-fg); }
.action-btn--save:hover { background: #444; }
.action-btn--danger { color: var(--color-price-down); border-color: #FECACA; }
.action-btn--danger:hover { background: var(--color-badge-drop-bg); }
</style>
