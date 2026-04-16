<template>
  <div class="config-section">
    <div class="section-title">输出配置</div>

    <!-- 输出参数 -->
    <div class="param-section">
      <div class="param-header">
        <span>输出参数</span>
        <a-button type="link" size="small" @click="addParam">
          <template #icon><PlusOutlined /></template>
          添加
        </a-button>
      </div>

      <div v-for="(param, index) in localParams" :key="index" class="param-item">
        <a-input
          :value="param.name"
          placeholder="参数名"
          size="small"
          style="width: 80px"
          @change="(e) => updateParam(index, 'name', e.target.value)"
        />
        <a-select
          :value="param.type"
          size="small"
          style="width: 80px"
          @change="(val) => updateParam(index, 'type', val)"
        >
          <a-select-option value="input">输入</a-select-option>
          <a-select-option value="reference">引用</a-select-option>
        </a-select>
        <div class="param-value">
          <a-input
            v-if="param.type === 'input'"
            :value="param.value"
            placeholder="输入值"
            size="small"
            @change="(e) => updateParam(index, 'value', e.target.value)"
          />
          <a-select
            v-else
            :value="param.referenceNode"
            placeholder="选择引用"
            size="small"
            allow-clear
            @change="(val) => updateParam(index, 'referenceNode', val)"
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
        <a-button type="text" danger size="small" @click="removeParam(index)">
          <template #icon><DeleteOutlined /></template>
        </a-button>
      </div>
    </div>

    <!-- 响应模板 -->
    <div class="template-section">
      <div class="template-label">响应模板</div>
      <a-textarea
        :value="responseContent"
        placeholder="使用 {{参数名}} 引用输出配置中的参数"
        :rows="6"
        style="font-family: monospace"
        @change="(e) => updateResponseContent(e.target.value)"
      />
      <div class="template-tip">
        💡 使用 {{ 参数名 }} 引用上面定义的参数
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { useWorkflowStore, type WorkflowNodeData, type OutputParam } from '@/stores/workflow'
import type { Node } from '@vue-flow/core'

const props = defineProps<{
  node: Node<WorkflowNodeData>
}>()

const emit = defineEmits<{
  (e: 'update', data: Partial<WorkflowNodeData>): void
}>()

const workflowStore = useWorkflowStore()

// 使用本地 ref 存储参数，确保响应式更新
const localParams = ref<OutputParam[]>([])

// 监听 node 变化，同步本地参数
watch(
  () => props.node.data.outputParams,
  (newParams) => {
    localParams.value = newParams ? JSON.parse(JSON.stringify(newParams)) : []
  },
  { immediate: true, deep: true }
)

const responseContent = computed(() => props.node.data.responseContent || '')

// 获取可引用的参数
const referenceableParams = computed(() =>
  workflowStore.getReferenceableParams(props.node.id)
)

function addParam() {
  const newParam: OutputParam = { name: '', type: 'input', value: '', referenceNode: '' }
  localParams.value.push(newParam)
  emitUpdate()
}

function removeParam(index: number) {
  localParams.value.splice(index, 1)
  emitUpdate()
}

function updateParam(index: number, field: string, value: any) {
  if (localParams.value[index]) {
    localParams.value[index][field] = value
    emitUpdate()
  }
}

function updateResponseContent(value: string) {
  emit('update', { responseContent: value })
}

function emitUpdate() {
  emit('update', { outputParams: JSON.parse(JSON.stringify(localParams.value)) })
}
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

.param-section {
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

.template-section {
  .template-label {
    font-size: 13px;
    color: #595959;
    margin-bottom: 8px;
  }

  .template-tip {
    font-size: 12px;
    color: #8c8c8c;
    margin-top: 8px;
  }
}
</style>
