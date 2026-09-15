<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createAgentSession,
  deleteAgentSession,
  getAgentMessages,
  getAgentSessions,
  renameAgentSession,
  sendAgentMessage,
  stopAgentRun,
  streamAgentRunEvents,
  type AgentEventStreamHandle
} from '@/api/agent'
import type { AgentMessage, AgentRunSubmission, AgentSession } from '@/api/agent/types'
import { useCopilotStore } from '@/store/modules/copilot'
import { getUserModels } from '@/api/playground'
import type { ModelOption } from '@/api/playground/types'
import { formatAiCredit } from '@/utils/currency'
import {
  createCopilotRunTraceState,
  reduceCopilotRunEvent,
  type CopilotRagCitation,
  type CopilotToolTrace
} from './copilot-events'
import {
  classifyCopilotError,
  clampCopilotDockWidth,
  moveCopilotSession,
  orderCopilotSessions,
  persistCopilotDockWidth,
  persistCopilotSessionOrder,
  readCopilotDockWidth,
  renderCopilotMessageHtml,
  runStatusText,
  shouldSubmitCopilotInput,
  summarizeToolTrace
} from './copilot-dock-ui'

const copilotStore = useCopilotStore()
const { open } = storeToRefs(copilotStore)

const sessions = ref<AgentSession[]>([])
const activeSessionNo = ref('')
const messages = ref<AgentMessage[]>([])
const input = ref('')
const model = ref('')
const loading = ref(false)
const modelsLoading = ref(false)
const availableModels = ref<ModelOption[]>([])
const sending = ref(false)
const running = ref<AgentRunSubmission | null>(null)
const sseStatus = ref('idle')
const streamHandle = ref<AgentEventStreamHandle | null>(null)
const currentAssistantMessageNo = ref('')
const runTrace = ref(createCopilotRunTraceState())
const toolsExpanded = ref(false)
const ragExpanded = ref(false)
const dockWidth = ref(readCopilotDockWidth())
const resizing = ref(false)
const draggedSessionNo = ref('')

const dockClass = computed(() => ['copilot-dock', {
  'copilot-dock--open': open.value,
  'copilot-dock--resizing': resizing.value
}])
const dockStyle = computed(() => open.value ? { '--copilot-dock-width': `${dockWidth.value}px` } : undefined)
const runStatusLabel = computed(() => runStatusText(sseStatus.value))
const runQuotaText = computed(() => formatAiCredit(runTrace.value.usage.quota))
const hasRagTrace = computed(() =>
  runTrace.value.rag.citations.length > 0 ||
  (!runTrace.value.rag.available && Boolean(runTrace.value.rag.message))
)
const hasRunTrace = computed(() =>
  hasRagTrace.value ||
  runTrace.value.tools.length > 0 ||
  runTrace.value.usage.promptTokens > 0 ||
  runTrace.value.usage.completionTokens > 0 ||
  runTrace.value.usage.quota > 0
)
const toolTraceTitle = computed(() => summarizeToolTrace(runTrace.value.tools))
const ragTraceTitle = computed(() => {
  if (!runTrace.value.rag.available) return '知识库状态'
  const count = runTrace.value.rag.citations.length
  return count > 0 ? '引用来源 ' + count : '引用来源'
})

onMounted(() => {
  loadAvailableModels().catch(() => undefined)
  loadSessions().catch(() => undefined)
})

onBeforeUnmount(() => {
  streamHandle.value?.abort()
  stopResizeListeners()
})

async function loadSessions(): Promise<void> {
  loading.value = true
  try {
    sessions.value = orderCopilotSessions(await getAgentSessions())
    if (!activeSessionNo.value && sessions.value[0]) {
      activeSessionNo.value = sessions.value[0].sessionNo
      syncModelFromSession(sessions.value[0])
      await loadMessages(activeSessionNo.value)
    }
  } finally {
    loading.value = false
  }
}

async function createSession(): Promise<void> {
  if (!ensureSelectedModel()) return
  const session = await createAgentSession({ title: 'AICrediFlux Copilot', model: model.value })
  sessions.value = [session, ...sessions.value]
  persistCopilotSessionOrder(sessions.value)
  activeSessionNo.value = session.sessionNo
  messages.value = []
  runTrace.value = createCopilotRunTraceState()
  toolsExpanded.value = false
  ragExpanded.value = false
}

async function ensureSession(): Promise<string> {
  if (activeSessionNo.value) return activeSessionNo.value
  await createSession()
  return activeSessionNo.value
}

async function loadMessages(sessionNo: string): Promise<void> {
  activeSessionNo.value = sessionNo
  const session = sessions.value.find((item) => item.sessionNo === sessionNo)
  syncModelFromSession(session)
  messages.value = await getAgentMessages(sessionNo)
  runTrace.value = createCopilotRunTraceState()
  toolsExpanded.value = false
  ragExpanded.value = false
}

async function send(): Promise<void> {
  const content = input.value.trim()
  if (!content) return
  if (!ensureSelectedModel()) return
  sending.value = true
  try {
    const sessionNo = await ensureSession()
    messages.value.push({
      messageNo: `local-${Date.now()}`,
      sessionNo,
      userId: 0,
      role: 'user',
      content
    })
    input.value = ''
    sseStatus.value = 'CONNECTING'
    running.value = await sendAgentMessage(sessionNo, { content, model: model.value })
    sseStatus.value = running.value.status
    runTrace.value = createCopilotRunTraceState()
    toolsExpanded.value = false
    ragExpanded.value = false
    currentAssistantMessageNo.value = `assistant-${running.value.runNo}`
    messages.value.push({
      messageNo: currentAssistantMessageNo.value,
      sessionNo,
      userId: 0,
      role: 'assistant',
      content: ''
    })
    streamRun(running.value.runNo)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Copilot 发送失败')
  } finally {
    sending.value = false
  }
}

async function stop(): Promise<void> {
  if (!running.value) return
  await stopAgentRun(running.value.runNo)
  streamHandle.value?.abort()
  sseStatus.value = 'CANCELLED'
  running.value = null
}

async function loadAvailableModels(): Promise<void> {
  modelsLoading.value = true
  try {
    availableModels.value = await getUserModels()
    ensureSelectedModel(false)
  } catch {
    availableModels.value = []
  } finally {
    modelsLoading.value = false
  }
}

function ensureSelectedModel(showMessage = true): boolean {
  if (model.value) return true
  const first = availableModels.value[0]?.value
  if (first) {
    model.value = first
    return true
  }
  if (showMessage) ElMessage.warning('当前没有可用模型，请先在渠道中配置并启用模型能力')
  return false
}

function syncModelFromSession(session?: AgentSession): void {
  if (session?.model) {
    model.value = session.model
    return
  }
  ensureSelectedModel(false)
}

async function handleSessionCommand(session: AgentSession, command: string | number | object): Promise<void> {
  if (command === 'rename') {
    await renameSession(session)
    return
  }
  if (command === 'delete') {
    await confirmDeleteSession(session)
  }
}

async function renameSession(session: AgentSession): Promise<void> {
  try {
    const result = await ElMessageBox.prompt('请输入新的会话名称', '重命名会话', {
      inputValue: session.title,
      inputPattern: /\S/,
      inputErrorMessage: '会话名称不能为空',
      confirmButtonText: '保存',
      cancelButtonText: '取消'
    })
    const nextTitle = String(result.value || '').trim()
    if (!nextTitle || nextTitle === session.title) return
    const renamed = await renameAgentSession(session.sessionNo, nextTitle)
    sessions.value = sessions.value.map((item) =>
      item.sessionNo === renamed.sessionNo ? { ...item, title: renamed.title } : item
    )
    ElMessage.success('会话已重命名')
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error instanceof Error ? error.message : '重命名会话失败')
  }
}

async function confirmDeleteSession(session: AgentSession): Promise<void> {
  if (running.value && session.sessionNo === activeSessionNo.value) {
    ElMessage.warning('当前会话正在运行，请停止后再删除')
    return
  }
  try {
    await ElMessageBox.confirm(`确定删除会话“${session.title}”？历史消息会保留在后端审计记录中。`, '删除会话', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      confirmButtonClass: 'el-button--danger'
    })
    await deleteAgentSession(session.sessionNo)
    const wasActive = session.sessionNo === activeSessionNo.value
    const nextSessions = sessions.value.filter((item) => item.sessionNo !== session.sessionNo)
    sessions.value = nextSessions
    persistCopilotSessionOrder(nextSessions)
    if (wasActive) {
      activeSessionNo.value = ''
      messages.value = []
      runTrace.value = createCopilotRunTraceState()
      const next = nextSessions[0]
      if (next) await loadMessages(next.sessionNo)
    }
    ElMessage.success('会话已删除')
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error instanceof Error ? error.message : '删除会话失败')
  }
}

function handleSessionDragStart(session: AgentSession, event: DragEvent): void {
  draggedSessionNo.value = session.sessionNo
  event.dataTransfer?.setData('text/plain', session.sessionNo)
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move'
}

function handleSessionDrop(target: AgentSession): void {
  const sourceNo = draggedSessionNo.value
  if (!sourceNo || sourceNo === target.sessionNo) return
  sessions.value = moveCopilotSession(sessions.value, sourceNo, target.sessionNo)
  persistCopilotSessionOrder(sessions.value)
  draggedSessionNo.value = ''
}

function handleSessionDragEnd(): void {
  draggedSessionNo.value = ''
}
function handleInputKeydown(event: Event): void {
  if (!(event instanceof KeyboardEvent)) return
  if (!shouldSubmitCopilotInput(event, input.value, sending.value)) return
  event.preventDefault()
  send()
}
function streamRun(runNo: string): void {
  streamHandle.value?.abort()
  streamHandle.value = streamAgentRunEvents(runNo, {
    maxReconnects: 1,
    onEvent(event) {
      runTrace.value = reduceCopilotRunEvent(runTrace.value, event)
      if (event.type === 'status' || event.type === 'run_started') {
        sseStatus.value = runTrace.value.status || 'RUNNING'
      }
      if (event.type === 'tool_start') {
        sseStatus.value = 'WAITING_TOOL'
      }
      if (event.type === 'rag_refs') {
        const data = event.data as { available?: boolean; citations?: unknown[]; message?: string }
        if (data.citations?.length) sseStatus.value = 'RAG_RETRIEVING'
      }
      if (event.type === 'text_delta') {
        const data = event.data as { delta?: string }
        appendAssistantDelta(data.delta || '')
        if (sseStatus.value === 'RECONNECTING' || sseStatus.value === 'CONNECTING') sseStatus.value = 'RUNNING'
      }
      if (event.type === 'done') {
        sseStatus.value = 'DONE'
        running.value = null
      }
      if (event.type === 'cancelled') {
        sseStatus.value = 'CANCELLED'
        running.value = null
      }
      if (event.type === 'error') {
        const data = event.data as { message?: string }
        const display = classifyCopilotError(data.message || runTrace.value.errorMessage)
        sseStatus.value = display.status
        running.value = null
        if (display.severity === 'warning') ElMessage.warning(display.message)
        else ElMessage.error(display.message)
      }
    },
    onReconnect() {
      sseStatus.value = 'RECONNECTING'
      ElMessage.warning('SSE 连接中断，正在恢复连接')
    },
    onError(message) {
      const display = classifyCopilotError(message)
      sseStatus.value = display.status
      running.value = null
      if (display.severity === 'warning') ElMessage.warning(display.message)
      else ElMessage.error(display.message)
    }
  })
}
function appendAssistantDelta(delta: string): void {
  if (!delta) return
  const messageNo = currentAssistantMessageNo.value
  const index = messages.value.findIndex((item) => item.messageNo === messageNo)
  if (index >= 0) {
    messages.value[index] = {
      ...messages.value[index],
      content: `${messages.value[index].content || ''}${delta}`
    }
  }
}

function beginResize(event: PointerEvent): void {
  if (!open.value) return
  resizing.value = true
  event.preventDefault()
  window.addEventListener('pointermove', resizeDock)
  window.addEventListener('pointerup', finishResize)
}

function resizeDock(event: PointerEvent): void {
  dockWidth.value = clampCopilotDockWidth(window.innerWidth - event.clientX)
}

function finishResize(): void {
  if (!resizing.value) return
  resizing.value = false
  dockWidth.value = persistCopilotDockWidth(dockWidth.value)
  stopResizeListeners()
}

function stopResizeListeners(): void {
  window.removeEventListener('pointermove', resizeDock)
  window.removeEventListener('pointerup', finishResize)
}

function toolStatusText(status: CopilotToolTrace['status']): string {
  if (status === 'RUNNING') return '执行中'
  if (status === 'FAILED') return '失败'
  return '完成'
}

function toolStatusIcon(status: CopilotToolTrace['status']): string {
  if (status === 'RUNNING') return 'i-lucide-loader-2'
  if (status === 'FAILED') return 'i-lucide-circle-alert'
  return 'i-lucide-circle-check'
}

function citationScore(citation: CopilotRagCitation): string {
  if (citation.score === undefined) return ''
  return Math.round(citation.score * 100) + '%'
}
</script>

<template>
  <aside :class="dockClass" :style="dockStyle">
    <button
      v-if="open"
      class="copilot-dock__resize-handle"
      type="button"
      aria-label="调整 Copilot 宽度"
      @pointerdown="beginResize"
    />

    <button class="copilot-dock__rail" type="button" @click="copilotStore.toggle()">
      <i class="i-lucide-bot" />
      <span>Copilot</span>
    </button>

    <section v-if="open" class="copilot-dock__panel">
      <header class="copilot-dock__header">
        <div class="copilot-dock__title">
          <h2>AICrediFlux Copilot</h2>
          <p>AI Gateway 智能运维与使用助手</p>
        </div>
        <button type="button" class="copilot-dock__icon" @click="copilotStore.setOpen(false)">
          <i class="i-ep-close" />
        </button>
      </header>

      <div class="copilot-dock__model-row">
        <el-select
          v-model="model"
          size="small"
          filterable
          allow-create
          default-first-option
          :loading="modelsLoading"
          placeholder="选择模型"
        >
          <el-option
            v-for="item in availableModels"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-button size="small" :loading="loading || modelsLoading" @click="loadAvailableModels">刷新</el-button>
      </div>

      <div v-if="hasRunTrace" class="copilot-dock__trace">
        <section class="copilot-dock__usage-card">
          <div>
            <span>本轮 AI Credit 消耗</span>
            <strong>{{ runQuotaText }}</strong>
          </div>
          <div>
            <span>Tokens</span>
            <strong>{{ runTrace.usage.promptTokens }} / {{ runTrace.usage.completionTokens }}</strong>
          </div>
        </section>
        <section v-if="hasRagTrace" class="copilot-dock__rag">
          <button type="button" class="copilot-dock__rag-toggle" @click="ragExpanded = !ragExpanded">
            <span>{{ ragTraceTitle }}</span>
            <i v-if="ragExpanded" class="i-lucide-chevron-up" />
            <i v-else class="i-lucide-chevron-down" />
          </button>
          <div v-if="ragExpanded" class="copilot-dock__rag-list">
            <p v-if="!runTrace.rag.available" class="copilot-dock__rag-unavailable">
              {{ runTrace.rag.message || "知识库暂不可用" }}
            </p>
            <article
              v-for="citation in runTrace.rag.citations"
              :key="citation.citationId"
              class="copilot-dock__citation"
            >
              <div>
                <strong>{{ citation.title }}</strong>
                <small v-if="citationScore(citation)">{{ citationScore(citation) }}</small>
              </div>
              <em>{{ citation.space }}<template v-if="citation.locator"> / {{ citation.locator }}</template></em>
              <p>{{ citation.snippet }}</p>
            </article>
          </div>
        </section>
        <section v-if="runTrace.tools.length > 0" class="copilot-dock__tools">
          <button type="button" class="copilot-dock__tools-toggle" @click="toolsExpanded = !toolsExpanded">
            <span>{{ toolTraceTitle }}</span>
            <i :class="toolsExpanded ? 'i-lucide-chevron-up' : 'i-lucide-chevron-down'" />
          </button>
          <div v-if="toolsExpanded" class="copilot-dock__tools-list">
            <article
              v-for="tool in runTrace.tools"
              :key="tool.id"
              class="copilot-dock__tool"
              :class="`is-${tool.status.toLowerCase()}`"
            >
              <div class="copilot-dock__tool-head">
                <i :class="toolStatusIcon(tool.status)" />
                <strong>{{ tool.toolName }}</strong>
                <em>{{ toolStatusText(tool.status) }}</em>
                <small v-if="tool.latencyMs !== undefined">{{ tool.latencyMs }}ms</small>
              </div>
              <p v-if="tool.resultSummary">{{ tool.resultSummary }}</p>
              <p v-if="tool.errorMessage">{{ tool.errorMessage }}</p>
            </article>
          </div>
        </section>
      </div>

      <div class="copilot-dock__body">
        <nav class="copilot-dock__sessions">
          <button type="button" class="copilot-dock__new" title="新会话" aria-label="新会话" @click="createSession">
            <i class="i-ep-plus" />
          </button>
          <div
            v-for="session in sessions"
            :key="session.sessionNo"
            class="copilot-dock__session-row"
            :class="{ 'is-active': session.sessionNo === activeSessionNo, 'is-dragging': session.sessionNo === draggedSessionNo }"
            draggable="true"
            @dragstart="handleSessionDragStart(session, $event)"
            @dragover.prevent
            @drop="handleSessionDrop(session)"
            @dragend="handleSessionDragEnd"
          >
            <button type="button" class="copilot-dock__session" @click="loadMessages(session.sessionNo)">
              {{ session.title }}
            </button>
            <el-dropdown trigger="click" @command="handleSessionCommand(session, $event)">
              <button type="button" class="copilot-dock__session-more" aria-label="会话操作" @click.stop>
                <i class="i-lucide-ellipsis" />
              </button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="rename">
                    <i class="i-lucide-pencil" />
                    重命名
                  </el-dropdown-item>
                  <el-dropdown-item command="delete" divided>
                    <i class="i-lucide-trash-2" />
                    删除
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </nav>

        <div class="copilot-dock__messages">
          <div v-if="messages.length === 0" class="copilot-dock__empty">
            询问模型选择、额度消耗、API 报错或渠道状态。
          </div>
          <div
            v-for="message in messages"
            :key="message.messageNo"
            class="copilot-dock__message"
            :class="`is-${message.role}`"
          >
            <span>{{ message.role }}</span>
            <div
              v-if="message.role === 'assistant'"
              class="copilot-dock__markdown markdown-body"
              v-html="renderCopilotMessageHtml(message.role, message.content)"
            />
            <p v-else>{{ message.content }}</p>
          </div>
        </div>
      </div>

      <footer class="copilot-dock__footer">
        <div class="copilot-dock__run">
          <span>Run</span>
          <strong>{{ runStatusLabel }}</strong>
          <button v-if="running" type="button" @click="stop">停止</button>
        </div>
        <el-input
          v-model="input"
          type="textarea"
          :rows="3"
          resize="none"
          placeholder="例如：我这周为什么消耗这么快？"
          @keydown="handleInputKeydown"
        />
        <el-button type="primary" :loading="sending" @click="send">发送</el-button>
      </footer>
    </section>
  </aside>
</template>

<style scoped lang="scss">
.copilot-dock {
  position: relative;
  display: flex;
  flex-shrink: 0;
  height: 100%;
  border-left: 1px solid var(--ys-border-lighter);
  background: color-mix(in srgb, var(--el-bg-color) 92%, var(--el-color-primary) 8%);

  &__resize-handle {
    position: absolute;
    top: 0;
    bottom: 0;
    left: -4px;
    z-index: 4;
    width: 8px;
    cursor: col-resize;
    background: transparent;
    border: 0;

    &::after {
      position: absolute;
      top: 0;
      bottom: 0;
      left: 3px;
      width: 2px;
      content: '';
      background: transparent;
    }

    &:hover::after,
    &:focus-visible::after {
      background: var(--el-color-primary-light-5);
    }
  }

  &--resizing {
    user-select: none;
  }

  &__rail {
    display: grid;
    width: 52px;
    padding: 14px 0;
    color: var(--el-text-color-secondary);
    cursor: pointer;
    background: transparent;
    border: 0;
    border-right: 1px solid var(--ys-border-lighter);
    place-items: center;
    writing-mode: vertical-rl;

    i {
      margin-bottom: 10px;
      font-size: 18px;
      writing-mode: horizontal-tb;
    }

    span {
      font-size: 12px;
      letter-spacing: 0;
    }
  }

  &--open {
    width: var(--copilot-dock-width, 420px);
  }

  &__panel {
    display: flex;
    flex: 1;
    flex-direction: column;
    min-width: 0;
    background: var(--el-bg-color);
  }

  &__header {
    position: relative;
    display: grid;
    place-items: center;
    min-height: 74px;
    padding: 14px 48px;
    text-align: center;
    border-bottom: 1px solid var(--ys-border-lighter);

    h2 {
      margin: 0;
      font-size: 17px;
      font-weight: 700;
    }

    p {
      margin: 4px 0 0;
      color: var(--el-text-color-secondary);
      font-size: 12px;
    }
  }

  &__title {
    min-width: 0;
  }

  &__icon {
    position: absolute;
    right: 16px;
    display: grid;
    width: 30px;
    height: 30px;
    cursor: pointer;
    background: transparent;
    border: 0;
    border-radius: 6px;
    place-items: center;
  }

  &__model-row {
    display: grid;
    grid-template-columns: 1fr auto;
    gap: 8px;
    padding: 12px 16px;
    border-bottom: 1px solid var(--ys-border-lighter);
  }

  &__trace {
    display: grid;
    gap: 10px;
    padding: 12px 16px;
    border-bottom: 1px solid var(--ys-border-lighter);
  }

  &__usage-card {
    display: grid;
    grid-template-columns: 1fr 1fr;
    overflow: hidden;
    background: var(--el-fill-color-lighter);
    border: 1px solid var(--ys-border-lighter);
    border-radius: 8px;

    div {
      min-width: 0;
      padding: 10px 12px;
    }

    div + div {
      border-left: 1px solid var(--ys-border-lighter);
    }

    span {
      display: block;
      color: var(--el-text-color-secondary);
      font-size: 12px;
    }

    strong {
      display: block;
      margin-top: 4px;
      overflow: hidden;
      font-size: 15px;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  &__tools,
  &__rag {
    display: grid;
    gap: 8px;
  }

  &__tools-toggle,
  &__rag-toggle {
    display: flex;
    align-items: center;
    justify-content: space-between;
    width: 100%;
    min-height: 32px;
    padding: 7px 9px;
    color: var(--el-text-color-secondary);
    cursor: pointer;
    background: var(--el-fill-color-lighter);
    border: 1px solid var(--ys-border-lighter);
    border-radius: 8px;

    span {
      min-width: 0;
      overflow: hidden;
      font-size: 12px;
      text-align: left;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    i {
      flex: 0 0 auto;
      font-size: 14px;
    }
  }

  &__tools-list,
  &__rag-list {
    display: grid;
    gap: 8px;
    max-height: 220px;
    overflow-y: auto;
  }

  &__tool {
    padding: 9px 10px;
    background: var(--el-fill-color-lighter);
    border: 1px solid var(--ys-border-lighter);
    border-radius: 8px;

    p {
      display: -webkit-box;
      margin: 6px 0 0;
      overflow: hidden;
      color: var(--el-text-color-secondary);
      font-size: 12px;
      line-height: 1.45;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
    }

    &.is-failed {
      border-color: var(--el-color-danger-light-7);
    }
  }

  &__tool-head {
    display: flex;
    gap: 7px;
    align-items: center;
    min-width: 0;

    i {
      flex: 0 0 auto;
    }

    strong {
      flex: 1;
      min-width: 0;
      overflow: hidden;
      font-size: 13px;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    em,
    small {
      flex: 0 0 auto;
      color: var(--el-text-color-secondary);
      font-size: 12px;
      font-style: normal;
    }
  }

  &__body {
    display: grid;
    grid-template-columns: 118px 1fr;
    flex: 1;
    min-height: 0;
  }

  &__sessions {
    min-width: 0;
    padding: 10px;
    overflow-y: auto;
    border-right: 1px solid var(--ys-border-lighter);
  }

  &__new {
    display: grid;
    width: 32px;
    height: 32px;
    margin: 0 auto 8px;
    color: var(--el-color-primary);
    cursor: pointer;
    background: var(--el-color-primary-light-9);
    border: 0;
    border-radius: 6px;
    place-items: center;

    i {
      font-size: 16px;
    }
  }

  &__session-row {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 28px;
    gap: 2px;
    align-items: center;
    min-height: 34px;
    margin-bottom: 6px;
    cursor: grab;
    border-radius: 6px;

    &:active {
      cursor: grabbing;
    }

    &.is-active {
      background: var(--el-color-primary-light-9);
    }

    &.is-dragging {
      opacity: 0.55;
    }
  }

  &__session {
    min-width: 0;
    min-height: 34px;
    padding: 7px 6px 7px 8px;
    overflow: hidden;
    color: var(--el-text-color-regular);
    text-align: left;
    text-overflow: ellipsis;
    white-space: nowrap;
    cursor: pointer;
    background: transparent;
    border: 0;
    border-radius: 6px;
  }

  &__session-row.is-active &__session {
    color: var(--el-color-primary);
  }

  &__session-more {
    display: grid;
    width: 26px;
    height: 26px;
    color: var(--el-text-color-secondary);
    cursor: pointer;
    background: transparent;
    border: 0;
    border-radius: 6px;
    place-items: center;

    &:hover {
      color: var(--el-color-primary);
      background: var(--el-fill-color-light);
    }
  }

  &__messages {
    display: flex;
    flex-direction: column;
    gap: 10px;
    min-width: 0;
    padding: 14px;
    overflow-y: auto;
  }

  &__empty {
    padding: 14px;
    color: var(--el-text-color-secondary);
    font-size: 13px;
    line-height: 1.6;
    border: 1px dashed var(--ys-border-lighter);
    border-radius: 8px;
  }

  &__message {
    padding: 10px 12px;
    background: var(--el-fill-color-light);
    border-radius: 8px;

    span {
      color: var(--el-text-color-secondary);
      font-size: 12px;
    }

    p {
      margin: 4px 0 0;
      line-height: 1.55;
      white-space: pre-wrap;
    }
  }

  &__markdown {
    margin-top: 4px;
    overflow-wrap: anywhere;
    color: var(--el-text-color-primary);
    font-size: 13px;
    line-height: 1.6;

    :deep(p) {
      margin: 0 0 8px;
    }

    :deep(p:last-child) {
      margin-bottom: 0;
    }

    :deep(ul),
    :deep(ol) {
      margin: 6px 0 8px;
      padding-left: 18px;
    }

    :deep(pre) {
      max-width: 100%;
      margin: 8px 0;
      padding: 10px;
      overflow-x: auto;
      border-radius: 6px;
    }

    :deep(code) {
      font-size: 12px;
    }
  }

  &__footer {
    display: grid;
    gap: 8px;
    padding: 12px 16px 16px;
    border-top: 1px solid var(--ys-border-lighter);
  }

  &__run {
    display: flex;
    gap: 8px;
    align-items: center;
    color: var(--el-text-color-secondary);
    font-size: 12px;

    strong {
      color: var(--el-text-color-primary);
    }

    button {
      margin-left: auto;
      color: var(--el-color-danger);
      cursor: pointer;
      background: transparent;
      border: 0;
    }
  }
}

@media (width <= 900px) {
  .copilot-dock {
    position: fixed;
    right: 0;
    bottom: 0;
    z-index: 60;
    height: calc(100vh - 48px);

    &--open {
      width: min(100vw, 420px);
    }

    &__resize-handle {
      display: none;
    }

    &__body {
      grid-template-columns: 1fr;
    }

    &__sessions {
      display: none;
    }
  }
}
</style>






<style scoped lang="scss">
.copilot-dock {
  &__rag-unavailable,
  &__citation {
    padding: 9px 10px;
    background: var(--el-fill-color-lighter);
    border: 1px solid var(--ys-border-lighter);
    border-radius: 8px;
  }
  &__rag-unavailable {
    margin: 0;
    color: var(--el-text-color-secondary);
    font-size: 12px;
    line-height: 1.5;
  }
  &__citation {
    div { display: flex; gap: 8px; align-items: center; min-width: 0; }
    strong { flex: 1; min-width: 0; overflow: hidden; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
    small { flex: 0 0 auto; color: var(--el-color-primary); font-size: 12px; }
    em { display: block; margin-top: 3px; overflow: hidden; color: var(--el-text-color-secondary); font-size: 12px; font-style: normal; text-overflow: ellipsis; white-space: nowrap; }
    p {
      display: -webkit-box;
      margin: 6px 0 0;
      overflow: hidden;
      line-height: 1.45;
      font-size: 12px;
      color: var(--el-text-color-secondary);
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
    }
  }
}
</style>
