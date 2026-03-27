import request from '@/utils/request'
import type { ApiResponse, PageResult, Workflow } from '@/types'

export function getWorkflowList(params: {
  page: number
  pageSize: number
  keyword?: string
}): Promise<ApiResponse<PageResult<Workflow>>> {
  return request.get('/api/workflow/list', { params })
}

export function getWorkflowDetail(id: number): Promise<ApiResponse<Workflow>> {
  return request.get(`/api/workflow/${id}`)
}

export interface WorkflowRequest {
  name: string
  description?: string
  flowData?: string
  status?: number
}

export function createWorkflow(data: WorkflowRequest): Promise<ApiResponse<null>> {
  return request.post('/api/workflow', data)
}

export function updateWorkflow(id: number, data: WorkflowRequest): Promise<ApiResponse<null>> {
  return request.put(`/api/workflow/${id}`, data)
}

export function deleteWorkflow(id: number): Promise<ApiResponse<null>> {
  return request.delete(`/api/workflow/${id}`)
}

export function toggleWorkflowStatus(id: number): Promise<ApiResponse<null>> {
  return request.put(`/api/workflow/${id}/status`)
}