<template>
  <div class="tag-page">
    <div class="page-header">
      <h2>标签管理</h2>
      <p class="description">管理文档标签，支持手动和自动标注</p>
    </div>

    <div class="filter-section">
      <a-form layout="inline">
        <a-form-item label="关键词">
          <a-input v-model:value="filters.keyword" placeholder="搜索标签" allow-clear style="width: 200px" />
        </a-form-item>
        <a-form-item label="分类">
          <a-select v-model:value="filters.category" placeholder="全部" allow-clear style="width: 150px">
            <a-select-option value="文档类型">文档类型</a-select-option>
            <a-select-option value="业务领域">业务领域</a-select-option>
            <a-select-option value="技术栈">技术栈</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item>
          <a-button type="primary" @click="loadTags">查询</a-button>
        </a-form-item>
        <a-form-item>
          <a-button type="primary" @click="showCreateModal">
            <template #icon><PlusOutlined /></template>
            新建标签
          </a-button>
        </a-form-item>
      </a-form>
    </div>

    <div class="tag-cloud">
      <a-card title="标签云" size="small" :bordered="false">
        <a-tag
          v-for="tag in tags"
          :key="tag.id"
          :color="tag.color || 'blue'"
          style="margin: 4px; cursor: pointer"
          @click="handleTagClick(tag)"
        >
          {{ tag.name }}
        </a-tag>
      </a-card>
    </div>

    <a-table
      :columns="columns"
      :data-source="tableData"
      :loading="loading"
      :pagination="pagination"
      row-key="id"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'color'">
          <div class="color-preview" :style="{ backgroundColor: record.color }"></div>
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <a-tag :color="record.status === 1 ? 'green' : 'red'">
            {{ record.status === 1 ? '启用' : '禁用' }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <a-space>
            <a-button type="link" size="small" @click="handleEdit(record)">编辑</a-button>
            <a-popconfirm title="确定删除该标签？" @confirm="handleDelete(record)">
              <a-button type="link" size="small" danger>删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal
      v-model:open="modalVisible"
      :title="isEdit ? '编辑标签' : '新建标签'"
      @ok="handleSubmit"
    >
      <a-form :model="formData" :label-col="{ span: 4 }" :wrapper-col="{ span: 18 }">
        <a-form-item label="标签名称" required>
          <a-input v-model:value="formData.name" placeholder="请输入标签名称" />
        </a-form-item>
        <a-form-item label="标签颜色">
          <a-input v-model:value="formData.color" type="color" style="width: 100px; height: 32px" />
        </a-form-item>
        <a-form-item label="分类">
          <a-select v-model:value="formData.category" placeholder="请选择分类">
            <a-select-option value="文档类型">文档类型</a-select-option>
            <a-select-option value="业务领域">业务领域</a-select-option>
            <a-select-option value="技术栈">技术栈</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="排序">
          <a-input-number v-model:value="formData.sort" :min="0" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { getTagList, createTag, updateTag, deleteTag, type Tag } from '@/api/tag'

const loading = ref(false)
const tableData = ref<Tag[]>([])
const tags = ref<Tag[]>([])
const modalVisible = ref(false)
const isEdit = ref(false)

const pagination = reactive({ current: 1, pageSize: 10, total: 0, showSizeChanger: true })
const filters = reactive({ keyword: '', category: '' })

const formData = reactive({
  id: 0,
  name: '',
  color: '#1890ff',
  category: '',
  sort: 0,
})

const columns = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '标签名称', dataIndex: 'name', width: 150 },
  { title: '颜色', dataIndex: 'color', width: 80 },
  { title: '分类', dataIndex: 'category', width: 100 },
  { title: '排序', dataIndex: 'sort', width: 80 },
  { title: '状态', dataIndex: 'status', width: 80 },
  { title: '操作', dataIndex: 'action', width: 150 },
]

async function loadTags() {
  loading.value = true
  try {
    const res = await getTagList({
      page: pagination.current,
      pageSize: pagination.pageSize,
      keyword: filters.keyword,
      category: filters.category,
    })
    if (res.code === 200) {
      tableData.value = res.data?.records || []
      pagination.total = res.data?.total || 0
    }
  } finally {
    loading.value = false
  }
}

async function loadAllTags() {
  try {
    const res = await getAllTags()
    if (res.code === 200) {
      tags.value = res.data || []
    }
  } catch (error) {
    console.error('加载标签失败')
  }
}

function showCreateModal() {
  isEdit.value = false
  Object.assign(formData, { id: 0, name: '', color: '#1890ff', category: '', sort: 0 })
  modalVisible.value = true
}

function handleEdit(record: Tag) {
  isEdit.value = true
  Object.assign(formData, record)
  modalVisible.value = true
}

async function handleSubmit() {
  if (!formData.name) {
    message.warning('请输入标签名称')
    return
  }
  try {
    if (isEdit.value) {
      await updateTag(formData.id, formData)
    } else {
      await createTag(formData)
    }
    message.success('操作成功')
    modalVisible.value = false
    loadTags()
    loadAllTags()
  } catch (error) {
    message.error('操作失败')
  }
}

async function handleDelete(record: Tag) {
  try {
    await deleteTag(record.id)
    message.success('删除成功')
    loadTags()
    loadAllTags()
  } catch (error) {
    message.error('删除失败')
  }
}

function handleTagClick(tag: Tag) {
  filters.keyword = tag.name
  loadTags()
}

onMounted(() => {
  loadTags()
  loadAllTags()
})
</script>

<style scoped lang="scss">
.tag-page {
  padding: 24px;

  .page-header {
    margin-bottom: 24px;
    h2 { margin: 0 0 8px; font-size: 20px; font-weight: 600; }
    .description { color: var(--text-secondary); font-size: 14px; }
  }

  .filter-section {
    margin-bottom: 16px;
    padding: 16px;
    background-color: var(--bg-color);
    border-radius: var(--border-radius-base);
  }

  .tag-cloud {
    margin-bottom: 16px;
  }

  .color-preview {
    width: 24px;
    height: 24px;
    border-radius: 4px;
    border: 1px solid #d9d9d9;
  }
}
</style>