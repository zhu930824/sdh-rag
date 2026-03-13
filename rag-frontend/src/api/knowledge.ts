import request from '@/utils/request'
import type { ApiResponse, PageResult } from '@/types'

// 知识库文档类型
export interface KnowledgeDocument {
  id: number
  title: string
  content: string
  fileType: string
  filePath: string
  fileSize: number
  categoryId: number
  userId: number
  status: number // 0-处理中, 1-成功, 2-失败
  createTime: string
  updateTime: string
}

// 文档分类类型
export interface DocumentCategory {
  id: number
  name: string
  description: string
  parentId: number
  sort: number
  createTime: string
  children?: DocumentCategory[]
}

// 上传文档
export function uploadDocument(formData: FormData): Promise<ApiResponse<KnowledgeDocument>> {
  return request.post('/api/knowledge/document/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

// 获取文档列表
export function getDocumentList(params: {
  page: number
  size: number
  categoryId?: number
}): Promise<ApiResponse<PageResult<KnowledgeDocument>>> {
  return request.get('/api/knowledge/document/list', { params })
}

// 搜索文档
export function searchDocuments(params: {
  keyword: string
  page: number
  size: number
}): Promise<ApiResponse<PageResult<KnowledgeDocument>>> {
  return request.get('/api/knowledge/document/search', { params })
}

// 删除文档
export function deleteDocument(id: number): Promise<ApiResponse<null>> {
  return request.delete(`/api/knowledge/document/${id}`)
}

// 获取文档详情
export function getDocumentDetail(id: number): Promise<ApiResponse<KnowledgeDocument>> {
  return request.get(`/api/knowledge/document/${id}`)
}

// 获取分类列表
export function getCategories(): Promise<ApiResponse<DocumentCategory[]>> {
  return request.get('/api/knowledge/category/list')
}

// 创建分类
export function createCategory(data: {
  name: string
  description?: string
  parentId?: number
}): Promise<ApiResponse<DocumentCategory>> {
  return request.post('/api/knowledge/category', data)
}

// 更新分类
export function updateCategory(
  id: number,
  data: { name?: string; description?: string; sort?: number }
): Promise<ApiResponse<DocumentCategory>> {
  return request.put(`/api/knowledge/category/${id}`, data)
}

// 删除分类
export function deleteCategory(id: number): Promise<ApiResponse<null>> {
  return request.delete(`/api/knowledge/category/${id}`)
}

// 获取相关文档推荐
export function getRelatedDocuments(id: number): Promise<ApiResponse<KnowledgeDocument[]>> {
  return request.get(`/api/knowledge/document/${id}/related`)
}
