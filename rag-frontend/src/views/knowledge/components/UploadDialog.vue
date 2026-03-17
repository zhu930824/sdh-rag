<template>
  <a-modal
    :open="open"
    title="上传文档"
    :width="600"
    :mask-closable="false"
    @update:open="handleClose"
  >
    <a-form ref="formRef" :model="formData" :rules="rules" :label-col="{ span: 4 }">
      <a-form-item label="文档分类" name="categoryId">
        <a-tree-select
          v-model:value="formData.categoryId"
          :tree-data="knowledgeStore.categoryTree"
          :field-names="{ label: 'name', value: 'id', children: 'children' }"
          placeholder="请选择分类"
          allow-clear
          tree-default-expand-all
          style="width: 100%"
        />
      </a-form-item>

      <a-form-item label="上传文件" name="files">
        <a-upload-dragger
          v-model:file-list="fileList"
          :multiple="true"
          :before-upload="beforeUpload"
          :max-count="10"
          accept=".pdf,.doc,.docx,.txt"
          @remove="handleFileRemove"
        >
          <p class="ant-upload-drag-icon">
            <InboxOutlined />
          </p>
          <p class="ant-upload-text">点击或拖拽文件到此区域上传</p>
          <p class="ant-upload-hint">
            支持 PDF、Word、TXT 格式，单个文件不超过 50MB，最多上传 10 个文件
          </p>
        </a-upload-dragger>
      </a-form-item>
    </a-form>

    <div v-if="uploading" class="upload-progress">
      <a-progress :percent="uploadProgress" :status="uploadStatus" />
      <div class="progress-text">{{ uploadProgressText }}</div>
    </div>

    <template #footer>
      <a-button @click="handleClose">取消</a-button>
      <a-button type="primary" :loading="uploading" :disabled="fileList.length === 0" @click="handleSubmit">
        开始上传
      </a-button>
    </template>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { InboxOutlined } from '@ant-design/icons-vue'
import type { FormInstance, Rule } from 'ant-design-vue/es/form'
import type { UploadFile, UploadProps } from 'ant-design-vue'
import { useKnowledgeStore } from '@/stores/knowledge'
import { uploadDocument } from '@/api/knowledge'
import { showSuccess, showError, showWarning } from '@/utils/message'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  success: []
}>()

const knowledgeStore = useKnowledgeStore()

const formRef = ref<FormInstance>()
const fileList = ref<UploadFile[]>([])
const uploading = ref(false)
const uploadProgress = ref(0)
const uploadStatus = ref<'' | 'success' | 'exception' | 'active'>('')

const formData = ref({
  categoryId: null as number | null,
})

const rules: Record<string, Rule[]> = {
  categoryId: [{ required: true, message: '请选择文档分类', trigger: 'change' }],
}

const uploadProgressText = computed(() => {
  if (uploadStatus.value === 'success') return '上传成功'
  if (uploadStatus.value === 'exception') return '上传失败'
  return `正在上传... ${uploadProgress.value}%`
})

const beforeUpload: UploadProps['beforeUpload'] = (file) => {
  const allowedTypes = [
    'application/pdf',
    'application/msword',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    'text/plain',
  ]
  const isAllowed = allowedTypes.includes(file.type) || /\.(pdf|doc|docx|txt)$/i.test(file.name)

  if (!isAllowed) {
    showError('只支持 PDF、Word、TXT 格式的文件')
    return Upload.LIST_IGNORE
  }

  const isLt50M = file.size / 1024 / 1024 < 50
  if (!isLt50M) {
    showError('文件大小不能超过 50MB')
    return Upload.LIST_IGNORE
  }

  return false
}

function handleFileRemove(file: UploadFile) {
  const index = fileList.value.indexOf(file)
  if (index > -1) {
    fileList.value.splice(index, 1)
  }
}

async function handleSubmit() {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
  } catch {
    return
  }

  if (fileList.value.length === 0) {
    showWarning('请选择要上传的文件')
    return
  }

  uploading.value = true
  uploadProgress.value = 0
  uploadStatus.value = ''

  const totalFiles = fileList.value.length
  let successCount = 0
  let failCount = 0

  for (let i = 0; i < fileList.value.length; i++) {
    const file = fileList.value[i]
    if (!file.originFileObj) continue

    try {
      const formDataObj = new FormData()
      formDataObj.append('file', file.originFileObj)
      if (formData.value.categoryId) {
        formDataObj.append('categoryId', String(formData.value.categoryId))
      }

      await uploadDocument(formDataObj)
      successCount++
    } catch (error) {
      failCount++
      console.error(`文件 ${file.name} 上传失败:`, error)
    }

    uploadProgress.value = Math.round(((i + 1) / totalFiles) * 100)
  }

  if (failCount === 0) {
    uploadStatus.value = 'success'
    showSuccess(`成功上传 ${successCount} 个文件`)
    emit('success')
    handleClose()
  } else if (successCount === 0) {
    uploadStatus.value = 'exception'
    showError('所有文件上传失败')
  } else {
    uploadStatus.value = 'exception'
    showWarning(`成功 ${successCount} 个，失败 ${failCount} 个`)
    emit('success')
  }

  uploading.value = false
}

function handleClose() {
  formRef.value?.resetFields()
  fileList.value = []
  uploading.value = false
  uploadProgress.value = 0
  uploadStatus.value = ''
  emit('update:open', false)
}
</script>

<style scoped lang="scss">
.upload-progress {
  margin-top: 16px;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 4px;

  .progress-text {
    margin-top: 8px;
    text-align: center;
    font-size: 14px;
    color: #606266;
  }
}
</style>
