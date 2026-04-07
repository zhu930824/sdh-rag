<template>
  <div class="chat-input">
    <div class="input-wrapper">
      <!-- 中间：输入框 -->
      <a-textarea
        ref="textareaRef"
        v-model:value="inputText"
        class="input-textarea"
        :placeholder="placeholder"
        :disabled="disabled"
        :auto-size="{ minRows: 3, maxRows: 8 }"
        @input="adjustHeight"
        @keydown="handleKeydown"
      />

      <!-- 底部：工具栏和发送按钮 -->
      <div class="input-toolbar">
        <div class="toolbar-left">
          <span class="knowledge-label">知识库</span>
          <a-select
            v-model:value="selectedKnowledgeId"
            placeholder="选择知识库"
            allow-clear
            size="small"
            style="width: 180px"
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
        </div>

        <div class="toolbar-right">
          <!-- 模型选择 -->
          <a-select
            v-model:value="selectedModelId"
            placeholder="选择模型"
            size="small"
            class="model-select"
            :loading="modelLoading"
            @change="handleModelChange"
          >
            <a-select-option
              v-for="model in modelList"
              :key="model.id"
              :value="model.id"
            >
              {{ model.name }}
            </a-select-option>
          </a-select>

          <!-- 停止/发送按钮 -->
          <a-button
            v-if="isGenerating"
            type="primary"
            danger
            size="small"
            shape="circle"
            class="send-btn"
            @click="handleStop"
          >
            <template #icon>
              <PauseOutlined />
            </template>
          </a-button>

          <a-button
            v-else
            type="primary"
            size="small"
            shape="circle"
            class="send-btn"
            :disabled="!canSend"
            :loading="sending"
            @click="handleSend"
          >
            <template #icon>
              <ArrowUpOutlined />
            </template>
          </a-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted } from 'vue'
import {
  ArrowUpOutlined,
  PauseOutlined,
} from '@ant-design/icons-vue'
import { getCategoryList } from '@/api/document'
import { getActiveModels } from '@/api/model'
import type { DocumentCategory } from '@/api/document'
import type { ModelConfig } from '@/types'

const props = withDefaults(
  defineProps<{
    disabled?: boolean
    placeholder?: string
    sending?: boolean
    isGenerating?: boolean
    knowledgeId?: number | null
    modelId?: number | null
  }>(),
  {
    disabled: false,
    placeholder: '输入您的问题...',
    sending: false,
    isGenerating: false,
    knowledgeId: null,
    modelId: null,
  }
)

const emit = defineEmits<{
  (e: 'send', message: string): void
  (e: 'stop'): void
  (e: 'knowledgeChange', knowledgeId: number | null): void
  (e: 'modelChange', modelId: number | null): void
}>()

const textareaRef = ref()
const inputText = ref('')
const selectedKnowledgeId = ref<number | null>(props.knowledgeId)
const selectedModelId = ref<number | null>(props.modelId)
const knowledgeList = ref<DocumentCategory[]>([])
const modelList = ref<ModelConfig[]>([])
const knowledgeLoading = ref(false)
const modelLoading = ref(false)

const canSend = computed(() => {
  return inputText.value.trim().length > 0 && !props.disabled && !props.sending
})

async function fetchKnowledgeList(): Promise<void> {
  knowledgeLoading.value = true
  try {
    const res = await getCategoryList()
    knowledgeList.value = res.data || []
  } catch (error) {
    console.error('获取知识库列表失败:', error)
  } finally {
    knowledgeLoading.value = false
  }
}

async function fetchModelList(): Promise<void> {
  modelLoading.value = true
  try {
    const res = await getActiveModels()
    modelList.value = res.data || []
    // 默认选择第一个模型
    if (modelList.value.length > 0 && !selectedModelId.value) {
      selectedModelId.value = modelList.value[0].id
      emit('modelChange', selectedModelId.value)
    }
  } catch (error) {
    console.error('获取模型列表失败:', error)
  } finally {
    modelLoading.value = false
  }
}

function handleKnowledgeChange(value: number | null): void {
  emit('knowledgeChange', value)
}

function handleModelChange(value: number | null): void {
  emit('modelChange', value)
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
  fetchModelList()
})

defineExpose({
  focus,
})
</script>

<style scoped lang="scss">
.chat-input {
  padding: 16px 20px;
  border-top: 1px solid var(--border-lighter);

  .input-wrapper {
    display: flex;
    flex-direction: column;
    background-color: var(--bg-page);
    border-radius: 12px;
    border: 1px solid var(--border-light);
    transition: border-color var(--transition-duration);
    overflow: hidden;

    &:focus-within {
      border-color: var(--primary-color);
    }

    // 输入框
    .input-textarea {
      :deep(.ant-input) {
        width: 100%;
        min-height: 100px;
        max-height: 300px;
        padding: 16px;
        border: none;
        background: transparent;
        font-size: 15px;
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

    // 底部工具栏
    .input-toolbar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 6px 12px;
      background-color: var(--bg-surface-secondary);
      border-top: 1px solid var(--border-lighter);

      .toolbar-left {
        display: flex;
        align-items: center;
        gap: 8px;

        .knowledge-label {
          font-size: 12px;
          color: var(--text-secondary);
        }

        :deep(.ant-select) {
          .ant-select-selector {
            border-radius: 4px;
            background-color: var(--bg-surface);
            border: 1px solid var(--border-light);
            height: 24px;
          }

          .ant-select-selection-placeholder,
          .ant-select-selection-item {
            font-size: 12px;
            line-height: 22px;
          }
        }
      }

      .toolbar-right {
        display: flex;
        align-items: center;
        gap: 8px;

        .model-select {
          min-width: 100px;

          :deep(.ant-select-selector) {
            border-radius: 4px;
            background-color: var(--bg-surface);
            border: 1px solid var(--border-light);
            height: 24px;
          }

          :deep(.ant-select-selection-placeholder),
          :deep(.ant-select-selection-item) {
            font-size: 12px;
            line-height: 22px;
          }
        }

        .send-btn {
          width: 28px;
          height: 28px;
          padding: 0;
          display: flex;
          align-items: center;
          justify-content: center;

          :deep(.anticon) {
            font-size: 14px;
          }
        }
      }
    }
  }
}

@media (max-width: 768px) {
  .chat-input {
    padding: 12px;

    .input-wrapper {
      .input-textarea {
        :deep(.ant-input) {
          padding: 12px;
          min-height: 80px;
        }
      }

      .input-toolbar {
        padding: 6px 10px;
        flex-wrap: wrap;
        gap: 8px;

        .toolbar-left {
          width: 100%;

          .ant-select {
            flex: 1;
          }
        }

        .toolbar-right {
          width: 100%;
          justify-content: flex-end;

          .model-select {
            min-width: 80px;
          }
        }
      }
    }
  }
}
</style>
