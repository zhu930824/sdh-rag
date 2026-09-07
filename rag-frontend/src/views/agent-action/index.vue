<template>
  <div class="page-shell">
    <div class="page-header">
      <div><h2>Agent 审批与执行</h2><p>在业务动作发生前保留人工控制，在执行后用真实业务结果改进知识与流程。</p></div>
      <a-button @click="router.push('/agent-tools')">业务工具中心</a-button>
    </div>
    <a-tabs v-model:activeKey="activeTab" @change="load">
      <a-tab-pane key="pending" tab="待我审批" />
      <a-tab-pane key="all" tab="相关动作" />
    </a-tabs>
    <a-table :columns="columns" :data-source="actions" :loading="loading" row-key="id" :pagination="{ pageSize: 20 }">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'tool'"><strong>{{ record.toolName }}</strong><div class="muted">#{{ record.id }} · {{ record.nodeId || 'API 调用' }}</div></template>
        <template v-else-if="column.key === 'status'"><a-tag :color="statusColor(record.status)">{{ statusText(record.status) }}</a-tag><div v-if="record.responseCode" class="muted">HTTP {{ record.responseCode }}</div></template>
        <template v-else-if="column.key === 'time'">{{ formatTime(record.requestedAt) }}</template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="link" size="small" @click="showDetail(record)">详情</a-button>
            <template v-if="activeTab === 'pending' && record.status === 'PENDING_APPROVAL'">
              <a-button type="link" size="small" @click="approve(record)">通过</a-button>
              <a-button danger type="link" size="small" @click="openReject(record)">拒绝</a-button>
            </template>
            <a-button v-if="activeTab === 'all' && record.status === 'FAILED'" type="link" size="small" @click="retry(record)">重试</a-button>
            <a-button v-if="activeTab === 'all' && record.status === 'SUCCEEDED'" type="link" size="small" @click="openFeedback(record)">结果反馈</a-button>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-drawer v-model:open="detailOpen" title="动作审计详情" width="620">
      <a-descriptions v-if="selected" bordered :column="1" size="small">
        <a-descriptions-item label="动作 ID">{{ selected.id }}</a-descriptions-item><a-descriptions-item label="工具">{{ selected.toolName }}（#{{ selected.toolId }}）</a-descriptions-item>
        <a-descriptions-item label="状态"><a-tag :color="statusColor(selected.status)">{{ statusText(selected.status) }}</a-tag></a-descriptions-item>
        <a-descriptions-item label="幂等键">{{ selected.idempotencyKey }}</a-descriptions-item><a-descriptions-item label="发起/审批人">{{ selected.requestedBy }} / {{ selected.approverId }}</a-descriptions-item>
        <a-descriptions-item label="错误">{{ selected.errorMessage || '-' }}</a-descriptions-item>
      </a-descriptions>
      <h4>请求参数</h4><pre>{{ pretty(selected?.requestJson) }}</pre>
      <h4>知识与决策依据</h4><pre>{{ pretty(selected?.evidenceJson) }}</pre>
      <h4>执行结果</h4><pre>{{ pretty(selected?.resultJson) }}</pre>
    </a-drawer>

    <a-modal v-model:open="rejectOpen" title="拒绝业务动作" @ok="reject"><a-textarea v-model:value="rejectReason" :rows="4" placeholder="请说明拒绝原因，便于流程改进" /></a-modal>
    <a-modal v-model:open="feedbackOpen" title="反馈真实业务结果" @ok="submitFeedback">
      <a-form layout="vertical"><a-form-item label="结果"><a-radio-group v-model:value="feedback.outcome"><a-radio-button value="SUCCEEDED">成功</a-radio-button><a-radio-button value="PARTIAL">部分成功</a-radio-button><a-radio-button value="FAILED">业务失败</a-radio-button></a-radio-group></a-form-item>
      <a-form-item label="说明"><a-textarea v-model:value="feedback.comment" :rows="4" placeholder="记录用户是否真正解决问题，以及失败原因" /></a-form-item>
      <a-form-item label="可沉淀为知识"><a-switch v-model:checked="feedback.reusableKnowledge" /><span class="field-tip"> 标记后可进入后续知识运营队列</span></a-form-item></a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { approveAgentAction, feedbackAgentAction, getAgentActions, getPendingAgentActions, rejectAgentAction, retryAgentAction, type AgentAction } from '@/api/agent'

const router = useRouter(); const activeTab = ref('pending'); const loading = ref(false); const actions = ref<AgentAction[]>([])
const selected = ref<AgentAction>(); const detailOpen = ref(false); const rejectOpen = ref(false); const rejectReason = ref(''); const feedbackOpen = ref(false)
const feedback = reactive({ outcome: 'SUCCEEDED', comment: '', reusableKnowledge: false })
const columns = [{ title:'业务动作',key:'tool',width:260 },{ title:'状态',key:'status',width:150 },{ title:'发起时间',key:'time',width:180 },{ title:'操作',key:'action',width:250 }]
async function load() { loading.value=true; try { actions.value=((activeTab.value==='pending' ? await getPendingAgentActions() : await getAgentActions()).data)||[] } finally { loading.value=false } }
function showDetail(action: AgentAction) { selected.value=action; detailOpen.value=true }
function approve(action: AgentAction) { Modal.confirm({ title:`批准“${action.toolName}”动作？`, content:'批准后将立即调用业务系统，执行结果会写入审计记录。', async onOk(){ await approveAgentAction(action.id); message.success('已审批并执行'); await load() } }) }
function openReject(action: AgentAction) { selected.value=action; rejectReason.value=''; rejectOpen.value=true }
async function reject() { if(!selected.value)return; await rejectAgentAction(selected.value.id,rejectReason.value); message.success('已拒绝'); rejectOpen.value=false; await load() }
function retry(action: AgentAction) { Modal.confirm({ title:'重试失败动作？', content:'将沿用原请求参数和幂等审计记录再次调用。', async onOk(){ await retryAgentAction(action.id); message.success('重试已完成'); await load() } }) }
function openFeedback(action: AgentAction) { selected.value=action; Object.assign(feedback,{outcome:'SUCCEEDED',comment:'',reusableKnowledge:false}); feedbackOpen.value=true }
async function submitFeedback(){ if(!selected.value)return; await feedbackAgentAction(selected.value.id,{...feedback}); message.success('业务结果已回流'); feedbackOpen.value=false }
function statusText(value:string){return ({PENDING_APPROVAL:'待审批',READY:'待执行',EXECUTING:'执行中',SUCCEEDED:'已成功',FAILED:'失败',REJECTED:'已拒绝'} as Record<string,string>)[value]||value}
function statusColor(value:string){return ({PENDING_APPROVAL:'orange',READY:'blue',EXECUTING:'processing',SUCCEEDED:'green',FAILED:'red',REJECTED:'default'} as Record<string,string>)[value]||'default'}
function formatTime(value:string){return value?new Date(value).toLocaleString():'-'}
function pretty(value?:string){if(!value)return '-';try{return JSON.stringify(JSON.parse(value),null,2)}catch{return value}}
onMounted(load)
</script>

<style scoped lang="scss">
.page-shell{padding:24px;background:var(--bg-surface,#fff);border-radius:12px;min-height:100%}.page-header{display:flex;justify-content:space-between;gap:20px}.page-header h2{margin:0 0 6px}.page-header p,.muted,.field-tip{color:#8c8c8c}pre{max-height:260px;overflow:auto;padding:12px;background:#f6f8fa;border-radius:8px;white-space:pre-wrap;word-break:break-word}h4{margin:20px 0 8px}
</style>
