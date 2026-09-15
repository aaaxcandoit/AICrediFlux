import { describe, expect, it } from 'vitest'
import { createCopilotRunTraceState, reduceCopilotRunEvent } from './copilot-events'

describe('copilot event reducer', () => {
  it('records status lifecycle events', () => {
    let state = createCopilotRunTraceState()

    state = reduceCopilotRunEvent(state, { type: 'run_started', data: { runNo: 'AR1' } })
    expect(state.status).toBe('RUNNING')

    state = reduceCopilotRunEvent(state, { type: 'status', data: { status: 'WAITING_TOOL' } })
    expect(state.status).toBe('WAITING_TOOL')

    state = reduceCopilotRunEvent(state, { type: 'cancelled', data: { runNo: 'AR1' } })
    expect(state.status).toBe('CANCELLED')
  })

  it('records error event message without dropping existing traces', () => {
    const state = reduceCopilotRunEvent(createCopilotRunTraceState(), {
      type: 'error',
      data: { message: '模型调用失败' }
    })

    expect(state.status).toBe('ERROR')
    expect(state.errorMessage).toBe('模型调用失败')
  })

  it('records tool timeline with sanitized result and latency', () => {
    let state = createCopilotRunTraceState()

    state = reduceCopilotRunEvent(state, {
      type: 'tool_start',
      data: { toolName: 'walletBalance', toolCallId: 'call-1' }
    }, 1000)
    state = reduceCopilotRunEvent(state, {
      type: 'tool_result',
      data: {
        toolName: 'walletBalance',
        toolCallId: 'call-1',
        status: 'SUCCEEDED',
        result: { result: '{"apiKey":"sk-secret-123456","balance":128000}', latencyMs: 38 }
      }
    }, 1038)

    expect(state.tools).toHaveLength(1)
    expect(state.tools[0]).toMatchObject({
      id: 'call-1',
      toolName: 'walletBalance',
      status: 'SUCCEEDED',
      latencyMs: 38
    })
    expect(state.tools[0].resultSummary).toContain('128000')
    expect(state.tools[0].resultSummary).toContain('***REDACTED***')
    expect(state.tools[0].resultSummary).not.toContain('sk-secret-123456')
  })

  it('updates current run usage from usage event', () => {
    const state = reduceCopilotRunEvent(createCopilotRunTraceState(), {
      type: 'usage',
      data: { promptTokens: 12, completionTokens: 8, quota: 20 }
    })

    expect(state.usage).toEqual({
      promptTokens: 12,
      completionTokens: 8,
      quota: 20
    })
  })

  it('records rag references from rag_refs event', () => {
    const state = reduceCopilotRunEvent(createCopilotRunTraceState(), {
      type: 'rag_refs',
      data: {
        available: true,
        citations: [
          {
            citationId: 'RC1',
            title: 'SiliconFlow 接入说明',
            space: 'platform_docs',
            locator: 'SiliconFlow 接入',
            snippet: 'Base URL 配置为上游兼容地址。',
            score: 0.91
          }
        ]
      }
    })

    expect(state.status).toBe('RAG_RETRIEVING')
    expect(state.rag.available).toBe(true)
    expect(state.rag.citations).toHaveLength(1)
    expect(state.rag.citations[0].title).toBe('SiliconFlow 接入说明')
  })
})