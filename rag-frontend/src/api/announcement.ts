import request from '@/utils/request'
import type { ApiResponse, PageResult } from '@/types'

export interface Announcement {
  id: number
  title: string
  content: string
  type: string
  priority: number
  targetType: string
  targetIds?: string
  publishTime?: string
  expireTime?: string
  isTop: number
  publisherId?: number
  status: number
  createTime?: string
  updateTime?: string
}

export interface AnnouncementDetail {
  announcement: Announcement
  readCount: number
  isRead?: boolean
}

export function getAnnouncementList(params: {
  page: number
  pageSize: number
  type?: string
  status?: number
}): Promise<ApiResponse<PageResult<Announcement>>> {
  return request.get('/api/announcement/list', { params })
}

export function getActiveAnnouncements(): Promise<ApiResponse<Announcement[]>> {
  return request.get('/api/announcement/active')
}

export function getAnnouncementDetail(id: number): Promise<ApiResponse<AnnouncementDetail>> {
  return request.get(`/api/announcement/${id}`)
}

export function createAnnouncement(data: Partial<Announcement>): Promise<ApiResponse<null>> {
  return request.post('/api/announcement', data)
}

export function updateAnnouncement(id: number, data: Partial<Announcement>): Promise<ApiResponse<null>> {
  return request.put(`/api/announcement/${id}`, data)
}

export function publishAnnouncement(id: number): Promise<ApiResponse<null>> {
  return request.put(`/api/announcement/${id}/publish`)
}

export function deleteAnnouncement(id: number): Promise<ApiResponse<null>> {
  return request.delete(`/api/announcement/${id}`)
}

export function markAnnouncementRead(id: number): Promise<ApiResponse<null>> {
  return request.post(`/api/announcement/${id}/read`)
}