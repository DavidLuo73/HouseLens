import { setActivePinia, createPinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { useFiltersStore } from '../src/stores/filters'

describe('useFiltersStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('initial state has empty districts and active status', () => {
    const store = useFiltersStore()
    expect(store.districts).toEqual([])
    expect(store.status).toBe('active')
    expect(store.page).toBe(1)
    expect(store.sortBy).toBe('score')
    expect(store.hasParking).toBeNull()
    expect(store.priceDropped).toBeNull()
  })

  it('toggleDistrict adds a district when not present', () => {
    const store = useFiltersStore()
    store.toggleDistrict('中和區')
    expect(store.districts).toContain('中和區')
  })

  it('toggleDistrict removes a district when already present', () => {
    const store = useFiltersStore()
    store.toggleDistrict('中和區')
    store.toggleDistrict('中和區')
    expect(store.districts).not.toContain('中和區')
  })

  it('toggleDistrict resets page to 1', () => {
    const store = useFiltersStore()
    store.setPage(3)
    store.toggleDistrict('永和區')
    expect(store.page).toBe(1)
  })

  it('toggleDistrict can hold multiple districts', () => {
    const store = useFiltersStore()
    store.toggleDistrict('中和區')
    store.toggleDistrict('板橋區')
    expect(store.districts).toHaveLength(2)
    expect(store.districts).toContain('中和區')
    expect(store.districts).toContain('板橋區')
  })

  it('setPage updates current page', () => {
    const store = useFiltersStore()
    store.setPage(5)
    expect(store.page).toBe(5)
  })

  it('reset restores all fields to defaults', () => {
    const store = useFiltersStore()
    store.toggleDistrict('中和區')
    store.setPage(3)
    store.$patch({ maxPrice: 800, hasParking: true, priceDropped: true, status: 'delisted' })

    store.reset()

    expect(store.districts).toEqual([])
    expect(store.page).toBe(1)
    expect(store.maxPrice).toBeNull()
    expect(store.hasParking).toBeNull()
    expect(store.priceDropped).toBeNull()
    expect(store.status).toBe('active')
    expect(store.sortBy).toBe('score')
  })
})
