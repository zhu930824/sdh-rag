import request from '@/utils/request'
import type { ApiResponse, PageResult } from '@/types'

export interface Tag {
  id: number
  name: string
  color?: string
  category?: string
  sort: number
  status: number
  createTime?: string
}

export interface DocumentTag {
  id: number
  documentId: number
  tagId: number
  source: string
  confidence?: number
  userId?: number
  createTime?: string
}

export function getTagList(params: {
  page: number
  pageSize: number
  keyword?: string
  category?: string
}): Promise<ApiResponse<PageResult<Tag>>> {
  return request.get('/api/tag/list', { params })
}

export function getAllTags(): Promise<ApiResponse<Tag[]>> {
  return request.get('/api/tag/all')
}

export function createTag(data: Partial<Tag>): Promise<ApiResponse<null>> {
  return request.post('/api/tag', data)
}

export function updateTag(id: number, data: Partial<Tag>): Promise<ApiResponse<null>> {
  return request.put(`/api/tag/${id}`, data)
}

export function deleteTag(id: number): Promise<ApiResponse<null>> {
  return request.delete(`/api/tag/${id}`)
}

export function addDocumentTag(documentId: number, tagId: number, source: string = 'manual'): Promise<ApiResponse<null>> {
  return request.post(`/api/tag/document/${documentId}`, null, { params: { tagId, source } })
}

export function removeDocumentTag(documentId: number, tagId: number): Promise<ApiResponse<null>> {
  return request.delete(`/api/tag/document/${documentId}/${tagId}`)
}

export function getDocumentTags(documentId: number): Promise<ApiResponse<Tag[]>> {
  return request.get(`/api/tag/document/${documentId}`)
}