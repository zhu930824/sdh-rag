<template>
  <div class="announcement-page">
    <div class="page-header">
      <h2>公告管理</h2>
      <a-button type="primary" @click="showCreateModal">
        <template #icon><PlusOutlined /></template>
        发布公告
      </a-button>
    </div>

    <div class="filter-section">
      <a-form layout="inline">
        <a-form-item label="类型">
          <a-select v-model:value="filters.type" placeholder="全部" allow-clear style="width: 120px">
            <a-select-option value="notice">通知</a-select-option>
            <a-select-option value="update">更新</a-select-option>
            <a-select-option value="warning">警告</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="filters.status" placeholder="全部" allow-clear style="width: 120px">
            <a-select-option :value="0">草稿</a-select-option>
            <a-select-option :value="1">已发布</a-select-option>
            <a-select-option :value="2">已下线</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item>
          <a-button type="primary" @click="loadData">查询</a-button>
        </a-form-item>
      </a-form>
    </div>

    <a-table
      :columns="columns"
      :data-source="tableData"
      :loading="loading"
      :pagination="pagination"
      row-key="id"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'title'">
          <span>
            <a-tag v-if="record.isTop" color="red" size="small">置顶</a-tag>
            {{ record.title }}
          </span>
        </template>
        <template v-else-if="column.dataIndex === 'type'">
          <a-tag :color="getTypeColor(record.type)">{{ getTypeText(record.type) }}</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <a-tag :color="getStatusColor(record.status)">{{ getStatusText(record.status) }}</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <a-space>
            <a-button type="link" size="small" @click="viewDetail(record)">详情</a-button>
            <a-button v-if="record.status === 0" type="link" size="small" @click="publishAnnouncement(record)">发布</a-button>
            <a-button type="link" size="small" @click="editAnnouncement(record)">编辑</a-button>
            <a-popconfirm title="确定删除？" @confirm="deleteAnnouncement(record)">
              <a-button type="link" size="small" danger>删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal
      v-model:open="modalVisible"
      :title="isEdit ? '编辑公告' : '发布公告'"
      :width="600"
      @ok="handleSubmit"
    >
      <a-form :model="formData" :label-col="{ span: 4 }" :wrapper-col="{ span: 18 }">
        <a-form-item label="标题" required>
          <a-input v-model:value="formData.title" placeholder="请输入公告标题" />
        </a-form-item>
        <a-form-item label="内容" required>
          <a-textarea v-model:value="formData.content" :rows="4" placeholder="请输入公告内容" />
        </a-form-item>
        <a-form-item label="类型">
          <a-select v-model:value="formData.type">
            <a-select-option value="notice">通知</a-select-option>
            <a-select-option value="update">更新</a-select-option>
            <a-select-option value="warning">警告</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="优先级">
          <a-input-number v-model:value="formData.priority" :min="0" :max="100" />
        </a-form-item>
        <a-form-item label="是否置顶">
          <a-switch v-model:checked="formData.isTop" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import {
  getAnnouncementList,
  createAnnouncement,
  updateAnnouncement,
  publishAnnouncement as publishApi,
  deleteAnnouncement as deleteApi,
  type Announcement,
} from '@/api/announcement'

const loading = ref(false)
const tableData = ref<Announcement[]>([])
const modalVisible = ref(false)
const isEdit = ref(false)

const pagination = reactive({ current: 1, pageSize: 10, total: 0, showSizeChanger: true })
const filters = reactive({ type: undefined, status: undefined })

const formData = reactive({
  id: 0,
  title: '',
  content: '',
  type: 'notice',
  priority: 0,
  isTop: false,
})

const columns = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '标题', dataIndex: 'title', ellipsis: true },
  { title: '类型', dataIndex: 'type', width: 80 },
  { title: '状态', dataIndex: 'status', width: 80 },
  { title: '优先级', dataIndex: 'priority', width: 80 },
  { title: '发布时间', dataIndex: 'publishTime', width: 160 },
  { title: '操作', dataIndex: 'action', width: 200, fixed: 'right' },
]

function getTypeColor(type: string): string {
  const map: Record<string, string> = { notice: 'blue', update: 'green', warning: 'orange' }
  return map[type] || 'default'
}

function getTypeText(type: string): string {
  const map: Record<string, string> = { notice: '通知', update: '更新', warning: '警告' }
  return map[type] || type
}

function getStatusColor(status: number): string {
  const map: Record<number, string> = { 0: 'default', 1: 'green', 2: 'red' }
  return map[status] || 'default'
}

function getStatusText(status: number): string {
  const map: Record<number, string> = { 0: '草稿', 1: '已发布', 2: '已下线' }
  return map[status] || String(status)
}

async function loadData() {
  loading.value = true
  try {
    const res = await getAnnouncementList({
      page: pagination.current,
      pageSize: pagination.pageSize,
      type: filters.type,
      status: filters.status,
    })
    if (res.code === 200) {
      tableData.value = res.data?.records || []
      pagination.total = res.data?.total || 0
    }
  } catch (error) {
    message.error('加载失败')
  } finally {
    loading.value = false
  }
}

function showCreateModal() {
  isEdit.value = false
  Object.assign(formData, { id: 0, title: '', content: '', type: 'notice', priority: 0, isTop: false })
  modalVisible.value = true
}

function viewDetail(record: Announcement) {
  // TODO: 显示详情
}

function editAnnouncement(record: Announcement) {
  isEdit.value = true
  Object.assign(formData, { ...record, isTop: !!record.isTop })
  modalVisible.value = true
}

async function handleSubmit() {
  if (!formData.title || !formData.content) {
    message.warning('请填写必要信息')
    return
  }
  try {
    const data = { ...formData, isTop: formData.isTop ? 1 : 0 }
    if (isEdit.value) {
      await updateAnnouncement(formData.id, data)
    } else {
      await createAnnouncement(data)
    }
    message.success('操作成功')
    modalVisible.value = false
    loadData()
  } catch (error) {
    message.error('操作失败')
  }
}

async function publishAnnouncement(record: Announcement) {
  try {
    await publishApi(record.id)
    message.success('发布成功')
    loadData()
  } catch (error) {
    message.error('发布失败')
  }
}

async function deleteAnnouncement(record: Announcement) {
  try {
    await deleteApi(record.id)
    message.success('删除成功')
    loadData()
  } catch (error) {
    message.error('删除失败')
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.announcement-page {
  padding: 24px;

  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 24px;
    h2 { margin: 0; font-size: 20px; font-weight: 600; }
  }

  .filter-section {
    margin-bottom: 16px;
    padding: 16px;
    background-color: var(--bg-color);
    border-radius: var(--border-radius-base);
  }
}
</style>