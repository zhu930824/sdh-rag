<template>
  <div class="flow-canvas-container">
    <VueFlow
      :nodes="nodes"
      :edges="edges"
      :default-viewport="{ zoom: 1, x: 0, y: 0 }"
      :min-zoom="0.2"
      :max-zoom="4"
      fit-view-on-init
      @node-click="handleNodeClick"
      @pane-click="handlePaneClick"
      @connect="handleConnect"
      @edge-click="handleEdgeClick"
      @nodes-change="handleNodesChange"
      @edges-change="handleEdgesChange"
    >
      <!-- 背景 -->
      <Background :gap="20" :size="1" pattern-color="#e5e7eb" />

      <!-- 控制按钮 -->
      <Controls position="bottom-left" />

      <!-- 小地图 -->
      <MiniMap
        position="bottom-right"
        :pannable="true"
        :zoomable="true"
        :node-color="getNodeColor"
      />

      <!-- 自定义节点类型 -->
      <template #node-input="nodeProps">
        <InputNode :data="nodeProps.data" :selected="nodeProps.selected" />
      </template>
      <template #node-output="nodeProps">
        <OutputNode :data="nodeProps.data" :selected="nodeProps.selected" />
      </template>
      <template #node-llm="nodeProps">
        <LlmNode :data="nodeProps.data" :selected="nodeProps.selected" />
      </template>
      <template #node-retrieval="nodeProps">
        <RetrievalNode :data="nodeProps.data" :selected="nodeProps.selected" />
      </template>
      <template #node-condition="nodeProps">
        <ConditionNode :data="nodeProps.data" :selected="nodeProps.selected" />
      </template>
      <template #node-http="nodeProps">
        <HttpNode :data="nodeProps.data" :selected="nodeProps.selected" />
      </template>
      <template #node-code="nodeProps">
        <CodeNode :data="nodeProps.data" :selected="nodeProps.selected" />
      </template>

      <!-- 自定义边 -->
      <template #edge-button="edgeProps">
        <EdgeWithButton v-bind="edgeProps" @delete="handleDeleteEdge" />
      </template>
    </VueFlow>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { VueFlow, useVueFlow, type Node, type Edge, type Connection } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import { MarkerType } from '@vue-flow/core'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import '@vue-flow/minimap/dist/style.css'

import { useWorkflowStore } from '@/stores/workflow'

// 自定义节点组件
import InputNode from './nodes/InputNode.vue'
import OutputNode from './nodes/OutputNode.vue'
import LlmNode from './nodes/LlmNode.vue'
import RetrievalNode from './nodes/RetrievalNode.vue'
import ConditionNode from './nodes/ConditionNode.vue'
import HttpNode from './nodes/HttpNode.vue'
import CodeNode from './nodes/CodeNode.vue'
import EdgeWithButton from './edges/EdgeWithButton.vue'

const emit = defineEmits<{
  (e: 'node-click', node: Node): void
  (e: 'pane-click'): void
}>()

const workflowStore = useWorkflowStore()
const { onConnect, addEdges, project, vueFlowRef } = useVueFlow()

// 使用计算属性获取节点和边（单向绑定，由 store 管理）
const nodes = computed(() => workflowStore.nodes)
const edges = computed(() => workflowStore.edges)

// 节点点击
function handleNodeClick(event: { node: Node }) {
  workflowStore.setSelectedNode(event.node)
  emit('node-click', event.node)
}

// 画布点击
function handlePaneClick() {
  workflowStore.setSelectedNode(null)
  emit('pane-click')
}

// 连接处理
function handleConnect(connection: Connection) {
  const newEdge: Edge = {
    id: `edge-${Date.now()}`,
    source: connection.source,
    target: connection.target,
    sourceHandle: connection.sourceHandle,
    targetHandle: connection.targetHandle,
    type: 'button',
    markerEnd: MarkerType.ArrowClosed,
    animated: true,
    style: { stroke: '#1890ff', strokeWidth: 2 },
  }
  workflowStore.addEdge(newEdge)
}

// 边点击
function handleEdgeClick(event: { edge: Edge }) {
  console.log('Edge clicked:', event.edge)
}

// 删除边
function handleDeleteEdge(edgeId: string) {
  workflowStore.deleteEdge(edgeId)
}

// 节点变化处理
function handleNodesChange(changes: any[]) {
  changes.forEach((change) => {
    if (change.type === 'position' && change.position) {
      // 更新节点位置
      workflowStore.updateNodePosition(change.id, change.position)
    } else if (change.type === 'remove') {
      // 处理节点删除（如用户按 Delete 键）
      workflowStore.deleteNode(change.id)
    }
    // 'add' 类型由 store 的 addNode 方法管理，忽略
  })
}

// 边变化处理
function handleEdgesChange(changes: any[]) {
  changes.forEach((change) => {
    if (change.type === 'remove') {
      workflowStore.deleteEdge(change.id)
    }
  })
}

// 获取节点颜色（用于小地图）
function getNodeColor(node: Node): string {
  const colors: Record<string, string> = {
    input: '#52c41a',
    output: '#722ed1',
    llm: '#13c2c2',
    retrieval: '#fa8c16',
    condition: '#faad14',
    http: '#2f54eb',
    code: '#eb2f96',
  }
  return colors[node.type || ''] || '#1890ff'
}
</script>

<style scoped lang="scss">
.flow-canvas-container {
  width: 100%;
  height: 100%;
  background: #f5f5f5;
}

:deep(.vue-flow) {
  background: linear-gradient(135deg, #f5f7fa 0%, #e4e8eb 100%);
}

:deep(.vue-flow__minimap) {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

:deep(.vue-flow__controls) {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);

  button {
    border: none;
    border-bottom: 1px solid #f0f0f0;

    &:hover {
      background: #f5f5f5;
    }

    &:last-child {
      border-bottom: none;
    }
  }
}
</style>
