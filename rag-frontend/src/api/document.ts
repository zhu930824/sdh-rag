import request from '@/utils/request'
import type { ApiResponse } from '@/types'

// 文档分类类型
export interface DocumentCategory {
  id: number
  name: string
  description: string
  parentId: number | null
  sort: number
  createTime: string
  children?: DocumentCategory[]
  docCount?: number
}

// 分类请求类型
export interface CategoryRequest {
  name: string
  description?: string
  parentId?: number | null
  sort?: number
}

// 文档类型
export interface Document {
  id: number
  title: string
  content: string
  fileType: string
  filePath: string
  fileSize: number
  categoryId: number | null
  userId: number
  status: number
  createTime: string
  updateTime: string
}

// 分页结果
export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

// 后端统一响应格式
interface BackendResponse<T> {
  code: number
  message: string
  data: T
}

// ==================== 分类相关 API ====================

// 获取分类列表
export function getCategoryList(): Promise<BackendResponse<DocumentCategory[]>> {
  return request.get('/api/knowledge/category/list')
}

// 创建分类
export function createCategory(data: CategoryRequest): Promise<BackendResponse<void>> {
  return request.post('/api/knowledge/category', data)
}

// 更新分类
export function updateCategory(id: number, data: CategoryRequest): Promise<BackendResponse<void>> {
  return request.put(`/api/knowledge/category/${id}`, data)
}

// 删除分类
export function deleteCategory(id: number): Promise<BackendResponse<void>> {
  return request.delete(`/api/knowledge/category/${id}`)
}

// ==================== 文档相关 API ====================

// 上传文档
export function uploadDocument(formData: FormData): Promise<BackendResponse<Document>> {
  return request.post('/api/knowledge/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

// 获取文档预览URL
export function getDocumentPreviewUrl(id: number): Promise<BackendResponse<string>> {
  return request.get(`/api/knowledge/document/${id}/preview`)
}

// 获取文档列表
export function getDocumentList(params: {
  page: number
  size: number
  categoryId?: number | null
}): Promise<BackendResponse<PageResult<Document>>> {
  return request.get('/api/knowledge/document/list', { params })
}

// 搜索文档
export function searchDocuments(params: {
  keyword: string
  page: number
  size: number
}): Promise<BackendResponse<PageResult<Document>>> {
  return request.get('/api/knowledge/search', { params })
}

// 删除文档
export function deleteDocument(id: number): Promise<BackendResponse<void>> {
  return request.delete(`/api/knowledge/${id}`)
}

// 获取文档详情
export function getDocumentDetail(id: number): Promise<BackendResponse<Document>> {
  return request.get(`/api/knowledge/document/${id}`)
}
