import request from '@/utils/request'
import type { ApiResponse } from '@/types'

export type KnowledgeDraftStatus = 'DRAFT' | 'IN_REVIEW' | 'APPROVED' | 'REJECTED' | 'PUBLISHING' | 'PUBLISHED' | 'PUBLISH_FAILED' | 'GATE_FAILED' | 'ROLLED_BACK'

export interface KnowledgeDraft {
  id: number
  improvementId?: number
  knowledgeId: number
  title: string
  content: string
  status: KnowledgeDraftStatus
  regressionQueries?: string
  publishedDocumentId?: number
  validationStatus: 'NOT_RUN' | 'PASSED' | 'FAILED'
  validationResult?: string
  reviewNote?: string
  errorMessage?: string
  createdAt: string
  updatedAt: string
  publishedAt?: string
  rolledBackAt?: string
}

export interface KnowledgeDraftForm {
  improvementId?: number
  knowledgeId: number
  title: string
  content: string
  regressionQueries: string[]
}

export interface KnowledgePublishRecord {
  id: number
  action: 'PUBLISH' | 'VALIDATE' | 'ROLLBACK'
  status: string
  detail?: string
  operatorId: number
  createdAt: string
}

export interface KnowledgeValidationResult {
  passed: boolean
  queries: Array<{ query: string; passed: boolean; rank?: number }>
}

export const getKnowledgeDrafts = (status?: string): Promise<ApiResponse<KnowledgeDraft[]>> =>
  request.get('/api/knowledge-publishing/drafts', { params: status ? { status } : undefined })
export const getKnowledgePublishRecords = (id: number): Promise<ApiResponse<KnowledgePublishRecord[]>> =>
  request.get(`/api/knowledge-publishing/drafts/${id}/records`)
export const createKnowledgeDraft = (data: KnowledgeDraftForm): Promise<ApiResponse<KnowledgeDraft>> =>
  request.post('/api/knowledge-publishing/drafts', data)
export const updateKnowledgeDraft = (id: number, data: KnowledgeDraftForm): Promise<ApiResponse<KnowledgeDraft>> =>
  request.post(`/api/knowledge-publishing/drafts/${id}`, data)
export const submitKnowledgeDraft = (id: number): Promise<ApiResponse<KnowledgeDraft>> =>
  request.post(`/api/knowledge-publishing/drafts/${id}/submit`)
export const reviewKnowledgeDraft = (id: number, approved: boolean, note?: string): Promise<ApiResponse<KnowledgeDraft>> =>
  request.post(`/api/knowledge-publishing/drafts/${id}/review`, { approved, note })
export const publishKnowledgeDraft = (id: number): Promise<ApiResponse<KnowledgeDraft>> =>
  request.post(`/api/knowledge-publishing/drafts/${id}/publish`)
export const validateKnowledgeDraft = (id: number): Promise<ApiResponse<KnowledgeValidationResult>> =>
  request.post(`/api/knowledge-publishing/drafts/${id}/validate`)
export const rollbackKnowledgeDraft = (id: number): Promise<ApiResponse<KnowledgeDraft>> =>
  request.post(`/api/knowledge-publishing/drafts/${id}/rollback`)
