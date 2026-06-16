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
}

export interface PropertyDetail extends Property {
  address?: string
  firstSeenAt: string
  lastSeenAt: string
  priceHistory: PriceHistoryEntry[]
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

// ─── API functions ───────────────────────────────────────────────────────────

export const api = {
  properties: {
    list: (params: Record<string, unknown> = {}) => {
      const qs = new URLSearchParams()
      for (const [k, v] of Object.entries(params)) {
        if (v !== undefined && v !== null) {
          if (Array.isArray(v)) v.forEach((item) => qs.append(k, String(item)))
          else qs.set(k, String(v))
        }
      }
      return request<PagedResult<Property>>(`/properties?${qs}`)
    },
    get: (id: string) => request<PropertyDetail>(`/properties/${id}`),
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
}
