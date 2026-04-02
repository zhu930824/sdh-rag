<template>
  <div class="approval-container">
    <a-card>
      <template #title>
        <div class="card-header">
          <span class="card-title">审核中心</span>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div class="search-toolbar">
        <a-form layout="inline" :model="filters" class="search-form">
          <a-form-item label="业务类型">
            <a-select v-model:value="filters.businessType" placeholder="全部" allow-clear style="width: 140px">
              <a-select-option value="document_publish">文档发布</a-select-option>
              <a-select-option value="content_audit">内容审核</a-select-option>
              <a-select-option value="sensitive_check">敏感词审核</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="状态">
            <a-select v-model:value="filters.status" placeholder="全部" allow-clear style="width: 120px">
              <a-select-option :value="0">待审核</a-select-option>
              <a-select-option :value="1">已通过</a-select-option>
              <a-select-option :value="2">已拒绝</a-select-option>
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
          <a-button @click="handleRefresh">
            <template #icon><SyncOutlined /></template>
            刷新
          </a-button>
        </div>
      </div>

      <!-- 标签页 -->
      <a-tabs v-model:activeKey="activeTab" class="approval-tabs" @change="handleTabChange">
        <a-tab-pane key="pending" tab="待审核">
          <a-table
            class="approval-table"
            :columns="columns"
            :data-source="pendingTasks"
            :loading="pendingLoading"
            :pagination="false"
            :scroll="{ x: 800 }"
            row-key="id"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'businessType'">
                <a-tag>{{ getBusinessTypeText(record.businessType) }}</a-tag>
              </template>
              <template v-else-if="column.dataIndex === 'status'">
                <a-tag color="orange">{{ getStatusText(record.status) }}</a-tag>
              </template>
              <template v-else-if="column.dataIndex === 'action'">
                <a-space>
                  <a-button type="primary" size="small" @click="handleApprove(record)">通过</a-button>
                  <a-button danger size="small" @click="showRejectModal(record)">拒绝</a-button>
                </a-space>
              </template>
            </template>
          </a-table>
        </a-tab-pane>

        <a-tab-pane key="my" tab="我的申请">
          <a-table
            class="approval-table"
            :columns="columns"
            :data-source="myTasks"
            :loading="myLoading"
            :pagination="false"
            :scroll="{ x: 800 }"
            row-key="id"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'businessType'">
                <a-tag>{{ getBusinessTypeText(record.businessType) }}</a-tag>
              </template>
              <template v-else-if="column.dataIndex === 'status'">
                <a-tag :color="getStatusColor(record.status)">{{ getStatusText(record.status) }}</a-tag>
              </template>
              <template v-else-if="column.dataIndex === 'action'">
                <a-button type="link" size="small" @click="viewDetail(record)">详情</a-button>
              </template>
            </template>
          </a-table>
        </a-tab-pane>
      </a-tabs>

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
      v-model:open="rejectVisible"
      title="拒绝原因"
      ok-text="确认"
      cancel-text="取消"
      @ok="handleReject"
    >
      <a-textarea v-model:value="rejectComment" :rows="3" placeholder="请输入拒绝原因" />
    </a-modal>

    <a-modal
      v-model:open="detailVisible"
      title="审核详情"
      :width="600"
      ok-text="确认"
      cancel-text="取消"
      :footer="null"
    >
      <a-descriptions :column="1" v-if="currentTask">
        <a-descriptions-item label="业务类型">{{ getBusinessTypeText(currentTask.businessType) }}</a-descriptions-item>
        <a-descriptions-item label="业务ID">{{ currentTask.businessId }}</a-descriptions-item>
        <a-descriptions-item label="状态">{{ getStatusText(currentTask.status) }}</a-descriptions-item>
        <a-descriptions-item label="创建时间">{{ currentTask.createTime }}</a-descriptions-item>
      </a-descriptions>

      <a-divider>审核记录</a-divider>

      <a-timeline v-if="currentRecords.length > 0">
        <a-timeline-item v-for="record in currentRecords" :key="record.id" :color="record.action === 1 ? 'green' : 'red'">
          <p><strong>{{ record.action === 1 ? '通过' : '拒绝' }}</strong></p>
          <p v-if="record.comment">{{ record.comment }}</p>
          <p style="color: var(--text-secondary); font-size: 12px;">{{ record.createTime }}</p>
        </a-timeline-item>
      </a-timeline>
      <a-empty v-else description="暂无审核记录" />
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { SearchOutlined, ReloadOutlined, SyncOutlined } from '@ant-design/icons-vue'
import {
  getPendingApprovals,
  getMyApprovals,
  getApprovalDetail,
  approveTask,
  rejectTask,
  type ApprovalTask,
  type ApprovalRecord,
} from '@/api/approval'

const activeTab = ref('pending')
const pendingTasks = ref<ApprovalTask[]>([])
const myTasks = ref<ApprovalTask[]>([])
const pendingLoading = ref(false)
const myLoading = ref(false)
const rejectVisible = ref(false)
const detailVisible = ref(false)
const rejectComment = ref('')
const currentTask = ref<ApprovalTask | null>(null)
const currentRecords = ref<ApprovalRecord[]>([])

const filters = reactive({
  businessType: undefined as string | undefined,
  status: undefined as number | undefined,
})

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
})

const columns = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '业务类型', dataIndex: 'businessType', width: 120 },
  { title: '业务ID', dataIndex: 'businessId', width: 100 },
  { title: '状态', dataIndex: 'status', width: 80 },
  { title: '创建时间', dataIndex: 'createTime', width: 160 },
  { title: '操作', dataIndex: 'action', width: 150, fixed: 'right' },
]

function getBusinessTypeText(type: string): string {
  const map: Record<string, string> = {
    document_publish: '文档发布',
    content_audit: '内容审核',
    sensitive_check: '敏感词审核',
  }
  return map[type] || type
}

function getStatusText(status: number): string {
  const map: Record<number, string> = { 0: '待审核', 1: '已通过', 2: '已拒绝', 3: '已撤回' }
  return map[status] || String(status)
}

function getStatusColor(status: number): string {
  const map: Record<number, string> = { 0: 'orange', 1: 'green', 2: 'red', 3: 'default' }
  return map[status] || 'default'
}

async function loadPending() {
  pendingLoading.value = true
  try {
    const res = await getPendingApprovals({ page: pagination.current, pageSize: pagination.pageSize })
    if (res.code === 200) {
      pendingTasks.value = res.data?.records || []
      pagination.total = res.data?.total || 0
    }
  } finally {
    pendingLoading.value = false
  }
}

async function loadMyTasks() {
  myLoading.value = true
  try {
    const res = await getMyApprovals({ page: pagination.current, pageSize: pagination.pageSize })
    if (res.code === 200) {
      myTasks.value = res.data?.records || []
      pagination.total = res.data?.total || 0
    }
  } finally {
    myLoading.value = false
  }
}

function handleSearch() {
  pagination.current = 1
  if (activeTab.value === 'pending') loadPending()
  if (activeTab.value === 'my') loadMyTasks()
}

function handleReset() {
  filters.businessType = undefined
  filters.status = undefined
  handleSearch()
}

function handleRefresh() {
  if (activeTab.value === 'pending') loadPending()
  if (activeTab.value === 'my') loadMyTasks()
  message.success('刷新成功')
}

function handlePageChange(page: number) {
  pagination.current = page
  handleRefresh()
}

function handleSizeChange(_current: number, size: number) {
  pagination.current = 1
  pagination.pageSize = size
  handleRefresh()
}

async function handleApprove(task: ApprovalTask) {
  try {
    await approveTask(task.id)
    message.success('已通过')
    loadPending()
  } catch (error) {
    message.error('操作失败')
  }
}

function showRejectModal(task: ApprovalTask) {
  currentTask.value = task
  rejectComment.value = ''
  rejectVisible.value = true
}

async function handleReject() {
  if (!currentTask.value) return
  try {
    await rejectTask(currentTask.value.id, rejectComment.value)
    message.success('已拒绝')
    rejectVisible.value = false
    loadPending()
  } catch (error) {
    message.error('操作失败')
  }
}

async function viewDetail(task: ApprovalTask) {
  try {
    const res = await getApprovalDetail(task.id)
    if (res.code === 200) {
      currentTask.value = res.data?.task || null
      currentRecords.value = res.data?.records || []
      detailVisible.value = true
    }
  } catch (error) {
    message.error('加载失败')
  }
}

function handleTabChange() {
  pagination.current = 1
  if (activeTab.value === 'pending') loadPending()
  if (activeTab.value === 'my') loadMyTasks()
}

onMounted(() => {
  loadPending()
})
</script>

<style scoped lang="scss">
.approval-container {
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

  .approval-tabs {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;

    :deep(.ant-tabs-content) {
      flex: 1;
      min-height: 0;
    }

    :deep(.ant-tabs-tabpane) {
      height: 100%;
    }
  }

  .approval-table {
    height: 100%;

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
}
</style>
