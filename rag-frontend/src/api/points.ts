import request from '@/utils/request'
import type { ApiResponse, PageResult } from '@/types'

export interface UserProfile {
  id: number
  userId: number
  pointsBalance: number
  totalPoints: number
  level: number
  experience: number
  badgeIds?: string
  createTime?: string
  updateTime?: string
}

export interface PointsRecord {
  id: number
  userId: number
  points: number
  balance: number
  type: string
  description?: string
  relatedType?: string
  relatedId?: number
  createTime?: string
}

export interface PointsGoods {
  id: number
  name: string
  description?: string
  image?: string
  points: number
  stock: number
  type: string
  status: number
  sort: number
  createTime?: string
}

export interface PointsExchange {
  id: number
  userId: number
  goodsId: number
  points: number
  status: number
  address?: string
  remark?: string
  createTime?: string
}

export function getPointsProfile(): Promise<ApiResponse<UserProfile>> {
  return request.get('/api/points/profile')
}

export function getPointsRecords(params: {
  page: number
  pageSize: number
}): Promise<ApiResponse<PageResult<PointsRecord>>> {
  return request.get('/api/points/records', { params })
}

export function getPointsGoods(params: {
  page: number
  pageSize: number
}): Promise<ApiResponse<PageResult<PointsGoods>>> {
  return request.get('/api/points/goods', { params })
}

export function exchangeGoods(goodsId: number): Promise<ApiResponse<null>> {
  return request.post(`/api/points/exchange/${goodsId}`)
}

export function getPointsExchanges(params: {
  page: number
  pageSize: number
}): Promise<ApiResponse<PageResult<PointsExchange>>> {
  return request.get('/api/points/exchanges', { params })
}