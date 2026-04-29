<template>
  <div ref="containerRef" class="message-list">
    <div v-if="messages.length === 0" class="empty-state">
      <div class="empty-icon">
        <CommentOutlined :style="{ fontSize: '64px' }" />
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
          <QuestionCircleOutlined />
          <span>{{ question }}</span>
        </div>
      </div>
    </div>

    <div v-for="message in messages" :key="message.id" :class="['message-item', message.role]">
      <div class="message-avatar">
        <a-avatar v-if="message.role === 'user'" :size="36" class="user-avatar">
          <template #icon>
            <UserOutlined />
          </template>
        </a-avatar>
        <a-avatar v-else :size="36" class="assistant-avatar">
          <template #icon>
            <RobotOutlined />
          </template>
        </a-avatar>
      </div>

      <div class="message-body">
        <div class="message-header">
          <span class="role-name">{{ message.role === 'user' ? '我' : 'AI助手' }}</span>
          <span class="message-time">{{ formatTime(message.createTime) }}</span>
        </div>

        <div class="message-content">
          <div v-if="message.role === 'user'" class="message-text user-text">
            {{ message.content }}
          </div>

          <div v-else class="message-text assistant-text" v-html="renderedContent(message.content)"></div>

          <span v-if="isGenerating && message === lastAssistantMessage" class="typing-cursor"></span>
        </div>

        <!-- 在 message-sources 之前添加 -->
        <div
          v-if="message.routerInfo && message.routerInfo.used"
          class="message-router-info"
        >
          <div class="router-header">
            <BulbOutlined />
            <span>智能路由</span>
          </div>
          <div class="router-detail">
            <div class="router-item">
              <span class="label">决策:</span>
              <span>{{ message.routerInfo.reason }}</span>
            </div>
            <div v-if="message.routerInfo.needRetrieval" class="router-item">
              <span class="label">已选择:</span>
              <a-tag color="blue">{{ message.routerInfo.selectedKnowledgeBaseName }}</a-tag>
            </div>
            <div v-if="message.routerInfo.candidates && message.routerInfo.candidates.length > 1" class="router-item">
              <span class="label">候选:</span>
              <span class="candidates">
                <span v-for="(c, idx) in message.routerInfo.candidates" :key="c.knowledgeBaseId">
                  {{ c.knowledgeBaseName }}({{ (c.score * 100).toFixed(0) }}%){{ idx < message.routerInfo.candidates!.length - 1 ? ', ' : '' }}
                </span>
              </span>
            </div>
          </div>
        </div>

        <div v-if="message.sources && message.sources.length > 0" class="message-sources">
          <div class="sources-header">
            <LinkOutlined />
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

        <div v-if="message.role === 'assistant' && message.content && !isGenerating" class="message-actions">
          <a-tooltip title="复制内容">
            <a-button type="text" size="small" @click="copyContent(message.content)">
              <template #icon>
                <CopyOutlined />
              </template>
            </a-button>
          </a-tooltip>
          <a-tooltip v-if="message.historyId" :title="message.userRating === 1 ? '已点赞' : '点赞'">
            <a-button
              type="text"
              size="small"
              :class="{ 'action-active': message.userRating === 1 }"
              @click="handleLike(message)"
            >
              <template #icon>
                <LikeFilled v-if="message.userRating === 1" />
                <LikeOutlined v-else />
              </template>
            </a-button>
          </a-tooltip>
          <a-tooltip v-if="message.historyId" :title="message.userRating === 0 ? '已点踩' : '点踩'">
            <a-button
              type="text"
              size="small"
              :class="{ 'action-active': message.userRating === 0 }"
              @click="handleDislike(message)"
            >
              <template #icon>
                <DislikeFilled v-if="message.userRating === 0" />
                <DislikeOutlined v-else />
              </template>
            </a-button>
          </a-tooltip>
        </div>
      </div>
    </div>

    <div v-if="loading" class="loading-indicator">
      <a-spin />
      <span>加载中...</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted } from 'vue'
import { marked } from 'marked'
import hljs from 'highlight.js'
import { message } from 'ant-design-vue'
import {
  CommentOutlined,
  UserOutlined,
  RobotOutlined,
  LinkOutlined,
  QuestionCircleOutlined,
  CopyOutlined,
  LikeOutlined,
  DislikeOutlined,
  LikeFilled,
  DislikeFilled,
  BulbOutlined,
} from '@ant-design/icons-vue'
import type { ChatMessage, Source } from '@/api/chat'
import SourceCard from './SourceCard.vue'
import { createFeedback } from '@/api/feedback'

const renderer = new marked.Renderer()

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
  (e: 'feedback', message: ChatMessage, rating: number): void
}>()

const containerRef = ref<HTMLElement>()

const quickQuestions = [
  '如何使用知识库？',
  '支持哪些文档格式？',
  '如何提高检索准确率？',
]

const lastAssistantMessage = computed(() => {
  const assistantMessages = props.messages.filter((m) => m.role === 'assistant')
  return assistantMessages[assistantMessages.length - 1]
})

function renderedContent(content: string): string {
  if (!content) return ''
  return marked.parse(content) as string
}

function handleQuickQuestion(question: string): void {
  emit('quickQuestion', question)
}

async function copyContent(content: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(content)
    message.success('已复制到剪贴板')
  } catch {
    message.error('复制失败')
  }
}

function formatTime(time: string): string {
  const date = new Date(time)
  const now = new Date()
  const diff = now.getTime() - date.getTime()

  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (date.toDateString() === now.toDateString()) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  const yesterday = new Date(now)
  yesterday.setDate(yesterday.getDate() - 1)
  if (date.toDateString() === yesterday.toDateString()) {
    return `昨天 ${date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}`
  }
  return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

function handleSourceClick(source: Source): void {
  emit('sourceClick', source)
}

async function handleLike(msg: ChatMessage): Promise<void> {
  if (!msg.historyId) return

  // 如果已经点赞，则取消
  const newRating = msg.userRating === 1 ? undefined : 1

  try {
    if (newRating !== undefined) {
      await createFeedback({
        chatHistoryId: msg.historyId,
        rating: newRating,
      })
    }
    msg.userRating = newRating
    emit('feedback', msg, newRating ?? 1)
  } catch (error) {
    message.error('评价失败')
  }
}

async function handleDislike(msg: ChatMessage): Promise<void> {
  if (!msg.historyId) return

  // 如果已经点踩，则取消
  const newRating = msg.userRating === 0 ? undefined : 0

  try {
    if (newRating !== undefined) {
      await createFeedback({
        chatHistoryId: msg.historyId,
        rating: newRating,
      })
    }
    msg.userRating = newRating
    emit('feedback', msg, newRating ?? 0)
  } catch (error) {
    message.error('评价失败')
  }
}

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

watch(
  () => props.messages.length,
  () => {
    scrollToBottom()
  }
)

watch(
  () => props.isGenerating,
  (generating) => {
    if (generating) {
      const interval = setInterval(() => {
        scrollToBottom(false)
        if (!props.isGenerating) {
          clearInterval(interval)
        }
      }, 100)
    }
  }
)

onMounted(() => {
  scrollToBottom(false)
})

defineExpose({
  scrollToBottom,
})
</script>

<style scoped lang="scss">
.message-list {
  height: 100%;
  overflow-y: auto;
  padding: 20px;

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
      }
    }
  }

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
        background: linear-gradient(135deg, var(--primary-color) 0%, #5a9cf8 100%);
        border-radius: 50%;
      }

      .assistant-avatar {
        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
        border-radius: 50%;
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
          padding: 14px 18px;
          border-radius: 18px;
          line-height: 1.7;
          word-break: break-word;
          box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);

          &.user-text {
            background: linear-gradient(135deg, var(--primary-color) 0%, var(--primary-color-hover, #5a9cf8) 100%);
            color: #fff;
            border-bottom-right-radius: 6px;
          }

          &.assistant-text {
            background-color: #f7f8fa;
            color: var(--text-primary);
            border-bottom-left-radius: 6px;
            border: 1px solid rgba(0, 0, 0, 0.05);

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

            :deep(.code-block) {
              margin: 12px 0;
              border-radius: 12px;
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

      .message-actions {
        display: flex;
        gap: 4px;
        margin-top: 8px;
        opacity: 0;
        transition: opacity var(--transition-duration);

        .action-active {
          color: var(--primary-color);
        }
      }
    }
  }

  .message-item:hover .message-actions {
    opacity: 1;
  }

  .loading-indicator {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    padding: 20px;
    color: var(--text-secondary);
  }

  .message-router-info {
    margin-top: 8px;
    padding: 10px 12px;
    background-color: var(--bg-page);
    border-radius: 8px;
    border: 1px solid var(--border-lighter);
    font-size: 13px;

    .router-header {
      display: flex;
      align-items: center;
      gap: 6px;
      font-weight: 500;
      color: var(--primary-color);
      margin-bottom: 6px;
    }

    .router-detail {
      color: var(--text-secondary);

      .router-item {
        margin-top: 4px;
        display: flex;
        align-items: flex-start;
        gap: 6px;

        .label {
          color: var(--text-secondary);
          font-weight: 500;
          flex-shrink: 0;
        }

        .candidates {
          color: var(--text-secondary);
        }
      }
    }
  }
}
</style>
