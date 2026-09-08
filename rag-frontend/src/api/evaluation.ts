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
  evaluationMode: 'RETRIEVAL' | 'GENERATION' | 'FULL'
  topK: number
  labeledCount: number
  judgedCount: number
  recallAtK: number | null
  precisionAtK: number | null
  ndcgAtK: number | null
  mapScore: number | null
  contextPrecision: number | null
  contextRecall: number | null
  faithfulness: number | null
  answerRelevancy: number | null
  completeness: number | null
  fluency: number | null
  overallScore: number | null
  judgeModel: string | null
  baselineTaskId: number | null
  regressionStatus: 'PASSED' | 'FAILED' | 'NOT_COMPARED' | null
  avgHitRank: number | null
  topKHits: string | null
  negativeCount: number | null
  negativeHitRate: number | null
  datasetType: string | null       // generated/imported/builtin/custom
  datasetId: number | null
  datasetVersion: number | null
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
  expectedDocumentIds: string | null
  relevanceJudgments: string | null
  retrievedDocumentIds: string | null
  retrievedContext: string | null
  actualAnswer: string | null
  hit: boolean
  docHit: boolean
  hitRank: number | null
  docHitRank: number | null
  recallAtK: number | null
  precisionAtK: number | null
  reciprocalRank: number | null
  ndcgAtK: number | null
  averagePrecision: number | null
  contextPrecision: number | null
  contextRecall: number | null
  faithfulness: number | null
  answerRelevancy: number | null
  completeness: number | null
  fluency: number | null
  overallScore: number | null
  judgeReason: string | null
  failureCategory: string | null
  failureSuggestion: string | null
  category: string | null
  difficulty: string | null
  businessLine: string | null
  userRole: string | null
  humanReviewStatus: 'PENDING' | 'COMPLETED' | 'SKIPPED' | null
  humanFaithfulness: number | null
  humanAnswerRelevancy: number | null
  humanCompleteness: number | null
  humanFluency: number | null
  humanOverallScore: number | null
  humanReviewNote: string | null
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

export interface EvaluationRunOptions {
  evaluationMode?: 'RETRIEVAL' | 'GENERATION' | 'FULL'
  topK?: number
  judgeModel?: string
  baselineTaskId?: number
}

// 生成测试集并运行评估
export function generateTestset(data: {
  knowledgeId: number
  qaCount?: number
  taskName?: string
  evaluationMode?: string
  topK?: number
  judgeModel?: string
  baselineTaskId?: number
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
  evaluationMode?: string
  topK?: number
  judgeModel?: string
  baselineTaskId?: number
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

export interface EvaluationExperimentVariant {
  id: number
  experimentId: number
  name: string
  evaluationMode: 'RETRIEVAL' | 'GENERATION' | 'FULL'
  topK: number
  judgeModel: string | null
  baselineTaskId: number | null
  sortOrder: number
}

export interface EvaluationExperiment {
  id: number
  name: string
  description: string | null
  knowledgeId: number
  datasetId: number
  status: 'ACTIVE' | 'PAUSED'
  scheduleEnabled: boolean
  cronExpression: string | null
  nextRunTime: string | null
  judgeBudget: number
  maxRetries: number
  alertOnRegression: boolean
  lastRunTime: string | null
  createTime: string
}

export interface EvaluationExperimentRun {
  id: number
  experimentId: number
  triggerType: 'MANUAL' | 'SCHEDULED'
  status: 'LAUNCHING' | 'RUNNING' | 'COMPLETED' | 'FAILED'
  errorMessage: string | null
  startTime: string
  completeTime: string | null
}

export interface EvaluationExperimentDetail {
  experiment: EvaluationExperiment
  variants: EvaluationExperimentVariant[]
  runs: Array<{
    run: EvaluationExperimentRun
    results: Array<{ variant: EvaluationExperimentVariant; task: EvaluationTask; attempt: number }>
  }>
}

export interface EvaluationSliceMetric {
  dimension: string
  value: string
  sampleCount: number
  failureCount: number
  recallAtK: number | null
  mrr: number | null
  faithfulness: number | null
  answerRelevancy: number | null
  overallScore: number | null
}

export interface EvaluationJudgeAlignment {
  taskId: number
  reviewedCount: number
  faithfulnessMae: number | null
  answerRelevancyMae: number | null
  completenessMae: number | null
  fluencyMae: number | null
  overallMae: number | null
  withinOneLevelRate: number | null
}

export interface EvaluationExperimentInput {
  name: string
  description?: string
  knowledgeId: number
  datasetId: number
  scheduleEnabled: boolean
  cronExpression?: string
  judgeBudget: number
  maxRetries: number
  alertOnRegression: boolean
  variants: Array<{
    name: string
    evaluationMode: 'RETRIEVAL' | 'GENERATION' | 'FULL'
    topK: number
    judgeModel?: string
    baselineTaskId?: number
  }>
}

export function listEvaluationExperiments(): Promise<ApiResponse<EvaluationExperiment[]>> {
  return request.get('/api/evaluation/experiment/list')
}

export function getEvaluationExperiment(id: number): Promise<ApiResponse<EvaluationExperimentDetail>> {
  return request.get(`/api/evaluation/experiment/${id}`)
}

export function createEvaluationExperiment(data: EvaluationExperimentInput): Promise<ApiResponse<EvaluationExperiment>> {
  return request.post('/api/evaluation/experiment', data)
}

export function toggleEvaluationExperiment(id: number): Promise<ApiResponse<void>> {
  return request.post(`/api/evaluation/experiment/${id}/toggle`)
}

export function runEvaluationExperiment(id: number): Promise<ApiResponse<EvaluationExperimentRun>> {
  return request.post(`/api/evaluation/experiment/${id}/run`)
}

export function deleteEvaluationExperiment(id: number): Promise<ApiResponse<void>> {
  return request.delete(`/api/evaluation/experiment/${id}`)
}

export function getEvaluationSlices(taskId: number, dimension: string): Promise<ApiResponse<EvaluationSliceMetric[]>> {
  return request.get(`/api/evaluation/operations/task/${taskId}/slices`, { params: { dimension } })
}

export function getHumanReviewQueue(params: { taskId?: number; status?: string; page?: number; pageSize?: number }) {
  return request.get<ApiResponse<{ records: EvaluationQa[]; total: number }>>('/api/evaluation/operations/review/queue', { params })
}

export function submitHumanReview(qaId: number, data: {
  status: 'COMPLETED' | 'SKIPPED'
  faithfulness?: number
  answerRelevancy?: number
  completeness?: number
  fluency?: number
  overallScore?: number
  note?: string
}): Promise<ApiResponse<void>> {
  return request.post(`/api/evaluation/operations/review/${qaId}`, data)
}

export function getJudgeAlignment(taskId: number): Promise<ApiResponse<EvaluationJudgeAlignment>> {
  return request.get(`/api/evaluation/operations/task/${taskId}/alignment`)
}
