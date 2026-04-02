<template>
  <div class="workflow-node llm-node" :class="{ selected }">
    <div class="node-header">
      <span class="node-icon">🤖</span>
      <span class="node-title">{{ data.label }}</span>
    </div>
    <div class="node-body">
      <div class="node-info">
        <span class="info-label">模型:</span>
        <span class="info-value">{{ data.model || '未设置' }}</span>
      </div>
      <div class="node-info">
        <span class="info-label">温度:</span>
        <span class="info-value">{{ data.temperature || 0.7 }}</span>
      </div>
    </div>

    <!-- 输入连接点 -->
    <Handle type="target" :position="Position.Left" class="node-handle input-handle" />
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
    border-color: #13c2c2;
    box-shadow: 0 0 0 2px rgba(19, 194, 194, 0.2);
  }
}

.llm-node {
  .node-header {
    background: linear-gradient(135deg, #13c2c2 0%, #36cfc9 100%);
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
  display: flex;
  flex-direction: column;
  gap: 4px;
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
    max-width: 100px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.node-handle {
  width: 12px;
  height: 12px;
  border: 2px solid #13c2c2;
  background: #fff;
  transition: all 0.2s ease;

  &:hover {
    transform: scale(1.2);
    background: #13c2c2;
  }
}

.input-handle {
  left: -6px;
}

.output-handle {
  right: -6px;
}
</style>
