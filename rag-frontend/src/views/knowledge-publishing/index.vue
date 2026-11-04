<template>
  <div class="publishing-page">
    <div class="page-header">
      <div><h2>知识审核与发布</h2><p>把真实业务经验编辑成可审核、可验证、可回滚的知识，再进入统一检索链路。</p></div>
      <a-space><a-select v-model:value="status" allow-clear placeholder="全部状态" style="width:150px" @change="load"><a-select-option v-for="item in statusOptions" :key="item.value" :value="item.value">{{ item.label }}</a-select-option></a-select><a-button :loading="loading" @click="load">刷新状态</a-button><a-button @click="router.push('/agent-ops')">返回改进队列</a-button><a-button type="primary" @click="openCreate">新建草稿</a-button></a-space>
    </div>
    <a-alert type="info" show-icon message="发布先构建隔离候选索引；全部回归问题须在 Top 5 中召回本次候选批次，通过后才参与线上检索。" class="tip" />
    <a-table :columns="columns" :data-source="drafts" :loading="loading" row-key="id" :pagination="{ pageSize: 12 }">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'title'"><strong>{{ record.title }}</strong><div class="muted">知识库 #{{ record.knowledgeId }} · 文档 #{{ record.publishedDocumentId || '-' }}</div></template>
        <template v-else-if="column.key === 'status'"><a-tag :color="statusColor(record.status)">{{ statusText(record.status) }}</a-tag></template>
        <template v-else-if="column.key === 'validation'"><a-tag :color="record.validationStatus === 'PASSED' ? 'green' : record.validationStatus === 'FAILED' ? 'red' : 'default'">{{ validationText(record.validationStatus) }}</a-tag></template>
        <template v-else-if="column.key === 'action'">
          <a-space wrap>
            <a-button type="link" size="small" @click="openDetail(record)">详情</a-button>
            <a-button v-if="editable(record)" type="link" size="small" @click="openEdit(record)">编辑</a-button>
            <a-button v-if="editable(record)" type="link" size="small" @click="submit(record)">提交审核</a-button>
            <a-button v-if="record.status === 'IN_REVIEW'" type="link" size="small" @click="openReview(record)">审核</a-button>
            <a-button v-if="record.status === 'APPROVED'" type="link" size="small" @click="publish(record)">发布</a-button>
            <a-button v-if="record.status === 'PUBLISHED'" type="link" size="small" @click="validate(record)">回归验证</a-button>
            <a-popconfirm v-if="['PUBLISHED','PUBLISH_FAILED','GATE_FAILED'].includes(record.status)" title="将移除知识库关联与检索向量，确认回滚？" @confirm="rollback(record)"><a-button type="link" danger size="small">回滚</a-button></a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-drawer v-model:open="editorOpen" :title="editing?.id ? '编辑知识草稿' : '新建知识草稿'" width="720" :destroy-on-close="true">
      <a-form layout="vertical">
        <a-form-item label="目标知识库" required><a-select v-model:value="form.knowledgeId" show-search option-filter-prop="label" :options="knowledgeOptions" /></a-form-item>
        <a-form-item label="知识标题" required><a-input v-model:value="form.title" :maxlength="255" /></a-form-item>
        <a-form-item label="知识正文" required extra="至少 50 个字符；建议写清适用场景、处理步骤、限制与验证结果。"><a-textarea v-model:value="form.content" :rows="14" show-count /></a-form-item>
        <a-form-item label="回归问题" extra="提交审核前至少填写 1 个，最多 10 个，每个不超过 500 字符。"><a-textarea v-model:value="queryText" :rows="5" placeholder="例如：客户退款超过 30 天应如何处理？" /></a-form-item>
      </a-form>
      <template #extra><a-space><a-button @click="editorOpen=false">取消</a-button><a-button type="primary" :loading="saving" @click="save">保存草稿</a-button></a-space></template>
    </a-drawer>

    <a-drawer v-model:open="detailOpen" title="发布详情与审计" width="720">
      <a-descriptions v-if="selected" bordered :column="1" size="small">
        <a-descriptions-item label="标题">{{ selected.title }}</a-descriptions-item><a-descriptions-item label="状态">{{ statusText(selected.status) }}</a-descriptions-item><a-descriptions-item label="审核意见">{{ selected.reviewNote || '-' }}</a-descriptions-item><a-descriptions-item label="发布错误">{{ selected.errorMessage || '-' }}</a-descriptions-item><a-descriptions-item label="回归结果">{{ selected.validationResult || '-' }}</a-descriptions-item>
      </a-descriptions>
      <h4>正文</h4><pre>{{ selected?.content }}</pre><h4>审计记录</h4>
      <a-timeline><a-timeline-item v-for="item in auditRecords" :key="item.id" :color="item.status === 'FAILED' ? 'red' : item.status === 'PASSED' || item.status === 'COMPLETED' ? 'green' : 'blue'"><strong>{{ actionText(item.action) }} · {{ item.status }}</strong><div class="muted">{{ item.createdAt }} · 操作人 #{{ item.operatorId }}</div><div>{{ item.detail }}</div></a-timeline-item></a-timeline>
    </a-drawer>

    <a-modal v-model:open="reviewOpen" title="审核知识草稿" :footer="null"><a-textarea v-model:value="reviewNote" :rows="4" placeholder="填写审核依据或驳回修改建议" /><div class="review-actions"><a-button danger @click="review(false)">驳回修改</a-button><a-button type="primary" @click="review(true)">审核通过</a-button></div></a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { getKnowledgeBaseList, type KnowledgeBase } from '@/api/knowledgeBase'
import {
  createKnowledgeDraft, getKnowledgeDrafts, getKnowledgePublishRecords, publishKnowledgeDraft,
  reviewKnowledgeDraft, rollbackKnowledgeDraft, submitKnowledgeDraft, updateKnowledgeDraft,
  validateKnowledgeDraft, type KnowledgeDraft, type KnowledgePublishRecord,
} from '@/api/knowledgePublishing'

const router=useRouter(); const drafts=ref<KnowledgeDraft[]>([]); const loading=ref(false); const saving=ref(false); const status=ref<string>()
const editorOpen=ref(false); const detailOpen=ref(false); const reviewOpen=ref(false); const editing=ref<KnowledgeDraft>(); const selected=ref<KnowledgeDraft>(); const auditRecords=ref<KnowledgePublishRecord[]>([]); const reviewNote=ref(''); const queryText=ref('')
const form=reactive({knowledgeId:undefined as number|undefined,title:'',content:'',improvementId:undefined as number|undefined}); const knowledgeOptions=ref<Array<{label:string,value:number}>>([])
const statusOptions=[['DRAFT','草稿'],['IN_REVIEW','待审核'],['APPROVED','审核通过'],['REJECTED','已驳回'],['PUBLISHING','索引与门禁中'],['PUBLISHED','已发布'],['PUBLISH_FAILED','发布失败'],['GATE_FAILED','质量门禁失败'],['ROLLED_BACK','已回滚']].map(([value,label])=>({value,label}))
const columns=[{title:'知识草稿',key:'title'},{title:'状态',key:'status',width:110},{title:'回归验证',key:'validation',width:110},{title:'更新时间',dataIndex:'updatedAt',key:'updatedAt',width:180},{title:'操作',key:'action',width:360}]
async function load(){loading.value=true;try{drafts.value=(await getKnowledgeDrafts(status.value)).data||[]}finally{loading.value=false}}
async function loadKnowledge(){const res=await getKnowledgeBaseList({page:1,pageSize:200});knowledgeOptions.value=(res.data?.records||[]).filter((k:KnowledgeBase)=>k.accessRole!=='reader').map((k:KnowledgeBase)=>({label:k.name,value:k.id}))}
function openCreate(){editing.value=undefined;Object.assign(form,{knowledgeId:undefined,title:'',content:'',improvementId:undefined});queryText.value='';editorOpen.value=true}
function openEdit(value:any){const item=value as KnowledgeDraft;editing.value=item;Object.assign(form,{knowledgeId:item.knowledgeId,title:item.title,content:item.content,improvementId:item.improvementId});queryText.value=parseQueries(item.regressionQueries).join('\n');editorOpen.value=true}
async function save(){if(!form.knowledgeId||!form.title.trim()||form.content.trim().length<50){message.warning('请选择知识库，并填写标题和至少 50 个字符的正文');return}saving.value=true;try{const data={knowledgeId:form.knowledgeId,title:form.title.trim(),content:form.content.trim(),improvementId:form.improvementId,regressionQueries:queryText.value.split('\n').map(v=>v.trim()).filter(Boolean)};if(editing.value)await updateKnowledgeDraft(editing.value.id,data);else await createKnowledgeDraft(data);message.success('草稿已保存');editorOpen.value=false;await load()}finally{saving.value=false}}
async function submit(value:any){const item=value as KnowledgeDraft;if(!parseQueries(item.regressionQueries).length){message.warning('请先编辑草稿并填写至少一个回归问题');return}await submitKnowledgeDraft(item.id);message.success('已提交审核');await load()}
function openReview(value:any){selected.value=value as KnowledgeDraft;reviewNote.value='';reviewOpen.value=true}
async function review(approved:boolean){if(!selected.value)return;await reviewKnowledgeDraft(selected.value.id,approved,reviewNote.value);message.success(approved?'审核通过':'已驳回修改');reviewOpen.value=false;await load()}
async function publish(value:any){const item=value as KnowledgeDraft;await publishKnowledgeDraft(item.id);message.success('已提交候选索引与门禁任务，通过后才会发布');await load()}
async function validate(value:any){const item=value as KnowledgeDraft;const res=await validateKnowledgeDraft(item.id);if(res.data?.passed)message.success('全部回归问题验证通过');else message.warning('部分回归问题未召回新知识');await load()}
async function rollback(value:any){const item=value as KnowledgeDraft;await rollbackKnowledgeDraft(item.id);message.success('知识已从检索链路回滚');await load()}
async function openDetail(value:any){const item=value as KnowledgeDraft;selected.value=item;detailOpen.value=true;auditRecords.value=(await getKnowledgePublishRecords(item.id)).data||[]}
function editable(value:any){const item=value as KnowledgeDraft;return ['DRAFT','REJECTED','APPROVED'].includes(item.status)&&!item.publishedDocumentId} function parseQueries(value?:string){try{return value?JSON.parse(value):[]}catch{return[]}}
function statusText(value:string){return Object.fromEntries(statusOptions.map(i=>[i.value,i.label]))[value]||value} function statusColor(value:string){return ({DRAFT:'default',IN_REVIEW:'orange',APPROVED:'cyan',REJECTED:'red',PUBLISHING:'blue',PUBLISHED:'green',PUBLISH_FAILED:'red',GATE_FAILED:'red',ROLLED_BACK:'default'} as Record<string,string>)[value]||'default'}
function validationText(value:string){return ({NOT_RUN:'未验证',PASSED:'已通过',FAILED:'未通过'} as Record<string,string>)[value]||value} function actionText(value:string){return ({PUBLISH:'发布',VALIDATE:'回归验证',ROLLBACK:'回滚'} as Record<string,string>)[value]||value}
onMounted(()=>{load();loadKnowledge()})
</script>

<style scoped lang="scss">
.publishing-page{padding:24px;background:var(--bg-surface,#fff);border-radius:12px;min-height:100%}.page-header{display:flex;justify-content:space-between;gap:20px;margin-bottom:18px}.page-header h2{margin:0 0 6px}.page-header p,.muted{color:#8c8c8c}.tip{margin-bottom:16px}pre{padding:14px;background:#f6f8fa;border-radius:8px;white-space:pre-wrap;word-break:break-word;max-height:380px;overflow:auto}.review-actions{display:flex;justify-content:flex-end;gap:12px;margin-top:18px}h4{margin-top:22px}
</style>
