import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import zhCN from '@/locales/zh-CN.json'
import SummaryCards from './SummaryCards.vue'

const push = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({ push }),
}))

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: {
    'zh-CN': zhCN,
  },
})

describe('SummaryCards', () => {
  it('余额健康度显示当前钱包 AI Credit，不再减去累计已消耗', () => {
    const wrapper = mount(SummaryCards, {
      props: {
        userInfo: {
          id: 2,
          username: 'ywx',
          displayName: 'Ywx',
          role: 1,
          status: 1,
          quota: 100099824,
          usedQuota: 176,
          requestCount: 1,
        },
        quotaDates: [{ timestamp: 1799485200, quota: 176, tokens: 0, requests: 1 }],
        sparklineData: { usage: [0, 176], requests: [0, 1] },
        loading: false,
      },
      global: {
        plugins: [i18n],
        stubs: {
          ElButton: { template: '<button><slot /></button>' },
        },
      },
    })

    expect(wrapper.text()).toContain('余额健康度')
    expect(wrapper.text()).toContain('100,099,824')
    expect(wrapper.text()).not.toContain('100,099,648')
  })
})