<template>
  <div ref="containerRef" class="message-list">
    <!-- 空状态 -->
    <div v-if="messages.length === 0" class="empty-state">
      <div class="empty-icon">
        <el-icon :size="64"><ChatDotRound /></el-icon>
      </div>
      <h3 class="empty-title">开始智能问答</h3>
      <p class="empty-desc">输入您的问题，AI将基于知识库为您生成回答</p>
      <div class="quick-questions">
        <div
          v-for="(question, index) in quickQuestions"
          :key="index"
          class="quick-question-item"
          @click="handleQuickQuestion(question)"
        >
          <el-icon><QuestionFilled /></el-icon>
          <span>{{ question }}</span>
        </div>
      </div>
    </div>

    <!-- 消息列表 -->
    <div v-for="message in messages" :key="message.id" :class="['message-item', message.role]">
      <div class="message-avatar">
        <el-avatar v-if="message.role === 'user'" :size="36" class="user-avatar">
          <el-icon><User /></el-icon>
        </el-avatar>
        <el-avatar v-else :size="36" class="assistant-avatar">
          <el-icon><Service /></el-icon>
        </el-avatar>
      </div>

      <div class="message-body">
        <div class="message-header">
          <span class="role-name">{{ message.role === 'user' ? '我' : 'AI助手' }}</span>
          <span class="message-time">{{ formatTime(message.createTime) }}</span>
        </div>

        <div class="message-content">
          <!-- 用户消息直接显示 -->
          <div v-if="message.role === 'user'" class="message-text user-text">
            {{ message.content }}
          </div>

          <!-- AI消息渲染Markdown -->
          <div v-else class="message-text assistant-text" v-html="renderedContent(message.content)"></div>

          <!-- 打字光标 - 流式输出时显示 -->
          <span v-if="isGenerating && message === lastAssistantMessage" class="typing-cursor"></span>
        </div>

        <!-- 引用来源 -->
        <div v-if="message.sources && message.sources.length > 0" class="message-sources">
          <div class="sources-header">
            <el-icon><Link /></el-icon>
            <span>参考来源</span>
          </div>
          <div class="sources-list">
            <SourceCard
              v-for="source in message.sources"
              :key="source.documentId"
              :source="source"
              @click="handleSourceClick"
            />
          </div>
        </div>

        <!-- 消息操作按钮 -->
        <div v-if="message.role === 'assistant' && message.content && !isGenerating" class="message-actions">
          <el-tooltip content="复制内容" placement="top">
            <el-button :icon="DocumentCopy" size="small" text @click="copyContent(message.content)" />
          </el-tooltip>
        </div>
      </div>
    </div>

    <!-- 加载指示器 -->
    <div v-if="loading" class="loading-indicator">
      <el-icon class="loading-icon"><Loading /></el-icon>
      <span>加载中...</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted } from 'vue'
import { marked } from 'marked'
import hljs from 'highlight.js'
import { ChatDotRound, User, Service, Link, Loading, QuestionFilled, DocumentCopy } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { ChatMessage, Source } from '@/api/chat'
import SourceCard from './SourceCard.vue'

// 配置 marked 使用 highlight.js 进行代码高亮
const renderer = new marked.Renderer()

// 自定义代码块渲染，添加语言标签和复制按钮
renderer.code = ({ text, lang }) => {
  const language = lang && hljs.getLanguage(lang) ? lang : 'plaintext'
  const highlighted = hljs.highlight(text, { language }).value
  return `<div class="code-block">
    <div class="code-header">
      <span class="code-lang">${language}</span>
      <button class="copy-btn" onclick="navigator.clipboard.writeText(this.closest('.code-block').querySelector('code').textContent)">复制</button>
    </div>
    <pre><code class="hljs language-${language}">${highlighted}</code></pre>
  </div>`
}

// 配置 marked 选项
marked.setOptions({
  renderer,
  breaks: true,
  gfm: true,
})

const props = defineProps<{
  messages: ChatMessage[]
  loading?: boolean
  isGenerating?: boolean
}>()

const emit = defineEmits<{
  (e: 'sourceClick', source: Source): void
  (e: 'quickQuestion', question: string): void
}>()

const containerRef = ref<HTMLElement>()

// 快捷问题列表
const quickQuestions = [
  '如何使用知识库？',
  '支持哪些文档格式？',
  '如何提高检索准确率？',
]

// 获取最后一条AI消息
const lastAssistantMessage = computed(() => {
  const assistantMessages = props.messages.filter((m) => m.role === 'assistant')
  return assistantMessages[assistantMessages.length - 1]
})

// 渲染Markdown内容
function renderedContent(content: string): string {
  if (!content) return ''
  return marked.parse(content) as string
}

// 点击快捷问题
function handleQuickQuestion(question: string): void {
  emit('quickQuestion', question)
}

// 复制内容到剪贴板
async function copyContent(content: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(content)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败')
  }
}

// 格式化时间
function formatTime(time: string): string {
  const date = new Date(time)
  const now = new Date()
  const diff = now.getTime() - date.getTime()

  // 一分钟内
  if (diff < 60000) return '刚刚'
  // 一小时内
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  // 今天
  if (date.toDateString() === now.toDateString()) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  // 昨天
  const yesterday = new Date(now)
  yesterday.setDate(yesterday.getDate() - 1)
  if (date.toDateString() === yesterday.toDateString()) {
    return `昨天 ${date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}`
  }
  // 其他
  return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

// 点击来源
function handleSourceClick(source: Source): void {
  emit('sourceClick', source)
}

// 滚动到底部
function scrollToBottom(smooth = true): void {
  nextTick(() => {
    if (containerRef.value) {
      containerRef.value.scrollTo({
        top: containerRef.value.scrollHeight,
        behavior: smooth ? 'smooth' : 'auto',
      })
    }
  })
}

// 监听消息变化自动滚动
watch(
  () => props.messages.length,
  () => {
    scrollToBottom()
  }
)

// 监听生成状态
watch(
  () => props.isGenerating,
  (generating) => {
    if (generating) {
      // 生成时持续滚动
      const interval = setInterval(() => {
        scrollToBottom(false)
        if (!props.isGenerating) {
          clearInterval(interval)
        }
      }, 100)
    }
  }
)

// 初始化滚动到底部
onMounted(() => {
  scrollToBottom(false)
})

// 暴露方法供父组件调用
defineExpose({
  scrollToBottom,
})
</script>

<style scoped lang="scss">
.message-list {
  height: 100%;
  overflow-y: auto;
  padding: 20px;

  // 空状态
  .empty-state {
    height: 100%;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    color: var(--text-secondary);

    .empty-icon {
      margin-bottom: 16px;
      opacity: 0.5;
    }

    .empty-title {
      font-size: 18px;
      font-weight: 500;
      color: var(--text-primary);
      margin-bottom: 8px;
    }

    .empty-desc {
      font-size: 14px;
      margin-bottom: 24px;
    }

    .quick-questions {
      display: flex;
      flex-direction: column;
      gap: 12px;
      width: 100%;
      max-width: 400px;

      .quick-question-item {
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 12px 16px;
        background-color: var(--bg-color);
        border: 1px solid var(--border-lighter);
        border-radius: var(--border-radius-base);
        cursor: pointer;
        transition: all var(--transition-duration);
        font-size: 14px;
        color: var(--text-primary);

        &:hover {
          border-color: var(--primary-light-5);
          background-color: var(--primary-light-9);
          color: var(--primary-color);
        }

        .el-icon {
          font-size: 16px;
          color: var(--primary-color);
        }
      }
    }
  }

  // 消息项
  .message-item {
    display: flex;
    gap: 12px;
    margin-bottom: 24px;

    &.user {
      flex-direction: row-reverse;

      .message-body {
        align-items: flex-end;
      }

      .message-content {
        align-items: flex-end;
      }
    }

    .message-avatar {
      flex-shrink: 0;

      .user-avatar {
        background-color: var(--primary-color);
      }

      .assistant-avatar {
        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      }
    }

    .message-body {
      display: flex;
      flex-direction: column;
      gap: 8px;
      max-width: 75%;

      .message-header {
        display: flex;
        align-items: center;
        gap: 8px;

        .role-name {
          font-size: 13px;
          font-weight: 500;
          color: var(--text-primary);
        }

        .message-time {
          font-size: 12px;
          color: var(--text-placeholder);
        }
      }

      .message-content {
        display: flex;
        flex-direction: column;
        gap: 4px;

        .message-text {
          padding: 12px 16px;
          border-radius: var(--border-radius-base);
          line-height: 1.7;
          word-break: break-word;

          &.user-text {
            background-color: var(--primary-color);
            color: #fff;
          }

          &.assistant-text {
            background-color: var(--bg-page);
            color: var(--text-primary);

            // Markdown 样式
            :deep(p) {
              margin: 0 0 12px;
              &:last-child {
                margin-bottom: 0;
              }
            }

            :deep(pre) {
              margin: 12px 0;
              padding: 0;
              background-color: transparent;
              border-radius: var(--border-radius-small);
              overflow-x: auto;

              code {
                font-family: 'Fira Code', 'Consolas', monospace;
                font-size: 13px;
              }
            }

            // 代码块样式
            :deep(.code-block) {
              margin: 12px 0;
              border-radius: var(--border-radius-base);
              overflow: hidden;
              background-color: #1e1e1e;

              .code-header {
                display: flex;
                justify-content: space-between;
                align-items: center;
                padding: 8px 12px;
                background-color: #2d2d2d;
                border-bottom: 1px solid #3d3d3d;

                .code-lang {
                  font-size: 12px;
                  color: #888;
                  text-transform: uppercase;
                }

                .copy-btn {
                  padding: 4px 8px;
                  font-size: 12px;
                  color: #888;
                  background: transparent;
                  border: 1px solid #3d3d3d;
                  border-radius: 4px;
                  cursor: pointer;
                  transition: all 0.2s;

                  &:hover {
                    color: #fff;
                    border-color: #555;
                  }
                }
              }

              pre {
                margin: 0;
                padding: 12px;
                overflow-x: auto;

                code {
                  font-family: 'Fira Code', 'Consolas', monospace;
                  font-size: 13px;
                  line-height: 1.6;
                }
              }
            }

            :deep(code) {
              padding: 2px 6px;
              background-color: var(--bg-color);
              border-radius: 4px;
              font-family: 'Fira Code', 'Consolas', monospace;
              font-size: 13px;
            }

            :deep(ul),
            :deep(ol) {
              margin: 8px 0;
              padding-left: 20px;
            }

            :deep(li) {
              margin: 4px 0;
            }

            :deep(blockquote) {
              margin: 12px 0;
              padding: 8px 16px;
              border-left: 4px solid var(--primary-color);
              background-color: var(--bg-color);
              color: var(--text-secondary);
            }

            :deep(h1),
            :deep(h2),
            :deep(h3),
            :deep(h4) {
              margin: 16px 0 8px;
              font-weight: 600;
            }

            :deep(a) {
              color: var(--primary-color);
              text-decoration: none;
              &:hover {
                text-decoration: underline;
              }
            }

            :deep(table) {
              width: 100%;
              margin: 12px 0;
              border-collapse: collapse;

              th,
              td {
                padding: 8px 12px;
                border: 1px solid var(--border-lighter);
                text-align: left;
              }

              th {
                background-color: var(--bg-color);
                font-weight: 500;
              }
            }
          }
        }

        // 打字光标动画
        .typing-cursor {
          display: inline-block;
          width: 2px;
          height: 16px;
          background-color: var(--primary-color);
          margin-left: 2px;
          animation: blink 1s infinite;
        }

        @keyframes blink {
          0%,
          50% {
            opacity: 1;
          }
          51%,
          100% {
            opacity: 0;
          }
        }
      }

      // 引用来源
      .message-sources {
        margin-top: 8px;

        .sources-header {
          display: flex;
          align-items: center;
          gap: 6px;
          font-size: 13px;
          font-weight: 500;
          color: var(--text-secondary);
          margin-bottom: 8px;
        }

        .sources-list {
          display: flex;
          flex-direction: column;
          gap: 8px;
        }
      }

      // 消息操作按钮
      .message-actions {
        display: flex;
        gap: 4px;
        margin-top: 8px;
        opacity: 0;
        transition: opacity var(--transition-duration);
      }
    }
  }

  // 消息项悬停时显示操作按钮
  .message-item:hover .message-actions {
    opacity: 1;
  }

  // 加载指示器
  .loading-indicator {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    padding: 20px;
    color: var(--text-secondary);

    .loading-icon {
      animation: spin 1s linear infinite;
    }

    @keyframes spin {
      from {
        transform: rotate(0deg);
      }
      to {
        transform: rotate(360deg);
      }
    }
  }
}
</style>
