<template>
  <div class="feedback-page">
    <div class="page-header">
      <h2>问答评价管理</h2>
      <p class="description">查看用户对问答的评价反馈，p>
    </div>

    <div class="filter-section">
      <a-form layout="inline">
        <a-form-item label="评分">
          <a-select v-model:value="filters.rating" placeholder="全部" allow-clear style="width: 120px">
            <a-select-option :value="1">点赞</a-select-option>
            <a-select-option :value="0">点踩</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="filters.status" placeholder="全部" allow-clear style="width: 120px">
            <a-select-option :value="0">待处理</a-select-option>
            <a-select-option :value="1">已处理</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item>
          <a-button type="primary" @click="loadData">查询</a-button>
        </a-form-item>
      </a-form>
    </div>

    <a-table
      :columns="columns"
      :data-source="tableData"
      :loading="loading"
      :pagination="pagination"
      row-key="id"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'rating'">
          <a-tag :color="record.rating === 1 ? 'green' : 'red'">
            {{ record.rating === 1 ? '点赞' : '点踩' }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'feedbackType'">
          <a-tag v-if="record.feedbackType">{{ getFeedbackTypeText(record.feedbackType) }}</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <a-tag :color="record.status === 0 ? 'orange' : 'green'">
            {{ record.status === 0 ? '待处理' : '已处理' }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <a-space>
            <a-button type="link" size="small" @click="viewDetail(record)">详情</a-button>
            <a-button v-if="record.status === 0" type="link" size="small" @click="handleFeedback(record, 1)">处理</a-button>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal
      v-model:open="detailVisible"
      title="反馈详情"
      :width="600"
    >
      <a-descriptions :column="1">
        <a-descriptions-item label="评价类型">{{ currentFeedback?.rating === 1 ? '点赞' : '点踩' }}</a-descriptions-item>
        <a-descriptions-item label="反馈类型">{{ getFeedbackTypeText(currentFeedback?.feedbackType) }}</a-descriptions-item>
        <a-descriptions-item label="反馈内容">{{ currentFeedback?.comment || '-' }}</a-descriptions-item>
        <a-descriptions-item label="正确答案" v-if="currentFeedback?.correctAnswer">
          <div class="correct-answer">{{ currentFeedback.correctAnswer }}</div>
        </a-descriptions-item>
      </a-descriptions>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { getFeedbackList, handleFeedback as handleFeedbackApi, type QaFeedback } from '@/api/feedback'

import type { PageResult } from '@/types'

const loading = ref(false)
const tableData = ref<QaFeedback[]>([])
const detailVisible = ref(false)
const currentFeedback = ref<QaFeedback | null>(null)

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: true,
})

const filters = reactive({
  rating: undefined,
  status: undefined,
})

const columns = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '用户ID', dataIndex: 'userId', width: 100 },
  { title: '评分', dataIndex: 'rating', width: 80 },
  { title: '反馈类型', dataIndex: 'feedbackType', width: 100 },
  { title: '反馈内容', dataIndex: 'comment', ellipsis: true },
  { title: '状态', dataIndex: 'status', width: 80 },
  { title: '创建时间', dataIndex: 'createTime', width: 160 },
  { title: '操作', dataIndex: 'action', width: 120, fixed: 'right' },
]

function getFeedbackTypeText(type: string | undefined): string {
  const map: Record<string, string> = {
    helpful: '有帮助',
    incorrect: '不正确',
    incomplete: '不完整',
    irrelevant: '不相关',
  }
  return map[type || ''] || type || '-'
}

async function loadData() {
  loading.value = true
  try {
    const res = await getFeedbackList({
      page: pagination.current,
      pageSize: pagination.pageSize,
      rating: filters.rating,
      status: filters.status,
    })
    if (res.code === 200) {
      tableData.value = res.data?.records || []
      pagination.total = res.data?.total || 0
    }
  } catch (error) {
    message.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

function viewDetail(record: QaFeedback) {
  currentFeedback.value = record
  detailVisible.value = true
}

async function handleFeedback(record: QaFeedback, status: number) {
  try {
    await handleFeedbackApi(record.id, status)
    message.success('处理成功')
    loadData()
  } catch (error) {
    message.error('处理失败')
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.feedback-page {
  padding: 24px;

  .page-header {
    margin-bottom: 24px;

    h2 {
      margin: 0 0 8px;
      font-size: 20px;
      font-weight: 600;
    }

    .description {
      color: var(--text-secondary);
      font-size: 14px;
    }
  }

  .filter-section {
    margin-bottom: 16px;
    background-color: var(--bg-color);
    padding: 16px;
    border-radius: var(--border-radius-base);
  }

  .correct-answer {
    white-space: pre-wrap;
    word-break: break-all;
  }
}
</style>