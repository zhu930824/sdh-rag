import request from '@/utils/request'
import type { ApiResponse } from '@/types'

export interface EvaluationTask {
  id: number
  knowledgeId: number
  knowledgeName: string | null
  taskName: string
  qaCount: number
  hitRate: number | null
  docHitRate: number | null
  mrr: number | null
  avgHitRank: number | null
  topKHits: string | null
  negativeCount: number | null
  negativeHitRate: number | null
  datasetType: string | null       // generated/imported/builtin/custom
  status: number
  configSnapshot: string | null
  userId: number
  canDelete: boolean
  errorMessage: string | null
  createTime: string
  updateTime: string
}

export interface EvaluationQa {
  id: number
  taskId: number
  question: string
  expectedAnswer: string
  sourceChunkId: string
  sourceDocumentId: number | null
  sourceChunkContent: string
  retrievedChunkIds: string
  hit: boolean
  docHit: boolean
  hitRank: number | null
  docHitRank: number | null
  isNegative: boolean | null
  sourceType: string | null
  externalId: string | null
  issueStatus: 'open' | 'resolved' | 'ignored'
  issueNote: string | null
  createTime: string
}

export interface KnowledgeQualityIssue {
  qaId: number
  taskId: number
  question: string
  expectedAnswer: string | null
  sourceDocumentId: number | null
  reason: string
  severity: 'high' | 'medium'
  status: 'open' | 'resolved' | 'ignored'
  note: string | null
}

export interface KnowledgeQualityReport {
  knowledgeId: number
  completedTasks: number
  totalQuestions: number
  chunkMisses: number
  documentMisses: number
  negativeFalsePositives: number
  openIssues: number
  recommendations: string[]
  issues: KnowledgeQualityIssue[]
}

export function getKnowledgeQuality(knowledgeId: number, issueLimit = 30): Promise<ApiResponse<KnowledgeQualityReport>> {
  return request.get(`/api/evaluation/knowledge/${knowledgeId}/quality`, { params: { issueLimit } })
}

export function updateQualityIssue(qaId: number, status: 'open' | 'resolved' | 'ignored', note?: string): Promise<ApiResponse<void>> {
  return request.post(`/api/evaluation/qa/${qaId}/issue`, { status, note })
}

export interface DatasetInfo {
  name: string
  description: string
  itemCount: number
  negativeCount: number
  fileName: string
}

// 生成测试集并运行评估
export function generateTestset(data: {
  knowledgeId: number
  qaCount?: number
  taskName?: string
}): Promise<ApiResponse<EvaluationTask>> {
  return request.post('/api/evaluation/generate-testset', data)
}

// 导入外部测试集
export function importTestset(formData: FormData): Promise<ApiResponse<EvaluationTask>> {
  return request.post('/api/evaluation/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

// 下载导入模板
export function downloadTemplate() {
  return request.get('/api/evaluation/template', { responseType: 'blob' })
}

// 运行内置数据集评估
export function runBuiltinDataset(data: {
  datasetName: string
  knowledgeId: number
}): Promise<ApiResponse<EvaluationTask>> {
  return request.post('/api/evaluation/builtin', data)
}

// 获取内置数据集列表
export function getBuiltinDatasets(): Promise<ApiResponse<DatasetInfo[]>> {
  return request.get('/api/evaluation/builtin/list')
}

// 获取评估任务详情
export function getEvaluationTask(id: number): Promise<ApiResponse<EvaluationTask>> {
  return request.get(`/api/evaluation/task/${id}`)
}

// 获取任务的QA列表
export function getEvaluationQaList(taskId: number): Promise<ApiResponse<EvaluationQa[]>> {
  return request.get(`/api/evaluation/task/${taskId}/qa-list`)
}

// 获取评估任务列表（分页）
export function getEvaluationList(params: { page: number; pageSize: number; knowledgeId?: number }): Promise<ApiResponse<{ records: EvaluationTask[]; total: number }>> {
  return request.get('/api/evaluation/list', { params })
}

// 删除评估任务
export function deleteEvaluationTask(id: number): Promise<ApiResponse<void>> {
  return request.post(`/api/evaluation/task/${id}/delete`)
}

// 导出评估报告
export function exportEvaluationReport(taskId: number) {
  return request.get(`/api/evaluation/task/${taskId}/export`, { responseType: 'blob' })
}
