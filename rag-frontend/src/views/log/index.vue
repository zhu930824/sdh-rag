<template>
  <div class="log-container">
    <a-card>
      <template #title>
        <div class="card-header">
          <span class="card-title">日志管理</span>
          <a-space>
            <a-popconfirm
              title="确定要清空所有日志吗？此操作不可恢复！"
              ok-text="确定"
              cancel-text="取消"
              @confirm="handleClearLogs"
            >
              <a-button danger>
                <template #icon><DeleteOutlined /></template>
                清空日志
              </a-button>
            </a-popconfirm>
          </a-space>
        </div>
      </template>

      <!-- 搜索筛选区域 -->
      <div class="search-toolbar">
        <a-form layout="inline" :model="filterForm" class="search-form">
          <a-form-item label="时间范围">
            <a-range-picker
              v-model:value="filterForm.dateRange"
              :placeholder="['开始时间', '结束时间']"
              style="width: 280px"
              allow-clear
            />
          </a-form-item>
          <a-form-item label="操作类型">
            <a-select
              v-model:value="filterForm.type"
              placeholder="请选择操作类型"
              allow-clear
              style="width: 140px"
            >
              <a-select-option value="login">登录</a-select-option>
              <a-select-option value="logout">登出</a-select-option>
              <a-select-option value="create">新增</a-select-option>
              <a-select-option value="update">修改</a-select-option>
              <a-select-option value="delete">删除</a-select-option>
              <a-select-option value="query">查询</a-select-option>
              <a-select-option value="export">导出</a-select-option>
              <a-select-option value="import">导入</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="用户">
            <a-input
              v-model:value="filterForm.user"
              placeholder="请输入用户名"
              allow-clear
              style="width: 160px"
              @press-enter="handleSearch"
            />
          </a-form-item>
          <a-form-item label="状态">
            <a-select
              v-model:value="filterForm.status"
              placeholder="请选择状态"
              allow-clear
              style="width: 100px"
            >
              <a-select-option :value="1">成功</a-select-option>
              <a-select-option :value="0">失败</a-select-option>
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
          <a-space>
            <a-button v-if="selectedRowKeys.length > 0" danger @click="handleBatchDelete">
              <template #icon><DeleteOutlined /></template>
              批量删除 ({{ selectedRowKeys.length }})
            </a-button>
            <TableToolbar
              :columns="tableColumns"
              :density="tableDensity"
              @refresh="loadData"
              @column-change="handleColumnChange"
              @density-change="handleDensityChange"
            />
          </a-space>
        </div>
      </div>

      <!-- 表格 -->
      <a-table
        :loading="loading"
        :data-source="tableData"
        :columns="tableColumnsConfig"
        :size="tableDensity"
        :pagination="false"
        :scroll="{ x: 1300 }"
        :row-key="(record: LogItem) => record.id"
        :row-selection="rowSelection"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'type'">
            <a-tag :color="getTypeColor(record.type)">
              {{ getTypeText(record.type) }}
            </a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'status'">
            <a-tag :color="record.status === 1 ? 'success' : 'error'">
              {{ record.status === 1 ? '成功' : '失败' }}
            </a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'duration'">
            <span>{{ record.duration }}ms</span>
          </template>
          <template v-else-if="column.dataIndex === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="handleViewDetail(record)">
                详情
              </a-button>
              <a-popconfirm
                title="确定要删除此日志吗？"
                ok-text="确定"
                cancel-text="取消"
                @confirm="handleDelete(record)"
              >
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>

      <!-- 分页 -->
      <div class="pagination">
        <a-pagination
          v-model:current="pagination.page"
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

    <!-- 详情抽屉 -->
    <a-drawer
      v-model:open="detailVisible"
      title="日志详情"
      :width="640"
      :destroy-on-close="true"
    >
      <div v-if="currentLog" class="log-detail">
        <a-descriptions :column="1" bordered>
          <a-descriptions-item label="日志ID">
            {{ currentLog.id }}
          </a-descriptions-item>
          <a-descriptions-item label="操作时间">
            {{ currentLog.createTime }}
          </a-descriptions-item>
          <a-descriptions-item label="操作用户">
            {{ currentLog.username }}
          </a-descriptions-item>
          <a-descriptions-item label="操作类型">
            <a-tag :color="getTypeColor(currentLog.type)">
              {{ getTypeText(currentLog.type) }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="操作内容">
            {{ currentLog.content }}
          </a-descriptions-item>
          <a-descriptions-item label="请求地址">
            {{ currentLog.requestUrl }}
          </a-descriptions-item>
          <a-descriptions-item label="请求方式">
            {{ currentLog.requestMethod }}
          </a-descriptions-item>
          <a-descriptions-item label="IP地址">
            {{ currentLog.ip }}
          </a-descriptions-item>
          <a-descriptions-item label="操作状态">
            <a-tag :color="currentLog.status === 1 ? 'success' : 'error'">
              {{ currentLog.status === 1 ? '成功' : '失败' }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="耗时">
            {{ currentLog.duration }}ms
          </a-descriptions-item>
          <a-descriptions-item v-if="currentLog.browser" label="浏览器">
            {{ currentLog.browser }}
          </a-descriptions-item>
          <a-descriptions-item v-if="currentLog.os" label="操作系统">
            {{ currentLog.os }}
          </a-descriptions-item>
        </a-descriptions>

        <!-- 请求参数 -->
        <div v-if="currentLog.requestParams" class="detail-section">
          <h4 class="section-title">请求参数</h4>
          <div class="code-block">
            <pre>{{ formatJson(currentLog.requestParams) }}</pre>
          </div>
        </div>

        <!-- 响应结果 -->
        <div v-if="currentLog.responseData" class="detail-section">
          <h4 class="section-title">响应结果</h4>
          <div class="code-block">
            <pre>{{ formatJson(currentLog.responseData) }}</pre>
          </div>
        </div>

        <!-- 错误信息 -->
        <div v-if="currentLog.errorMsg" class="detail-section">
          <h4 class="section-title">错误信息</h4>
          <a-alert type="error" :message="currentLog.errorMsg" show-icon />
        </div>
      </div>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { SearchOutlined, ReloadOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import type { TableColumnType, TableProps } from 'ant-design-vue'
import type { Dayjs } from 'dayjs'
import TableToolbar from '@/components/TableToolbar.vue'
import { useLoading } from '@/composables'
import type { ColumnConfig, TableDensity } from '@/components/TableToolbar.vue'
import { getLogList, deleteLog, batchDeleteLogs, clearLogs } from '@/api/log'
import type { OperationLog } from '@/api/log'

interface LogItem extends OperationLog {
  user?: string
}

const { loading, withLoading } = useLoading()

const filterForm = reactive({
  dateRange: null as [Dayjs, Dayjs] | null,
  type: undefined as string | undefined,
  user: '',
  status: undefined as number | undefined,
})

const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0,
})

const tableData = ref<LogItem[]>([])
const selectedRowKeys = ref<number[]>([])

const tableColumns = ref<ColumnConfig[]>([
  { prop: 'id', label: 'ID', visible: true },
  { prop: 'createTime', label: '操作时间', visible: true },
  { prop: 'username', label: '操作用户', visible: true },
  { prop: 'type', label: '操作类型', visible: true },
  { prop: 'content', label: '操作内容', visible: true },
  { prop: 'ip', label: 'IP地址', visible: true },
  { prop: 'status', label: '状态', visible: true },
  { prop: 'duration', label: '耗时', visible: false },
])

const tableDensity = ref<TableDensity>('default')

const tableColumnsConfig = computed<TableColumnType[]>(() => {
  const columns: TableColumnType[] = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    { title: '操作时间', dataIndex: 'createTime', key: 'createTime', width: 180 },
    { title: '操作用户', dataIndex: 'username', key: 'username', width: 120 },
    { title: '操作类型', dataIndex: 'type', key: 'type', width: 100 },
    { title: '操作内容', dataIndex: 'content', key: 'content', width: 280, ellipsis: true },
    { title: 'IP地址', dataIndex: 'ip', key: 'ip', width: 140 },
    { title: '状态', dataIndex: 'status', key: 'status', width: 80 },
    { title: '耗时', dataIndex: 'duration', key: 'duration', width: 80 },
    { title: '操作', dataIndex: 'action', key: 'action', width: 120, fixed: 'right' },
  ]
  return columns.filter(col => {
    const prop = col.dataIndex as string
    const config = tableColumns.value.find(c => c.prop === prop)
    return config ? config.visible !== false : true
  })
})

const rowSelection: TableProps['rowSelection'] = computed(() => ({
  selectedRowKeys: selectedRowKeys.value,
  onChange: (keys: number[]) => {
    selectedRowKeys.value = keys
  },
}))

const detailVisible = ref(false)
const currentLog = ref<LogItem | null>(null)

async function loadData() {
  await withLoading(async () => {
    try {
      const params: Record<string, unknown> = {
        page: pagination.page,
        pageSize: pagination.pageSize,
        type: filterForm.type,
        username: filterForm.user || undefined,
        status: filterForm.status,
      }

      if (filterForm.dateRange && filterForm.dateRange.length === 2) {
        params.startTime = filterForm.dateRange[0].format('YYYY-MM-DD HH:mm:ss')
        params.endTime = filterForm.dateRange[1].format('YYYY-MM-DD HH:mm:ss')
      }

      const { data } = await getLogList(params as any)
      tableData.value = data.records || data.list || []
      pagination.total = data.total
    } catch (error) {
      tableData.value = []
      pagination.total = 0
    }
  })
}

function handleSearch() {
  pagination.page = 1
  loadData()
}

function handleReset() {
  filterForm.dateRange = null
  filterForm.type = undefined
  filterForm.user = ''
  filterForm.status = undefined
  handleSearch()
}

function handlePageChange(page: number) {
  pagination.page = page
  loadData()
}

function handleSizeChange(_current: number, size: number) {
  pagination.page = 1
  pagination.pageSize = size
  loadData()
}

function handleColumnChange(prop: string, visible: boolean) {
  const col = tableColumns.value.find(c => c.prop === prop)
  if (col) {
    col.visible = visible
  }
}

function handleDensityChange(density: TableDensity) {
  tableDensity.value = density
}

function handleViewDetail(record: LogItem) {
  currentLog.value = record
  detailVisible.value = true
}

async function handleDelete(record: LogItem) {
  try {
    await deleteLog(record.id)
    message.success('删除成功')
    loadData()
  } catch (error) {
    message.error('删除失败')
  }
}

async function handleBatchDelete() {
  try {
    const res = await batchDeleteLogs(selectedRowKeys.value)
    message.success(`成功删除 ${res.data.success} 条日志`)
    if (res.data.fail > 0) {
      message.warning(`${res.data.fail} 条日志删除失败`)
    }
    selectedRowKeys.value = []
    loadData()
  } catch (error) {
    message.error('批量删除失败')
  }
}

async function handleClearLogs() {
  try {
    await clearLogs()
    message.success('清空日志成功')
    loadData()
  } catch (error) {
    message.error('清空日志失败')
  }
}

function getTypeColor(type: string): string {
  const colorMap: Record<string, string> = {
    login: 'green',
    logout: 'orange',
    create: 'blue',
    update: 'cyan',
    delete: 'red',
    query: 'purple',
    export: 'geekblue',
    import: 'magenta',
  }
  return colorMap[type] || 'default'
}

function getTypeText(type: string): string {
  const textMap: Record<string, string> = {
    login: '登录',
    logout: '登出',
    create: '新增',
    update: '修改',
    delete: '删除',
    query: '查询',
    export: '导出',
    import: '导入',
  }
  return textMap[type] || type
}

function formatJson(data: string | Record<string, unknown>): string {
  if (typeof data === 'string') {
    try {
      return JSON.stringify(JSON.parse(data), null, 2)
    } catch {
      return data
    }
  }
  return JSON.stringify(data, null, 2)
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.log-container {
  height: calc(100vh - 64px - 48px);
  overflow-y: auto;

  :deep(.ant-card) {
    margin-bottom: 20px;
  }

  :deep(.ant-card-head-title) {
    width: 100%;
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .card-title {
      font-family: var(--font-display);
      font-size: 18px;
      font-weight: var(--font-weight-semibold);
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

    :deep(.table-toolbar) {
      display: inline-flex;
    }
  }

  :deep(.ant-table-wrapper) {
    flex: 1;
    min-height: 0;
    overflow: hidden;

    .ant-spin-nested-loading {
      height: 100%;
    }

    .ant-spin-container {
      height: 100%;
      display: flex;
      flex-direction: column;
    }

    .ant-table {
      flex: 1;
      display: flex;
      flex-direction: column;
    }

    .ant-table-container {
      flex: 1;
      display: flex;
      flex-direction: column;
    }

    .ant-table-body {
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

.log-detail {
  .detail-section {
    margin-top: 24px;

    .section-title {
      font-size: 14px;
      font-weight: 500;
      margin-bottom: 12px;
      color: var(--text-primary);
    }

    .code-block {
      background: #f5f5f5;
      border: 1px solid #e8e8e8;
      border-radius: 4px;
      padding: 12px;
      overflow: auto;
      max-height: 300px;

      pre {
        margin: 0;
        font-family: 'Courier New', Courier, monospace;
        font-size: 13px;
        line-height: 1.6;
        white-space: pre-wrap;
        word-wrap: break-word;
      }
    }
  }
}

// 深色模式适配
html.dark {
  .log-detail {
    .detail-section {
      .code-block {
        background: #1a1a1a;
        border-color: #303030;

        pre {
          color: rgba(255, 255, 255, 0.85);
        }
      }
    }
  }
}
</style>
