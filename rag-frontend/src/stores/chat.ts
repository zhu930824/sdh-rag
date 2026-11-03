import { defineStore } from 'pinia'
import { ref, computed, watch, onScopeDispose } from 'vue'
import type {
  AgentPendingApproval,
  AgentStepView,
  ChatMessage,
  ChatSession,
  Source,
  RouterInfo,
  AskRequest,
  StreamEvent,
} from '@/api/chat'
import {
  askQuestion,
  getChatSessions,
  getSessionMessages,
  deleteSession,
  createSession,
} from '@/api/chat'
import { approveActionSkip, rejectActionSkip, getAgentRunSnapshot, recoverAgentRun } from '@/api/agentRun'
import { isActiveAgentRun, mergeAgentRunSnapshot } from '@/utils/agentRunState'

export const useChatStore = defineStore('chat', () => {
  // 当前会话ID
  const currentSessionId = ref<string | null>(null)

  // 当前选择的知识库ID
  const selectedKnowledgeId = ref<number | null>(null)

  // 当前选择的模型ID
  const selectedModelId = ref<number | null>(null)

  // 记忆增强开关（默认开启）
  const memoryEnabled = ref(true)

  // Agent 模式开关(会话级偏好,本地持久化)
  const agentEnabled = ref(localStorage.getItem('chat.agentEnabled') === '1')

  // 当前 Agent 运行 key(续流定位)
  const currentRunKey = ref<string | null>(null)

  // 消息列表
  const messages = ref<ChatMessage[]>([])

  // 会话列表
  const sessions = ref<ChatSession[]>([])

  // 加载状态
  const loading = ref(false)

  // 正在生成标记
  const isGenerating = ref(false)

  let pollTimer: ReturnType<typeof setTimeout> | undefined
  let pollEpoch = 0
  let pollFailures = 0
  function scheduleRunPoll(epoch: number): void {
    if (pollTimer) clearTimeout(pollTimer)
    if (!currentSessionId.value || isGenerating.value || !messages.value.some(message =>
      message.agentRunKey && isActiveAgentRun(message.agentStatus))) return
    pollTimer = setTimeout(() => { void pollRuns(epoch) }, Math.min(60000, 2000 * 2 ** Math.min(pollFailures, 5)))
  }
  async function pollRuns(epoch: number): Promise<void> {
    const sessionId = currentSessionId.value
    const targets = messages.value.filter(message => message.agentRunKey && isActiveAgentRun(message.agentStatus))
    for (const target of targets) {
      if (epoch !== pollEpoch || sessionId !== currentSessionId.value || isGenerating.value) return
      try {
        const response = await getAgentRunSnapshot(target.agentRunKey!)
        if (epoch !== pollEpoch || sessionId !== currentSessionId.value || isGenerating.value) return
        pollFailures = 0
        const index = messages.value.findIndex(message => message.id === target.id)
        if (index >= 0 && response.data) messages.value[index] = mergeAgentRunSnapshot(messages.value[index], response.data)
      } catch (error) {
        if (epoch !== pollEpoch || sessionId !== currentSessionId.value) return
        pollFailures++
        const details = error as { response?: { status?: number }; code?: number | string }
        const status = details.response?.status ?? Number(details.code)
        if (status && [401, 403, 404].includes(status)) {
          const unavailable = messages.value.find(message => message.id === target.id)
          if (unavailable) unavailable.agentStatus = 'UNAVAILABLE'
        }
        console.error('刷新后台任务状态失败:', error)
      }
    }
    if (epoch === pollEpoch) scheduleRunPoll(epoch)
  }
  watch(() => JSON.stringify([currentSessionId.value, isGenerating.value,
    messages.value.map(message => `${message.id}:${message.agentRunKey}:${message.agentStatus}`).join('|')]), () => {
    pollEpoch++
    if (pollTimer) clearTimeout(pollTimer)
    scheduleRunPoll(pollEpoch)
  }, { flush: 'post' })
  onScopeDispose(() => { pollEpoch++; if (pollTimer) clearTimeout(pollTimer) })

  // 当前正在生成的消息内容
  const generatingContent = ref('')

  // 当前正在生成的消息来源
  const generatingSources = ref<Source[]>([])

  // 最后的路由信息
  const lastRouterInfo = ref<RouterInfo | null>(null)

  // AbortController 用于取消请求
  let abortController: AbortController | null = null

  // 会话列表分页
  const sessionPage = ref(1)
  const sessionPageSize = ref(20)
  const sessionTotal = ref(0)

  // 是否有更多会话
  const hasMoreSessions = computed(() => (sessions.value?.length || 0) < sessionTotal.value)

  // 获取会话列表
  async function fetchSessions(append = false): Promise<void> {
    if (loading.value) return

    loading.value = true
    try {
      if (!append) {
        sessionPage.value = 1
      }

      const res = await getChatSessions({
        page: sessionPage.value,
        size: sessionPageSize.value,
      })

      if (res.data) {
        if (append) {
          sessions.value = [...sessions.value, ...(res.data.records || [])]
        } else {
          sessions.value = res.data.records || []
        }
        sessionTotal.value = res.data.total || 0
      }
    } catch (error) {
      console.error('获取会话列表失败:', error)
    } finally {
      loading.value = false
    }
  }

  // 加载更多会话
  async function loadMoreSessions(): Promise<void> {
    if (!hasMoreSessions.value || loading.value) return

    sessionPage.value++
    await fetchSessions(true)
  }

  // 选择会话
  async function selectSession(sessionId: string): Promise<void> {
    if (currentSessionId.value === sessionId) return

    currentSessionId.value = sessionId
    messages.value = []

    try {
      loading.value = true
      const res = await getSessionMessages(sessionId)
      messages.value = res.data || []
    } catch (error) {
      console.error('获取会话消息失败:', error)
    } finally {
      loading.value = false
    }
  }

  // 创建新会话
  async function createNewSession(): Promise<ChatSession | null> {
    try {
      const res = await createSession()
      const newSession = res.data
      sessions.value.unshift(newSession)
      currentSessionId.value = newSession.id
      messages.value = []
      return newSession
    } catch (error) {
      console.error('创建会话失败:', error)
      return null
    }
  }

  // 删除会话
  async function removeSession(sessionId: string): Promise<boolean> {
    try {
      await deleteSession(sessionId)
      sessions.value = sessions.value.filter((s) => s.id !== sessionId)

      // 如果删除的是当前会话，清空消息
      if (currentSessionId.value === sessionId) {
        currentSessionId.value = null
        messages.value = []
      }

      return true
    } catch (error) {
      console.error('删除会话失败:', error)
      return false
    }
  }

  // 发送消息
  async function sendMessage(questionOrPayload: string | { message: string; metadataFilter?: import('@/types/search').MetadataFilter }): Promise<void> {
    const question = typeof questionOrPayload === 'string' ? questionOrPayload.trim() : questionOrPayload.message.trim()
    const metadataFilter = typeof questionOrPayload === 'object' ? questionOrPayload.metadataFilter : undefined
    if (!question || isGenerating.value) return

    // 如果没有当前会话，先创建一个
    let sessionId = currentSessionId.value
    if (!sessionId) {
      const newSession = await createNewSession()
      if (!newSession) return
      sessionId = newSession.id
    }

    // 添加用户消息
    const userMessage: ChatMessage = {
      id: Date.now(),
      role: 'user',
      content: question,
      createTime: new Date().toISOString(),
    }
    messages.value.push(userMessage)

    // 初始化AI消息
    const aiMessageId = Date.now() + 1
    const aiMessage: ChatMessage = {
      id: aiMessageId,
      role: 'assistant',
      content: '',
      sources: [],
      createTime: new Date().toISOString(),
    }
    messages.value.push(aiMessage)

    // 重置生成状态
    isGenerating.value = true
    generatingContent.value = ''
    generatingSources.value = []

    // 创建 AbortController
    abortController = new AbortController()

    try {
      const askRequest: AskRequest = {
        question,
        sessionId,
        knowledgeId: selectedKnowledgeId.value,
        modelId: selectedModelId.value,
        memoryEnabled: memoryEnabled.value,
      }
      if (metadataFilter) {
        askRequest.metadataFilter = metadataFilter
      }
      if (agentEnabled.value) {
        askRequest.agent = { enabled: true }
      }
      const targetMessage = () => messages.value.find((m) => m.id === aiMessageId) ?? null
      await askQuestion(askRequest, (event) => handleStreamEvent(targetMessage(), event),
        abortController.signal)
    } catch (error: any) {
      if (error.name !== 'AbortError') {
        console.error('发送消息失败:', error)
        const targetMessage = messages.value.find((m) => m.id === aiMessageId)
        if (targetMessage) {
          targetMessage.content = '发送消息失败，请稍后重试'
        }
      }
    } finally {
      isGenerating.value = false
      abortController = null
    }
  }

  // ===================== Agent 事件与审批 =====================

  // 找到某工具尚未结束的过程卡(RUNNING/等待其 tool_result)
  function findOpenToolStep(message: ChatMessage, toolCode: string, actionId?: number): AgentStepView | undefined {
    const steps = message.agentSteps || []
    for (let i = steps.length - 1; i >= 0; i--) {
      const step = steps[i]
      if (step.type === 'tool' && step.toolCode === toolCode && step.status === 'RUNNING') {
        if (actionId === undefined || step.actionId === undefined || step.actionId === actionId) {
          return step
        }
      }
    }
    return undefined
  }

  /** ask/resume 共用的 SSE 事件分发(Agent 新增 6 类事件 + 原有 5 类) */
  function handleStreamEvent(targetMessage: ChatMessage | null, event: StreamEvent): void {
    if (!targetMessage) return
    switch (event.type) {
      case 'content':
        if (event.content) {
          generatingContent.value += event.content
          targetMessage.content = generatingContent.value
        }
        break
      case 'sources':
        if (event.sources) {
          generatingSources.value = event.sources
          targetMessage.sources = generatingSources.value
        }
        break
      case 'done':
        if (event.historyId) {
          targetMessage.historyId = event.historyId
        }
        targetMessage.agentStatus = 'COMPLETED'
        currentRunKey.value = null
        isGenerating.value = false
        break
      case 'routerInfo':
        if (event.routerInfo) {
          lastRouterInfo.value = event.routerInfo
          targetMessage.routerInfo = event.routerInfo
        }
        break
      case 'error':
        console.error('流式响应错误:', event.code ?? 0, event.message)
        if (event.code && event.code !== 200) {
          // 业务码错误(如 resume 409):保留已有内容,仅提示刷新
          if (!targetMessage.content) {
            targetMessage.content = event.message || '操作失败,请刷新会话后重试'
          }
        } else {
          targetMessage.content = event.message || '生成回复时发生错误'
        }
        currentRunKey.value = null
        isGenerating.value = false
        break

      // ---------- Agent 事件 ----------
      case 'agent_start':
        currentRunKey.value = event.runId || null
        targetMessage.agentRunKey = event.runId || null
        targetMessage.agentStatus = 'RUNNING'
        targetMessage.agentSteps = []
        targetMessage.pendingApprovals = []
        break
      case 'agent_status':
        targetMessage.agentStatus = event.status || targetMessage.agentStatus
        if (event.status === 'WAITING_APPROVAL' || event.status === 'WAITING_ACTION') {
          // 流在此正常结束(无 done);等待用户审批后通过 resume 续流
          currentRunKey.value = null
          isGenerating.value = false
        }
        break
      case 'tool_call':
        targetMessage.agentSteps = targetMessage.agentSteps || []
        targetMessage.agentSteps.push({
          seq: (targetMessage.agentSteps.length + 1),
          type: 'tool',
          toolCode: event.toolCode,
          status: 'RUNNING',
          argsPreview: event.argsPreview,
        })
        break
      case 'tool_result': {
        targetMessage.agentSteps = targetMessage.agentSteps || []
        const step = findOpenToolStep(targetMessage, event.toolCode || '', event.actionId)
          || targetMessage.agentSteps[targetMessage.agentSteps.length - 1]
        if (step) {
          step.status = event.status || 'FAILED'
          step.actionId = event.actionId ?? step.actionId
          step.resultPreview = event.result || event.error
          step.latencyMs = event.latencyMs
        }
        break
      }
      case 'approval_required': {
        if (!event.actionId || !event.runId) break
        targetMessage.pendingApprovals = targetMessage.pendingApprovals || []
        const existing = targetMessage.pendingApprovals.find((a) => a.actionId === event.actionId)
        const approval: AgentPendingApproval = {
          runId: event.runId,
          actionId: event.actionId,
          toolCode: event.toolCode || '',
          toolName: event.toolName || event.toolCode || '',
          argsPreview: event.argsPreview,
          evidencePreview: event.evidencePreview,
          canApprove: event.canApprove ?? false,
        }
        if (existing) {
          Object.assign(existing, approval)
        } else {
          targetMessage.pendingApprovals.push(approval)
        }
        break
      }
    }
  }

  /** Inline decisions are followed through the durable background task. */
  async function decideInline(
    message: ChatMessage,
    approval: AgentPendingApproval,
    decision: 'approve' | 'reject',
    reason?: string,
  ): Promise<void> {
    if (!message || !approval || isGenerating.value) return
    try {
      if (decision === 'approve') {
        await approveActionSkip(approval.actionId)
      } else {
        await rejectActionSkip(approval.actionId, reason || '审批人拒绝')
      }
    } catch (error) {
      console.error('审批操作失败:', error)
      return
    }

    message.pendingApprovals = (message.pendingApprovals || []).filter(
      (a) => a.actionId !== approval.actionId,
    )
    message.agentStatus = decision === 'approve' ? 'WAITING_ACTION' : 'WAITING_APPROVAL'
    currentRunKey.value = null
    // The decision endpoint commits a durable job; polling follows its result instead of racing execution with resume.
    scheduleRunPoll(pollEpoch)
  }

  /** 手动刷新会话消息(外带审批完成后拉取终态) */
  async function recoverRun(target: ChatMessage): Promise<void> {
    const sessionId = currentSessionId.value
    const runKey = target.agentRunKey
    if (!runKey || isGenerating.value) return
    const current = (): ChatMessage | undefined => sessionId === currentSessionId.value
      ? messages.value.find(item => item.id === target.id && item.agentRunKey === runKey) : undefined
    const diagnosis = await getAgentRunSnapshot(runKey)
    const selected = current()
    if (!selected || !diagnosis.data) return
    Object.assign(selected, mergeAgentRunSnapshot(selected, diagnosis.data))
    if (!selected.agentCanRecover) return
    const receipt = await recoverAgentRun(runKey)
    const accepted = current()
    if (accepted && receipt.data) {
      accepted.agentStatus = receipt.data.status
      accepted.agentCanRecover = false
      accepted.agentRecoveryReason = undefined
      accepted.content = '恢复任务已进入队列，正在等待重新执行。'
      scheduleRunPoll(pollEpoch)
    }
  }

  /** 手动刷新会话消息(外带审批完成后拉取终态) */
  async function refreshSessionMessages(): Promise<void> {
    if (!currentSessionId.value) return
    try {
      const res = await getSessionMessages(currentSessionId.value)
      messages.value = res.data || []
    } catch (error) {
      console.error('刷新会话消息失败:', error)
    }
  }

  /** 切换 Agent 模式(本地持久化) */
  function setAgentEnabled(enabled: boolean): void {
    agentEnabled.value = enabled
    localStorage.setItem('chat.agentEnabled', enabled ? '1' : '0')
  }

  // 停止生成
  function stopGeneration(): void {
    if (abortController) {
      abortController.abort()
      abortController = null
    }
    isGenerating.value = false
  }

  // 清空当前会话消息
  function clearMessages(): void {
    messages.value = []
  }

  // 重置状态
  function reset(): void {
    currentSessionId.value = null
    messages.value = []
    sessions.value = []
    loading.value = false
    isGenerating.value = false
    generatingContent.value = ''
    generatingSources.value = []
    selectedKnowledgeId.value = null
    currentRunKey.value = null
    if (abortController) {
      abortController.abort()
      abortController = null
    }
  }

  // 设置选中的知识库
  function setKnowledgeId(knowledgeId: number | null): void {
    selectedKnowledgeId.value = knowledgeId
  }

  // 设置选中的模型
  function setModelId(modelId: number | null): void {
    selectedModelId.value = modelId
  }

  // 设置记忆增强开关
  function setMemoryEnabled(enabled: boolean): void {
    memoryEnabled.value = enabled
  }

  return {
    // 状态
    currentSessionId,
    messages,
    sessions,
    loading,
    isGenerating,
    generatingContent,
    generatingSources,
    lastRouterInfo,
    sessionPage,
    sessionPageSize,
    sessionTotal,
    selectedKnowledgeId,
    selectedModelId,
    memoryEnabled,
    agentEnabled,
    currentRunKey,

    // 计算属性
    hasMoreSessions,

    // 方法
    fetchSessions,
    loadMoreSessions,
    selectSession,
    createNewSession,
    removeSession,
    sendMessage,
    stopGeneration,
    clearMessages,
    reset,
    setKnowledgeId,
    setModelId,
    setMemoryEnabled,
    setAgentEnabled,
    decideInline,
    refreshSessionMessages,
    recoverRun,
  }
})
