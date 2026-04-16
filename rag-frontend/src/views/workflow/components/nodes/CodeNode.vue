<template>
  <div class="workflow-node code-node" :class="{ selected }">
    <div class="node-header">
      <span class="node-icon">💻</span>
      <span class="node-title">{{ data.label }}</span>
      <button v-if="selected" class="delete-btn" @click.stop="handleDelete">
        <CloseOutlined />
      </button>
    </div>
    <div class="node-body">
      <div class="node-info">
        <span class="info-label">语言:</span>
        <span class="info-value">{{ data.language || 'javascript' }}</span>
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
import { CloseOutlined } from '@ant-design/icons-vue'
import { useWorkflowStore, type WorkflowNodeData } from '@/stores/workflow'

const props = defineProps<{
  data: WorkflowNodeData
  selected?: boolean
  id?: string
}>()

const workflowStore = useWorkflowStore()

function handleDelete() {
  if (props.id) {
    workflowStore.deleteNode(props.id)
  }
}
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
    border-color: #eb2f96;
    box-shadow: 0 0 0 2px rgba(235, 47, 150, 0.2);
  }
}

.code-node {
  .node-header {
    background: linear-gradient(135deg, #eb2f96 0%, #f759ab 100%);
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

  .delete-btn {
    width: 20px;
    height: 20px;
    border: none;
    background: rgba(255, 255, 255, 0.3);
    border-radius: 50%;
    color: #fff;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 10px;
    transition: all 0.2s;

    &:hover {
      background: #ff4d4f;
    }
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
  border: 2px solid #eb2f96;
  background: #fff;
  transition: all 0.2s ease;

  &:hover {
    transform: scale(1.2);
    background: #eb2f96;
  }
}

.input-handle {
  left: -6px;
}

.output-handle {
  right: -6px;
}
</style>
