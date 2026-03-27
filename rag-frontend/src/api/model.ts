import request from '@/utils/request'
import type { ApiResponse, PageResult, ModelConfig } from '@/types'

export function getModelList(params: {
  page: number
  pageSize: number
  keyword?: string
}): Promise<ApiResponse<PageResult<ModelConfig>>> {
  return request.get('/api/model/list', { params })
}

export function getModelDetail(id: number): Promise<ApiResponse<ModelConfig>> {
  return request.get(`/api/model/${id}`)
}

export function getModelDefault(): Promise<ApiResponse<ModelConfig>> {
  return request.get('/api/model/default')
}

export interface ModelConfigRequest {
  name: string
  provider: string
  modelType: string
  modelId: string
  apiKey?: string
  baseUrl?: string
  temperature?: number
  maxTokens?: number
  status?: number
  sort?: number
  isLocal?: number
  isBuiltIn?: number
  icon?: string
}

export function createModel(data: ModelConfigRequest): Promise<ApiResponse<null>> {
  return request.post('/api/model', data)
}

export function updateModel(id: number, data: ModelConfigRequest): Promise<ApiResponse<null>> {
  return request.put(`/api/model/${id}`, data)
}

export function deleteModel(id: number): Promise<ApiResponse<null>> {
  return request.delete(`/api/model/${id}`)
}

export function setModelDefault(id: number): Promise<ApiResponse<null>> {
  return request.put(`/api/model/${id}/default`)
}