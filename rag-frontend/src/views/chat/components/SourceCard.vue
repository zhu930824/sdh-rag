<template>
  <div class="source-card" @click="handleClick">
    <div class="source-header">
      <FileTextOutlined class="document-icon" />
      <a-tooltip :title="source.documentTitle">
        <span class="document-title">{{ source.documentTitle }}</span>
      </a-tooltip>
      <a-tag color="blue" class="score-tag">
        相关度: {{ (source.score * 100).toFixed(1) }}%
      </a-tag>
    </div>
    <div class="source-content">
      {{ truncatedContent }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { FileTextOutlined } from '@ant-design/icons-vue'
import type { Source } from '@/api/chat'

const props = defineProps<{
  source: Source
}>()

const emit = defineEmits<{
  (e: 'click', source: Source): void
}>()

const truncatedContent = computed(() => {
  const content = props.source.content
  return content.length > 150 ? content.slice(0, 150) + '...' : content
})

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
