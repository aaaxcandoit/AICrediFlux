import { describe, expect, it, beforeEach } from 'vitest'
import {
  clampCopilotDockWidth,
  classifyCopilotError,
  moveCopilotSession,
  orderCopilotSessions,
  persistCopilotDockWidth,
  persistCopilotSessionOrder,
  readCopilotDockWidth,
  readCopilotSessionOrder,
  renderCopilotMessageHtml,
  runStatusText,
  summarizeToolTrace,
  shouldSubmitCopilotInput
} from './copilot-dock-ui'

describe('copilot dock ui helpers', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('clamps and persists dock width for desktop resizing', () => {
    expect(clampCopilotDockWidth(200)).toBe(360)
    expect(clampCopilotDockWidth(520)).toBe(520)
    expect(clampCopilotDockWidth(900)).toBe(720)

    persistCopilotDockWidth(680)
    expect(localStorage.getItem('aicrediflux:copilot:dock-width')).toBe('680')
    expect(readCopilotDockWidth()).toBe(680)
  })

  it('falls back to default width when stored width is invalid', () => {
    localStorage.setItem('aicrediflux:copilot:dock-width', 'broken')
    expect(readCopilotDockWidth()).toBe(420)
  })

  it('persists and applies local session order', () => {
    const sessions = [{ sessionNo: 'AS1' }, { sessionNo: 'AS2' }, { sessionNo: 'AS3' }]
    persistCopilotSessionOrder([{ sessionNo: 'AS3' }, { sessionNo: 'AS1' }])

    expect(readCopilotSessionOrder()).toEqual(['AS3', 'AS1'])
    expect(orderCopilotSessions(sessions).map((item) => item.sessionNo)).toEqual(['AS3', 'AS1', 'AS2'])
  })

  it('moves one session before the drop target without mutating original list', () => {
    const sessions = [{ sessionNo: 'AS1' }, { sessionNo: 'AS2' }, { sessionNo: 'AS3' }]
    const moved = moveCopilotSession(sessions, 'AS3', 'AS1')

    expect(moved.map((item) => item.sessionNo)).toEqual(['AS3', 'AS1', 'AS2'])
    expect(sessions.map((item) => item.sessionNo)).toEqual(['AS1', 'AS2', 'AS3'])
  })

  it('renders assistant content as sanitized markdown and keeps user content plain', () => {
    const assistantHtml = renderCopilotMessageHtml('assistant', '**余额**\n\n<script>alert(1)</script>')
    const userHtml = renderCopilotMessageHtml('user', '**不要渲染我**')

    expect(assistantHtml).toContain('<strong>余额</strong>')
    expect(assistantHtml).not.toContain('<script>')
    expect(userHtml).toBe('')
  })

  it('summarizes tool trace for collapsed display', () => {
    expect(summarizeToolTrace([])).toBe('工具轨迹')
    expect(summarizeToolTrace([
      { id: '1', toolName: 'walletBalance', status: 'SUCCEEDED', startedAt: 1 },
      { id: '2', toolName: 'channelStatus', status: 'FAILED', startedAt: 2 }
    ])).toBe('工具轨迹：2 次调用，1 个失败')
  })

  it('maps raw run status to Chinese labels', () => {
    expect(runStatusText('idle')).toBe('空闲')
    expect(runStatusText('WAITING_TOOL')).toBe('调用工具')
    expect(runStatusText('RAG_RETRIEVING')).toBe('检索知识库')
    expect(runStatusText('CANCELLED')).toBe('已取消')
    expect(runStatusText('DISCONNECTED')).toBe('连接中断')
  })

  it('classifies rag, network and tool errors for display', () => {
    expect(classifyCopilotError('Milvus VectorStore 尚未配置')).toMatchObject({ status: 'RAG_UNAVAILABLE', severity: 'warning' })
    expect(classifyCopilotError('Failed to fetch')).toMatchObject({ status: 'DISCONNECTED', severity: 'warning' })
    expect(classifyCopilotError('tool channelStatus failed')).toMatchObject({ status: 'ERROR', severity: 'error' })
  })

  it('submits on Enter and keeps Shift Enter for newline', () => {
    expect(shouldSubmitCopilotInput({ key: 'Enter', shiftKey: false, isComposing: false }, '你好', false)).toBe(true)
    expect(shouldSubmitCopilotInput({ key: 'Enter', shiftKey: true, isComposing: false }, '你好', false)).toBe(false)
    expect(shouldSubmitCopilotInput({ key: 'Enter', shiftKey: false, isComposing: false }, '   ', false)).toBe(false)
    expect(shouldSubmitCopilotInput({ key: 'Enter', shiftKey: false, isComposing: false }, '你好', true)).toBe(false)
    expect(shouldSubmitCopilotInput({ key: 'a', shiftKey: false, isComposing: false }, '你好', false)).toBe(false)
  })
})