<template>
  <div class="workflow-node condition-node" :class="{ selected }">
    <div class="node-header">
      <span class="node-icon">🔀</span>
      <span class="node-title">{{ data.label }}</span>
    </div>
    <div class="node-body">
      <div class="node-info">
        <span class="info-label">分支:</span>
        <span class="info-value">{{ data.conditions?.length || 0 }} 个</span>
      </div>
    </div>

    <!-- 输入连接点 -->
    <Handle type="target" :position="Position.Left" class="node-handle input-handle" />
    <!-- 多个输出连接点 -->
    <div v-for="(condition, index) in data.conditions" :key="index" class="condition-handle">
      <Handle
        :id="`condition-${index}`"
        type="source"
        :position="Position.Right"
        class="node-handle output-handle"
        :style="{ top: `${60 + index * 20}px` }"
      />
      <span class="handle-label">{{ condition.label }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Handle, Position } from '@vue-flow/core'
import type { WorkflowNodeData } from '@/stores/workflow'

defineProps<{
  data: WorkflowNodeData
  selected?: boolean
}>()
</script>

<style scoped lang="scss">
.workflow-node {
  background: #fff;
  border: 2px solid #e8e8e8;
  border-radius: 8px;
  min-width: 180px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  transition: all 0.2s ease;

  &:hover {
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
  }

  &.selected {
    border-color: #faad14;
    box-shadow: 0 0 0 2px rgba(250, 173, 20, 0.2);
  }
}

.condition-node {
  .node-header {
    background: linear-gradient(135deg, #faad14 0%, #ffc53d 100%);
    border-radius: 6px 6px 0 0;
  }
}

.node-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  color: #fff;

  .node-icon {
    font-size: 16px;
  }

  .node-title {
    font-size: 14px;
    font-weight: 500;
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.node-body {
  padding: 10px 12px;
}

.node-info {
  display: flex;
  justify-content: space-between;
  font-size: 12px;

  .info-label {
    color: #8c8c8c;
  }

  .info-value {
    color: #262626;
    font-weight: 500;
  }
}

.condition-handle {
  position: relative;

  .handle-label {
    position: absolute;
    right: 20px;
    top: -6px;
    font-size: 10px;
    color: #8c8c8c;
    background: #f5f5f5;
    padding: 1px 4px;
    border-radius: 2px;
  }
}

.node-handle {
  width: 12px;
  height: 12px;
  border: 2px solid #faad14;
  background: #fff;
  transition: all 0.2s ease;

  &:hover {
    transform: scale(1.2);
    background: #faad14;
  }
}

.input-handle {
  left: -6px;
}

.output-handle {
  right: -6px;
}
</style>
