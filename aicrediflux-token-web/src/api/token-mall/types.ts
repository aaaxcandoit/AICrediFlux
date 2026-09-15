export type IntegerValue = number | string | bigint

export interface TokenPackage {
  id: IntegerValue
  name: string
  description?: string
  creditAmount: IntegerValue
  originalPrice: IntegerValue
  salePrice: IntegerValue
  status: number
  sort: number
}

export interface FlashSaleCampaign {
  id: IntegerValue
  packageId: IntegerValue
  activityName: string
  flashPrice: IntegerValue
  creditAmount: IntegerValue
  totalStock: number
  availableStock: number
  startTime: number | string
  endTime: number | string
  status: number
}

export interface TokenOrder {
  id?: IntegerValue
  orderNo: string
  packageName: string
  creditAmount: IntegerValue
  payAmount: IntegerValue
  orderSource: 'ORDINARY' | 'FLASH_SALE'
  orderStatus: 'CREATED' | 'PAID' | 'CLOSED'
  payStatus: 'UNPAID' | 'PAID'
  creditStatus: 'PENDING' | 'GRANTED'
  expireTime: number | string
  payTime?: number | string
  createTime: number | string
}

export interface CampaignListResponse {
  serverTime: number | string
  campaigns: FlashSaleCampaign[]
}

export interface FlashSaleSubmission {
  requestNo: string
  orderNo: string
  status: 'PENDING'
}

export interface SubmissionStatus {
  status: 'PENDING' | 'SUCCESS' | 'FAILED'
  reason?: string
  order?: TokenOrder
}