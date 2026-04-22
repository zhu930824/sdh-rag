import request from '@/utils/request'

export interface TestDataset {
  id: number
  name: string
  description: string | null
  itemCount: number
  negativeCount: number
  datasetType: string  // builtin/custom
  fileName: string | null
  userId: number | null
  createTime: string
  updateTime: string
}

export interface TestDatasetItem {
  id?: number
  datasetId?: number
  question: string
  expectedAnswer: string | null
  sourceChunkId: string | null
  sourceDocumentId: number | null
  isNegative: boolean | null
  externalId: string | null
  sortOrder: number | null
  createTime: string | null
}

// 获取数据集列表
export function getDatasetList() {
  return request.get<TestDataset[]>('/api/dataset/list')
}

// 获取数据集详情
export function getDatasetDetail(id: number) {
  return request.get<TestDataset>(`/api/dataset/${id}`)
}

// 获取数据集问题列表
export function getDatasetItems(id: number) {
  return request.get<TestDatasetItem[]>(`/api/dataset/${id}/items`)
}

// 创建数据集
export function createDataset(data: { name: string; description?: string }) {
  return request.post<TestDataset>('/api/dataset/create', data)
}

// 更新数据集
export function updateDataset(id: number, data: { name: string; description?: string }) {
  return request.put(`/api/dataset/${id}`, data)
}

// 删除数据集
export function deleteDataset(id: number) {
  return request.delete(`/api/dataset/${id}`)
}

// 批量添加问题
export function addDatasetItems(id: number, items: TestDatasetItem[]) {
  return request.post(`/api/dataset/${id}/items`, items)
}

// 添加单个问题
export function addDatasetItem(id: number, item: TestDatasetItem) {
  return request.post(`/api/dataset/${id}/item`, item)
}

// 更新问题
export function updateDatasetItem(id: number, itemId: number, item: TestDatasetItem) {
  return request.put(`/api/dataset/${id}/items/${itemId}`, item)
}

// 删除问题
export function removeDatasetItem(id: number, itemId: number) {
  return request.delete(`/api/dataset/${id}/items/${itemId}`)
}

// 从文件导入创建数据集
export function importDataset(formData: FormData) {
  return request.post<TestDataset>('/api/dataset/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

// 运行数据集评估
export function runDatasetEvaluation(datasetId: number, knowledgeId: number) {
  return request.post(`/api/evaluation/dataset/${datasetId}`, { knowledgeId })
}

// 导出评估报告
export function exportEvaluationReport(taskId: number) {
  return request.get(`/api/evaluation/task/${taskId}/export`, { responseType: 'blob' })
}
