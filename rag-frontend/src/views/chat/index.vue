<template>
  <div class="chat-page">
    <aside class="session-sidebar" :class="{ collapsed: sidebarCollapsed }">
      <a-card :bordered="false" class="sidebar-card">
        <template #title>
          <div class="panel-header">
            <span class="panel-title">历史对话</span>
          </div>
        </template>
        <SessionList
          :sessions="chatStore.sessions"
          :current-session-id="chatStore.currentSessionId"
          :loading="chatStore.loading"
          :has-more="chatStore.hasMoreSessions"
          @select="handleSelectSession"
          @new="handleNewSession"
          @delete="handleDeleteSession"
          @load-more="handleLoadMore"
        />
      </a-card>

      <div class="collapse-btn" @click="toggleSidebar">
        <LeftOutlined v-if="!sidebarCollapsed" />
        <RightOutlined v-else />
      </div>
    </aside>

    <main class="chat-main">
      <a-card :bordered="false" class="chat-card">
        <template #title>
          <div class="card-header">
            <div class="header-left">
              <a-button
                v-if="sidebarCollapsed"
                type="text"
                shape="circle"
                @click="toggleSidebar"
              >
                <template #icon>
                  <MenuFoldOutlined />
                </template>
              </a-button>
              <span class="card-title">{{ currentTitle }}</span>
            </div>
            <a-button @click="handleClearMessages">
              <template #icon>
                <DeleteOutlined />
              </template>
              清空对话
            </a-button>
          </div>
        </template>

        <div class="chat-content">
          <div class="message-area">
            <MessageList
              ref="messageListRef"
              :messages="chatStore.messages"
              :loading="chatStore.loading"
              :is-generating="chatStore.isGenerating"
              @source-click="handleSourceClick"
              @quick-question="handleQuickQuestion"
            />
          </div>

          <ChatInput
            ref="chatInputRef"
            :sending="chatStore.loading"
            :is-generating="chatStore.isGenerating"
            :knowledge-id="chatStore.selectedKnowledgeId"
            :model-id="chatStore.selectedModelId"
            @send="handleSendMessage"
            @stop="handleStopGeneration"
            @knowledge-change="handleKnowledgeChange"
            @model-change="handleModelChange"
          />
        </div>
      </a-card>
    </main>

    <a-drawer
      v-model:open="sourceDrawerVisible"
      title="引用来源详情"
      placement="right"
      :width="400"
    >
      <div v-if="selectedSource" class="source-detail">
        <div class="detail-item">
          <label>文档名称</label>
          <p>{{ selectedSource.documentTitle }}</p>
        </div>
        <div class="detail-item">
          <label>相关度分数</label>
          <a-progress
            :percent="selectedSource.score * 100"
            :format="(percent: number) => percent.toFixed(1) + '%'"
          />
        </div>
        <div class="detail-item">
          <label>相关内容</label>
          <div class="content-box">{{ selectedSource.content }}</div>
        </div>
        <div class="detail-actions">
          <a-button type="primary" @click="goToDocument">
            查看完整文档
          </a-button>
        </div>
      </div>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  LeftOutlined,
  RightOutlined,
  MenuFoldOutlined,
  DeleteOutlined,
} from '@ant-design/icons-vue'
import { useChatStore } from '@/stores/chat'
import type { Source } from '@/api/chat'
import SessionList from './components/SessionList.vue'
import MessageList from './components/MessageList.vue'
import ChatInput from './components/ChatInput.vue'

const router = useRouter()
const chatStore = useChatStore()

const messageListRef = ref<InstanceType<typeof MessageList>>()
const chatInputRef = ref<InstanceType<typeof ChatInput>>()

const sidebarCollapsed = ref(false)

const sourceDrawerVisible = ref(false)
const selectedSource = ref<Source | null>(null)

const currentTitle = computed(() => {
  if (!chatStore.currentSessionId) return '智能问答'
  const session = chatStore.sessions.find((s) => s.id === chatStore.currentSessionId)
  return session?.title || '新对话'
})

function toggleSidebar(): void {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

async function handleSelectSession(sessionId: string): Promise<void> {
  await chatStore.selectSession(sessionId)
  chatInputRef.value?.focus()
}

async function handleNewSession(): Promise<void> {
  await chatStore.createNewSession()
  chatInputRef.value?.focus()
}

async function handleDeleteSession(sessionId: string): Promise<void> {
  const success = await chatStore.removeSession(sessionId)
  if (success) {
    message.success('删除成功')
  }
}

function handleLoadMore(): void {
  chatStore.loadMoreSessions()
}

async function handleSendMessage(message: string): Promise<void> {
  await chatStore.sendMessage(message)
}

function handleStopGeneration(): void {
  chatStore.stopGeneration()
  message.info('已停止生成')
}

function handleClearMessages(): void {
  if (chatStore.messages.length === 0) return
  chatStore.clearMessages()
  message.success('对话已清空')
}

function handleSourceClick(source: Source): void {
  selectedSource.value = source
  sourceDrawerVisible.value = true
}

async function handleQuickQuestion(question: string): Promise<void> {
  await chatStore.sendMessage(question)
}

function handleKnowledgeChange(knowledgeId: number | null): void {
  chatStore.setKnowledgeId(knowledgeId)
}

function handleModelChange(modelId: number | null): void {
  chatStore.setModelId(modelId)
}

function goToDocument(): void {
  if (selectedSource.value) {
    router.push(`/document/${selectedSource.value.documentId}`)
    sourceDrawerVisible.value = false
  }
}

onMounted(async () => {
  await chatStore.fetchSessions()
  chatInputRef.value?.focus()
})

onUnmounted(() => {
  if (chatStore.isGenerating) {
    chatStore.stopGeneration()
  }
})
</script>

<style scoped lang="scss">
.chat-page {
  display: flex;
  gap: 16px;
  height: calc(100vh - 64px - 48px);

  .session-sidebar {
    width: 280px;
    flex-shrink: 0;
    position: relative;
    transition: width var(--transition-duration);

    .sidebar-card {
      height: 100%;
    }

    :deep(.ant-card) {
      height: 100%;
      display: flex;
      flex-direction: column;

      .ant-card-head {
        flex-shrink: 0;
      }

      .ant-card-body {
        flex: 1;
        min-height: 0;
        display: flex;
        flex-direction: column;
        overflow: hidden;
      }
    }

    .panel-header {
      display: flex;
      justify-content: space-between;
      align-items: center;

      .panel-title {
        font-family: var(--font-display);
        font-size: 18px;
        font-weight: var(--font-weight-semibold);
      }
    }

    &.collapsed {
      width: 0;

      .sidebar-card {
        visibility: hidden;
      }
    }
  }

  .chat-main {
    flex: 1;
    min-width: 0;

    .chat-card {
      height: 100%;
    }

    :deep(.ant-card) {
      height: 100%;
      display: flex;
      flex-direction: column;

      .ant-card-head {
        flex-shrink: 0;
      }

      .ant-card-body {
        flex: 1;
        min-height: 0;
        display: flex;
        flex-direction: column;
        overflow: hidden;
      }
    }

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      width: 100%;

      .header-left {
        display: flex;
        align-items: center;
        gap: 12px;

        .card-title {
          font-family: var(--font-display);
          font-size: 18px;
          font-weight: var(--font-weight-semibold);
          color: var(--text-primary);
        }
      }
    }

    .chat-content {
      display: flex;
      flex-direction: column;
      height: 100%;

      .message-area {
        flex: 1;
        overflow: hidden;
      }
    }
  }

  .collapse-btn {
    position: absolute;
    right: -12px;
    top: 50%;
    transform: translateY(-50%);
    width: 24px;
    height: 48px;
    background-color: var(--bg-color);
    border: 1px solid var(--border-lighter);
    border-left: none;
    border-radius: 0 12px 12px 0;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    z-index: 10;
    transition: all var(--transition-duration);

    &:hover {
      background-color: var(--bg-page);
      color: var(--primary-color);
    }
  }

  .source-detail {
    padding: 0 20px;

    .detail-item {
      margin-bottom: 24px;

      label {
        display: block;
        font-size: 13px;
        font-weight: 500;
        color: var(--text-secondary);
        margin-bottom: 8px;
      }

      p {
        font-size: 14px;
        color: var(--text-primary);
        margin: 0;
      }

      .content-box {
        padding: 12px;
        background-color: var(--bg-page);
        border-radius: var(--border-radius-base);
        font-size: 14px;
        line-height: 1.7;
        color: var(--text-primary);
        max-height: 300px;
        overflow-y: auto;
      }
    }

    .detail-actions {
      margin-top: 24px;
    }
  }
}

@media (max-width: 768px) {
  .chat-page {
    .session-sidebar {
      position: absolute;
      left: 0;
      top: 0;
      bottom: 0;
      z-index: 100;

      &.collapsed {
        left: -280px;
      }
    }
  }
}
</style>
