<template>
  <div class="chat-page">
    <!-- 左侧会话列表 -->
    <aside class="session-sidebar" :class="{ collapsed: sidebarCollapsed }">
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

      <!-- 折叠按钮 -->
      <div class="collapse-btn" @click="toggleSidebar">
        <el-icon>
          <ArrowLeft v-if="!sidebarCollapsed" />
          <ArrowRight v-else />
        </el-icon>
      </div>
    </aside>

    <!-- 右侧对话区域 -->
    <main class="chat-main">
      <!-- 顶部标题栏 -->
      <header class="chat-header">
        <div class="header-left">
          <el-button
            v-if="sidebarCollapsed"
            :icon="Expand"
            circle
            text
            @click="toggleSidebar"
          />
          <h2 class="chat-title">{{ currentTitle }}</h2>
        </div>
        <div class="header-right">
          <el-button :icon="Delete" @click="handleClearMessages">清空对话</el-button>
        </div>
      </header>

      <!-- 消息列表区域 -->
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

      <!-- 输入区域 -->
      <ChatInput
        ref="chatInputRef"
        :sending="chatStore.loading"
        :is-generating="chatStore.isGenerating"
        @send="handleSendMessage"
        @stop="handleStopGeneration"
      />
    </main>

    <!-- 来源详情抽屉 -->
    <el-drawer
      v-model="sourceDrawerVisible"
      title="引用来源详情"
      direction="rtl"
      size="400px"
    >
      <div v-if="selectedSource" class="source-detail">
        <div class="detail-item">
          <label>文档名称</label>
          <p>{{ selectedSource.documentTitle }}</p>
        </div>
        <div class="detail-item">
          <label>相关度分数</label>
          <el-progress
            :percentage="selectedSource.score * 100"
            :format="(val: number) => val.toFixed(1) + '%'"
          />
        </div>
        <div class="detail-item">
          <label>相关内容</label>
          <div class="content-box">{{ selectedSource.content }}</div>
        </div>
        <div class="detail-actions">
          <el-button type="primary" @click="goToDocument">
            查看完整文档
          </el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, Expand, Delete } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useChatStore } from '@/stores/chat'
import type { Source } from '@/api/chat'
import SessionList from './components/SessionList.vue'
import MessageList from './components/MessageList.vue'
import ChatInput from './components/ChatInput.vue'

const router = useRouter()
const chatStore = useChatStore()

// 组件引用
const messageListRef = ref<InstanceType<typeof MessageList>>()
const chatInputRef = ref<InstanceType<typeof ChatInput>>()

// 侧边栏折叠状态
const sidebarCollapsed = ref(false)

// 来源详情抽屉
const sourceDrawerVisible = ref(false)
const selectedSource = ref<Source | null>(null)

// 当前会话标题
const currentTitle = computed(() => {
  if (!chatStore.currentSessionId) return '智能问答'
  const session = chatStore.sessions.find((s) => s.id === chatStore.currentSessionId)
  return session?.title || '新对话'
})

// 切换侧边栏
function toggleSidebar(): void {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

// 选择会话
async function handleSelectSession(sessionId: string): Promise<void> {
  await chatStore.selectSession(sessionId)
  chatInputRef.value?.focus()
}

// 新建会话
async function handleNewSession(): Promise<void> {
  await chatStore.createNewSession()
  chatInputRef.value?.focus()
}

// 删除会话
async function handleDeleteSession(sessionId: string): Promise<void> {
  const success = await chatStore.removeSession(sessionId)
  if (success) {
    ElMessage.success('删除成功')
  }
}

// 加载更多会话
function handleLoadMore(): void {
  chatStore.loadMoreSessions()
}

// 发送消息
async function handleSendMessage(message: string): Promise<void> {
  await chatStore.sendMessage(message)
}

// 停止生成
function handleStopGeneration(): void {
  chatStore.stopGeneration()
  ElMessage.info('已停止生成')
}

// 清空消息
function handleClearMessages(): void {
  if (chatStore.messages.length === 0) return
  chatStore.clearMessages()
  ElMessage.success('对话已清空')
}

// 点击来源
function handleSourceClick(source: Source): void {
  selectedSource.value = source
  sourceDrawerVisible.value = true
}

// 点击快捷问题
async function handleQuickQuestion(question: string): Promise<void> {
  await chatStore.sendMessage(question)
}

// 跳转到文档
function goToDocument(): void {
  if (selectedSource.value) {
    router.push(`/document/${selectedSource.value.documentId}`)
    sourceDrawerVisible.value = false
  }
}

// 初始化
onMounted(async () => {
  await chatStore.fetchSessions()
  chatInputRef.value?.focus()
})

// 清理
onUnmounted(() => {
  // 如果正在生成，停止
  if (chatStore.isGenerating) {
    chatStore.stopGeneration()
  }
})
</script>

<style scoped lang="scss">
.chat-page {
  display: flex;
  height: calc(100vh - 100px);
  background-color: var(--bg-page);
  border-radius: var(--border-radius-large);
  overflow: hidden;

  // 左侧会话列表
  .session-sidebar {
    width: 280px;
    background-color: var(--bg-color);
    border-right: 1px solid var(--border-lighter);
    display: flex;
    flex-direction: column;
    position: relative;
    transition: width var(--transition-duration);

    &.collapsed {
      width: 0;
      overflow: hidden;
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
  }

  // 右侧对话区域
  .chat-main {
    flex: 1;
    display: flex;
    flex-direction: column;
    min-width: 0;

    .chat-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 16px 20px;
      background-color: var(--bg-color);
      border-bottom: 1px solid var(--border-lighter);

      .header-left {
        display: flex;
        align-items: center;
        gap: 12px;

        .chat-title {
          font-size: 16px;
          font-weight: 500;
          color: var(--text-primary);
          margin: 0;
        }
      }
    }

    .message-area {
      flex: 1;
      overflow: hidden;
      background-color: var(--bg-color);
    }
  }

  // 来源详情抽屉
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

// 响应式设计
@media (max-width: 768px) {
  .chat-page {
    .session-sidebar {
      position: absolute;
      left: 0;
      top: 0;
      bottom: 0;
      z-index: 100;
      box-shadow: var(--box-shadow);

      &.collapsed {
        left: -280px;
      }
    }
  }
}
</style>
