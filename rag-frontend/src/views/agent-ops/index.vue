<template>
  <div class="ops-page">
    <div class="page-header">
      <div><h2>Agent 运营与改进</h2><p>用任务完成率、工具可靠性和真实业务反馈持续优化 Agent、流程与知识库。</p></div>
      <a-space><a-select v-model:value="days" style="width:120px" @change="loadReport"><a-select-option :value="7">近 7 天</a-select-option><a-select-option :value="30">近 30 天</a-select-option><a-select-option :value="90">近 90 天</a-select-option></a-select><a-button @click="router.push('/agent-actions')">审批记录</a-button></a-space>
    </div>

    <a-row :gutter="16" class="metric-row">
      <a-col v-for="card in metricCards" :key="card.key" :xs="12" :md="8" :xl="4">
        <a-card size="small"><a-statistic :title="card.title" :value="card.value" :suffix="card.suffix" :value-style="{ color: card.color }" /><div class="metric-hint">{{ card.hint }}</div></a-card>
      </a-col>
    </a-row>

    <a-row :gutter="16" class="section-row">
      <a-col :xs="24" :xl="14">
        <a-card title="工具可靠性" :loading="loading">
          <a-table :columns="toolColumns" :data-source="report.tools" row-key="toolId" size="small" :pagination="false">
            <template #bodyCell="{ column, record }"><template v-if="column.key === 'successRate'"><a-progress :percent="successRate(record)" size="small" :status="successRate(record) < 80 ? 'exception' : 'success'" /></template></template>
          </a-table>
        </a-card>
      </a-col>
      <a-col :xs="24" :xl="10">
        <a-card title="每日任务趋势" :loading="loading">
          <a-table :columns="trendColumns" :data-source="report.trend" row-key="day" size="small" :pagination="false" :scroll="{ y: 260 }" />
        </a-card>
      </a-col>
    </a-row>

    <a-card title="知识与流程改进队列" class="section-row">
      <template #extra><a-radio-group v-model:value="improvementStatus" button-style="solid" size="small" @change="loadImprovements"><a-radio-button value="OPEN">待处理</a-radio-button><a-radio-button value="RESOLVED">已解决</a-radio-button><a-radio-button value="IGNORED">已忽略</a-radio-button><a-radio-button value="">全部</a-radio-button></a-radio-group></template>
      <a-alert type="info" show-icon message="用户把真实业务结果标记为“可沉淀为知识”后，会自动进入这里并关联对应知识库与 Agent 运行。" class="queue-tip" />
      <a-table :columns="improvementColumns" :data-source="improvements" :loading="improvementLoading" row-key="id" :pagination="{ pageSize: 10 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'title'"><strong>{{ record.title }}</strong><div class="muted">{{ categoryText(record.category) }} · {{ record.knowledgeId ? `知识库 #${record.knowledgeId}` : '未关联知识库' }}</div></template>
          <template v-else-if="column.key === 'priority'"><a-tag :color="priorityColor(record.priority)">{{ priorityText(record.priority) }}</a-tag></template>
          <template v-else-if="column.key === 'status'"><a-tag>{{ statusText(record.status) }}</a-tag></template>
          <template v-else-if="column.key === 'action'"><a-space><a-button type="link" size="small" @click="showImprovement(record)">查看</a-button><a-button v-if="record.status === 'OPEN' && record.category === 'KNOWLEDGE_REUSE' && record.knowledgeId" type="link" size="small" @click="createDraft(record)">生成知识草稿</a-button><a-button v-if="record.status === 'OPEN'" type="link" size="small" @click="openReview(record, 'RESOLVED')">解决</a-button><a-button v-if="record.status === 'OPEN'" type="link" size="small" @click="openReview(record, 'IGNORED')">忽略</a-button></a-space></template>
        </template>
      </a-table>
    </a-card>

    <a-drawer v-model:open="detailOpen" title="改进项详情" width="600"><a-descriptions v-if="selected" bordered :column="1" size="small"><a-descriptions-item label="标题">{{ selected.title }}</a-descriptions-item><a-descriptions-item label="来源">{{ selected.sourceType }} / action #{{ selected.actionId || '-' }}</a-descriptions-item><a-descriptions-item label="知识库">{{ selected.knowledgeId || '-' }}</a-descriptions-item><a-descriptions-item label="处理说明">{{ selected.reviewNote || '-' }}</a-descriptions-item></a-descriptions><pre>{{ selected?.detail || '-' }}</pre></a-drawer>
    <a-modal v-model:open="reviewOpen" :title="reviewStatus === 'RESOLVED' ? '解决改进项' : '忽略改进项'" @ok="submitReview"><a-textarea v-model:value="reviewNote" :rows="4" placeholder="记录采取的知识更新、工具修复或流程调整" /></a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { getAgentImprovements, getAgentOpsReport, reviewAgentImprovement, type AgentImprovementItem, type AgentOpsReport } from '@/api/agent'
import { createKnowledgeDraft } from '@/api/knowledgePublishing'

const router=useRouter(); const days=ref(30); const loading=ref(false); const improvementLoading=ref(false)
const report=ref<AgentOpsReport>({days:30,overview:{},tools:[],trend:[]}); const improvements=ref<AgentImprovementItem[]>([]); const improvementStatus=ref('OPEN')
const selected=ref<AgentImprovementItem>(); const detailOpen=ref(false); const reviewOpen=ref(false); const reviewStatus=ref('RESOLVED'); const reviewNote=ref('')
const n=(key:string)=>Number(report.value.overview[key]||0); const pct=(a:number,b:number)=>b?Number((a*100/b).toFixed(1)):0
const metricCards=computed(()=>[
  {key:'runs',title:'Agent 任务',value:n('totalRuns'),hint:`平均 ${n('avgSteps')} 步`,color:'#1677ff'},
  {key:'completion',title:'任务完成率',value:pct(n('completedRuns'),n('totalRuns')),suffix:'%',hint:`完成 ${n('completedRuns')} / 失败 ${n('failedRuns')}`,color:'#52c41a'},
  {key:'waiting',title:'待审批任务',value:n('waitingRuns'),hint:`待审批动作 ${n('pendingActions')}`,color:'#faad14'},
  {key:'actions',title:'工具成功率',value:pct(n('succeededActions'),n('totalActions')),suffix:'%',hint:`共调用 ${n('totalActions')} 次`,color:'#13c2c2'},
  {key:'feedback',title:'真实结果反馈',value:n('feedbackCount'),hint:`业务失败 ${n('businessFailedCount')}`,color:'#722ed1'},
  {key:'reuse',title:'待沉淀经验',value:n('reusableCount'),hint:'已标记可复用知识',color:'#eb2f96'},
])
const toolColumns=[{title:'工具',dataIndex:'toolName',key:'toolName'},{title:'调用',dataIndex:'calls',key:'calls',width:70},{title:'成功率',key:'successRate',width:180},{title:'失败',dataIndex:'failed',key:'failed',width:70},{title:'拒绝',dataIndex:'rejected',key:'rejected',width:70}]
const trendColumns=[{title:'日期',dataIndex:'day',key:'day'},{title:'任务',dataIndex:'runs',key:'runs'},{title:'完成',dataIndex:'completed',key:'completed'},{title:'失败',dataIndex:'failed',key:'failed'}]
const improvementColumns=[{title:'改进项',key:'title'},{title:'优先级',key:'priority',width:100},{title:'状态',key:'status',width:100},{title:'创建时间',dataIndex:'createdAt',key:'createdAt',width:180},{title:'操作',key:'action',width:300}]
async function loadReport(){loading.value=true;try{report.value=(await getAgentOpsReport(days.value)).data||report.value}finally{loading.value=false}}
async function loadImprovements(){improvementLoading.value=true;try{improvements.value=(await getAgentImprovements(improvementStatus.value||undefined)).data||[]}finally{improvementLoading.value=false}}
function successRate(record:Record<string,number>){return pct(Number(record.succeeded||0),Number(record.calls||0))}
function showImprovement(item:any){selected.value=item as AgentImprovementItem;detailOpen.value=true}
function openReview(item:any,status:string){selected.value=item as AgentImprovementItem;reviewStatus.value=status;reviewNote.value='';reviewOpen.value=true}
async function submitReview(){if(!selected.value)return;await reviewAgentImprovement(selected.value.id,reviewStatus.value,reviewNote.value);message.success('改进项已更新');reviewOpen.value=false;await loadImprovements()}
async function createDraft(value:any){const item=value as AgentImprovementItem;if(!item.knowledgeId)return;const content=`${item.detail||item.title}\n\n适用场景：请补充。\n处理步骤：请补充。\n限制条件：请补充。\n业务验证：请补充。`;await createKnowledgeDraft({improvementId:item.id,knowledgeId:item.knowledgeId,title:item.title,content,regressionQueries:[]});message.success('知识草稿已生成');router.push('/knowledge-publishing')}
function priorityText(v:string){return ({LOW:'低',MEDIUM:'中',HIGH:'高'} as Record<string,string>)[v]||v} function priorityColor(v:string){return ({LOW:'green',MEDIUM:'orange',HIGH:'red'} as Record<string,string>)[v]||'default'}
function statusText(v:string){return ({OPEN:'待处理',RESOLVED:'已解决',IGNORED:'已忽略'} as Record<string,string>)[v]||v}
function categoryText(v:string){return ({KNOWLEDGE_REUSE:'知识沉淀',AGENT_FAILURE:'Agent失败',PROCESS_POLICY:'流程策略'} as Record<string,string>)[v]||v}
onMounted(()=>{loadReport();loadImprovements()})
</script>

<style scoped lang="scss">
.ops-page{padding:24px;background:var(--bg-surface,#fff);border-radius:12px;min-height:100%}.page-header{display:flex;justify-content:space-between;gap:20px;margin-bottom:18px}.page-header h2{margin:0 0 6px}.page-header p,.muted,.metric-hint{color:#8c8c8c}.metric-row{row-gap:16px}.metric-hint{font-size:12px;margin-top:6px}.section-row{margin-top:16px}.queue-tip{margin-bottom:16px}pre{margin-top:18px;max-height:520px;overflow:auto;padding:14px;background:#f6f8fa;border-radius:8px;white-space:pre-wrap;word-break:break-word}
</style>
