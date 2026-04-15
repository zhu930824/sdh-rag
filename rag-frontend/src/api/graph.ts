import request from '@/utils/request'
import type { ApiResponse } from '@/types'

export interface NodeData {
  id: number
  label: string
  type: string
  entityType?: string
  description?: string
  documentId?: number
  sourceDocumentId?: number
  weight?: number
  frequency?: number
  properties?: Record<string, any>
}

export interface EdgeData {
  id: string
  source: number
  target: number
  relationType: string
  weight?: number
}

export interface GraphData {
  nodes: NodeData[]
  edges: EdgeData[]
}

export interface GraphStats {
  totalNodes: number
  totalRelationships: number
  nodesByType: Array<{ type: string; count: number }>
  relationshipsByType: Array<{ type: string; count: number }>
}

export interface GraphPath {
  nodes: Array<{
    id: number
    labels: string[]
    name: string
    entityType?: string
  }>
  relationships: Array<{
    type: string
    source: number
    target: number
  }>
  length: number
}

export interface GraphBuildResult {
  success: boolean
  message: string
  documentId: number
  entityCount: number
  relationCount: number
  conceptCount: number
  keywordCount: number
}

// 图谱构建任务状态
export interface GraphBuildTask {
  taskId: string
  knowledgeId?: number
  documentId?: number
  taskType: 'KNOWLEDGE_BASE' | 'DOCUMENT'
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'
  progress: number
  currentStage?: string
  processedDocuments: number
  totalDocuments: number
  entityCount: number
  relationCount: number
  conceptCount: number
  keywordCount: number
  errorMessage?: string
  createTime?: string
  startTime?: string
  finishTime?: string
}

// 获取图谱数据
export function getGraphData(centerNodeId?: number, depth: number = 2, knowledgeBaseId?: number): Promise<ApiResponse<GraphData>> {
  return request.get('/api/graph/data', { params: { centerNodeId, depth, knowledgeBaseId } })
}

// 搜索节点
export function searchNodes(keyword: string, knowledgeBaseId?: number): Promise<ApiResponse<NodeData[]>> {
  return request.get('/api/graph/nodes', { params: { keyword, knowledgeBaseId } })
}

// 获取节点详情
export function getNode(id: number): Promise<ApiResponse<NodeData>> {
  return request.get(`/api/graph/nodes/${id}`)
}

// 获取节点邻居
export function getNodeNeighbors(nodeId: number): Promise<ApiResponse<GraphData>> {
  return request.get(`/api/graph/nodes/${nodeId}/neighbors`)
}

// 获取最短路径
export function getShortestPath(startId: number, endId: number): Promise<ApiResponse<GraphPath>> {
  return request.get('/api/graph/path', { params: { startId, endId } })
}

// 获取统计信息
export function getGraphStats(knowledgeBaseId?: number): Promise<ApiResponse<GraphStats>> {
  return request.get('/api/graph/stats', { params: { knowledgeBaseId } })
}

// 按类型获取节点
export function getNodesByType(nodeType: string, limit: number = 50): Promise<ApiResponse<NodeData[]>> {
  return request.get(`/api/graph/nodes/type/${nodeType}`, { params: { limit } })
}

// 从文档构建图谱
export function buildGraphFromDocument(documentId: number): Promise<ApiResponse<GraphBuildResult>> {
  return request.post(`/api/graph/build/${documentId}`)
}

// 重建文档图谱
export function rebuildGraphFromDocument(documentId: number): Promise<ApiResponse<GraphBuildResult>> {
  return request.post(`/api/graph/rebuild/${documentId}`)
}

// 批量构建图谱
export function batchBuildGraph(documentIds: number[]): Promise<ApiResponse<void>> {
  return request.post('/api/graph/build/batch', documentIds)
}

// 知识库图谱状态
export interface KnowledgeGraphStatus {
  knowledgeId: number
  graphBuilt: boolean
  nodeCount: number
  relationshipCount: number
  lastBuildTime: string
  builtDocumentCount: number
  totalDocumentCount: number
}

// 从知识库构建图谱（异步）
export function buildGraphFromKnowledgeBase(knowledgeId: number): Promise<ApiResponse<GraphBuildTask>> {
  return request.post(`/api/graph/build/knowledge/${knowledgeId}`)
}

// 重建知识库图谱（异步）
export function rebuildGraphFromKnowledgeBase(knowledgeId: number): Promise<ApiResponse<GraphBuildTask>> {
  return request.post(`/api/graph/rebuild/knowledge/${knowledgeId}`)
}

// 删除知识库图谱
export function deleteGraphByKnowledgeBase(knowledgeId: number): Promise<ApiResponse<void>> {
  return request.delete(`/api/graph/knowledge/${knowledgeId}`)
}

// 获取知识库图谱构建状态
export function getKnowledgeGraphStatus(knowledgeId: number): Promise<ApiResponse<KnowledgeGraphStatus>> {
  return request.get(`/api/graph/status/knowledge/${knowledgeId}`)
}

// 获取构建任务状态
export function getBuildTask(taskId: string): Promise<ApiResponse<GraphBuildTask>> {
  return request.get(`/api/graph/task/${taskId}`)
}
