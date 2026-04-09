<template>
  <div class="link-document-dialog">
    <!-- 步骤指示器 -->
    <div class="steps-header">
      <div class="step-item" :class="{ active: currentStep === 1, done: currentStep > 1 }">
        <div class="step-number">1</div>
        <div class="step-title">选择文档</div>
      </div>
      <div class="step-line" :class="{ active: currentStep > 1 }"></div>
      <div class="step-item" :class="{ active: currentStep === 2 }">
        <div class="step-number">2</div>
        <div class="step-title">选择切分策略</div>
      </div>
    </div>

    <!-- 步骤1：选择文档 -->
    <div v-show="currentStep === 1" class="step-content">
      <div class="document-section">
        <div class="document-toolbar">
          <a-input-search
            v-model:value="searchKeyword"
            placeholder="搜索文档名称"
            style="width: 260px"
            allow-clear
            @search="loadDocuments"
          />
          <div class="toolbar-right">
            已选择 <span class="count">{{ selectedRowKeys.length }}</span> 个文档
          </div>
        </div>

        <div class="document-list-wrapper">
          <a-table
            :loading="loading"
            :data-source="documents"
            :columns="columns"
            :pagination="false"
            :row-selection="rowSelection"
            row-key="id"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'title'">
                <div class="doc-title">
                  <FileTextOutlined class="doc-icon" />
                  <span class="title-text">{{ record.title }}</span>
                </div>
              </template>
              <template v-else-if="column.dataIndex === 'fileType'">
                <a-tag :color="getFileTypeColor(record.fileType)" size="small">
                  {{ record.fileType?.toUpperCase() || '未知' }}
                </a-tag>
              </template>
              <template v-else-if="column.dataIndex === 'fileSize'">
                <span class="file-size">{{ formatFileSize(record.fileSize) }}</span>
              </template>
            </template>
          </a-table>
        </div>

        <div class="pagination-wrapper">
          <a-pagination
            v-model:current="pagination.current"
            v-model:page-size="pagination.pageSize"
            :total="pagination.total"
            size="small"
            :show-total="(total: number) => `共 ${total} 条`"
            @change="loadDocuments"
          />
        </div>
      </div>

      <div class="step-actions">
        <a-button type="primary" :disabled="selectedRowKeys.length === 0" @click="goToStep2">
          下一步
        </a-button>
      </div>
    </div>

    <!-- 步骤2：选择切分策略 -->
    <div v-show="currentStep === 2" class="step-content">
      <div class="selected-docs-bar">
        <span class="bar-label">已选择文档：</span>
        <a-tag color="blue">{{ selectedRowKeys.length }} 个</a-tag>
        <a-button type="link" size="small" @click="currentStep = 1">
          重新选择
        </a-button>
      </div>

      <div class="strategy-section">
        <div class="section-title">切分策略</div>
        <div class="strategy-options">
          <div
            v-for="strategy in strategies"
            :key="strategy.key"
            class="strategy-card"
            :class="{ selected: selectedStrategy === strategy.key }"
            @click="selectedStrategy = strategy.key"
          >
            <div class="strategy-icon">
              <component :is="strategy.icon" />
            </div>
            <div class="strategy-info">
              <div class="strategy-name">{{ strategy.name }}</div>
              <div class="strategy-desc">{{ strategy.desc }}</div>
            </div>
            <div v-if="selectedStrategy === strategy.key" class="check-icon">
              <CheckOutlined />
            </div>
          </div>
        </div>
      </div>

      <!-- 自定义配置 -->
      <div v-if="selectedStrategy === 'custom'" class="config-section">
        <div class="section-title">切分参数</div>

        <!-- 切分方式选择 -->
        <div class="split-type-selector">
          <div class="split-type-label">切分方式</div>
          <div class="split-type-options">
            <div
              v-for="type in splitTypes"
              :key="type.key"
              class="split-type-item"
              :class="{ active: splitConfig.splitType === type.key }"
              @click="splitConfig.splitType = type.key"
            >
              {{ type.name }}
            </div>
          </div>
        </div>

        <!-- 按长度切分参数 -->
        <div v-if="splitConfig.splitType === 'length'" class="param-form">
          <div class="param-item">
            <div class="param-label">分块大小</div>
            <a-input-number
              v-model:value="splitConfig.chunkSize"
              :min="100"
              :max="5000"
              :step="100"
              addon-after="字符"
              style="width: 180px"
            />
          </div>
          <div class="param-item">
            <div class="param-label">分块重叠</div>
            <a-input-number
              v-model:value="splitConfig.chunkOverlap"
              :min="0"
              :max="500"
              :step="10"
              addon-after="字符"
              style="width: 180px"
            />
          </div>
        </div>

        <!-- 按页切分参数 -->
        <div v-if="splitConfig.splitType === 'page'" class="param-form">
          <div class="param-item">
            <div class="param-label">每块页数</div>
            <a-input-number
              v-model:value="splitConfig.pagesPerChunk"
              :min="1"
              :max="50"
              addon-after="页"
              style="width: 180px"
            />
          </div>
          <div class="param-item full-width">
            <a-alert
              message="按页切分适用于PDF等有明确分页的文档，系统会尝试识别分页符"
              type="info"
              show-icon
            />
          </div>
        </div>

        <!-- 按标题切分参数 -->
        <div v-if="splitConfig.splitType === 'heading'" class="param-form">
          <div class="param-item full-width">
            <div class="param-label">标题层级</div>
            <a-checkbox-group v-model:value="splitConfig.headingLevels">
              <a-checkbox value="h1">一级标题 (H1)</a-checkbox>
              <a-checkbox value="h2">二级标题 (H2)</a-checkbox>
              <a-checkbox value="h3">三级标题 (H3)</a-checkbox>
              <a-checkbox value="h4">四级标题 (H4)</a-checkbox>
            </a-checkbox-group>
          </div>
          <div class="param-item full-width">
            <a-alert
              message="系统会在选中的标题层级处进行切分，每个标题下的内容作为一个分块"
              type="info"
              show-icon
            />
          </div>
        </div>

        <!-- 按正则切分参数 -->
        <div v-if="splitConfig.splitType === 'regex'" class="param-form">
          <div class="param-item full-width">
            <div class="param-label">正则表达式</div>
            <a-input
              v-model:value="splitConfig.regexPattern"
              placeholder="例如：\n\n\n 用于按空行切分"
              style="width: 100%"
            />
          </div>
          <div class="param-item full-width">
            <a-alert
              message="正则表达式将用于匹配分割点，匹配到的内容将作为分块边界"
              type="info"
              show-icon
            />
          </div>
        </div>

        <!-- 嵌入模型 -->
        <div class="param-form" style="margin-top: 16px; padding-top: 16px; border-top: 1px solid var(--border-color);">
          <div class="param-item">
            <div class="param-label">嵌入模型</div>
            <a-select
              v-model:value="splitConfig.embeddingModel"
              style="width: 280px"
              placeholder="请选择嵌入模型"
              :loading="loadingModels"
              allow-clear
            >
              <a-select-option
                v-for="model in embeddingModels"
                :key="model.id"
                :value="model.modelId"
              >
                {{ model.name }}
              </a-select-option>
            </a-select>
          </div>
        </div>
      </div>

      <!-- 智能切分配置 -->
      <div v-if="selectedStrategy === 'smart'" class="config-section">
        <div class="section-title">智能切分参数</div>
        <div class="param-form">
          <div class="param-item">
            <div class="param-label">最大分块大小</div>
            <a-input-number
              v-model:value="splitConfig.smartConfig.maxChunkSize"
              :min="200"
              :max="5000"
              :step="100"
              addon-after="字符"
              style="width: 180px"
            />
          </div>
          <div class="param-item">
            <div class="param-label">最小分块大小</div>
            <a-input-number
              v-model:value="splitConfig.smartConfig.minChunkSize"
              :min="50"
              :max="1000"
              :step="50"
              addon-after="字符"
              style="width: 180px"
            />
          </div>
        </div>
        <div class="param-form" style="margin-top: 16px;">
          <div class="param-item full-width">
            <a-checkbox v-model:checked="splitConfig.smartConfig.respectParagraphs">
              识别段落结构
            </a-checkbox>
            <span class="param-hint">尽量保持段落完整性</span>
          </div>
          <div class="param-item full-width">
            <a-checkbox v-model:checked="splitConfig.smartConfig.respectHeaders">
              识别标题层级
            </a-checkbox>
            <span class="param-hint">在标题处优先分割</span>
          </div>
        </div>
        <!-- 嵌入模型 -->
        <div class="param-form" style="margin-top: 16px; padding-top: 16px; border-top: 1px solid var(--border-color);">
          <div class="param-item">
            <div class="param-label">嵌入模型</div>
            <a-select
              v-model:value="splitConfig.embeddingModel"
              style="width: 280px"
              placeholder="请选择嵌入模型"
              :loading="loadingModels"
              allow-clear
            >
              <a-select-option
                v-for="model in embeddingModels"
                :key="model.id"
                :value="model.modelId"
              >
                {{ model.name }}
              </a-select-option>
            </a-select>
          </div>
        </div>
      </div>

      <div class="step-actions">
        <a-button @click="currentStep = 1">
          上一步
        </a-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { FileTextOutlined, CheckOutlined, SettingOutlined, ThunderboltOutlined, AppstoreOutlined } from '@ant-design/icons-vue'
import { getAvailableDocuments, type DocumentLinkConfig } from '@/api/knowledgeBase'
import { getActiveModels } from '@/api/model'
import type { ModelConfig } from '@/types'

const props = defineProps<{
  knowledgeBaseId: number
  selectedIds: number[]
  defaultChunkSize?: number
  defaultChunkOverlap?: number
  defaultEmbeddingModel?: string
}>()

const emit = defineEmits<{
  (e: 'selection-change', ids: number[]): void
  (e: 'config-change', configs: DocumentLinkConfig[]): void
}>()

const currentStep = ref(1)
const loading = ref(false)
const documents = ref<any[]>([])
const searchKeyword = ref('')
const selectedRowKeys = ref<number[]>([])
const selectedStrategy = ref('default')
const allModels = ref<ModelConfig[]>([])
const loadingModels = ref(false)

// 嵌入模型列表
const embeddingModels = computed(() => {
  return allModels.value.filter(m => m.modelType === 'embedding' && m.status === 1)
})

const splitConfig = reactive({
  // 自定义配置参数
  splitType: 'length',
  chunkSize: 500,
  chunkOverlap: 50,
  pagesPerChunk: 1,
  headingLevels: ['h1', 'h2'] as string[],
  regexPattern: '',
  embeddingModel: '',
  // 智能切分参数
  smartConfig: {
    respectParagraphs: true,
    respectHeaders: true,
    maxChunkSize: 1000,
    minChunkSize: 100,
    sentenceMode: 'semantic',
  },
})

const strategies = [
  {
    key: 'default',
    name: '知识库默认',
    desc: '使用知识库已配置的切分参数',
    icon: AppstoreOutlined,
  },
  {
    key: 'smart',
    name: '智能切分',
    desc: '根据文档结构智能识别段落和标题，自动优化切分',
    icon: ThunderboltOutlined,
  },
  {
    key: 'custom',
    name: '自定义配置',
    desc: '手动设置切分方式和参数',
    icon: SettingOutlined,
  },
]

const splitTypes = [
  { key: 'length', name: '按长度切分' },
  { key: 'page', name: '按页切分' },
  { key: 'heading', name: '按标题切分' },
  { key: 'regex', name: '按正则切分' },
]

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
})

const columns = [
  { title: '文档名称', dataIndex: 'title', ellipsis: true },
  { title: '类型', dataIndex: 'fileType', width: 80 },
  { title: '大小', dataIndex: 'fileSize', width: 90 },
]

const rowSelection = computed(() => ({
  selectedRowKeys: selectedRowKeys.value,
  onChange: (keys: number[]) => {
    selectedRowKeys.value = keys
    emit('selection-change', keys)
  },
}))

function goToStep2() {
  currentStep.value = 2
}

async function loadDocuments() {
  loading.value = true
  try {
    const res = await getAvailableDocuments({
      page: pagination.current,
      pageSize: pagination.pageSize,
      excludeKnowledgeId: props.knowledgeBaseId,
      keyword: searchKeyword.value || undefined,
    })
    if (res.code === 200) {
      documents.value = res.data?.records || []
      pagination.total = res.data?.total || 0
    }
  } finally {
    loading.value = false
  }
}

function getFileTypeColor(fileType: string): string {
  const colors: Record<string, string> = {
    pdf: 'red',
    doc: 'blue',
    docx: 'blue',
    txt: 'green',
    xls: 'green',
    xlsx: 'green',
  }
  return colors[fileType?.toLowerCase()] || 'default'
}

function formatFileSize(bytes: number): string {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

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

watch(() => props.selectedIds, (ids) => {
  selectedRowKeys.value = ids || []
}, { immediate: true })

onMounted(() => {
  loadDocuments()
  loadModels()
  if (props.defaultChunkSize) splitConfig.chunkSize = props.defaultChunkSize
  if (props.defaultChunkOverlap) splitConfig.chunkOverlap = props.defaultChunkOverlap
  if (props.defaultEmbeddingModel) splitConfig.embeddingModel = props.defaultEmbeddingModel
})

defineExpose({
  reset: () => {
    // 重置到第一步
    currentStep.value = 1
    // 清空选中的文档
    selectedRowKeys.value = []
    emit('selection-change', [])
    // 重置策略选择
    selectedStrategy.value = 'default'
    // 重置搜索关键词
    searchKeyword.value = ''
    // 重置分页
    pagination.current = 1
    // 重置自定义配置
    splitConfig.splitType = 'length'
    splitConfig.chunkSize = 500
    splitConfig.chunkOverlap = 50
    splitConfig.pagesPerChunk = 1
    splitConfig.headingLevels = ['h1', 'h2']
    splitConfig.regexPattern = ''
    splitConfig.embeddingModel = ''
    // 重置智能切分配置
    splitConfig.smartConfig.respectParagraphs = true
    splitConfig.smartConfig.respectHeaders = true
    splitConfig.smartConfig.maxChunkSize = 1000
    splitConfig.smartConfig.minChunkSize = 100
    splitConfig.smartConfig.sentenceMode = 'semantic'
    // 重新加载文档列表
    loadDocuments()
  },
  getConfigs: (): DocumentLinkConfig[] => {
    if (selectedStrategy.value === 'default') {
      return selectedRowKeys.value.map(id => ({
        documentId: id,
        chunkMode: 'default',
      }))
    }

    if (selectedStrategy.value === 'smart') {
      return selectedRowKeys.value.map(id => ({
        documentId: id,
        chunkMode: 'smart',
        embeddingModel: splitConfig.embeddingModel,
        smartConfig: {
          respectParagraphs: splitConfig.smartConfig.respectParagraphs,
          respectHeaders: splitConfig.smartConfig.respectHeaders,
          maxChunkSize: splitConfig.smartConfig.maxChunkSize,
          minChunkSize: splitConfig.smartConfig.minChunkSize,
          sentenceMode: splitConfig.smartConfig.sentenceMode,
        },
      }))
    }

    // 自定义配置
    const customConfig: DocumentLinkConfig = {
      chunkMode: 'custom',
      splitType: splitConfig.splitType,
      embeddingModel: splitConfig.embeddingModel,
    }

    // 根据切分方式设置不同参数
    switch (splitConfig.splitType) {
      case 'length':
        customConfig.chunkSize = splitConfig.chunkSize
        customConfig.chunkOverlap = splitConfig.chunkOverlap
        break
      case 'page':
        customConfig.pagesPerChunk = splitConfig.pagesPerChunk
        break
      case 'heading':
        customConfig.headingLevels = splitConfig.headingLevels
        break
      case 'regex':
        customConfig.regexPattern = splitConfig.regexPattern
        break
    }

    return selectedRowKeys.value.map(id => ({
      documentId: id,
      ...customConfig,
    }))
  },
  isCustomMode: () => selectedStrategy.value === 'custom',
  getStrategy: () => selectedStrategy.value,
})
</script>

<style scoped lang="scss">
.link-document-dialog {
  // 步骤指示器
  .steps-header {
    display: flex;
    align-items: center;
    justify-content: center;
    margin-bottom: 24px;

    .step-item {
      display: flex;
      align-items: center;
      gap: 8px;

      .step-number {
        width: 28px;
        height: 28px;
        border-radius: 50%;
        background: var(--bg-page);
        border: 2px solid var(--border-color);
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 14px;
        font-weight: 600;
        color: var(--text-tertiary);
        transition: all 0.3s;
      }

      .step-title {
        font-size: 14px;
        color: var(--text-tertiary);
        transition: all 0.3s;
      }

      &.active {
        .step-number {
          background: var(--primary-color);
          border-color: var(--primary-color);
          color: #fff;
        }

        .step-title {
          color: var(--text-primary);
          font-weight: 500;
        }
      }

      &.done {
        .step-number {
          background: var(--primary-color);
          border-color: var(--primary-color);
          color: #fff;
        }
      }
    }

    .step-line {
      width: 60px;
      height: 2px;
      background: var(--border-color);
      margin: 0 16px;
      transition: all 0.3s;

      &.active {
        background: var(--primary-color);
      }
    }
  }

  // 步骤内容
  .step-content {
    min-height: 300px;

    .section-title {
      font-size: 14px;
      font-weight: 500;
      color: var(--text-primary);
      margin-bottom: 12px;
    }

    .step-actions {
      margin-top: 24px;
      display: flex;
      justify-content: flex-end;
      gap: 12px;
    }
  }

  // 已选文档提示
  .selected-docs-bar {
    padding: 10px 16px;
    background: var(--bg-page);
    border-radius: var(--radius-base);
    margin-bottom: 16px;
    display: flex;
    align-items: center;
    gap: 8px;

    .bar-label {
      font-size: 13px;
      color: var(--text-secondary);
    }
  }

  // 文档列表
  .document-section {
    .document-toolbar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 12px;

      .toolbar-right {
        font-size: 13px;
        color: var(--text-secondary);

        .count {
          color: var(--primary-color);
          font-weight: 600;
        }
      }
    }

    .document-list-wrapper {
      border: 1px solid var(--border-color);
      border-radius: var(--radius-base);
      max-height: 320px;
      overflow-y: auto;

      .doc-title {
        display: flex;
        align-items: center;
        gap: 8px;

        .doc-icon {
          color: var(--primary-color);
          font-size: 14px;
        }

        .title-text {
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
      }

      .file-size {
        font-size: 12px;
        color: var(--text-secondary);
      }
    }

    .pagination-wrapper {
      margin-top: 12px;
      display: flex;
      justify-content: flex-end;
    }
  }

  // 切分策略选择
  .strategy-section {
    margin-bottom: 20px;

    .strategy-options {
      display: flex;
      gap: 16px;
    }

    .strategy-card {
      flex: 1;
      padding: 16px;
      border: 2px solid var(--border-color);
      border-radius: var(--radius-lg);
      cursor: pointer;
      transition: all 0.2s;
      display: flex;
      align-items: flex-start;
      gap: 12px;
      position: relative;

      &:hover {
        border-color: var(--primary-color-hover);
        background: var(--bg-page);
      }

      &.selected {
        border-color: var(--primary-color);
        background: rgba(24, 144, 255, 0.04);
      }

      .strategy-icon {
        width: 40px;
        height: 40px;
        border-radius: var(--radius-base);
        background: var(--bg-page);
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 18px;
        color: var(--primary-color);
        flex-shrink: 0;
      }

      .strategy-info {
        flex: 1;

        .strategy-name {
          font-size: 15px;
          font-weight: 500;
          color: var(--text-primary);
          margin-bottom: 4px;
        }

        .strategy-desc {
          font-size: 12px;
          color: var(--text-secondary);
        }
      }

      .check-icon {
        position: absolute;
        top: 8px;
        right: 8px;
        width: 20px;
        height: 20px;
        border-radius: 50%;
        background: var(--primary-color);
        color: #fff;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 12px;
      }
    }
  }

  // 配置区域
  .config-section {
    padding: 16px;
    background: var(--bg-page);
    border-radius: var(--radius-lg);
  }

  // 切分方式选择器
  .split-type-selector {
    margin-bottom: 20px;

    .split-type-label {
      font-size: 13px;
      color: var(--text-secondary);
      margin-bottom: 10px;
    }

    .split-type-options {
      display: flex;
      gap: 12px;

      .split-type-item {
        padding: 8px 16px;
        border: 1px solid var(--border-color);
        border-radius: var(--radius-base);
        cursor: pointer;
        font-size: 13px;
        color: var(--text-secondary);
        transition: all 0.2s;

        &:hover {
          border-color: var(--primary-color-hover);
          color: var(--text-primary);
        }

        &.active {
          border-color: var(--primary-color);
          background: var(--primary-color);
          color: #fff;
        }
      }
    }
  }

  // 参数表单
  .param-form {
    display: flex;
    flex-wrap: wrap;
    gap: 16px;

    .param-item {
      display: flex;
      flex-direction: column;
      gap: 6px;

      &.full-width {
        width: 100%;
        flex-direction: column;
        align-items: flex-start;
        gap: 8px;
      }

      .param-label {
        font-size: 13px;
        color: var(--text-primary);
      }

      .param-hint {
        font-size: 12px;
        color: var(--text-tertiary);
      }
    }
  }
}
</style>
