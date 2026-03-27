import request from '@/utils/request'
import type { ApiResponse } from '@/types'

export interface DocumentComparison {
  id: number
  documentId1: number
  documentId2: number
  userId: number
  comparisonType: string
  similarityScore: number
  diffResult?: string
  summary?: string
  createTime: string
}

export function compareDocuments(documentId1: number, documentId2: number, comparisonType: string = 'text'): Promise<ApiResponse<DocumentComparison>> {
  return request.post('/api/document-comparison/compare', { documentId1, documentId2, comparisonType })
}

export function getComparison(id: number): Promise<ApiResponse<DocumentComparison>> {
  return request.get(`/api/document-comparison/${id}`)
}

export function getComparisonsByDocument(documentId: number): Promise<ApiResponse<DocumentComparison[]>> {
  return request.get(`/api/document-comparison/document/${documentId}`)
}

export function getSimilarity(docId1: number, docId2: number): Promise<ApiResponse<{ similarityScore: number, summary: string }>> {
  return request.get('/api/document-comparison/similarity', { params: { docId1, docId2 } })
}

export function getTextDiff(text1: string, text2: string): Promise<ApiResponse<Record<string, unknown>>> {
  return request.post('/api/document-comparison/text-diff', { text1, text2 })
}
