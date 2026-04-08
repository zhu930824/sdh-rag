import request from '@/utils/request'
import type { ApiResponse, PageResult } from '@/types'

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
  createTime: string
  updateTime: string
  tags?: DocumentTag[]
}

// 文档标签类型
export interface DocumentTag {
  id: number
  name: string
  color?: string
}

// 分类请求类型
export interface CategoryRequest {
  name: string
  description?: string
  parentId?: number | null
  sort?: number
}

// ==================== 分类相关 API ====================

// 获取分类列表
export function getCategoryList(): Promise<ApiResponse<DocumentCategory[]>> {
  return request.get('/api/document/category/list')
}

// 创建分类
export function createCategory(data: CategoryRequest): Promise<ApiResponse<void>> {
  return request.post('/api/document/category', data)
}

// 更新分类
export function updateCategory(id: number, data: CategoryRequest): Promise<ApiResponse<void>> {
  return request.put(`/api/document/category/${id}`, data)
}

// 删除分类
export function deleteCategory(id: number): Promise<ApiResponse<void>> {
  return request.delete(`/api/document/category/${id}`)
}

// ==================== 文档相关 API ====================

// 上传文档
export function uploadDocument(formData: FormData): Promise<ApiResponse<Document>> {
  return request.post('/api/document/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

// 获取文档预览URL
export function getDocumentPreviewUrl(id: number): Promise<ApiResponse<string>> {
  return request.get(`/api/document/${id}/preview`)
}

// 获取文档列表
export function getDocumentList(params: {
  page: number
  size: number
  categoryId?: number | null
}): Promise<ApiResponse<PageResult<Document>>> {
  return request.get('/api/document/list', { params })
}

// 搜索文档
export function searchDocuments(params: {
  keyword: string
  page: number
  size: number
}): Promise<ApiResponse<PageResult<Document>>> {
  return request.get('/api/document/search', { params })
}

// 删除文档
export function deleteDocument(id: number): Promise<ApiResponse<void>> {
  return request.delete(`/api/document/${id}`)
}

// 获取文档详情
export function getDocumentDetail(id: number): Promise<ApiResponse<Document>> {
  return request.get(`/api/document/${id}`)
}
