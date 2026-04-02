<template>
  <div class="announcement-container">
    <a-card>
      <template #title>
        <div class="card-header">
          <span class="card-title">公告管理</span>
          <a-button type="primary" @click="showCreateModal">
            <template #icon><PlusOutlined /></template>
            发布公告
          </a-button>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div class="search-toolbar">
        <a-form layout="inline" :model="filters" class="search-form">
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
            <a-space>
              <a-button type="primary" @click="handleSearch">
                <template #icon><SearchOutlined /></template>
                搜索
              </a-button>
              <a-button @click="handleReset">
                <template #icon><ReloadOutlined /></template>
                重置
              </a-button>
            </a-space>
          </a-form-item>
        </a-form>
        <div class="toolbar-actions">
          <a-button @click="loadData">
            <template #icon><SyncOutlined /></template>
            刷新
          </a-button>
        </div>
      </div>

      <!-- 表格 -->
      <a-table
        class="announcement-table"
        :columns="columns"
        :data-source="tableData"
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 900 }"
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
              <a-popconfirm title="确定删除？" ok-text="确定" cancel-text="取消" @confirm="deleteAnnouncement(record)">
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>

      <!-- 分页 -->
      <div class="pagination">
        <a-pagination
          v-model:current="pagination.current"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-size-options="['10', '20', '50', '100']"
          show-size-changer
          show-quick-jumper
          :show-total="(total: number) => `共 ${total} 条`"
          @change="handlePageChange"
          @show-size-change="handleSizeChange"
        />
      </div>
    </a-card>

    <a-modal
      v-model:open="modalVisible"
      :title="isEdit ? '编辑公告' : '发布公告'"
      :width="600"
      ok-text="确认"
      cancel-text="取消"
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
import { PlusOutlined, SearchOutlined, ReloadOutlined, SyncOutlined } from '@ant-design/icons-vue'
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

const pagination = reactive({ current: 1, pageSize: 10, total: 0 })
const filters = reactive({ type: undefined as string | undefined, status: undefined as number | undefined })

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

function handleSearch() {
  pagination.current = 1
  loadData()
}

function handleReset() {
  filters.type = undefined
  filters.status = undefined
  handleSearch()
}

function handlePageChange(page: number) {
  pagination.current = page
  loadData()
}

function handleSizeChange(_current: number, size: number) {
  pagination.current = 1
  pagination.pageSize = size
  loadData()
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
.announcement-container {
  height: calc(100vh - 56px - 32px);
  overflow: hidden;

  :deep(.ant-card) {
    height: 100%;
    display: flex;
    flex-direction: column;

    .ant-card-head {
      flex-shrink: 0;
    }

    .ant-card-body {
      flex: 1;
      min-height: 0;
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }
  }

  :deep(.ant-card-head-title) {
    width: 100%;
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .card-title {
      font-size: 16px;
      font-weight: 500;
    }
  }

  .search-toolbar {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    margin-bottom: 16px;
    flex-shrink: 0;
  }

  .search-form {
    flex: 1;
  }

  .toolbar-actions {
    flex-shrink: 0;
    padding-top: 4px;
    display: flex;
    align-items: center;
  }

  .announcement-table {
    flex: 1;
    min-height: 0;
    overflow: hidden;

    :deep(.ant-table-wrapper) {
      height: 100%;
    }

    :deep(.ant-spin-nested-loading) {
      height: 100%;
    }

    :deep(.ant-spin-container) {
      height: 100%;
      display: flex;
      flex-direction: column;
    }

    :deep(.ant-table) {
      flex: 1;
      display: flex;
      flex-direction: column;
    }

    :deep(.ant-table-container) {
      flex: 1;
      display: flex;
      flex-direction: column;
    }

    :deep(.ant-table-body) {
      flex: 1;
      overflow: auto !important;
    }
  }

  .pagination {
    margin-top: 16px;
    display: flex;
    justify-content: flex-end;
    flex-shrink: 0;
  }
}
</style>
