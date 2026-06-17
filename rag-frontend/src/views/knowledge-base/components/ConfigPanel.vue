<template>
  <div class="config-panel">
    <a-collapse v-model:activeKey="activeKeys" :bordered="false">
      <!-- 切分配置 -->
      <a-collapse-panel key="chunk" header="切分配置">
        <a-form
          :model="form"
          :label-col="{ span: 6 }"
          :wrapper-col="{ span: 14 }"
        >
          <a-form-item label="分块大小" name="chunkSize">
            <a-input-number
              v-model:value="form.chunkSize"
              :min="100"
              :max="5000"
              :step="100"
              style="width: 200px"
            />
            <span class="form-hint">字符数，推荐 500-1000</span>
          </a-form-item>

          <a-form-item label="分块重叠" name="chunkOverlap">
            <a-input-number
              v-model:value="form.chunkOverlap"
              :min="0"
              :max="500"
              :step="10"
              style="width: 200px"
            />
            <span class="form-hint">字符数，推荐 50-100</span>
          </a-form-item>

          <a-form-item label="嵌入模型" name="embeddingModel">
            <a-select
              v-model:value="form.embeddingModel"
              style="width: 300px"
              placeholder="请选择嵌入模型"
              :loading="loadingModels"
              allow-clear
            >
              <a-select-option
                v-for="model in embeddingModels"
                :key="model.id"
                :value="model.modelId"
              >
                {{ model.name }} ({{ model.modelId }})
              </a-select-option>
            </a-select>
          </a-form-item>
        </a-form>
      </a-collapse-panel>

      <!-- 检索配置 -->
      <a-collapse-panel key="retrieval" header="检索配置">
        <a-form
          :model="form"
          :label-col="{ span: 6 }"
          :wrapper-col="{ span: 14 }"
        >
          <a-form-item label="重排序模型" name="rankModel">
            <a-select
              v-model:value="form.rankModel"
              style="width: 300px"
              allow-clear
              placeholder="不启用重排序"
              :loading="loadingModels"
            >
              <a-select-option
                v-for="model in rerankerModels"
                :key="model.id"
                :value="model.modelId"
              >
                {{ model.name }} ({{ model.modelId }})
              </a-select-option>
            </a-select>
          </a-form-item>

          <a-form-item label="多轮对话改写" name="enableRewrite">
            <a-switch v-model:checked="form.enableRewrite" />
            <span class="form-hint">根据对话历史改写查询，提高多轮对话检索效果</span>
          </a-form-item>

          <a-form-item label="查询扩展" name="enableQueryExpansion">
            <a-switch v-model:checked="form.enableQueryExpansion" />
            <span class="form-hint">生成多个语义相关的查询变体，提升召回率</span>
          </a-form-item>

          <a-form-item v-if="form.enableQueryExpansion" label="扩展数量" name="queryExpansionCount">
            <a-input-number
              v-model:value="form.queryExpansionCount"
              :min="1"
              :max="5"
              style="width: 200px"
            />
            <span class="form-hint">生成查询变体的数量，推荐 2-3 个</span>
          </a-form-item>

          <a-form-item label="HyDE 检索增强" name="enableHyde">
            <a-switch v-model:checked="form.enableHyde" />
            <span class="form-hint">生成假设性答案文档用于检索，缓解查询-文档语义不匹配</span>
          </a-form-item>

          <a-form-item v-if="form.enableHyde" label="HyDE 模型" name="hydeModel">
            <a-select
              v-model:value="form.hydeModel"
              style="width: 300px"
              allow-clear
              placeholder="默认使用聊天模型"
              :loading="loadingModels"
            >
              <a-select-option
                v-for="model in chatModels"
                :key="model.id"
                :value="model.modelId"
              >
                {{ model.name }} ({{ model.modelId }})
              </a-select-option>
            </a-select>
            <span class="form-hint">用于生成假设性文档的模型</span>
          </a-form-item>

          <a-divider />
          <a-collapse ghost>
            <a-collapse-panel key="advanced" header="🔧 高级检索优化">
              <a-space direction="vertical" style="width: 100%">
                <!-- 摘要索引 -->
                <a-form-item label="摘要索引">
                  <a-switch v-model:checked="form.enableSummaryIndex" />
                  <span class="form-hint">入库时自动生成文档摘要，用于快速粗筛</span>
                </a-form-item>

                <a-form-item v-if="form.enableSummaryIndex" label="粗筛文档数">
                  <a-slider v-model:value="form.summaryCoarseTopK"
                    :min="10" :max="200" :step="10" style="width: 200px" />
                  <span class="form-hint">{{ form.summaryCoarseTopK }} 个文档</span>
                </a-form-item>

                <!-- 冷热分离 -->
                <a-form-item label="冷热分离">
                  <a-switch v-model:checked="form.enableColdArchive" />
                  <span class="form-hint">6个月前的文档自动归档冷库，热库不足时触发检索</span>
                </a-form-item>

                <a-form-item v-if="form.enableColdArchive" label="热库最低结果数">
                  <a-input-number v-model:value="form.hotMinResults"
                    :min="1" :max="10" />
                  <span class="form-hint">热库结果少于此数自动触发冷库检索</span>
                </a-form-item>

                <!-- MMR 配置 -->
                <a-form-item label="MMR 多样性">
                  <a-slider v-model:value="form.mmrLambda"
                    :min="0.1" :max="1.0" :step="0.05" style="width: 200px" />
                  <span class="form-hint">λ={{ form.mmrLambda }}（越大越偏向相关性）</span>
                </a-form-item>

                <!-- 时间衰减 -->
                <a-form-item label="时间衰减系数">
                  <a-slider v-model:value="form.recencyAlpha"
                    :min="0.0" :max="0.5" :step="0.05" style="width: 200px" />
                  <span class="form-hint">α={{ form.recencyAlpha }}（越大新文档权重越高）</span>
                </a-form-item>

              </a-space>
            </a-collapse-panel>
          </a-collapse>

          <a-form-item label="相似度阈值" name="similarityThreshold">
            <a-slider
              v-model:value="form.similarityThreshold"
              :min="0"
              :max="1"
              :step="0.05"
              style="width: 200px"
            />
            <span class="form-hint">{{ (form.similarityThreshold * 100).toFixed(0) }}%</span>
          </a-form-item>

          <a-form-item label="关键字TopK" name="keywordTopK">
            <a-input-number
              v-model:value="form.keywordTopK"
              :min="1"
              :max="50"
              style="width: 200px"
            />
            <span class="form-hint">关键字检索返回数量</span>
          </a-form-item>

          <a-form-item label="向量TopK" name="vectorTopK">
            <a-input-number
              v-model:value="form.vectorTopK"
              :min="1"
              :max="50"
              style="width: 200px"
            />
            <span class="form-hint">向量检索返回数量</span>
          </a-form-item>

          <a-form-item label="检索权重配置">
            <div class="weight-config">
              <div class="weight-item">
                <span>关键字权重:</span>
                <a-input-number
                  v-model:value="form.keywordWeight"
                  :min="0"
                  :max="1"
                  :step="0.1"
                  style="width: 100px"
                />
              </div>
              <div class="weight-item">
                <span>向量权重:</span>
                <a-input-number
                  v-model:value="form.vectorWeight"
                  :min="0"
                  :max="1"
                  :step="0.1"
                  style="width: 100px"
                />
              </div>
            </div>
            <div class="form-hint">两者之和应为1，系统会自动归一化</div>
          </a-form-item>
        </a-form>
      </a-collapse-panel>
    </a-collapse>

    <div class="actions">
      <a-space>
        <a-button type="primary" :loading="saving" @click="handleSave">保存配置</a-button>
        <a-button @click="handleReset">重置</a-button>
      </a-space>
    </div>

    <a-divider />

    <a-alert
      message="配置修改说明"
      description="修改分块配置后，新关联的文档将使用新配置进行处理。已处理的文档需要重新处理才能应用新配置。检索配置即时生效。"
      type="info"
      show-icon
    />
  </div>
</template>

<script setup lang="ts">
import { reactive, watch, ref, onMounted, computed } from 'vue'
import type { KnowledgeBase } from '@/api/knowledgeBase'
import { getActiveModels } from '@/api/model'
import type { ModelConfig } from '@/types'
import { DOC_TYPES } from '@/types/search'

const props = defineProps<{
  knowledgeBase: KnowledgeBase | null
}>()

const emit = defineEmits<{
  (e: 'save', config: {
    chunkSize: number
    chunkOverlap: number
    embeddingModel: string
    rankModel?: string
    enableRewrite?: boolean
    enableQueryExpansion?: boolean
    queryExpansionCount?: number
    enableHyde?: boolean
    hydeModel?: string
    similarityThreshold?: number
    keywordTopK?: number
    vectorTopK?: number
    keywordWeight?: number
    vectorWeight?: number
    enableSummaryIndex?: boolean
    summaryCoarseTopK?: number
    enableColdArchive?: boolean
    hotMinResults?: number
    mmrLambda?: number
    recencyAlpha?: number
  }): void
}>()

const saving = ref(false)
const activeKeys = ref(['chunk', 'retrieval'])
const allModels = ref<ModelConfig[]>([])
const loadingModels = ref(false)

// 嵌入模型列表
const embeddingModels = computed(() => {
  return allModels.value.filter(m => m.modelType === 'embedding' && m.status === 1)
})

// 重排模型列表
const rerankerModels = computed(() => {
  return allModels.value.filter(m => m.modelType === 'reranker' && m.status === 1)
})

// 聊天模型列表（用于 HyDE）
const chatModels = computed(() => {
  return allModels.value.filter(m => m.modelType === 'chat' && m.status === 1)
})

const form = reactive({
  chunkSize: 500,
  chunkOverlap: 50,
  embeddingModel: '',
  rankModel: '',
  enableRewrite: false,
  enableQueryExpansion: false,
  queryExpansionCount: 3,
  enableHyde: false,
  hydeModel: '',
  similarityThreshold: 0.7,
  keywordTopK: 10,
  vectorTopK: 10,
  keywordWeight: 0.3,
  vectorWeight: 0.7,
  enableSummaryIndex: true,
  summaryCoarseTopK: 50,
  enableColdArchive: true,
  hotMinResults: 3,
  mmrLambda: 0.7,
  recencyAlpha: 0.3,
})

// 加载模型列表
async function loadModels() {
  loadingModels.value = true
  try {
    const res = await getActiveModels()
    if (res.code === 200) {
      allModels.value = res.data || []
    }
  } catch (error) {
    console.error('加载模型列表失败', error)
  } finally {
    loadingModels.value = false
  }
}

watch(() => props.knowledgeBase, (kb) => {
  if (kb) {
    form.chunkSize = kb.chunkSize || 500
    form.chunkOverlap = kb.chunkOverlap || 50
    form.embeddingModel = kb.embeddingModel || ''
    form.rankModel = kb.rankModel || ''
    form.enableRewrite = kb.enableRewrite || false
    form.enableQueryExpansion = kb.enableQueryExpansion || false
    form.queryExpansionCount = kb.queryExpansionCount || 3
    form.enableHyde = kb.enableHyde || false
    form.hydeModel = kb.hydeModel || ''
    form.similarityThreshold = kb.similarityThreshold || 0.7
    form.keywordTopK = kb.keywordTopK || 10
    form.vectorTopK = kb.vectorTopK || 10
    form.keywordWeight = kb.keywordWeight || 0.3
    form.vectorWeight = kb.vectorWeight || 0.7
    form.enableSummaryIndex = kb.enableSummaryIndex ?? true
    form.summaryCoarseTopK = kb.summaryCoarseTopK || 50
    form.enableColdArchive = kb.enableColdArchive ?? true
    form.hotMinResults = kb.hotMinResults || 3
    form.mmrLambda = kb.mmrLambda || 0.7
    form.recencyAlpha = kb.recencyAlpha || 0.3
  }
}, { immediate: true })

function handleSave() {
  emit('save', {
    chunkSize: form.chunkSize,
    chunkOverlap: form.chunkOverlap,
    embeddingModel: form.embeddingModel,
    rankModel: form.rankModel || undefined,
    enableRewrite: form.enableRewrite,
    enableQueryExpansion: form.enableQueryExpansion,
    queryExpansionCount: form.queryExpansionCount,
    enableHyde: form.enableHyde,
    hydeModel: form.hydeModel || undefined,
    similarityThreshold: form.similarityThreshold,
    keywordTopK: form.keywordTopK,
    vectorTopK: form.vectorTopK,
    keywordWeight: form.keywordWeight,
    vectorWeight: form.vectorWeight,
    enableSummaryIndex: form.enableSummaryIndex,
    summaryCoarseTopK: form.summaryCoarseTopK,
    enableColdArchive: form.enableColdArchive,
    hotMinResults: form.hotMinResults,
    mmrLambda: form.mmrLambda,
    recencyAlpha: form.recencyAlpha,
  })
}

function handleReset() {
  if (props.knowledgeBase) {
    form.chunkSize = props.knowledgeBase.chunkSize || 500
    form.chunkOverlap = props.knowledgeBase.chunkOverlap || 50
    form.embeddingModel = props.knowledgeBase.embeddingModel || ''
    form.rankModel = props.knowledgeBase.rankModel || ''
    form.enableRewrite = props.knowledgeBase.enableRewrite || false
    form.enableQueryExpansion = props.knowledgeBase.enableQueryExpansion || false
    form.queryExpansionCount = props.knowledgeBase.queryExpansionCount || 3
    form.enableHyde = props.knowledgeBase.enableHyde || false
    form.hydeModel = props.knowledgeBase.hydeModel || ''
    form.similarityThreshold = props.knowledgeBase.similarityThreshold || 0.7
    form.keywordTopK = props.knowledgeBase.keywordTopK || 10
    form.vectorTopK = props.knowledgeBase.vectorTopK || 10
    form.keywordWeight = props.knowledgeBase.keywordWeight || 0.3
    form.vectorWeight = props.knowledgeBase.vectorWeight || 0.7
    form.enableSummaryIndex = props.knowledgeBase.enableSummaryIndex ?? true
    form.summaryCoarseTopK = props.knowledgeBase.summaryCoarseTopK || 50
    form.enableColdArchive = props.knowledgeBase.enableColdArchive ?? true
    form.hotMinResults = props.knowledgeBase.hotMinResults || 3
    form.mmrLambda = props.knowledgeBase.mmrLambda || 0.7
    form.recencyAlpha = props.knowledgeBase.recencyAlpha || 0.3
  }
}

onMounted(() => {
  loadModels()
})
</script>

<style scoped lang="scss">
.config-panel {
  max-width: 800px;

  .form-hint {
    margin-left: 12px;
    color: var(--text-tertiary);
    font-size: 12px;
  }

  .weight-config {
    display: flex;
    gap: 24px;

    .weight-item {
      display: flex;
      align-items: center;
      gap: 8px;
    }
  }

  .actions {
    margin-top: 16px;
    padding: 16px 0;
  }
}
</style>
