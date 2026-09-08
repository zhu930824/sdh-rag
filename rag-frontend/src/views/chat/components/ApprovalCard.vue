<template>
  <div class="approval-card">
    <div class="approval-header">
      <SafetyCertificateFilled class="approval-icon" />
      <span class="approval-title">需要人工审批</span>
      <a-tag color="warning">业务操作</a-tag>
    </div>
    <div class="approval-body">
      <div class="approval-row">
        <span class="label">工具:</span>
        <span class="value">{{ approval.toolName || approval.toolCode }}</span>
      </div>
      <div v-if="approval.argsPreview" class="approval-row">
        <span class="label">参数:</span>
        <pre class="approval-json">{{ prettyJson(approval.argsPreview) }}</pre>
      </div>
      <div v-if="approval.evidencePreview" class="approval-row">
        <span class="label">依据:</span>
        <pre class="approval-json evidence">{{ prettyJson(approval.evidencePreview) }}</pre>
      </div>
      <div v-if="!approval.canApprove" class="approval-note">
        审批人为其他用户,请到
        <router-link to="/agent-actions">Agent 审批与执行</router-link>
        中心处理。
      </div>
    </div>
    <div v-if="approval.canApprove" class="approval-actions">
      <a-button size="small" type="primary" :loading="acting" @click="handleApprove">通过并继续</a-button>
      <a-popconfirm title="拒绝该业务操作?" ok-text="拒绝" cancel-text="取消" @confirm="handleReject">
        <template #icon><StopOutlined style="color: red" /></template>
        <a-button size="small" danger :loading="acting">拒绝</a-button>
      </a-popconfirm>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { SafetyCertificateFilled, StopOutlined } from '@ant-design/icons-vue'
import type { AgentPendingApproval, ChatMessage } from '@/api/chat'
import { useChatStore } from '@/stores/chat'

const props = defineProps<{
  approval: AgentPendingApproval
  message: ChatMessage
}>()

const store = useChatStore()
const acting = ref(false)

async function handleApprove(): Promise<void> {
  acting.value = true
  try {
    await store.decideInline(props.message, props.approval, 'approve')
  } finally {
    acting.value = false
  }
}

async function handleReject(): Promise<void> {
  acting.value = true
  try {
    await store.decideInline(props.message, props.approval, 'reject')
    message.info('已拒绝该操作')
  } finally {
    acting.value = false
  }
}

function prettyJson(value?: string): string {
  if (!value) return ''
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}
</script>

<style scoped>
.approval-card {
  border: 1px solid #ffe58f;
  border-radius: 8px;
  margin: 8px 0;
  background: #fffbe6;
  overflow: hidden;
}
.approval-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #fff7e6;
  border-bottom: 1px dashed #ffe58f;
}
.approval-icon {
  color: #faad14;
}
.approval-title {
  font-weight: 600;
  color: #ad6800;
}
.approval-body {
  padding: 8px 12px;
}
.approval-row {
  display: flex;
  gap: 8px;
  margin: 4px 0;
  align-items: flex-start;
}
.approval-row .label {
  color: #8c8c8c;
  flex-shrink: 0;
}
.approval-row .value {
  color: #333;
}
.approval-json {
  background: #fff;
  border-radius: 6px;
  padding: 8px;
  font-size: 12px;
  max-height: 160px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
  flex: 1;
}
.approval-json.evidence {
  background: #f6ffed;
}
.approval-note {
  color: #999;
  font-size: 12px;
  margin: 4px 0;
}
.approval-actions {
  padding: 6px 12px 10px;
  display: flex;
  gap: 10px;
  border-top: 1px dashed #ffe58f;
}
</style>
