import request from '@/utils/request'
import type { ApiResponse } from '@/types'

export interface KnowledgeSource {
  id: number
  knowledgeId: number
  name: string
  sourceType: string
  configJson: string
  credentialRef: string | null
  syncCursor: string | null
  status: number
  syncEnabled: number
  syncIntervalMinutes: number
  nextSyncTime: string | null
  consecutiveFailures: number
  lastError: string | null
  lastSyncTime: string | null
  lastSyncStatus: 'COMPLETED' | 'FAILED' | null
  createTime: string
}
export interface SyncTask {
  id: number
  taskStatus: 'RUNNING' | 'COMPLETED' | 'FAILED'
  scannedCount: number
  createdCount: number
  updatedCount: number
  unchangedCount: number
  deletedCount: number
  errorMessage: string | null
  startTime: string
  finishTime: string | null
}
export interface SourceCommand {
  name: string
  sourceType: 'api_push' | 'rest_json'
  configuration: Record<string, unknown>
  credentialRef?: string
  status?: number
  syncEnabled?: boolean
  syncIntervalMinutes?: number
}
export interface SourceApiKey {
  id: number
  name: string
  keyPrefix: string
  rateLimitPerMinute: number
  status: number
  expiresAt: string | null
  lastUsedAt: string | null
  createTime: string
  revokeTime: string | null
}
export interface CreatedSourceApiKey {
  id: number
  name: string
  keyPrefix: string
  apiKey: string
  rateLimitPerMinute: number
  expiresAt: string | null
  createTime: string
}
export interface SourceApiAudit {
  id: number
  requestId: string
  itemCount: number
  taskId: number | null
  resultStatus: 'SUCCESS' | 'FAILED'
  responseCode: number
  errorMessage: string | null
  durationMs: number
  userAgent: string | null
  createTime: string
}
export function getKnowledgeSources(knowledgeId: number): Promise<ApiResponse<KnowledgeSource[]>> {
  return request.get(`/api/knowledge-base/${knowledgeId}/sources`)
}
export function createKnowledgeSource(knowledgeId: number, data: SourceCommand): Promise<ApiResponse<KnowledgeSource>> {
  return request.post(`/api/knowledge-base/${knowledgeId}/sources`, data)
}
export function updateKnowledgeSource(knowledgeId: number, sourceId: number, data: SourceCommand): Promise<ApiResponse<KnowledgeSource>> {
  return request.post(`/api/knowledge-base/${knowledgeId}/sources/${sourceId}`, data)
}
export function disableKnowledgeSource(knowledgeId: number, sourceId: number): Promise<ApiResponse<void>> {
  return request.delete(`/api/knowledge-base/${knowledgeId}/sources/${sourceId}`)
}
export function ingestKnowledgeSource(knowledgeId: number, sourceId: number, data: unknown): Promise<ApiResponse<unknown>> {
  return request.post(`/api/knowledge-base/${knowledgeId}/sources/${sourceId}/ingest`, data)
}
export function getKnowledgeSyncTasks(knowledgeId: number, sourceId: number): Promise<ApiResponse<SyncTask[]>> {
  return request.get(`/api/knowledge-base/${knowledgeId}/sources/${sourceId}/tasks`)
}
export function pullKnowledgeSource(knowledgeId: number, sourceId: number): Promise<ApiResponse<unknown>> {
  return request.post(`/api/knowledge-base/${knowledgeId}/sources/${sourceId}/pull`)
}
export function getSourceApiKeys(knowledgeId: number, sourceId: number): Promise<ApiResponse<SourceApiKey[]>> {
  return request.get(`/api/knowledge-base/${knowledgeId}/sources/${sourceId}/api-keys`)
}
export function createSourceApiKey(knowledgeId: number, sourceId: number, data: { name: string; rateLimitPerMinute: number; expiresAt?: string }): Promise<ApiResponse<CreatedSourceApiKey>> {
  return request.post(`/api/knowledge-base/${knowledgeId}/sources/${sourceId}/api-keys`, data)
}
export function rotateSourceApiKey(knowledgeId: number, sourceId: number, keyId: number): Promise<ApiResponse<CreatedSourceApiKey>> {
  return request.post(`/api/knowledge-base/${knowledgeId}/sources/${sourceId}/api-keys/${keyId}/rotate`)
}
export function revokeSourceApiKey(knowledgeId: number, sourceId: number, keyId: number): Promise<ApiResponse<void>> {
  return request.delete(`/api/knowledge-base/${knowledgeId}/sources/${sourceId}/api-keys/${keyId}`)
}
export function getSourceApiAudits(knowledgeId: number, sourceId: number, limit = 50): Promise<ApiResponse<SourceApiAudit[]>> {
  return request.get(`/api/knowledge-base/${knowledgeId}/sources/${sourceId}/api-audits`, { params: { limit } })
}
