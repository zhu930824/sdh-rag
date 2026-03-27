import request from '@/utils/request'
import type { ApiResponse, PageResult } from '@/types'

export interface ChannelConfig {
  id: number
  name: string
  channelType: string
  config?: string
  assistantId?: number
  callbackUrl?: string
  secretKey?: string
  status: number
  createTime?: string
  updateTime?: string
}

export function getChannelList(params: {
  page: number
  pageSize: number
}): Promise<ApiResponse<PageResult<ChannelConfig>>> {
  return request.get('/api/channel/list', { params })
}

export function getChannelDetail(id: number): Promise<ApiResponse<ChannelConfig>> {
  return request.get(`/api/channel/${id}`)
}

export function createChannel(data: Partial<ChannelConfig>): Promise<ApiResponse<null>> {
  return request.post('/api/channel', data)
}

export function updateChannel(id: number, data: Partial<ChannelConfig>): Promise<ApiResponse<null>> {
  return request.put(`/api/channel/${id}`, data)
}

export function deleteChannel(id: number): Promise<ApiResponse<null>> {
  return request.delete(`/api/channel/${id}`)
}

export function toggleChannelStatus(id: number): Promise<ApiResponse<null>> {
  return request.put(`/api/channel/${id}/status`)
}