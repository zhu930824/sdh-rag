import request from '@/utils/request'
import type { ApiResponse, PageResult } from '@/types'
import type { MetadataFilter } from '@/types/search'
import { useUserStore } from '@/stores/user'

// 聊天消息类型
export interface ChatMessage {
  id: number | string
  role: 'user' | 'assistant'
  content: string
  sources?: Source[] | null
  createTime: string
  historyId?: number // 聊天历史ID，用于评价
  userRating?: number // 用户评价：1-点赞，0-点踩，undefined-未评价
  routerInfo?: RouterInfo | null // 路由信息
  // ---- Agent 字段(回放来自 chat_history;运行态由 SSE 写入) ----
  knowledgeId?: number | null
  modelId?: number | null
  agentRunKey?: string | null
  agentTrace?: AgentTrace | null
  /** 运行态:Agent 状态(RUNNING/WAITING_APPROVAL/COMPLETED/...) */
  agentStatus?: string | null
  agentCanRecover?: boolean
  agentRecoveryReason?: string
  /** 运行态:工具调用过程卡 */
  agentSteps?: AgentStepView[]
  /** 运行态:待审批动作 */
  pendingApprovals?: AgentPendingApproval[]
}

// 引用来源类型
export interface Source {
  documentId: number
  documentTitle: string
  content: string
  score: number
  chunks?: SourceChunk[]
  mixedGenerations?: boolean
}

export interface SourceChunk {
  vectorId?: string | null
  chunkIndex?: number | null
  totalChunks?: number | null
  indexRevision?: number | null
  indexedContentSha256?: string | null
  chunkContentSha256?: string | null
  indexedChunkHashVerified?: boolean
  content: string
  score?: number | null
  truncated?: boolean
}

// 知识库评分
export interface KnowledgeBaseScore {
  knowledgeBaseId: number
  knowledgeBaseName: string
  score: number
}

// 路由信息
export interface RouterInfo {
  used: boolean
  needRetrieval: boolean
  reason: string
  selectedKnowledgeBaseId?: number
  selectedKnowledgeBaseName?: string
  candidates?: KnowledgeBaseScore[]
}

// 会话类型
export interface ChatSession {
  id: string
  title: string
  createTime: string
  updateTime: string
  messageCount?: number
}

// Agent 工具步骤(实时展示 / 历史回放共用)
export interface AgentStepView {
  seq: number
  type: 'llm' | 'tool'
  toolCode?: string
  actionId?: number
  status: string
  argsPreview?: string
  resultPreview?: string
  latencyMs?: number
}

// Agent 运行轨迹(历史回放,来自 chat_history.agent_trace)
export interface AgentTrace {
  runId: string
  status: string
  steps: AgentStepView[]
}

// 待审批动作(实时事件 approval_required)
export interface AgentPendingApproval {
  runId: string
  actionId: number
  toolCode: string
  toolName: string
  argsPreview?: string
  evidencePreview?: string
  canApprove: boolean
}

// Agent 模式请求配置
export interface AgentAskConfig {
  enabled?: boolean
  maxSteps?: number
}

// 流式响应事件类型
export interface StreamEvent {
  type:
    | 'content'
    | 'sources'
    | 'done'
    | 'error'
    | 'routerInfo'
    | 'agent_start'
    | 'agent_status'
    | 'tool_call'
    | 'tool_result'
    | 'approval_required'
  content?: string
  sources?: Source[]
  message?: string
  /** 业务码(如 resume 前置校验 404/409/403) */
  code?: number
  historyId?: number // 聊天历史ID，用于评价
  routerInfo?: RouterInfo // 路由信息
  // ---- Agent 事件字段 ----
  runId?: string
  maxSteps?: number
  status?: string
  pendingActionIds?: number[]
  stepSeq?: number
  toolCode?: string
  toolName?: string
  argsPreview?: string
  actionId?: number
  result?: string
  error?: string
  latencyMs?: number
  canApprove?: boolean
  evidencePreview?: string
}

// 问答请求参数
export interface AskRequest {
  question: string
  sessionId?: string
  knowledgeId?: number | null
  modelId?: number | null
  /** 是否启用记忆增强，默认true */
  memoryEnabled?: boolean
  /** 元数据筛选条件 */
  metadataFilter?: MetadataFilter
  /** Agent 模式(开启后 LLM 可调用受控业务工具;需审批时暂停等待) */
  agent?: AgentAskConfig
}

// 发起问答（流式响应）
export async function askQuestion(
  data: AskRequest,
  onMessage: (event: StreamEvent) => void,
  signal?: AbortSignal
): Promise<void> {
  const userStore = useUserStore()

  const response = await fetch('/api/chat/ask', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      token: userStore.token,
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

    // 按行处理
    const lines = buffer.split('\n')
    buffer = lines.pop() || ''

    for (const line of lines) {
      const trimmed = line.trim()
      if (!trimmed) continue

      if (trimmed.startsWith('data:')) {
        const jsonStr = trimmed.slice(5).trim()
        if (!jsonStr || jsonStr === '[DONE]') continue

        try {
          const streamEvent = JSON.parse(jsonStr) as StreamEvent
          onMessage(streamEvent)
        } catch (e) {
          console.error('Failed to parse SSE event:', e, jsonStr)
        }
      }
    }
  }

  // 处理剩余的 buffer
  const remaining = buffer.trim()
  if (remaining && remaining.startsWith('data:')) {
    const jsonStr = remaining.slice(5).trim()
    if (jsonStr && jsonStr !== '[DONE]') {
      try {
        const streamEvent = JSON.parse(jsonStr) as StreamEvent
        onMessage(streamEvent)
      } catch (e) {
        console.error('Failed to parse SSE event:', e, jsonStr)
      }
    }
  }
}

// 获取会话列表（按session分组）
export function getChatSessions(params: { page: number; size: number }): Promise<ApiResponse<PageResult<ChatSession>>> {
  return request.get('/api/chat/sessions', { params })
}

// 获取历史对话列表（兼容旧接口）
export function getChatHistory(params: { page: number; size: number }): Promise<ApiResponse<PageResult<ChatSession>>> {
  return request.get('/api/chat/history', { params })
}

// 获取会话消息
export function getSessionMessages(sessionId: string): Promise<ApiResponse<ChatMessage[]>> {
  return request.get(`/api/chat/session/${sessionId}/messages`)
}

// 删除会话
export function deleteSession(sessionId: string): Promise<ApiResponse<null>> {
  return request.post(`/api/chat/session/delete/${sessionId}`)
}

// 创建新会话
export function createSession(): Promise<ApiResponse<ChatSession>> {
  return request.post('/api/chat/session')
}

// 更新会话标题
export function updateSessionTitle(sessionId: string, title: string): Promise<ApiResponse<null>> {
  return request.post(`/api/chat/session/${sessionId}/title`, { title })
}
