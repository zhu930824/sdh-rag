import request from '@/utils/request'

export interface KnowledgeBase {
  id: number
  name: string
  description: string
  icon: string
  color: string
  documentCount: number
  chunkCount: number
  status: number
  isPublic: boolean
  embeddingModel: string
  createTime: string
  updateTime: string
}

export interface KnowledgeBaseFormData {
  id?: number
  name: string
  description: string
  icon: string
  color: string
  isPublic: boolean
  embeddingModel: string
}

export interface KnowledgeBaseQuery {
  page: number
  pageSize: number
  keyword?: string
  status?: number
}

export interface KnowledgeBaseListResult {
  records: KnowledgeBase[]
  total: number
}

// 获取知识库列表
export function getKnowledgeBaseList(params: KnowledgeBaseQuery) {
  return request.get<KnowledgeBaseListResult>('/api/knowledge-base/list', { params })
}

// 获取知识库详情
export function getKnowledgeBaseDetail(id: number) {
  return request.get<KnowledgeBase>(`/api/knowledge-base/${id}`)
}

// 创建知识库
export function createKnowledgeBase(data: KnowledgeBaseFormData) {
  return request.post<KnowledgeBase>('/api/knowledge-base', data)
}

// 更新知识库
export function updateKnowledgeBase(id: number, data: KnowledgeBaseFormData) {
  return request.put<KnowledgeBase>(`/api/knowledge-base/${id}`, data)
}

// 删除知识库
export function deleteKnowledgeBase(id: number) {
  return request.delete(`/api/knowledge-base/${id}`)
}

// 获取知识库统计
export function getKnowledgeBaseStats() {
  return request.get<{
    totalBases: number
    totalDocuments: number
    totalChunks: number
    activeBases: number
  }>('/api/knowledge-base/stats')
}
