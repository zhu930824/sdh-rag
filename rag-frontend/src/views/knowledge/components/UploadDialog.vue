<template>
  <el-dialog
    :model-value="modelValue"
    title="上传文档"
    width="600px"
    :close-on-click-modal="false"
    @update:model-value="handleClose"
  >
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="80px">
      <!-- 分类选择 -->
      <el-form-item label="文档分类" prop="categoryId">
        <el-tree-select
          v-model="formData.categoryId"
          :data="knowledgeStore.categoryTree"
          :props="{ label: 'name', children: 'children', value: 'id' }"
          placeholder="请选择分类"
          check-strictly
          clearable
          style="width: 100%"
        />
      </el-form-item>

      <!-- 文件上传 -->
      <el-form-item label="上传文件" prop="files">
        <el-upload
          ref="uploadRef"
          v-model:file-list="fileList"
          class="upload-area"
          drag
          multiple
          :auto-upload="false"
          :limit="10"
          :on-exceed="handleExceed"
          :before-upload="beforeUpload"
          :on-change="handleFileChange"
          :on-remove="handleFileRemove"
          accept=".pdf,.doc,.docx,.txt"
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">
            将文件拖到此处，或<em>点击上传</em>
          </div>
          <template #tip>
            <div class="el-upload__tip">
              支持 PDF、Word、TXT 格式，单个文件不超过 50MB，最多上传 10 个文件
            </div>
          </template>
        </el-upload>
      </el-form-item>
    </el-form>

    <!-- 上传进度 -->
    <div v-if="uploading" class="upload-progress">
      <el-progress :percentage="uploadProgress" :status="uploadStatus" />
      <div class="progress-text">{{ uploadProgressText }}</div>
    </div>

    <template #footer>
      <span class="dialog-footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" :loading="uploading" :disabled="fileList.length === 0" @click="handleSubmit">
          开始上传
        </el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import type { FormInstance, FormRules, UploadFile, UploadFiles, UploadInstance } from 'element-plus'
import { useKnowledgeStore } from '@/stores/knowledge'
import { uploadDocument } from '@/api/knowledge'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
}>()

const knowledgeStore = useKnowledgeStore()

// 表单引用
const formRef = ref<FormInstance>()
// 上传组件引用
const uploadRef = ref<UploadInstance>()
// 文件列表
const fileList = ref<UploadFile[]>([])
// 上传中状态
const uploading = ref(false)
// 上传进度
const uploadProgress = ref(0)
// 上传状态
const uploadStatus = ref<'' | 'success' | 'warning' | 'exception'>('')

// 表单数据
const formData = ref({
  categoryId: null as number | null,
})

// 表单验证规则
const rules: FormRules = {
  categoryId: [{ required: true, message: '请选择文档分类', trigger: 'change' }],
}

// 上传进度文本
const uploadProgressText = computed(() => {
  if (uploadStatus.value === 'success') return '上传成功'
  if (uploadStatus.value === 'exception') return '上传失败'
  return `正在上传... ${uploadProgress.value}%`
})

// 文件上传前校验
function beforeUpload(file: File): boolean {
  const allowedTypes = ['application/pdf', 'application/msword', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 'text/plain']
  const isAllowed = allowedTypes.includes(file.type) || /\.(pdf|doc|docx|txt)$/i.test(file.name)
  
  if (!isAllowed) {
    ElMessage.error('只支持 PDF、Word、TXT 格式的文件')
    return false
  }

  const isLt50M = file.size / 1024 / 1024 < 50
  if (!isLt50M) {
    ElMessage.error('文件大小不能超过 50MB')
    return false
  }

  return true
}

// 文件变化
function handleFileChange(file: UploadFile, files: UploadFiles) {
  fileList.value = files
}

// 文件移除
function handleFileRemove(file: UploadFile, files: UploadFiles) {
  fileList.value = files
}

// 超出限制
function handleExceed(files: File[]) {
  ElMessage.warning(`最多只能上传 10 个文件，当前选择了 ${files.length} 个文件`)
}

// 提交上传
async function handleSubmit() {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    if (fileList.value.length === 0) {
      ElMessage.warning('请选择要上传的文件')
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
      if (!file.raw) continue

      try {
        const formDataObj = new FormData()
        formDataObj.append('file', file.raw)
        if (formData.value.categoryId) {
          formDataObj.append('categoryId', String(formData.value.categoryId))
        }

        await uploadDocument(formDataObj)
        successCount++
      } catch (error) {
        failCount++
        console.error(`文件 ${file.name} 上传失败:`, error)
      }

      // 更新进度
      uploadProgress.value = Math.round(((i + 1) / totalFiles) * 100)
    }

    // 设置最终状态
    if (failCount === 0) {
      uploadStatus.value = 'success'
      ElMessage.success(`成功上传 ${successCount} 个文件`)
      emit('success')
      handleClose()
    } else if (successCount === 0) {
      uploadStatus.value = 'exception'
      ElMessage.error('所有文件上传失败')
    } else {
      uploadStatus.value = 'warning'
      ElMessage.warning(`成功 ${successCount} 个，失败 ${failCount} 个`)
      emit('success')
    }

    uploading.value = false
  })
}

// 关闭对话框
function handleClose() {
  formRef.value?.resetFields()
  fileList.value = []
  uploading.value = false
  uploadProgress.value = 0
  uploadStatus.value = ''
  emit('update:modelValue', false)
}
</script>

<style scoped lang="scss">
.upload-area {
  width: 100%;

  :deep(.el-upload-dragger) {
    width: 100%;
  }
}

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

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
