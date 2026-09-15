import { request } from '@/utils/request'
import type {
  CampaignListResponse,
  FlashSaleSubmission,
  SubmissionStatus,
  TokenOrder,
  TokenPackage
} from './types'

export const getTokenPackages = () => request.get<TokenPackage[]>('/api/token-packages')
export const createTokenOrder = (packageId: number | string | bigint) =>
  request.post<TokenOrder>('/api/token-packages/' + packageId + '/orders')
export const getFlashSaleCampaigns = () =>
  request.get<CampaignListResponse>('/api/flash-sale/campaigns')
export const submitFlashSale = (campaignId: number | string | bigint) =>
  request.post<FlashSaleSubmission>('/api/flash-sale/' + campaignId)
export const getTokenOrders = () => request.get<TokenOrder[]>('/api/flash-sale/orders')
export const getTokenOrder = (orderNo: string) =>
  request.get<TokenOrder>('/api/flash-sale/orders/' + encodeURIComponent(orderNo))
export const getSubmissionStatus = (orderNo: string) =>
  request.get<SubmissionStatus>(
    '/api/flash-sale/orders/' + encodeURIComponent(orderNo) + '/status',
    { _silent: true }
  )
export const mockPayTokenOrder = (orderNo: string) =>
  request.post<TokenOrder>('/api/flash-sale/orders/' + encodeURIComponent(orderNo) + '/mock-pay')
export const getFlashSaleWallet = () =>
  request.get<{ balance: number | string | bigint }>('/api/flash-sale/wallet')
export const getAdminTokenPackages = () =>
  request.get<TokenPackage[]>('/api/admin/token-packages')
export const saveAdminTokenPackage = (pack: Partial<TokenPackage>) =>
  pack.id
    ? request.put<TokenPackage>('/api/admin/token-packages/' + pack.id, pack)
    : request.post<TokenPackage>('/api/admin/token-packages', pack)
export const getAdminCampaigns = () =>
  request.get<import('./types').FlashSaleCampaign[]>('/api/admin/flash-sale')
export const saveAdminCampaign = (campaign: Partial<import('./types').FlashSaleCampaign>) =>
  campaign.id
    ? request.put<import('./types').FlashSaleCampaign>('/api/admin/flash-sale/' + campaign.id, campaign)
    : request.post<import('./types').FlashSaleCampaign>('/api/admin/flash-sale', campaign)
export const publishAdminCampaign = (id: number | string | bigint) =>
  request.post<import('./types').FlashSaleCampaign>('/api/admin/flash-sale/' + id + '/publish')
export const reconcileAdminCampaign = (id: number | string | bigint) =>
  request.post<import('./types').FlashSaleCampaign>('/api/admin/flash-sale/' + id + '/reconcile')