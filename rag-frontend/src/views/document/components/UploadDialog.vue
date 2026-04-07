<template>
  <a-modal
    :open="open"
    title="上传文档"
    :width="520"
    :mask-closable="false"
    :confirm-loading="uploading"
    @update:open="handleClose"
    @ok="handleUpload"
    @cancel="handleClose"
  >
    <a-form :label-col="{ span: 4 }" :wrapper-col="{ span: 20 }">
      <a-form-item label="文档分类">
        <a-tree-select
          v-model:value="categoryId"
          :tree-data="categoryTree"
          :field-names="{ label: 'name', value: 'id', children: 'children' }"
          placeholder="请选择分类（可选）"
          allow-clear
          tree-default-expand-all
        />
      </a-form-item>
      <a-form-item label="文档标题">
        <a-input
          v-model:value="title"
          placeholder="请输入文档标题（可选，默认使用文件名）"
          allow-clear
        />
      </a-form-item>
      <a-form-item label="选择文件">
        <a-upload-dragger
          v-model:file-list="fileList"
          :before-upload="beforeUpload"
          :max-count="1"
          accept=".pdf,.doc,.docx,.txt,.md,.xlsx,.xls,.ppt,.pptx"
        >
          <p class="ant-upload-drag-icon">
            <InboxOutlined />
          </p>
          <p class="ant-upload-text">点击或拖拽文件到此区域上传</p>
          <p class="ant-upload-hint">
            支持 PDF、Word、Excel、PPT、TXT、Markdown 等格式，最大 100MB
          </p>
        </a-upload-dragger>
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { InboxOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import type { UploadProps } from 'ant-design-vue'
import { useDocumentStore } from '@/stores/document'
import { uploadDocument } from '@/api/document'
import type { DocumentCategory } from '@/api/document'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  success: []
}>()

const documentStore = useDocumentStore()

const categoryId = ref<number | null>(null)
const title = ref('')
const fileList = ref<any[]>([])
const uploading = ref(false)

const categoryTree = computed(() => documentStore.categoryTree)

// 监听弹窗打开，重置表单
watch(() => props.open, (val) => {
  if (val) {
    categoryId.value = null
    title.value = ''
    fileList.value = []
  }
})

const beforeUpload: UploadProps['beforeUpload'] = (file) => {
  // 检查文件大小
  const maxSize = 100 * 1024 * 1024 // 100MB
  if (file.size > maxSize) {
    message.error('文件大小不能超过 100MB')
    return false
  }

  // 检查文件类型
  const allowedTypes = [
    'application/pdf',
    'application/msword',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    'application/vnd.ms-excel',
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    'application/vnd.ms-powerpoint',
    'application/vnd.openxmlformats-officedocument.presentationml.presentation',
    'text/plain',
    'text/markdown',
  ]
  const allowedExtensions = ['.pdf', '.doc', '.docx', '.xls', '.xlsx', '.ppt', '.pptx', '.txt', '.md']
  const fileExtension = file.name.substring(file.name.lastIndexOf('.')).toLowerCase()

  if (!allowedExtensions.includes(fileExtension)) {
    message.error('不支持的文件格式')
    return false
  }

  return false // 阻止自动上传
}

async function handleUpload() {
  if (fileList.value.length === 0) {
    message.warning('请选择要上传的文件')
    return
  }

  const file = fileList.value[0].originFileObj || fileList.value[0]

  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    if (categoryId.value) {
      formData.append('categoryId', String(categoryId.value))
    }
    if (title.value) {
      formData.append('title', title.value)
    }

    await uploadDocument(formData)
    message.success('上传成功')
    emit('success')
    emit('update:open', false)
  } catch (error: any) {
    message.error(error.response?.data?.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

function handleClose() {
  emit('update:open', false)
}
</script>

<style scoped lang="scss">
.ant-upload-drag-icon {
  color: var(--primary-color);
}
</style>
