import request from '@/utils/request'
import type { ApiResponse } from '@/types'

export interface WebhookConfig {
  id: number
  name: string
  url: string
  secret?: string
  events: string
  headers?: string
  status: number
  lastTriggerTime?: string
  failCount: number
  createTime: string
}

export function getWebhooks(page: number = 1, pageSize: number = 10, status?: number): Promise<ApiResponse<{ records: WebhookConfig[], total: number }>> {
  return request.get('/api/webhook/list', { params: { page, pageSize, status } })
}

export function getActiveWebhooks(): Promise<ApiResponse<WebhookConfig[]>> {
  return request.get('/api/webhook/active')
}

export function getWebhook(id: number): Promise<ApiResponse<WebhookConfig>> {
  return request.get(`/api/webhook/${id}`)
}

export function createWebhook(data: Partial<WebhookConfig>): Promise<ApiResponse<WebhookConfig>> {
  return request.post('/api/webhook', data)
}

export function updateWebhook(id: number, data: Partial<WebhookConfig>): Promise<ApiResponse<null>> {
  return request.put(`/api/webhook/${id}`, data)
}

export function toggleWebhookStatus(id: number): Promise<ApiResponse<null>> {
  return request.put(`/api/webhook/${id}/toggle`)
}

export function deleteWebhook(id: number): Promise<ApiResponse<null>> {
  return request.delete(`/api/webhook/${id}`)
}

export function testWebhook(id: number): Promise<ApiResponse<null>> {
  return request.post(`/api/webhook/${id}/test`)
}

export function triggerWebhooks(event: string, payload: Record<string, unknown>): Promise<ApiResponse<null>> {
  return request.post('/api/webhook/trigger', { event, payload })
}
