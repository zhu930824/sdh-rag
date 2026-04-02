<template>
  <div class="config-section">
    <div class="section-title">输入变量</div>

    <div class="variable-list">
      <div v-for="(variable, index) in variables" :key="index" class="variable-item">
        <a-input
          v-model:value="variable.name"
          placeholder="变量名"
          size="small"
          style="width: 100px"
        />
        <a-select v-model:value="variable.type" size="small" style="width: 90px">
          <a-select-option value="string">字符串</a-select-option>
          <a-select-option value="number">数字</a-select-option>
          <a-select-option value="boolean">布尔</a-select-option>
          <a-select-option value="object">对象</a-select-option>
        </a-select>
        <a-input
          v-model:value="variable.defaultValue"
          placeholder="默认值"
          size="small"
          style="flex: 1"
        />
        <a-button type="text" danger size="small" @click="removeVariable(index)">
          <template #icon><DeleteOutlined /></template>
        </a-button>
      </div>
    </div>

    <a-button type="dashed" block size="small" @click="addVariable">
      <template #icon><PlusOutlined /></template>
      添加变量
    </a-button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons-vue'
import type { Node } from '@vue-flow/core'
import type { WorkflowNodeData, InputVariable } from '@/stores/workflow'

const props = defineProps<{
  node: Node<WorkflowNodeData>
}>()

const emit = defineEmits<{
  (e: 'update', data: Partial<WorkflowNodeData>): void
}>()

const variables = computed({
  get: () => props.node.data.variables || [],
  set: (val) => emit('update', { variables: val }),
})

function addVariable() {
  const newVar: InputVariable = { name: '', type: 'string', defaultValue: '' }
  emit('update', { variables: [...variables.value, newVar] })
}

function removeVariable(index: number) {
  const newVariables = [...variables.value]
  newVariables.splice(index, 1)
  emit('update', { variables: newVariables })
}
</script>

<style scoped lang="scss">
.config-section {
  margin-bottom: 16px;

  .section-title {
    font-size: 13px;
    font-weight: 500;
    color: #262626;
    margin-bottom: 12px;
    padding-bottom: 8px;
    border-bottom: 1px solid #f0f0f0;
  }
}

.variable-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}

.variable-item {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
