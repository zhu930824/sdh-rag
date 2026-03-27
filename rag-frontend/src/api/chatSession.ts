import request from '@/utils/request'
import type { ApiResponse } from '@/types'

export interface ChatSession {
  id: number
  sessionId: string
  userId: number
  title: string
  modelId?: number
  promptTemplateId?: number
  messageCount: number
  totalTokens: number
  isStarred: number
  isArchived: number
  lastMessageTime: string
  createTime: string
}

export interface SessionShare {
  id: number
  sessionId: string
  userId: number
  shareCode: string
  password?: string
  expireTime?: string
  viewCount: number
  status: number
  createTime: string
}

export function getChatSessions(page: number = 1, pageSize: number = 10): Promise<ApiResponse<{ records: ChatSession[], total: number }>> {
  return request.get('/api/chat-session/list', { params: { page, pageSize } })
}

export function getStarredSessions(): Promise<ApiResponse<ChatSession[]>> {
  return request.get('/api/chat-session/starred')
}

export function getArchivedSessions(): Promise<ApiResponse<ChatSession[]>> {
  return request.get('/api/chat-session/archived')
}

export function getChatSession(sessionId: string): Promise<ApiResponse<ChatSession>> {
  return request.get(`/api/chat-session/${sessionId}`)
}

export function createChatSession(data: { title?: string; modelId?: number; promptTemplateId?: number }): Promise<ApiResponse<ChatSession>> {
  return request.post('/api/chat-session', data)
}

export function updateSessionTitle(sessionId: string, title: string): Promise<ApiResponse<null>> {
  return request.put(`/api/chat-session/${sessionId}/title`, { title })
}

export function toggleSessionStar(sessionId: string): Promise<ApiResponse<null>> {
  return request.put(`/api/chat-session/${sessionId}/star`)
}

export function toggleSessionArchive(sessionId: string): Promise<ApiResponse<null>> {
  return request.put(`/api/chat-session/${sessionId}/archive`)
}

export function deleteChatSession(sessionId: string): Promise<ApiResponse<null>> {
  return request.delete(`/api/chat-session/${sessionId}`)
}

export function createSessionShare(sessionId: string, password?: string, expireHours?: number): Promise<ApiResponse<SessionShare>> {
  return request.post(`/api/chat-session/${sessionId}/share`, { password, expireHours })
}

export function getSessionShares(sessionId: string): Promise<ApiResponse<SessionShare[]>> {
  return request.get(`/api/chat-session/${sessionId}/shares`)
}

export function getSharedSession(shareCode: string, password?: string): Promise<ApiResponse<ChatSession>> {
  return request.get(`/api/chat-session/shared/${shareCode}`, { params: { password } })
}

export function closeSessionShare(shareId: number): Promise<ApiResponse<null>> {
  return request.delete(`/api/chat-session/share/${shareId}`)
}

export function exportChatSession(sessionId: string, format: string = 'markdown'): Promise<Blob> {
  return request.get(`/api/chat-session/${sessionId}/export`, { 
    params: { format }, 
    responseType: 'blob' 
  })
}
