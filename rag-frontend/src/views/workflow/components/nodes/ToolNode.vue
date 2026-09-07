<template>
  <div class="workflow-node tool-node" :class="{ selected }">
    <div class="node-header">
      <span class="node-icon">🛡️</span>
      <span class="node-title">{{ data.label }}</span>
      <button v-if="selected" class="delete-btn" @click.stop="handleDelete"><CloseOutlined /></button>
    </div>
    <div class="node-body">
      <div class="node-info"><span>工具</span><strong>{{ data.toolName || '未选择' }}</strong></div>
      <div class="node-info"><span>治理</span><strong>审批 · 审计 · 幂等</strong></div>
    </div>
    <Handle type="target" :position="Position.Left" class="node-handle" />
    <Handle type="source" :position="Position.Right" class="node-handle" />
  </div>
</template>

<script setup lang="ts">
import { Handle, Position } from '@vue-flow/core'
import { CloseOutlined } from '@ant-design/icons-vue'
import { useWorkflowStore, type WorkflowNodeData } from '@/stores/workflow'

const props = defineProps<{ data: WorkflowNodeData; selected?: boolean; id?: string }>()
const workflowStore = useWorkflowStore()
function handleDelete() { if (props.id) workflowStore.deleteNode(props.id) }
</script>

<style scoped lang="scss">
.workflow-node { background: #fff; border: 2px solid #e8e8e8; border-radius: 8px; min-width: 200px; box-shadow: 0 2px 8px rgba(0,0,0,.08); }
.workflow-node.selected { border-color: #08979c; box-shadow: 0 0 0 2px rgba(8,151,156,.18); }
.node-header { display:flex; align-items:center; gap:8px; padding:10px 12px; color:#fff; background:linear-gradient(135deg,#006d75,#13c2c2); border-radius:6px 6px 0 0; }
.node-title { flex:1; font-size:14px; font-weight:500; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.delete-btn { width:20px; height:20px; border:0; border-radius:50%; color:#fff; background:rgba(255,255,255,.3); cursor:pointer; }
.node-body { padding:10px 12px; display:flex; flex-direction:column; gap:5px; }
.node-info { display:flex; justify-content:space-between; gap:12px; color:#8c8c8c; font-size:12px; }
.node-info strong { max-width:120px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; color:#262626; }
.node-handle { width:12px; height:12px; border:2px solid #08979c; background:#fff; }
</style>
