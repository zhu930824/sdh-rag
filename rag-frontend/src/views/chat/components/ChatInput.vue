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

      <!-- 筛选面板 -->
      <div v-if="showFilter" class="filter-panel">
        <a-space wrap>
          <a-select
            v-model:value="metadataFilter.department"
            placeholder="部门"
            allow-clear
            size="small"
            style="width: 120px"
          >
            <a-select-option v-for="d in departments" :key="d" :value="d">
              {{ d }}
            </a-select-option>
          </a-select>

          <a-select
            v-model:value="metadataFilter.docType"
            placeholder="文档类型"
            allow-clear
            size="small"
            style="width: 120px"
          >
            <a-select-option
              v-for="t in DOC_TYPES" :key="t.value" :value="t.value"
            >
              {{ t.label }}
            </a-select-option>
          </a-select>

          <a-date-picker
            v-model:value="metadataFilter.dateFrom"
            placeholder="开始日期"
            size="small"
            style="width: 130px"
          />

          <a-date-picker
            v-model:value="metadataFilter.dateTo"
            placeholder="结束日期"
            size="small"
            style="width: 130px"
          />
        </a-space>
      </div>

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
            <a-select-option :value="null">自动选择(推荐)</a-select-option>
            <a-select-option
              v-for="kb in knowledgeList"
              :key="kb.id"
              :value="kb.id"
            >
              {{ kb.name }}
            </a-select-option>
          </a-select>
        </div>

        <!-- 高级筛选按钮 -->
        <div class="filter-toggle">
          <a-button type="link" size="small" @click="showFilter = !showFilter">
            <FilterOutlined />
            {{ showFilter ? '收起筛选' : '高级筛选' }}
          </a-button>
        </div>

        <div class="toolbar-right">
          <!-- Agent 模式开关 -->
          <a-tooltip title="Agent 模式：AI可调用受控业务工具完成任务；高风险操作需人工审批">
            <div class="agent-toggle" :class="{ active: localAgentEnabled }">
              <RobotOutlined :style="{ color: localAgentEnabled ? '#1677ff' : '#999' }" />
              <a-switch
                v-model:checked="localAgentEnabled"
                size="small"
                :disabled="sending || isGenerating"
                @change="handleAgentChange"
              />
            </div>
          </a-tooltip>

          <!-- 记忆增强开关 -->
          <a-tooltip title="记忆增强：AI会记住您的偏好和历史对话">
            <div class="memory-toggle">
              <BulbOutlined :style="{ color: localMemoryEnabled ? '#faad14' : '#999' }" />
              <a-switch
                v-model:checked="localMemoryEnabled"
                size="small"
                @change="handleMemoryChange"
              />
            </div>
          </a-tooltip>

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
  BulbOutlined,
  FilterOutlined,
  RobotOutlined,
} from '@ant-design/icons-vue'
import { getKnowledgeBaseList, type KnowledgeBase } from '@/api/knowledgeBase'
import { getActiveChatModels } from '@/api/model'
import type { ModelConfig } from '@/types'
import { DOC_TYPES, type MetadataFilter } from '@/types/search'

const props = withDefaults(
  defineProps<{
    disabled?: boolean
    placeholder?: string
    sending?: boolean
    isGenerating?: boolean
    knowledgeId?: number | null
    modelId?: number | null
    memoryEnabled?: boolean
    agentEnabled?: boolean
  }>(),
  {
    disabled: false,
    placeholder: '输入您的问题...',
    sending: false,
    isGenerating: false,
    knowledgeId: null,
    modelId: null,
    memoryEnabled: true,
    agentEnabled: false,
  }
)

export interface SendPayload {
  message: string
  metadataFilter?: MetadataFilter
}

const emit = defineEmits<{
  (e: 'send', payload: SendPayload): void
  (e: 'stop'): void
  (e: 'knowledgeChange', knowledgeId: number | null): void
  (e: 'modelChange', modelId: number | null): void
  (e: 'memoryChange', enabled: boolean): void
  (e: 'agentChange', enabled: boolean): void
}>()

const textareaRef = ref()
const inputText = ref('')
const selectedKnowledgeId = ref<number | null>(props.knowledgeId)
const selectedModelId = ref<number | null>(props.modelId)
const localMemoryEnabled = ref(props.memoryEnabled)
const localAgentEnabled = ref(props.agentEnabled)
const knowledgeList = ref<KnowledgeBase[]>([])
const modelList = ref<ModelConfig[]>([])
const knowledgeLoading = ref(false)
const modelLoading = ref(false)
const showFilter = ref(false)
const metadataFilter = reactive<MetadataFilter>({})
const departments = ['技术部', '财务部', '人力资源部', '市场部', '运营部']

const canSend = computed(() => {
  return inputText.value.trim().length > 0 && !props.disabled && !props.sending
})

async function fetchKnowledgeList(): Promise<void> {
  knowledgeLoading.value = true
  try {
    const res = await getKnowledgeBaseList({ page: 1, pageSize: 100, status: 1 })
    knowledgeList.value = res.data?.records || []
  } catch (error) {
    console.error('获取知识库列表失败:', error)
  } finally {
    knowledgeLoading.value = false
  }
}

async function fetchModelList(): Promise<void> {
  modelLoading.value = true
  try {
    const res = await getActiveChatModels()
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

function handleMemoryChange(value: boolean): void {
  emit('memoryChange', value)
}

function handleAgentChange(value: boolean): void {
  emit('agentChange', value)
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

  const hasActiveFilter = metadataFilter.department || metadataFilter.docType || metadataFilter.dateFrom || metadataFilter.dateTo
  const payload: SendPayload = {
    message,
    ...(hasActiveFilter ? { metadataFilter: { ...metadataFilter } } : {}),
  }
  emit('send', payload)
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

        .agent-toggle,
        .memory-toggle {
          display: flex;
          align-items: center;
          gap: 4px;
          padding: 0 4px;

          :deep(.anticon) {
            font-size: 14px;
          }
        }

        .agent-toggle.active {
          background: rgba(22, 119, 255, 0.08);
          border-radius: 6px;
        }

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

      .filter-toggle {
        :deep(.ant-btn) {
          padding: 0 4px;
          font-size: 12px;
        }
      }
    }

    // 筛选面板
    .filter-panel {
      padding: 8px 12px;
      background: #fafafa;
      border-bottom: 1px solid var(--border-lighter);
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
