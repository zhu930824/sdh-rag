<template>
  <div class="agent-step-item" :class="'status-' + step.status">
    <div class="step-header">
      <LoadingOutlined v-if="step.status === 'RUNNING'" class="step-icon running" spin />
      <CheckCircleFilled v-else-if="step.status === 'SUCCEEDED'" class="step-icon success" />
      <ClockCircleFilled v-else-if="step.status === 'PENDING_APPROVAL'" class="step-icon pending" />
      <StopFilled v-else-if="step.status === 'REJECTED'" class="step-icon rejected" />
      <CloseCircleFilled v-else class="step-icon failed" />
      <span class="step-name">{{ label }}</span>
      <span class="step-status-text">{{ statusText }}</span>
      <span v-if="step.latencyMs != null" class="step-latency">{{ step.latencyMs }}ms</span>
    </div>
    <a-collapse v-if="hasDetail" ghost class="step-detail-collapse">
      <template #expandIcon> </template>
      <a-collapse-panel key="1" :show-arrow="false">
        <template #header>
          <span class="detail-toggle">查看参数/结果</span>
        </template>
        <div v-if="step.argsPreview" class="step-detail">
          <div class="detail-label">参数</div>
          <pre class="detail-json">{{ prettyJson(step.argsPreview) }}</pre>
        </div>
        <div v-if="step.resultPreview" class="step-detail">
          <div class="detail-label">结果</div>
          <pre class="detail-json" :class="{ error: step.status !== 'SUCCEEDED' }">{{ prettyJson(step.resultPreview) }}</pre>
        </div>
      </a-collapse-panel>
    </a-collapse>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  LoadingOutlined,
  CheckCircleFilled,
  ClockCircleFilled,
  StopFilled,
  CloseCircleFilled,
} from '@ant-design/icons-vue'
import type { AgentStepView } from '@/api/chat'

const props = defineProps<{ step: AgentStepView }>()

const label = computed(() => props.step.toolCode || (props.step.type === 'llm' ? '模型思考' : '步骤'))
const hasDetail = computed(() => !!props.step.argsPreview || !!props.step.resultPreview)
const statusText = computed(() => {
  switch (props.step.status) {
    case 'RUNNING':
      return '执行中'
    case 'SUCCEEDED':
      return '成功'
    case 'FAILED':
      return '失败'
    case 'PENDING_APPROVAL':
      return '等待审批'
    case 'REJECTED':
      return '已拒绝'
    default:
      return props.step.status
  }
})

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
.agent-step-item {
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  padding: 6px 12px;
  margin: 6px 0;
  background: #fafafa;
}
.step-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
.step-icon.running {
  color: #1677ff;
}
.step-icon.success {
  color: #52c41a;
}
.step-icon.pending {
  color: #faad14;
}
.step-icon.rejected {
  color: #ff4d4f;
}
.step-icon.failed {
  color: #ff4d4f;
}
.step-name {
  font-weight: 600;
}
.step-status-text {
  color: #888;
  font-size: 12px;
}
.step-latency {
  margin-left: auto;
  color: #aaa;
  font-size: 12px;
}
.detail-toggle {
  font-size: 12px;
  color: #1677ff;
}
.step-detail-collapse {
  margin-top: 4px;
}
.step-detail {
  margin: 2px 0;
}
.detail-label {
  font-size: 12px;
  color: #999;
}
.detail-json {
  background: #f5f5f5;
  border-radius: 6px;
  padding: 8px;
  font-size: 12px;
  max-height: 220px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 4px 0 0;
}
.detail-json.error {
  background: #fff1f0;
}
</style>
