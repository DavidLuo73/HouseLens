import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import PriceHistoryModal from '../src/components/PriceHistoryModal.vue'
import type { Property } from '../src/services/api'

const property = {
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
  listingUrl: 'https://example.com/p1',
  sources: [],
  priceHistory: [
    { capturedAt: '2026-06-11T00:00:00Z', totalPrice: 1280, changeFlag: 'decreased', changePercent: -0.015, isBigDrop: false },
    { capturedAt: '2026-06-01T00:00:00Z', totalPrice: 1300, changeFlag: 'none', isBigDrop: false },
  ],
} as unknown as Property

function mountModal() {
  return mount(PriceHistoryModal, {
    props: { property },
    global: { stubs: { VChart: true } },
  })
}

describe('PriceHistoryModal', () => {
  it('renders the property title and embeds the panel', () => {
    const wrapper = mountModal()
    expect(wrapper.text()).toContain('測試物件')
    expect(wrapper.findComponent({ name: 'PriceHistoryPanel' }).exists()).toBe(true)
  })

  it('emits close when the close button is clicked', async () => {
    const wrapper = mountModal()
    await wrapper.find('.modal-close').trigger('click')
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('emits close when the overlay is clicked', async () => {
    const wrapper = mountModal()
    await wrapper.find('.modal-overlay').trigger('click.self')
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('links 查看商品 to the listing url', () => {
    const wrapper = mountModal()
    expect(wrapper.find('.modal-visit').attributes('href')).toBe('https://example.com/p1')
  })
})
