import request from '@/utils/request'
import type { ApiResponse } from '@/types'

export interface HotwordStats {
  totalQueries: number
  uniqueWords: number
  avgQueries: number
}

export interface WordRankItem {
  word: string
  count: number
  percent: number
  trend?: number
  isNew?: boolean
}

export interface TrendItem {
  date: string
  count: number
}

export function getHotwordStats(startDate: string, endDate: string): Promise<ApiResponse<HotwordStats>> {
  return request.get('/api/hotword/stats', { params: { startDate, endDate } })
}

export function getHotwordRanking(limit: number, startDate: string, endDate: string): Promise<ApiResponse<WordRankItem[]>> {
  return request.get('/api/hotword/ranking', { params: { limit, startDate, endDate } })
}

export function getHotwordTrend(word: string, startDate: string, endDate: string): Promise<ApiResponse<TrendItem[]>> {
  return request.get('/api/hotword/trend', { params: { word, startDate, endDate } })
}

export function getHotwordList(params: {
  page: number
  pageSize: number
  keyword?: string
  startDate?: string
  endDate?: string
}): Promise<ApiResponse<WordRankItem[]>> {
  return request.get('/api/hotword/list', { params })
}