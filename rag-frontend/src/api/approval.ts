import request from '@/utils/request'
import type { ApiResponse, PageResult } from '@/types'

export interface ApprovalTask {
  id: number
  flowId: number
  businessType: string
  businessId: number
  applicantId: number
  currentNode: number
  status: number
  createTime?: string
  updateTime?: string
}

export interface ApprovalRecord {
  id: number
  taskId: number
  approverId: number
  nodeOrder: number
  action: number
  comment?: string
  createTime?: string
}

export interface ApprovalDetail {
  task: ApprovalTask
  records: ApprovalRecord[]
}

export function getPendingApprovals(params: {
  page: number
  pageSize: number
}): Promise<ApiResponse<PageResult<ApprovalTask>>> {
  return request.get('/api/approval/pending', { params })
}

export function getMyApprovals(params: {
  page: number
  pageSize: number
}): Promise<ApiResponse<PageResult<ApprovalTask>>> {
  return request.get('/api/approval/my', { params })
}

export function getApprovalDetail(id: number): Promise<ApiResponse<ApprovalDetail>> {
  return request.get(`/api/approval/${id}`)
}

export function approveTask(id: number, comment?: string): Promise<ApiResponse<null>> {
  return request.post(`/api/approval/${id}/approve`, null, { params: { comment } })
}

export function rejectTask(id: number, comment?: string): Promise<ApiResponse<null>> {
  return request.post(`/api/approval/${id}/reject`, null, { params: { comment } })
}