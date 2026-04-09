import request from '@/utils/request'
import type { ApiResponse, PageResult } from '@/types'

export interface OperationLog {
  id: number
  userId: number
  username: string
  type: string
  content: string
  ip: string
  status: number
  browser: string
  os: string
  requestUrl: string
  requestMethod: string
  requestParams: string
  responseData: string
  errorMsg: string
  duration: number
  createTime: string
}

export interface LogQuery {
  page: number
  pageSize: number
  type?: string
  username?: string
  status?: number
  startTime?: string
  endTime?: string
}

export function getLogList(params: LogQuery): Promise<ApiResponse<PageResult<OperationLog>>> {
  return request.get('/api/log/list', { params })
}

export function getLogDetail(id: number): Promise<ApiResponse<OperationLog>> {
  return request.get(`/api/log/${id}`)
}

export function deleteLog(id: number): Promise<ApiResponse<null>> {
  return request.post(`/api/log/delete/${id}`)
}

export function batchDeleteLogs(ids: number[]): Promise<ApiResponse<{ success: number; fail: number }>> {
  return request.post('/api/log/batch-delete', { ids })
}

export function clearLogs(): Promise<ApiResponse<null>> {
  return request.post('/api/log/clear')
}