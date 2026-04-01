<template>
  <div v-if="node" class="node-detail">
    <a-card :title="node.label" size="small" :bordered="false">
      <template #extra>
        <a-button type="text" size="small" @click="handleClose">
          <CloseOutlined />
        </a-button>
      </template>

      <a-descriptions :column="1" size="small">
        <a-descriptions-item label="类型">
          <a-tag :color="getTypeColor(node.type)">
            {{ getTypeText(node.type) }}
          </a-tag>
          <a-tag v-if="node.entityType" color="blue" style="margin-left: 4px">
            {{ node.entityType }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item v-if="node.description" label="描述">
          {{ node.description }}
        </a-descriptions-item>
        <a-descriptions-item v-if="node.weight" label="权重">
          {{ node.weight }}
        </a-descriptions-item>
        <a-descriptions-item v-if="node.frequency" label="频率">
          {{ node.frequency }}
        </a-descriptions-item>
        <a-descriptions-item v-if="node.documentId" label="文档ID">
          {{ node.documentId }}
        </a-descriptions-item>
      </a-descriptions>

      <a-divider style="margin: 12px 0" />

      <a-space direction="vertical" style="width: 100%">
        <a-button type="primary" block size="small" @click="handleExpand">
          <template #icon><ExpandOutlined /></template>
          展开关系
        </a-button>
        <a-button block size="small" @click="handleFindPath">
          <template #icon><NodeIndexOutlined /></template>
          查找路径
        </a-button>
      </a-space>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { CloseOutlined, ExpandOutlined, NodeIndexOutlined } from '@ant-design/icons-vue'
import type { NodeData } from '@/api/graph'

const props = defineProps<{
  node: NodeData | null
}>()

const emit = defineEmits<{
  close: []
  expand: [nodeId: number]
  findPath: [nodeId: number]
}>()

const getTypeColor = (type: string): string => {
  const colors: Record<string, string> = {
    Document: 'cyan',
    Entity: 'green',
    Concept: 'magenta',
    Keyword: 'gold'
  }
  return colors[type] || 'default'
}

const getTypeText = (type: string): string => {
  const texts: Record<string, string> = {
    Document: '文档',
    Entity: '实体',
    Concept: '概念',
    Keyword: '关键词'
  }
  return texts[type] || type
}

const handleClose = () => {
  emit('close')
}

const handleExpand = () => {
  if (props.node) {
    emit('expand', props.node.id)
  }
}

const handleFindPath = () => {
  if (props.node) {
    emit('findPath', props.node.id)
  }
}
</script>

<style scoped lang="scss">
.node-detail {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 280px;
  z-index: 10;
  box-shadow: var(--box-shadow);
  border-radius: var(--border-radius-base);
}
</style>
