import request from '@/utils/request'
import type { ApiResponse } from '@/types'

export interface TableInfo {
  name: string
  comment: string
}

export interface ColumnInfo {
  name: string
  type: string
  comment: string
}

export interface NlpQueryResult {
  sql: string
  results: any[]
  message: string
}

export function getTables(): Promise<ApiResponse<TableInfo[]>> {
  return request.get('/api/nlp-query/tables')
}

export function getTableSchema(tableName: string): Promise<ApiResponse<ColumnInfo[]>> {
  return request.get(`/api/nlp-query/schema/${tableName}`)
}

export function executeQuery(question: string): Promise<ApiResponse<NlpQueryResult>> {
  return request.post('/api/nlp-query', { question })
}