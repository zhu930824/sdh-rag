import request from '@/utils/request'

export interface KnowledgeBase {
  id: number
  name: string
  description: string
  userId: number
  chunkSize: number
  chunkOverlap: number
  embeddingModel: string
  icon: string
  color: string
  // 检索配置
  rankModel: string
  enableRewrite: boolean
  similarityThreshold: number
  keywordTopK: number
  vectorTopK: number
  keywordWeight: number
  vectorWeight: number
  documentCount: number
  chunkCount: number
  status: number
  isPublic: boolean
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
  chunkSize?: number
  chunkOverlap?: number
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

export interface KnowledgeBaseDetail {
  knowledgeBase: KnowledgeBase
  tags: Tag[]
  documentCount: number
  chunkCount: number
  processingCount: number
  successCount: number
  failedCount: number
}

export interface Tag {
  id: number
  name: string
  color: string
  category: string
}

export interface KnowledgeChunk {
  id: number
  documentId: number
  documentTitle: string
  chunkIndex: number
  content: string
  chunkSize: number
  vectorId: string
  createTime: string
  contentPreview: string
}

export interface DocumentLinkConfig {
  documentId: number
  chunkSize?: number
  chunkOverlap?: number
  embeddingModel?: string
}

export interface KnowledgeBaseConfigRequest {
  chunkSize?: number
  chunkOverlap?: number
  embeddingModel?: string
  tagIds?: number[]
  // 检索配置
  rankModel?: string
  enableRewrite?: boolean
  similarityThreshold?: number
  keywordTopK?: number
  vectorTopK?: number
  keywordWeight?: number
  vectorWeight?: number
}

export interface LinkDocumentsRequest {
  documentIds?: number[]
  configs?: DocumentLinkConfig[]
}

// 获取知识库列表
export function getKnowledgeBaseList(params: KnowledgeBaseQuery) {
  return request.get<KnowledgeBaseListResult>('/api/knowledge-base/list', { params })
}

// 获取知识库详情
export function getKnowledgeBaseDetail(id: number) {
  return request.get<KnowledgeBase>(`/api/knowledge-base/${id}`)
}

// 获取知识库完整详情（包含统计和标签）
export function getKnowledgeBaseFullDetail(id: number) {
  return request.get<KnowledgeBaseDetail>(`/api/knowledge-base/${id}/detail`)
}

// 创建知识库
export function createKnowledgeBase(data: KnowledgeBaseFormData) {
  return request.post<KnowledgeBase>('/api/knowledge-base', data)
}

// 更新知识库
export function updateKnowledgeBase(id: number, data: KnowledgeBaseFormData) {
  return request.put<KnowledgeBase>(`/api/knowledge-base/${id}`, data)
}

// 更新知识库配置
export function updateKnowledgeBaseConfig(id: number, data: KnowledgeBaseConfigRequest) {
  return request.put(`/api/knowledge-base/${id}/config`, data)
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

// 获取知识库文档列表
export function getKnowledgeBaseDocuments(id: number, params: { page: number; pageSize: number }) {
  return request.get<{ records: any[]; total: number }>(`/api/knowledge-base/${id}/documents`, { params })
}

// 关联文档到知识库
export function linkDocumentsToKnowledgeBase(knowledgeBaseId: number, request: LinkDocumentsRequest) {
  return request.post(`/api/knowledge-base/${knowledgeBaseId}/documents/link`, request)
}

// 更新文档关联配置
export function updateDocumentLinkConfig(knowledgeBaseId: number, documentId: number, config: DocumentLinkConfig) {
  return request.put(`/api/knowledge-base/${knowledgeBaseId}/documents/${documentId}/config`, config)
}

// 获取文档关联详情
export function getDocumentRelation(knowledgeBaseId: number, documentId: number) {
  return request.get<KnowledgeDocumentRelation>(`/api/knowledge-base/${knowledgeBaseId}/documents/${documentId}/relation`)
}

export interface KnowledgeDocumentRelation {
  id: number
  knowledgeId: number
  documentId: number
  processStatus: number
  chunkCount: number
  processTime: string
  chunkSize: number
  chunkOverlap: number
  embeddingModel: string
  createTime: string
}

// 移除文档关联
export function unlinkDocumentFromKnowledgeBase(knowledgeBaseId: number, documentId: number) {
  return request.delete(`/api/knowledge-base/${knowledgeBaseId}/documents/${documentId}`)
}

// 获取可关联的文档列表
export function getAvailableDocuments(params: {
  page: number
  pageSize: number
  excludeKnowledgeId?: number
}) {
  return request.get<{ records: any[]; total: number }>('/api/knowledge-base/documents/available', { params })
}

// 获取知识库分块列表
export function getKnowledgeBaseChunks(id: number, params: { page: number; pageSize: number }) {
  return request.get<{ records: KnowledgeChunk[]; total: number }>(`/api/knowledge-base/${id}/chunks`, { params })
}

// 添加知识库标签
export function addKnowledgeBaseTag(knowledgeBaseId: number, tagId: number) {
  return request.post(`/api/knowledge-base/${knowledgeBaseId}/tags`, null, { params: { tagId } })
}

// 移除知识库标签
export function removeKnowledgeBaseTag(knowledgeBaseId: number, tagId: number) {
  return request.delete(`/api/knowledge-base/${knowledgeBaseId}/tags/${tagId}`)
}

// 获取知识库标签列表
export function getKnowledgeBaseTags(knowledgeBaseId: number) {
  return request.get<Tag[]>(`/api/knowledge-base/${knowledgeBaseId}/tags`)
}
