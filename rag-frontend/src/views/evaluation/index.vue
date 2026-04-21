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
      title="评估详情"
      width="960"
      :destroy-on-close="true"
      root-class-name="evaluation-detail-drawer"
    >
      <template v-if="currentTask">
        <!-- 任务概览横幅 -->
        <div class="task-banner">
          <div class="banner-content">
            <div class="banner-left">
              <div class="banner-header">
                <div class="task-icon">
                  <FileTextOutlined />
                </div>
                <div class="task-info">
                  <h2 class="task-name">{{ currentTask.taskName || '未命名任务' }}</h2>
                  <div class="task-meta">
                    <span class="meta-dot">
                      <DatabaseOutlined />
                      {{ currentTask.knowledgeName || '未知知识库' }}
                    </span>
                    <span class="meta-separator">|</span>
                    <span class="meta-dot">
                      <ClockCircleOutlined />
                      {{ currentTask.createTime }}
                    </span>
                  </div>
                </div>
              </div>
            </div>
            <div class="banner-right">
              <div class="status-badge" :class="'status-' + currentTask.status">
                <span class="status-dot"></span>
                {{ statusText(currentTask.status) }}
              </div>
            </div>
          </div>
        </div>

        <!-- 核心指标 -->
        <div class="metrics-panel">
          <div class="panel-title">核心指标</div>
          <div class="metrics-row">
            <div class="metric-item metric-hit">
              <div class="metric-visual">
                <svg viewBox="0 0 36 36" class="metric-ring">
                  <path
                    class="ring-bg"
                    d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                  />
                  <path
                    class="ring-fill"
                    :stroke-dasharray="`${(currentTask.hitRate || 0) * 100}, 100`"
                    d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                  />
                </svg>
                <div class="metric-ring-value">
                  {{ currentTask.hitRate ? (currentTask.hitRate * 100).toFixed(0) : 0 }}<span class="percent">%</span>
                </div>
              </div>
              <div class="metric-info">
                <div class="metric-name">Hit Rate</div>
                <div class="metric-desc">命中率</div>
              </div>
            </div>

            <div class="metric-divider"></div>

            <div class="metric-item metric-mrr">
              <div class="metric-visual">
                <div class="metric-big-value">
                  {{ currentTask.mrr ? currentTask.mrr.toFixed(3) : '-' }}
                </div>
              </div>
              <div class="metric-info">
                <div class="metric-name">MRR</div>
                <div class="metric-desc">平均倒数排名</div>
              </div>
            </div>

            <div class="metric-divider"></div>

            <div class="metric-item metric-recall">
              <div class="metric-visual">
                <div class="metric-bar-visual">
                  <div class="bar-track">
                    <div class="bar-fill" :style="{ width: (currentTask.avgRecall || 0) * 100 + '%' }"></div>
                  </div>
                  <div class="bar-value">
                    {{ currentTask.avgRecall ? (currentTask.avgRecall * 100).toFixed(0) : 0 }}%
                  </div>
                </div>
              </div>
              <div class="metric-info">
                <div class="metric-name">Recall@K</div>
                <div class="metric-desc">召回率</div>
              </div>
            </div>

            <div class="metric-divider"></div>

            <div class="metric-item metric-count">
              <div class="metric-visual">
                <div class="metric-big-value highlight">
                  {{ currentTask.qaCount || 0 }}
                </div>
              </div>
              <div class="metric-info">
                <div class="metric-name">测试问题</div>
                <div class="metric-desc">QA 数量</div>
              </div>
            </div>
          </div>
        </div>

        <!-- 命中统计 -->
        <div class="hit-stats" v-if="qaList.length > 0">
          <div class="stats-bar">
            <div class="stats-segment stats-hit" :style="{ width: (hitCount / qaList.length * 100) + '%' }">
              <CheckCircleOutlined />
              <span class="segment-num">{{ hitCount }}</span>
              <span class="segment-label">命中</span>
            </div>
            <div class="stats-segment stats-miss" :style="{ width: (missCount / qaList.length * 100) + '%' }">
              <CloseCircleOutlined />
              <span class="segment-num">{{ missCount }}</span>
              <span class="segment-label">未命中</span>
            </div>
          </div>
          <div class="stats-footer" v-if="avgHitRank">
            <TrophyOutlined class="trophy-icon" />
            <span>平均命中排名</span>
            <span class="rank-value">第 {{ avgHitRank }} 位</span>
          </div>
        </div>

        <!-- QA 详情列表 -->
        <div class="qa-section">
          <div class="section-header">
            <h3 class="section-title">QA 评估详情</h3>
            <a-input-search
              v-model:value="qaSearchText"
              placeholder="搜索问题..."
              style="width: 240px"
              allow-clear
            />
          </div>
          <a-table
            :columns="qaColumns"
            :data-source="filteredQaList"
            :loading="loadingQa"
            row-key="id"
            :pagination="{ pageSize: 10, showSizeChanger: true, showQuickJumper: true }"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'hit'">
                <a-tag :color="record.hit ? 'success' : 'error'" style="font-weight: 500">
                  <template #icon>
                    <CheckCircleOutlined v-if="record.hit" />
                    <CloseCircleOutlined v-else />
                  </template>
                  {{ record.hit ? '命中' : '未命中' }}
                </a-tag>
              </template>
              <template v-else-if="column.key === 'hitRank'">
                <a-badge
                  v-if="record.hitRank"
                  :count="record.hitRank"
                  :number-style="{ backgroundColor: record.hitRank <= 3 ? '#52c41a' : '#1890ff' }"
                />
                <span v-else class="text-muted">-</span>
              </template>
              <template v-else-if="column.key === 'question'">
                <a-tooltip :title="record.question">
                  <span class="text-ellipsis">{{ record.question }}</span>
                </a-tooltip>
              </template>
              <template v-else-if="column.key === 'action'">
                <a-button type="link" size="small" @click="showQaDetail(record)">
                  查看详情
                </a-button>
              </template>
            </template>
            <template #expandedRowRender="{ record }">
              <div class="qa-expand-content">
                <div class="expand-section">
                  <div class="expand-label">期望答案</div>
                  <div class="expand-value">{{ record.expectedAnswer }}</div>
                </div>
                <div class="expand-section" v-if="record.sourceChunkContent">
                  <div class="expand-label">源分块内容</div>
                  <div class="expand-value chunk-content">{{ record.sourceChunkContent }}</div>
                </div>
              </div>
            </template>
          </a-table>
        </div>
      </template>
    </a-drawer>

    <!-- QA 详情模态框 -->
    <a-modal
      v-model:open="qaDetailVisible"
      :title="'QA 详情 - ' + (selectedQa?.question?.substring(0, 30) + '...' || '')"
      width="800"
      :footer="null"
    >
      <template v-if="selectedQa">
        <a-descriptions :column="2" bordered size="small">
          <a-descriptions-item label="问题" :span="2">
            {{ selectedQa.question }}
          </a-descriptions-item>
          <a-descriptions-item label="是否命中">
            <a-tag :color="selectedQa.hit ? 'success' : 'error'">
              {{ selectedQa.hit ? '命中' : '未命中' }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="命中排名">
            {{ selectedQa.hitRank ? '第 ' + selectedQa.hitRank + ' 位' : '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="期望答案" :span="2">
            {{ selectedQa.expectedAnswer }}
          </a-descriptions-item>
          <a-descriptions-item label="源分块ID" :span="2">
            <a-typography-text copyable>
              {{ selectedQa.sourceChunkId || '-' }}
            </a-typography-text>
          </a-descriptions-item>
          <a-descriptions-item label="源分块内容" :span="2">
            <div class="chunk-preview">{{ selectedQa.sourceChunkContent || '-' }}</div>
          </a-descriptions-item>
          <a-descriptions-item label="检索到的分块IDs" :span="2">
            <a-typography-text copyable>
              {{ selectedQa.retrievedChunkIds || '-' }}
            </a-typography-text>
          </a-descriptions-item>
        </a-descriptions>
      </template>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import {
  DatabaseOutlined,
  ClockCircleOutlined,
  FileTextOutlined,
  CheckCircleFilled,
  SortAscendingOutlined,
  SearchOutlined,
  QuestionCircleOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  TrophyOutlined,
} from '@ant-design/icons-vue'
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
const qaSearchText = ref('')
const qaDetailVisible = ref(false)
const selectedQa = ref<EvaluationQa | null>(null)

const generateForm = reactive({
  knowledgeId: null as number | null,
  qaCount: 10,
  taskName: '',
})

const taskColumns = [
  { title: '任务名称', dataIndex: 'taskName', key: 'taskName' },
  { title: '知识库', dataIndex: 'knowledgeName', key: 'knowledgeName', width: 120 },
  { title: 'QA数量', dataIndex: 'qaCount', key: 'qaCount', width: 80 },
  { title: 'Hit Rate', key: 'hitRate', width: 100 },
  { title: 'MRR', key: 'mrr', width: 100 },
  { title: 'Recall@K', key: 'avgRecall', width: 100 },
  { title: '状态', key: 'status', width: 80 },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 170 },
  { title: '操作', key: 'action', width: 140 },
]

const qaColumns = [
  { title: '问题', dataIndex: 'question', key: 'question', ellipsis: true, width: 300 },
  { title: '是否命中', key: 'hit', width: 100 },
  { title: '命中排名', key: 'hitRank', width: 100 },
  { title: '操作', key: 'action', width: 100 },
]

// 计算属性
const hitCount = computed(() => qaList.value.filter(q => q.hit).length)
const missCount = computed(() => qaList.value.filter(q => !q.hit).length)
const avgHitRank = computed(() => {
  const hitRanks = qaList.value.filter(q => q.hitRank).map(q => q.hitRank!)
  if (hitRanks.length === 0) return null
  return (hitRanks.reduce((a, b) => a + b, 0) / hitRanks.length).toFixed(1)
})
const filteredQaList = computed(() => {
  if (!qaSearchText.value) return qaList.value
  const search = qaSearchText.value.toLowerCase()
  return qaList.value.filter(q => q.question.toLowerCase().includes(search))
})

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
  loadingTasks.value = true
  try {
    const res = await getEvaluationList()
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
  qaSearchText.value = ''
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

function showQaDetail(record: EvaluationQa) {
  selectedQa.value = record
  qaDetailVisible.value = true
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

onMounted(() => {
  loadKnowledgeBases()
  loadTasks()
})
</script>

<style scoped lang="scss">
.evaluation-page {
  .text-ellipsis {
    display: inline-block;
    max-width: 280px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    vertical-align: bottom;
  }

  .text-muted {
    color: #bbb;
  }
}
</style>

<style lang="scss">
// 抽屉详情样式 - 全局样式
.evaluation-detail-drawer {
  // 抽屉容器
  .ant-drawer-content-wrapper {
    background: #f8fafc;
  }

  .ant-drawer-body {
    padding: 0 !important;
    background: #f8fafc;
    overflow-y: auto;
  }

  .ant-drawer-header {
    background: #fff;
    border-bottom: 1px solid #f0f0f0;
    padding: 16px 24px;
  }

  .ant-drawer-title {
    font-weight: 600;
    font-size: 16px;
    color: #1f1f1f;
  }

  // 任务概览横幅
  .task-banner {
    background: linear-gradient(135deg, #1e1b4b 0%, #312e81 40%, #4338ca 100%);
    padding: 24px;
    margin-bottom: 20px;

    .banner-content {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }

    .banner-header {
      display: flex;
      align-items: center;
      gap: 14px;
    }

    .task-icon {
      width: 44px;
      height: 44px;
      border-radius: 12px;
      background: rgba(255, 255, 255, 0.12);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 20px;
      color: rgba(255, 255, 255, 0.9);
      flex-shrink: 0;
    }

    .task-info {
      .task-name {
        margin: 0;
        font-size: 18px;
        font-weight: 600;
        color: #fff;
        line-height: 1.3;
      }

      .task-meta {
        display: flex;
        align-items: center;
        gap: 6px;
        margin-top: 5px;
        font-size: 13px;
        color: rgba(255, 255, 255, 0.6);

        .meta-dot {
          display: inline-flex;
          align-items: center;
          gap: 4px;
        }

        .meta-separator {
          opacity: 0.3;
        }

        .anticon {
          font-size: 12px;
        }
      }
    }

    .status-badge {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 5px 14px;
      border-radius: 20px;
      font-size: 13px;
      font-weight: 500;
      background: rgba(255, 255, 255, 0.1);
      color: rgba(255, 255, 255, 0.85);

      .status-dot {
        width: 7px;
        height: 7px;
        border-radius: 50%;
      }

      &.status-0 .status-dot { background: #d9d9d9; }
      &.status-1 .status-dot { background: #69b1ff; animation: pulse 1.5s infinite; }
      &.status-2 .status-dot { background: #73d13d; }
      &.status-3 .status-dot { background: #ff7875; }
    }
  }

  @keyframes pulse {
    0%, 100% { opacity: 1; }
    50% { opacity: 0.4; }
  }

  // 核心指标面板
  .metrics-panel {
    background: #fff;
    border: 1px solid #f0f0f0;
    border-radius: 12px;
    padding: 20px;
    margin: 0 24px 20px;

    .panel-title {
      font-size: 13px;
      font-weight: 500;
      color: #8c8c8c;
      text-transform: uppercase;
      letter-spacing: 1px;
      margin-bottom: 18px;
    }

    .metrics-row {
      display: flex;
      align-items: center;
    }

    .metric-divider {
      width: 1px;
      height: 56px;
      background: #f0f0f0;
      flex-shrink: 0;
      margin: 0 4px;
    }

    .metric-item {
      flex: 1;
      display: flex;
      align-items: center;
      gap: 14px;
      padding: 0 12px;
    }

    .metric-visual {
      flex-shrink: 0;
      position: relative;
    }

    .metric-info {
      .metric-name {
        font-size: 14px;
        font-weight: 600;
        color: #262626;
      }

      .metric-desc {
        font-size: 12px;
        color: #bfbfbf;
        margin-top: 2px;
      }
    }
  }

  // 圆环 - Hit Rate
  .metric-ring {
    display: block;
    width: 60px;
    height: 60px;
    transform: rotate(-90deg);

    path {
      fill: none;
    }

    .ring-bg {
      stroke: #f0f0f0;
      stroke-width: 3;
    }

    .ring-fill {
      stroke: #1890ff;
      stroke-width: 3;
      stroke-linecap: round;
      transition: stroke-dasharray 0.6s ease;
    }
  }

  .metric-ring-value {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 14px;
    font-weight: 700;
    color: #1890ff;

    .percent {
      font-size: 10px;
      font-weight: 500;
    }
  }

  .metric-hit .metric-name { color: #0958d9; }
  .metric-mrr .metric-name { color: #237804; }
  .metric-recall .metric-name { color: #d46b08; }
  .metric-count .metric-name { color: #531dab; }

  // 大数字 - MRR
  .metric-big-value {
    width: 60px;
    text-align: center;
    font-size: 22px;
    font-weight: 700;
    color: #389e0d;
    line-height: 60px;

    &.highlight {
      color: #531dab;
    }
  }

  // 横条 - Recall
  .metric-bar-visual {
    width: 60px;

    .bar-track {
      height: 8px;
      border-radius: 4px;
      background: #f5f5f5;
      overflow: hidden;

      .bar-fill {
        height: 100%;
        border-radius: 4px;
        background: linear-gradient(90deg, #fa8c16, #faad14);
        transition: width 0.6s ease;
      }
    }

    .bar-value {
      text-align: center;
      font-size: 14px;
      font-weight: 700;
      color: #d46b08;
      margin-top: 6px;
    }
  }

  // 命中统计
  .hit-stats {
    margin: 0 24px 24px;

    .stats-bar {
      display: flex;
      border-radius: 10px;
      overflow: hidden;
      height: 42px;
      font-size: 14px;
    }

    .stats-segment {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 6px;
      transition: width 0.5s ease;
      min-width: 80px;

      .segment-num {
        font-weight: 700;
        font-size: 16px;
      }

      .segment-label {
        opacity: 0.8;
      }
    }

    .stats-hit {
      background: #f6ffed;
      color: #389e0d;
    }

    .stats-miss {
      background: #fff2f0;
      color: #cf1322;
    }

    .stats-footer {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      margin-top: 10px;
      padding: 8px;
      background: #fffbe6;
      border-radius: 8px;
      font-size: 13px;
      color: #8c8c8c;

      .trophy-icon {
        color: #faad14;
        font-size: 15px;
      }

      .rank-value {
        font-weight: 600;
        color: #d48806;
      }
    }
  }

  // QA 列表区域
  .qa-section {
    padding: 20px 24px;
    background: #fff;
    margin: 0 24px 24px;
    border-radius: 12px;
    border: 1px solid #f0f0f0;

    .section-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;

      .section-title {
        margin: 0;
        font-size: 16px;
        font-weight: 600;
        color: #1a1a2e;
      }
    }
  }

  // 展开行内容
  .qa-expand-content {
    padding: 12px 16px;
    background: #fafafa;
    border-radius: 6px;

    .expand-section {
      margin-bottom: 12px;

      &:last-child {
        margin-bottom: 0;
      }

      .expand-label {
        font-size: 12px;
        color: #999;
        margin-bottom: 4px;
      }

      .expand-value {
        font-size: 14px;
        color: #333;
        line-height: 1.6;

        &.chunk-content {
          padding: 8px 12px;
          background: #fff;
          border-radius: 4px;
          border-left: 3px solid #1890ff;
        }
      }
    }
  }

  .text-ellipsis {
    display: inline-block;
    max-width: 280px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    vertical-align: bottom;
  }

  .text-muted {
    color: #bbb;
  }
}

// 主页面样式（非抽屉内容）
.evaluation-page {
  .text-ellipsis {
    display: inline-block;
    max-width: 280px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    vertical-align: bottom;
  }

  .text-muted {
    color: #bbb;
  }
}

// 响应式
@media (max-width: 768px) {
  .evaluation-detail-drawer {
    .metrics-row {
      flex-wrap: wrap;
    }

    .metric-divider {
      display: none;
    }

    .metric-item {
      flex: 0 0 50%;
      padding: 8px 0;
    }
  }
}
</style>
