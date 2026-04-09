import request from '@/utils/request'
import type { ApiResponse } from '@/types'

export interface DashboardStats {
  knowledgeCount: number
  documentCount: number
  chatCount: number
  userCount: number
  todayChatCount: number
  // 同比昨日趋势百分比
  documentTrend: number
  userTrend: number
  chatTrend: number
  avgResponseTime: number
  accuracyRate: number
  hourlyStats: HourlyStats[]
}

export interface HourlyStats {
  hour: string
  count: number
}

export interface ActivityRecord {
  id: number
  title: string
  time: string
  type: string
  status: string
  statusText: string
}

export function getDashboardStats(): Promise<ApiResponse<DashboardStats>> {
  return request.get('/api/dashboard/stats')
}

export function getRecentActivities(): Promise<ApiResponse<ActivityRecord[]>> {
  return request.get('/api/dashboard/recent-activities')
}
