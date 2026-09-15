<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteAdminRagDocument,
  getAdminRagDocuments,
  indexProjectDocs,
  reindexAdminRagDocument,
  saveAdminRagDocument
} from '@/api/rag'
import type { RagDocument, RagSpace, SaveRagDocumentPayload } from '@/api/rag/types'

const spaces: Array<{ label: string; value: RagSpace; desc: string }> = [
  { label: '平台文档', value: 'platform_docs', desc: 'API 使用、计费、渠道配置和项目学习笔记' },
  { label: '模型文档', value: 'model_docs', desc: '模型能力、上下文长度、适用场景和价格说明' },
  { label: '故障排查', value: 'troubleshooting', desc: '错误码、常见异常、运维处理路径' }
]

const loading = ref(false)
const saving = ref(false)
const indexing = ref(false)
const documents = ref<RagDocument[]>([])
const fileInput = ref<HTMLInputElement | null>(null)
const form = reactive<SaveRagDocumentPayload>({
  space: 'platform_docs',
  title: '',
  sourceName: '',
  content: ''
})

const readyCount = computed(() => documents.value.filter((item) => item.status === 'READY').length)
const failedCount = computed(() => documents.value.filter((item) => item.status === 'FAILED').length)

onMounted(() => {
  refresh().catch(() => undefined)
})

async function refresh(): Promise<void> {
  loading.value = true
  try {
    documents.value = await getAdminRagDocuments()
  } finally {
    loading.value = false
  }
}

async function submit(): Promise<void> {
  if (!form.title.trim()) {
    ElMessage.warning('请填写文档标题')
    return
  }
  if (!form.content.trim()) {
    ElMessage.warning('请粘贴或选择 Markdown/Text 内容')
    return
  }
  saving.value = true
  try {
    const document = await saveAdminRagDocument({
      space: form.space,
      title: form.title.trim(),
      sourceName: form.sourceName?.trim(),
      content: form.content
    })
    showIndexResult(document, '文档已提交索引')
    form.title = ''
    form.sourceName = ''
    form.content = ''
    if (fileInput.value) fileInput.value.value = ''
    await refresh()
  } finally {
    saving.value = false
  }
}

async function readFile(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  const name = file.name || ''
  if (!/\.(md|txt)$/i.test(name)) {
    ElMessage.warning('第一版仅支持 .md / .txt')
    input.value = ''
    return
  }
  form.sourceName = name
  if (!form.title) form.title = name.replace(/\.(md|txt)$/i, '')
  form.content = await file.text()
}

async function indexLocalDocs(): Promise<void> {
  indexing.value = true
  try {
    await indexProjectDocs()
    ElMessage.success('项目 docs 已提交索引')
    await refresh()
  } finally {
    indexing.value = false
  }
}

async function reindex(row: unknown): Promise<void> {
  const document = row as RagDocument
  const result = await reindexAdminRagDocument(document.docNo)
  showIndexResult(result, '已重新提交索引')
  await refresh()
}

async function remove(row: unknown): Promise<void> {
  const document = row as RagDocument
  await ElMessageBox.confirm(`确认删除知识库文档「${document.title}」？`, '删除文档', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  await deleteAdminRagDocument(document.docNo)
  ElMessage.success('文档已删除')
  await refresh()
}

function showIndexResult(document: RagDocument, successMessage: string): void {
  if (document.status === 'FAILED') {
    ElMessage.warning(document.errorMessage ? `文档已保存，但索引失败：${document.errorMessage}` : '文档已保存，但索引失败')
    return
  }
  ElMessage.success(successMessage)
}

function statusType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'READY') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'INDEXING') return 'warning'
  return 'info'
}
</script>

<template>
  <main class="rag-admin-page">
    <header class="rag-admin-page__header">
      <div>
        <h1>知识库</h1>
        <p>维护 AICrediFlux Copilot 的平台公共 Markdown/Text 知识来源。</p>
      </div>
      <div class="rag-admin-page__actions">
        <el-button :loading="loading" @click="refresh">刷新</el-button>
        <el-button type="primary" :loading="indexing" @click="indexLocalDocs">索引项目 docs</el-button>
      </div>
    </header>

    <section class="rag-admin-page__stats" aria-label="知识库状态">
      <div><span>文档数</span><strong>{{ documents.length }}</strong></div>
      <div><span>可检索</span><strong>{{ readyCount }}</strong></div>
      <div><span>失败</span><strong>{{ failedCount }}</strong></div>
    </section>

    <section class="rag-admin-page__editor">
      <div class="rag-admin-page__form-head">
        <h2>新增文档</h2>
        <input ref="fileInput" type="file" accept=".md,.txt" @change="readFile" />
      </div>
      <el-form label-position="top">
        <el-form-item label="知识空间">
          <el-select v-model="form.space">
            <el-option v-for="space in spaces" :key="space.value" :label="space.label" :value="space.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="form.title" placeholder="例如：SiliconFlow 接入说明" />
        </el-form-item>
        <el-form-item label="来源名">
          <el-input v-model="form.sourceName" placeholder="例如：docs/siliconflow.md" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="form.content" type="textarea" :rows="10" resize="vertical" placeholder="粘贴 Markdown 或 Text 内容" />
        </el-form-item>
        <el-button type="primary" :loading="saving" @click="submit">保存并索引</el-button>
      </el-form>
    </section>

    <section class="rag-admin-page__table">
      <el-table v-loading="loading" :data="documents" row-key="docNo">
        <el-table-column label="标题" min-width="220">
          <template #default="{ row }">
            <strong>{{ row.title }}</strong>
            <p>{{ row.sourceName || row.docNo }}</p>
          </template>
        </el-table-column>
        <el-table-column prop="space" label="空间" width="150" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="90" />
        <el-table-column prop="chunkCount" label="分块" width="90" />
        <el-table-column prop="indexedAt" label="索引时间" min-width="170" />
        <el-table-column label="错误" min-width="220">
          <template #default="{ row }">
            <span class="rag-admin-page__error">{{ row.errorMessage || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="reindex(row)">重建</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </main>
</template>

<style scoped lang="scss">
.rag-admin-page {
  display: grid;
  gap: 18px;
  padding: 24px;

  &__header,
  &__actions,
  &__form-head {
    display: flex;
    gap: 12px;
    align-items: center;
    justify-content: space-between;
  }

  h1,
  h2 {
    margin: 0;
  }

  &__header p,
  &__table p,
  &__error {
    color: var(--el-text-color-secondary);
  }

  &__stats {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    overflow: hidden;
    background: var(--el-bg-color);
    border: 1px solid var(--ys-border-lighter);
    border-radius: 8px;
  }

  &__stats div,
  &__editor,
  &__table {
    padding: 16px;
  }

  &__stats div + div {
    border-left: 1px solid var(--ys-border-lighter);
  }

  &__stats span {
    display: block;
    color: var(--el-text-color-secondary);
    font-size: 13px;
  }

  &__stats strong {
    display: block;
    margin-top: 4px;
    font-size: 22px;
  }

  &__editor,
  &__table {
    background: var(--el-bg-color);
    border: 1px solid var(--ys-border-lighter);
    border-radius: 8px;
  }

  &__table p {
    margin: 4px 0 0;
    font-size: 12px;
  }
}
</style>
