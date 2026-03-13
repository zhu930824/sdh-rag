<template>
  <div class="session-list">
    <!-- 新建会话按钮 -->
    <div class="new-session-btn" @click="handleNewSession">
      <el-icon><Plus /></el-icon>
      <span>新建对话</span>
    </div>

    <!-- 会话列表 -->
    <div ref="listRef" class="list-container" @scroll="handleScroll">
      <div v-if="sessions.length === 0 && !loading" class="empty-list">
        <el-icon :size="32"><ChatLineSquare /></el-icon>
        <p>暂无历史对话</p>
      </div>

      <div
        v-for="session in sessions"
        :key="session.id"
        :class="['session-item', { active: currentSessionId === session.id }]"
        @click="handleSelect(session.id)"
      >
        <div class="session-icon">
          <el-icon><ChatDotSquare /></el-icon>
        </div>

        <div class="session-info">
          <div class="session-title" :title="session.title">
            {{ session.title || '新对话' }}
          </div>
          <div class="session-time">{{ formatTime(session.updateTime) }}</div>
        </div>

        <div class="session-actions">
          <el-button
            type="danger"
            :icon="Delete"
            size="small"
            circle
            text
            @click.stop="handleDelete(session.id)"
          />
        </div>
      </div>

      <!-- 加载更多 -->
      <div v-if="hasMore" class="load-more">
        <el-button v-if="!loading" text @click="handleLoadMore">加载更多</el-button>
        <el-icon v-else class="loading-icon"><Loading /></el-icon>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Plus, Delete, ChatDotSquare, ChatLineSquare, Loading } from '@element-plus/icons-vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import type { ChatSession } from '@/api/chat'

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

// 格式化时间
function formatTime(time: string): string {
  const date = new Date(time)
  const now = new Date()
  const diff = now.getTime() - date.getTime()

  // 一天内
  if (diff < 86400000) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  // 一周内
  if (diff < 604800000) {
    const days = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
    return days[date.getDay()]
  }
  // 其他
  return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}

// 选择会话
function handleSelect(sessionId: string): void {
  emit('select', sessionId)
}

// 新建会话
function handleNewSession(): void {
  emit('new')
}

// 删除会话
async function handleDelete(sessionId: string): Promise<void> {
  try {
    await ElMessageBox.confirm('确定要删除这个对话吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    emit('delete', sessionId)
  } catch {
    // 用户取消
  }
}

// 加载更多
function handleLoadMore(): void {
  emit('loadMore')
}

// 滚动加载
function handleScroll(): void {
  if (!listRef.value || !props.hasMore || props.loading) return

  const { scrollTop, scrollHeight, clientHeight } = listRef.value
  // 距离底部100px时加载更多
  if (scrollHeight - scrollTop - clientHeight < 100) {
    handleLoadMore()
  }
}

// 滚动到顶部
function scrollToTop(): void {
  if (listRef.value) {
    listRef.value.scrollTop = 0
  }
}

// 暴露方法
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

    .empty-list {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 40px 20px;
      color: var(--text-placeholder);

      p {
        margin-top: 8px;
        font-size: 13px;
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
