import type { AgentSseEvent } from '@/api/agent/types'

export type CopilotToolStatus = 'RUNNING' | 'SUCCEEDED' | 'FAILED'

export interface CopilotToolTrace {
  id: string
  toolName: string
  status: CopilotToolStatus
  startedAt: number
  finishedAt?: number
  latencyMs?: number
  resultSummary?: string
  errorMessage?: string
}

export interface CopilotRunUsage {
  promptTokens: number
  completionTokens: number
  quota: number
}

export interface CopilotRagCitation {
  citationId: string
  title: string
  space: string
  docNo?: string
  locator?: string
  snippet: string
  score?: number
}

export interface CopilotRagTrace {
  available: boolean
  message: string
  citations: CopilotRagCitation[]
}

export interface CopilotRunTraceState {
  status: string
  errorMessage: string
  tools: CopilotToolTrace[]
  usage: CopilotRunUsage
  rag: CopilotRagTrace
}

export function createCopilotRunTraceState(): CopilotRunTraceState {
  return {
    status: 'IDLE',
    errorMessage: '',
    tools: [],
    usage: {
      promptTokens: 0,
      completionTokens: 0,
      quota: 0
    },
    rag: {
      available: false,
      message: '',
      citations: []
    }
  }
}

export function reduceCopilotRunEvent(
  state: CopilotRunTraceState,
  event: AgentSseEvent,
  now = Date.now()
): CopilotRunTraceState {
  if (event.type === 'run_started') {
    return { ...state, status: 'RUNNING', errorMessage: '' }
  }

  if (event.type === 'status') {
    const data = asRecord(event.data)
    return { ...state, status: text(data.status) || state.status || 'RUNNING' }
  }

  if (event.type === 'cancelled') {
    return { ...state, status: 'CANCELLED' }
  }

  if (event.type === 'done') {
    return { ...state, status: 'DONE' }
  }

  if (event.type === 'error') {
    const data = asRecord(event.data)
    return { ...state, status: 'ERROR', errorMessage: text(data.message) || summarize(event.data) }
  }

  if (event.type === 'tool_start') {
    const data = asRecord(event.data)
    const toolCallId = text(data.toolCallId) || `tool-${now}`
    const toolName = text(data.toolName) || 'tool'
    const existing = state.tools.find((item) => item.id === toolCallId)
    if (existing) {
      return {
        ...state,
        status: 'WAITING_TOOL',
        tools: state.tools.map((item) =>
          item.id === toolCallId ? { ...item, toolName, status: 'RUNNING', startedAt: item.startedAt || now } : item
        )
      }
    }
    return {
      ...state,
      status: 'WAITING_TOOL',
      tools: [
        ...state.tools,
        {
          id: toolCallId,
          toolName,
          status: 'RUNNING',
          startedAt: now
        }
      ]
    }
  }

  if (event.type === 'tool_result') {
    const data = asRecord(event.data)
    const toolCallId = text(data.toolCallId) || `tool-${now}`
    const toolName = text(data.toolName) || 'tool'
    const status = normalizeStatus(text(data.status))
    const result = asRecord(data.result)
    const latencyMs = numberValue(result.latencyMs ?? data.latencyMs)
    const nextTool: CopilotToolTrace = {
      id: toolCallId,
      toolName,
      status,
      startedAt: now - (latencyMs ?? 0),
      finishedAt: now,
      latencyMs,
      resultSummary: status === 'SUCCEEDED' ? summarize(result.result ?? data.result) : undefined,
      errorMessage: status === 'FAILED' ? summarize(result.error ?? data.error ?? data.result) : undefined
    }
    const found = state.tools.some((item) => item.id === toolCallId)
    return {
      ...state,
      status: 'RUNNING',
      tools: found
        ? state.tools.map((item) =>
            item.id === toolCallId ? { ...item, ...nextTool, startedAt: item.startedAt } : item
          )
        : [...state.tools, nextTool]
    }
  }

  if (event.type === 'rag_refs') {
    const data = asRecord(event.data)
    const citations = Array.isArray(data.citations)
      ? data.citations.map(toCitation).filter((item): item is CopilotRagCitation => item !== null)
      : []
    return {
      ...state,
      status: citations.length > 0 ? 'RAG_RETRIEVING' : state.status,
      rag: {
        available: Boolean(data.available),
        message: text(data.message),
        citations
      }
    }
  }

  if (event.type === 'usage') {
    const data = asRecord(event.data)
    return {
      ...state,
      usage: {
        promptTokens: numberValue(data.promptTokens) ?? 0,
        completionTokens: numberValue(data.completionTokens) ?? 0,
        quota: numberValue(data.quota) ?? 0
      }
    }
  }

  return state
}

function normalizeStatus(status: string): CopilotToolStatus {
  return status === 'FAILED' ? 'FAILED' : 'SUCCEEDED'
}

function toCitation(value: unknown): CopilotRagCitation | null {
  const item = asRecord(value)
  const citationId = text(item.citationId) || text(item.id)
  const title = text(item.title)
  const snippet = sanitize(text(item.snippet))
  if (!citationId || !title || !snippet) return null
  return {
    citationId,
    title,
    space: text(item.space) || 'platform_docs',
    docNo: text(item.docNo),
    locator: text(item.locator),
    snippet,
    score: numberValue(item.score)
  }
}

function summarize(value: unknown): string {
  const raw = typeof value === 'string' ? value : JSON.stringify(value ?? '')
  return sanitize(raw).slice(0, 360)
}

function sanitize(value: string): string {
  return value
    .replace(/("(?:apiKey|api_key|api-key|channelKey|channel_key|secret|secretKey|secret_key|accessKey|access_key|token|authorization|password|key)"\s*:\s*")[^"]*(")/gi, '$1***REDACTED***$2')
    .replace(/Bearer\s+[A-Za-z0-9._\-+/=]{8,}/gi, 'Bearer ***REDACTED***')
    .replace(/sk-[A-Za-z0-9._\-]{6,}/gi, 'sk-***REDACTED***')
}

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' ? (value as Record<string, unknown>) : {}
}

function text(value: unknown): string {
  return typeof value === 'string' ? value : ''
}

function numberValue(value: unknown): number | undefined {
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : undefined
  }
  return undefined
}