<template>
  <div class="evaluation-page">
    <!-- 生成测试集 -->
    <a-card title="检索评估" style="margin-bottom: 16px">
      <a-form layout="inline" :model="generateForm">
        <a-form-item label="知识库">
          <a-select
            v-model:value="generateForm.knowledgeId"
            style="width: 240px"
            placeholder="请选择知识库"
            :loading="loadingKbList"
          >
            <a-select-option
              v-for="kb in knowledgeBases"
              :key="kb.id"
              :value="kb.id"
            >
              {{ kb.name }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="QA数量">
          <a-input-number
            v-model:value="generateForm.qaCount"
            :min="3"
            :max="50"
            style="width: 120px"
          />
        </a-form-item>
        <a-form-item label="任务名称">
          <a-input
            v-model:value="generateForm.taskName"
            placeholder="可选"
            style="width: 200px"
          />
        </a-form-item>
        <a-form-item>
          <a-button
            type="primary"
            :loading="generating"
            :disabled="!generateForm.knowledgeId"
            @click="handleGenerate"
          >
            生成测试集并评估
          </a-button>
        </a-form-item>
      </a-form>
    </a-card>

    <!-- 评估任务列表 -->
    <a-card title="评估记录">
      <a-table
        :columns="taskColumns"
        :data-source="tasks"
        :loading="loadingTasks"
        row-key="id"
        :pagination="false"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">{{ statusText(record.status) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'hitRate'">
            <span v-if="record.hitRate !== null">{{ (record.hitRate * 100).toFixed(1) }}%</span>
            <span v-else>-</span>
          </template>
          <template v-else-if="column.key === 'mrr'">
            <span v-if="record.mrr !== null">{{ record.mrr.toFixed(4) }}</span>
            <span v-else>-</span>
          </template>
          <template v-else-if="column.key === 'avgRecall'">
            <span v-if="record.avgRecall !== null">{{ (record.avgRecall * 100).toFixed(1) }}%</span>
            <span v-else>-</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="handleViewDetail(record)">查看详情</a-button>
              <a-popconfirm title="确定删除？" @confirm="handleDelete(record)">
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 评估详情抽屉 -->
    <a-drawer
      v-model:open="detailVisible"
      :title="currentTask ? currentTask.taskName : '评估详情'"
      width="900"
      :destroy-on-close="true"
    >
      <template v-if="currentTask">
        <!-- 指标概览 -->
        <div class="metrics-grid">
          <div class="metric-card">
            <div class="metric-value">{{ currentTask.hitRate ? (currentTask.hitRate * 100).toFixed(1) + '%' : '-' }}</div>
            <div class="metric-label">Hit Rate</div>
          </div>
          <div class="metric-card">
            <div class="metric-value">{{ currentTask.mrr ? currentTask.mrr.toFixed(4) : '-' }}</div>
            <div class="metric-label">MRR</div>
          </div>
          <div class="metric-card">
            <div class="metric-value">{{ currentTask.avgRecall ? (currentTask.avgRecall * 100).toFixed(1) + '%' : '-' }}</div>
            <div class="metric-label">Recall@K</div>
          </div>
          <div class="metric-card">
            <div class="metric-value">{{ currentTask.qaCount || 0 }}</div>
            <div class="metric-label">QA 数量</div>
          </div>
        </div>

        <!-- QA 详情列表 -->
        <a-divider>QA 评估详情</a-divider>
        <a-table
          :columns="qaColumns"
          :data-source="qaList"
          :loading="loadingQa"
          row-key="id"
          :pagination="{ pageSize: 10 }"
          size="small"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'hit'">
              <a-tag :color="record.hit ? 'green' : 'red'">
                {{ record.hit ? '命中' : '未命中' }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'hitRank'">
              <span v-if="record.hitRank">第 {{ record.hitRank }} 位</span>
              <span v-else>-</span>
            </template>
            <template v-else-if="column.key === 'question'">
              <a-tooltip :title="record.question">
                <span class="text-ellipsis">{{ record.question }}</span>
              </a-tooltip>
            </template>
            <template v-else-if="column.key === 'expectedAnswer'">
              <a-tooltip :title="record.expectedAnswer">
                <span class="text-ellipsis">{{ record.expectedAnswer }}</span>
              </a-tooltip>
            </template>
          </template>
        </a-table>
      </template>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { message } from 'ant-design-vue'
import type { EvaluationTask, EvaluationQa } from '@/api/evaluation'
import {
  generateTestset,
  getEvaluationList,
  getEvaluationQaList,
  deleteEvaluationTask,
} from '@/api/evaluation'
import { getKnowledgeBaseList } from '@/api/knowledgeBase'

const knowledgeBases = ref<any[]>([])
const loadingKbList = ref(false)
const generating = ref(false)
const loadingTasks = ref(false)
const tasks = ref<EvaluationTask[]>([])
const detailVisible = ref(false)
const currentTask = ref<EvaluationTask | null>(null)
const qaList = ref<EvaluationQa[]>([])
const loadingQa = ref(false)

const generateForm = reactive({
  knowledgeId: null as number | null,
  qaCount: 10,
  taskName: '',
})

const taskColumns = [
  { title: '任务名称', dataIndex: 'taskName', key: 'taskName' },
  { title: 'QA数量', dataIndex: 'qaCount', key: 'qaCount', width: 80 },
  { title: 'Hit Rate', key: 'hitRate', width: 100 },
  { title: 'MRR', key: 'mrr', width: 100 },
  { title: 'Recall@K', key: 'avgRecall', width: 100 },
  { title: '状态', key: 'status', width: 80 },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 170 },
  { title: '操作', key: 'action', width: 140 },
]

const qaColumns = [
  { title: '问题', dataIndex: 'question', key: 'question', ellipsis: true },
  { title: '期望答案', dataIndex: 'expectedAnswer', key: 'expectedAnswer', ellipsis: true },
  { title: '是否命中', key: 'hit', width: 80 },
  { title: '命中排名', key: 'hitRank', width: 90 },
]

function statusText(status: number) {
  const map: Record<number, string> = { 0: '待运行', 1: '运行中', 2: '完成', 3: '失败' }
  return map[status] || '未知'
}

function statusColor(status: number) {
  const map: Record<number, string> = { 0: 'default', 1: 'processing', 2: 'success', 3: 'error' }
  return map[status] || 'default'
}

async function loadKnowledgeBases() {
  loadingKbList.value = true
  try {
    const res = await getKnowledgeBaseList({ page: 1, pageSize: 100 })
    if (res.code === 200) {
      knowledgeBases.value = res.data.records || []
    }
  } catch (error) {
    console.error('加载知识库列表失败', error)
  } finally {
    loadingKbList.value = false
  }
}

async function loadTasks() {
  if (!generateForm.knowledgeId) {
    tasks.value = []
    return
  }
  loadingTasks.value = true
  try {
    const res = await getEvaluationList(generateForm.knowledgeId!)
    if (res.code === 200) {
      tasks.value = res.data || []
    }
  } catch (error) {
    console.error('加载评估任务失败', error)
  } finally {
    loadingTasks.value = false
  }
}

async function handleGenerate() {
  if (!generateForm.knowledgeId) {
    message.warning('请选择知识库')
    return
  }
  generating.value = true
  try {
    const res = await generateTestset({
      knowledgeId: generateForm.knowledgeId!,
      qaCount: generateForm.qaCount,
      taskName: generateForm.taskName || undefined,
    })
    if (res.code === 200) {
      message.success('评估任务已创建，正在后台运行')
      generateForm.taskName = ''
      loadTasks()
      // 定时刷新任务状态
      startPolling()
    } else {
      message.error(res.message || '创建失败')
    }
  } catch (error: any) {
    message.error(error.message || '创建失败')
  } finally {
    generating.value = false
  }
}

let pollingTimer: ReturnType<typeof setInterval> | null = null

function startPolling() {
  if (pollingTimer) clearInterval(pollingTimer)
  pollingTimer = setInterval(() => {
    const hasRunning = tasks.value.some(t => t.status === 0 || t.status === 1)
    if (hasRunning) {
      loadTasks()
    } else {
      if (pollingTimer) clearInterval(pollingTimer)
      pollingTimer = null
    }
  }, 5000)
}

async function handleViewDetail(record: EvaluationTask) {
  currentTask.value = record
  detailVisible.value = true
  loadingQa.value = true
  try {
    const res = await getEvaluationQaList(record.id)
    if (res.code === 200) {
      qaList.value = res.data || []
    }
  } catch (error) {
    console.error('加载QA列表失败', error)
  } finally {
    loadingQa.value = false
  }
}

async function handleDelete(record: EvaluationTask) {
  try {
    const res = await deleteEvaluationTask(record.id)
    if (res.code === 200) {
      message.success('删除成功')
      loadTasks()
    }
  } catch (error) {
    console.error('删除失败', error)
  }
}

watch(() => generateForm.knowledgeId, () => {
  loadTasks()
})

onMounted(() => {
  loadKnowledgeBases()
})
</script>

<style scoped lang="scss">
.evaluation-page {
  .metrics-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 16px;
    margin-bottom: 16px;
  }

  .metric-card {
    text-align: center;
    padding: 16px;
    background: var(--bg-color-secondary, #fafafa);
    border-radius: 8px;

    .metric-value {
      font-size: 24px;
      font-weight: 600;
      color: var(--primary-color, #1890ff);
    }

    .metric-label {
      margin-top: 4px;
      font-size: 13px;
      color: var(--text-tertiary, #999);
    }
  }

  .text-ellipsis {
    display: inline-block;
    max-width: 200px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    vertical-align: bottom;
  }
}
</style>
