<template>
  <div class="prompt-template-page">
    <a-card title="Prompt模板管理" :bordered="false">
      <template #extra>
        <a-space>
          <a-select v-model:value="filterCategory" placeholder="选择分类" style="width: 150px" allowClear @change="handleSearch">
            <a-select-option v-for="cat in categories" :key="cat" :value="cat">{{ cat }}</a-select-option>
          </a-select>
          <a-input-search v-model:value="keyword" placeholder="搜索模板" style="width: 200px" @search="handleSearch" />
          <a-button type="primary" @click="showCreateModal">新建模板</a-button>
        </a-space>
      </template>

      <a-table :dataSource="templates" :columns="columns" :pagination="pagination" :loading="loading" rowKey="id">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'category'">
            <a-tag :color="getCategoryColor(record.category)">{{ record.category }}</a-tag>
          </template>
          <template v-if="column.key === 'isSystem'">
            <a-tag :color="record.isSystem ? 'blue' : 'green'">{{ record.isSystem ? '系统' : '自定义' }}</a-tag>
          </template>
          <template v-if="column.key === 'status'">
            <a-tag :color="record.status === 1 ? 'success' : 'error'">{{ record.status === 1 ? '启用' : '禁用' }}</a-tag>
          </template>
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="showDetail(record)">查看</a-button>
              <a-button type="link" size="small" @click="showEditModal(record)" :disabled="record.isSystem">编辑</a-button>
              <a-button type="link" size="small" @click="showPreviewModal(record)">预览</a-button>
              <a-popconfirm title="确定删除?" @confirm="handleDelete(record.id)" :disabled="record.isSystem">
                <a-button type="link" size="small" danger :disabled="record.isSystem">删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="modalVisible" :title="isEdit ? '编辑模板' : '新建模板'" width="800px" ok-text="确认" cancel-text="取消" @ok="handleSubmit">
      <a-form :model="formData" :labelCol="{ span: 4 }" :wrapperCol="{ span: 18 }">
        <a-form-item label="模板名称" required>
          <a-input v-model:value="formData.name" placeholder="请输入模板名称" />
        </a-form-item>
        <a-form-item label="模板编码" required>
          <a-input v-model:value="formData.code" placeholder="唯一标识，如：my_template" :disabled="isEdit" />
        </a-form-item>
        <a-form-item label="分类" required>
          <a-select v-model:value="formData.category" placeholder="选择分类">
            <a-select-option v-for="cat in categories" :key="cat" :value="cat">{{ cat }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="模板内容" required>
          <a-textarea v-model:value="formData.content" :rows="8" placeholder="输入模板内容，使用 {变量名} 表示变量" />
          <div class="variable-hint">
            可用变量：{context} {question} {history} {content} {target_language}
          </div>
        </a-form-item>
        <a-form-item label="变量说明">
          <a-textarea v-model:value="formData.variables" :rows="3" placeholder="JSON格式，如：[{\"name\":\"question\",\"desc\":\"用户问题\"}]" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="formData.description" :rows="2" placeholder="模板描述" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="previewVisible" title="模板预览" width="800px" :footer="null">
      <div v-if="currentTemplate">
        <a-descriptions :column="2" bordered>
          <a-descriptions-item label="模板名称">{{ currentTemplate.name }}</a-descriptions-item>
          <a-descriptions-item label="编码">{{ currentTemplate.code }}</a-descriptions-item>
          <a-descriptions-item label="分类">{{ currentTemplate.category }}</a-descriptions-item>
          <a-descriptions-item label="使用次数">{{ currentTemplate.useCount }}</a-descriptions-item>
        </a-descriptions>
        <a-divider>模板内容</a-divider>
        <pre class="template-content">{{ currentTemplate.content }}</pre>
        <a-divider>渲染测试</a-divider>
        <a-form layout="inline" style="margin-bottom: 16px">
          <a-form-item label="问题">
            <a-input v-model:value="previewVars.question" style="width: 300px" />
          </a-form-item>
          <a-form-item label="上下文">
            <a-input v-model:value="previewVars.context" style="width: 300px" />
          </a-form-item>
          <a-form-item>
            <a-button type="primary" @click="handleRender">渲染预览</a-button>
          </a-form-item>
        </a-form>
        <a-card v-if="renderedContent" title="渲染结果" size="small">
          <pre>{{ renderedContent }}</pre>
        </a-card>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import {
  getPromptTemplates, getPromptCategories, createPromptTemplate,
  updatePromptTemplate, deletePromptTemplate, renderPromptTemplate, type PromptTemplate
} from '@/api/promptTemplate'

const loading = ref(false)
const templates = ref<PromptTemplate[]>([])
const categories = ref<string[]>([])
const filterCategory = ref<string>()
const keyword = ref('')
const modalVisible = ref(false)
const previewVisible = ref(false)
const isEdit = ref(false)
const currentTemplate = ref<PromptTemplate | null>(null)
const renderedContent = ref('')

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  onChange: (page: number) => {
    pagination.current = page
    loadTemplates()
  }
})

const formData = reactive({
  id: 0,
  name: '',
  code: '',
  category: 'general',
  content: '',
  variables: '',
  description: ''
})

const previewVars = reactive({
  question: '什么是机器学习？',
  context: '机器学习是人工智能的一个分支，它使计算机能够从数据中学习。'
})

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '编码', dataIndex: 'code', key: 'code' },
  { title: '分类', dataIndex: 'category', key: 'category', width: 100 },
  { title: '类型', dataIndex: 'isSystem', key: 'isSystem', width: 80 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 80 },
  { title: '使用次数', dataIndex: 'useCount', key: 'useCount', width: 100 },
  { title: '操作', key: 'action', width: 250 }
]

const loadTemplates = async () => {
  loading.value = true
  try {
    const res = await getPromptTemplates(pagination.current, pagination.pageSize, filterCategory.value, keyword.value)
    if (res.data) {
      templates.value = res.data.records
      pagination.total = res.data.total
    }
  } finally {
    loading.value = false
  }
}

const loadCategories = async () => {
  const res = await getPromptCategories()
  if (res.data) categories.value = res.data
}

const handleSearch = () => {
  pagination.current = 1
  loadTemplates()
}

const getCategoryColor = (category: string) => {
  const colors: Record<string, string> = {
    'general': 'default',
    'qa': 'blue',
    'chat': 'green',
    'summary': 'orange',
    'translate': 'purple',
    'custom': 'cyan'
  }
  return colors[category] || 'default'
}

const showCreateModal = () => {
  isEdit.value = false
  Object.assign(formData, {
    id: 0, name: '', code: '', category: 'general',
    content: '', variables: '', description: ''
  })
  modalVisible.value = true
}

const showEditModal = (record: PromptTemplate) => {
  isEdit.value = true
  Object.assign(formData, {
    id: record.id,
    name: record.name,
    code: record.code,
    category: record.category,
    content: record.content,
    variables: record.variables || '',
    description: record.description || ''
  })
  modalVisible.value = true
}

const showDetail = (record: PromptTemplate) => {
  currentTemplate.value = record
  previewVisible.value = true
}

const showPreviewModal = (record: PromptTemplate) => {
  currentTemplate.value = record
  renderedContent.value = ''
  previewVisible.value = true
}

const handleSubmit = async () => {
  try {
    if (isEdit.value) {
      await updatePromptTemplate(formData.id, formData)
      message.success('更新成功')
    } else {
      await createPromptTemplate(formData)
      message.success('创建成功')
    }
    modalVisible.value = false
    loadTemplates()
  } catch (error) {
    message.error('操作失败')
  }
}

const handleDelete = async (id: number) => {
  try {
    await deletePromptTemplate(id)
    message.success('删除成功')
    loadTemplates()
  } catch (error) {
    message.error('删除失败')
  }
}

const handleRender = async () => {
  if (!currentTemplate.value) return
  try {
    const res = await renderPromptTemplate(currentTemplate.value.code, previewVars)
    if (res.data) {
      renderedContent.value = res.data.content
    }
  } catch (error) {
    message.error('渲染失败')
  }
}

onMounted(() => {
  loadTemplates()
  loadCategories()
})
</script>

<style scoped>
.prompt-template-page {
  padding: 24px;
}
.template-content {
  background: #f5f5f5;
  padding: 16px;
  border-radius: 4px;
  white-space: pre-wrap;
}
.variable-hint {
  margin-top: 8px;
  color: #666;
  font-size: 12px;
}
</style>
