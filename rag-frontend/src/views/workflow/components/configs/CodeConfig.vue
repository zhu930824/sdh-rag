<template>
  <div class="config-section">
    <div class="section-title">代码配置</div>

    <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 18 }">
      <!-- 语言选择 -->
      <a-form-item label="语言">
        <a-select v-model:value="language" size="small">
          <a-select-option value="javascript">JavaScript</a-select-option>
          <a-select-option value="python">Python</a-select-option>
        </a-select>
      </a-form-item>

      <!-- 代码编辑 -->
      <a-form-item label="代码">
        <a-textarea
          v-model:value="code"
          placeholder="请输入代码"
          :rows="12"
          style="font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace; font-size: 12px"
        />
      </a-form-item>
    </a-form>

    <div class="code-tip">
      <div class="tip-title">💡 代码说明</div>
      <div class="tip-content">
        <p>• 输入参数可通过 <code>input.参数名</code> 访问</p>
        <p>• 最后一个表达式的值将作为输出</p>
        <p>• 使用 <code>return</code> 返回结果对象</p>
      </div>
    </div>
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

const language = computed({
  get: () => props.node.data.language || 'javascript',
  set: (val) => emit('update', { language: val }),
})

const code = computed({
  get: () => props.node.data.code || '',
  set: (val) => emit('update', { code: val }),
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

.code-tip {
  margin-top: 12px;
  background: #e6f7ff;
  border-radius: 6px;
  padding: 12px;
  font-size: 12px;

  .tip-title {
    font-weight: 500;
    margin-bottom: 8px;
  }

  .tip-content {
    color: #595959;

    p {
      margin: 4px 0;
    }

    code {
      background: #bae7ff;
      padding: 1px 4px;
      border-radius: 2px;
      font-family: monospace;
    }
  }
}
</style>
