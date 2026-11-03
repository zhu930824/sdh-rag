import request from '@/utils/request'
import type { ApiResponse } from '@/types'

export interface PlatformJob {
  id: number
  jobType: string
  resourceType: string | null
  resourceId: number | null
  status: 'PENDING' | 'RUNNING' | 'RETRY_WAIT' | 'SUCCEEDED' | 'FAILED' | 'DEAD_LETTER' | 'CANCEL_REQUESTED' | 'CANCELLED'
  priority: number
  attempts: number
  maxAttempts: number
  availableAt: string
  heartbeatAt: string | null
  traceId: string
  resultSummary: string | null
  errorMessage: string | null
  startedAt: string | null
  finishedAt: string | null
  createTime: string
  updateTime: string
}

export interface ReliableExecutionAlert {
  code: string
  severity: 'WARNING' | 'CRITICAL'
  message: string
  current: number
  threshold: number
}

export interface ReliableExecutionMetrics {
  sampledAt: string | null
  pendingJobs: number
  retryWaitJobs: number
  runningJobs: number
  deadLetterJobs: number
  outboxBacklog: number
  outboxDeadLetters: number
  webhookBacklog: number
  webhookDeadLetters: number
  jobs24h: number
  succeeded24h: number
  failed24h: number
  retried24h: number
  successRate24h: number
  oldestQueuedSeconds: number
  alerts: ReliableExecutionAlert[]
}

export function getJobs(params: { page: number; pageSize: number; status?: string; jobType?: string }): Promise<ApiResponse<{ records: PlatformJob[]; total: number }>> {
  return request.get('/api/jobs', { params })
}

export function cancelJob(id: number): Promise<ApiResponse<void>> {
  return request.post(`/api/jobs/${id}/cancel`)
}

export function replayJob(id: number): Promise<ApiResponse<void>> {
  return request.post(`/api/jobs/${id}/replay`)
}

export function getReliableExecutionMetrics(): Promise<ApiResponse<ReliableExecutionMetrics>> {
  return request.get('/api/admin/reliable-execution/metrics')
}
