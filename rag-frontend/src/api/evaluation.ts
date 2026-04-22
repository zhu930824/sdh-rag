import request from '@/utils/request'

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
  createTime: string
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
}) {
  return request.post<EvaluationTask>('/api/evaluation/generate-testset', data)
}

// 导入外部测试集
export function importTestset(formData: FormData) {
  return request.post<EvaluationTask>('/api/evaluation/import', formData, {
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
}) {
  return request.post<EvaluationTask>('/api/evaluation/builtin', data)
}

// 获取内置数据集列表
export function getBuiltinDatasets() {
  return request.get<DatasetInfo[]>('/api/evaluation/builtin/list')
}

// 获取评估任务详情
export function getEvaluationTask(id: number) {
  return request.get<EvaluationTask>(`/api/evaluation/task/${id}`)
}

// 获取任务的QA列表
export function getEvaluationQaList(taskId: number) {
  return request.get<EvaluationQa[]>(`/api/evaluation/task/${taskId}/qa-list`)
}

// 获取评估任务列表（分页）
export function getEvaluationList(params: { page: number; pageSize: number; knowledgeId?: number }) {
  return request.get<{ records: EvaluationTask[]; total: number }>('/api/evaluation/list', { params })
}

// 删除评估任务
export function deleteEvaluationTask(id: number) {
  return request.post(`/api/evaluation/task/${id}/delete`)
}

// 导出评估报告
export function exportEvaluationReport(taskId: number) {
  return request.get(`/api/evaluation/task/${taskId}/export`, { responseType: 'blob' })
}
