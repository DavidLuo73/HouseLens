<template>
  <div class="page">
    <div class="page-header">
      <h1>地區價格設定</h1>
      <p class="subtitle">設定各縣市地區的撈取價格上限，啟用/停用追蹤</p>
    </div>

    <div v-if="loading" class="loading">載入中…</div>
    <div v-else-if="error" class="error">{{ error }}</div>

    <template v-else>
      <!-- 新增表單 -->
      <div class="add-card">
        <h2>新增地區</h2>
        <div class="form-row">
          <div class="form-group">
            <label>縣市</label>
            <select v-model="form.city">
              <option value="新北市">新北市</option>
              <option value="桃園市">桃園市</option>
              <option value="台北市">台北市</option>
              <option value="基隆市">基隆市</option>
              <option value="新竹市">新竹市</option>
              <option value="新竹縣">新竹縣</option>
            </select>
          </div>
          <div class="form-group">
            <label>地區</label>
            <input v-model="form.district" placeholder="例：永和區" />
          </div>
          <div class="form-group">
            <label>最高總價（萬）</label>
            <input v-model.number="form.maxTotalPrice" type="number" min="100" step="50" />
          </div>
          <button class="btn btn--primary" :disabled="saving" @click="addDistrict">
            {{ saving ? '儲存中…' : '新增' }}
          </button>
        </div>
        <p v-if="addError" class="form-error">{{ addError }}</p>
      </div>

      <!-- 地區列表 -->
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>縣市</th>
              <th>地區</th>
              <th>最高總價（萬）</th>
              <th>追蹤</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!districts.length">
              <td colspan="5" class="empty">尚未設定任何地區</td>
            </tr>
            <template v-for="d in districts" :key="d.id">
              <tr :class="{ 'row--disabled': !d.isEnabled }">
                <td>
                  <span v-if="editingId !== d.id">{{ d.city }}</span>
                  <select v-else v-model="editForm.city">
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
                  <input
                    v-else
                    v-model.number="editForm.maxTotalPrice"
                    type="number"
                    min="100"
                    step="50"
                    class="inline-input inline-input--price"
                  />
                </td>
                <td>
                  <button
                    class="toggle-btn"
                    :class="d.isEnabled ? 'toggle-btn--on' : 'toggle-btn--off'"
                    @click="toggle(d)"
                  >
                    {{ d.isEnabled ? '追蹤中' : '已停用' }}
                  </button>
                </td>
                <td class="actions">
                  <template v-if="editingId === d.id">
                    <button class="btn btn--small btn--primary" @click="saveEdit(d)">儲存</button>
                    <button class="btn btn--small" @click="editingId = null">取消</button>
                  </template>
                  <template v-else>
                    <button class="btn btn--small" @click="startEdit(d)">編輯</button>
                    <button class="btn btn--small btn--danger" @click="remove(d)">刪除</button>
                  </template>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api, type DistrictConfig } from '@/services/api'

const districts = ref<DistrictConfig[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const saving = ref(false)
const addError = ref<string | null>(null)
const editingId = ref<number | null>(null)

const form = ref({ city: '新北市', district: '', maxTotalPrice: 800 })
const editForm = ref({ city: '', district: '', maxTotalPrice: 800 })

async function load() {
  loading.value = true
  error.value = null
  try {
    districts.value = await api.districts.list()
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '載入失敗'
  } finally {
    loading.value = false
  }
}

async function addDistrict() {
  addError.value = null
  if (!form.value.district.trim()) {
    addError.value = '請輸入地區名稱'
    return
  }
  if (form.value.maxTotalPrice <= 0) {
    addError.value = '總價上限必須大於 0'
    return
  }
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
.page {
  max-width: 900px;
  margin: 0 auto;
  padding: 1.5rem;
}
.page-header {
  margin-bottom: 1.5rem;
}
h1 {
  font-size: 1.6rem;
  margin-bottom: 0.25rem;
}
h2 {
  font-size: 1rem;
  font-weight: 600;
  margin-bottom: 0.75rem;
  color: #374151;
}
.subtitle {
  color: #6b7280;
  font-size: 0.9rem;
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
.add-card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 1.25rem;
  margin-bottom: 1.5rem;
}
.form-row {
  display: flex;
  gap: 0.75rem;
  align-items: flex-end;
  flex-wrap: wrap;
}
.form-group {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}
.form-group label {
  font-size: 0.8rem;
  color: #6b7280;
  font-weight: 500;
}
.form-group select,
.form-group input {
  padding: 0.45rem 0.65rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 0.9rem;
  min-width: 120px;
}
.form-error {
  margin-top: 0.5rem;
  color: #dc2626;
  font-size: 0.85rem;
}
.table-wrap {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
}
table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}
thead {
  background: #f9fafb;
}
th,
td {
  padding: 0.75rem 1rem;
  text-align: left;
  border-bottom: 1px solid #f3f4f6;
}
th {
  font-weight: 600;
  color: #374151;
  font-size: 0.8rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}
.empty {
  text-align: center;
  color: #9ca3af;
  padding: 2rem;
}
.row--disabled td {
  color: #9ca3af;
}
.inline-input {
  padding: 0.3rem 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 0.9rem;
  width: 100%;
}
.inline-input--price {
  width: 90px;
}
.toggle-btn {
  padding: 0.25rem 0.6rem;
  border-radius: 999px;
  border: none;
  font-size: 0.75rem;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.15s;
}
.toggle-btn--on {
  background: #d1fae5;
  color: #065f46;
}
.toggle-btn--off {
  background: #f3f4f6;
  color: #9ca3af;
}
.toggle-btn:hover {
  opacity: 0.8;
}
.actions {
  display: flex;
  gap: 0.5rem;
}
.btn {
  padding: 0.4rem 0.9rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: #fff;
  color: #374151;
  cursor: pointer;
  font-size: 0.85rem;
  transition: background 0.15s;
}
.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.btn:not(:disabled):hover {
  background: #f3f4f6;
}
.btn--primary {
  background: #1d4ed8;
  color: #fff;
  border-color: #1d4ed8;
}
.btn--primary:not(:disabled):hover {
  background: #1e40af;
}
.btn--danger {
  color: #dc2626;
  border-color: #fca5a5;
}
.btn--danger:not(:disabled):hover {
  background: #fee2e2;
}
.btn--small {
  padding: 0.25rem 0.6rem;
  font-size: 0.8rem;
}
</style>
