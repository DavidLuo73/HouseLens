const BASE = '/api'

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    headers: { 'Content-Type': 'application/json', ...options?.headers },
    ...options,
  })
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: { message: res.statusText } }))
    throw new Error(err?.error?.message ?? `HTTP ${res.status}`)
  }
  return res.json()
}

// ─── Types ──────────────────────────────────────────────────────────────────

export interface PropertySource {
  sourceSite: string
  url: string
  imageUrl?: string
  title?: string
  postedDate?: string
}

export interface DistrictConfig {
  id: number
  city: string
  district: string
  maxTotalPrice: number
  isEnabled: boolean
  /** 屋齡上限（年，0 = 不限）。樂屋網對應 age=~N */
  maxAgeYears: number
  /** 停車位代碼（逗號分隔如 "PF,PM"；空 = 不限）。樂屋網對應 other= */
  parkingCodes: string
}

/** 平台專屬搜尋篩選（每平台一筆，套用到該平台的所有地區；樂屋網與信義房屋使用） */
export interface PlatformFilterConfig {
  sourceSite: string
  /** 最小坪數，0 = 不限（樂屋網 size={N}~；信義 {N}-up-area） */
  minSizePing: number
  /** 房數，逗號分隔如 "2,3,4,5~"，空 = 不限（樂屋網 room=；信義取最小值為 {N}-up-roomtotal） */
  rooms: string
  /** 建物型態代碼，逗號分隔（樂屋網 typecode：R1公寓/R2大樓華廈…；信義 slug：apartment/dalou/huaxia/townhouse/villa） */
  typeCodes: string
  /** 用途代碼（樂屋網 usecode：1住宅/2商用/6住辦/3車位；信義不使用） */
  useCode: string
}

export interface PriceHistoryEntry {
  capturedAt: string
  totalPrice: number
  unitPrice?: number
  changeFlag: 'none' | 'increased' | 'decreased'
  changePercent?: number
  isBigDrop: boolean
}

export interface Property {
  id: string
  title: string
  city: string
  district: string
  areaPing: number
  floor?: string
  ageYears?: number
  hasParking: boolean
  currentTotalPrice: number
  currentUnitPrice?: number
  status: 'active' | 'delisted'
  score?: number
  isNew: boolean
  latestChangeFlag?: string
  latestChangePercent?: number
  latestIsBigDrop: boolean
  imageUrl?: string
  listingUrl?: string
  sources: PropertySource[]
  priceHistory: PriceHistoryEntry[]
}

export interface PropertyDetail extends Property {
  address?: string
  firstSeenAt: string
  lastSeenAt: string
}

export interface PagedResult<T> {
  total: number
  page: number
  pageSize: number
  items: T[]
}

export interface CrawlRun {
  id: string
  startedAt: string
  finishedAt?: string
  status: 'running' | 'completed' | 'failed'
  newCount: number
  delistedCount: number
  bigDropCount: number
  sources: {
    sourceSite: string
    success: boolean
    fetchedCount: number
    errorMessage?: string
  }[]
}

export interface DistrictStats {
  district: string
  propertyCount: number
  avgUnitPrice: number
  minTotalPrice: number
  maxTotalPrice: number
  priceBuckets: { range: string; count: number }[]
  trend: { date: string; avgUnitPrice: number }[]
  insufficientData?: boolean
}

export interface TopPropertyItem {
  id: string
  title: string
  totalPrice: number
  unitPrice?: number
  ageYears?: number
  hasParking: boolean
  score: number
}

export interface BigDropItem {
  id: string
  title: string
  district: string
  address?: string
  originalPrice: number
  newPrice: number
  dropAmount: number
  dropPercent: number
  url: string
}

export interface PlatformStats {
  sourceSite: string
  displayName: string
  listingCount: number
  propertyCount: number
  priceHistoryCount: number
  lastCrawlAt?: string
  lastCrawlSuccess?: boolean
  lastCrawlFetchedCount?: number
}

export interface PurgeResult {
  listingsDeleted: number
  propertiesDeleted: number
  priceHistoryDeleted: number
  sourceRunResultsDeleted: number
}

export interface AppConfig {
  tracking: { districts: string[]; maxTotalPrice: number }
  scoring: {
    weightUnitPrice: number
    weightAge: number
    weightParking: number
    weightLocation: number
    bigDropPercent: number
    bigDropAmount: number
  }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

function toProxyUrl(url?: string): string | undefined {
  if (!url) return undefined
  // 只有信義房屋需要後端代理繞過防盜鏈（Referer: sinyi.com.tw）
  // 591 等其他平台可直接存取，且影片檔不應受限於代理的 5 MB 上限
  if (url.includes('sinyi.com.tw'))
    return `/api/proxy/image?url=${encodeURIComponent(url)}`
  return url
}

function mapProperty<T extends Property>(p: T): T {
  return { ...p, imageUrl: toProxyUrl(p.imageUrl) }
}

// ─── API functions ───────────────────────────────────────────────────────────

export const api = {
  properties: {
    list: async (params: Record<string, unknown> = {}): Promise<PagedResult<Property>> => {
      const qs = new URLSearchParams()
      for (const [k, v] of Object.entries(params)) {
        if (v !== undefined && v !== null) {
          if (Array.isArray(v)) v.forEach((item) => qs.append(k, String(item)))
          else qs.set(k, String(v))
        }
      }
      const result = await request<PagedResult<Property>>(`/properties?${qs}`)
      return { ...result, items: result.items.map(mapProperty) }
    },
    get: async (id: string): Promise<PropertyDetail> => {
      const p = await request<PropertyDetail>(`/properties/${id}`)
      return mapProperty(p)
    },
  },

  analytics: {
    districts: () => request<{ districts: DistrictStats[] }>('/analytics/districts'),
    topRated: (limit = 5) =>
      request<{ type: string; byDistrict: { district: string; items: TopPropertyItem[] }[] }>(
        `/analytics/top-properties?type=topRated&limit=${limit}`
      ),
    bigDrop: () => request<{ type: string; items: BigDropItem[] }>('/analytics/top-properties?type=bigDrop'),
  },

  crawlRuns: {
    latest: () => request<CrawlRun>('/crawl-runs/latest'),
  },

  admin: {
    triggerCrawl: () =>
      request<{ message: string }>('/admin/trigger-crawl', { method: 'POST' }),
    platformStats: () =>
      request<PlatformStats[]>('/admin/platform-stats'),
    purgePlatform: (site: string) =>
      request<PurgeResult>(`/admin/platform/${site}`, { method: 'DELETE' }),
    recrawlPlatform: (site: string) =>
      request<{ message: string }>(`/admin/platform/${site}/recrawl`, { method: 'POST' }),
  },

  config: {
    get: () => request<AppConfig>('/config'),
    put: (config: AppConfig) =>
      request<AppConfig>('/config', { method: 'PUT', body: JSON.stringify(config) }),
  },

  districts: {
    list: () => request<DistrictConfig[]>('/config/districts'),
    create: (d: Omit<DistrictConfig, 'id'>) =>
      request<DistrictConfig>('/config/districts', { method: 'POST', body: JSON.stringify(d) }),
    update: (id: number, d: Omit<DistrictConfig, 'id'>) =>
      request<DistrictConfig>(`/config/districts/${id}`, { method: 'PUT', body: JSON.stringify(d) }),
    delete: (id: number) =>
      request<void>(`/config/districts/${id}`, { method: 'DELETE' }),
    toggle: (id: number) =>
      request<{ id: number; isEnabled: boolean }>(`/config/districts/${id}/toggle`, { method: 'PATCH' }),
  },

  platformFilters: {
    list: () => request<PlatformFilterConfig[]>('/config/platform-filters'),
    update: (sourceSite: string, d: Partial<Omit<PlatformFilterConfig, 'sourceSite'>>) =>
      request<PlatformFilterConfig>(`/config/platform-filters/${sourceSite}`, {
        method: 'PUT',
        body: JSON.stringify(d),
      }),
  },
}
