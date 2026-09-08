<template>
  <div class="evaluation-lab page-container">
    <div class="page-header">
      <div>
        <h1>评测实验室</h1>
        <p>用同一份数据集对比多套 RAG 配置，持续发现质量回归，并用人工复核校准自动评审。</p>
      </div>
      <a-button v-if="activeTab === 'experiments'" type="primary" @click="openCreate">新建对比实验</a-button>
    </div>

    <a-alert class="ops-tip" type="info" show-icon>
      <template #message>定时实验会在后台按 Cron 运行；Webhook 订阅 <code>evaluation.regression</code> 和 <code>evaluation.run_failed</code> 可接收告警。</template>
    </a-alert>

    <a-tabs v-model:activeKey="activeTab">
      <a-tab-pane key="experiments" tab="对比实验">
        <a-table :data-source="experiments" :columns="experimentColumns" row-key="id" :loading="loadingExperiments" :pagination="false">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'name'">
              <a-button type="link" class="name-link" @click="openDetail(record.id)">{{ record.name }}</a-button>
              <div class="muted">知识库 #{{ record.knowledgeId }} · 数据集 #{{ record.datasetId }}</div>
            </template>
            <template v-else-if="column.key === 'schedule'">
              <a-tag :color="record.scheduleEnabled ? 'blue' : 'default'">{{ record.scheduleEnabled ? '已定时' : '仅手动' }}</a-tag>
              <div v-if="record.nextRunTime" class="muted">下次 {{ formatTime(record.nextRunTime) }}</div>
            </template>
            <template v-else-if="column.key === 'budget'">
              {{ record.judgeBudget ? `${record.judgeBudget} 次/批` : '不限' }}
              <div class="muted">失败重试 {{ record.maxRetries }} 次</div>
            </template>
            <template v-else-if="column.key === 'status'">
              <a-badge :status="record.status === 'ACTIVE' ? 'success' : 'default'" :text="record.status === 'ACTIVE' ? '运行中' : '已暂停'" />
            </template>
            <template v-else-if="column.key === 'action'">
              <a-space>
                <a-button size="small" @click="runExperiment(record)">立即运行</a-button>
                <a-button size="small" @click="toggleExperiment(record)">{{ record.status === 'ACTIVE' ? '暂停' : '启用' }}</a-button>
                <a-popconfirm title="删除实验配置和运行记录？评测任务本身会保留。" @confirm="removeExperiment(record)">
                  <a-button size="small" danger>删除</a-button>
                </a-popconfirm>
              </a-space>
            </template>
          </template>
          <template #emptyText><a-empty description="暂无实验，先创建两套配置做一次可重复的对比" /></template>
        </a-table>
      </a-tab-pane>

      <a-tab-pane key="slices" tab="场景切片">
        <div class="toolbar">
          <a-select v-model:value="sliceTaskId" show-search option-filter-prop="label" placeholder="选择已完成评测任务" style="width: 360px" @change="loadSlices">
            <a-select-option v-for="task in completedTasks" :key="task.id" :value="task.id" :label="task.taskName">{{ task.taskName }} · #{{ task.id }}</a-select-option>
          </a-select>
          <a-select v-model:value="sliceDimension" style="width: 150px" @change="loadSlices">
            <a-select-option value="category">场景分类</a-select-option>
            <a-select-option value="difficulty">难度</a-select-option>
            <a-select-option value="businessLine">业务线</a-select-option>
            <a-select-option value="userRole">用户角色</a-select-option>
          </a-select>
        </div>
        <a-table :data-source="slices" :columns="sliceColumns" row-key="value" :loading="loadingSlices" :pagination="false">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'failureRate'">{{ record.sampleCount ? percent(record.failureCount / record.sampleCount) : '-' }}</template>
            <template v-else-if="['recallAtK','mrr','faithfulness','answerRelevancy','overallScore'].includes(column.key)">{{ score(record[column.key]) }}</template>
          </template>
          <template #emptyText><a-empty description="选择一个评测任务查看不同业务场景的质量差异" /></template>
        </a-table>
      </a-tab-pane>

      <a-tab-pane key="reviews" tab="人工复核">
        <div class="toolbar">
          <a-select v-model:value="reviewTaskId" allow-clear show-search option-filter-prop="label" placeholder="全部评测任务" style="width: 360px" @change="loadReviews">
            <a-select-option v-for="task in completedTasks" :key="task.id" :value="task.id" :label="task.taskName">{{ task.taskName }} · #{{ task.id }}</a-select-option>
          </a-select>
          <a-radio-group v-model:value="reviewStatus" button-style="solid" @change="loadReviews">
            <a-radio-button value="PENDING">待复核</a-radio-button>
            <a-radio-button value="COMPLETED">已复核</a-radio-button>
            <a-radio-button value="SKIPPED">已跳过</a-radio-button>
          </a-radio-group>
          <a-button v-if="reviewTaskId" @click="loadAlignment">刷新人机偏差</a-button>
        </div>

        <div v-if="alignment" class="alignment-grid">
          <div class="metric-card"><span>复核样本</span><strong>{{ alignment.reviewedCount }}</strong></div>
          <div class="metric-card"><span>总体分 MAE</span><strong>{{ score(alignment.overallMae) }}</strong></div>
          <div class="metric-card"><span>忠实度 MAE</span><strong>{{ score(alignment.faithfulnessMae) }}</strong></div>
          <div class="metric-card"><span>相差 ≤ 1 档</span><strong>{{ score(alignment.withinOneLevelRate) }}</strong></div>
        </div>

        <a-table :data-source="reviews" :columns="reviewColumns" row-key="id" :loading="loadingReviews"
                 :pagination="reviewPagination" @change="onReviewPageChange">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'question'">
              <div class="question">{{ record.question }}</div>
              <div class="muted">任务 #{{ record.taskId }} · {{ record.category || '未分类' }} · {{ record.difficulty || '未标难度' }}</div>
            </template>
            <template v-else-if="column.key === 'autoScore'">
              <div>总体 {{ score(record.overallScore) }}</div>
              <div class="muted">忠实度 {{ score(record.faithfulness) }}</div>
            </template>
            <template v-else-if="column.key === 'humanScore'">{{ score(record.humanOverallScore) }}</template>
            <template v-else-if="column.key === 'action'">
              <a-space>
                <a-button size="small" type="primary" ghost @click="openReview(record)">复核</a-button>
                <a-button v-if="reviewStatus === 'PENDING'" size="small" @click="skipReview(record)">跳过</a-button>
              </a-space>
            </template>
          </template>
        </a-table>
      </a-tab-pane>
    </a-tabs>

    <a-modal v-model:open="createVisible" title="新建对比实验" width="820px" :confirm-loading="savingExperiment" @ok="saveExperiment">
      <a-form layout="vertical">
        <div class="form-grid">
          <a-form-item label="实验名称" required><a-input v-model:value="experimentForm.name" placeholder="例如：混合检索 Top K 对比" /></a-form-item>
          <a-form-item label="知识库" required>
            <a-select v-model:value="experimentForm.knowledgeId" show-search option-filter-prop="label">
              <a-select-option v-for="kb in knowledgeBases" :key="kb.id" :value="kb.id" :label="kb.name">{{ kb.name }}</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="测试数据集" required>
            <a-select v-model:value="experimentForm.datasetId" show-search option-filter-prop="label">
              <a-select-option v-for="dataset in datasets" :key="dataset.id" :value="dataset.id" :label="dataset.name">{{ dataset.name }} · v{{ dataset.version || 1 }} · {{ dataset.itemCount }} 条</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="单批评审预算"><a-input-number v-model:value="experimentForm.judgeBudget" :min="0" :max="100000" style="width:100%" /><div class="muted">0 表示不限；仅生成/完整评测计入调用数</div></a-form-item>
        </div>
        <a-form-item label="说明"><a-textarea v-model:value="experimentForm.description" :rows="2" /></a-form-item>

        <div class="section-title">对比变体 <span>至少两套，除 Top K 外也可对比评测层级、模型和基线</span></div>
        <div v-for="(variant, index) in experimentForm.variants" :key="index" class="variant-row">
          <a-input v-model:value="variant.name" placeholder="变体名称" />
          <a-select v-model:value="variant.evaluationMode">
            <a-select-option value="RETRIEVAL">检索</a-select-option>
            <a-select-option value="GENERATION">生成</a-select-option>
            <a-select-option value="FULL">完整</a-select-option>
          </a-select>
          <a-input-number v-model:value="variant.topK" :min="1" :max="20" addon-before="Top K" />
          <a-input v-model:value="variant.judgeModel" :disabled="variant.evaluationMode === 'RETRIEVAL'" placeholder="评审模型（可选）" />
          <a-select v-model:value="variant.baselineTaskId" allow-clear show-search option-filter-prop="label" placeholder="回归基线（可选）">
            <a-select-option v-for="task in compatibleBaselines(variant)" :key="task.id" :value="task.id" :label="task.taskName">#{{ task.id }} {{ task.taskName }}</a-select-option>
          </a-select>
          <a-button v-if="experimentForm.variants.length > 2" danger type="text" @click="experimentForm.variants.splice(index, 1)">移除</a-button>
        </div>
        <a-button type="dashed" block @click="addVariant">增加变体</a-button>

        <div class="schedule-row">
          <a-switch v-model:checked="experimentForm.scheduleEnabled" />
          <span>启用定时回归</span>
          <a-input v-if="experimentForm.scheduleEnabled" v-model:value="experimentForm.cronExpression" placeholder="Spring Cron，如 0 0 2 * * *" style="width:260px" />
          <span>失败重试</span>
          <a-input-number v-model:value="experimentForm.maxRetries" :min="0" :max="3" />
          <a-checkbox v-model:checked="experimentForm.alertOnRegression">回归时告警</a-checkbox>
        </div>
      </a-form>
    </a-modal>

    <a-drawer v-model:open="detailVisible" title="实验运行详情" width="980" @close="selectedDetail = null">
      <template v-if="selectedDetail">
        <div class="variant-cards">
          <div v-for="variant in selectedDetail.variants" :key="variant.id" class="variant-card">
            <strong>{{ variant.name }}</strong>
            <span>{{ variant.evaluationMode }} · Top {{ variant.topK }}</span>
            <span v-if="variant.judgeModel">{{ variant.judgeModel }}</span>
          </div>
        </div>
        <a-collapse accordion>
          <a-collapse-panel v-for="item in selectedDetail.runs" :key="item.run.id">
            <template #header>
              <a-space><strong>批次 #{{ item.run.id }}</strong><a-tag>{{ item.run.triggerType === 'SCHEDULED' ? '定时' : '手动' }}</a-tag><a-tag :color="runColor(item.run.status)">{{ runStatus(item.run.status) }}</a-tag><span class="muted">{{ formatTime(item.run.startTime) }}</span></a-space>
            </template>
            <a-alert v-if="item.run.errorMessage" type="error" :message="item.run.errorMessage" show-icon class="run-error" />
            <a-table :data-source="item.results" row-key="variant.id" :pagination="false" size="small">
              <a-table-column title="变体" key="variant"><template #default="{ record }">{{ record.variant?.name || '-' }}<div class="muted">第 {{ record.attempt + 1 }} 次尝试</div></template></a-table-column>
              <a-table-column title="状态" key="status"><template #default="{ record }">{{ taskStatus(record.task?.status) }}</template></a-table-column>
              <a-table-column title="Recall@K" key="recall"><template #default="{ record }">{{ score(record.task?.recallAtK) }}</template></a-table-column>
              <a-table-column title="MRR" key="mrr"><template #default="{ record }">{{ score(record.task?.mrr) }}</template></a-table-column>
              <a-table-column title="忠实度" key="faith"><template #default="{ record }">{{ score(record.task?.faithfulness) }}</template></a-table-column>
              <a-table-column title="总体分" key="overall"><template #default="{ record }">{{ score(record.task?.overallScore) }}</template></a-table-column>
              <a-table-column title="回归" key="regression"><template #default="{ record }"><a-tag :color="record.task?.regressionStatus === 'FAILED' ? 'red' : 'default'">{{ regressionStatus(record.task?.regressionStatus) }}</a-tag></template></a-table-column>
            </a-table>
          </a-collapse-panel>
        </a-collapse>
      </template>
    </a-drawer>

    <a-modal v-model:open="reviewVisible" title="人工复核自动评审" :confirm-loading="submittingReview" @ok="saveReview">
      <div v-if="selectedQa" class="review-context">
        <strong>问题</strong><p>{{ selectedQa.question }}</p>
        <strong>实际回答</strong><p>{{ selectedQa.actualAnswer || '-' }}</p>
        <strong>自动评审理由</strong><p>{{ selectedQa.judgeReason || '-' }}</p>
      </div>
      <a-form layout="vertical">
        <div class="score-grid">
          <a-form-item v-for="field in scoreFields" :key="field.key" :label="field.label">
            <a-rate v-model:value="reviewForm[field.key]" />
          </a-form-item>
        </div>
        <a-form-item label="复核说明"><a-textarea v-model:value="reviewForm.note" :rows="3" placeholder="记录与自动评审不一致的原因" /></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import type { TablePaginationConfig } from 'ant-design-vue'
import { getKnowledgeBaseList, type KnowledgeBase } from '@/api/knowledgeBase'
import { getDatasetList, type TestDataset } from '@/api/dataset'
import {
  createEvaluationExperiment, deleteEvaluationExperiment, getEvaluationExperiment, getEvaluationList,
  getEvaluationSlices, getHumanReviewQueue, getJudgeAlignment, listEvaluationExperiments,
  runEvaluationExperiment, submitHumanReview, toggleEvaluationExperiment,
  type EvaluationExperiment, type EvaluationExperimentDetail, type EvaluationQa,
  type EvaluationSliceMetric, type EvaluationTask, type EvaluationJudgeAlignment,
} from '@/api/evaluation'

const activeTab = ref('experiments')
const experiments = ref<EvaluationExperiment[]>([])
const loadingExperiments = ref(false)
const knowledgeBases = ref<KnowledgeBase[]>([])
const datasets = ref<TestDataset[]>([])
const tasks = ref<EvaluationTask[]>([])
const completedTasks = computed(() => tasks.value.filter(task => task.status === 2))

const experimentColumns = [
  { title: '实验', key: 'name', width: 310 }, { title: '调度', key: 'schedule', width: 210 },
  { title: '成本与重试', key: 'budget', width: 150 }, { title: '状态', key: 'status', width: 100 },
  { title: '操作', key: 'action', width: 260 },
]

const createVisible = ref(false)
const savingExperiment = ref(false)
const emptyVariant = (name: string, topK: number) => ({ name, evaluationMode: 'RETRIEVAL' as const, topK, judgeModel: '', baselineTaskId: undefined as number | undefined })
const experimentForm = reactive({
  name: '', description: '', knowledgeId: undefined as number | undefined, datasetId: undefined as number | undefined,
  scheduleEnabled: false, cronExpression: '0 0 2 * * *', judgeBudget: 0, maxRetries: 1, alertOnRegression: true,
  variants: [emptyVariant('基准配置', 5), emptyVariant('候选配置', 10)] as Array<{ name: string; evaluationMode: 'RETRIEVAL'|'GENERATION'|'FULL'; topK: number; judgeModel: string; baselineTaskId?: number }>,
})

function openCreate() {
  Object.assign(experimentForm, { name: '', description: '', knowledgeId: knowledgeBases.value[0]?.id, datasetId: datasets.value[0]?.id,
    scheduleEnabled: false, cronExpression: '0 0 2 * * *', judgeBudget: 0, maxRetries: 1, alertOnRegression: true,
    variants: [emptyVariant('基准配置', 5), emptyVariant('候选配置', 10)] })
  createVisible.value = true
}
function addVariant() { experimentForm.variants.push(emptyVariant(`候选配置 ${experimentForm.variants.length}`, 5)) }
function compatibleBaselines(variant: { evaluationMode: string; topK: number }) {
  return completedTasks.value.filter(task => task.knowledgeId === experimentForm.knowledgeId
    && (task.evaluationMode || 'RETRIEVAL') === variant.evaluationMode && (task.topK || 5) === variant.topK)
}
async function saveExperiment() {
  if (!experimentForm.name.trim() || !experimentForm.knowledgeId || !experimentForm.datasetId || experimentForm.variants.some(v => !v.name.trim())) {
    message.warning('请完整填写实验、知识库、数据集和变体名称'); return
  }
  savingExperiment.value = true
  try {
    const res = await createEvaluationExperiment({
      name: experimentForm.name, description: experimentForm.description, knowledgeId: experimentForm.knowledgeId,
      datasetId: experimentForm.datasetId, scheduleEnabled: experimentForm.scheduleEnabled,
      cronExpression: experimentForm.scheduleEnabled ? experimentForm.cronExpression : undefined,
      judgeBudget: experimentForm.judgeBudget, maxRetries: experimentForm.maxRetries,
      alertOnRegression: experimentForm.alertOnRegression,
      variants: experimentForm.variants.map(v => ({ ...v, judgeModel: v.judgeModel || undefined, baselineTaskId: v.baselineTaskId || undefined })),
    })
    if (res.code === 200) { message.success('实验已创建'); createVisible.value = false; await loadExperiments() }
    else message.error(res.message)
  } finally { savingExperiment.value = false }
}

async function loadExperiments() {
  loadingExperiments.value = true
  try { const res = await listEvaluationExperiments(); if (res.code === 200) experiments.value = res.data || [] }
  finally { loadingExperiments.value = false }
}
async function runExperiment(item: EvaluationExperiment) {
  const res = await runEvaluationExperiment(item.id)
  if (res.code === 200) { message.success(`实验批次 #${res.data.id} 已启动`); await openDetail(item.id) } else message.error(res.message)
}
async function toggleExperiment(item: EvaluationExperiment) {
  const res = await toggleEvaluationExperiment(item.id); if (res.code === 200) { message.success('状态已更新'); await loadExperiments() }
}
async function removeExperiment(item: EvaluationExperiment) {
  const res = await deleteEvaluationExperiment(item.id); if (res.code === 200) { message.success('实验已删除'); await loadExperiments() } else message.error(res.message)
}

const detailVisible = ref(false)
const selectedDetail = ref<EvaluationExperimentDetail | null>(null)
async function openDetail(id: number) {
  const res = await getEvaluationExperiment(id)
  if (res.code === 200) { selectedDetail.value = res.data; detailVisible.value = true }
}

const sliceTaskId = ref<number>()
const sliceDimension = ref('category')
const slices = ref<EvaluationSliceMetric[]>([])
const loadingSlices = ref(false)
const sliceColumns = [
  { title: '切片', dataIndex: 'value', key: 'value' }, { title: '样本数', dataIndex: 'sampleCount', key: 'sampleCount' },
  { title: '失败率', key: 'failureRate' }, { title: 'Recall@K', key: 'recallAtK' }, { title: 'MRR', key: 'mrr' },
  { title: '忠实度', key: 'faithfulness' }, { title: '答案相关性', key: 'answerRelevancy' }, { title: '总体分', key: 'overallScore' },
]
async function loadSlices() {
  if (!sliceTaskId.value) { slices.value = []; return }
  loadingSlices.value = true
  try { const res = await getEvaluationSlices(sliceTaskId.value, sliceDimension.value); if (res.code === 200) slices.value = res.data || [] }
  finally { loadingSlices.value = false }
}

const reviewTaskId = ref<number>()
const reviewStatus = ref('PENDING')
const reviews = ref<EvaluationQa[]>([])
const loadingReviews = ref(false)
const reviewPagination = reactive({ current: 1, pageSize: 20, total: 0, showSizeChanger: true })
const alignment = ref<EvaluationJudgeAlignment | null>(null)
const reviewColumns = [
  { title: '样本', key: 'question' }, { title: '自动评分', key: 'autoScore', width: 150 },
  { title: '人工总体分', key: 'humanScore', width: 130 }, { title: '操作', key: 'action', width: 150 },
]
async function loadReviews() {
  loadingReviews.value = true
  try {
    const res = await getHumanReviewQueue({ taskId: reviewTaskId.value, status: reviewStatus.value, page: reviewPagination.current, pageSize: reviewPagination.pageSize })
    if (res.code === 200) { reviews.value = res.data?.records || []; reviewPagination.total = res.data?.total || 0 }
    if (reviewTaskId.value) await loadAlignment(); else alignment.value = null
  } finally { loadingReviews.value = false }
}
function onReviewPageChange(pagination: TablePaginationConfig) {
  reviewPagination.current = pagination.current || 1; reviewPagination.pageSize = pagination.pageSize || 20; loadReviews()
}
async function loadAlignment() {
  if (!reviewTaskId.value) return
  const res = await getJudgeAlignment(reviewTaskId.value); if (res.code === 200) alignment.value = res.data
}

const reviewVisible = ref(false)
const submittingReview = ref(false)
const selectedQa = ref<EvaluationQa | null>(null)
const reviewForm = reactive<Record<string, any>>({ faithfulness: 5, answerRelevancy: 5, completeness: 5, fluency: 5, overallScore: 5, note: '' })
const scoreFields = [
  { key: 'faithfulness', label: '忠实度' }, { key: 'answerRelevancy', label: '答案相关性' },
  { key: 'completeness', label: '完整性' }, { key: 'fluency', label: '流畅性' }, { key: 'overallScore', label: '总体分' },
]
function openReview(qa: EvaluationQa) {
  selectedQa.value = qa
  Object.assign(reviewForm, { faithfulness: toStars(qa.humanFaithfulness ?? qa.faithfulness), answerRelevancy: toStars(qa.humanAnswerRelevancy ?? qa.answerRelevancy),
    completeness: toStars(qa.humanCompleteness ?? qa.completeness), fluency: toStars(qa.humanFluency ?? qa.fluency), overallScore: toStars(qa.humanOverallScore ?? qa.overallScore), note: qa.humanReviewNote || '' })
  reviewVisible.value = true
}
async function saveReview() {
  if (!selectedQa.value) return
  submittingReview.value = true
  try {
    const res = await submitHumanReview(selectedQa.value.id, { status: 'COMPLETED', ...reviewForm })
    if (res.code === 200) { message.success('复核结果已保存'); reviewVisible.value = false; await loadReviews() }
  } finally { submittingReview.value = false }
}
async function skipReview(qa: EvaluationQa) {
  const res = await submitHumanReview(qa.id, { status: 'SKIPPED', note: '人工跳过' }); if (res.code === 200) await loadReviews()
}

function score(value: number | null | undefined) { return value === null || value === undefined ? '-' : `${(value * 100).toFixed(1)}%` }
function percent(value: number) { return `${(value * 100).toFixed(1)}%` }
function toStars(value: number | null | undefined) { return Math.max(1, Math.min(5, Math.round((value ?? 1) * 5))) }
function formatTime(value: string | null) { return value ? new Date(value).toLocaleString() : '-' }
function runColor(status: string) { return ({ COMPLETED: 'green', FAILED: 'red', RUNNING: 'blue', LAUNCHING: 'orange' } as Record<string,string>)[status] || 'default' }
function runStatus(status: string) { return ({ COMPLETED: '完成', FAILED: '失败', RUNNING: '运行中', LAUNCHING: '启动中' } as Record<string,string>)[status] || status }
function taskStatus(status?: number) { return ({ 0: '等待', 1: '运行中', 2: '完成', 3: '失败' } as Record<number,string>)[status ?? -1] || '-' }
function regressionStatus(status?: string | null) { return ({ PASSED: '通过', FAILED: '回归', NOT_COMPARED: '未对比' } as Record<string,string>)[status || ''] || '-' }

onMounted(async () => {
  await Promise.all([loadExperiments(), (async () => {
    const res = await getKnowledgeBaseList({ page: 1, pageSize: 100 }); if (res.code === 200) knowledgeBases.value = (res.data.records || []).filter(kb => kb.accessRole === 'owner' || kb.accessRole === 'editor')
  })(), (async () => { const res = await getDatasetList(); if (res.code === 200) datasets.value = res.data || [] })(),
  (async () => { const res = await getEvaluationList({ page: 1, pageSize: 200 }); if (res.code === 200) tasks.value = res.data?.records || [] })()])
  await loadReviews()
})
</script>

<style scoped lang="scss">
.evaluation-lab { padding: 24px; }
.page-header { display:flex; justify-content:space-between; align-items:flex-start; gap:24px; margin-bottom:16px; }
.page-header h1 { margin:0 0 6px; font-size:26px; color:var(--text-primary); }
.page-header p, .muted { margin:0; color:var(--text-tertiary); font-size:12px; }
.ops-tip { margin-bottom:14px; }
.toolbar { display:flex; gap:12px; align-items:center; margin-bottom:16px; }
.name-link { padding:0; height:auto; font-weight:600; }
.form-grid { display:grid; grid-template-columns:1fr 1fr; gap:0 16px; }
.section-title { display:flex; align-items:baseline; gap:10px; font-weight:600; margin:8px 0 12px; }
.section-title span { color:var(--text-tertiary); font-size:12px; font-weight:400; }
.variant-row { display:grid; grid-template-columns:1fr 110px 140px 1fr 1.2fr auto; gap:8px; margin-bottom:10px; }
.schedule-row { display:flex; align-items:center; flex-wrap:wrap; gap:10px; margin-top:18px; padding:14px; border-radius:10px; background:var(--bg-surface-secondary); }
.variant-cards, .alignment-grid { display:grid; grid-template-columns:repeat(4, minmax(0,1fr)); gap:12px; margin-bottom:18px; }
.variant-card, .metric-card { display:flex; flex-direction:column; gap:5px; padding:14px; border:1px solid var(--border-color); border-radius:10px; background:var(--bg-surface); }
.variant-card span, .metric-card span { color:var(--text-tertiary); font-size:12px; }
.metric-card strong { font-size:22px; color:var(--text-primary); }
.run-error { margin-bottom:10px; }
.question { max-width:560px; line-height:1.55; }
.review-context { padding:12px; margin-bottom:16px; border-radius:8px; background:var(--bg-surface-secondary); }
.review-context p { white-space:pre-wrap; color:var(--text-secondary); }
.score-grid { display:grid; grid-template-columns:1fr 1fr; gap:0 12px; }
@media (max-width: 900px) { .form-grid, .score-grid { grid-template-columns:1fr; } .variant-row { grid-template-columns:1fr 1fr; } .variant-cards, .alignment-grid { grid-template-columns:1fr 1fr; } }
</style>
