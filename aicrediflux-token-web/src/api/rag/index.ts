import { request } from '@/utils/request'
import type { RagDocument, RagRetrievalResult, SaveRagDocumentPayload } from './types'

export const getAdminRagDocuments = () => request.get<RagDocument[]>('/api/admin/rag/documents')

export const saveAdminRagDocument = (payload: SaveRagDocumentPayload) =>
  request.post<RagDocument>('/api/admin/rag/documents', payload)

export const indexProjectDocs = () =>
  request.post<RagDocument[]>('/api/admin/rag/documents/index-project-docs')

export const reindexAdminRagDocument = (docNo: string, content?: string) =>
  request.post<RagDocument>(`/api/admin/rag/documents/${encodeURIComponent(docNo)}/index`, { content })

export const deleteAdminRagDocument = (docNo: string) =>
  request.delete(`/api/admin/rag/documents/${encodeURIComponent(docNo)}`)

export const searchAgentRag = (q: string, space?: string) =>
  request.get<RagRetrievalResult>('/api/agent/rag/search', {
    params: { q, space },
    _silent: true
  })
