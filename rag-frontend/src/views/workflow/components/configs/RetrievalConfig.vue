<template>
  <div class="config-section">
    <div class="section-title">检索配置</div>

    <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 18 }">
      <!-- 知识库选择 -->
      <a-form-item label="知识库">
        <a-select
          v-model:value="knowledgeBaseId"
          placeholder="选择知识库"
          size="small"
          show-search
          :filter-option="filterOption"
          @change="handleKbChange"
        >
          <a-select-option v-for="kb in knowledgeBases" :key="kb.id" :value="kb.id">
            {{ kb.name }}
          </a-select-option>
        </a-select>
      </a-form-item>

      <!-- 检索数量 -->
      <a-form-item label="检索数量">
        <a-input-number
          v-model:value="topK"
          :min="1"
          :max="20"
          size="small"
          style="width: 100%"
        />
      </a-form-item>

      <!-- 相似度阈值 -->
      <a-form-item label="相似度阈值">
        <a-slider v-model:value="scoreThreshold" :min="0" :max="1" :step="0.05" />
        <span class="slider-value">{{ scoreThreshold }}</span>
      </a-form-item>
    </a-form>

    <!-- 输入参数 -->
    <div class="param-section">
      <div class="param-header">
        <span>查询参数</span>
      </div>
      <div class="param-tip">
        检索节点会从前置节点获取查询文本，使用检索结果时引用 documents 或 context 参数
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useWorkflowStore, type WorkflowNodeData } from '@/stores/workflow'
import type { Node } from '@vue-flow/core'
import { getKnowledgeBaseList, type KnowledgeBase } from '@/api/knowledgeBase'

const props = defineProps<{
  node: Node<WorkflowNodeData>
}>()

const emit = defineEmits<{
  (e: 'update', data: Partial<WorkflowNodeData>): void
}>()

const knowledgeBases = ref<KnowledgeBase[]>([])

const knowledgeBaseId = computed({
  get: () => props.node.data.knowledgeBaseId,
  set: (val) => emit('update', { knowledgeBaseId: val }),
})

const topK = computed({
  get: () => props.node.data.topK || 5,
  set: (val) => emit('update', { topK: val }),
})

const scoreThreshold = computed({
  get: () => props.node.data.scoreThreshold || 0.7,
  set: (val) => emit('update', { scoreThreshold: val }),
})

function filterOption(input: string, option: any) {
  return option.children?.[0]?.children?.toLowerCase().includes(input.toLowerCase())
}

function handleKbChange(kbId: number) {
  const kb = knowledgeBases.value.find((k) => k.id === kbId)
  if (kb) {
    emit('update', { knowledgeBaseId: kbId, knowledgeBaseName: kb.name })
  }
}

async function loadKnowledgeBases() {
  try {
    const res = await getKnowledgeBaseList({ page: 1, pageSize: 100 })
    if (res.data?.records) {
      knowledgeBases.value = res.data.records
    }
  } catch (error) {
    console.error('加载知识库失败:', error)
  }
}

onMounted(() => {
  loadKnowledgeBases()
})
</script>

<style scoped lang="scss">
.config-section {
  .section-title {
    font-size: 13px;
    font-weight: 500;
    color: #262626;
    margin-bottom: 12px;
    padding-bottom: 8px;
    border-bottom: 1px solid #f0f0f0;
  }
}

.slider-value {
  font-size: 12px;
  color: #8c8c8c;
  margin-left: 8px;
}

.param-section {
  margin-top: 16px;
}

.param-header {
  font-size: 13px;
  color: #595959;
  margin-bottom: 8px;
}

.param-tip {
  font-size: 12px;
  color: #8c8c8c;
  background: #fafafa;
  padding: 8px;
  border-radius: 4px;
}
</style>
