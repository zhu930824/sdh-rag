import request from '@/utils/request'

export interface EvaluationTask {
  id: number
  knowledgeId: number
  knowledgeName: string | null
  taskName: string
  qaCount: number
  hitRate: number | null         // 分块级命中率
  docHitRate: number | null      // 文档级命中率
  mrr: number | null             // MRR
  avgHitRank: number | null      // 平均命中排名
  topKHits: string | null        // Top-K命中分布JSON
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
  sourceDocumentId: number | null
  sourceChunkContent: string
  retrievedChunkIds: string
  hit: boolean                   // 分块级命中
  docHit: boolean                 // 文档级命中
  hitRank: number | null         // 分块命中排名
  docHitRank: number | null       // 文档命中排名
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

// 获取评估任务列表（不传知识库ID则返回全部）
export function getEvaluationList(knowledgeId?: number) {
  return request.get<EvaluationTask[]>('/api/evaluation/list', { params: knowledgeId ? { knowledgeId } : {} })
}

// 删除评估任务
export function deleteEvaluationTask(id: number) {
  return request.post(`/api/evaluation/task/${id}/delete`)
}
