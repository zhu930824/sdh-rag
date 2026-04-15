<template>
  <div class="config-section">
    <div class="section-title">重排序配置</div>

    <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 18 }">
      <!-- 模型选择 -->
      <a-form-item label="重排序模型">
        <a-select
          v-model:value="model"
          placeholder="选择重排序模型"
          size="small"
          :loading="modelLoading"
          show-search
          :filter-option="filterModelOption"
        >
          <a-select-option
            v-for="modelItem in rerankModels"
            :key="modelItem.id"
            :value="modelItem.modelId"
          >
            {{ modelItem.name }}
            <span class="model-id-hint">({{ modelItem.modelId }})</span>
          </a-select-option>
        </a-select>
      </a-form-item>

      <!-- 返回数量 -->
      <a-form-item label="返回数量">
        <a-input-number
          v-model:value="topK"
          :min="1"
          :max="20"
          size="small"
          style="width: 100%"
        />
      </a-form-item>
    </a-form>

    <!-- 输入参数 -->
    <div class="param-section">
      <div class="param-header">
        <span>输入参数</span>
      </div>
      <div class="param-tip">
        重排序节点需要从前置节点获取 documents 和 query 参数，输出重排序后的 documents 和 context
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useWorkflowStore, type WorkflowNodeData } from '@/stores/workflow'
import { getActiveRerankModels } from '@/api/model'
import type { ModelConfig } from '@/types'
import type { Node } from '@vue-flow/core'

const props = defineProps<{
  node: Node<WorkflowNodeData>
}>()

const emit = defineEmits<{
  (e: 'update', data: Partial<WorkflowNodeData>): void
}>()

const workflowStore = useWorkflowStore()

// 模型列表
const rerankModels = ref<{ id: number; name: string; modelId: string }[]>([])
const modelLoading = ref(false)

// 加载可用重排序模型列表
async function loadRerankModels() {
  modelLoading.value = true
  try {
    const res = await getActiveRerankModels()
    if (res.code === 200 && res.data) {
      rerankModels.value = res.data.map(m => ({
        id: m.id,
        name: m.name,
        modelId: m.modelId,
      }))
    }
  } catch (error) {
    console.error('加载重排序模型列表失败', error)
  } finally {
    modelLoading.value = false
  }
}

// 模型筛选
function filterModelOption(input: string, option: any) {
  const modelItem = rerankModels.value.find(m => m.modelId === option.value)
  if (!modelItem) return false
  const searchStr = input.toLowerCase()
  return modelItem.name.toLowerCase().includes(searchStr) ||
         modelItem.modelId.toLowerCase().includes(searchStr)
}

const model = computed({
  get: () => props.node.data.model || '',
  set: (val) => emit('update', { model: val }),
})

const topK = computed({
  get: () => props.node.data.topK || 5,
  set: (val) => emit('update', { topK: val }),
})

onMounted(() => {
  loadRerankModels()
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

.model-id-hint {
  font-size: 12px;
  color: #8c8c8c;
  margin-left: 4px;
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
