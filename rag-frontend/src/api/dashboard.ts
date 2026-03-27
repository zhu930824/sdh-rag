import request from '@/utils/request'
import type { ApiResponse } from '@/types'

export interface DashboardStats {
  knowledgeCount: number
  documentCount: number
  chatCount: number
  userCount: number
}

export function getDashboardStats(): Promise<ApiResponse<DashboardStats>> {
  return request.get('/api/dashboard/stats')
}