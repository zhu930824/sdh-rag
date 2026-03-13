import request, { axiosInstance } from '@/utils/request'
import type { ApiResponse, PageResult } from '@/types'
import { useUserStore } from '@/stores/user'

// 聊天消息类型
export interface ChatMessage {
  id: number
  role: 'user' | 'assistant'
  content: string
  sources?: Source[]
  createTime: string
}

// 引用来源类型
export interface Source {
  documentId: number
  documentTitle: string
  content: string
  score: number
}

// 会话类型
export interface ChatSession {
  id: string
  title: string
  createTime: string
  updateTime: string
}

// 流式响应事件类型
export interface StreamEvent {
  type: 'content' | 'sources' | 'done' | 'error'
  content?: string
  sources?: Source[]
  message?: string
}

// 发起问答（流式响应）
export async function askQuestion(
  data: { question: string; sessionId?: string },
  onMessage: (event: StreamEvent) => void,
  signal?: AbortSignal
): Promise<void> {
  const userStore = useUserStore()
  const baseUrl = import.meta.env.VITE_API_BASE_URL || ''

  const response = await fetch(`${baseUrl}/api/chat/ask`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${userStore.token}`,
    },
    body: JSON.stringify(data),
    signal,
  })

  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`)
  }

  const reader = response.body?.getReader()
  if (!reader) {
    throw new Error('No response body')
  }

  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() || ''

    for (const line of lines) {
      if (line.startsWith('data: ')) {
        const jsonStr = line.slice(6).trim()
        if (!jsonStr || jsonStr === '[DONE]') continue

        try {
          const event = JSON.parse(jsonStr) as StreamEvent
          onMessage(event)
        } catch (e) {
          console.error('Failed to parse SSE event:', e)
        }
      }
    }
  }
}

// 获取历史对话列表
export function getChatHistory(params: { page: number; size: number }): Promise<ApiResponse<PageResult<ChatSession>>> {
  return request.get('/api/chat/history', { params })
}

// 获取会话消息
export function getSessionMessages(sessionId: string): Promise<ApiResponse<ChatMessage[]>> {
  return request.get(`/api/chat/session/${sessionId}/messages`)
}

// 删除会话
export function deleteSession(sessionId: string): Promise<ApiResponse<null>> {
  return request.delete(`/api/chat/session/${sessionId}`)
}

// 创建新会话
export function createSession(): Promise<ApiResponse<ChatSession>> {
  return request.post('/api/chat/session')
}

// 更新会话标题
export function updateSessionTitle(sessionId: string, title: string): Promise<ApiResponse<null>> {
  return request.put(`/api/chat/session/${sessionId}/title`, { title })
}
