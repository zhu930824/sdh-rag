import request from '@/utils/request'
import type { ApiResponse } from '@/types'

export interface GraphNode {
  id: number
  nodeType: string
  name: string
  description?: string
  documentId?: number
  properties?: string
  weight: number
  status: number
  createTime?: string
  updateTime?: string
}

export interface GraphEdge {
  id: number
  sourceId: number
  targetId: number
  relationType: string
  weight: number
  properties?: string
  createTime?: string
}

export interface GraphData {
  nodes: GraphNode[]
  edges: GraphEdge[]
}

export function getGraphData(centerNodeId?: number, depth: number = 2): Promise<ApiResponse<GraphData>> {
  return request.get('/api/graph/data', { params: { centerNodeId, depth } })
}

export function searchNodes(keyword: string): Promise<ApiResponse<GraphNode[]>> {
  return request.get('/api/graph/nodes', { params: { keyword } })
}

export function getNode(id: number): Promise<ApiResponse<GraphNode>> {
  return request.get(`/api/graph/nodes/${id}`)
}

export function createNode(data: Partial<GraphNode>): Promise<ApiResponse<GraphNode>> {
  return request.post('/api/graph/nodes', data)
}

export function deleteNode(id: number): Promise<ApiResponse<null>> {
  return request.delete(`/api/graph/nodes/${id}`)
}

export function createEdge(data: Partial<GraphEdge>): Promise<ApiResponse<GraphEdge>> {
  return request.post('/api/graph/edges', data)
}

export function deleteEdge(id: number): Promise<ApiResponse<null>> {
  return request.delete(`/api/graph/edges/${id}`)
}

export function getNodeEdges(nodeId: number): Promise<ApiResponse<GraphEdge[]>> {
  return request.get(`/api/graph/nodes/${nodeId}/edges`)
}

export function buildGraphFromDocument(documentId: number): Promise<ApiResponse<null>> {
  return request.post(`/api/graph/build/${documentId}`)
}