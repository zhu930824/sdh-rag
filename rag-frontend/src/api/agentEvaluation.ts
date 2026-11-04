import request from '@/utils/request'
import type { ApiResponse } from '@/types'

export interface AgentScenario {
  id: string
  task: string
  initialContext?: string | null
  allowedTools: string[]
  forbiddenTools: string[]
  expectedOutcome: string
  criticalSteps: string[]
  risk: 'LOW' | 'MEDIUM' | 'HIGH'
}
export interface AgentDatasetSummary {
  id: string
  name: string
  version: number
  parentId: string | null
  sha256: string
  scenarioCount: number
  createdAt: string
}
export interface AgentDataset extends Omit<AgentDatasetSummary, 'scenarioCount'> { scenarios: AgentScenario[] }
export interface AgentEvaluationSummary {
  id: string
  datasetId: string
  scenarioId: string
  runKey: string
  status: string
  createdAt: string
}
export interface AgentEvaluationRecord extends AgentEvaluationSummary {
  datasetSha256: string
  snapshotSha256: string
  scenarioJson: string
  runSnapshotJson: string
}
const base = '/api/agent/evaluation'
export const listAgentDatasets = (): Promise<ApiResponse<AgentDatasetSummary[]>> => request.get(`${base}/datasets`)
export const getAgentDataset = (id: string): Promise<ApiResponse<AgentDataset>> => request.get(`${base}/datasets/${encodeURIComponent(id)}`)
export const importAgentDataset = (body: { name: string; parentId?: string; scenarios: AgentScenario[] }): Promise<ApiResponse<AgentDataset>> => request.post(`${base}/datasets`, body)
export const listAgentEvaluations = (datasetId: string): Promise<ApiResponse<AgentEvaluationSummary[]>> => request.get(`${base}/records`, { params: { datasetId } })
export const recordAgentEvaluation = (body: { datasetId: string; scenarioId: string; runKey: string }): Promise<ApiResponse<AgentEvaluationRecord>> => request.post(`${base}/records`, body)
export const getAgentEvaluation = (id: string): Promise<ApiResponse<AgentEvaluationRecord>> => request.get(`${base}/records/${encodeURIComponent(id)}`)
