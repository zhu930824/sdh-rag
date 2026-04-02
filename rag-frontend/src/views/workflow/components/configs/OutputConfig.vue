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

      <div v-for="(param, index) in outputParams" :key="index" class="param-item">
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
        <a-button type="text" danger size="small" @click="removeParam(index)">
          <template #icon><DeleteOutlined /></template>
        </a-button>
      </div>
    </div>

    <!-- 响应模板 -->
    <div class="template-section">
      <div class="template-label">响应模板</div>
      <a-textarea
        v-model:value="responseContent"
        placeholder="使用 {{参数名}} 引用输出配置中的参数"
        :rows="6"
        style="font-family: monospace"
      />
      <div class="template-tip">
        💡 使用 {{ 参数名 }} 引用上面定义的参数
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
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

const outputParams = computed({
  get: () => props.node.data.outputParams || [],
  set: (val) => emit('update', { outputParams: val }),
})

const responseContent = computed({
  get: () => props.node.data.responseContent || '',
  set: (val) => emit('update', { responseContent: val }),
})

// 获取可引用的参数
const referenceableParams = computed(() =>
  workflowStore.getReferenceableParams(props.node.id)
)

function addParam() {
  const newParam: OutputParam = { name: '', type: 'input', value: '' }
  emit('update', { outputParams: [...outputParams.value, newParam] })
}

function removeParam(index: number) {
  const newParams = [...outputParams.value]
  newParams.splice(index, 1)
  emit('update', { outputParams: newParams })
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
