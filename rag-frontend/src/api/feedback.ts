import request from '@/utils/request'
import type { ApiResponse, PageResult } from '@/types'

export interface QaFeedback {
  id: number
  chatHistoryId: number
  userId: number
  rating: number
  feedbackType?: string
  comment?: string
  correctAnswer?: string
  status: number
  handledBy?: number
  handleTime?: string
  createTime?: string
}

export interface FeedbackStats {
  likes: number
  total: number
}

export function getFeedbackList(params: {
  page: number
  pageSize: number
  rating?: number
  status?: number
}): Promise<ApiResponse<PageResult<QaFeedback>>> {
  return request.get('/api/feedback/list', { params })
}

export function createFeedback(data: Partial<QaFeedback>): Promise<ApiResponse<QaFeedback>> {
  return request.post('/api/feedback', data)
}

export function handleFeedback(id: number, status: number): Promise<ApiResponse<null>> {
  return request.put(`/api/feedback/${id}/handle`, null, { params: { status } })
}

export function getFeedbackStats(chatHistoryId: number): Promise<ApiResponse<FeedbackStats>> {
  return request.get(`/api/feedback/stats/${chatHistoryId}`)
}