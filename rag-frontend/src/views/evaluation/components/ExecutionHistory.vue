<template>
  <a-collapse style="margin-bottom: 16px" @change="load">
    <a-collapse-panel key="history" header="执行批次与归档结果">
      <a-alert v-if="error" type="warning" :message="error" show-icon />
      <a-table v-else :data-source="attempts" :loading="loading" row-key="id" :pagination="false" size="small" :scroll="{ x: 660 }">
        <a-table-column title="批次" data-index="attempt_number" />
        <a-table-column title="状态" key="status"><template #default="{ record }">{{ statusLabel(record.status) }}</template></a-table-column>
        <a-table-column title="语料版本" key="corpus"><template #default="{ record }"><span :title="record.publication_sha256">{{ record.publication_sha256.slice(0, 12) }}</span></template></a-table-column>
        <a-table-column title="已归档样本" data-index="result_count" />
        <a-table-column title="操作" key="actions"><template #default="{ record }">
          <a-button type="link" size="small" @click="open(record)">查看结果</a-button>
          <a-popconfirm v-if="record.status === 'COMPLETED'" title="使用当时语料和检索配置、当前模型目录新建评测？排队保留窗口为 24 小时。" @confirm="replay(record)">
            <a-button type="link" size="small" :disabled="!!replaying" :loading="replaying === record.id">重放此批次</a-button>
          </a-popconfirm>
        </template></a-table-column>
        <template #emptyText>暂无执行归档，历史任务不会自动补造批次。</template>
      </a-table>
    </a-collapse-panel>
  </a-collapse>
  <a-modal :open="!!selected" title="批次归档结果" :footer="null" :width="900" @cancel="close">
    <a-alert type="info" show-icon message="归档保留当时的结果。失败或未完整完成的批次不能作为有效基线。" style="margin-bottom: 12px" />
    <a-alert v-if="resultError" type="error" :message="resultError" />
    <a-table v-else :data-source="results" :loading="loadingResults" row-key="ordinal" size="small" :scroll="{ x: 660 }"
      :pagination="{ current: page, pageSize: 20, total: selected?.result_count || 0, showSizeChanger: false }" @change="changePage">
      <a-table-column title="#" data-index="ordinal" :width="55" />
      <a-table-column title="问题" key="question"><template #default="{ record }">{{ record.snapshot.question }}</template></a-table-column>
      <a-table-column title="期望答案" key="expected"><template #default="{ record }">{{ record.snapshot.expectedAnswer || '—' }}</template></a-table-column>
      <a-table-column title="生成答案" key="answer"><template #default="{ record }">{{ record.snapshot.actualAnswer || '—' }}</template></a-table-column>
    </a-table>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { getEvaluationExecutions, getEvaluationExecutionResults, replayEvaluationExecution, type EvaluationExecution } from '@/api/evaluation'

const props = defineProps<{ taskId: number }>()
const emit = defineEmits<{ created: [] }>()
const replaying = ref<string | null>(null)
const requestKeys = new Map<string, string>()
const attempts = ref<EvaluationExecution[]>([])
const selected = ref<EvaluationExecution | null>(null)
const results = ref<Array<{ ordinal: number; snapshot: { question?: string; expectedAnswer?: string; actualAnswer?: string } }>>([])
const loading = ref(false)
const loadingResults = ref(false)
const error = ref('')
const resultError = ref('')
const page = ref(1)
let version = 0
let resultVersion = 0

watch(() => props.taskId, () => { version++; attempts.value = []; error.value = ''; loading.value = false; close() })
function statusLabel(status: string) { return ({ RUNNING: '运行中', COMPLETED: '完成', FAILED: '失败', INTERRUPTED: '已中断', CANCELLED: '已取消' } as Record<string, string>)[status] || status }
async function load(keys: string | number | Array<string | number>) {
  if (!keys || (Array.isArray(keys) && !keys.length)) return
  const requestVersion = ++version
  loading.value = true
  error.value = ''
  try {
    const response = await getEvaluationExecutions(props.taskId)
    if (requestVersion !== version) return
    if (response.code !== 200) throw new Error('加载失败')
    attempts.value = response.data || []
  } catch {
    if (requestVersion === version) error.value = '无法读取批次归档，请确认任务创建者身份及知识库访问权限。'
  } finally { if (requestVersion === version) loading.value = false }
}
function close() { resultVersion++; selected.value = null; results.value = []; loadingResults.value = false }
async function replay(attempt: EvaluationExecution) {
  if (replaying.value) return
  replaying.value = attempt.id
  const requestKey = requestKeys.get(attempt.id) || crypto.randomUUID()
  requestKeys.set(attempt.id, requestKey)
  try {
    const response = await replayEvaluationExecution(attempt.id, requestKey)
    if (response.code !== 200) throw new Error(response.message || '重放创建失败')
    requestKeys.delete(attempt.id)
    message.success(`已创建历史重放任务 #${response.data.id}`)
    emit('created')
  } catch (error) { message.error(error instanceof Error ? error.message : '重放创建失败，请重试') }
  finally { replaying.value = null }
}
async function open(attempt: EvaluationExecution) { selected.value = attempt; page.value = 1; await loadResults() }
async function changePage(pagination: { current?: number }) { page.value = pagination.current || 1; await loadResults() }
async function loadResults() {
  if (!selected.value) return
  const requestVersion = ++resultVersion
  loadingResults.value = true
  resultError.value = ''
  results.value = []
  try {
    const response = await getEvaluationExecutionResults(selected.value.id, (page.value - 1) * 20, 20)
    if (requestVersion !== resultVersion) return
    if (response.code !== 200) throw new Error('加载失败')
    results.value = (response.data || []).map(row => ({ ordinal: row.ordinal, snapshot: JSON.parse(row.snapshot_json) }))
  } catch { if (requestVersion === resultVersion) resultError.value = '批次结果加载失败，请重新打开重试。' }
  finally { if (requestVersion === resultVersion) loadingResults.value = false }
}
</script>
