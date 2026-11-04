<template>
  <a-space direction="vertical" style="width: 100%" :size="16">
    <a-alert show-icon message="保存场景版本，关联你已结束的 Agent 运行，并保留回答与轨迹快照。记录尚未评分，不会重新调用模型或执行工具。" />
    <a-space wrap>
      <a-select :value="datasetId" placeholder="选择场景版本（最近 100 个）" style="min-width: 280px" :loading="loading" @change="onDatasetChange">
        <a-select-option v-for="item in datasets" :key="item.id" :value="item.id">{{ item.name }} · v{{ item.version }} · {{ item.scenarioCount }} 条</a-select-option>
      </a-select>
      <a-button :loading="loading" @click="loadDatasets">刷新版本</a-button>
      <a-button @click="downloadTemplate">下载场景模板</a-button>
    </a-space>
    <a-card size="small" title="导入场景">
      <a-space wrap>
        <a-input v-model:value="name" placeholder="版本名称" :maxlength="120" style="width: 200px" />
        <input ref="fileInput" type="file" accept=".json,application/json" aria-label="选择 Agent 场景 JSON 文件" @change="readFile" />
        <a-checkbox v-model:checked="nextVersion" :disabled="!datasetId">基于已选版本创建下一版</a-checkbox>
        <a-button type="primary" :disabled="!name.trim() || !imported.length" :loading="importing" @click="importScenarios">导入 {{ imported.length || '' }} 条场景</a-button>
      </a-space>
      <p>每次导入生成不可修改的新版本；支持 1—100 条场景，文件不超过 1 MiB。</p>
    </a-card>
    <template v-if="dataset">
      <a-select v-model:value="scenarioId" placeholder="选择场景" style="min-width: 280px">
        <a-select-option v-for="item in dataset.scenarios" :key="item.id" :value="item.id">{{ item.id }} · {{ item.task }}</a-select-option>
      </a-select>
      <a-descriptions v-if="scenario" bordered size="small" :column="1">
        <a-descriptions-item label="任务">{{ scenario.task }}</a-descriptions-item>
        <a-descriptions-item v-if="scenario.initialContext" label="初始上下文">{{ scenario.initialContext }}</a-descriptions-item>
        <a-descriptions-item label="期望结果">{{ scenario.expectedOutcome }}</a-descriptions-item>
        <a-descriptions-item label="允许 / 禁止工具">{{ scenario.allowedTools.join('、') || '未指定' }} / {{ scenario.forbiddenTools.join('、') || '未指定' }}</a-descriptions-item>
        <a-descriptions-item label="关键步骤">{{ scenario.criticalSteps.join(' → ') || '未指定' }}</a-descriptions-item>
        <a-descriptions-item label="风险等级">{{ riskLabels[scenario.risk] }}</a-descriptions-item>
      </a-descriptions>
      <a-space wrap>
        <a-input v-model:value="runKey" placeholder="已结束运行的 runKey" :maxlength="64" style="width: 320px" />
        <a-button type="primary" :loading="recording" :disabled="!scenarioId || !runKey.trim() || loading" @click="recordRun">关联运行并保存快照</a-button>
      </a-space>
      <a-table :data-source="records" :columns="columns" row-key="id" size="small" :pagination="{ pageSize: 10 }" :scroll="{ x: 650 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">已记录 · 未评分</template>
          <a-button v-else-if="column.key === 'detail'" type="link" @click="showDetail(record.id)">查看快照</a-button>
        </template>
      </a-table>
    </template>
    <a-empty v-else-if="!loading" description="先导入场景，再关联运行结果" />
    <a-modal v-model:open="detailOpen" title="运行归档快照" :footer="null" width="850px">
      <a-alert type="info" show-icon message="快照保留归档时观察到的运行证据；未在执行时记录的模型、Prompt、工具定义和知识发布版本标为 UNKNOWN。" />
      <p v-if="detail">快照校验值：{{ detail.snapshotSha256 }}</p>
      <pre class="snapshot">{{ detail ? JSON.stringify(JSON.parse(detail.runSnapshotJson), null, 2) : '' }}</pre>
    </a-modal>
  </a-space>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { getAgentDataset, getAgentEvaluation, importAgentDataset, listAgentDatasets, listAgentEvaluations, recordAgentEvaluation } from '@/api/agentEvaluation'
import type { AgentDataset, AgentDatasetSummary, AgentEvaluationRecord, AgentEvaluationSummary, AgentScenario } from '@/api/agentEvaluation'

const datasets = ref<AgentDatasetSummary[]>([]), dataset = ref<AgentDataset>(), datasetId = ref<string>()
const records = ref<AgentEvaluationSummary[]>([]), scenarioId = ref<string>(), runKey = ref('')
const imported = ref<AgentScenario[]>([]), name = ref(''), nextVersion = ref(false), fileInput = ref<HTMLInputElement>()
const loading = ref(false), importing = ref(false), recording = ref(false), detailOpen = ref(false), detail = ref<AgentEvaluationRecord>()
const scenario = computed(() => dataset.value?.scenarios.find(item => item.id === scenarioId.value))
const riskLabels = { LOW: '低', MEDIUM: '中', HIGH: '高' }
const columns = [
  { title: '场景', dataIndex: 'scenarioId' }, { title: '来源运行', dataIndex: 'runKey' },
  { title: '状态', key: 'status' }, { title: '归档时间', dataIndex: 'createdAt' }, { title: '操作', key: 'detail' },
]
let selection = 0, detailRequest = 0, fileRead = 0
async function loadDatasets() {
  loading.value = true
  try { datasets.value = (await listAgentDatasets()).data } catch { /* request interceptor reports API errors */ }
  finally { loading.value = false }
}
async function selectDataset(id: string) {
  const current = ++selection
  datasetId.value = id; dataset.value = undefined; records.value = []; scenarioId.value = undefined
  loading.value = true
  try {
    const [version, results] = await Promise.all([getAgentDataset(id), listAgentEvaluations(id)])
    if (current !== selection) return
    dataset.value = version.data; records.value = results.data; scenarioId.value = version.data.scenarios[0]?.id
  } catch { /* request interceptor reports API errors */ }
  finally { if (current === selection) loading.value = false }
}
function onDatasetChange(value: unknown) {
  if (typeof value === 'string') void selectDataset(value)
}
async function readFile(event: Event) {
  const current = ++fileRead
  imported.value = []
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (file.size > 1024 * 1024) { message.error('文件不能超过 1 MiB'); return }
  try {
    const value: unknown = JSON.parse(await file.text())
    if (current !== fileRead) return
    if (!value || typeof value !== 'object' || !('scenarios' in value) || !Array.isArray(value.scenarios)) throw new Error()
    if (!value.scenarios.length || value.scenarios.length > 100) throw new Error()
    imported.value = value.scenarios
  } catch { message.error('请使用场景模板，文件需包含 1—100 条 scenarios') }
}
async function importScenarios() {
  importing.value = true
  try {
    const result = await importAgentDataset({ name: name.value.trim(), parentId: nextVersion.value ? datasetId.value : undefined, scenarios: imported.value })
    imported.value = []; if (fileInput.value) fileInput.value.value = ''
    message.success('场景版本已保存'); await loadDatasets(); await selectDataset(result.data.id)
  } catch { /* request interceptor reports API errors */ }
  finally { importing.value = false }
}
async function recordRun() {
  if (!datasetId.value || !scenarioId.value) return
  const selected = datasetId.value
  recording.value = true
  try {
    await recordAgentEvaluation({ datasetId: selected, scenarioId: scenarioId.value, runKey: runKey.value.trim() })
    const result = await listAgentEvaluations(selected)
    if (datasetId.value === selected) records.value = result.data
    message.success('运行快照已保存，尚未评分')
  } catch { /* request interceptor reports API errors */ }
  finally { recording.value = false }
}
async function showDetail(id: string) {
  const current = ++detailRequest
  try {
    const result = await getAgentEvaluation(id)
    if (current === detailRequest) { detail.value = result.data; detailOpen.value = true }
  } catch { /* request interceptor reports API errors */ }
}
function downloadTemplate() {
  const scenarios: AgentScenario[] = [{ id: 'after-sales-ticket', task: '为订单 42 创建售后工单', initialContext: '客户已确认申请售后', allowedTools: ['get_order', 'create_ticket'], forbiddenTools: ['delete_order'], expectedOutcome: '核对订单后，经审批创建一个工单并返回编号', criticalSteps: ['核对订单', '请求审批', '返回工单编号'], risk: 'MEDIUM' }]
  const url = URL.createObjectURL(new Blob([JSON.stringify({ scenarios }, null, 2)], { type: 'application/json' }))
  const link = document.createElement('a'); link.href = url; link.download = 'agent-scenarios.json'; link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
onMounted(loadDatasets)
</script>

<style scoped>
p { margin: 12px 0 0; color: #666; }
.snapshot { max-height: 60vh; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; margin-top: 16px; }
</style>
