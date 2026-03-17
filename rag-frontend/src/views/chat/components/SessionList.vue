<template>
  <div class="session-list">
    <div class="new-session-btn" @click="handleNewSession">
      <PlusOutlined />
      <span>新建对话</span>
    </div>

    <div ref="listRef" class="list-container" @scroll="handleScroll">
      <a-empty v-if="sessions.length === 0 && !loading" :image="simpleImage">
        <template #description>
          <span>暂无历史对话</span>
        </template>
      </a-empty>

      <div
        v-for="session in sessions"
        :key="session.id"
        :class="['session-item', { active: currentSessionId === session.id }]"
        @click="handleSelect(session.id)"
      >
        <div class="session-icon">
          <MessageFilled />
        </div>

        <div class="session-info">
          <div class="session-title" :title="session.title">
            {{ session.title || '新对话' }}
          </div>
          <div class="session-time">{{ formatTime(session.updateTime) }}</div>
        </div>

        <div class="session-actions">
          <a-popconfirm
            title="确定要删除这个对话吗？"
            ok-text="确定"
            cancel-text="取消"
            @confirm="handleDelete(session.id)"
          >
            <a-button type="text" danger size="small" class="delete-btn">
              <template #icon>
                <DeleteOutlined />
              </template>
            </a-button>
          </a-popconfirm>
        </div>
      </div>

      <div v-if="hasMore" class="load-more">
        <a-button v-if="!loading" type="link" @click="handleLoadMore">加载更多</a-button>
        <LoadingOutlined v-else class="loading-icon" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Empty } from 'ant-design-vue'
import { message } from 'ant-design-vue'
import {
  PlusOutlined,
  DeleteOutlined,
  MessageFilled,
  MessageOutlined,
  LoadingOutlined,
} from '@ant-design/icons-vue'
import type { ChatSession } from '@/api/chat'

const simpleImage = Empty.PRESENTED_IMAGE_SIMPLE

const props = defineProps<{
  sessions: ChatSession[]
  currentSessionId: string | null
  loading?: boolean
  hasMore?: boolean
}>()

const emit = defineEmits<{
  (e: 'select', sessionId: string): void
  (e: 'new'): void
  (e: 'delete', sessionId: string): void
  (e: 'loadMore'): void
}>()

const listRef = ref<HTMLElement>()

function formatTime(time: string): string {
  const date = new Date(time)
  const now = new Date()
  const diff = now.getTime() - date.getTime()

  if (diff < 86400000) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  if (diff < 604800000) {
    const days = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
    return days[date.getDay()]
  }
  return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}

function handleSelect(sessionId: string): void {
  emit('select', sessionId)
}

function handleNewSession(): void {
  emit('new')
}

function handleDelete(sessionId: string): void {
  emit('delete', sessionId)
}

function handleLoadMore(): void {
  emit('loadMore')
}

function handleScroll(): void {
  if (!listRef.value || !props.hasMore || props.loading) return

  const { scrollTop, scrollHeight, clientHeight } = listRef.value
  if (scrollHeight - scrollTop - clientHeight < 100) {
    handleLoadMore()
  }
}

function scrollToTop(): void {
  if (listRef.value) {
    listRef.value.scrollTop = 0
  }
}

defineExpose({
  scrollToTop,
})
</script>

<style scoped lang="scss">
.session-list {
  height: 100%;
  display: flex;
  flex-direction: column;
  background-color: var(--bg-color);

  .new-session-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    margin: 12px;
    padding: 12px;
    background-color: var(--primary-light-9);
    color: var(--primary-color);
    border-radius: var(--border-radius-base);
    cursor: pointer;
    font-weight: 500;
    transition: all var(--transition-duration);

    &:hover {
      background-color: var(--primary-light-8);
    }

    &:active {
      transform: scale(0.98);
    }
  }

  .list-container {
    flex: 1;
    overflow-y: auto;
    padding: 0 8px 8px;

    :deep(.ant-empty) {
      padding: 40px 20px;

      .ant-empty-description {
        color: var(--text-placeholder);
      }
    }

    .session-item {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 12px;
      margin-bottom: 4px;
      border-radius: var(--border-radius-base);
      cursor: pointer;
      transition: all var(--transition-duration);

      &:hover {
        background-color: var(--bg-page);

        .session-actions {
          opacity: 1;
        }
      }

      &.active {
        background-color: var(--primary-light-9);

        .session-icon {
          color: var(--primary-color);
        }

        .session-title {
          color: var(--primary-color);
          font-weight: 500;
        }
      }

      .session-icon {
        flex-shrink: 0;
        color: var(--text-secondary);
        font-size: 18px;
      }

      .session-info {
        flex: 1;
        min-width: 0;

        .session-title {
          font-size: 14px;
          color: var(--text-primary);
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .session-time {
          font-size: 12px;
          color: var(--text-placeholder);
          margin-top: 2px;
        }
      }

      .session-actions {
        opacity: 0;
        transition: opacity var(--transition-duration);

        .delete-btn {
          padding: 4px 8px;
        }
      }
    }

    .load-more {
      display: flex;
      justify-content: center;
      padding: 12px;

      .loading-icon {
        animation: spin 1s linear infinite;
        color: var(--primary-color);
        font-size: 20px;
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
}
</style>
