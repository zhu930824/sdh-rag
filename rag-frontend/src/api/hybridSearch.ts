import request from '@/utils/request'
import type { ApiResponse } from '@/types'

export interface HybridSearchResult {
  chunk: {
    id: number
    documentId: number
    content: string
    keywords?: string
    chunkIndex: number
  }
  keywordScore: number
  semanticScore: number
  finalScore: number
}

export interface SearchRequest {
  knowledgeId?: number
  query: string
  topK?: number
  keywordWeight?: number
  semanticWeight?: number
}

export function hybridSearch(params: SearchRequest): Promise<ApiResponse<HybridSearchResult[]>> {
  return request.post('/api/hybrid-search/search', params)
}

export function keywordSearch(knowledgeId: number, query: string, topK: number = 10): Promise<ApiResponse<{ chunk: { id: number, documentId: number, content: string }, score: number }[]>> {
  return request.get('/api/hybrid-search/keyword/' + knowledgeId, { params: { query, topK } })
}

export function semanticSearch(knowledgeId: number, query: string, topK: number = 10): Promise<ApiResponse<{ chunk: { id: number, documentId: number, content: string }, score: number }[]>> {
  return request.get('/api/hybrid-search/semantic/' + knowledgeId, { params: { query, topK } })
}

export function filteredSearch(params: {
  knowledgeId: number
  query: string
  filters?: Record<string, unknown>
  page?: number
  pageSize?: number
}): Promise<ApiResponse<{ records: HybridSearchResult[], total: number }>> {
  return request.post('/api/hybrid-search/filtered-search', params)
}

export function buildKeywordIndex(knowledgeId: number): Promise<ApiResponse<null>> {
  return request.post(`/api/hybrid-search/build-index/${knowledgeId}`)
}

export function updateKeywordIndex(documentId: number): Promise<ApiResponse<null>> {
  return request.post(`/api/hybrid-search/update-index/${documentId}`)
}
