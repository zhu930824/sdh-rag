import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Node, Edge } from '@vue-flow/core'

export interface WorkflowNodeData {
  label: string
  type: string
  // LLM 节点配置
  model?: string
  systemPrompt?: string
  temperature?: number
  maxTokens?: number
  prompt?: string
  inputParams?: InputParam[]
  outputParams?: OutputParam[]
  // 检索节点配置
  knowledgeBaseId?: number
  knowledgeBaseName?: string
  topK?: number
  scoreThreshold?: number
  // 输出节点配置
  responseContent?: string
  // 条件节点配置
  conditions?: ConditionItem[]
  // HTTP 节点配置
  method?: string
  url?: string
  headers?: string
  body?: string
  // 代码节点配置
  language?: string
  code?: string
  // 输入节点配置
  variables?: InputVariable[]
}

export interface InputParam {
  name: string
  type: 'input' | 'reference'
  value: string
  referenceNode?: string
}

export interface OutputParam {
  name: string
  type: 'input' | 'reference'
  value?: string
  referenceNode?: string
}

export interface ConditionItem {
  expression: string
  label: string
}

export interface InputVariable {
  name: string
  type: string
  defaultValue: string
}

export interface WorkflowState {
  nodes: Node<WorkflowNodeData>[]
  edges: Edge[]
  selectedNode: Node<WorkflowNodeData> | null
  currentWorkflowId: number | null
  workflowName: string
  engineType: string
}

// 创建默认的工作流节点
function createDefaultWorkflowNodes(): Node<WorkflowNodeData>[] {
  return [
    {
      id: 'input-default',
      type: 'input',
      position: { x: 250, y: 100 },
      data: {
        label: '输入节点',
        type: 'input',
        variables: [{ name: 'query', type: 'string', defaultValue: '' }],
      },
    },
    {
      id: 'output-default',
      type: 'output',
      position: { x: 250, y: 400 },
      data: {
        label: '输出节点',
        type: 'output',
        outputParams: [],
        responseContent: '',
      },
    },
  ]
}

// 规范化节点数据
function normalizeNode(node: Node<WorkflowNodeData>): Node<WorkflowNodeData> {
  const label = node.data?.label || node.id
  const type = node.data?.type || node.type || 'default'
  return {
    ...node,
    data: {
      label: typeof label === 'string' ? label : String(label),
      type: typeof type === 'string' ? type : String(type),
      ...node.data,
    },
  }
}

export const useWorkflowStore = defineStore('workflow', () => {
  // 状态
  const nodes = ref<Node<WorkflowNodeData>[]>(createDefaultWorkflowNodes())
  const edges = ref<Edge[]>([])
  const selectedNode = ref<Node<WorkflowNodeData> | null>(null)
  const currentWorkflowId = ref<number | null>(null)
  const workflowName = ref('未命名工作流')
  const engineType = ref('dag')

  // 计算属性
  const hasNodes = computed(() => nodes.value.length > 0)
  const nodeCount = computed(() => nodes.value.length)
  const edgeCount = computed(() => edges.value.length)

  // 设置节点
  function setNodes(newNodes: Node<WorkflowNodeData>[]) {
    nodes.value = newNodes.map(normalizeNode)
  }

  // 设置边
  function setEdges(newEdges: Edge[]) {
    edges.value = newEdges
  }

  // 设置选中的节点
  function setSelectedNode(node: Node<WorkflowNodeData> | null) {
    selectedNode.value = node
  }

  // 设置当前工作流 ID
  function setCurrentWorkflowId(id: number | null) {
    currentWorkflowId.value = id
  }

  // 设置工作流名称
  function setWorkflowName(name: string) {
    workflowName.value = name
  }

  // 设置引擎类型
  function setEngineType(type: string) {
    engineType.value = type
  }

  // 添加节点
  function addNode(node: Node<WorkflowNodeData>) {
    nodes.value.push(normalizeNode(node))
  }

  // 更新节点数据
  function updateNode(id: string, data: Partial<WorkflowNodeData>) {
    const index = nodes.value.findIndex((n) => n.id === id)
    if (index !== -1) {
      const currentData = nodes.value[index].data
      nodes.value[index] = {
        ...nodes.value[index],
        data: {
          label: currentData?.label || '',
          type: currentData?.type || '',
          ...currentData,
          ...data,
        },
      }
      // 如果更新的是当前选中的节点，同步更新 selectedNode
      if (selectedNode.value?.id === id) {
        selectedNode.value = nodes.value[index]
      }
    }
  }

  // 更新节点位置
  function updateNodePosition(id: string, position: { x: number; y: number }) {
    const index = nodes.value.findIndex((n) => n.id === id)
    if (index !== -1) {
      nodes.value[index].position = position
    }
  }

  // 删除节点
  function deleteNode(id: string) {
    nodes.value = nodes.value.filter((n) => n.id !== id)
    // 删除相关的边
    edges.value = edges.value.filter((e) => e.source !== id && e.target !== id)
    // 如果删除的是选中的节点，清除选中状态
    if (selectedNode.value?.id === id) {
      selectedNode.value = null
    }
  }

  // 添加边
  function addEdge(edge: Edge) {
    // 检查是否已存在相同的边
    const exists = edges.value.some(
      (e) => e.source === edge.source && e.target === edge.target
    )
    if (!exists) {
      edges.value.push(edge)
    }
  }

  // 删除边
  function deleteEdge(id: string) {
    edges.value = edges.value.filter((e) => e.id !== id)
  }

  // 清空工作流
  function clear() {
    nodes.value = createDefaultWorkflowNodes()
    edges.value = []
    selectedNode.value = null
    currentWorkflowId.value = null
    workflowName.value = '未命名工作流'
    engineType.value = 'dag'
  }

  // 重置为新建状态
  function resetToNew() {
    nodes.value = createDefaultWorkflowNodes()
    edges.value = []
    selectedNode.value = null
    currentWorkflowId.value = null
    workflowName.value = '未命名工作流'
  }

  // 序列化节点（用于保存）
  function serializeNodes() {
    return nodes.value.map((node) => ({
      id: node.id,
      type: node.data.type,
      position: node.position,
      data: node.data,
    }))
  }

  // 序列化边（用于保存）
  function serializeEdges() {
    return edges.value.map((edge) => ({
      id: edge.id,
      source: edge.source,
      target: edge.target,
      sourceHandle: edge.sourceHandle,
      targetHandle: edge.targetHandle,
    }))
  }

  // 加载工作流数据
  function loadWorkflowData(data: {
    id: number
    name: string
    flowData: string
    engineType?: string
  }) {
    currentWorkflowId.value = data.id
    workflowName.value = data.name
    engineType.value = data.engineType || 'dag'

    try {
      const flowData = JSON.parse(data.flowData)
      if (flowData.nodes) {
        nodes.value = flowData.nodes.map(normalizeNode)
      }
      if (flowData.edges) {
        edges.value = flowData.edges
      }
    } catch (error) {
      console.error('解析工作流数据失败:', error)
    }
  }

  // 获取可引用的节点列表
  function getReferenceableNodes(excludeId?: string) {
    return nodes.value.filter(
      (node) => node.id !== excludeId && node.data.type !== 'output'
    )
  }

  // 获取节点的输出参数
  function getNodeOutputParams(nodeType: string): string[] {
    switch (nodeType) {
      case 'input':
        return ['query', 'input']
      case 'llm':
        return ['output', 'tokens']
      case 'retrieval':
        return ['documents', 'context']
      case 'rerank':
        return ['documents', 'context']
      case 'http':
        return ['response', 'status']
      default:
        return ['output']
    }
  }

  // 获取所有可引用的参数
  function getReferenceableParams(excludeId?: string) {
    const params: { label: string; value: string }[] = []
    getReferenceableNodes(excludeId).forEach((node) => {
      const nodeLabel = node.data.label || node.id
      const outputParams = getNodeOutputParams(node.data.type)
      outputParams.forEach((param) => {
        params.push({
          label: `${nodeLabel}.${param}`,
          value: `${node.id}.${param}`,
        })
      })
    })
    return params
  }

  return {
    // 状态
    nodes,
    edges,
    selectedNode,
    currentWorkflowId,
    workflowName,
    engineType,
    // 计算属性
    hasNodes,
    nodeCount,
    edgeCount,
    // 方法
    setNodes,
    setEdges,
    setSelectedNode,
    setCurrentWorkflowId,
    setWorkflowName,
    setEngineType,
    addNode,
    updateNode,
    updateNodePosition,
    deleteNode,
    addEdge,
    deleteEdge,
    clear,
    resetToNew,
    serializeNodes,
    serializeEdges,
    loadWorkflowData,
    getReferenceableNodes,
    getNodeOutputParams,
    getReferenceableParams,
  }
})
