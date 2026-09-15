import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import zhCN from '@/locales/zh-CN.json'
import WalletStatsCard from './WalletStatsCard.vue'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: {
    'zh-CN': zhCN,
  },
})

describe('WalletStatsCard', () => {
  it('用 AI Credit 钱包余额解释可用余额，并显示原始 AI Credit 数量', () => {
    const wrapper = mount(WalletStatsCard, {
      props: {
        user: {
          id: 2,
          username: 'ywx',
          quota: 199880,
          usedQuota: 120,
          requestCount: 2,
          affQuota: 0,
          affHistoryQuota: 0,
          affCount: 0,
          group: 'default',
        },
      },
      global: {
        plugins: [i18n],
      },
    })

    expect(wrapper.text()).toContain('AI Credit 钱包余额')
    expect(wrapper.text()).toContain('199,880')
    expect(wrapper.text()).toContain('可用于 API / Playground / Agent 调用')
    expect(wrapper.text()).toContain('已消耗 AI Credit')
    expect(wrapper.text()).toContain('120')
  })
})