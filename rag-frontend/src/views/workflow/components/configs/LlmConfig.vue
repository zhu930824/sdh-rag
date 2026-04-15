<template>
  <div class="config-section">
    <div class="section-title">LLM 配置</div>

    <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 18 }">
      <!-- 模型选择 -->
      <a-form-item label="模型">
        <a-select
          v-model:value="model"
          placeholder="选择模型"
          size="small"
          :loading="modelLoading"
          show-search
          :filter-option="filterModelOption"
        >
          <a-select-option
            v-for="modelItem in chatModels"
            :key="modelItem.id"
            :value="modelItem.modelId"
          >
            {{ modelItem.name }}
            <span class="model-id-hint">({{ modelItem.modelId }})</span>
          </a-select-option>
        </a-select>
      </a-form-item>

      <!-- 系统提示 -->
      <a-form-item label="系统提示">
        <a-textarea
          v-model:value="systemPrompt"
          placeholder="请输入系统提示词"
          :rows="4"
          size="small"
        />
      </a-form-item>

      <!-- 提示词模板 -->
      <a-form-item label="提示词模板">
        <a-textarea
          v-model:value="prompt"
          placeholder="使用 {{参数名}} 引用输入参数"
          :rows="6"
          size="small"
          style="font-family: monospace"
        />
        <div class="field-tip">💡 使用 {{ 参数名 }} 引用输入参数</div>
      </a-form-item>

      <!-- 温度 -->
      <a-form-item label="温度">
        <a-slider v-model:value="temperature" :min="0" :max="2" :step="0.1" />
        <span class="slider-value">{{ temperature }}</span>
      </a-form-item>

      <!-- 最大Token -->
      <a-form-item label="最大Token">
        <a-input-number
          v-model:value="maxTokens"
          :min="100"
          :max="32000"
          :step="100"
          size="small"
          style="width: 100%"
        />
      </a-form-item>
    </a-form>

    <!-- 输入参数 -->
    <div class="param-section">
      <div class="param-header">
        <span>输入参数</span>
        <a-button type="link" size="small" @click="addInputParam">
          <template #icon><PlusOutlined /></template>
          添加
        </a-button>
      </div>

      <div v-for="(param, index) in inputParams" :key="index" class="param-item">
        <a-input
          v-model:value="param.name"
          placeholder="参数名"
          size="small"
          style="width: 80px"
        />
        <a-select v-model:value="param.type" size="small" style="width: 80px">
          <a-select-option value="input">输入</a-select-option>
          <a-select-option value="reference">引用</a-select-option>
        </a-select>
        <div class="param-value">
          <a-input
            v-if="param.type === 'input'"
            v-model:value="param.value"
            placeholder="输入值"
            size="small"
          />
          <a-select
            v-else
            v-model:value="param.referenceNode"
            placeholder="选择引用"
            size="small"
            allow-clear
          >
            <a-select-option
              v-for="ref in referenceableParams"
              :key="ref.value"
              :value="ref.value"
            >
              {{ ref.label }}
            </a-select-option>
          </a-select>
        </div>
        <a-button type="text" danger size="small" @click="removeInputParam(index)">
          <template #icon><DeleteOutlined /></template>
        </a-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { useWorkflowStore, type WorkflowNodeData, type InputParam } from '@/stores/workflow'
import { getActiveChatModels, type ModelConfigRequest } from '@/api/model'
import type { Node } from '@vue-flow/core'

const props = defineProps<{
  node: Node<WorkflowNodeData>
}>()

const emit = defineEmits<{
  (e: 'update', data: Partial<WorkflowNodeData>): void
}>()

const workflowStore = useWorkflowStore()

// 模型列表
const chatModels = ref<{ id: number; name: string; modelId: string }[]>([])
const modelLoading = ref(false)

// 加载可用模型列表
async function loadChatModels() {
  modelLoading.value = true
  try {
    const res = await getActiveChatModels()
    if (res.code === 200 && res.data) {
      chatModels.value = res.data.map(m => ({
        id: m.id,
        name: m.name,
        modelId: m.modelId,
      }))
    }
  } catch (error) {
    console.error('加载模型列表失败', error)
  } finally {
    modelLoading.value = false
  }
}

// 模型筛选
function filterModelOption(input: string, option: any) {
  const modelItem = chatModels.value.find(m => m.modelId === option.value)
  if (!modelItem) return false
  const searchStr = input.toLowerCase()
  return modelItem.name.toLowerCase().includes(searchStr) ||
         modelItem.modelId.toLowerCase().includes(searchStr)
}

const model = computed({
  get: () => props.node.data.model || '',
  set: (val) => emit('update', { model: val }),
})

const systemPrompt = computed({
  get: () => props.node.data.systemPrompt || '',
  set: (val) => emit('update', { systemPrompt: val }),
})

const prompt = computed({
  get: () => props.node.data.prompt || '',
  set: (val) => emit('update', { prompt: val }),
})

const temperature = computed({
  get: () => props.node.data.temperature ?? 0.7,
  set: (val) => emit('update', { temperature: val }),
})

const maxTokens = computed({
  get: () => props.node.data.maxTokens || 2000,
  set: (val) => emit('update', { maxTokens: val }),
})

const inputParams = computed({
  get: () => props.node.data.inputParams || [],
  set: (val) => emit('update', { inputParams: val }),
})

const referenceableParams = computed(() =>
  workflowStore.getReferenceableParams(props.node.id)
)

function addInputParam() {
  const newParam: InputParam = { name: '', type: 'input', value: '' }
  emit('update', { inputParams: [...inputParams.value, newParam] })
}

function removeInputParam(index: number) {
  const newParams = [...inputParams.value]
  newParams.splice(index, 1)
  emit('update', { inputParams: newParams })
}

onMounted(() => {
  loadChatModels()
})
</script>

<style scoped lang="scss">
.config-section {
  .section-title {
    font-size: 13px;
    font-weight: 500;
    color: #262626;
    margin-bottom: 12px;
    padding-bottom: 8px;
    border-bottom: 1px solid #f0f0f0;
  }
}

.model-id-hint {
  font-size: 12px;
  color: #8c8c8c;
  margin-left: 4px;
}

.field-tip {
  font-size: 12px;
  color: #8c8c8c;
  margin-top: 4px;
}

.slider-value {
  font-size: 12px;
  color: #8c8c8c;
  margin-left: 8px;
}

.param-section {
  margin-top: 16px;
  margin-bottom: 16px;
}

.param-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-size: 13px;
  color: #595959;
}

.param-item {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;

  .param-value {
    flex: 1;
    min-width: 0;
  }
}
</style>
