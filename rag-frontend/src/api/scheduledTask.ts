import request from '@/utils/request'
import type { ApiResponse } from '@/types'

export interface ScheduledTask {
  id: number
  name: string
  taskType: string
  cronExpression: string
  params?: string
  description?: string
  status: number
  lastExecuteTime?: string
  nextExecuteTime?: string
  lastResult?: string
  successCount: number
  failCount: number
  createTime: string
}

export function getScheduledTasks(page: number = 1, pageSize: number = 10, taskType?: string, status?: number): Promise<ApiResponse<{ records: ScheduledTask[], total: number }>> {
  return request.get('/api/scheduled-task/list', { params: { page, pageSize, taskType, status } })
}

export function getScheduledTask(id: number): Promise<ApiResponse<ScheduledTask>> {
  return request.get(`/api/scheduled-task/${id}`)
}

export function createScheduledTask(data: Partial<ScheduledTask>): Promise<ApiResponse<ScheduledTask>> {
  return request.post('/api/scheduled-task', data)
}

export function updateScheduledTask(id: number, data: Partial<ScheduledTask>): Promise<ApiResponse<null>> {
  return request.post(`/api/scheduled-task/update/${id}`, data)
}

export function toggleTaskStatus(id: number): Promise<ApiResponse<null>> {
  return request.post(`/api/scheduled-task/toggle/${id}`)
}

export function executeTask(id: number): Promise<ApiResponse<null>> {
  return request.post(`/api/scheduled-task/${id}/execute`)
}

export function deleteScheduledTask(id: number): Promise<ApiResponse<null>> {
  return request.post(`/api/scheduled-task/delete/${id}`)
}
