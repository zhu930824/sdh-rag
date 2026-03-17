<template>
  <div class="log-container">
    <a-card>
      <template #title>
        <div class="card-header">
          <span class="card-title">日志管理</span>
        </div>
      </template>

      <!-- 搜索筛选区域 -->
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

      <!-- 表格工具栏 -->
      <TableToolbar
        :columns="tableColumns"
        :density="tableDensity"
        @refresh="loadData"
        @column-change="handleColumnChange"
        @density-change="handleDensityChange"
      />

      <!-- 表格 -->
      <a-table
        :loading="loading"
        :data-source="tableData"
        :columns="tableColumnsConfig"
        :size="tableDensity"
        :pagination="false"
        :scroll="{ x: 1200 }"
        :row-key="(record: LogItem) => record.id"
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
          <template v-else-if="column.dataIndex === 'action'">
            <a-button type="link" size="small" @click="handleViewDetail(record)">
              查看详情
            </a-button>
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
            {{ currentLog.user }}
          </a-descriptions-item>
          <a-descriptions-item label="操作类型">
            <a-tag :color="getTypeColor(currentLog.type)">
              {{ getTypeText(currentLog.type) }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="操作内容">
            {{ currentLog.content }}
          </a-descriptions-item>
          <a-descriptions-item label="IP地址">
            {{ currentLog.ip }}
          </a-descriptions-item>
          <a-descriptions-item label="操作状态">
            <a-tag :color="currentLog.status === 1 ? 'success' : 'error'">
              {{ currentLog.status === 1 ? '成功' : '失败' }}
            </a-tag>
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
import { SearchOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import type { TableColumnType } from 'ant-design-vue'
import type { Dayjs } from 'dayjs'
import TableToolbar from '@/components/TableToolbar.vue'
import { useLoading } from '@/composables'
import type { ColumnConfig, TableDensity } from '@/components/TableToolbar.vue'

// 日志项接口定义
interface LogItem {
  id: number
  createTime: string
  user: string
  type: string
  content: string
  ip: string
  status: number
  browser?: string
  os?: string
  requestParams?: Record<string, unknown>
  responseData?: Record<string, unknown>
  errorMsg?: string
}

// 筛选表单接口
interface FilterForm {
  dateRange: [Dayjs, Dayjs] | null
  type: string | undefined
  user: string
  status: number | undefined
}

const { loading, withLoading } = useLoading()

// 筛选表单
const filterForm = reactive<FilterForm>({
  dateRange: null,
  type: undefined,
  user: '',
  status: undefined,
})

// 分页配置
const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0,
})

// 表格数据
const tableData = ref<LogItem[]>([])

// 表格列配置
const tableColumns = ref<ColumnConfig[]>([
  { prop: 'id', label: 'ID', visible: true },
  { prop: 'createTime', label: '操作时间', visible: true },
  { prop: 'user', label: '操作用户', visible: true },
  { prop: 'type', label: '操作类型', visible: true },
  { prop: 'content', label: '操作内容', visible: true },
  { prop: 'ip', label: 'IP地址', visible: true },
  { prop: 'status', label: '状态', visible: true },
])

const tableDensity = ref<TableDensity>('default')

// 表格列配置计算属性
const tableColumnsConfig = computed<TableColumnType[]>(() => {
  const columns: TableColumnType[] = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    { title: '操作时间', dataIndex: 'createTime', key: 'createTime', width: 180 },
    { title: '操作用户', dataIndex: 'user', key: 'user', width: 120 },
    { title: '操作类型', dataIndex: 'type', key: 'type', width: 100 },
    { title: '操作内容', dataIndex: 'content', key: 'content', width: 300, ellipsis: true },
    { title: 'IP地址', dataIndex: 'ip', key: 'ip', width: 140 },
    { title: '状态', dataIndex: 'status', key: 'status', width: 80 },
    { title: '操作', dataIndex: 'action', key: 'action', width: 100, fixed: 'right' },
  ]
  return columns.filter(col => {
    const prop = col.dataIndex as string
    const config = tableColumns.value.find(c => c.prop === prop)
    return config ? config.visible !== false : true
  })
})

// 详情抽屉
const detailVisible = ref(false)
const currentLog = ref<LogItem | null>(null)

// 模拟数据生成
function generateMockData(): LogItem[] {
  const types = ['login', 'logout', 'create', 'update', 'delete', 'query', 'export', 'import']
  const users = ['admin', 'user01', 'user02', '张三', '李四', '王五']
  const contents = [
    '用户登录系统',
    '用户退出系统',
    '新增知识库文档',
    '修改用户信息',
    '删除文档记录',
    '查询文档列表',
    '导出数据报表',
    '导入用户数据',
    '修改系统配置',
    '创建新用户',
  ]

  return Array.from({ length: 50 }, (_, index) => {
    const type = types[Math.floor(Math.random() * types.length)]
    const status = Math.random() > 0.1 ? 1 : 0
    const hasRequest = ['create', 'update', 'delete', 'query', 'export', 'import'].includes(type)

    return {
      id: index + 1,
      createTime: getRandomDateTime(),
      user: users[Math.floor(Math.random() * users.length)],
      type,
      content: contents[Math.floor(Math.random() * contents.length)],
      ip: `192.168.${Math.floor(Math.random() * 255)}.${Math.floor(Math.random() * 255)}`,
      status,
      browser: 'Chrome 120.0.0',
      os: 'Windows 10',
      requestParams: hasRequest ? {
        page: 1,
        pageSize: 10,
        keyword: '测试',
        status: 1,
      } : undefined,
      responseData: hasRequest ? {
        code: 200,
        message: 'success',
        data: {
          total: 100,
          list: [],
        },
      } : undefined,
      errorMsg: status === 0 ? '操作失败：权限不足' : undefined,
    }
  })
}

// 生成随机日期时间
function getRandomDateTime(): string {
  const start = new Date(2024, 0, 1)
  const end = new Date()
  const date = new Date(start.getTime() + Math.random() * (end.getTime() - start.getTime()))
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).replace(/\//g, '-')
}

// 加载数据
async function loadData() {
  await withLoading(async () => {
    // 模拟异步请求
    await new Promise(resolve => setTimeout(resolve, 500))

    // 生成模拟数据
    let data = generateMockData()

    // 应用筛选条件
    if (filterForm.type) {
      data = data.filter(item => item.type === filterForm.type)
    }
    if (filterForm.user) {
      data = data.filter(item => item.user.includes(filterForm.user))
    }
    if (filterForm.status !== undefined) {
      data = data.filter(item => item.status === filterForm.status)
    }
    if (filterForm.dateRange && filterForm.dateRange.length === 2) {
      const [start, end] = filterForm.dateRange
      data = data.filter(item => {
        const itemDate = new Date(item.createTime)
        return itemDate >= start.toDate() && itemDate <= end.toDate()
      })
    }

    // 应用分页
    const start = (pagination.page - 1) * pagination.pageSize
    const end = start + pagination.pageSize
    tableData.value = data.slice(start, end)
    pagination.total = data.length
  })
}

// 搜索
function handleSearch() {
  pagination.page = 1
  loadData()
}

// 重置
function handleReset() {
  filterForm.dateRange = null
  filterForm.type = undefined
  filterForm.user = ''
  filterForm.status = undefined
  handleSearch()
}

// 分页变化
function handlePageChange(page: number) {
  pagination.page = page
  loadData()
}

// 每页条数变化
function handleSizeChange(_current: number, size: number) {
  pagination.page = 1
  pagination.pageSize = size
  loadData()
}

// 列显示切换
function handleColumnChange(prop: string, visible: boolean) {
  const col = tableColumns.value.find(c => c.prop === prop)
  if (col) {
    col.visible = visible
  }
}

// 表格密度变化
function handleDensityChange(density: TableDensity) {
  tableDensity.value = density
}

// 查看详情
function handleViewDetail(record: LogItem) {
  currentLog.value = record
  detailVisible.value = true
}

// 获取操作类型颜色
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

// 获取操作类型文本
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

// 格式化JSON
function formatJson(data: Record<string, unknown>): string {
  return JSON.stringify(data, null, 2)
}

// 初始化
onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.log-container {
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

  .search-form {
    margin-bottom: 20px;
  }

  .pagination {
    margin-top: 20px;
    display: flex;
    justify-content: flex-end;
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
