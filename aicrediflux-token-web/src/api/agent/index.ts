import { request } from '@/utils/request'
import { getToken } from '@/utils/auth'
import type {
  AgentMessage,
  AgentRun,
  AgentRunSubmission,
  AgentSession,
  AgentSseEvent,
  ChannelStatusResult,
  ModelPriceResult,
  UsageSummaryResult,
  WalletBalanceResult
} from './types'

const BASE_URL = import.meta.env.VITE_API_BASE || ''

export const getAgentSessions = () => request.get<AgentSession[]>('/api/agent/sessions')
export const createAgentSession = (payload?: { title?: string; model?: string }) =>
  request.post<AgentSession>('/api/agent/sessions', payload ?? {})
export const renameAgentSession = (sessionNo: string, title: string) =>
  request.patch<AgentSession>('/api/agent/sessions/' + encodeURIComponent(sessionNo), { title })
export const deleteAgentSession = (sessionNo: string) =>
  request.delete('/api/agent/sessions/' + encodeURIComponent(sessionNo))
export const getAgentMessages = (sessionNo: string) =>
  request.get<AgentMessage[]>('/api/agent/sessions/' + encodeURIComponent(sessionNo) + '/messages')
export const sendAgentMessage = (sessionNo: string, payload: { content: string; model?: string }) =>
  request.post<AgentRunSubmission>(
    '/api/agent/sessions/' + encodeURIComponent(sessionNo) + '/messages',
    payload
  )
export const stopAgentRun = (runNo: string) =>
  request.post<AgentRun>('/api/agent/runs/' + encodeURIComponent(runNo) + '/stop')
export const getAgentWalletBalance = () =>
  request.get<WalletBalanceResult>('/api/agent/tools/wallet-balance')
export const getAgentUsage = (params?: { hours?: number; model?: string; tokenId?: number }) =>
  request.get<UsageSummaryResult>('/api/agent/tools/usage', { params })
export const getAgentModelPrice = (params: {
  model: string
  group?: string
  promptTokens?: number
  completionTokens?: number
}) => request.get<ModelPriceResult>('/api/agent/tools/model-price', { params })
export const getAgentChannelStatus = (params?: { model?: string; hours?: number }) =>
  request.get<ChannelStatusResult>('/api/agent/tools/channel-status', { params, _silent: true })

export interface AgentEventCallbacks {
  onEvent?: (event: AgentSseEvent) => void
  onError?: (message: string) => void
  onDone?: () => void
  onReconnect?: (attempt: number) => void
  maxReconnects?: number
}

export interface AgentEventStreamHandle {
  abort: () => void
}

export function streamAgentRunEvents(runNo: string, callbacks: AgentEventCallbacks): AgentEventStreamHandle {
  const controller = new AbortController()
  const maxReconnects = callbacks.maxReconnects ?? 1
  let reconnects = 0

  ;(async () => {
    while (!controller.signal.aborted) {
      try {
        const response = await fetch(`${BASE_URL}/api/agent/runs/${encodeURIComponent(runNo)}/events`, {
          method: 'GET',
          headers: {
            Accept: 'text/event-stream',
            ...(getToken() ? { aicrediflux: getToken() as string } : {})
          },
          signal: controller.signal
        })
        if (!response.ok || !response.body) {
          if (response.status >= 500 && reconnects < maxReconnects) {
            callbacks.onReconnect?.(++reconnects)
            continue
          }
          callbacks.onError?.(`HTTP ${response.status}`)
          return
        }
        await readSseStream(response.body, controller.signal, callbacks)
        callbacks.onDone?.()
        return
      } catch (error) {
        if (controller.signal.aborted) return
        if (reconnects < maxReconnects) {
          callbacks.onReconnect?.(++reconnects)
          continue
        }
        callbacks.onError?.(error instanceof Error ? error.message : 'SSE 连接失败')
        return
      }
    }
  })()

  return { abort: () => controller.abort() }
}

async function readSseStream(
  body: ReadableStream<Uint8Array>,
  signal: AbortSignal,
  callbacks: AgentEventCallbacks
): Promise<void> {
  const reader = body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  while (true) {
    if (signal.aborted) return
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    let sepIndex: number
    while ((sepIndex = buffer.indexOf('\n\n')) !== -1) {
      const raw = buffer.slice(0, sepIndex)
      buffer = buffer.slice(sepIndex + 2)
      const event = parseSseBlock(raw)
      if (event) callbacks.onEvent?.(event)
    }
  }
}

function parseSseBlock(raw: string): AgentSseEvent | null {
  const lines = raw.split(/\r?\n/)
  const eventLine = lines.find((line) => line.startsWith('event:'))
  const dataLines = lines.filter((line) => line.startsWith('data:'))
  const type = eventLine?.slice(6).trim() || 'message'
  const dataText = dataLines.map((line) => line.slice(5).trimStart()).join('\n')
  if (!dataText) return { type, data: null }
  try {
    return { type, data: JSON.parse(dataText) }
  } catch {
    return { type, data: dataText }
  }
}