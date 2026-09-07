<template>
  <a-space direction="vertical" style="width: 100%" :size="16">
    <a-spin :spinning="loading">
      <a-empty v-if="report && report.completedTasks === 0" description="暂无质量基线，请先到评估中心运行测试集" />
      <template v-else-if="report">
        <a-row :gutter="16">
          <a-col :span="4"><a-card size="small"><a-statistic title="已完成评估" :value="report.completedTasks" /></a-card></a-col>
          <a-col :span="5"><a-card size="small"><a-statistic title="评估问题" :value="report.totalQuestions" /></a-card></a-col>
          <a-col :span="5"><a-card size="small"><a-statistic title="文档未召回" :value="report.documentMisses" /></a-card></a-col>
          <a-col :span="5"><a-card size="small"><a-statistic title="错误召回" :value="report.negativeFalsePositives" /></a-card></a-col>
          <a-col :span="5"><a-card size="small"><a-statistic title="待处理问题" :value="report.openIssues" :value-style="{ color: report.openIssues ? '#cf1322' : '#3f8600' }" /></a-card></a-col>
        </a-row>
        <a-alert type="info" show-icon>
          <template #message>优化建议</template>
          <template #description><div v-for="item in report.recommendations" :key="item">• {{ item }}</div></template>
        </a-alert>
        <a-table :data-source="report.issues" :columns="columns" row-key="qaId" :pagination="{ pageSize: 10 }" size="small">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'question'">
              <a-tooltip :title="record.expectedAnswer || record.question"><span class="question">{{ record.question }}</span></a-tooltip>
            </template>
            <template v-else-if="column.key === 'severity'">
              <a-tag :color="record.severity === 'high' ? 'red' : 'orange'">{{ record.severity === 'high' ? '高' : '中' }}</a-tag>
            </template>
            <template v-else-if="column.key === 'status'">
              <a-tag :color="statusColor(record.status)">{{ statusText(record.status) }}</a-tag>
            </template>
            <template v-else-if="column.key === 'actions' && canEdit">
              <a-space>
                <a-button v-if="record.status !== 'resolved'" type="link" size="small" @click="openUpdate(record.qaId, record.note, 'resolved')">已处理</a-button>
                <a-button v-if="record.status !== 'ignored'" type="link" size="small" @click="openUpdate(record.qaId, record.note, 'ignored')">忽略</a-button>
                <a-button v-if="record.status !== 'open'" type="link" size="small" @click="save(record.qaId, 'open', '')">重新打开</a-button>
              </a-space>
            </template>
          </template>
        </a-table>
      </template>
    </a-spin>
    <a-modal v-model:open="modalOpen" :title="targetStatus === 'resolved' ? '记录处理结果' : '忽略问题'" :confirm-loading="saving" @ok="confirmUpdate">
      <a-textarea v-model:value="note" :rows="4" :maxlength="500" show-count placeholder="可填写补充知识、调整配置或忽略原因" />
    </a-modal>
  </a-space>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { getKnowledgeQuality, updateQualityIssue, type KnowledgeQualityReport } from '@/api/evaluation'

const props = defineProps<{ knowledgeBaseId: number; canEdit: boolean }>()
const report = ref<KnowledgeQualityReport | null>(null)
const loading = ref(false)
const saving = ref(false)
const modalOpen = ref(false)
const note = ref('')
const targetQaId = ref<number>()
const targetStatus = ref<'resolved' | 'ignored'>('resolved')
const columns = [
  { title: '业务问题', key: 'question', width: 300 },
  { title: '问题原因', dataIndex: 'reason', key: 'reason', width: 210 },
  { title: '等级', key: 'severity', width: 70 },
  { title: '状态', key: 'status', width: 90 },
  { title: '处理说明', dataIndex: 'note', key: 'note', ellipsis: true },
  { title: '操作', key: 'actions', width: 180 },
]
const statusText = (status: string) => ({ open: '待处理', resolved: '已处理', ignored: '已忽略' }[status] || status)
const statusColor = (status: string) => ({ open: 'red', resolved: 'green', ignored: 'default' }[status] || 'default')
async function load() {
  loading.value = true
  try {
    const res = await getKnowledgeQuality(props.knowledgeBaseId)
    if (res.code === 200) report.value = res.data
  } catch { message.error('加载知识质量报告失败') }
  finally { loading.value = false }
}
function openUpdate(qaId: number, issueNote: string | null, status: 'resolved' | 'ignored') {
  targetQaId.value = qaId
  targetStatus.value = status
  note.value = issueNote || ''
  modalOpen.value = true
}
async function confirmUpdate() {
  if (targetQaId.value) await save(targetQaId.value, targetStatus.value, note.value)
  modalOpen.value = false
}
async function save(qaId: number, status: 'open' | 'resolved' | 'ignored', issueNote: string) {
  saving.value = true
  try {
    const res = await updateQualityIssue(qaId, status, issueNote)
    if (res.code === 200) { message.success('问题状态已更新'); await load() }
  } catch { message.error('更新问题状态失败') }
  finally { saving.value = false }
}
watch(() => props.knowledgeBaseId, load, { immediate: true })
</script>

<style scoped>
.question { display: inline-block; max-width: 280px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
