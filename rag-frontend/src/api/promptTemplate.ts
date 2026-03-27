import request from '@/utils/request'
import type { ApiResponse } from '@/types'

export interface PromptTemplate {
  id: number
  name: string
  code: string
  category: string
  content: string
  variables?: string
  description?: string
  isSystem: number
  status: number
  useCount: number
  userId?: number
  createTime: string
  updateTime?: string
}

export function getPromptTemplates(page: number = 1, pageSize: number = 10, category?: string, keyword?: string): Promise<ApiResponse<{ records: PromptTemplate[], total: number }>> {
  return request.get('/api/prompt-template/list', { params: { page, pageSize, category, keyword } })
}

export function getActivePromptTemplates(): Promise<ApiResponse<PromptTemplate[]>> {
  return request.get('/api/prompt-template/active')
}

export function getPromptTemplatesByCategory(category: string): Promise<ApiResponse<PromptTemplate[]>> {
  return request.get(`/api/prompt-template/category/${category}`)
}

export function getPromptCategories(): Promise<ApiResponse<string[]>> {
  return request.get('/api/prompt-template/categories')
}

export function getPromptTemplate(id: number): Promise<ApiResponse<PromptTemplate>> {
  return request.get(`/api/prompt-template/${id}`)
}

export function getPromptTemplateByCode(code: string): Promise<ApiResponse<PromptTemplate>> {
  return request.get(`/api/prompt-template/code/${code}`)
}

export function createPromptTemplate(data: Partial<PromptTemplate>): Promise<ApiResponse<PromptTemplate>> {
  return request.post('/api/prompt-template', data)
}

export function updatePromptTemplate(id: number, data: Partial<PromptTemplate>): Promise<ApiResponse<null>> {
  return request.put(`/api/prompt-template/${id}`, data)
}

export function deletePromptTemplate(id: number): Promise<ApiResponse<null>> {
  return request.delete(`/api/prompt-template/${id}`)
}

export function renderPromptTemplate(code: string, variables: Record<string, string>): Promise<ApiResponse<{ content: string }>> {
  return request.post('/api/prompt-template/render', { code, variables })
}

export function validatePromptTemplate(content: string): Promise<ApiResponse<null>> {
  return request.post('/api/prompt-template/validate', { content })
}
