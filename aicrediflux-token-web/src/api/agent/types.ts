export interface AgentSession {
  id?: number | string
  sessionNo: string
  userId: number
  title: string
  model?: string
  status: string
  createTime?: string
  updateTime?: string
}

export interface AgentMessage {
  id?: number | string
  messageNo: string
  sessionNo: string
  userId: number
  role: 'system' | 'user' | 'assistant' | 'tool' | string
  content: string
  toolCallId?: string
  metadata?: string
  usageJson?: string
  createTime?: string
}

export interface AgentRun {
  runNo: string
  sessionNo: string
  userId: number
  model: string
  status: string
  requestId?: string
  source?: string
  startedAt?: string
  finishedAt?: string
  errorMessage?: string
  totalPromptTokens?: number
  totalCompletionTokens?: number
  totalQuota?: number | string
  stopRequested?: number
}

export interface AgentRunSubmission {
  sessionNo: string
  runNo: string
  status: string
}

export interface WalletBalanceResult {
  balance: number | string
}

export interface UsageModelSummary {
  modelName: string
  requestCount: number
  quota: number
  promptTokens: number
  completionTokens: number
}

export interface UsageSummaryResult {
  hours: number
  requestCount: number
  totalQuota: number
  promptTokens: number
  completionTokens: number
  models: UsageModelSummary[]
}

export interface ModelPriceResult {
  modelName: string
  group: string
  configured: boolean
  quotaType: number
  modelRatio: number
  completionRatio: number
  modelPrice: number
  groupRatio: number
  estimatedCreditCost: number
  note: string
}

export interface ChannelSummary {
  id: number
  name?: string
  status?: number
  group?: string
  priority?: number
  weight?: number
  recentRequests: number
  recentErrors: number
  successRate: number
  avgLatencyMs: number
  recentQuota: number
}

export interface ChannelStatusResult {
  hours: number
  channels: ChannelSummary[]
}

export interface AgentSseEvent {
  type: string
  data: unknown
}
