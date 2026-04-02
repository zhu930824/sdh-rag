<template>
  <div class="config-panel">
    <div class="panel-header">
      <span class="panel-title">节点配置</span>
      <a-button v-if="selectedNode" type="text" size="small" @click="clearSelection">
        <template #icon><CloseOutlined /></template>
      </a-button>
    </div>

    <div class="panel-body">
      <template v-if="selectedNode">
        <!-- 节点基本信息 -->
        <div class="config-section">
          <div class="section-title">基本信息</div>
          <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 18 }">
            <a-form-item label="节点 ID">
              <a-input :value="selectedNode.id" disabled size="small" />
            </a-form-item>
            <a-form-item label="节点名称">
              <a-input v-model:value="selectedNode.data.label" placeholder="请输入节点名称" size="small" />
            </a-form-item>
          </a-form>
        </div>

        <!-- 输入节点配置 -->
        <template v-if="selectedNode.data.type === 'input'">
          <InputConfig :node="selectedNode" @update="updateNodeData" />
        </template>

        <!-- 输出节点配置 -->
        <template v-if="selectedNode.data.type === 'output'">
          <OutputConfig :node="selectedNode" @update="updateNodeData" />
        </template>

        <!-- LLM 节点配置 -->
        <template v-if="selectedNode.data.type === 'llm'">
          <LlmConfig :node="selectedNode" @update="updateNodeData" />
        </template>

        <!-- 检索节点配置 -->
        <template v-if="selectedNode.data.type === 'retrieval'">
          <RetrievalConfig :node="selectedNode" @update="updateNodeData" />
        </template>

        <!-- 条件节点配置 -->
        <template v-if="selectedNode.data.type === 'condition'">
          <ConditionConfig :node="selectedNode" @update="updateNodeData" />
        </template>

        <!-- HTTP 节点配置 -->
        <template v-if="selectedNode.data.type === 'http'">
          <HttpConfig :node="selectedNode" @update="updateNodeData" />
        </template>

        <!-- 代码节点配置 -->
        <template v-if="selectedNode.data.type === 'code'">
          <CodeConfig :node="selectedNode" @update="updateNodeData" />
        </template>
      </template>

      <template v-else>
        <div class="empty-state">
          <span class="empty-icon">📋</span>
          <span class="empty-text">请选择一个节点进行配置</span>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { CloseOutlined } from '@ant-design/icons-vue'
import { useWorkflowStore, type WorkflowNodeData } from '@/stores/workflow'
import type { Node } from '@vue-flow/core'

// 配置组件
import InputConfig from './configs/InputConfig.vue'
import OutputConfig from './configs/OutputConfig.vue'
import LlmConfig from './configs/LlmConfig.vue'
import RetrievalConfig from './configs/RetrievalConfig.vue'
import ConditionConfig from './configs/ConditionConfig.vue'
import HttpConfig from './configs/HttpConfig.vue'
import CodeConfig from './configs/CodeConfig.vue'

const workflowStore = useWorkflowStore()

const selectedNode = computed(() => workflowStore.selectedNode as Node<WorkflowNodeData> | null)

function clearSelection() {
  workflowStore.setSelectedNode(null)
}

function updateNodeData(data: Partial<WorkflowNodeData>) {
  if (selectedNode.value) {
    workflowStore.updateNode(selectedNode.value.id, data)
  }
}
</script>

<style scoped lang="scss">
.config-panel {
  width: 360px;
  height: 100%;
  background: #fff;
  border-left: 1px solid #f0f0f0;
  display: flex;
  flex-direction: column;
}

.panel-header {
  padding: 16px;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  justify-content: space-between;
  align-items: center;

  .panel-title {
    font-size: 15px;
    font-weight: 600;
    color: #262626;
  }
}

.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

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

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 200px;
  color: #8c8c8c;

  .empty-icon {
    font-size: 48px;
    margin-bottom: 12px;
  }

  .empty-text {
    font-size: 14px;
  }
}

:deep(.ant-form-item) {
  margin-bottom: 12px;
}

:deep(.ant-form-item-label > label) {
  font-size: 13px;
  color: #595959;
}
</style>
