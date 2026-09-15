import { describe, expect, it, vi } from 'vitest'
import { useSidebarData } from './useSidebarData'
import { SIDEBAR_ICONS } from '@/components/layout/icons'
import zhCN from '@/locales/zh-CN.json'
import en from '@/locales/en.json'
import type { NavCollapsible, NavGroup, NavItem } from '@/components/layout/types'

type FlatNavItem = { title: string; url?: string; icon?: unknown }

const mocks = vi.hoisted(() => ({
  currentRole: 1,
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('@/plugins/spi/registry', () => ({
  getExtraNavGroups: () => [],
  isFeatureHidden: () => false,
}))

vi.mock('@/composables/useUserPermissions', () => ({
  useUserPermissions: () => ({
    hasRole: (required: number) => mocks.currentRole >= required,
  }),
}))

function groups(): NavGroup[] {
  return useSidebarData().value.navGroups
}

function flattenItems(items: NavItem[]): FlatNavItem[] {
  return items.flatMap((item): FlatNavItem[] => {
    if ('items' in item && item.items) {
      return [{ title: item.title, icon: item.icon }, ...item.items]
    }
    return [item]
  })
}

function urls() {
  return groups().flatMap((group) => flattenItems(group.items).map((item) => item.url))
}

function collapsible(title: string): NavCollapsible | undefined {
  return groups()
    .flatMap((group) => group.items)
    .find((item): item is NavCollapsible => item.title === title && 'items' in item)
}

describe('useSidebarData - token mall navigation', () => {
  it('普通用户侧栏把商城、订单和钱包归到 AI Credit 分组，钱包图标放在父级', () => {
    mocks.currentRole = 1
    const credit = collapsible('nav.aiCredit')

    expect(credit).toBeTruthy()
    expect(credit?.icon).toBe(SIDEBAR_ICONS.wallet)
    expect(credit?.items.map((item) => item.url)).toEqual(['/token-mall', '/orders/token', '/wallet'])
    expect(credit?.items[0]).toEqual(expect.objectContaining({ url: '/token-mall', icon: SIDEBAR_ICONS.tokenMall }))
    expect(credit?.items[1]).toEqual(expect.objectContaining({ url: '/orders/token', icon: SIDEBAR_ICONS.tokenOrders }))
    expect(credit?.items[2]).toEqual(expect.objectContaining({ url: '/wallet' }))
    expect(credit?.items[2].icon).toBeUndefined()
    expect(urls()).toContain('/token-mall')
    expect(urls()).toContain('/orders/token')
  })

  it('管理员侧栏把套餐和秒杀活动归到 AI Credit 管理分组并显示图标', () => {
    mocks.currentRole = 10
    const creditAdmin = collapsible('nav.aiCreditAdmin')

    expect(creditAdmin).toBeTruthy()
    expect(SIDEBAR_ICONS.aiCreditAdmin).toBe(SIDEBAR_ICONS.wallet)
    expect(creditAdmin?.icon).toBe(SIDEBAR_ICONS.wallet)
    expect(creditAdmin?.items.map((item) => item.url)).toEqual(['/admin/token-package', '/admin/flash-sale'])
    expect(creditAdmin?.items[0]).toEqual(
      expect.objectContaining({ url: '/admin/token-package', icon: SIDEBAR_ICONS.adminTokenPackages })
    )
    expect(creditAdmin?.items[1]).toEqual(
      expect.objectContaining({ url: '/admin/flash-sale', icon: SIDEBAR_ICONS.adminFlashSale })
    )
  })

  it('AI Credit 子菜单文案不重复父级命名，管理入口也统一显示 AI Credit', () => {
    expect(zhCN.nav.aiCreditAdmin).toBe('AI Credit')
    expect(zhCN.nav.tokenMall).toBe('商城')
    expect(zhCN.nav.tokenOrders).toBe('订单')
    expect(zhCN.nav.adminTokenPackages).toBe('套餐')
    expect(en.nav.aiCreditAdmin).toBe('AI Credit')
    expect(en.nav.tokenMall).toBe('Mall')
    expect(en.nav.tokenOrders).toBe('Orders')
    expect(en.nav.adminTokenPackages).toBe('Packages')
  })
})