<template>
  <div class="config-panel">
    <a-form
      :model="form"
      :label-col="{ span: 6 }"
      :wrapper-col="{ span: 14 }"
      @finish="handleSave"
    >
      <a-form-item label="分块大小" name="chunkSize">
        <a-input-number
          v-model:value="form.chunkSize"
          :min="100"
          :max="5000"
          :step="100"
          style="width: 200px"
        />
        <span class="form-hint">字符数，推荐 500-1000</span>
      </a-form-item>

      <a-form-item label="分块重叠" name="chunkOverlap">
        <a-input-number
          v-model:value="form.chunkOverlap"
          :min="0"
          :max="500"
          :step="10"
          style="width: 200px"
        />
        <span class="form-hint">字符数，推荐 50-100</span>
      </a-form-item>

      <a-form-item label="嵌入模型" name="embeddingModel">
        <a-select v-model:value="form.embeddingModel" style="width: 300px">
          <a-select-option value="text-embedding-ada-002">text-embedding-ada-002 (OpenAI)</a-select-option>
          <a-select-option value="text-embedding-3-small">text-embedding-3-small (OpenAI)</a-select-option>
          <a-select-option value="text-embedding-3-large">text-embedding-3-large (OpenAI)</a-select-option>
          <a-select-option value="dashscope/text-embedding-v2">text-embedding-v2 (通义千问)</a-select-option>
          <a-select-option value="bge-small-zh">bge-small-zh (本地)</a-select-option>
        </a-select>
      </a-form-item>

      <a-form-item :wrapper-col="{ offset: 6, span: 14 }">
        <a-space>
          <a-button type="primary" html-type="submit" :loading="saving">保存配置</a-button>
          <a-button @click="handleReset">重置</a-button>
        </a-space>
      </a-form-item>
    </a-form>

    <a-divider />

    <a-alert
      message="配置修改说明"
      description="修改分块配置后，新关联的文档将使用新配置进行处理。已处理的文档需要重新处理才能应用新配置。"
      type="info"
      show-icon
    />
  </div>
</template>

<script setup lang="ts">
import { reactive, watch, ref } from 'vue'
import type { KnowledgeBase } from '@/api/knowledgeBase'

const props = defineProps<{
  knowledgeBase: KnowledgeBase | null
}>()

const emit = defineEmits<{
  (e: 'save', config: { chunkSize: number; chunkOverlap: number; embeddingModel: string }): void
}>()

const saving = ref(false)

const form = reactive({
  chunkSize: 500,
  chunkOverlap: 50,
  embeddingModel: 'text-embedding-ada-002',
})

watch(() => props.knowledgeBase, (kb) => {
  if (kb) {
    form.chunkSize = kb.chunkSize || 500
    form.chunkOverlap = kb.chunkOverlap || 50
    form.embeddingModel = kb.embeddingModel || 'text-embedding-ada-002'
  }
}, { immediate: true })

function handleSave() {
  emit('save', {
    chunkSize: form.chunkSize,
    chunkOverlap: form.chunkOverlap,
    embeddingModel: form.embeddingModel,
  })
}

function handleReset() {
  if (props.knowledgeBase) {
    form.chunkSize = props.knowledgeBase.chunkSize || 500
    form.chunkOverlap = props.knowledgeBase.chunkOverlap || 50
    form.embeddingModel = props.knowledgeBase.embeddingModel || 'text-embedding-ada-002'
  }
}
</script>

<style scoped lang="scss">
.config-panel {
  max-width: 600px;

  .form-hint {
    margin-left: 12px;
    color: var(--text-tertiary);
    font-size: 12px;
}
}
</style>