<template>
  <div class="config-section">
    <div class="section-title">条件配置</div>

    <div class="condition-list">
      <div v-for="(condition, index) in conditions" :key="index" class="condition-item">
        <div class="condition-header">
          <span class="condition-index">分支 {{ index + 1 }}</span>
          <a-button
            v-if="conditions.length > 1"
            type="text"
            danger
            size="small"
            @click="removeCondition(index)"
          >
            <template #icon><DeleteOutlined /></template>
          </a-button>
        </div>
        <a-input
          v-model:value="condition.label"
          placeholder="分支标签"
          size="small"
          style="margin-bottom: 8px"
        />
        <a-textarea
          v-model:value="condition.expression"
          placeholder="条件表达式，如: input.score > 0.8"
          :rows="2"
          size="small"
          style="font-family: monospace"
        />
      </div>
    </div>

    <a-button type="dashed" block size="small" @click="addCondition">
      <template #icon><PlusOutlined /></template>
      添加分支
    </a-button>

    <div class="condition-tip">
      💡 条件表达式支持 JavaScript 语法，可引用前置节点的输出参数
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons-vue'
import type { WorkflowNodeData, ConditionItem } from '@/stores/workflow'
import type { Node } from '@vue-flow/core'

const props = defineProps<{
  node: Node<WorkflowNodeData>
}>()

const emit = defineEmits<{
  (e: 'update', data: Partial<WorkflowNodeData>): void
}>()

const conditions = computed({
  get: () => props.node.data.conditions || [{ expression: 'true', label: '默认' }],
  set: (val) => emit('update', { conditions: val }),
})

function addCondition() {
  const newCondition: ConditionItem = { expression: 'false', label: '新分支' }
  emit('update', { conditions: [...conditions.value, newCondition] })
}

function removeCondition(index: number) {
  const newConditions = [...conditions.value]
  newConditions.splice(index, 1)
  emit('update', { conditions: newConditions })
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

.condition-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 12px;
}

.condition-item {
  background: #fafafa;
  border-radius: 6px;
  padding: 12px;

  .condition-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;

    .condition-index {
      font-size: 12px;
      font-weight: 500;
      color: #595959;
    }
  }
}

.condition-tip {
  font-size: 12px;
  color: #8c8c8c;
  margin-top: 12px;
  background: #fffbe6;
  padding: 8px;
  border-radius: 4px;
}
</style>
