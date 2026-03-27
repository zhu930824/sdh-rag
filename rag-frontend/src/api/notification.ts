import request from '@/utils/request'
import type { ApiResponse, PageResult } from '@/types'

export interface Notification {
  id: number
  userId: number
  title: string
  content?: string
  type: string
  relatedType?: string
  relatedId?: number
  isRead: number
  readTime?: string
  createTime?: string
}

export function getNotificationList(params: {
  page: number
  pageSize: number
  type?: string
  isRead?: number
}): Promise<ApiResponse<PageResult<Notification>>> {
  return request.get('/api/notification/list', { params })
}

export function getUnreadCount(): Promise<ApiResponse<{ count: number }>> {
  return request.get('/api/notification/unread-count')
}

export function markNotificationRead(id: number): Promise<ApiResponse<null>> {
  return request.put(`/api/notification/${id}/read`)
}

export function markAllNotificationRead(): Promise<ApiResponse<null>> {
  return request.put('/api/notification/read-all')
}

export function deleteNotification(id: number): Promise<ApiResponse<null>> {
  return request.delete(`/api/notification/${id}`)
}