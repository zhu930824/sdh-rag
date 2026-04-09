import request from '@/utils/request'
import type { ApiResponse, PageResult } from '@/types'

export interface SensitiveWord {
  id: number
  word: string
  category: string
  status: number
  createTime: string
}

export interface SensitiveWordQuery {
  page: number
  pageSize: number
  keyword?: string
  category?: string
}

export interface SensitiveWordFormData {
  id?: number
  word: string
  category: string
  status: number
}

export function getSensitiveList(params: SensitiveWordQuery): Promise<ApiResponse<PageResult<SensitiveWord>>> {
  return request.get('/api/sensitive/list', { params })
}

export function getSensitiveDetail(id: number): Promise<ApiResponse<SensitiveWord>> {
  return request.get(`/api/sensitive/${id}`)
}

export function createSensitive(data: SensitiveWordFormData): Promise<ApiResponse<null>> {
  return request.post('/api/sensitive', data)
}

export function updateSensitive(id: number, data: SensitiveWordFormData): Promise<ApiResponse<null>> {
  return request.post(`/api/sensitive/update/${id}`, data)
}

export function deleteSensitive(id: number): Promise<ApiResponse<null>> {
  return request.post(`/api/sensitive/delete/${id}`)
}

export function batchDeleteSensitive(ids: number[]): Promise<ApiResponse<null>> {
  return request.post('/api/sensitive/batch-delete', ids)
}

export function toggleSensitiveStatus(id: number): Promise<ApiResponse<null>> {
  return request.post(`/api/sensitive/status/${id}`)
}

export function checkSensitiveWord(text: string): Promise<ApiResponse<string[]>> {
  return request.post('/api/sensitive/check', text)
}