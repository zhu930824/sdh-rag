import request from '@/utils/request'
import type { ApiResponse } from '@/types'

export interface DocumentChunk {
  id: number
  documentId: number
  knowledgeId?: number
  chunkIndex: number
  content: string
  chunkSize?: number
  embedding?: string
  keywords?: string
  createTime: string
}

export function getHealthHistory(knowledgeId: number): Promise<ApiResponse<{ records: DocumentChunk[], total: number }>> {
  return request.get(`/api/knowledge-health/history/${knowledgeId}`)
}

export function runHealthCheck(knowledgeId: number): Promise<ApiResponse<Record<string, unknown>>> {
  return request.post(`/api/knowledge-health/check/${knowledgeId}`)
}

export function checkDocumentCount(knowledgeId: number): Promise<ApiResponse<{ id: number, knowledgeId: number, checkType: string, score: number, details: string, suggestions: string, checkTime: string }>> {
  return request.get(`/api/knowledge-health/check/${knowledgeId}/document-count`)
}

export function checkEmbeddingQuality(knowledgeId: number): Promise<ApiResponse<{ id: number, knowledgeId: number, checkType: string, score: number, details: string, suggestions: string, checkTime: string }>> {
  return request.get(`/api/knowledge-health/check/${knowledgeId}/embedding-quality`)
}

export function checkIndexStatus(knowledgeId: number): Promise<ApiResponse<{ id: number, knowledgeId: number, checkType: string, score: number, details: string, suggestions: string, checkTime: string }>> {
  return request.get(`/api/knowledge-health/check/${knowledgeId}/index-status`)
}

export function getHealthSummary(): Promise<ApiResponse<Record<string, unknown>[]>> {
  return request.get('/api/knowledge-health/summary')
}

export function getHealthScore(knowledgeId: number): Promise<ApiResponse<{ knowledgeId: number, overallScore: number, healthLevel: string }>> {
  return request.get(`/api/knowledge-health/score/${knowledgeId}`)
}
