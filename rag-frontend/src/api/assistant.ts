import request from '@/utils/request'
import type { ApiResponse, PageResult } from '@/types'

export interface AiAssistant {
  id: number
  name: string
  description?: string
  category?: string
  icon?: string
  systemPrompt?: string
  welcomeMessage?: string
  suggestedQuestions?: string
  knowledgeIds?: string
  modelConfigId?: number
  temperature?: number
  maxTokens?: number
  isPublic: number
  creatorId?: number
  useCount: number
  ratingAvg: number
  ratingCount: number
  status: number
  sort: number
  createTime?: string
  updateTime?: string
}

export function getAssistantList(params: {
  page: number
  pageSize: number
  keyword?: string
  category?: string
}): Promise<ApiResponse<PageResult<AiAssistant>>> {
  return request.get('/api/assistant/list', { params })
}

export function getPublicAssistants(params: {
  page: number
  pageSize: number
  category?: string
}): Promise<ApiResponse<PageResult<AiAssistant>>> {
  return request.get('/api/assistant/public', { params })
}

export function getHotAssistants(limit: number = 10): Promise<ApiResponse<AiAssistant[]>> {
  return request.get('/api/assistant/hot', { params: { limit } })
}

export function getCategories(): Promise<ApiResponse<string[]>> {
  return request.get('/api/assistant/categories')
}

export function getAssistantDetail(id: number): Promise<ApiResponse<AiAssistant>> {
  return request.get(`/api/assistant/${id}`)
}

export function createAssistant(data: Partial<AiAssistant>): Promise<ApiResponse<AiAssistant>> {
  return request.post('/api/assistant', data)
}

export function updateAssistant(id: number, data: Partial<AiAssistant>): Promise<ApiResponse<AiAssistant>> {
  return request.put(`/api/assistant/${id}`, data)
}

export function deleteAssistant(id: number): Promise<ApiResponse<null>> {
  return request.delete(`/api/assistant/${id}`)
}

export function incrementAssistantUse(id: number): Promise<ApiResponse<null>> {
  return request.post(`/api/assistant/${id}/use`)
}