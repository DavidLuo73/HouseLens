import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import PriceHistoryPanel from '../src/components/PriceHistoryPanel.vue'
import type { PriceHistoryEntry } from '../src/services/api'

vi.mock('vue-echarts', () => ({ default: { name: 'VChart', template: '<div />' } }))
vi.mock('echarts/core', () => ({ use: vi.fn() }))
vi.mock('echarts/charts', () => ({ LineChart: {} }))
vi.mock('echarts/components', () => ({ GridComponent: {}, TooltipComponent: {}, MarkPointComponent: {} }))
vi.mock('echarts/renderers', () => ({ CanvasRenderer: {} }))

const history: PriceHistoryEntry[] = [
  { capturedAt: '2026-06-11T00:00:00Z', totalPrice: 1280, unitPrice: 42.6, changeFlag: 'decreased', changePercent: -0.015, isBigDrop: false },
  { capturedAt: '2026-06-01T00:00:00Z', totalPrice: 1300, unitPrice: 43.3, changeFlag: 'none', isBigDrop: false },
  { capturedAt: '2026-05-20T00:00:00Z', totalPrice: 1250, unitPrice: 41.6, changeFlag: 'increased', changePercent: 0.04, isBigDrop: false },
]

function mountPanel() {
  return mount(PriceHistoryPanel, {
    props: { history },
    global: { stubs: { VChart: true } },
  })
}

describe('PriceHistoryPanel', () => {
  it('renders one table row per history entry', () => {
    const wrapper = mountPanel()
    expect(wrapper.findAll('.history-table tbody tr')).toHaveLength(3)
  })

  it('shows highest and lowest total price', () => {
    const wrapper = mountPanel()
    expect(wrapper.find('.stat--high').text()).toContain('1300')
    expect(wrapper.find('.stat--low').text()).toContain('1250')
  })
})
