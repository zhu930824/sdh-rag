import request from '@/utils/request'
import type { ApiResponse } from '@/types'

export interface StatsOverview {
  totalDocuments: number
  totalUsers: number
  totalChats: number
  totalQuestions: number
  todayChats: number
  todayQuestions: number
}

export interface ChartData {
  dates: string[]
  values: number[]
}

export interface ApiCostStats {
  totalTokens: number
  totalCost: number
  todayTokens: number
  todayCost: number
  modelStats: { model: string; tokens: number; cost: number }[]
}

export function getStatsOverview(): Promise<ApiResponse<StatsOverview>> {
  return request.get('/api/stats/overview')
}

export function getChatTrend(startDate: string, endDate: string): Promise<ApiResponse<ChartData>> {
  return request.get('/api/stats/chat-trend', { params: { startDate, endDate } })
}

export function getApiCostStats(startDate: string, endDate: string): Promise<ApiResponse<ApiCostStats>> {
  return request.get('/api/stats/api-cost', { params: { startDate, endDate } })
}

