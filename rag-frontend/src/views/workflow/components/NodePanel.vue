<template>
  <div class="node-panel">
    <div class="panel-header">
      <span class="panel-title">节点组件</span>
    </div>
    <div class="panel-body">
      <a-collapse v-model:activeKey="activeKeys" ghost>
        <a-collapse-panel v-for="category in nodeCategories" :key="category.key" :header="category.name">
          <div class="node-list">
            <div
              v-for="node in category.nodes"
              :key="node.type"
              class="node-item"
              draggable="true"
              @dragstart="handleDragStart($event, node)"
            >
              <div class="node-icon" :style="{ backgroundColor: node.color }">
                <span>{{ node.icon }}</span>
              </div>
              <div class="node-info">
                <div class="node-name">{{ node.name }}</div>
                <div class="node-desc">{{ node.description }}</div>
              </div>
            </div>
          </div>
        </a-collapse-panel>
      </a-collapse>

      <div class="panel-tip">
        <span>💡 拖拽节点到画布中使用</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

export interface NodeDefinition {
  type: string
  name: string
  icon: string
  color: string
  description: string
}

const activeKeys = ref(['basic', 'data', 'process'])

const nodeCategories = [
  {
    key: 'basic',
    name: '基础节点',
    nodes: [
      { type: 'input', name: '输入', icon: '📥', color: '#52c41a', description: '接收用户输入' },
      { type: 'output', name: '输出', icon: '📤', color: '#722ed1', description: '输出结果' },
    ],
  },
  {
    key: 'process',
    name: '处理节点',
    nodes: [
      { type: 'llm', name: 'LLM', icon: '🤖', color: '#13c2c2', description: '大语言模型' },
      { type: 'retrieval', name: '检索', icon: '🔍', color: '#fa8c16', description: '知识库检索' },
      { type: 'http', name: 'HTTP', icon: '🌐', color: '#2f54eb', description: 'HTTP请求' },
      { type: 'code', name: '代码', icon: '💻', color: '#eb2f96', description: '执行代码' },
    ],
  },
  {
    key: 'control',
    name: '控制节点',
    nodes: [
      { type: 'condition', name: '条件', icon: '🔀', color: '#faad14', description: '条件分支' },
    ],
  },
]

function handleDragStart(event: DragEvent, node: NodeDefinition) {
  if (event.dataTransfer) {
    event.dataTransfer.setData('application/vueflow', JSON.stringify(node))
    event.dataTransfer.effectAllowed = 'move'
  }
}
</script>

<style scoped lang="scss">
.node-panel {
  width: 280px;
  height: 100%;
  background: #fff;
  border-right: 1px solid #f0f0f0;
  display: flex;
  flex-direction: column;
}

.panel-header {
  padding: 16px;
  border-bottom: 1px solid #f0f0f0;

  .panel-title {
    font-size: 15px;
    font-weight: 600;
    color: #262626;
  }
}

.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.node-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.node-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: #fafafa;
  border-radius: 8px;
  cursor: grab;
  transition: all 0.2s ease;

  &:hover {
    background: #e6f7ff;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  }

  &:active {
    cursor: grabbing;
  }
}

.node-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}

.node-info {
  flex: 1;
  min-width: 0;
}

.node-name {
  font-size: 14px;
  font-weight: 500;
  color: #262626;
}

.node-desc {
  font-size: 12px;
  color: #8c8c8c;
  margin-top: 2px;
}

.panel-tip {
  margin-top: 12px;
  padding: 12px;
  background: #e6f7ff;
  border-radius: 8px;
  font-size: 12px;
  color: #1890ff;
}

:deep(.ant-collapse-header) {
  font-weight: 500;
  color: #262626;
}

:deep(.ant-collapse-content-box) {
  padding: 8px 0 !important;
}
</style>
