import request from '@/utils/request'
import type { ApiResponse, PageResult } from '@/types'

// 工作流节点类型
export type NodeType = 'start' | 'end' | 'input' | 'output' | 'llm' | 'retrieval' | 'tool' | 'condition' | 'code' | 'http' | 'delay'

// 节点位置
export interface NodePosition {
  x: number
  y: number
}

// 工作流节点
export interface WorkflowNode {
  id: string
  type: NodeType
  name: string
  position: NodePosition
  config: Record<string, any>
  inputs?: string[]
  outputs?: string[]
}

// 节点连接
export interface WorkflowEdge {
  id: string
  source: string
  target: string
  sourceHandle?: string
  targetHandle?: string
  label?: string
  condition?: Record<string, any>
}

// 工作流定义
export interface WorkflowDefinition {
  nodes: WorkflowNode[]
  edges: WorkflowEdge[]
  variables?: Record<string, any>
}

// 工作流
export interface Workflow {
  id: number
  name: string
  description: string
  icon: string
  color: string
  definition: WorkflowDefinition
  status: number
  version: number
  createTime: string
  updateTime: string
}

// 工作流表单
export interface WorkflowFormData {
  id?: number
  name: string
  description: string
  icon: string
  color: string
  definition: WorkflowDefinition
}

// 工作流执行结果
export interface WorkflowExecution {
  id: string
  workflowId: number
  status: 'running' | 'success' | 'failed'
  startTime: string
  endTime?: string
  inputs: Record<string, any>
  outputs?: Record<string, any>
  nodeExecutions: NodeExecution[]
  error?: string
}

// 节点执行记录
export interface NodeExecution {
  nodeId: string
  nodeName: string
  status: 'pending' | 'running' | 'success' | 'failed' | 'skipped'
  startTime: string
  endTime?: string
  inputs: Record<string, any>
  outputs?: Record<string, any>
  error?: string
  duration?: number
}

// 节点类型定义
export interface NodeTypeDefinition {
  type: NodeType
  name: string
  icon: string
  category: string
  description: string
  defaultConfig: Record<string, any>
  inputs: { name: string; type: string; required: boolean }[]
  outputs: { name: string; type: string }[]
}

// 获取工作流列表
export function getWorkflowList(params: {
  page: number
  pageSize: number
  keyword?: string
  status?: number
}): Promise<ApiResponse<PageResult<Workflow>>> {
  return request.get('/api/workflow/list', { params })
}

// 获取工作流详情
export function getWorkflowDetail(id: number): Promise<ApiResponse<Workflow>> {
  return request.get(`/api/workflow/${id}`)
}

// 创建工作流
export function createWorkflow(data: WorkflowFormData): Promise<ApiResponse<Workflow>> {
  return request.post('/api/workflow', data)
}

// 更新工作流
export function updateWorkflow(id: number, data: WorkflowFormData): Promise<ApiResponse<Workflow>> {
  return request.post(`/api/workflow/update/${id}`, data)
}

// 删除工作流
export function deleteWorkflow(id: number): Promise<ApiResponse<null>> {
  return request.post(`/api/workflow/delete/${id}`)
}

// 切换工作流状态
export function toggleWorkflowStatus(id: number): Promise<ApiResponse<null>> {
  return request.post(`/api/workflow/status/${id}`)
}

// 执行工作流
export function executeWorkflow(id: number, inputs: Record<string, any>): Promise<ApiResponse<WorkflowExecution>> {
  return request.post(`/api/workflow/${id}/execute`, { inputs })
}

// 获取执行记录
export function getExecutionList(workflowId: number, params: {
  page: number
  pageSize: number
}): Promise<ApiResponse<PageResult<WorkflowExecution>>> {
  return request.get(`/api/workflow/${workflowId}/executions`, { params })
}

// 获取节点类型定义
export function getNodeTypeDefinitions(): Promise<ApiResponse<NodeTypeDefinition[]>> {
  return request.get('/api/workflow/node-types')
}

// 调试节点
export function debugNode(workflowId: number, nodeId: string, inputs: Record<string, any>): Promise<ApiResponse<any>> {
  return request.post(`/api/workflow/${workflowId}/debug-node`, { nodeId, inputs })
}
