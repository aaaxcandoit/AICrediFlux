import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import NavGroup from './NavGroup.vue'

const route = vi.hoisted(() => ({ path: '/wallet' }))
const push = vi.hoisted(() => vi.fn())

vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ push }),
}))

describe('NavGroup', () => {
  it('折叠子项没有图标时仍渲染占位，保持层级缩进一致', () => {
    const wrapper = mount(NavGroup, {
      props: {
        title: '个人',
        items: [
          {
            title: 'AI Credit',
            icon: 'i-ep-wallet',
            items: [
              { title: '商城', url: '/token-mall', icon: 'i-ep-shopping-cart' },
              { title: '订单', url: '/orders/token', icon: 'i-ep-list' },
              { title: '钱包', url: '/wallet' },
            ],
          },
        ],
      },
    })

    const walletSubLink = wrapper.findAll('.nav-group__sub-link').at(2)
    expect(walletSubLink?.text()).toBe('钱包')
    expect(walletSubLink?.find('.nav-group__sub-icon--placeholder').exists()).toBe(true)
  })
})