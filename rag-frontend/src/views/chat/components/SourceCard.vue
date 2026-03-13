<template>
  <div class="source-card" @click="handleClick">
    <div class="source-header">
      <el-icon class="document-icon"><Document /></el-icon>
      <span class="document-title" :title="source.documentTitle">{{ source.documentTitle }}</span>
      <el-tag size="small" type="info" class="score-tag">
        相关度: {{ (source.score * 100).toFixed(1) }}%
      </el-tag>
    </div>
    <div class="source-content">
      {{ truncatedContent }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Document } from '@element-plus/icons-vue'
import type { Source } from '@/api/chat'

const props = defineProps<{
  source: Source
}>()

const emit = defineEmits<{
  (e: 'click', source: Source): void
}>()

// 截断内容显示
const truncatedContent = computed(() => {
  const content = props.source.content
  return content.length > 150 ? content.slice(0, 150) + '...' : content
})

// 点击跳转到文档详情
function handleClick(): void {
  emit('click', props.source)
}
</script>

<style scoped lang="scss">
.source-card {
  padding: 12px;
  background-color: var(--bg-page);
  border-radius: var(--border-radius-base);
  border: 1px solid var(--border-lighter);
  cursor: pointer;
  transition: all var(--transition-duration);

  &:hover {
    border-color: var(--primary-light-5);
    box-shadow: var(--box-shadow-light);
  }

  .source-header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;

    .document-icon {
      color: var(--primary-color);
      font-size: 16px;
      flex-shrink: 0;
    }

    .document-title {
      flex: 1;
      font-weight: 500;
      color: var(--text-primary);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .score-tag {
      flex-shrink: 0;
      font-size: 11px;
    }
  }

  .source-content {
    font-size: 13px;
    color: var(--text-secondary);
    line-height: 1.6;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
}
</style>
