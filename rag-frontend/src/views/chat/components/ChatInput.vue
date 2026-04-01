<template>
  <div class="chat-input">
    <div class="input-wrapper">
      <div class="knowledge-selector">
        <a-select
          v-model:value="selectedKnowledgeId"
          placeholder="选择知识库（可选）"
          allow-clear
          style="width: 200px"
          :loading="knowledgeLoading"
          @change="handleKnowledgeChange"
        >
          <a-select-option :value="null">全部知识库</a-select-option>
          <a-select-option
            v-for="kb in knowledgeList"
            :key="kb.id"
            :value="kb.id"
          >
            {{ kb.name }}
          </a-select-option>
        </a-select>
        <span class="knowledge-hint">
          {{ selectedKnowledgeId ? '将从选定知识库检索' : '将从所有知识库检索' }}
        </span>
      </div>

      <a-textarea
        ref="textareaRef"
        v-model:value="inputText"
        class="input-textarea"
        :placeholder="placeholder"
        :disabled="disabled"
        :auto-size="{ minRows: 2, maxRows: 6 }"
        @input="adjustHeight"
        @keydown="handleKeydown"
      />

      <div class="input-actions">
        <div class="input-tips">
          <InfoCircleOutlined />
          <span>Enter 发送，Shift + Enter 换行</span>
        </div>

        <a-space class="input-buttons">
          <a-button v-if="isGenerating" danger @click="handleStop">
            <template #icon>
              <PauseCircleOutlined />
            </template>
            停止生成
          </a-button>

          <a-button
            v-else
            type="primary"
            :disabled="!canSend"
            :loading="sending"
            @click="handleSend"
          >
            <template #icon>
              <SendOutlined />
            </template>
            发送
          </a-button>
        </a-space>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted } from 'vue'
import {
  SendOutlined,
  PauseCircleOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons-vue'
import { getCategories } from '@/api/knowledge'
import type { DocumentCategory } from '@/api/knowledge'

const props = withDefaults(
  defineProps<{
    disabled?: boolean
    placeholder?: string
    sending?: boolean
    isGenerating?: boolean
    knowledgeId?: number | null
  }>(),
  {
    disabled: false,
    placeholder: '输入您的问题...',
    sending: false,
    isGenerating: false,
    knowledgeId: null,
  }
)

const emit = defineEmits<{
  (e: 'send', message: string): void
  (e: 'stop'): void
  (e: 'knowledgeChange', knowledgeId: number | null): void
}>()

const textareaRef = ref()
const inputText = ref('')
const selectedKnowledgeId = ref<number | null>(props.knowledgeId)
const knowledgeList = ref<DocumentCategory[]>([])
const knowledgeLoading = ref(false)

const canSend = computed(() => {
  return inputText.value.trim().length > 0 && !props.disabled && !props.sending
})

async function fetchKnowledgeList(): Promise<void> {
  knowledgeLoading.value = true
  try {
    const res = await getCategories()
    knowledgeList.value = res.data || []
  } catch (error) {
    console.error('获取知识库列表失败:', error)
  } finally {
    knowledgeLoading.value = false
  }
}

function handleKnowledgeChange(value: number | null): void {
  emit('knowledgeChange', value)
}

function adjustHeight(): void {
  nextTick(() => {
    if (textareaRef.value?.resizableTextArea?.textArea) {
      const textarea = textareaRef.value.resizableTextArea.textArea
      textarea.style.height = 'auto'
      const newHeight = Math.min(Math.max(textarea.scrollHeight, 60), 200)
      textarea.style.height = `${newHeight}px`
    }
  })
}

function handleKeydown(event: KeyboardEvent): void {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    handleSend()
  }
}

function handleSend(): void {
  const message = inputText.value.trim()
  if (!message || props.disabled || props.sending) return

  emit('send', message)
  inputText.value = ''

  nextTick(() => {
    if (textareaRef.value?.resizableTextArea?.textArea) {
      textareaRef.value.resizableTextArea.textArea.style.height = '60px'
    }
  })
}

function handleStop(): void {
  emit('stop')
}

function focus(): void {
  textareaRef.value?.focus()
}

onMounted(() => {
  fetchKnowledgeList()
})

defineExpose({
  focus,
})
</script>

<style scoped lang="scss">
.chat-input {
  padding: 16px 20px;
  background-color: var(--bg-color);
  border-top: 1px solid var(--border-lighter);

  .input-wrapper {
    display: flex;
    flex-direction: column;
    background-color: var(--bg-page);
    border-radius: var(--border-radius-large);
    border: 1px solid var(--border-light);
    transition: border-color var(--transition-duration);

    &:focus-within {
      border-color: var(--primary-color);
    }

    .knowledge-selector {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 8px 12px;
      border-bottom: 1px solid var(--border-lighter);

      .knowledge-hint {
        font-size: 12px;
        color: var(--text-placeholder);
      }
    }

    .input-textarea {
      :deep(.ant-input) {
        width: 100%;
        min-height: 60px;
        max-height: 200px;
        padding: 12px 16px;
        border: none;
        background: transparent;
        font-size: 14px;
        line-height: 1.6;
        color: var(--text-primary);
        resize: none;
        outline: none;
        box-shadow: none;

        &::placeholder {
          color: var(--text-placeholder);
        }

        &:disabled {
          cursor: not-allowed;
          opacity: 0.6;
        }
      }
    }

    .input-actions {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 8px 12px;
      border-top: 1px solid var(--border-lighter);

      .input-tips {
        display: flex;
        align-items: center;
        gap: 4px;
        font-size: 12px;
        color: var(--text-placeholder);
      }

      .input-buttons {
        display: flex;
        gap: 8px;
      }
    }
  }
}

@media (max-width: 768px) {
  .chat-input {
    .input-wrapper {
      .knowledge-selector {
        flex-direction: column;
        align-items: flex-start;
        gap: 8px;

        .ant-select {
          width: 100% !important;
        }
      }
    }
  }
}
</style>
