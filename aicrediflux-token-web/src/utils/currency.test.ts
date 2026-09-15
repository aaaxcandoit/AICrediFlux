import { describe, expect, it } from 'vitest'
import { formatQuotaBilling, formatQuotaWithCurrency } from './currency'

describe('currency quota formatting', () => {
  it('把钱包余额按原始 AI Credit 单位展示', () => {
    expect(formatQuotaWithCurrency(199880)).toBe('199,880')
  })

  it('把控制台和调用统计用量按原始 AI Credit 单位展示', () => {
    expect(formatQuotaBilling(199880)).toBe('199,880')
  })
})