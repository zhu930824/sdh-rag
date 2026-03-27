<template>
  <div class="approval-page">
    <div class="page-header">
      <h2>审核中心</h2>
    </div>

    <a-tabs v-model:activeKey="activeTab">
      <a-tab-pane key="pending" tab="待审核">
        <a-table :columns="columns" :data-source="pendingTasks" :loading="pendingLoading" :pagination="false" row-key="id">
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
        <a-table :columns="columns" :data-source="myTasks" :loading="myLoading" :pagination="false" row-key="id">
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

    <a-modal
      v-model:open="rejectVisible"
      title="拒绝原因"
      @ok="handleReject"
    >
      <a-textarea v-model:value="rejectComment" :rows="3" placeholder="请输入拒绝原因" />
    </a-modal>

    <a-modal
      v-model:open="detailVisible"
      title="审核详情"
      :width="600"
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
import { ref, onMounted, watch } from 'vue'
import { message } from 'ant-design-vue'
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
    const res = await getPendingApprovals({ page: 1, pageSize: 100 })
    if (res.code === 200) {
      pendingTasks.value = res.data?.records || []
    }
  } finally {
    pendingLoading.value = false
  }
}

async function loadMyTasks() {
  myLoading.value = true
  try {
    const res = await getMyApprovals({ page: 1, pageSize: 100 })
    if (res.code === 200) {
      myTasks.value = res.data?.records || []
    }
  } finally {
    myLoading.value = false
  }
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

watch(activeTab, (val) => {
  if (val === 'pending') loadPending()
  if (val === 'my') loadMyTasks()
})

onMounted(() => {
  loadPending()
})
</script>

<style scoped lang="scss">
.approval-page {
  padding: 24px;

  .page-header {
    margin-bottom: 24px;
    h2 { margin: 0; font-size: 20px; font-weight: 600; }
  }
}
</style>