import request from '@/utils/request'

export interface EvaluationTask {
  id: number
  knowledgeId: number
  taskName: string
  qaCount: number
  hitRate: number | null
  mrr: number | null
  avgRecall: number | null
  status: number // 0-待运行, 1-运行中, 2-完成, 3-失败
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
  sourceChunkContent: string
  retrievedChunkIds: string
  hit: boolean
  hitRank: number | null
  createTime: string
}

// 生成测试集并运行评估
export function generateTestset(data: {
  knowledgeId: number
  qaCount?: number
  taskName?: string
}) {
  return request.post<EvaluationTask>('/api/evaluation/generate-testset', data)
}

// 获取评估任务详情
export function getEvaluationTask(id: number) {
  return request.get<EvaluationTask>(`/api/evaluation/task/${id}`)
}

// 获取任务的QA列表
export function getEvaluationQaList(taskId: number) {
  return request.get<EvaluationQa[]>(`/api/evaluation/task/${taskId}/qa-list`)
}

// 获取知识库的评估任务列表
export function getEvaluationList(knowledgeId: number) {
  return request.get<EvaluationTask[]>('/api/evaluation/list', { params: { knowledgeId } })
}

// 删除评估任务
export function deleteEvaluationTask(id: number) {
  return request.post(`/api/evaluation/task/${id}/delete`)
}
