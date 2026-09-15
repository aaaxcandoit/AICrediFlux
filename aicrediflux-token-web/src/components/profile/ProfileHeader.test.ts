import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import zhCN from '@/locales/zh-CN.json'
import ProfileHeader from './ProfileHeader.vue'

vi.mock('@/plugins/spi/registry', () => ({
  isFeatureHidden: () => false,
}))

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: {
    'zh-CN': zhCN,
  },
})

describe('ProfileHeader', () => {
  it('个人资料页的 AI Credit 钱包余额显示原始 AI Credit 数量', () => {
    const wrapper = mount(ProfileHeader, {
      props: {
        loading: false,
        profile: {
          id: 2,
          username: 'ywx',
          displayName: 'Ywx',
          role: 1,
          status: 1,
          quota: 199880,
          usedQuota: 120,
          requestCount: 2,
        },
      },
      global: {
        plugins: [i18n],
      },
    })

    expect(wrapper.text()).toContain('AI Credit 钱包余额')
    expect(wrapper.text()).toContain('199,880')
    expect(wrapper.text()).toContain('已消耗 AI Credit')
    expect(wrapper.text()).toContain('120')
  })
})