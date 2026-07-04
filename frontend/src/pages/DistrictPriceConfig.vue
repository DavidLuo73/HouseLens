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
              <select v-if="districtOptions(form.city).length" v-model="form.district" class="field-input">
                <option value="" disabled>請選擇地區</option>
                <option v-for="d in districtOptions(form.city)" :key="d" :value="d">{{ d }}</option>
              </select>
              <input v-else v-model="form.district" placeholder="例：永和區" class="field-input" />
            </div>
            <div class="field">
              <label class="field-label">最高總價（萬）</label>
              <input v-model.number="form.maxTotalPrice" type="number" min="100" step="50" class="field-input field-input--price" />
            </div>
            <div class="field">
              <label class="field-label">屋齡上限（年，0 = 不限）</label>
              <input v-model.number="form.maxAgeYears" type="number" min="0" step="5" class="field-input field-input--price" />
            </div>
            <div class="field">
              <label class="field-label">停車位（不勾 = 不限）</label>
              <div class="check-group">
                <label v-for="p in PARKING_OPTIONS" :key="p.code" class="check-item">
                  <input type="checkbox" :value="p.code" v-model="form.parkingCodes" />
                  {{ p.label }}
                </label>
              </div>
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
                  <th>屋齡上限</th>
                  <th>停車位</th>
                  <th>追蹤狀態</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="!districts.length">
                  <td colspan="7" class="td-empty">尚未設定任何地區</td>
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
                      <select v-else-if="districtOptions(editForm.city).length" v-model="editForm.district" class="inline-input">
                        <option v-for="opt in districtOptions(editForm.city)" :key="opt" :value="opt">{{ opt }}</option>
                      </select>
                      <input v-else v-model="editForm.district" class="inline-input" />
                    </td>
                    <td>
                      <span v-if="editingId !== d.id">{{ d.maxTotalPrice }} 萬</span>
                      <input v-else v-model.number="editForm.maxTotalPrice" type="number" min="100" step="50" class="inline-input inline-input--price" />
                    </td>
                    <td>
                      <span v-if="editingId !== d.id">{{ d.maxAgeYears > 0 ? `${d.maxAgeYears} 年內` : '不限' }}</span>
                      <input v-else v-model.number="editForm.maxAgeYears" type="number" min="0" step="5" class="inline-input inline-input--price" />
                    </td>
                    <td>
                      <span v-if="editingId !== d.id">{{ parkingLabel(d.parkingCodes) }}</span>
                      <div v-else class="check-group check-group--inline">
                        <label v-for="p in PARKING_OPTIONS" :key="p.code" class="check-item">
                          <input type="checkbox" :value="p.code" v-model="editForm.parkingCodes" />
                          {{ p.label }}
                        </label>
                      </div>
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
              <span v-if="['Rakuya', 'Sinyi', 'F591', 'Yungching', 'HBHousing'].includes(s.value)" class="tab-dot" title="支援額外篩選" />
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

          <!-- 信義房屋：型態 / 車位 / 坪數 / 房數（連動搜尋 URL 路徑段） -->
          <div v-else-if="activePlatform === 'Sinyi'" class="platform-panel">
            <div class="add-row">
              <div class="field">
                <label class="field-label">最小坪數（0 = 不限）</label>
                <input v-model.number="sinyiForm.minSizePing" type="number" min="0" step="1" class="field-input field-input--price" />
              </div>
              <div class="field">
                <label class="field-label">房數</label>
                <select v-model="sinyiForm.minRooms" class="field-input">
                  <option value="">不限</option>
                  <option v-for="n in [1, 2, 3, 4, 5]" :key="n" :value="String(n)">{{ n }} 房以上</option>
                </select>
              </div>
            </div>
            <div class="add-row">
              <div class="field">
                <label class="field-label">建物型態（不勾 = 全部住宅型態）</label>
                <div class="check-group">
                  <label v-for="t in SINYI_TYPE_OPTIONS" :key="t.code" class="check-item">
                    <input type="checkbox" :value="t.code" v-model="sinyiForm.typeCodes" />
                    {{ t.label }}
                  </label>
                </div>
              </div>
            </div>
            <p class="card-subtitle">停車位條件由上方地區列表的通用設定決定：不勾＝不限（有無車位都抓）；勾選後自動轉為信義的車位型式並要求必須有車位。</p>
            <div class="platform-actions">
              <button class="btn-add" :disabled="filterSaving" @click="saveSinyiFilter">
                {{ filterSaving ? '儲存中…' : '儲存信義房屋篩選' }}
              </button>
              <span v-if="filterSavedAt" class="save-hint">已儲存 ✓</span>
            </div>
            <p v-if="filterError" class="field-error">{{ filterError }}</p>
          </div>

          <!-- 591 房屋：坪數 / 房數（連動搜尋 URL 的 area / pattern 參數） -->
          <div v-else-if="activePlatform === 'F591'" class="platform-panel">
            <div class="add-row">
              <div class="field">
                <label class="field-label">最小坪數（0 = 不限）</label>
                <input v-model.number="f591Form.minSizePing" type="number" min="0" step="1" class="field-input field-input--price" />
              </div>
              <div class="field">
                <label class="field-label">房數（不勾 = 不限）</label>
                <div class="check-group">
                  <label v-for="r in ROOM_OPTIONS" :key="r.code" class="check-item">
                    <input type="checkbox" :value="r.code" v-model="f591Form.rooms" />
                    {{ r.label }}
                  </label>
                </div>
              </div>
            </div>
            <p class="card-subtitle">
              地區（regionid / section）、最高總價（price）、屋齡上限（houseage）、停車位（parking）
              由上方地區列表的通用設定決定，會自動轉為 591 搜尋 URL 參數；此處為 591 專屬的坪數（area）與房數（pattern）條件。
            </p>
            <div class="platform-actions">
              <button class="btn-add" :disabled="filterSaving" @click="saveF591Filter">
                {{ filterSaving ? '儲存中…' : '儲存 591 篩選' }}
              </button>
              <span v-if="filterSavedAt" class="save-hint">已儲存 ✓</span>
            </div>
            <p v-if="filterError" class="field-error">{{ filterError }}</p>
          </div>

          <!-- 永慶房屋：型態 / 坪數（連動搜尋 URL 路徑段 _type / _pin） -->
          <div v-else-if="activePlatform === 'Yungching'" class="platform-panel">
            <div class="add-row">
              <div class="field">
                <label class="field-label">最小坪數（0 = 不限）</label>
                <input v-model.number="yungchingForm.minSizePing" type="number" min="0" step="1" class="field-input field-input--price" />
              </div>
              <div class="field">
                <label class="field-label">用途</label>
                <select v-model="yungchingForm.useCode" class="field-input">
                  <option value="1">住宅</option>
                </select>
              </div>
            </div>
            <div class="add-row">
              <div class="field">
                <label class="field-label">建物型態（不勾 = 全部住宅型態）</label>
                <div class="check-group">
                  <label v-for="t in YUNGCHING_TYPE_OPTIONS" :key="t.code" class="check-item">
                    <input type="checkbox" :value="t.code" v-model="yungchingForm.typeCodes" />
                    {{ t.label }}
                  </label>
                </div>
              </div>
            </div>
            <p class="card-subtitle">
              地區（{城市}-{地區}_c）、最高總價（_price）、屋齡上限（_age）、停車位（_park）
              由上方地區列表的通用設定決定，會自動轉為永慶搜尋 URL 路徑段；
              停車位勾選後自動轉為永慶車位型式（平面→坡道平面＋昇降平面、機械→坡道機械＋昇降機械）。
            </p>
            <div class="platform-actions">
              <button class="btn-add" :disabled="filterSaving" @click="saveYungchingFilter">
                {{ filterSaving ? '儲存中…' : '儲存永慶篩選' }}
              </button>
              <span v-if="filterSavedAt" class="save-hint">已儲存 ✓</span>
            </div>
            <p v-if="filterError" class="field-error">{{ filterError }}</p>
          </div>

          <!-- 住商不動產：型態 / 坪數 / 房數（連動搜尋 URL 路徑段） -->
          <div v-else-if="activePlatform === 'HBHousing'" class="platform-panel">
            <div class="add-row">
              <div class="field">
                <label class="field-label">最小坪數（0 = 不限）</label>
                <input v-model.number="hbForm.minSizePing" type="number" min="0" step="1" class="field-input field-input--price" />
              </div>
              <div class="field">
                <label class="field-label">房數</label>
                <select v-model="hbForm.minRooms" class="field-input">
                  <option value="">不限</option>
                  <option v-for="n in [1, 2, 3, 4, 5]" :key="n" :value="String(n)">{{ n }} 房以上</option>
                </select>
              </div>
              <div class="field">
                <label class="field-label">用途</label>
                <select v-model="hbForm.useCode" class="field-input">
                  <option value="1">住宅</option>
                </select>
              </div>
            </div>
            <div class="add-row">
              <div class="field">
                <label class="field-label">建物型態（不勾 = 全部住宅型態）</label>
                <div class="check-group">
                  <label v-for="t in HB_TYPE_OPTIONS" :key="t.code" class="check-item">
                    <input type="checkbox" :value="t.code" v-model="hbForm.typeCodes" />
                    {{ t.label }}
                  </label>
                </div>
              </div>
            </div>
            <p class="card-subtitle">
              地區（{城市}/{郵遞區號}）、最高總價（{N}-down-price）、屋齡上限（{N}-down-age）、停車位（parking-tag）
              由上方地區列表的通用設定決定，會自動轉為住商搜尋 URL 路徑段；
              停車位勾選任一型式即帶入「有車位」條件（住商不分平面/機械）。
            </p>
            <div class="platform-actions">
              <button class="btn-add" :disabled="filterSaving" @click="saveHBFilter">
                {{ filterSaving ? '儲存中…' : '儲存住商篩選' }}
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

const form = ref({ city: '新北市', district: '', maxTotalPrice: 800, maxAgeYears: 0, parkingCodes: [] as string[] })
const editForm = ref({ city: '', district: '', maxTotalPrice: 800, maxAgeYears: 0, parkingCodes: [] as string[] })

// 停車位代碼（樂屋網 other= 參數：PF 平面 / PM 機械）
const PARKING_OPTIONS = [
  { code: 'PF', label: '平面車位' },
  { code: 'PM', label: '機械車位' },
]

// 各縣市可選地區（與爬蟲 DistrictMap 支援的行政區一致；其餘縣市維持自由輸入）
const CITY_DISTRICTS: Record<string, string[]> = {
  新北市: [
    '板橋區', '汐止區', '深坑區', '瑞芳區', '新店區', '永和區', '中和區', '土城區', '三峽區', '樹林區',
    '鶯歌區', '三重區', '新莊區', '泰山區', '林口區', '蘆洲區', '五股區', '八里區', '淡水區',
  ],
  桃園市: [
    '中壢區', '平鎮區', '龍潭區', '楊梅區', '新屋區', '觀音區', '桃園區', '龜山區', '八德區', '大溪區',
    '大園區', '蘆竹區',
  ],
}
const districtOptions = (city: string) => CITY_DISTRICTS[city] ?? []

function parkingLabel(codes: string): string {
  const list = splitCodes(codes || '')
  if (!list.length) return '不限'
  return list
    .map((c) => PARKING_OPTIONS.find((p) => p.code === c)?.label ?? c)
    .join('、')
}

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

// ===== 信義房屋（搜尋 URL 路徑段代碼，實站驗證）=====
const SINYI_TYPE_OPTIONS = [
  { code: 'apartment', label: '公寓' },
  { code: 'dalou', label: '大廈' },
  { code: 'huaxia', label: '華廈' },
  { code: 'townhouse', label: '透天' },
  { code: 'villa', label: '別墅' },
]
// ===== 住商不動產（搜尋 URL {…}-style 段的型態代碼，實站驗證）=====
const HB_TYPE_OPTIONS = [
  { code: 'noelevator', label: '無電梯公寓' },
  { code: 'elevator', label: '大樓(11樓以上)' },
  { code: 'mansion', label: '華廈(10樓以下)' },
]
// ===== 永慶房屋（搜尋 URL {…}_type 段的型態名稱，實站驗證）=====
const YUNGCHING_TYPE_OPTIONS = [
  { code: '電梯大廈', label: '電梯大廈' },
  { code: '華廈', label: '華廈' },
  { code: '無電梯公寓', label: '無電梯公寓' },
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

const sinyiForm = ref({
  minSizePing: 0,
  minRooms: '' as string,
  typeCodes: [] as string[],
})

// 591 專屬篩選（area / pattern URL 參數；房數代碼與樂屋網共用 ROOM_OPTIONS，爬蟲端 5~ → 5）
const f591Form = ref({
  minSizePing: 0,
  rooms: [] as string[],
})

// 永慶專屬篩選（_pin 坪數／_type 型態；用途固定住宅）
const yungchingForm = ref({
  minSizePing: 0,
  useCode: '1',
  typeCodes: [] as string[],
})

// 住商專屬篩選（area 坪數／room-pattern 房數／style 型態；用途固定住宅）
const hbForm = ref({
  minSizePing: 0,
  minRooms: '' as string,
  useCode: '1',
  typeCodes: [] as string[],
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
    const sinyi = filters.find((f) => f.sourceSite === 'Sinyi')
    if (sinyi) {
      const sinyiTypeSlugs = SINYI_TYPE_OPTIONS.map((t) => t.code)
      sinyiForm.value = {
        minSizePing: sinyi.minSizePing ?? 0,
        minRooms: splitCodes(sinyi.rooms || '')[0] ?? '',
        // 舊資料可能存 Rakuya 代碼（R1,R2），非信義 slug 一律視為未勾（爬蟲端自行對映）
        typeCodes: splitCodes(sinyi.typeCodes || '').filter((c) => sinyiTypeSlugs.includes(c)),
      }
    }
    const f591 = filters.find((f) => f.sourceSite === 'F591')
    if (f591) {
      f591Form.value = {
        minSizePing: f591.minSizePing ?? 0,
        rooms: splitCodes(f591.rooms || ''),
      }
    }
    const yungching = filters.find((f) => f.sourceSite === 'Yungching')
    if (yungching) {
      const yungchingTypes = YUNGCHING_TYPE_OPTIONS.map((t) => t.code)
      yungchingForm.value = {
        minSizePing: yungching.minSizePing ?? 0,
        useCode: '1',
        // 舊資料可能存 Rakuya 代碼（R1,R2），非永慶型態名一律視為未勾（爬蟲端自行對映）
        typeCodes: splitCodes(yungching.typeCodes || '').filter((c) => yungchingTypes.includes(c)),
      }
    }
    const hb = filters.find((f) => f.sourceSite === 'HBHousing')
    if (hb) {
      const hbTypeCodes = HB_TYPE_OPTIONS.map((t) => t.code)
      hbForm.value = {
        minSizePing: hb.minSizePing ?? 0,
        minRooms: splitCodes(hb.rooms || '')[0] ?? '',
        useCode: '1',
        // 舊資料可能存 Rakuya 代碼（R1,R2），非住商代碼一律視為未勾（爬蟲端自行對映）
        typeCodes: splitCodes(hb.typeCodes || '').filter((c) => hbTypeCodes.includes(c)),
      }
    }
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '載入失敗'
  } finally {
    loading.value = false
  }
}

async function saveF591Filter() {
  filterError.value = null
  filterSavedAt.value = false
  filterSaving.value = true
  try {
    await api.platformFilters.update('F591', {
      minSizePing: f591Form.value.minSizePing,
      rooms: f591Form.value.rooms.join(','),
      typeCodes: '',
      useCode: '1',
    })
    filterSavedAt.value = true
  } catch (e: unknown) {
    filterError.value = e instanceof Error ? e.message : '儲存失敗'
  } finally {
    filterSaving.value = false
  }
}

async function saveYungchingFilter() {
  filterError.value = null
  filterSavedAt.value = false
  filterSaving.value = true
  try {
    await api.platformFilters.update('Yungching', {
      minSizePing: yungchingForm.value.minSizePing,
      rooms: '',
      typeCodes: yungchingForm.value.typeCodes.join(','),
      useCode: '1',
    })
    filterSavedAt.value = true
  } catch (e: unknown) {
    filterError.value = e instanceof Error ? e.message : '儲存失敗'
  } finally {
    filterSaving.value = false
  }
}

async function saveHBFilter() {
  filterError.value = null
  filterSavedAt.value = false
  filterSaving.value = true
  try {
    await api.platformFilters.update('HBHousing', {
      minSizePing: hbForm.value.minSizePing,
      rooms: hbForm.value.minRooms,
      typeCodes: hbForm.value.typeCodes.join(','),
      useCode: '1',
    })
    filterSavedAt.value = true
  } catch (e: unknown) {
    filterError.value = e instanceof Error ? e.message : '儲存失敗'
  } finally {
    filterSaving.value = false
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

async function saveSinyiFilter() {
  filterError.value = null
  filterSavedAt.value = false
  filterSaving.value = true
  try {
    await api.platformFilters.update('Sinyi', {
      minSizePing: sinyiForm.value.minSizePing,
      rooms: sinyiForm.value.minRooms,
      typeCodes: sinyiForm.value.typeCodes.join(','),
      useCode: '1',
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
      maxAgeYears: form.value.maxAgeYears,
      parkingCodes: form.value.parkingCodes.join(','),
    })
    districts.value.push(created)
    form.value.district = ''
    form.value.maxTotalPrice = 800
    form.value.maxAgeYears = 0
    form.value.parkingCodes = []
  } catch (e: unknown) {
    addError.value = e instanceof Error ? e.message : '新增失敗'
  } finally {
    saving.value = false
  }
}

function startEdit(d: DistrictConfig) {
  editingId.value = d.id
  editForm.value = {
    city: d.city,
    district: d.district,
    maxTotalPrice: d.maxTotalPrice,
    maxAgeYears: d.maxAgeYears ?? 0,
    parkingCodes: splitCodes(d.parkingCodes || ''),
  }
}

async function saveEdit(d: DistrictConfig) {
  try {
    const updated = await api.districts.update(d.id, {
      city: editForm.value.city,
      district: editForm.value.district,
      maxTotalPrice: editForm.value.maxTotalPrice,
      isEnabled: d.isEnabled,
      maxAgeYears: editForm.value.maxAgeYears,
      parkingCodes: editForm.value.parkingCodes.join(','),
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

.check-group--inline { padding: 0; gap: 4px 10px; flex-wrap: nowrap; }

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
