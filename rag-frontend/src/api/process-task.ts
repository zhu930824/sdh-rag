import request from '@/utils/request'
import type { ApiResponse, PageResult } from '@/types'

export interface DocumentProcessTask {
  id: number
  documentId: number
  taskType: string
  status: number
  progress: number
  result?: string
  errorMsg?: string
  processor?: string
  startTime?: string
  endTime?: string
  createTime?: string
}

export function getProcessTaskList(params: {
  page: number
  pageSize: number
  documentId?: number
  taskType?: string
  status?: number
}): Promise<ApiResponse<PageResult<DocumentProcessTask>>> {
  return request.get('/api/process-task/list', { params })
}

export function getProcessTaskDetail(id: number): Promise<ApiResponse<DocumentProcessTask>> {
  return request.get(`/api/process-task/${id}`)
}

export function createProcessTask(data: Partial<DocumentProcessTask>): Promise<ApiResponse<null>> {
  return request.post('/api/process-task', data)
}

export function retryProcessTask(id: number): Promise<ApiResponse<null>> {
  return request.post(`/api/process-task/${id}/retry`)
}