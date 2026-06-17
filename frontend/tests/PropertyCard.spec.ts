import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import PropertyCard from '../src/components/PropertyCard.vue'
import type { Property } from '../src/services/api'

function makeProperty(overrides: Partial<Property> = {}): Property {
  return {
    id: 'p1',
    title: '測試物件',
    city: '新北市',
    district: '中和區',
    areaPing: 30,
    hasParking: false,
    currentTotalPrice: 1280,
    status: 'active',
    isNew: false,
    latestIsBigDrop: false,
    sources: [],
    priceHistory: [
      { capturedAt: '2026-06-11T00:00:00Z', totalPrice: 1280, changeFlag: 'decreased', changePercent: -0.015, isBigDrop: false },
      { capturedAt: '2026-06-01T00:00:00Z', totalPrice: 1300, changeFlag: 'none', isBigDrop: false },
    ],
    ...overrides,
  } as Property
}

function mountCard(property: Property) {
  return mount(PropertyCard, { props: { property }, global: { stubs: { VChart: true } } })
}

describe('PropertyCard', () => {
  it('shows the sparkline when there are at least two history points', () => {
    const wrapper = mountCard(makeProperty())
    expect(wrapper.find('.card-spark').exists()).toBe(true)
  })

  it('hides the sparkline when history has fewer than two points', () => {
    const wrapper = mountCard(makeProperty({ priceHistory: [
      { capturedAt: '2026-06-11T00:00:00Z', totalPrice: 1280, changeFlag: 'none', isBigDrop: false },
    ] }))
    expect(wrapper.find('.card-spark').exists()).toBe(false)
  })

  it('emits open-history with the property when 看明細 is clicked', async () => {
    const property = makeProperty()
    const wrapper = mountCard(property)
    await wrapper.find('.spark-btn').trigger('click')
    expect(wrapper.emitted('open-history')?.[0]).toEqual([property])
  })
})
