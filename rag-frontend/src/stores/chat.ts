import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { ChatMessage, ChatSession, Source } from '@/api/chat'
import { askQuestion, getChatSessions, getSessionMessages, deleteSession, createSession } from '@/api/chat'

export const useChatStore = defineStore('chat', () => {
  // 当前会话ID
  const currentSessionId = ref<string | null>(null)

  // 当前选择的知识库ID
  const selectedKnowledgeId = ref<number | null>(null)

  // 当前选择的模型ID
  const selectedModelId = ref<number | null>(null)

  // 消息列表
  const messages = ref<ChatMessage[]>([])

  // 会话列表
  const sessions = ref<ChatSession[]>([])

  // 加载状态
  const loading = ref(false)

  // 正在生成标记
  const isGenerating = ref(false)

  // 当前正在生成的消息内容
  const generatingContent = ref('')

  // 当前正在生成的消息来源
  const generatingSources = ref<Source[]>([])

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
  async function sendMessage(question: string): Promise<void> {
    if (!question.trim() || isGenerating.value) return

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
      content: question.trim(),
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
      await askQuestion(
        {
          question: question.trim(),
          sessionId,
          knowledgeId: selectedKnowledgeId.value,
          modelId: selectedModelId.value,
        },
        (event) => {
          // 处理流式事件
          const targetMessage = messages.value.find((m) => m.id === aiMessageId)
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
              isGenerating.value = false
              break
            case 'error':
              console.error('流式响应错误:', event.message)
              targetMessage.content = event.message || '生成回复时发生错误'
              isGenerating.value = false
              break
          }
        },
        abortController.signal
      )
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

  return {
    // 状态
    currentSessionId,
    messages,
    sessions,
    loading,
    isGenerating,
    generatingContent,
    generatingSources,
    sessionPage,
    sessionPageSize,
    sessionTotal,
    selectedKnowledgeId,
    selectedModelId,

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
  }
})
