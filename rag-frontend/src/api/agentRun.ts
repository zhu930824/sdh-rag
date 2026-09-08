import request from '@/utils/request'
import type { ApiResponse } from '@/types'
import { useUserStore } from '@/stores/user'
import type { StreamEvent } from './chat'

/** 审批(聊天内联):带 X-Agent-Resume: skip,由调用方自行发起 resume 续流 */
export function approveActionSkip(actionId: number): Promise<ApiResponse<unknown>> {
  return request.post(`/api/agent-actions/${actionId}/approve`, null, {
    headers: { 'X-Agent-Resume': 'skip' },
  })
}

export function rejectActionSkip(actionId: number, reason: string): Promise<ApiResponse<unknown>> {
  return request.post(`/api/agent-actions/${actionId}/reject`, { reason }, {
    headers: { 'X-Agent-Resume': 'skip' },
  })
}

/** 运行详情(轮询刷新/恢复界面用) */
export function getAgentRun(runKey: string): Promise<ApiResponse<Record<string, unknown>>> {
  return request.get(`/api/agent/runs/${runKey}`)
}

/** 续跑(SSE 续流):从暂停点继续执行到终态,事件协议与 /api/chat/ask 一致 */
export async function resumeRun(
  runKey: string,
  actionId: number,
  onMessage: (event: StreamEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  const userStore = useUserStore()
  const response = await fetch(`/api/agent/runs/${runKey}/resume`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      token: userStore.token,
    },
    body: JSON.stringify({ actionId }),
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

  const handleLine = (jsonStr: string): void => {
    if (!jsonStr || jsonStr === '[DONE]') return
    try {
      const streamEvent = JSON.parse(jsonStr) as StreamEvent
      onMessage(streamEvent)
    } catch (e) {
      console.error('Failed to parse resume SSE event:', e, jsonStr)
    }
  }

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() || ''
    for (const line of lines) {
      const trimmed = line.trim()
      if (!trimmed || !trimmed.startsWith('data:')) continue
      handleLine(trimmed.slice(5).trim())
    }
  }

  const remaining = buffer.trim()
  if (remaining && remaining.startsWith('data:')) {
    handleLine(remaining.slice(5).trim())
  }
}
