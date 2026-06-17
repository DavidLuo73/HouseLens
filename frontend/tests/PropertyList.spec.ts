import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Property } from '../src/services/api'

const sampleProperty = {
  id: 'p1', title: '測試物件', city: '新北市', district: '中和區', areaPing: 30,
  hasParking: false, currentTotalPrice: 1280, status: 'active', isNew: false,
  latestIsBigDrop: false, sources: [], priceHistory: [],
} as unknown as Property

vi.mock('../src/services/api', () => ({
  api: {
    properties: {
      list: vi.fn().mockResolvedValue({
        total: 1, page: 1, pageSize: 20,
        items: [{
          id: 'p1', title: '測試物件', city: '新北市', district: '中和區', areaPing: 30,
          hasParking: false, currentTotalPrice: 1280, status: 'active', isNew: false,
          latestIsBigDrop: false, sources: [], priceHistory: [],
        }],
      }),
    },
  },
}))

import PropertyList from '../src/pages/PropertyList.vue'

const cardStub = {
  name: 'PropertyCard',
  props: ['property'],
  emits: ['open-history'],
  template: '<button class="card-stub" @click="$emit(\'open-history\', property)">card</button>',
}
const modalStub = {
  name: 'PriceHistoryModal',
  props: ['property'],
  emits: ['close'],
  template: '<div class="modal-stub" @click="$emit(\'close\')">modal</div>',
}

function mountList() {
  return mount(PropertyList, {
    global: {
      stubs: { FilterBar: true, PropertyCard: cardStub, PriceHistoryModal: modalStub, RouterLink: true },
    },
  })
}

describe('PropertyList modal wiring', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('opens the modal when a card emits open-history and closes it again', async () => {
    const wrapper = mountList()
    await flushPromises()

    expect(wrapper.find('.modal-stub').exists()).toBe(false)

    await wrapper.find('.card-stub').trigger('click')
    expect(wrapper.find('.modal-stub').exists()).toBe(true)

    await wrapper.find('.modal-stub').trigger('click') // stub emits close
    expect(wrapper.find('.modal-stub').exists()).toBe(false)
  })
})

// Keep sampleProperty in scope to avoid unused variable warning
void sampleProperty
