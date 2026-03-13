<template>
  <div class="empty-state">
    <div class="empty-icon">
      <el-icon :size="iconSize">
        <component :is="icon" />
      </el-icon>
    </div>
    <div class="empty-text">{{ text }}</div>
    <div v-if="description" class="empty-description">{{ description }}</div>
    <div v-if="$slots.action" class="empty-action">
      <slot name="action" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { FolderOpened, Search, Warning, Document, User } from '@element-plus/icons-vue'

/**
 * 空状态类型
 */
type EmptyType = 'default' | 'search' | 'error' | 'document' | 'user'

interface Props {
  type?: EmptyType
  text?: string
  description?: string
  iconSize?: number
}

const props = withDefaults(defineProps<Props>(), {
  type: 'default',
  text: '',
  description: '',
  iconSize: 64,
})

// 根据类型选择图标
const icon = computed(() => {
  const iconMap: Record<EmptyType, typeof FolderOpened> = {
    default: FolderOpened,
    search: Search,
    error: Warning,
    document: Document,
    user: User,
  }
  return iconMap[props.type]
})

// 根据类型选择默认文本
const text = computed(() => {
  if (props.text) return props.text
  const textMap: Record<EmptyType, string> = {
    default: '暂无数据',
    search: '未找到相关内容',
    error: '加载失败',
    document: '暂无文档',
    user: '暂无用户',
  }
  return textMap[props.type]
})
</script>

<style scoped lang="scss">
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;

  .empty-icon {
    color: var(--el-text-color-placeholder);
    margin-bottom: 16px;
  }

  .empty-text {
    font-size: 14px;
    color: var(--el-text-color-secondary);
    margin-bottom: 8px;
  }

  .empty-description {
    font-size: 12px;
    color: var(--el-text-color-placeholder);
    margin-bottom: 16px;
    max-width: 300px;
    text-align: center;
  }

  .empty-action {
    margin-top: 8px;
  }
}
</style>
