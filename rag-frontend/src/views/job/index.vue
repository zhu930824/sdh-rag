<template>
  <div class="job-page">
    <div class="page-header">
      <div>
        <h1>任务中心</h1>
        <p>查看后台任务的排队、租约、重试、取消和死信状态。</p>
      </div>
      <a-button :loading="loading" @click="loadJobs">刷新</a-button>
    </div>

    <a-alert class="guide" type="info" show-icon message="任务由数据库可靠队列驱动；进程重启后会自动接管租约过期的任务，重复执行由业务幂等键保护。" />

    <a-card>
      <div class="filters">
        <a-select v-model:value="filters.status" allow-clear placeholder="全部状态" style="width: 180px" @change="resetAndLoad">
          <a-select-option v-for="option in statusOptions" :key="option.value" :value="option.value">{{ option.label }}</a-select-option>
        </a-select>
        <a-select v-model:value="filters.jobType" allow-clear placeholder="全部类型" style="width: 180px" @change="resetAndLoad">
          <a-select-option value="RAG_EVALUATION">RAG 评测</a-select-option>
          <a-select-option value="KNOWLEDGE_SOURCE_PULL">知识源同步</a-select-option>
        </a-select>
      </div>

      <a-table :columns="columns" :data-source="records" :loading="loading" row-key="id"
               :pagination="pagination" @change="handleTableChange">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'type'">
            <a-tag color="blue">{{ typeText(record.jobType) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">{{ statusText(record.status) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'attempts'">
            {{ record.attempts }} / {{ record.maxAttempts }}
          </template>
          <template v-else-if="column.key === 'traceId'">
            <a-typography-text copyable class="trace">{{ record.traceId }}</a-typography-text>
          </template>
          <template v-else-if="column.key === 'result'">
            <a-tooltip :title="record.errorMessage || record.resultSummary">
              <span :class="{ error: record.errorMessage }">{{ record.errorMessage || record.resultSummary || '-' }}</span>
            </a-tooltip>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-popconfirm v-if="canCancel(record.status)" title="确定取消该任务？" @confirm="handleCancel(record)">
                <a-button type="link" size="small" danger>取消</a-button>
              </a-popconfirm>
              <a-button v-if="canReplay(record.status)" type="link" size="small" @click="handleReplay(record)">重放</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { cancelJob, getJobs, replayJob, type PlatformJob } from '@/api/job'

const loading = ref(false)
const records = ref<PlatformJob[]>([])
const filters = reactive<{ status?: string; jobType?: string }>({})
const pagination = reactive({ current: 1, pageSize: 20, total: 0, showSizeChanger: true, showTotal: (total: number) => `共 ${total} 条` })
const columns = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '类型', key: 'type', width: 120 },
  { title: '业务资源', dataIndex: 'resourceId', width: 110 },
  { title: '状态', key: 'status', width: 110 },
  { title: '尝试次数', key: 'attempts', width: 100 },
  { title: 'Trace ID', key: 'traceId', width: 210 },
  { title: '结果 / 错误', key: 'result', ellipsis: true },
  { title: '创建时间', dataIndex: 'createTime', width: 170 },
  { title: '操作', key: 'action', width: 110 },
]
const statusOptions = [
  ['PENDING', '排队中'], ['RUNNING', '运行中'], ['RETRY_WAIT', '等待重试'], ['SUCCEEDED', '成功'], ['FAILED', '失败'],
  ['DEAD_LETTER', '死信'], ['CANCEL_REQUESTED', '取消中'], ['CANCELLED', '已取消'],
].map(([value, label]) => ({ value, label }))

function statusText(status: string) {
  return Object.fromEntries(statusOptions.map(item => [item.value, item.label]))[status] || status
}
function statusColor(status: string) {
  return ({ PENDING: 'default', RUNNING: 'processing', RETRY_WAIT: 'orange', SUCCEEDED: 'success', FAILED: 'error', DEAD_LETTER: 'red', CANCEL_REQUESTED: 'warning', CANCELLED: 'default' } as Record<string, string>)[status] || 'default'
}
function typeText(type: string) { return ({ RAG_EVALUATION: 'RAG 评测', KNOWLEDGE_SOURCE_PULL: '知识源同步' } as Record<string, string>)[type] || type }
function canCancel(status: string) { return ['PENDING', 'RUNNING', 'RETRY_WAIT'].includes(status) }
function canReplay(status: string) { return ['FAILED', 'DEAD_LETTER', 'CANCELLED'].includes(status) }

async function loadJobs() {
  loading.value = true
  try {
    const res = await getJobs({ page: pagination.current, pageSize: pagination.pageSize, ...filters })
    if (res.code === 200) {
      records.value = res.data?.records || []
      pagination.total = res.data?.total || 0
    }
  } finally { loading.value = false }
}
function resetAndLoad() { pagination.current = 1; loadJobs() }
function handleTableChange(page: any) { pagination.current = page.current; pagination.pageSize = page.pageSize; loadJobs() }
async function handleCancel(job: any) {
  const res = await cancelJob(job.id)
  if (res.code === 200) { message.success('取消请求已提交'); loadJobs() }
}
async function handleReplay(job: any) {
  const res = await replayJob(job.id)
  if (res.code === 200) { message.success('任务已重新进入队列'); loadJobs() }
}

let timer: ReturnType<typeof setInterval> | undefined
onMounted(() => { loadJobs(); timer = setInterval(loadJobs, 5000) })
onUnmounted(() => { if (timer) clearInterval(timer) })
</script>

<style scoped lang="scss">
.job-page { padding: 24px; }
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; }
.page-header h1 { margin: 0 0 6px; font-size: 24px; }
.page-header p { margin: 0; color: #8c8c8c; }
.guide { margin-bottom: 16px; }
.filters { display: flex; gap: 12px; margin-bottom: 16px; }
.trace { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px; }
.error { color: #cf1322; }
</style>
