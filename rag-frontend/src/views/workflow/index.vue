<template>
  <div class="workflow-editor">
    <!-- 顶部工具栏 -->
    <div class="editor-toolbar">
      <div class="toolbar-left">
        <a-input
          v-model:value="workflowName"
          placeholder="工作流名称"
          style="width: 200px"
          @blur="handleNameChange"
        />
        <a-tag v-if="currentWorkflowId" color="blue">ID: {{ currentWorkflowId }}</a-tag>
      </div>
      <div class="toolbar-right">
        <a-button @click="handleAutoLayout">
          <template #icon><ApartmentOutlined /></template>
          自动布局
        </a-button>
        <a-button type="primary" :loading="saving" @click="handleSave">
          <template #icon><SaveOutlined /></template>
          保存
        </a-button>
        <a-button type="primary" :disabled="!currentWorkflowId" :loading="running" @click="showRunDialog">
          <template #icon><PlayCircleOutlined /></template>
          运行
        </a-button>
      </div>
    </div>

    <!-- 运行参数输入对话框 -->
    <a-modal
      v-model:open="runDialogVisible"
      title="输入运行参数"
      :confirm-loading="running"
      @ok="handleRun"
      @cancel="runDialogVisible = false"
    >
      <div v-if="inputVariables.length > 0">
        <a-form layout="vertical">
          <a-form-item
            v-for="variable in inputVariables"
            :key="variable.name"
            :label="variable.name"
            :required="!variable.defaultValue"
          >
            <a-input
              v-if="variable.type === 'string'"
              v-model:value="inputValues[variable.name]"
              :placeholder="`请输入${variable.name}`"
            />
            <a-input-number
              v-else-if="variable.type === 'number'"
              v-model:value="inputValues[variable.name]"
              :placeholder="`请输入${variable.name}`"
              style="width: 100%"
            />
            <a-switch
              v-else-if="variable.type === 'boolean'"
              v-model:checked="inputValues[variable.name]"
            />
            <a-textarea
              v-else
              v-model:value="inputValues[variable.name]"
              :placeholder="`请输入${variable.name}（JSON格式）`"
              :rows="4"
            />
            <div v-if="variable.defaultValue" class="default-value-hint">
              默认值: {{ variable.defaultValue }}
            </div>
          </a-form-item>
        </a-form>
      </div>
      <a-empty v-else description="无需输入参数" />
    </a-modal>

    <!-- 主内容区 -->
    <div class="editor-content">
      <!-- 左侧节点面板 -->
      <NodePanel />

      <!-- 中间画布 -->
      <div
        class="canvas-wrapper"
        @dragover.prevent
        @drop="handleDrop"
      >
        <FlowCanvas @node-click="handleNodeClick" @pane-click="handlePaneClick" />
      </div>

      <!-- 右侧配置面板 -->
      <ConfigPanel />
    </div>

    <!-- 运行结果抽屉 -->
    <a-drawer
      v-model:open="resultDrawerVisible"
      title="运行结果"
      :width="500"
      placement="right"
    >
      <div v-if="executionResult" class="execution-result">
        <a-alert
          :type="executionResult.status?.toUpperCase() === 'SUCCESS' ? 'success' : 'error'"
          :message="executionResult.status?.toUpperCase() === 'SUCCESS' ? '执行成功' : '执行失败'"
          show-icon
          style="margin-bottom: 16px"
        />

        <a-timeline>
          <a-timeline-item
            v-for="nodeExec in executionResult.nodeResults || executionResult.nodeExecutions"
            :key="nodeExec.nodeId"
            :color="getNodeExecutionColor(nodeExec.status)"
          >
            <div class="execution-node">
              <div class="node-header">
                <span class="node-name">{{ nodeExec.nodeName }}</span>
                <a-tag :color="getNodeExecutionColor(nodeExec.status)" size="small">
                  {{ nodeExec.status }}
                </a-tag>
              </div>
              <div v-if="nodeExec.duration" class="node-duration">
                耗时: {{ nodeExec.duration }}ms
              </div>
              <div v-if="nodeExec.error" class="node-error">
                <a-alert type="error" :message="nodeExec.error" />
              </div>
            </div>
          </a-timeline-item>
        </a-timeline>

        <a-divider>输出结果</a-divider>
        <pre class="output-json">{{ JSON.stringify(executionResult.outputData || executionResult.outputs, null, 2) }}</pre>
      </div>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  SaveOutlined,
  PlayCircleOutlined,
  ApartmentOutlined,
} from '@ant-design/icons-vue'
import type { Node } from '@vue-flow/core'

import { useWorkflowStore, type WorkflowNodeData } from '@/stores/workflow'
import {
  getWorkflowDetail,
  createWorkflow,
  updateWorkflow,
  executeWorkflow,
  type WorkflowExecution,
} from '@/api/workflow'

import NodePanel from './components/NodePanel.vue'
import FlowCanvas from './components/FlowCanvas.vue'
import ConfigPanel from './components/ConfigPanel.vue'

const route = useRoute()
const router = useRouter()
const workflowStore = useWorkflowStore()

// 状态
const saving = ref(false)
const running = ref(false)
const resultDrawerVisible = ref(false)
const executionResult = ref<any>(null)
const runDialogVisible = ref(false)
const inputValues = ref<Record<string, any>>({})

// 计算属性
const workflowName = computed({
  get: () => workflowStore.workflowName,
  set: (val) => workflowStore.setWorkflowName(val),
})

const currentWorkflowId = computed(() => workflowStore.currentWorkflowId)

// 获取所有输入节点的变量
const inputVariables = computed(() => {
  const variables: { name: string; type: string; defaultValue: string }[] = []
  workflowStore.nodes.forEach((node) => {
    if (node.data.type === 'input' && node.data.variables) {
      node.data.variables.forEach((v) => {
        if (v.name) {
          variables.push({
            name: v.name,
            type: v.type || 'string',
            defaultValue: v.defaultValue || '',
          })
        }
      })
    }
  })
  return variables
})

// 加载工作流
async function loadWorkflow(id: number) {
  try {
    const res = await getWorkflowDetail(id)
    if (res.data) {
      workflowStore.loadWorkflowData({
        id: res.data.id,
        name: res.data.name,
        flowData: res.data.flowData,
      })
    }
  } catch (error) {
    message.error('加载工作流失败')
    console.error(error)
  }
}

// 处理拖放
function handleDrop(event: DragEvent) {
  const data = event.dataTransfer?.getData('application/vueflow')
  if (!data) return

  try {
    const nodeDef = JSON.parse(data)

    // 获取画布偏移量
    const canvasWrapper = event.target as HTMLElement
    const rect = canvasWrapper.getBoundingClientRect()
    const x = event.clientX - rect.left - 90
    const y = event.clientY - rect.top - 30

    // 创建新节点
    const newNode: Node<WorkflowNodeData> = {
      id: `${nodeDef.type}-${Date.now()}`,
      type: nodeDef.type,
      position: { x: Math.max(0, x), y: Math.max(0, y) },
      data: {
        label: nodeDef.name,
        type: nodeDef.type,
        ...getDefaultConfig(nodeDef.type),
      },
    }

    workflowStore.addNode(newNode)
    workflowStore.setSelectedNode(newNode)
  } catch (error) {
    console.error('解析拖放数据失败:', error)
  }
}

// 获取默认配置
function getDefaultConfig(type: string): Partial<WorkflowNodeData> {
  const defaults: Record<string, Partial<WorkflowNodeData>> = {
    input: { variables: [{ name: 'query', type: 'string', defaultValue: '' }] },
    output: { outputParams: [], responseContent: '' },
    llm: { model: '', temperature: 0.7, maxTokens: 2000, inputParams: [], prompt: '' },
    retrieval: { topK: 5, scoreThreshold: 0.7, knowledgeBaseId: null },
    rerank: { model: '', topK: 5 },
    condition: { conditions: [{ expression: 'true', label: '默认' }] },
    http: { method: 'GET', url: '', headers: '{}', body: '' },
    code: { language: 'javascript', code: '' },
  }
  return defaults[type] || {}
}

// 处理节点点击
function handleNodeClick(node: Node) {
  workflowStore.setSelectedNode(node)
}

// 处理画布点击
function handlePaneClick() {
  workflowStore.setSelectedNode(null)
}

// 处理名称变化
function handleNameChange() {
  workflowStore.setWorkflowName(workflowName.value)
}

// 自动布局
function handleAutoLayout() {
  const nodeWidth = 200
  const nodeHeight = 100
  const gapX = 100
  const gapY = 150
  const startX = 50
  const startY = 50

  // 按拓扑顺序排列
  const nodes = [...workflowStore.nodes]
  const edges = workflowStore.edges

  // 计算每个节点的层级
  const levels: Map<string, number> = new Map()
  const nodeMap = new Map(nodes.map((n) => [n.id, n]))

  // 找到输入节点作为起始
  const startNodes = nodes.filter((n) => n.type === 'input')
  startNodes.forEach((n) => levels.set(n.id, 0))

  // BFS 计算层级
  let changed = true
  while (changed) {
    changed = false
    for (const edge of edges) {
      const sourceLevel = levels.get(edge.source)
      if (sourceLevel !== undefined) {
        const targetLevel = levels.get(edge.target)
        if (targetLevel === undefined || targetLevel < sourceLevel + 1) {
          levels.set(edge.target, sourceLevel + 1)
          changed = true
        }
      }
    }
  }

  // 分配未连接的节点
  let maxLevel = Math.max(...levels.values(), 0)
  for (const node of nodes) {
    if (!levels.has(node.id)) {
      levels.set(node.id, ++maxLevel)
    }
  }

  // 按层级分组
  const levelGroups: Map<number, Node[]> = new Map()
  for (const node of nodes) {
    const level = levels.get(node.id) || 0
    if (!levelGroups.has(level)) {
      levelGroups.set(level, [])
    }
    levelGroups.get(level)!.push(node)
  }

  // 应用布局
  levelGroups.forEach((group, level) => {
    group.forEach((node, index) => {
      workflowStore.updateNodePosition(node.id, {
        x: startX + level * (nodeWidth + gapX),
        y: startY + index * (nodeHeight + gapY),
      })
    })
  })

  message.success('布局完成')
}

// 保存工作流
async function handleSave() {
  if (workflowStore.nodes.length === 0) {
    message.warning('工作流为空，无法保存')
    return
  }

  saving.value = true
  try {
    const flowData = JSON.stringify({
      nodes: workflowStore.serializeNodes(),
      edges: workflowStore.serializeEdges(),
    })

    if (currentWorkflowId.value) {
      await updateWorkflow(currentWorkflowId.value, {
        id: currentWorkflowId.value,
        name: workflowName.value,
        description: '',
        flowData: flowData,
      })
      message.success('保存成功')
    } else {
      const res = await createWorkflow({
        name: workflowName.value,
        description: '',
        flowData: flowData,
      })
      if (res.data?.id) {
        workflowStore.setCurrentWorkflowId(res.data.id)
        router.replace(`/workflow/${res.data.id}`)
        message.success('创建成功')
      }
    }
  } catch (error) {
    message.error('保存失败')
    console.error(error)
  } finally {
    saving.value = false
  }
}

// 显示运行参数对话框
function showRunDialog() {
  if (!currentWorkflowId.value) {
    message.warning('请先保存工作流')
    return
  }

  // 初始化输入值，使用默认值
  inputValues.value = {}
  inputVariables.value.forEach((v) => {
    if (v.defaultValue) {
      // 根据类型转换默认值
      if (v.type === 'number') {
        inputValues.value[v.name] = Number(v.defaultValue) || 0
      } else if (v.type === 'boolean') {
        inputValues.value[v.name] = v.defaultValue === 'true'
      } else {
        inputValues.value[v.name] = v.defaultValue
      }
    }
  })

  runDialogVisible.value = true
}

// 运行工作流
async function handleRun() {
  if (!currentWorkflowId.value) {
    message.warning('请先保存工作流')
    return
  }

  // 检查必填参数
  const missingParams = inputVariables.value.filter(
    (v) => !v.defaultValue && inputValues.value[v.name] === undefined
  )
  if (missingParams.length > 0) {
    message.warning(`请填写参数: ${missingParams.map((p) => p.name).join(', ')}`)
    return
  }

  runDialogVisible.value = false
  running.value = true
  resultDrawerVisible.value = true
  executionResult.value = null

  try {
    const res = await executeWorkflow(currentWorkflowId.value, inputValues.value)
    executionResult.value = res.data
  } catch (error) {
    message.error('执行失败')
    console.error(error)
  } finally {
    running.value = false
  }
}

// 获取节点执行颜色
function getNodeExecutionColor(status: string): string {
  const statusLower = status?.toLowerCase()
  const colors: Record<string, string> = {
    pending: 'gray',
    running: 'blue',
    success: 'green',
    failed: 'red',
    skipped: 'default',
  }
  return colors[statusLower] || 'default'
}

// 初始化
onMounted(() => {
  const id = route.params.id as string
  if (id && id !== 'create') {
    loadWorkflow(Number(id))
  } else {
    workflowStore.resetToNew()
  }
})

// 监听路由变化
watch(
  () => route.params.id,
  (newId) => {
    if (newId && newId !== 'create') {
      loadWorkflow(Number(newId))
    } else {
      workflowStore.resetToNew()
    }
  }
)
</script>

<style scoped lang="scss">
.workflow-editor {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 64px - 48px);
  overflow-y: auto;
  background: #f5f5f5;
}

.editor-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
  flex-shrink: 0;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.editor-content {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.canvas-wrapper {
  flex: 1;
  position: relative;
  overflow: hidden;
}

.execution-result {
  .execution-node {
    .node-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 4px;

      .node-name {
        font-weight: 500;
      }
    }

    .node-duration {
      font-size: 12px;
      color: #8c8c8c;
      margin-bottom: 4px;
    }
  }

  .output-json {
    background: #f5f5f5;
    padding: 12px;
    border-radius: 8px;
    font-size: 12px;
    overflow-x: auto;
    max-height: 300px;
  }
}

.default-value-hint {
  font-size: 12px;
  color: #8c8c8c;
  margin-top: 4px;
}
</style>
