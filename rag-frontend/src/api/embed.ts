import request from '@/utils/request'
import type { ApiResponse, PageResult, EmbedConfig } from '@/types'

export function getEmbedList(params: {
  page: number
  pageSize: number
  keyword?: string
}): Promise<ApiResponse<PageResult<EmbedConfig>>> {
  return request.get('/api/embed/list', { params })
}

export function getEmbedDetail(id: number): Promise<ApiResponse<EmbedConfig>> {
  return request.get(`/api/embed/${id}`)
}

export function getActiveEmbed(): Promise<ApiResponse<EmbedConfig>> {
  return request.get('/api/embed/active')
}

export interface EmbedConfigRequest {
  name: string
  theme?: string
  position?: string
  width?: number
  height?: number
  primaryColor?: string
  title?: string
  welcomeMsg?: string
  knowledgeIds?: string
  status?: number
}

export function createEmbed(data: EmbedConfigRequest): Promise<ApiResponse<null>> {
  return request.post('/api/embed', data)
}

export function updateEmbed(id: number, data: EmbedConfigRequest): Promise<ApiResponse<null>> {
  return request.put(`/api/embed/${id}`, data)
}

export function deleteEmbed(id: number): Promise<ApiResponse<null>> {
  return request.delete(`/api/embed/${id}`)
}

export function toggleEmbedStatus(id: number): Promise<ApiResponse<null>> {
  return request.put(`/api/embed/${id}/status`)
}