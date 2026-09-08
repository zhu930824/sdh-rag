<template>
  <div class="config-section">
    <div class="section-title">受控业务工具</div>
    <a-alert type="info" show-icon message="工具地址与凭据由工具中心统一治理；高风险调用会暂停并进入审批中心。" />
    <a-form layout="vertical" class="tool-form">
      <a-form-item label="业务工具" required>
        <a-select v-model:value="toolId" :loading="loading" placeholder="请选择已启用工具" show-search option-filter-prop="label">
          <a-select-option v-for="tool in tools" :key="tool.id" :value="tool.id" :label="tool.name">
            {{ tool.name }} · {{ riskLabel(tool.riskLevel) }}
          </a-select-option>
        </a-select>
        <a-button type="link" size="small" class="manage-link" @click="router.push('/agent-tools')">管理业务工具</a-button>
      </a-form-item>
      <a-form-item label="请求参数（JSON）" required>
        <a-textarea v-model:value="parameters" :rows="7" class="code-input" placeholder='{"customerId":"{{customerId}}","answer":"{{output}}"}' />
        <div class="hint">{{ variableHint }}</div>
      </a-form-item>
      <a-form-item label="知识依据字段">
        <a-input v-model:value="evidenceKey" placeholder="documents" />
        <div class="hint">检索节点输出的 documents 会随动作存档，便于解释和复盘。</div>
      </a-form-item>
      <a-form-item label="幂等键（可选）">
        <a-input v-model:value="idempotencyKey" placeholder="{{requestId}}" />
        <div class="hint">同一工具、用户与幂等键只执行一次；留空则每次生成新键。</div>
      </a-form-item>
    </a-form>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { Node } from '@vue-flow/core'
import { getAgentTools, type AgentTool } from '@/api/agent'
import type { WorkflowNodeData } from '@/stores/workflow'

const props = defineProps<{ node: Node<WorkflowNodeData> }>()
const emit = defineEmits<{ (e: 'update', data: Partial<WorkflowNodeData>): void }>()
const router = useRouter()
const tools = ref<AgentTool[]>([])
const loading = ref(false)

const toolId = computed({
  get: () => props.node.data.toolId,
  set: (value) => {
    const selected = tools.value.find(tool => tool.id === value)
    emit('update', { toolId: value, toolName: selected?.name || '' })
  },
})
const parameters = computed({ get: () => props.node.data.parameters || '{}', set: value => emit('update', { parameters: value }) })
const evidenceKey = computed({ get: () => props.node.data.evidenceKey || 'documents', set: value => emit('update', { evidenceKey: value }) })
const idempotencyKey = computed({ get: () => props.node.data.idempotencyKey || '', set: value => emit('update', { idempotencyKey: value }) })

function riskLabel(value: string) { return ({ LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险' } as Record<string, string>)[value] || value }
// 字面量花括号须经 JS 常量输出：模板中直接写 {{ '{{变量名}}' }} 会被编译器把引号内的 }} 误判为插值结束
const variableHint = '支持 {{变量名}}，字符串变量请放在 JSON 引号内。'
onMounted(async () => {
  loading.value = true
  try { tools.value = (await getAgentTools()).data?.filter(tool => tool.status === 1) || [] }
  finally { loading.value = false }
})
</script>

<style scoped lang="scss">
.tool-form { margin-top: 16px; }
.manage-link { padding-left: 0; }
.code-input { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }
.hint { margin-top: 4px; color: #8c8c8c; font-size: 12px; line-height: 1.5; }
</style>
