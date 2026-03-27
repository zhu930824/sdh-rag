import request from '@/utils/request'
import type { ApiResponse } from '@/types'

export interface DocumentVersion {
  id: number
  documentId: number
  versionNumber: number
  content: string
  changeSummary?: string
  userId: number
  status: number
  createTime: string
}

export function getDocumentVersions(documentId: number): Promise<ApiResponse<DocumentVersion[]>> {
  return request.get(`/api/document-version/list/${documentId}`)
}

export function getDocumentVersion(id: number): Promise<ApiResponse<DocumentVersion>> {
  return request.get(`/api/document-version/${id}`)
}

export function getLatestVersion(documentId: number): Promise<ApiResponse<DocumentVersion>> {
  return request.get(`/api/document-version/latest/${documentId}`)
}

export function createVersion(documentId: number, changeSummary?: string): Promise<ApiResponse<DocumentVersion>> {
  return request.post(`/api/document-version/${documentId}`, null, { params: { changeSummary } })
}

export function rollbackVersion(versionId: number): Promise<ApiResponse<null>> {
  return request.post(`/api/document-version/rollback/${versionId}`)
}
