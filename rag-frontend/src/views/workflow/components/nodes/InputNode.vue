<template>
  <div class="workflow-node input-node" :class="{ selected }">
    <div class="node-header">
      <span class="node-icon">📥</span>
      <span class="node-title">{{ data.label }}</span>
    </div>
    <div class="node-body">
      <div class="node-info">
        <span class="info-label">变量:</span>
        <span class="info-value">{{ data.variables?.length || 0 }} 个</span>
      </div>
    </div>

    <!-- 输出连接点 -->
    <Handle type="source" :position="Position.Right" class="node-handle output-handle" />
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
    border-color: #52c41a;
    box-shadow: 0 0 0 2px rgba(82, 196, 26, 0.2);
  }
}

.input-node {
  .node-header {
    background: linear-gradient(135deg, #52c41a 0%, #73d13d 100%);
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

.node-handle {
  width: 12px;
  height: 12px;
  border: 2px solid #52c41a;
  background: #fff;
  transition: all 0.2s ease;

  &:hover {
    transform: scale(1.2);
    background: #52c41a;
  }
}

.output-handle {
  right: -6px;
}
</style>
