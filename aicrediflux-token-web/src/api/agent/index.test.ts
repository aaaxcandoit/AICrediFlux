import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'

const patch = vi.fn()
const deleteRequest = vi.fn()

vi.mock('@/utils/request', () => ({
  request: {
    patch,
    delete: deleteRequest
  }
}))

vi.mock('@/utils/auth', () => ({
  getToken: vi.fn(() => 'token')
}))

describe('agent api', () => {
  beforeEach(() => {
    patch.mockReset()
    deleteRequest.mockReset()
    vi.restoreAllMocks()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('renames agent session with PATCH endpoint', async () => {
    patch.mockResolvedValueOnce({ sessionNo: 'AS001', title: '成本复盘' })
    const { renameAgentSession } = await import('./index')

    const result = await renameAgentSession('AS001', '成本复盘')

    expect(result.title).toBe('成本复盘')
    expect(patch).toHaveBeenCalledWith('/api/agent/sessions/AS001', { title: '成本复盘' })
  })

  it('deletes agent session with DELETE endpoint', async () => {
    deleteRequest.mockResolvedValueOnce({})
    const { deleteAgentSession } = await import('./index')

    await deleteAgentSession('AS001')

    expect(deleteRequest).toHaveBeenCalledWith('/api/agent/sessions/AS001')
  })

  it('reconnects once when SSE fetch fails before a successful stream', async () => {
    const fetchMock = vi.fn()
      .mockRejectedValueOnce(new Error('Failed to fetch'))
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        body: streamFrom('event: done\ndata: {"status":"COMPLETED"}\n\n')
      })
    vi.stubGlobal('fetch', fetchMock)
    const onReconnect = vi.fn()
    const onEvent = vi.fn()
    const onDone = vi.fn()
    const { streamAgentRunEvents } = await import('./index')

    streamAgentRunEvents('AR001', { onReconnect, onEvent, onDone, maxReconnects: 1 })
    await waitFor(() => expect(onDone).toHaveBeenCalledTimes(1))

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(onReconnect).toHaveBeenCalledWith(1)
    expect(onEvent).toHaveBeenCalledWith({ type: 'done', data: { status: 'COMPLETED' } })
  })

  it('does not reconnect after explicit abort', async () => {
    let rejectRead: ((error: Error) => void) | undefined
    const body = new ReadableStream<Uint8Array>({
      pull() {
        return new Promise<void>((_, reject) => {
          rejectRead = reject
        })
      }
    })
    const fetchMock = vi.fn().mockResolvedValueOnce({ ok: true, status: 200, body })
    vi.stubGlobal('fetch', fetchMock)
    const onReconnect = vi.fn()
    const { streamAgentRunEvents } = await import('./index')

    const handle = streamAgentRunEvents('AR001', { onReconnect, maxReconnects: 1 })
    await Promise.resolve()
    handle.abort()
    rejectRead?.(new Error('connection lost'))
    await new Promise((resolve) => setTimeout(resolve, 0))

    expect(onReconnect).not.toHaveBeenCalled()
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})

function streamFrom(text: string): ReadableStream<Uint8Array> {
  return new ReadableStream<Uint8Array>({
    start(controller) {
      controller.enqueue(new TextEncoder().encode(text))
      controller.close()
    }
  })
}

async function waitFor(assertion: () => void): Promise<void> {
  const startedAt = Date.now()
  let lastError: unknown
  while (Date.now() - startedAt < 1000) {
    try {
      assertion()
      return
    } catch (error) {
      lastError = error
      await new Promise((resolve) => setTimeout(resolve, 10))
    }
  }
  throw lastError
}