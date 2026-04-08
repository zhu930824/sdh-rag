<template>
  <div class="document-list">
    <a-table
      :loading="loading"
      :data-source="documents"
      :columns="columns"
      :pagination="false"
      row-key="id"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="getStatusColor(record.processStatus)">
            {{ getStatusText(record.processStatus) }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'chunkMode'">
          <a-tag :color="getChunkModeColor(record.chunkMode)">
            {{ getChunkModeText(record.chunkMode) }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'chunkConfig'">
          <div class="chunk-config">
            <span class="config-item" title="分块大小">
              <span class="label">大小:</span>
              <span class="value">{{ record.chunkSize || '-' }}</span>
            </span>
            <span class="config-item" title="分块重叠">
              <span class="label">重叠:</span>
              <span class="value">{{ record.chunkOverlap || '-' }}</span>
            </span>
          </div>
        </template>
        <template v-else-if="column.dataIndex === 'embeddingModel'">
          <a-tooltip v-if="record.embeddingModel" :title="record.embeddingModel">
            <span class="model-name">{{ formatModelName(record.embeddingModel) }}</span>
          </a-tooltip>
          <span v-else class="no-model">-</span>
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <a-space>
            <a-button type="link" size="small" @click="showConfigModal(record)">
              配置
            </a-button>
            <a-popconfirm
              v-if="record.processStatus === 2 || record.processStatus === 3"
              title="确定要重新处理该文档吗？"
              @confirm="handleReprocess(record.id)"
            >
              <a-button type="link" size="small">重处理</a-button>
            </a-popconfirm>
            <a-popconfirm
              title="确定要移除该文档吗？"
              @confirm="handleUnlink(record.id)"
            >
              <a-button type="link" size="small" danger>移除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <div class="pagination">
      <a-pagination
        v-model:current="pagination.current"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        show-size-changer
        :show-total="(total: number) => `共 ${total} 条`"
        @change="loadDocuments"
      />
    </div>

    <!-- 配置编辑弹窗 -->
    <a-modal
      v-model:open="configModalVisible"
      title="文档切分配置"
      :width="560"
      @ok="handleSaveConfig"
    >
      <a-form :model="configForm" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="切分模式">
          <a-radio-group v-model:value="configForm.chunkMode">
            <a-radio-button value="default">默认</a-radio-button>
            <a-radio-button value="smart">智能</a-radio-button>
            <a-radio-button value="custom">自定义</a-radio-button>
          </a-radio-group>
        </a-form-item>

        <template v-if="configForm.chunkMode === 'custom'">
          <a-form-item label="切分方式">
            <a-select v-model:value="configForm.splitType" style="width: 200px">
              <a-select-option value="length">按长度切分</a-select-option>
              <a-select-option value="page">按页切分</a-select-option>
              <a-select-option value="heading">按标题切分</a-select-option>
              <a-select-option value="regex">按正则切分</a-select-option>
            </a-select>
          </a-form-item>

          <template v-if="configForm.splitType === 'length'">
            <a-form-item label="分块大小">
              <a-input-number
                v-model:value="configForm.chunkSize"
                :min="100"
                :max="5000"
                :step="100"
                style="width: 200px"
              />
              <span class="form-hint">字符数</span>
            </a-form-item>
            <a-form-item label="分块重叠">
              <a-input-number
                v-model:value="configForm.chunkOverlap"
                :min="0"
                :max="500"
                :step="10"
                style="width: 200px"
              />
              <span class="form-hint">字符数</span>
            </a-form-item>
          </template>

          <template v-if="configForm.splitType === 'page'">
            <a-form-item label="每块页数">
              <a-input-number
                v-model:value="configForm.pagesPerChunk"
                :min="1"
                :max="50"
                style="width: 200px"
              />
              <span class="form-hint">页</span>
            </a-form-item>
          </template>

          <template v-if="configForm.splitType === 'heading'">
            <a-form-item label="标题层级">
              <a-checkbox-group v-model:value="configForm.headingLevels">
                <a-checkbox value="h1">H1</a-checkbox>
                <a-checkbox value="h2">H2</a-checkbox>
                <a-checkbox value="h3">H3</a-checkbox>
                <a-checkbox value="h4">H4</a-checkbox>
              </a-checkbox-group>
            </a-form-item>
          </template>

          <template v-if="configForm.splitType === 'regex'">
            <a-form-item label="正则表达式">
              <a-input
                v-model:value="configForm.regexPattern"
                placeholder="例如：\n\n\n"
                style="width: 100%"
              />
            </a-form-item>
          </template>
        </template>

        <a-form-item label="嵌入模型">
          <a-select v-model:value="configForm.embeddingModel" style="width: 200px" allow-clear>
            <a-select-option value="text-embedding-ada-002">text-embedding-ada-002</a-select-option>
            <a-select-option value="text-embedding-3-small">text-embedding-3-small</a-select-option>
            <a-select-option value="text-embedding-3-large">text-embedding-3-large</a-select-option>
            <a-select-option value="dashscope/text-embedding-v2">通义千问 embedding</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
      <a-alert
        message="修改配置后需要重新处理文档才能生效"
        type="info"
        show-icon
        style="margin-top: 16px"
      />
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { message } from 'ant-design-vue'
import {
  getKnowledgeBaseDocuments,
  updateDocumentLinkConfig,
  type DocumentLinkConfig,
} from '@/api/knowledgeBase'

const props = defineProps<{
  knowledgeBaseId: number
}>()

const emit = defineEmits<{
  (e: 'unlink', documentId: number): void
  (e: 'reprocess', documentId: number): void
  (e: 'refresh'): void
}>()

const loading = ref(false)
const documents = ref<any[]>([])
const configModalVisible = ref(false)
const currentDocument = ref<any>(null)

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
})

const configForm = reactive({
  chunkMode: 'default' as string,
  splitType: 'length' as string,
  chunkSize: undefined as number | undefined,
  chunkOverlap: undefined as number | undefined,
  pagesPerChunk: undefined as number | undefined,
  headingLevels: [] as string[],
  regexPattern: undefined as string | undefined,
  embeddingModel: undefined as string | undefined,
})

const columns = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '文档名称', dataIndex: 'title', ellipsis: true },
  { title: '处理状态', dataIndex: 'status', width: 100 },
  { title: '切分模式', dataIndex: 'chunkMode', width: 100 },
  { title: '切分配置', dataIndex: 'chunkConfig', width: 140 },
  { title: '嵌入模型', dataIndex: 'embeddingModel', width: 120 },
  { title: '分块数', dataIndex: 'chunkCount', width: 80 },
  { title: '操作', dataIndex: 'action', width: 160 },
]

async function loadDocuments() {
  loading.value = true
  try {
    const res = await getKnowledgeBaseDocuments(props.knowledgeBaseId, {
      page: pagination.current,
      pageSize: pagination.pageSize,
    })
    if (res.code === 200) {
      documents.value = res.data?.records || []
      pagination.total = res.data?.total || 0
    }
  } finally {
    loading.value = false
  }
}

function getStatusColor(status: number): string {
  const colors: Record<number, string> = {
    0: 'warning',
    1: 'processing',
    2: 'success',
    3: 'error',
  }
  return colors[status] || 'default'
}

function getStatusText(status: number): string {
  const texts: Record<number, string> = {
    0: '待处理',
    1: '处理中',
    2: '成功',
    3: '失败',
  }
  return texts[status] || '未知'
}

function getChunkModeColor(mode: string): string {
  const colors: Record<string, string> = {
    default: 'blue',
    smart: 'green',
    custom: 'orange',
  }
  return colors[mode] || 'default'
}

function getChunkModeText(mode: string): string {
  const texts: Record<string, string> = {
    default: '默认',
    smart: '智能',
    custom: '自定义',
  }
  return texts[mode] || '默认'
}

function formatModelName(model: string): string {
  if (!model) return '-'
  if (model.includes('ada')) return 'Ada-002'
  if (model.includes('3-small')) return 'V3-Small'
  if (model.includes('3-large')) return 'V3-Large'
  if (model.includes('dashscope')) return '通义千问'
  return model.substring(0, 12) + '...'
}

function showConfigModal(record: any) {
  currentDocument.value = record
  configForm.chunkMode = record.chunkMode || 'default'
  configForm.splitType = record.splitType || 'length'
  configForm.chunkSize = record.chunkSize
  configForm.chunkOverlap = record.chunkOverlap
  configForm.pagesPerChunk = record.pagesPerChunk
  configForm.headingLevels = record.headingLevels ? JSON.parse(record.headingLevels) : ['h1', 'h2']
  configForm.regexPattern = record.regexPattern
  configForm.embeddingModel = record.embeddingModel
  configModalVisible.value = true
}

async function handleSaveConfig() {
  if (!currentDocument.value) return

  try {
    const config: DocumentLinkConfig = {
      documentId: currentDocument.value.id,
      chunkMode: configForm.chunkMode,
      embeddingModel: configForm.embeddingModel,
    }

    if (configForm.chunkMode === 'custom') {
      config.splitType = configForm.splitType

      switch (configForm.splitType) {
        case 'length':
          config.chunkSize = configForm.chunkSize
          config.chunkOverlap = configForm.chunkOverlap
          break
        case 'page':
          config.pagesPerChunk = configForm.pagesPerChunk
          break
        case 'heading':
          config.headingLevels = configForm.headingLevels
          break
        case 'regex':
          config.regexPattern = configForm.regexPattern
          break
      }
    }

    await updateDocumentLinkConfig(props.knowledgeBaseId, currentDocument.value.id, config)
    message.success('配置已保存')
    configModalVisible.value = false
    loadDocuments()
    emit('refresh')
  } catch (error) {
    message.error('保存失败')
  }
}

function handleUnlink(documentId: number) {
  emit('unlink', documentId)
}

function handleReprocess(documentId: number) {
  emit('reprocess', documentId)
}

watch(() => props.knowledgeBaseId, loadDocuments, { immediate: true })

defineExpose({
  loadDocuments,
})
</script>

<style scoped lang="scss">
.document-list {
  height: 100%;
  display: flex;
  flex-direction: column;

  .pagination {
    margin-top: 16px;
    display: flex;
    justify-content: flex-end;
  }

  .chunk-config {
    display: flex;
    gap: 12px;
    font-size: 12px;

    .config-item {
      .label {
        color: var(--text-tertiary);
      }
      .value {
        color: var(--text-primary);
        font-weight: 500;
      }
    }
  }

  .model-name {
    font-size: 12px;
    color: var(--text-secondary);
  }

  .no-model {
    color: var(--text-tertiary);
  }
}

.form-hint {
  margin-left: 8px;
  color: var(--text-tertiary);
  font-size: 12px;
}
</style>
