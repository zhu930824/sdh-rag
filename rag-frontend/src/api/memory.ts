import request from '@/utils/request'
import type { ApiResponse } from '@/types'

export interface MemoryItem {
  id: string
  type: string
  content: string
  importance: number
  accessCount: number
  createdAt: string
}

export interface MemoryOverview {
  preferences: MemoryItem[]
  facts: MemoryItem[]
  patterns: MemoryItem[]
  recentSummaries: MemoryItem[]
}

export function getMemoryOverview(): Promise<ApiResponse<MemoryOverview>> {
  return request.get('/api/memory/overview')
}

export function addPreference(content: string, importance: number = 7): Promise<ApiResponse<null>> {
  return request.post('/api/memory/preference', { content, importance })
}

export function deleteMemory(memoryId: string, layer: string): Promise<ApiResponse<null>> {
  return request.delete(`/api/memory/${memoryId}`, { params: { layer } })
}

export function searchMemory(query: string, type?: string, limit: number = 10): Promise<ApiResponse<MemoryItem[]>> {
  return request.get('/api/memory/search', { params: { query, type, limit } })
}

export function clearAllMemory(): Promise<ApiResponse<null>> {
  return request.delete('/api/memory/all')
}
