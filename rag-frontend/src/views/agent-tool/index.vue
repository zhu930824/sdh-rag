<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <h2>业务工具中心</h2>
        <p>把业务系统能力登记成受控工具，统一管理风险、审批人、凭据引用与网络边界。</p>
      </div>
      <div class="header-actions">
        <a-button @click="router.push('/agent-actions')">审批与执行记录</a-button>
        <a-button type="primary" @click="openCreate">新建工具</a-button>
      </div>
    </div>

    <a-alert type="info" show-icon class="security-tip" message="平台不保存明文密钥。请使用 env:VARIABLE_NAME 引用服务端环境变量；工具节点只能调用这里登记的固定 endpoint。" />

    <a-table :columns="columns" :data-source="tools" :loading="loading" row-key="id" :pagination="false">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'name'">
          <div class="tool-name">{{ record.name }} <a-tag>{{ record.code }}</a-tag></div>
          <div class="muted ellipsis">{{ record.description || record.endpoint }}</div>
        </template>
        <template v-else-if="column.key === 'method'"><a-tag color="blue">{{ record.httpMethod }}</a-tag></template>
        <template v-else-if="column.key === 'risk'">
          <a-tag :color="riskColor(record.riskLevel)">{{ riskText(record.riskLevel) }}</a-tag>
          <span class="muted">{{ approvalText(record.approvalMode) }}</span>
        </template>
        <template v-else-if="column.key === 'status'">
          <a-badge :status="record.status === 1 ? 'success' : 'default'" :text="record.status === 1 ? '启用' : '停用'" />
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space><a-button type="link" size="small" @click="openEdit(record)">编辑</a-button><a-button v-if="record.status === 1" danger type="link" size="small" @click="disable(record)">停用</a-button></a-space>
        </template>
      </template>
    </a-table>

    <a-modal v-model:open="modalOpen" :title="editingId ? '编辑业务工具' : '新建业务工具'" width="720px" :confirm-loading="saving" @ok="save">
      <a-form layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12"><a-form-item label="工具名称" required><a-input v-model:value="form.name" placeholder="例如：创建售后工单" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="工具编码" required><a-input v-model:value="form.code" placeholder="create_ticket" /></a-form-item></a-col>
        </a-row>
        <a-form-item label="说明"><a-input v-model:value="form.description" placeholder="说明适用业务场景和使用边界" /></a-form-item>
        <a-row :gutter="16">
          <a-col :span="6"><a-form-item label="方法"><a-select v-model:value="form.httpMethod"><a-select-option v-for="method in methods" :key="method" :value="method">{{ method }}</a-select-option></a-select></a-form-item></a-col>
          <a-col :span="18"><a-form-item label="固定 endpoint" required><a-input v-model:value="form.endpoint" placeholder="https://business.example.com/api/tickets" /></a-form-item></a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="8"><a-form-item label="认证类型"><a-select v-model:value="form.authType"><a-select-option value="none">无认证</a-select-option><a-select-option value="bearer">Bearer</a-select-option><a-select-option value="header">自定义请求头</a-select-option></a-select></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="凭据引用"><a-input v-model:value="form.credentialRef" :disabled="form.authType === 'none'" placeholder="env:TICKET_API_KEY" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="认证请求头"><a-input v-model:value="form.authHeader" :disabled="form.authType !== 'header'" placeholder="X-API-Key" /></a-form-item></a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="8"><a-form-item label="风险等级"><a-select v-model:value="form.riskLevel"><a-select-option value="LOW">低</a-select-option><a-select-option value="MEDIUM">中</a-select-option><a-select-option value="HIGH">高</a-select-option></a-select></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="审批策略"><a-select v-model:value="form.approvalMode"><a-select-option value="NEVER">无需审批</a-select-option><a-select-option value="HIGH_RISK">中高风险审批</a-select-option><a-select-option value="ALWAYS">总是审批</a-select-option></a-select></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="审批人 ID"><a-input-number v-model:value="form.approverId" :min="1" style="width:100%" placeholder="留空为当前用户" /></a-form-item></a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="8"><a-form-item label="超时秒数"><a-input-number v-model:value="form.timeoutSeconds" :min="3" :max="120" style="width:100%" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="允许访问内网"><a-switch v-model:checked="form.allowPrivateNetwork" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="允许 HTTP"><a-switch v-model:checked="form.allowInsecureHttp" /></a-form-item></a-col>
        </a-row>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { createAgentTool, disableAgentTool, getAgentTools, updateAgentTool, type AgentTool, type AgentToolForm } from '@/api/agent'

const router = useRouter()
const tools = ref<AgentTool[]>([])
const loading = ref(false)
const saving = ref(false)
const modalOpen = ref(false)
const editingId = ref<number>()
const methods = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE']
const columns = [
  { title: '工具', key: 'name', width: 320 }, { title: '方法', key: 'method', width: 90 },
  { title: '风险与审批', key: 'risk', width: 210 }, { title: '状态', key: 'status', width: 90 },
  { title: '操作', key: 'action', width: 130 },
]
const defaults = (): AgentToolForm => ({ name: '', code: '', description: '', endpoint: '', httpMethod: 'POST', credentialRef: '', authType: 'none', authHeader: '', riskLevel: 'MEDIUM', approvalMode: 'HIGH_RISK', approverId: undefined, timeoutSeconds: 30, allowPrivateNetwork: false, allowInsecureHttp: false, status: 1 })
const form = reactive<AgentToolForm>(defaults())

async function load() { loading.value = true; try { tools.value = (await getAgentTools()).data || [] } finally { loading.value = false } }
function openCreate() { editingId.value = undefined; Object.assign(form, defaults()); modalOpen.value = true }
function openEdit(tool: AgentTool) {
  editingId.value = tool.id
  Object.assign(form, { ...tool, allowPrivateNetwork: tool.allowPrivateNetwork === 1, allowInsecureHttp: tool.allowInsecureHttp === 1 })
  modalOpen.value = true
}
async function save() {
  if (!form.name || !form.code || !form.endpoint) return message.warning('请填写工具名称、编码和 endpoint')
  saving.value = true
  try {
    if (editingId.value) await updateAgentTool(editingId.value, { ...form })
    else await createAgentTool({ ...form })
    message.success('业务工具已保存'); modalOpen.value = false; await load()
  } finally { saving.value = false }
}
function disable(tool: AgentTool) {
  Modal.confirm({ title: `停用“${tool.name}”？`, content: '已有动作审计会保留，新工作流调用将被阻止。', okType: 'danger', async onOk() { await disableAgentTool(tool.id); message.success('工具已停用'); await load() } })
}
function riskText(value: string) { return ({ LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险' } as Record<string,string>)[value] || value }
function riskColor(value: string) { return ({ LOW: 'green', MEDIUM: 'orange', HIGH: 'red' } as Record<string,string>)[value] || 'default' }
function approvalText(value: string) { return ({ NEVER: '无需审批', HIGH_RISK: '中高风险审批', ALWAYS: '总是审批' } as Record<string,string>)[value] || value }
onMounted(load)
</script>

<style scoped lang="scss">
.page-shell { padding: 24px; background: var(--bg-surface, #fff); border-radius: 12px; min-height: 100%; }
.page-header { display:flex; align-items:flex-start; justify-content:space-between; gap:20px; margin-bottom:16px; }
.page-header h2 { margin:0 0 6px; }.page-header p,.muted { color:#8c8c8c; }.header-actions { display:flex; gap:8px; }
.security-tip { margin-bottom:18px; }.tool-name { font-weight:600; margin-bottom:4px; }.ellipsis { max-width:290px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
</style>
