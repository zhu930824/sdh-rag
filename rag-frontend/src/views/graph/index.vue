<template>
  <div class="graph-page">
    <div class="page-header">
      <h2>知识图谱</h2>
      <a-space>
        <a-input-search
          v-model:value="searchKeyword"
          placeholder="搜索节点..."
          style="width: 250px"
          @search="handleSearch"
        />
        <a-button type="primary" @click="handleRefresh">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
      </a-space>
    </div>

    <div class="graph-container">
      <div class="graph-sidebar">
        <a-card title="图例" size="small" :bordered="false">
          <div class="legend-item">
            <span class="legend-dot document"></span>
            <span>文档</span>
          </div>
          <div class="legend-item">
            <span class="legend-dot entity"></span>
            <span>实体</span>
          </div>
          <div class="legend-item">
            <span class="legend-dot concept"></span>
            <span>概念</span>
          </div>
          <div class="legend-item">
            <span class="legend-dot keyword"></span>
            <span>关键词</span>
          </div>
        </a-card>

        <a-card title="节点统计" size="small" :bordered="false" style="margin-top: 12px">
          <a-statistic title="总节点数" :value="stats.totalNodes" />
          <a-statistic title="总关系数" :value="stats.totalEdges" style="margin-top: 8px" />
        </a-card>

        <a-card title="关系类型" size="small" :bordered="false" style="margin-top: 12px">
          <a-tag color="blue">引用 references</a-tag>
          <a-tag color="green">包含 contains</a-tag>
          <a-tag color="orange">相关 related_to</a-tag>
          <a-tag color="purple">父节点 parent_of</a-tag>
        </a-card>
      </div>

      <div class="graph-main">
        <div ref="graphRef" class="graph-canvas"></div>
        
        <div v-if="selectedNode" class="node-info">
          <a-card :title="selectedNode.name" size="small" :bordered="false">
            <template #extra>
              <a-button type="text" size="small" @click="selectedNode = null">
                <CloseOutlined />
              </a-button>
            </template>
            <p><strong>类型：</strong>{{ getNodeTypeText(selectedNode.nodeType) }}</p>
            <p v-if="selectedNode.description"><strong>描述：</strong>{{ selectedNode.description }}</p>
            <p><strong>权重：</strong>{{ selectedNode.weight }}</p>
            <p><strong>关系数：</strong>{{ getNodeEdgeCount(selectedNode.id) }}</p>
            <a-button type="link" size="small" @click="handleExpandNode(selectedNode)">
              展开关系
            </a-button>
          </a-card>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { message } from 'ant-design-vue'
import { ReloadOutlined, CloseOutlined } from '@ant-design/icons-vue'
import { getGraphData, searchNodes, type GraphNode, type GraphEdge, type GraphData } from '@/api/graph'

const graphRef = ref<HTMLElement>()
const searchKeyword = ref('')
const selectedNode = ref<GraphNode | null>(null)

const graphData = ref<GraphData>({ nodes: [], edges: [] })
const stats = reactive({
  totalNodes: 0,
  totalEdges: 0
})

let canvas: HTMLCanvasElement | null = null
let ctx: CanvasRenderingContext2D | null = null
let nodePositions: Map<number, { x: number; y: number }> = new Map()
let animationId: number | null = null

function getNodeTypeText(type: string): string {
  const map: Record<string, string> = {
    document: '文档',
    entity: '实体',
    concept: '概念',
    keyword: '关键词'
  }
  return map[type] || type
}

function getNodeColor(type: string): string {
  const map: Record<string, string> = {
    document: '#1890ff',
    entity: '#52c41a',
    concept: '#fa8c16',
    keyword: '#722ed1'
  }
  return map[type] || '#666'
}

function getNodeEdgeCount(nodeId: number): number {
  return graphData.value.edges.filter(
    e => e.sourceId === nodeId || e.targetId === nodeId
  ).length
}

async function loadGraphData(centerNodeId?: number) {
  try {
    const res = await getGraphData(centerNodeId, 2)
    if (res.code === 200) {
      graphData.value = res.data || { nodes: [], edges: [] }
      stats.totalNodes = graphData.value.nodes.length
      stats.totalEdges = graphData.value.edges.length
      await nextTick()
      initGraph()
    }
  } catch (error) {
    message.error('加载图谱数据失败')
  }
}

async function handleSearch() {
  if (!searchKeyword.value.trim()) {
    loadGraphData()
    return
  }
  
  try {
    const res = await searchNodes(searchKeyword.value)
    if (res.code === 200 && res.data?.length > 0) {
      loadGraphData(res.data[0].id)
    } else {
      message.info('未找到相关节点')
    }
  } catch (error) {
    message.error('搜索失败')
  }
}

function handleRefresh() {
  loadGraphData()
}

function handleExpandNode(node: GraphNode) {
  loadGraphData(node.id)
}

function initGraph() {
  if (!graphRef.value) return
  
  const width = graphRef.value.clientWidth
  const height = graphRef.value.clientHeight
  
  if (!canvas) {
    canvas = document.createElement('canvas')
    canvas.width = width
    canvas.height = height
    canvas.style.width = '100%'
    canvas.style.height = '100%'
    graphRef.value.appendChild(canvas)
    ctx = canvas.getContext('2d')
    
    canvas.addEventListener('click', handleCanvasClick)
  }
  
  calculatePositions(width, height)
  render()
}

function calculatePositions(width: number, height: number) {
  const nodes = graphData.value.nodes
  const centerX = width / 2
  const centerY = height / 2
  const radius = Math.min(width, height) * 0.35
  
  nodePositions.clear()
  
  nodes.forEach((node, index) => {
    const angle = (2 * Math.PI * index) / nodes.length
    const x = centerX + radius * Math.cos(angle)
    const y = centerY + radius * Math.sin(angle)
    nodePositions.set(node.id, { x, y })
  })
}

function render() {
  if (!ctx || !canvas) return
  
  ctx.clearRect(0, 0, canvas.width, canvas.height)
  
  // Draw edges
  graphData.value.edges.forEach(edge => {
    const source = nodePositions.get(edge.sourceId)
    const target = nodePositions.get(edge.targetId)
    if (source && target) {
      ctx.beginPath()
      ctx.moveTo(source.x, source.y)
      ctx.lineTo(target.x, target.y)
      ctx.strokeStyle = getEdgeColor(edge.relationType)
      ctx.lineWidth = Math.max(1, edge.weight * 2)
      ctx.stroke()
    }
  })
  
  // Draw nodes
  graphData.value.nodes.forEach(node => {
    const pos = nodePositions.get(node.id)
    if (pos) {
      const radius = Math.max(8, 10 + node.weight * 2)
      
      ctx.beginPath()
      ctx.arc(pos.x, pos.y, radius, 0, 2 * Math.PI)
      ctx.fillStyle = getNodeColor(node.nodeType)
      ctx.fill()
      
      if (selectedNode.value?.id === node.id) {
        ctx.strokeStyle = '#ff4d4f'
        ctx.lineWidth = 3
        ctx.stroke()
      }
      
      // Draw label
      ctx.fillStyle = '#333'
      ctx.font = '12px sans-serif'
      ctx.textAlign = 'center'
      ctx.fillText(node.name.slice(0, 10), pos.x, pos.y + radius + 15)
    }
  })
}

function getEdgeColor(type: string): string {
  const map: Record<string, string> = {
    references: '#1890ff',
    contains: '#52c41a',
    related_to: '#fa8c16',
    parent_of: '#722ed1'
  }
  return map[type] || '#999'
}

function handleCanvasClick(event: MouseEvent) {
  if (!canvas) return
  
  const rect = canvas.getBoundingClientRect()
  const x = event.clientX - rect.left
  const y = event.clientY - rect.top
  
  for (const node of graphData.value.nodes) {
    const pos = nodePositions.get(node.id)
    if (pos) {
      const radius = Math.max(8, 10 + node.weight * 2)
      const distance = Math.sqrt((x - pos.x) ** 2 + (y - pos.y) ** 2)
      if (distance <= radius) {
        selectedNode.value = node
        render()
        return
      }
    }
  }
  
  selectedNode.value = null
  render()
}

onMounted(() => {
  loadGraphData()
})

onUnmounted(() => {
  if (animationId) {
    cancelAnimationFrame(animationId)
  }
  if (canvas) {
    canvas.removeEventListener('click', handleCanvasClick)
  }
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
    margin-bottom: 24px;

    h2 {
      margin: 0;
      font-size: 20px;
      font-weight: 600;
    }
  }

  .graph-container {
    display: flex;
    gap: 16px;
    height: calc(100% - 60px);

    .graph-sidebar {
      width: 220px;
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

          &.document { background-color: #1890ff; }
          &.entity { background-color: #52c41a; }
          &.concept { background-color: #fa8c16; }
          &.keyword { background-color: #722ed1; }
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
        cursor: pointer;
      }

      .node-info {
        position: absolute;
        top: 16px;
        right: 16px;
        width: 280px;
        box-shadow: var(--box-shadow);
        border-radius: var(--border-radius-base);
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