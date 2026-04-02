<template>
  <div class="config-section">
    <div class="section-title">HTTP 配置</div>

    <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 18 }">
      <!-- 请求方法 -->
      <a-form-item label="请求方法">
        <a-select v-model:value="method" size="small">
          <a-select-option value="GET">GET</a-select-option>
          <a-select-option value="POST">POST</a-select-option>
          <a-select-option value="PUT">PUT</a-select-option>
          <a-select-option value="DELETE">DELETE</a-select-option>
        </a-select>
      </a-form-item>

      <!-- URL -->
      <a-form-item label="URL">
        <a-input v-model:value="url" placeholder="请输入 URL" size="small" />
      </a-form-item>

      <!-- 请求头 -->
      <a-form-item label="请求头">
        <a-textarea
          v-model:value="headers"
          placeholder='{"Content-Type": "application/json"}'
          :rows="3"
          size="small"
          style="font-family: monospace"
        />
      </a-form-item>

      <!-- 请求体 -->
      <a-form-item v-if="method !== 'GET'" label="请求体">
        <a-textarea
          v-model:value="body"
          placeholder="请求体内容"
          :rows="4"
          size="small"
          style="font-family: monospace"
        />
      </a-form-item>
    </a-form>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { WorkflowNodeData } from '@/stores/workflow'
import type { Node } from '@vue-flow/core'

const props = defineProps<{
  node: Node<WorkflowNodeData>
}>()

const emit = defineEmits<{
  (e: 'update', data: Partial<WorkflowNodeData>): void
}>()

const method = computed({
  get: () => props.node.data.method || 'GET',
  set: (val) => emit('update', { method: val }),
})

const url = computed({
  get: () => props.node.data.url || '',
  set: (val) => emit('update', { url: val }),
})

const headers = computed({
  get: () => props.node.data.headers || '',
  set: (val) => emit('update', { headers: val }),
})

const body = computed({
  get: () => props.node.data.body || '',
  set: (val) => emit('update', { body: val }),
})
</script>

<style scoped lang="scss">
.config-section {
  .section-title {
    font-size: 13px;
    font-weight: 500;
    color: #262626;
    margin-bottom: 12px;
    padding-bottom: 8px;
    border-bottom: 1px solid #f0f0f0;
  }
}
</style>
