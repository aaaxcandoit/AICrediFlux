// 红线2：编码前已查证 @element-plus/icons-vue 图标名，不存在时 fallback Iconify（unplugin-icons autoInstall）
// 与 M1-C EmptyState/ErrorState 一致用 `<i class="i-ep-xxx" />` 模式

export const SIDEBAR_ICONS = {
  // Chat 分组
  playground: 'i-ep-magic-stick',
  chat: 'i-ep-chat-dot-round',
  // General 分组
  overview: 'i-ep-data-line',
  dashboard: 'i-ep-menu',
  analytics: 'i-ep-trend-charts',
  apiKeys: 'i-ep-key',
  usageLogs: 'i-ep-document',
  taskLogs: 'i-ep-finished',
  // Personal 分组
  aiCredit: 'i-ep-wallet',
  wallet: 'i-ep-wallet',
  tokenMall: 'i-ep-shopping-cart',
  tokenOrders: 'i-ep-list',
  profile: 'i-ep-user',
  // Admin 分组
  aiCreditAdmin: 'i-ep-wallet',
  channels: 'i-ep-connection',
  models: 'i-ep-box',
  deployments: 'i-ep-cpu',
  users: 'i-ep-user-filled',
  redemptionCodes: 'i-ep-ticket',
  adminTokenPackages: 'i-ep-goods',
  adminFlashSale: 'i-ep-timer',
  adminRagKnowledge: 'i-ep-document',
  subscriptions: 'i-ep-credit-card',
  systemSettings: 'i-ep-setting'
} as const

export type SidebarIconKey = keyof typeof SIDEBAR_ICONS

// 头部 actions 图标
export const HEADER_ICONS = {
  search: 'i-ep-search',
  notifications: 'i-ep-bell',
  language: 'i-lucide-languages', // M1-B（M1-A-Judge-T2）：原 ep-languege 无效，改用 lucide Languages
  theme: 'i-ep-moon',
  config: 'i-ep-setting',
  signOut: 'i-ep-switch-button',
  close: 'i-ep-close',
  menu: 'i-ep-menu',
  chevronLeft: 'i-ep-arrow-left',
  chevronRight: 'i-ep-arrow-right'
} as const

// ProfileDropdown 头部 actions
export const PROFILE_ICONS = {
  profile: 'i-ep-user',
  wallet: 'i-ep-wallet',
  tokenMall: 'i-ep-shopping-cart',
  tokenOrders: 'i-ep-list',
  signOut: 'i-ep-switch-button'
} as const
