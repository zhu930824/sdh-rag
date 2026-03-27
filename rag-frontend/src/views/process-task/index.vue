<template>
  <div class="process-task-page">
    <div class="page-header">
      <h2>文档预处理</h2>
      <p class="description">智能处理文档，支持OCR识别、表格提取、结构化解析等</p>
    </div>

    <div class="process-types">
      <a-row :gutter="16">
        <a-col :span="6" v-for="type in processTypes" :key="type.key">
          <a-card hoverable class="type-card" @click="showProcessModal(type.key)">
            <template #cover>
              <div class="type-icon">
                <component :is="type.icon" />
              </div>
            </template>
            <a-card-meta :title="type.name" :description="type.description" />
          </a-card>
        </a-col>
      </a-row>
    </div>

    <div class="task-list">
      <a-card title="处理任务列表" :bordered="false">
        <template #extra>
          <a-select v-model:value="filterStatus" placeholder="状态筛选" allow-clear style="width: 120px" @change="loadTasks">
            <a-select-option :value="0">待处理</a-select-option>
            <a-select-option :value="1">处理中</a-select-option>
            <a-select-option :value="2">成功</a-select-option>
            <a-select-option :value="3">失败</a-select-option>
          </a-select>
        </template>

        <a-table :columns="columns" :data-source="tasks" :loading="loading" :pagination="pagination" row-key="id">
          <template #bodyCell="{ column, record }">
            <template v-if="column.dataIndex === 'taskType'">
              <a-tag>{{ getTaskTypeText(record.taskType) }}</a-tag>
            </template>
            <template v-else-if="column.dataIndex === 'status'">
              <a-tag :color="getStatusColor(record.status)">{{ getStatusText(record.status) }}</a-tag>
            </template>
            <template v-else-if="column.dataIndex === 'progress'">
              <a-progress :percent="record.progress" :status="getProgressStatus(record.status)" size="small" />
            </template>
            <template v-else-if="column.dataIndex === 'action'">
              <a-button type="link" size="small" @click="viewResult(record)" :disabled="record.status !== 2">查看结果</a-button>
            </template>
          </template>
        </a-table>
      </a-card>
    </div>

    <a-modal
      v-model:open="processModalVisible"
      :title="'创建' + currentTypeName + '任务'"
      @ok="handleCreateTask"
    >
      <a-form :label-col="{ span: 6 }">
        <a-form-item label="选择文档">
          <a-select v-model:value="selectedDocumentId" placeholder="请选择文档" show-search>
            <a-select-option v-for="doc in documents" :key="doc.id" :value="doc.id">
              {{ doc.title }}
            </a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="resultModalVisible"
      title="处理结果"
      :width="700"
    >
      <pre class="result-content">{{ currentResult }}</pre>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { EyeOutlined, TableOutlined, FileSearchOutlined, TagsOutlined } from '@ant-design/icons-vue'
import { getProcessTaskList, createProcessTask, type DocumentProcessTask } from '@/api/process-task'
import { getDocumentList, type KnowledgeDocument } from '@/api/knowledge'

import type { PageResult } from '@/types'

const processTypes = [
  { key: 'ocr', name: 'OCR识别', description: '识别图片中的文字内容', icon: EyeOutlined },
  { key: 'table_extract', name: '表格提取', description: '提取文档中的表格数据', icon: TableOutlined },
  { key: 'structure', name: '结构化解析', description: '解析文档结构层次', icon: FileSearchOutlined },
  { key: 'entity_extract', name: '实体提取', description: '提取文档中的关键实体', icon: TagsOutlined },
]

const loading = ref(false)
const tasks = ref<DocumentProcessTask[]>([])
const documents = ref<KnowledgeDocument[]>([])
const filterStatus = ref<number | undefined>()
const processModalVisible = ref(false)
const resultModalVisible = ref(false)
const currentTaskType = ref('')
const currentTypeName = ref('')
const selectedDocumentId = ref<number>()
const currentResult = ref('')

const pagination = reactive({ current: 1, pageSize: 10, total: 0 })
const columns = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '文档ID', dataIndex: 'documentId', width: 100 },
  { title: '任务类型', dataIndex: 'taskType', width: 120 },
  { title: '状态', dataIndex: 'status', width: 100 },
  { title: '进度', dataIndex: 'progress', width: 150 },
  { title: '创建时间', dataIndex: 'createTime', width: 160 },
  { title: '操作', dataIndex: 'action', width: 100 },
]

function getTaskTypeText(type: string): string {
  const map: Record<string, string> = { ocr: 'OCR识别', table_extract: '表格提取', structure: '结构化解析', entity_extract: '实体提取' }
  return map[type] || type
}

function getStatusText(status: number): string {
  const map: Record<number, string> = { 0: '待处理', 1: '处理中', 2: '成功', 3: '失败' }
  return map[status] || String(status)
}

function getStatusColor(status: number): string {
  const map: Record<number, string> = { 0: 'default', 1: 'processing', 2: 'success', 3: 'error' }
  return map[status] || 'default'
}

function getProgressStatus(status: number): 'success' | 'exception' | 'normal' | 'active' | undefined {
  if (status === 2) return 'success'
  if (status === 3) return 'exception'
  if (status === 1) return 'active'
  return undefined
}

async function loadTasks() {
  loading.value = true
  try {
    const res = await getProcessTaskList({
      page: pagination.current,
      pageSize: pagination.pageSize,
      status: filterStatus.value,
    })
    if (res.code === 200) {
      tasks.value = res.data?.records || []
      pagination.total = res.data?.total || 0
    }
  } finally {
    loading.value = false
  }
}

async function loadDocuments() {
  try {
    const res = await getDocumentList({ page: 1, pageSize: 100 })
    if (res.code === 200) {
      documents.value = res.data?.records || []
    }
  } catch (error) {
    console.error('加载文档失败')
  }
}

function showProcessModal(type: string) {
  currentTaskType.value = type
  currentTypeName.value = getTaskTypeText(type)
  selectedDocumentId.value = undefined
  processModalVisible.value = true
}

async function handleCreateTask() {
  if (!selectedDocumentId.value) {
    message.warning('请选择文档')
    return
  }
  
  try {
    await createProcessTask({
      documentId: selectedDocumentId.value,
      taskType: currentTaskType.value,
    })
    message.success('任务创建成功')
    processModalVisible.value = false
    loadTasks()
  } catch (error) {
    message.error('创建失败')
  }
}

function viewResult(task: DocumentProcessTask) {
  currentResult.value = task.result || '无结果'
  resultModalVisible.value = true
}

onMounted(() => {
  loadTasks()
  loadDocuments()
})
</script>

<style scoped lang="scss">
.process-task-page {
  padding: 24px;

  .page-header {
    margin-bottom: 24px;
    h2 { margin: 0 0 8px; font-size: 20px; font-weight: 600; }
    .description { color: var(--text-secondary); font-size: 14px; }
  }

  .process-types {
    margin-bottom: 24px;

    .type-card {
      text-align: center;

      .type-icon {
        height: 100px;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 48px;
        color: var(--primary-color);
        background-color: var(--primary-light-9);
      }
    }
  }

  .result-content {
    max-height: 400px;
    overflow: auto;
    padding: 16px;
    background-color: var(--bg-page);
    border-radius: var(--border-radius-base);
    white-space: pre-wrap;
    word-break: break-all;
  }
}
</style>