export type RagSpace = 'platform_docs' | 'model_docs' | 'troubleshooting'

export type RagDocumentStatus = 'PENDING' | 'INDEXING' | 'READY' | 'FAILED' | 'DELETED'

export interface RagDocument {
  id?: number | string | bigint
  docNo: string
  space: RagSpace | string
  title: string
  sourceType: string
  sourceName?: string
  contentHash: string
  version: number
  status: RagDocumentStatus | string
  chunkCount: number
  createdBy?: number | string | bigint
  indexedAt?: string
  errorMessage?: string
  createTime?: string
  updateTime?: string
}

export interface SaveRagDocumentPayload {
  space: RagSpace
  title: string
  sourceName?: string
  content: string
}

export interface RagCitation {
  citationId: string
  title: string
  space: string
  docNo?: string
  locator?: string
  snippet: string
  score?: number
}

export interface RagRetrievalResult {
  skip: boolean
  available: boolean
  message: string
  citations: RagCitation[]
}
