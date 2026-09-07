import request from '@/utils/request'
import type { ApiResponse } from '@/types'

export interface AgentTool {
  id: number
  name: string
  code: string
  description?: string
  endpoint: string
  httpMethod: string
  credentialRef?: string
  authType: 'none' | 'bearer' | 'header'
  authHeader?: string
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH'
  approvalMode: 'NEVER' | 'HIGH_RISK' | 'ALWAYS'
  approverId: number
  timeoutSeconds: number
  allowPrivateNetwork: number
  allowInsecureHttp: number
  status: number
  createdBy: number
  createTime: string
  updateTime: string
}

export interface AgentToolForm {
  name: string
  code: string
  description?: string
  endpoint: string
  httpMethod: string
  credentialRef?: string
  authType: string
  authHeader?: string
  riskLevel: string
  approvalMode: string
  approverId?: number
  timeoutSeconds: number
  allowPrivateNetwork: boolean
  allowInsecureHttp: boolean
  status: number
}

export interface AgentAction {
  id: number
  workflowId?: number
  nodeId?: string
  toolId: number
  toolName: string
  requestedBy: number
  approverId: number
  idempotencyKey: string
  requestJson: string
  evidenceJson?: string
  status: string
  resultJson?: string
  responseCode?: number
  errorMessage?: string
  requestedAt: string
  decidedAt?: string
  executedAt?: string
}

export const getAgentTools = (): Promise<ApiResponse<AgentTool[]>> => request.get('/api/agent-tools')
export const createAgentTool = (data: AgentToolForm): Promise<ApiResponse<AgentTool>> => request.post('/api/agent-tools', data)
export const updateAgentTool = (id: number, data: AgentToolForm): Promise<ApiResponse<AgentTool>> => request.post(`/api/agent-tools/${id}`, data)
export const disableAgentTool = (id: number): Promise<ApiResponse<void>> => request.delete(`/api/agent-tools/${id}`)

export const getAgentActions = (status?: string): Promise<ApiResponse<AgentAction[]>> =>
  request.get('/api/agent-actions', { params: status ? { status } : undefined })
export const getPendingAgentActions = (): Promise<ApiResponse<AgentAction[]>> => request.get('/api/agent-actions/pending')
export const approveAgentAction = (id: number): Promise<ApiResponse<AgentAction>> => request.post(`/api/agent-actions/${id}/approve`)
export const rejectAgentAction = (id: number, reason: string): Promise<ApiResponse<AgentAction>> => request.post(`/api/agent-actions/${id}/reject`, { reason })
export const retryAgentAction = (id: number): Promise<ApiResponse<AgentAction>> => request.post(`/api/agent-actions/${id}/retry`)
export const feedbackAgentAction = (id: number, data: { outcome: string; comment?: string; reusableKnowledge: boolean }): Promise<ApiResponse<void>> =>
  request.post(`/api/agent-actions/${id}/feedback`, data)
