<template>
  <div class="feedback-container">
    <a-card>
      <template #title>
        <div class="card-header">
          <span class="card-title">问答评价管理</span>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div class="search-toolbar">
        <a-form layout="inline" :model="filters" class="search-form">
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
            <a-space>
              <a-button type="primary" @click="handleSearch">
                <template #icon><SearchOutlined /></template>
                搜索
              </a-button>
              <a-button @click="handleReset">
                <template #icon><ReloadOutlined /></template>
                重置
              </a-button>
            </a-space>
          </a-form-item>
        </a-form>
        <div class="toolbar-actions">
          <a-button @click="loadData">
            <template #icon><SyncOutlined /></template>
            刷新
          </a-button>
        </div>
      </div>

      <!-- 表格 -->
      <a-table
        class="feedback-table"
        :columns="columns"
        :data-source="tableData"
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 900 }"
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

      <!-- 分页 -->
      <div class="pagination">
        <a-pagination
          v-model:current="pagination.current"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-size-options="['10', '20', '50', '100']"
          show-size-changer
          show-quick-jumper
          :show-total="(total: number) => `共 ${total} 条`"
          @change="handlePageChange"
          @show-size-change="handleSizeChange"
        />
      </div>
    </a-card>

    <a-modal
      v-model:open="detailVisible"
      title="反馈详情"
      :width="600"
      ok-text="确认"
      cancel-text="取消"
      :footer="null"
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
import { SearchOutlined, ReloadOutlined, SyncOutlined } from '@ant-design/icons-vue'
import { getFeedbackList, handleFeedback as handleFeedbackApi, type QaFeedback } from '@/api/feedback'

const loading = ref(false)
const tableData = ref<QaFeedback[]>([])
const detailVisible = ref(false)
const currentFeedback = ref<QaFeedback | null>(null)

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
})

const filters = reactive({
  rating: undefined as number | undefined,
  status: undefined as number | undefined,
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

function handleSearch() {
  pagination.current = 1
  loadData()
}

function handleReset() {
  filters.rating = undefined
  filters.status = undefined
  handleSearch()
}

function handlePageChange(page: number) {
  pagination.current = page
  loadData()
}

function handleSizeChange(_current: number, size: number) {
  pagination.current = 1
  pagination.pageSize = size
  loadData()
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
.feedback-container {
  height: calc(100vh - 56px - 32px);
  overflow: hidden;

  :deep(.ant-card) {
    height: 100%;
    display: flex;
    flex-direction: column;

    .ant-card-head {
      flex-shrink: 0;
    }

    .ant-card-body {
      flex: 1;
      min-height: 0;
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }
  }

  :deep(.ant-card-head-title) {
    width: 100%;
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .card-title {
      font-size: 16px;
      font-weight: 500;
    }
  }

  .search-toolbar {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    margin-bottom: 16px;
    flex-shrink: 0;
  }

  .search-form {
    flex: 1;
  }

  .toolbar-actions {
    flex-shrink: 0;
    padding-top: 4px;
    display: flex;
    align-items: center;
  }

  .feedback-table {
    flex: 1;
    min-height: 0;
    overflow: hidden;

    :deep(.ant-table-wrapper) {
      height: 100%;
    }

    :deep(.ant-spin-nested-loading) {
      height: 100%;
    }

    :deep(.ant-spin-container) {
      height: 100%;
      display: flex;
      flex-direction: column;
    }

    :deep(.ant-table) {
      flex: 1;
      display: flex;
      flex-direction: column;
    }

    :deep(.ant-table-container) {
      flex: 1;
      display: flex;
      flex-direction: column;
    }

    :deep(.ant-table-body) {
      flex: 1;
      overflow: auto !important;
    }
  }

  .pagination {
    margin-top: 16px;
    display: flex;
    justify-content: flex-end;
    flex-shrink: 0;
  }

  .correct-answer {
    white-space: pre-wrap;
    word-break: break-all;
  }
}
</style>
