<template>
  <div class="graph-filter">
    <a-card title="筛选" size="small" :bordered="false">
      <div class="filter-section">
        <div class="filter-label">节点类型</div>
        <a-checkbox-group v-model:value="selectedNodeTypes">
          <a-checkbox v-for="item in nodeTypes" :key="item.value" :value="item.value">
            <span class="node-type-dot" :style="{ backgroundColor: item.color }"></span>
            {{ item.label }}
          </a-checkbox>
        </a-checkbox-group>
      </div>

      <div class="filter-section">
        <div class="filter-label">关系类型</div>
        <a-checkbox-group v-model:value="selectedRelationTypes">
          <a-checkbox v-for="item in relationTypes" :key="item.value" :value="item.value">
            {{ item.label }}
          </a-checkbox>
        </a-checkbox-group>
      </div>

      <div class="filter-section">
        <div class="filter-label">显示数量</div>
        <a-slider v-model:value="maxNodes" :min="50" :max="500" :step="50" />
        <div class="slider-label">最多显示 {{ maxNodes }} 个节点</div>
      </div>

      <a-button type="primary" block @click="handleApply">应用筛选</a-button>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'

const emit = defineEmits<{
  apply: [filters: { nodeTypes: string[]; relationTypes: string[]; maxNodes: number }]
}>()

const nodeTypes = [
  { label: '文档', value: 'Document', color: '#13c2c2' },
  { label: '实体', value: 'Entity', color: '#52c41a' },
  { label: '概念', value: 'Concept', color: '#eb2f96' },
  { label: '关键词', value: 'Keyword', color: '#faad14' }
]

const relationTypes = [
  { label: '包含', value: 'CONTAINS' },
  { label: '引用', value: 'REFERENCES' },
  { label: '相关', value: 'RELATED_TO' },
  { label: '实例', value: 'INSTANCE_OF' }
]

const selectedNodeTypes = ref(['Document', 'Entity', 'Concept', 'Keyword'])
const selectedRelationTypes = ref(['CONTAINS', 'REFERENCES', 'RELATED_TO', 'INSTANCE_OF'])
const maxNodes = ref(200)

const handleApply = () => {
  emit('apply', {
    nodeTypes: selectedNodeTypes.value,
    relationTypes: selectedRelationTypes.value,
    maxNodes: maxNodes.value
  })
}

onMounted(() => {
  handleApply()
})
</script>

<style scoped lang="scss">
.graph-filter {
  .filter-section {
    margin-bottom: 16px;

    .filter-label {
      font-weight: 500;
      margin-bottom: 8px;
      color: var(--text-color);
    }
  }

  .node-type-dot {
    display: inline-block;
    width: 10px;
    height: 10px;
    border-radius: 50%;
    margin-right: 4px;
  }

  .slider-label {
    text-align: center;
    color: var(--text-color-secondary);
    font-size: 12px;
  }

  :deep(.ant-checkbox-wrapper) {
    display: block;
    margin-bottom: 4px;
  }
}
</style>
