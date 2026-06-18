import { defineStore } from 'pinia'

export const TRACKED_DISTRICTS = [
  '中和區', '永和區', '新店區', '板橋區', '樹林區', '新莊區', '中壢區', '桃園區',
]

interface FilterState {
  districts: string[]
  sourceSites: string[]
  minPrice: number | null
  maxPrice: number | null
  hasParking: boolean | null
  priceDropped: boolean | null
  status: 'active' | 'delisted'
  sortBy: string
  page: number
  pageSize: number
}

export const useFiltersStore = defineStore('filters', {
  state: (): FilterState => ({
    districts: [],
    sourceSites: [],
    minPrice: null,
    maxPrice: null,
    hasParking: null,
    priceDropped: null,
    status: 'active',
    sortBy: 'score',
    page: 1,
    pageSize: 20,
  }),
  actions: {
    toggleDistrict(district: string) {
      const idx = this.districts.indexOf(district)
      if (idx === -1) this.districts.push(district)
      else this.districts.splice(idx, 1)
      this.page = 1
    },
    toggleSourceSite(site: string) {
      const idx = this.sourceSites.indexOf(site)
      if (idx === -1) this.sourceSites.push(site)
      else this.sourceSites.splice(idx, 1)
      this.page = 1
    },
    reset() {
      this.districts = []
      this.sourceSites = []
      this.minPrice = null
      this.maxPrice = null
      this.hasParking = null
      this.priceDropped = null
      this.status = 'active'
      this.sortBy = 'score'
      this.page = 1
    },
    setPage(p: number) {
      this.page = p
    },
  },
})
