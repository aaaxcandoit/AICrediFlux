import { renderMarkdown } from '@/utils/markdown'
import type { CopilotToolTrace } from './copilot-events'

export const COPILOT_DOCK_WIDTH_STORAGE_KEY = 'aicrediflux:copilot:dock-width'
export const COPILOT_SESSION_ORDER_STORAGE_KEY = 'aicrediflux:copilot:session-order'
export const COPILOT_DOCK_MIN_WIDTH = 360
export const COPILOT_DOCK_MAX_WIDTH = 720
export const COPILOT_DOCK_DEFAULT_WIDTH = 420

export interface CopilotSessionLike {
  sessionNo: string
}

export interface CopilotErrorDisplay {
  status: string
  message: string
  severity: 'warning' | 'error'
}

export function clampCopilotDockWidth(width: number): number {
  if (!Number.isFinite(width)) return COPILOT_DOCK_DEFAULT_WIDTH
  return Math.min(COPILOT_DOCK_MAX_WIDTH, Math.max(COPILOT_DOCK_MIN_WIDTH, Math.round(width)))
}

export function readCopilotDockWidth(): number {
  if (typeof window === 'undefined') return COPILOT_DOCK_DEFAULT_WIDTH
  const raw = window.localStorage.getItem(COPILOT_DOCK_WIDTH_STORAGE_KEY)
  if (!raw) return COPILOT_DOCK_DEFAULT_WIDTH
  const parsed = Number(raw)
  if (!Number.isFinite(parsed)) return COPILOT_DOCK_DEFAULT_WIDTH
  return clampCopilotDockWidth(parsed)
}

export function persistCopilotDockWidth(width: number): number {
  const nextWidth = clampCopilotDockWidth(width)
  if (typeof window !== 'undefined') {
    window.localStorage.setItem(COPILOT_DOCK_WIDTH_STORAGE_KEY, String(nextWidth))
  }
  return nextWidth
}

export function readCopilotSessionOrder(): string[] {
  if (typeof window === 'undefined') return []
  const raw = window.localStorage.getItem(COPILOT_SESSION_ORDER_STORAGE_KEY)
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string' && item.length > 0) : []
  } catch {
    return []
  }
}

export function persistCopilotSessionOrder(sessions: CopilotSessionLike[]): string[] {
  const order = sessions.map((item) => item.sessionNo).filter(Boolean)
  if (typeof window !== 'undefined') {
    window.localStorage.setItem(COPILOT_SESSION_ORDER_STORAGE_KEY, JSON.stringify(order))
  }
  return order
}

export function orderCopilotSessions<T extends CopilotSessionLike>(sessions: T[], order = readCopilotSessionOrder()): T[] {
  if (order.length === 0) return [...sessions]
  const rank = new Map(order.map((sessionNo, index) => [sessionNo, index]))
  return [...sessions].sort((left, right) => {
    const leftRank = rank.get(left.sessionNo)
    const rightRank = rank.get(right.sessionNo)
    if (leftRank === undefined && rightRank === undefined) return 0
    if (leftRank === undefined) return 1
    if (rightRank === undefined) return -1
    return leftRank - rightRank
  })
}

export function moveCopilotSession<T extends CopilotSessionLike>(sessions: T[], sourceNo: string, targetNo: string): T[] {
  const sourceIndex = sessions.findIndex((item) => item.sessionNo === sourceNo)
  const targetIndex = sessions.findIndex((item) => item.sessionNo === targetNo)
  if (sourceIndex < 0 || targetIndex < 0 || sourceIndex === targetIndex) return [...sessions]
  const next = [...sessions]
  const [item] = next.splice(sourceIndex, 1)
  next.splice(targetIndex, 0, item)
  return next
}

export function renderCopilotMessageHtml(role: string, content: string): string {
  if (role !== 'assistant') return ''
  return renderMarkdown(content || '')
}

export function summarizeToolTrace(tools: CopilotToolTrace[]): string {
  if (tools.length === 0) return '工具轨迹'
  const failed = tools.filter((item) => item.status === 'FAILED').length
  if (failed > 0) return `工具轨迹：${tools.length} 次调用，${failed} 个失败`
  const running = tools.filter((item) => item.status === 'RUNNING').length
  if (running > 0) return `工具轨迹：${tools.length} 次调用，${running} 个执行中`
  return `工具轨迹：${tools.length} 次调用，全部完成`
}

export function runStatusText(status: string): string {
  const normalized = (status || 'IDLE').toUpperCase()
  if (normalized === 'IDLE') return '空闲'
  if (normalized === 'CONNECTING') return '连接中'
  if (normalized === 'RECONNECTING') return '正在恢复连接'
  if (normalized === 'RUNNING' || normalized === 'CREATED') return '运行中'
  if (normalized === 'WAITING_TOOL') return '调用工具'
  if (normalized === 'RAG_RETRIEVING') return '检索知识库'
  if (normalized === 'DONE' || normalized === 'COMPLETED' || normalized === 'SUCCEEDED') return '已完成'
  if (normalized === 'CANCELLED' || normalized === 'STOP_REQUESTED') return '已取消'
  if (normalized === 'DISCONNECTED') return '连接中断'
  if (normalized === 'ERROR' || normalized === 'FAILED') return '失败'
  return status || '空闲'
}

export function classifyCopilotError(message: string): CopilotErrorDisplay {
  const text = message || 'Copilot 请求失败'
  if (/知识库|RAG|Milvus|VectorStore/i.test(text)) {
    return { status: 'RAG_UNAVAILABLE', message: text, severity: 'warning' }
  }
  if (/network|fetch|SSE|连接|断开|Failed to fetch/i.test(text)) {
    return { status: 'DISCONNECTED', message: '连接中断，可刷新会话或重新发送', severity: 'warning' }
  }
  if (/tool|工具/i.test(text)) {
    return { status: 'ERROR', message: `工具调用失败：${text}`, severity: 'error' }
  }
  return { status: 'ERROR', message: text, severity: 'error' }
}

export interface CopilotInputKeyState {
  key: string
  shiftKey?: boolean
  isComposing?: boolean
}

export function shouldSubmitCopilotInput(event: CopilotInputKeyState, content: string, sending: boolean): boolean {
  return event.key === 'Enter' &&
    !event.shiftKey &&
    !event.isComposing &&
    !sending &&
    content.trim().length > 0
}