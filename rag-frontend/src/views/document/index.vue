<template>
  <div class="document-container">
    <a-card>
      <template #title>
        <div class="card-header">
          <span class="card-title">文档管理</span>
          <a-button type="primary">
            <template #icon><UploadOutlined /></template>
            上传文档
          </a-button>
        </div>
      </template>

      <!-- 搜索栏 -->
      <a-form layout="inline" :model="searchForm" class="search-form">
        <a-form-item label="文档名称">
          <a-input v-model:value="searchForm.keyword" placeholder="请输入文档名称" allow-clear />
        </a-form-item>
        <a-form-item label="知识库">
          <a-select v-model:value="searchForm.knowledgeId" placeholder="请选择知识库" allow-clear style="width: 150px">
            <a-select-option :value="1">产品文档库</a-select-option>
            <a-select-option :value="2">技术文档库</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item>
          <a-button type="primary" @click="handleSearch">
            <template #icon><SearchOutlined /></template>
            搜索
          </a-button>
          <a-button style="margin-left: 8px" @click="handleReset">
            <template #icon><ReloadOutlined /></template>
            重置
          </a-button>
        </a-form-item>
      </a-form>

      <!-- 表格 -->
      <a-table
        :columns="columns"
        :data-source="tableData"
        :loading="loading"
        :pagination="pagination"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="record.status === '已处理' ? 'success' : 'warning'">
              {{ record.status }}
            </a-tag>
          </template>
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="handleView(record)">查看</a-button>
              <a-button type="link" size="small" @click="handleDownload(record)">下载</a-button>
              <a-popconfirm title="确定要删除该文档吗？" @confirm="handleDelete(record)">
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  UploadOutlined,
  SearchOutlined,
  ReloadOutlined,
} from '@ant-design/icons-vue'

interface DocumentItem {
  id: number
  name: string
  knowledgeName: string
  size: string
  status: string
  createTime: string
}

const loading = ref(false)

// 搜索表单
const searchForm = reactive({
  keyword: '',
  knowledgeId: undefined as number | undefined,
})

// 表格列定义
const columns = [
  {
    title: 'ID',
    dataIndex: 'id',
    key: 'id',
    width: 80,
  },
  {
    title: '文档名称',
    dataIndex: 'name',
    key: 'name',
    ellipsis: true,
  },
  {
    title: '所属知识库',
    dataIndex: 'knowledgeName',
    key: 'knowledgeName',
    width: 150,
  },
  {
    title: '文件大小',
    dataIndex: 'size',
    key: 'size',
    width: 120,
  },
  {
    title: '状态',
    key: 'status',
    width: 100,
  },
  {
    title: '上传时间',
    dataIndex: 'createTime',
    key: 'createTime',
    width: 180,
  },
  {
    title: '操作',
    key: 'action',
    width: 200,
    fixed: 'right' as const,
  },
]

// 分页
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 200,
  showSizeChanger: true,
  showQuickJumper: true,
  showTotal: (total: number) => `共 ${total} 条`,
})

// 表格数据
const tableData = ref<DocumentItem[]>([
  { id: 1, name: '产品需求文档v1.0.pdf', knowledgeName: '产品文档库', size: '2.5MB', status: '已处理', createTime: '2024-01-15 10:30:00' },
  { id: 2, name: '系统架构设计.docx', knowledgeName: '技术文档库', size: '1.8MB', status: '处理中', createTime: '2024-01-16 14:20:00' },
  { id: 3, name: '用户操作手册.pdf', knowledgeName: '用户手册库', size: '3.2MB', status: '已处理', createTime: '2024-01-17 09:15:00' },
])

// 搜索
function handleSearch() {
  loading.value = true
  setTimeout(() => {
    loading.value = false
    message.success('搜索完成')
  }, 500)
}

// 重置
function handleReset() {
  searchForm.keyword = ''
  searchForm.knowledgeId = undefined
  pagination.current = 1
}

// 表格变化
function handleTableChange(pag: any) {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
}

// 查看
function handleView(record: DocumentItem) {
  message.info(`查看文档：${record.name}`)
}

// 下载
function handleDownload(record: DocumentItem) {
  message.success(`开始下载：${record.name}`)
}

// 删除
function handleDelete(record: DocumentItem) {
  message.success(`已删除：${record.name}`)
}
</script>

<style scoped lang="scss">
.document-container {
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .card-title {
      font-size: 16px;
      font-weight: 500;
    }
  }

  .search-form {
    margin-bottom: 20px;
  }
}
</style>
