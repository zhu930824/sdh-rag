<template>
  <div class="graph-page">
    <div class="page-header">
      <h2>知识图谱</h2>
      <a-space>
        <a-input-search
          v-model:value="searchKeyword"
          placeholder="搜索节点..."
          style="width: 280px"
          @search="handleSearch"
        />
        <a-button type="primary" @click="handleRefresh">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
      </a-space>
    </div>

    <GraphToolbar
      :graph="graph"
      @refresh="handleRefresh"
      @layout-change="handleLayoutChange"
    />

    <div class="graph-container">
      <div class="graph-sidebar">
        <GraphFilter @apply="handleFilterApply" />

        <a-card title="统计信息" size="small" :bordered="false" style="margin-top: 12px">
          <a-spin :spinning="statsLoading">
            <a-statistic title="总节点数" :value="stats.totalNodes" />
            <a-statistic title="总关系数" :value="stats.totalRelationships" style="margin-top: 8px" />
            <a-divider style="margin: 12px 0" />
            <div class="stats-chart">
              <div v-for="item in stats.nodesByType" :key="item.type" class="stats-item">
                <span class="stats-label">{{ item.type }}</span>
                <a-progress :percent="(item.count / stats.totalNodes) * 100" :show-info="false" />
                <span class="stats-count">{{ item.count }}</span>
              </div>
            </div>
          </a-spin>
        </a-card>

        <a-card title="图例" size="small" :bordered="false" style="margin-top: 12px">
          <div class="legend-item">
            <span class="legend-dot" style="backgroundColor: #13c2c2"></span>
            <span>文档</span>
          </div>
          <div class="legend-item">
            <span class="legend-dot" style="backgroundColor: #52c41a"></span>
            <span>实体</span>
          </div>
          <div class="legend-item">
            <span class="legend-dot" style="backgroundColor: #eb2f96"></span>
            <span>概念</span>
          </div>
          <div class="legend-item">
            <span class="legend-dot" style="backgroundColor: #faad14"></span>
            <span>关键词</span>
          </div>
        </a-card>
      </div>

      <div class="graph-main">
        <div ref="graphContainerRef" class="graph-canvas"></div>
        <NodeDetail
          :node="selectedNode"
          @close="selectedNode = null"
          @expand="handleExpandNode"
          @find-path="handleFindPath"
        />
      </div>
    </div>

    <!-- 路径查找对话框 -->
    <a-modal
      v-model:open="pathModalVisible"
      title="查找路径"
      @ok="handlePathSearch"
    >
      <a-form layout="vertical">
        <a-form-item label="起始节点">
          <a-select
            v-model:value="pathStartNode"
            show-search
            placeholder="搜索选择起始节点"
            :filter-option="false"
            :not-found-content="pathSearchLoading ? undefined : null"
            @search="handlePathNodeSearch"
          >
            <a-spin v-if="pathSearchLoading" slot="notFoundContent" size="small" />
            <a-select-option v-for="node in pathSearchResults" :key="node.id" :value="node.id">
              {{ node.label }} ({{ node.type }})
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="目标节点">
          <a-input :value="selectedNode?.label" disabled />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { message } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import G6 from '@antv/g6'
import GraphToolbar from './components/GraphToolbar.vue'
import GraphFilter from './components/GraphFilter.vue'
import NodeDetail from './components/NodeDetail.vue'
import {
  getGraphData,
  searchNodes,
  getGraphStats,
  getShortestPath,
  type NodeData,
  type GraphData,
  type GraphStats
} from '@/api/graph'

const graphContainerRef = ref<HTMLElement>()
const searchKeyword = ref('')
const selectedNode = ref<NodeData | null>(null)
const statsLoading = ref(false)
const pathModalVisible = ref(false)
const pathStartNode = ref<number>()
const pathSearchLoading = ref(false)
const pathSearchResults = ref<NodeData[]>([])
const pathEndNode = ref<number>()

let graph: any = null

const stats = reactive<GraphStats>({
  totalNodes: 0,
  totalRelationships: 0,
  nodesByType: [],
  relationshipsByType: []
})

const filters = reactive({
  nodeTypes: ['Document', 'Entity', 'Concept', 'Keyword'],
  relationTypes: ['CONTAINS', 'REFERENCES', 'RELATED_TO', 'INSTANCE_OF'],
  maxNodes: 200
})

// 节点颜色映射
const getNodeColor = (node: NodeData): string => {
  const colors: Record<string, string> = {
    Document: '#13c2c2',
    Entity: '#52c41a',
    Concept: '#eb2f96',
    Keyword: '#faad14'
  }
  return colors[node.type] || '#666'
}

// 边颜色映射
const getEdgeColor = (relationType: string): string => {
  const colors: Record<string, string> = {
    CONTAINS: '#1890ff',
    REFERENCES: '#52c41a',
    RELATED_TO: '#fa8c16',
    INSTANCE_OF: '#eb2f96',
    PARENT_OF: '#faad14'
  }
  return colors[relationType] || '#999'
}

// 加载图谱数据
async function loadGraphData(centerNodeId?: number) {
  try {
    const res = await getGraphData(centerNodeId, 2)
    if (res.code === 200) {
      renderGraph(res.data)
    }
  } catch (error) {
    message.error('加载图谱数据失败')
  }
}

// 加载统计信息
async function loadStats() {
  statsLoading.value = true
  try {
    const res = await getGraphStats()
    if (res.code === 200) {
      Object.assign(stats, res.data)
    }
  } catch (error) {
    console.error('加载统计信息失败', error)
  } finally {
    statsLoading.value = false
  }
}

// 渲染图谱
function renderGraph(data: GraphData) {
  if (!graphContainerRef.value) return

  // 过滤节点和边
  const filteredNodes = data.nodes
    .filter(n => filters.nodeTypes.includes(n.type))
    .slice(0, filters.maxNodes)

  const nodeIds = new Set(filteredNodes.map(n => n.id))
  const filteredEdges = data.edges
    .filter(e => nodeIds.has(e.source) && nodeIds.has(e.target))
    .filter(e => filters.relationTypes.includes(e.relationType))

  // 转换为 G6 数据格式
  const nodes = filteredNodes.map(node => ({
    id: String(node.id),
    label: node.label?.slice(0, 10) || '',
    type: 'circle',
    size: Math.max(20, 20 + (node.weight || node.frequency || 0) * 2),
    style: {
      fill: getNodeColor(node),
      stroke: '#fff',
      lineWidth: 2
    },
    labelCfg: {
      style: {
        fill: '#333',
        fontSize: 11
      },
      position: 'bottom'
    },
    originalData: node
  }))

  const edges = filteredEdges.map(edge => ({
    id: edge.id,
    source: String(edge.source),
    target: String(edge.target),
    type: 'quadratic',
    style: {
      stroke: getEdgeColor(edge.relationType),
      lineWidth: 1.5,
      endArrow: true
    },
    label: edge.relationType,
    labelCfg: {
      style: {
        fill: '#666',
        fontSize: 10
      },
      autoRotate: true
    },
    originalData: edge
  }))

  if (!graph) {
    initGraph()
  }

  graph.changeData({ nodes, edges })
  graph.fitView(20)
}

// 初始化 G6 图
function initGraph() {
  if (!graphContainerRef.value) return

  const width = graphContainerRef.value.clientWidth
  const height = graphContainerRef.value.clientHeight

  graph = new G6.Graph({
    container: graphContainerRef.value,
    width,
    height,
    modes: {
      default: ['drag-canvas', 'zoom-canvas', 'drag-node']
    },
    layout: {
      type: 'force',
      preventOverlap: true,
      linkDistance: 150,
      nodeStrength: -30,
      edgeStrength: 0.1
    },
    defaultNode: {
      type: 'circle',
      size: 30,
      style: {
        lineWidth: 2
      }
    },
    defaultEdge: {
      type: 'quadratic',
      style: {
        endArrow: true
      }
    }
  })

  // 节点点击事件
  graph.on('node:click', (evt: any) => {
    const node = evt.item.getModel()
    selectedNode.value = node.originalData
    highlightNode(node.id)
  })

  // 节点双击事件 - 展开
  graph.on('node:dblclick', (evt: any) => {
    const node = evt.item.getModel()
    handleExpandNode(node.originalData.id)
  })

  // 画布点击事件
  graph.on('canvas:click', () => {
    clearHighlight()
  })

  // 窗口大小变化
  window.addEventListener('resize', handleResize)
}

// 高亮节点
function highlightNode(nodeId: string) {
  graph.getNodes().forEach((node: any) => {
    const model = node.getModel()
    if (model.id === nodeId) {
      graph.updateItem(node, {
        style: { stroke: '#1890ff', lineWidth: 4 }
      })
    } else {
      graph.updateItem(node, {
        style: { stroke: '#fff', lineWidth: 2 }
      })
    }
  })
}

// 清除高亮
function clearHighlight() {
  graph?.getNodes().forEach((node: any) => {
    graph.updateItem(node, {
      style: { stroke: '#fff', lineWidth: 2 }
    })
  })
}

// 处理搜索
async function handleSearch() {
  if (!searchKeyword.value.trim()) {
    loadGraphData()
    return
  }

  try {
    const res = await searchNodes(searchKeyword.value)
    if (res.code === 200 && res.data.length > 0) {
      loadGraphData(res.data[0].id)
    } else {
      message.info('未找到相关节点')
    }
  } catch (error) {
    message.error('搜索失败')
  }
}

// 处理刷新
function handleRefresh() {
  selectedNode.value = null
  loadGraphData()
  loadStats()
}

// 处理布局变化
function handleLayoutChange(layout: string) {
  if (!graph) return

  graph.updateLayout({
    type: layout,
    preventOverlap: true,
    linkDistance: 150
  })
}

// 处理筛选应用
function handleFilterApply(newFilters: typeof filters) {
  Object.assign(filters, newFilters)
  loadGraphData()
}

// 处理节点展开
function handleExpandNode(nodeId: number) {
  loadGraphData(nodeId)
}

// 处理路径查找
function handleFindPath(nodeId: number) {
  pathEndNode.value = nodeId
  pathModalVisible.value = true
}

// 路径节点搜索
async function handlePathNodeSearch(keyword: string) {
  if (!keyword) return

  pathSearchLoading.value = true
  try {
    const res = await searchNodes(keyword)
    if (res.code === 200) {
      pathSearchResults.value = res.data
    }
  } catch (error) {
    console.error('搜索失败', error)
  } finally {
    pathSearchLoading.value = false
  }
}

// 路径搜索
async function handlePathSearch() {
  if (!pathStartNode.value || !pathEndNode.value) {
    message.warning('请选择起始节点')
    return
  }

  try {
    const res = await getShortestPath(pathStartNode.value, pathEndNode.value)
    if (res.code === 200 && res.data) {
      pathModalVisible.value = false
      highlightPath(res.data)
      message.success(`找到路径，长度: ${res.data.length}`)
    } else {
      message.info('未找到路径')
    }
  } catch (error) {
    message.error('路径查找失败')
  }
}

// 高亮路径
function highlightPath(pathData: any) {
  const pathNodeIds = pathData.nodes.map((n: any) => String(n.id))

  graph.getNodes().forEach((node: any) => {
    const model = node.getModel()
    if (pathNodeIds.includes(model.id)) {
      graph.updateItem(node, {
        style: { stroke: '#ff4d4f', lineWidth: 4 }
      })
    } else {
      graph.updateItem(node, {
        style: { stroke: '#fff', lineWidth: 2 }
      })
    }
  })

  graph.getEdges().forEach((edge: any) => {
    const model = edge.getModel()
    const sourceId = model.source
    const targetId = model.target
    const inPath = pathData.relationships.some(
      (r: any) => (String(r.source) === sourceId && String(r.target) === targetId) ||
                   (String(r.source) === targetId && String(r.target) === sourceId)
    )
    graph.updateItem(edge, {
      style: {
        stroke: inPath ? '#ff4d4f' : getEdgeColor(model.originalData?.relationType),
        lineWidth: inPath ? 3 : 1.5
      }
    })
  })
}

// 处理窗口大小变化
function handleResize() {
  if (graphContainerRef.value && graph) {
    graph.changeSize(
      graphContainerRef.value.clientWidth,
      graphContainerRef.value.clientHeight
    )
  }
}

onMounted(async () => {
  await nextTick()
  initGraph()
  loadGraphData()
  loadStats()
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  graph?.destroy()
})
</script>

<style scoped lang="scss">
.graph-page {
  padding: 24px;
  height: calc(100vh - 100px);

  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;

    h2 {
      margin: 0;
      font-size: 20px;
      font-weight: 600;
    }
  }

  .graph-container {
    display: flex;
    gap: 16px;
    height: calc(100% - 110px);

    .graph-sidebar {
      width: 240px;
      flex-shrink: 0;

      .legend-item {
        display: flex;
        align-items: center;
        gap: 8px;
        margin-bottom: 8px;

        .legend-dot {
          width: 12px;
          height: 12px;
          border-radius: 50%;
        }
      }

      .stats-item {
        display: flex;
        align-items: center;
        margin-bottom: 8px;

        .stats-label {
          width: 60px;
          font-size: 12px;
        }

        .stats-count {
          width: 40px;
          text-align: right;
          font-size: 12px;
        }
      }
    }

    .graph-main {
      flex: 1;
      position: relative;
      background-color: var(--bg-color);
      border-radius: var(--border-radius-large);
      overflow: hidden;

      .graph-canvas {
        width: 100%;
        height: 100%;
      }
    }
  }
}

@media (max-width: 768px) {
  .graph-page {
    padding: 16px;

    .graph-container {
      flex-direction: column;

      .graph-sidebar {
        width: 100%;
      }

      .graph-main {
        height: 400px;
      }
    }
  }
}
</style>
