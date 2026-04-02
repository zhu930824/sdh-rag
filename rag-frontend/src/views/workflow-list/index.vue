<template>
  <div class="workflow-list-page">
    <!-- 统计概览 -->
    <div class="stats-grid">
      <div
        v-for="(stat, index) in statsData"
        :key="stat.label"
        class="stat-card"
        :style="{ animationDelay: `${index * 0.1}s` }"
      >
        <div class="stat-icon" :style="{ backgroundColor: stat.color }">
          <ApartmentOutlined v-if="stat.icon === 'workflow'" />
          <CheckCircleOutlined v-else-if="stat.icon === 'active'" />
          <PlayCircleOutlined v-else-if="stat.icon === 'run'" />
          <ClockCircleOutlined v-else />
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ stat.value }}</div>
          <div class="stat-label">{{ stat.label }}</div>
        </div>
      </div>
    </div>

    <!-- 主内容区 -->
    <a-card class="main-card">
      <template #title>
        <div class="card-header">
          <span class="card-title">工作流管理</span>
          <a-button type="primary" @click="handleCreate">
            <template #icon><PlusOutlined /></template>
            新建工作流
          </a-button>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div class="search-toolbar">
        <a-form layout="inline" :model="filters" class="search-form">
          <a-form-item label="关键词">
            <a-input
              v-model:value="filters.keyword"
              placeholder="搜索工作流名称"
              allow-clear
              style="width: 200px"
              @pressEnter="handleSearch"
            />
          </a-form-item>
          <a-form-item label="状态">
            <a-select v-model:value="filters.status" placeholder="全部" allow-clear style="width: 120px">
              <a-select-option :value="1">启用</a-select-option>
              <a-select-option :value="0">禁用</a-select-option>
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

      <!-- 工作流卡片列表 -->
      <a-spin :spinning="loading">
        <div class="workflow-grid">
          <div
            v-for="workflow in tableData"
            :key="workflow.id"
            class="workflow-card"
            @click="handleEdit(workflow)"
          >
            <div class="workflow-header">
              <div class="workflow-icon" :style="{ backgroundColor: workflow.color || '#1890ff' }">
                <ApartmentOutlined />
              </div>
              <div class="workflow-title-wrap">
                <div class="workflow-name">{{ workflow.name }}</div>
                <div class="workflow-desc">{{ workflow.description || '暂无描述' }}</div>
              </div>
              <a-dropdown @click.stop>
                <a-button type="text" size="small">
                  <MoreOutlined />
                </a-button>
                <template #overlay>
                  <a-menu>
                    <a-menu-item key="edit" @click="handleEdit(workflow)">
                      <EditOutlined /> 编辑
                    </a-menu-item>
                    <a-menu-item key="copy" @click="handleCopy(workflow)">
                      <CopyOutlined /> 复制
                    </a-menu-item>
                    <a-menu-divider />
                    <a-menu-item key="toggle" @click="handleToggle(workflow)">
                      <CheckCircleOutlined /> {{ workflow.status === 1 ? '禁用' : '启用' }}
                    </a-menu-item>
                    <a-menu-item key="delete" danger @click="handleDelete(workflow)">
                      <DeleteOutlined /> 删除
                    </a-menu-item>
                  </a-menu>
                </template>
              </a-dropdown>
            </div>

            <div class="workflow-stats">
              <div class="stat-item">
                <span class="stat-value">{{ getNodeCount(workflow) }}</span>
                <span class="stat-label">节点</span>
              </div>
              <div class="stat-item">
                <span class="stat-value">{{ workflow.version || 1 }}</span>
                <span class="stat-label">版本</span>
              </div>
            </div>

            <div class="workflow-footer">
              <a-tag :color="workflow.status === 1 ? 'green' : 'red'" size="small">
                {{ workflow.status === 1 ? '启用' : '禁用' }}
              </a-tag>
              <span class="workflow-time">{{ formatDate(workflow.createTime) }}</span>
            </div>
          </div>

          <div v-if="!loading && tableData.length === 0" class="empty-state">
            <a-empty description="暂无工作流">
              <a-button type="primary" @click="handleCreate">新建工作流</a-button>
            </a-empty>
          </div>
        </div>
      </a-spin>

      <!-- 分页 -->
      <div class="pagination">
        <a-pagination
          v-model:current="pagination.current"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-size-options="['12', '24', '48', '96']"
          show-size-changer
          show-quick-jumper
          :show-total="(total: number) => `共 ${total} 条`"
          @change="handlePageChange"
          @show-size-change="handleSizeChange"
        />
      </div>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import {
  PlusOutlined,
  SearchOutlined,
  ReloadOutlined,
  SyncOutlined,
  ApartmentOutlined,
  CheckCircleOutlined,
  PlayCircleOutlined,
  ClockCircleOutlined,
  MoreOutlined,
  EditOutlined,
  CopyOutlined,
  DeleteOutlined,
} from '@ant-design/icons-vue'
import {
  getWorkflowList,
  deleteWorkflow,
  toggleWorkflowStatus,
  type Workflow,
} from '@/api/workflow'

const router = useRouter()
const loading = ref(false)
const tableData = ref<Workflow[]>([])

const pagination = reactive({
  current: 1,
  pageSize: 12,
  total: 0,
})

const filters = reactive({
  keyword: '',
  status: undefined as number | undefined,
})

const stats = reactive({
  total: 0,
  active: 0,
  runs: 0,
  avgTime: 0,
})

const statsData = [
  { label: '工作流总数', value: stats.total, icon: 'workflow', color: '#1890ff' },
  { label: '已启用', value: stats.active, icon: 'active', color: '#52c41a' },
  { label: '执行次数', value: stats.runs, icon: 'run', color: '#722ed1' },
  { label: '平均耗时', value: `${stats.avgTime}ms`, icon: 'time', color: '#13c2c2' },
]

async function loadData() {
  loading.value = true
  try {
    const res = await getWorkflowList({
      page: pagination.current,
      pageSize: pagination.pageSize,
      keyword: filters.keyword,
      status: filters.status,
    })
    if (res.code === 200) {
      tableData.value = res.data?.records || []
      pagination.total = res.data?.total || 0

      // 更新统计
      stats.total = pagination.total
      stats.active = tableData.value.filter(w => w.status === 1).length
    }
  } catch (error) {
    console.error('加载工作流列表失败:', error)
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.current = 1
  loadData()
}

function handleReset() {
  filters.keyword = ''
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

function handleCreate() {
  router.push('/workflow/create')
}

function handleEdit(workflow: Workflow) {
  router.push(`/workflow/${workflow.id}`)
}

async function handleCopy(workflow: Workflow) {
  message.info('复制功能开发中')
}

async function handleToggle(workflow: Workflow) {
  try {
    await toggleWorkflowStatus(workflow.id)
    message.success('操作成功')
    loadData()
  } catch (error) {
    message.error('操作失败')
  }
}

async function handleDelete(workflow: Workflow) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除工作流"${workflow.name}"吗？`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteWorkflow(workflow.id)
        message.success('删除成功')
        loadData()
      } catch (error) {
        message.error('删除失败')
      }
    },
  })
}

function getNodeCount(workflow: Workflow): number {
  return workflow.definition?.nodes?.length || 0
}

function formatDate(dateStr: string): string {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  })
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.workflow-list-page {
  height: calc(100vh - 56px - 32px);
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

// Stats Grid
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  flex-shrink: 0;
}

.stat-card {
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-xl);
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  transition: all var(--duration-normal) var(--ease-nature);
  animation: slideInUp 0.4s var(--ease-out) both;

  &:hover {
    transform: translateY(-2px);
    box-shadow: var(--shadow-card-hover);
  }
}

.stat-icon {
  width: 52px;
  height: 52px;
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 24px;
  flex-shrink: 0;
}

.stat-info {
  flex: 1;
  min-width: 0;
}

.stat-value {
  font-family: var(--font-serif);
  font-size: 28px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: var(--text-secondary);
  margin-top: 2px;
}

// Main Card
.main-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;

  :deep(.ant-card-body) {
    flex: 1;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }
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
  margin-bottom: 20px;
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

// Workflow Grid
.workflow-grid {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
  align-content: start;
  overflow-y: auto;
}

.workflow-card {
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-xl);
  padding: 20px;
  cursor: pointer;
  transition: all var(--duration-normal) var(--ease-nature);

  &:hover {
    border-color: var(--primary-color);
    box-shadow: var(--shadow-card-hover);
  }
}

.workflow-header {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
}

.workflow-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 22px;
  flex-shrink: 0;
}

.workflow-title-wrap {
  flex: 1;
  min-width: 0;
}

.workflow-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workflow-desc {
  font-size: 13px;
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workflow-stats {
  display: flex;
  gap: 24px;
  padding: 12px 0;
  border-top: 1px solid var(--border-light);
  border-bottom: 1px solid var(--border-light);
  margin-bottom: 12px;

  .stat-item {
    display: flex;
    align-items: center;
    gap: 8px;

    .stat-value {
      font-size: 18px;
      font-weight: 600;
      color: var(--text-primary);
    }

    .stat-label {
      font-size: 12px;
      color: var(--text-tertiary);
    }
  }
}

.workflow-footer {
  display: flex;
  align-items: center;
  gap: 8px;

  .workflow-time {
    margin-left: auto;
    font-size: 12px;
    color: var(--text-tertiary);
  }
}

.empty-state {
  grid-column: 1 / -1;
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 300px;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
  flex-shrink: 0;
}

// Animations
@keyframes slideInUp {
  from {
    opacity: 0;
    transform: translateY(16px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

// Responsive
@media (max-width: 1200px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .workflow-grid {
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  }
}

@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }

  .search-toolbar {
    flex-direction: column;
    gap: 12px;
  }

  .workflow-grid {
    grid-template-columns: 1fr;
  }
}
</style>
