<template>
  <div class="graph-toolbar">
    <a-space>
      <a-select
        v-model:value="selectedLayout"
        style="width: 120px"
        @change="handleLayoutChange"
      >
        <a-select-option value="force">力导向布局</a-select-option>
        <a-select-option value="circular">环形布局</a-select-option>
        <a-select-option value="radial">辐射布局</a-select-option>
        <a-select-option value="tree">树形布局</a-select-option>
      </a-select>

      <a-divider type="vertical" />

      <a-tooltip title="放大">
        <a-button @click="handleZoomIn">
          <template #icon><ZoomInOutlined /></template>
        </a-button>
      </a-tooltip>

      <a-tooltip title="缩小">
        <a-button @click="handleZoomOut">
          <template #icon><ZoomOutOutlined /></template>
        </a-button>
      </a-tooltip>

      <a-tooltip title="适应画布">
        <a-button @click="handleFitView">
          <template #icon><FullscreenOutlined /></template>
        </a-button>
      </a-tooltip>

      <a-divider type="vertical" />

      <a-tooltip title="刷新">
        <a-button @click="handleRefresh">
          <template #icon><ReloadOutlined /></template>
        </a-button>
      </a-tooltip>

      <a-tooltip title="下载图片">
        <a-button @click="handleDownload">
          <template #icon><DownloadOutlined /></template>
        </a-button>
      </a-tooltip>
    </a-space>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ZoomInOutlined, ZoomOutOutlined, FullscreenOutlined, ReloadOutlined, DownloadOutlined } from '@ant-design/icons-vue'

const props = defineProps<{
  graph?: any
}>()

const emit = defineEmits<{
  refresh: []
  layoutChange: [layout: string]
}>()

const selectedLayout = ref('force')

const handleLayoutChange = () => {
  emit('layoutChange', selectedLayout.value)
}

const handleZoomIn = () => {
  if (props.graph) {
    const zoom = props.graph.getZoom()
    props.graph.zoomTo(zoom * 1.2)
  }
}

const handleZoomOut = () => {
  if (props.graph) {
    const zoom = props.graph.getZoom()
    props.graph.zoomTo(zoom / 1.2)
  }
}

const handleFitView = () => {
  if (props.graph) {
    props.graph.fitView(20)
  }
}

const handleRefresh = () => {
  emit('refresh')
}

const handleDownload = () => {
  if (props.graph) {
    props.graph.downloadFullImage('knowledge-graph', 'image/png', {
      backgroundColor: '#fff',
      padding: 20
    })
  }
}
</script>

<style scoped lang="scss">
.graph-toolbar {
  padding: 12px 16px;
  background: var(--bg-color);
  border-radius: var(--border-radius-base);
}
</style>
