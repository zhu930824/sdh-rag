import request from '@/utils/request'
import type { ApiResponse } from '@/types'

export interface ApiQuota {
  id: number
  userId: number
  quotaType: string
  dailyLimit: number
  monthlyLimit: number
  dailyUsed: number
  monthlyUsed: number
  resetDate: string
  createTime: string
}

export interface QuotaCheck {
  hasQuota: boolean
  remainingDaily: number
  remainingMonthly: number
  dailyLimit: number
  monthlyLimit: number
  dailyUsed: number
  monthlyUsed: number
}

export function getQuotas(): Promise<ApiResponse<ApiQuota[]>> {
  return request.get('/api/quota/list')
}

export function getQuota(quotaType: string): Promise<ApiResponse<ApiQuota>> {
  return request.get(`/api/quota/${quotaType}`)
}

export function checkQuota(quotaType: string): Promise<ApiResponse<QuotaCheck>> {
  return request.get(`/api/quota/check/${quotaType}`)
}

export function setQuota(quotaType: string, dailyLimit?: number, monthlyLimit?: number): Promise<ApiResponse<null>> {
  return request.post('/api/quota/set', { quotaType, dailyLimit, monthlyLimit })
}

export function adminSetQuota(userId: number, quotaType: string, dailyLimit?: number, monthlyLimit?: number): Promise<ApiResponse<null>> {
  return request.post('/api/quota/admin/set', { userId, quotaType, dailyLimit, monthlyLimit })
}
