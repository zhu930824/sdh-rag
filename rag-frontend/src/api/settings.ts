import request from '@/utils/request'
import type { ApiResponse } from '@/types'

export interface BasicSettings {
  systemName: string
  defaultTheme: string
}

export interface ModelSettings {
  defaultModel: string
  temperature: number
  maxTokens: number
}

export interface NotificationSettings {
  enableNotification: boolean
  enableEmail: boolean
  notificationEmail: string
}

export interface SystemSettings {
  basic: BasicSettings
  model: ModelSettings
  notification: NotificationSettings
}

export function getAllSettings(): Promise<ApiResponse<SystemSettings>> {
  return request.get('/api/settings')
}

export function getSettingByKey(key: string): Promise<ApiResponse<Record<string, unknown>>> {
  return request.get(`/api/settings/${key}`)
}

export function updateAllSettings(settings: Record<string, Record<string, unknown>>): Promise<ApiResponse<null>> {
  return request.post('/api/settings/update', settings)
}

export function updateSettingByKey(key: string, value: Record<string, unknown>): Promise<ApiResponse<null>> {
  return request.post(`/api/settings/update/${key}`, value)
}