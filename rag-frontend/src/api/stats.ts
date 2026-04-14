import request from '@/utils/request'
import type { ApiResponse } from '@/types'

export interface StatsOverview {
  totalDocuments: number
  totalUsers: number
  totalChats: number
  todayChats: number
  // 趋势百分比
  documentTrend: number
  userTrend: number
  chatTrend: number
  todayChatsTrend: number
}

export interface ChatTrendItem {
  date: string
  count: number
}

export interface ModelStats {
  model: string
  tokens: number
  cost: number
}

export interface ApiCostStats {
  // 历史累计
  totalTokens: number
  totalCost: number
  // 时间范围内
  rangeTokens: number
  rangeCost: number
  // 今日
  todayTokens: number
  todayCost: number
  modelStats: ModelStats[]
}

export function getStatsOverview(): Promise<ApiResponse<StatsOverview>> {
  return request.get('/api/stats/overview')
}

export function getChatTrend(startDate: string, endDate: string): Promise<ApiResponse<ChatTrendItem[]>> {
  return request.get('/api/stats/chat-trend', { params: { startDate, endDate } })
}

export function getApiCostStats(startDate: string, endDate: string): Promise<ApiResponse<ApiCostStats>> {
  return request.get('/api/stats/api-cost', { params: { startDate, endDate } })
}
