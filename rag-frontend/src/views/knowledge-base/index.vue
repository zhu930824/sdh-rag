<template>
  <div class="knowledge-base-page">
    <!-- 统计概览 -->
    <div class="stats-grid">
      <div
        v-for="(stat, index) in statsData"
        :key="stat.label"
        class="stat-card"
        :style="{ animationDelay: `${index * 0.1}s` }"
      >
        <div class="stat-icon" :style="{ backgroundColor: stat.color }">
          <FolderOutlined v-if="stat.icon === 'folder'" />
          <FileTextOutlined v-else-if="stat.icon === 'file'" />
          <PartitionOutlined v-else-if="stat.icon === 'partition'" />
          <CheckCircleOutlined v-else />
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
          <span class="card-title">知识库管理</span>
          <a-button type="primary" @click="showCreateModal">
            <template #icon><PlusOutlined /></template>
            新建知识库
          </a-button>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div class="search-toolbar">
        <a-form layout="inline" :model="filters" class="search-form">
          <a-form-item label="关键词">
            <a-input
              v-model:value="filters.keyword"
              placeholder="搜索知识库名称"
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

      <!-- 知识库卡片列表 -->
      <a-spin :spinning="loading">
        <div class="kb-grid">
          <div
            v-for="kb in tableData"
            :key="kb.id"
            class="kb-card"
            @click="handleViewDetail(kb)"
          >
            <div class="kb-header">
              <div class="kb-icon" :style="{ backgroundColor: kb.color || '#1890ff' }">
                <span class="icon-text">{{ kb.icon || 'KB' }}</span>
              </div>
              <div class="kb-title-wrap">
                <div class="kb-name">{{ kb.name }}</div>
                <div class="kb-desc">{{ kb.description || '暂无描述' }}</div>
              </div>
              <a-dropdown @click.stop>
                <a-button type="text" size="small">
                  <MoreOutlined />
                </a-button>
                <template #overlay>
                  <a-menu>
                    <a-menu-item key="edit" @click="handleEdit(kb)">
                      <EditOutlined /> 编辑
                    </a-menu-item>
                    <a-menu-item key="documents" @click="handleViewDocuments(kb)">
                      <FileTextOutlined /> 查看文档
                    </a-menu-item>
                    <a-menu-divider />
                    <a-menu-item key="delete" danger @click="handleDelete(kb)">
                      <DeleteOutlined /> 删除
                    </a-menu-item>
                  </a-menu>
                </template>
              </a-dropdown>
            </div>

            <div class="kb-stats">
              <div class="kb-stat-item">
                <FileTextOutlined class="stat-icon" />
                <span class="stat-value">{{ kb.documentCount || 0 }}</span>
                <span class="stat-label">文档</span>
              </div>
              <div class="kb-stat-item">
                <PartitionOutlined class="stat-icon" />
                <span class="stat-value">{{ kb.chunkCount || 0 }}</span>
                <span class="stat-label">分块</span>
              </div>
            </div>

            <div class="kb-footer">
              <a-tag :color="kb.status === 1 ? 'green' : 'red'" size="small">
                {{ kb.status === 1 ? '启用' : '禁用' }}
              </a-tag>
              <a-tag v-if="kb.isPublic" color="blue" size="small">公开</a-tag>
              <span class="kb-time">{{ formatDate(kb.createTime) }}</span>
            </div>
          </div>

          <div v-if="!loading && tableData.length === 0" class="empty-state">
            <a-empty description="暂无知识库">
              <a-button type="primary" @click="showCreateModal">新建知识库</a-button>
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
          :page-size-options="['8', '16', '24', '32']"
          show-size-changer
          show-quick-jumper
          :show-total="(total: number) => `共 ${total} 条`"
          @change="handlePageChange"
          @show-size-change="handleSizeChange"
        />
      </div>
    </a-card>

    <!-- 新建/编辑弹窗 -->
    <a-modal
      v-model:open="modalVisible"
      :title="isEdit ? '编辑知识库' : '新建知识库'"
      :width="520"
      ok-text="确认"
      cancel-text="取消"
      @ok="handleSubmit"
    >
      <a-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        :label-col="{ span: 5 }"
        :wrapper-col="{ span: 18 }"
      >
        <a-form-item label="名称" name="name">
          <a-input v-model:value="formData.name" placeholder="请输入知识库名称" :maxlength="50" />
        </a-form-item>
        <a-form-item label="描述" name="description">
          <a-textarea
            v-model:value="formData.description"
            placeholder="请输入知识库描述"
            :rows="3"
            :maxlength="200"
            show-count
          />
        </a-form-item>
        <a-form-item label="图标" name="icon">
          <a-input v-model:value="formData.icon" placeholder="图标文字(如: KB)" :maxlength="4" style="width: 100px" />
        </a-form-item>
        <a-form-item label="颜色" name="color">
          <div class="color-picker">
            <div
              v-for="color in colorOptions"
              :key="color"
              class="color-item"
              :class="{ active: formData.color === color }"
              :style="{ backgroundColor: color }"
              @click="formData.color = color"
            />
          </div>
        </a-form-item>
        <a-form-item label="是否公开" name="isPublic">
          <a-switch v-model:checked="formData.isPublic" />
          <span class="form-hint">公开后其他用户可查看和使用此知识库</span>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 详情抽屉 -->
    <a-drawer
      v-model:open="detailVisible"
      :title="currentKb?.name"
      :width="600"
      placement="right"
    >
      <a-descriptions :column="1" bordered v-if="currentKb">
        <a-descriptions-item label="知识库名称">{{ currentKb.name }}</a-descriptions-item>
        <a-descriptions-item label="描述">{{ currentKb.description || '暂无描述' }}</a-descriptions-item>
        <a-descriptions-item label="文档数量">{{ currentKb.documentCount || 0 }}</a-descriptions-item>
        <a-descriptions-item label="分块数量">{{ currentKb.chunkCount || 0 }}</a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-tag :color="currentKb.status === 1 ? 'green' : 'red'">
            {{ currentKb.status === 1 ? '启用' : '禁用' }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="是否公开">
          <a-tag :color="currentKb.isPublic ? 'blue' : 'default'">
            {{ currentKb.isPublic ? '公开' : '私有' }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="创建时间">{{ currentKb.createTime }}</a-descriptions-item>
        <a-descriptions-item label="更新时间">{{ currentKb.updateTime }}</a-descriptions-item>
      </a-descriptions>

      <a-divider />

      <div class="drawer-actions">
        <a-button type="primary" @click="handleViewDocuments(currentKb)">
          <template #icon><FileTextOutlined /></template>
          查看文档
        </a-button>
        <a-button @click="handleEdit(currentKb)">
          <template #icon><EditOutlined /></template>
          编辑知识库
        </a-button>
      </div>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { FormInstance, Rule } from 'ant-design-vue/es/form'
import {
  PlusOutlined,
  SearchOutlined,
  ReloadOutlined,
  SyncOutlined,
  FolderOutlined,
  FileTextOutlined,
  PartitionOutlined,
  CheckCircleOutlined,
  MoreOutlined,
  EditOutlined,
  DeleteOutlined,
} from '@ant-design/icons-vue'
import {
  getKnowledgeBaseList,
  getKnowledgeBaseStats,
  createKnowledgeBase,
  updateKnowledgeBase,
  deleteKnowledgeBase,
  type KnowledgeBase,
  type KnowledgeBaseFormData,
} from '@/api/knowledgeBase'

const router = useRouter()
const loading = ref(false)
const tableData = ref<KnowledgeBase[]>([])
const modalVisible = ref(false)
const detailVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()
const currentKb = ref<KnowledgeBase | null>(null)

const pagination = reactive({
  current: 1,
  pageSize: 8,
  total: 0,
})

const filters = reactive({
  keyword: '',
  status: undefined as number | undefined,
})

const stats = reactive({
  totalBases: 0,
  totalDocuments: 0,
  totalChunks: 0,
  activeBases: 0,
})

const statsData = computed(() => [
  { label: '知识库总数', value: stats.totalBases, icon: 'folder', color: '#1890ff' },
  { label: '文档总数', value: stats.totalDocuments, icon: 'file', color: '#52c41a' },
  { label: '分块总数', value: stats.totalChunks, icon: 'partition', color: '#722ed1' },
  { label: '启用数量', value: stats.activeBases, icon: 'check', color: '#13c2c2' },
])

const formData = reactive<KnowledgeBaseFormData>({
  name: '',
  description: '',
  icon: 'KB',
  color: '#1890ff',
  isPublic: false,
})

const formRules: Record<string, Rule[]> = {
  name: [
    { required: true, message: '请输入知识库名称', trigger: 'blur' },
    { min: 2, max: 50, message: '名称长度在 2 到 50 个字符', trigger: 'blur' },
  ],
}

const colorOptions = [
  '#1890ff', '#13c2c2', '#52c41a', '#faad14',
  '#f5222d', '#eb2f96', '#722ed1', '#2f54eb',
]

async function loadData() {
  loading.value = true
  try {
    const res = await getKnowledgeBaseList({
      page: pagination.current,
      pageSize: pagination.pageSize,
      keyword: filters.keyword,
      status: filters.status,
    })
    if (res.code === 200) {
      tableData.value = res.data?.records || []
      pagination.total = res.data?.total || 0
    }
  } catch (error) {
    console.error('加载知识库列表失败:', error)
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    const res = await getKnowledgeBaseStats()
    if (res.code === 200 && res.data) {
      stats.totalBases = res.data.totalBases || 0
      stats.totalDocuments = res.data.totalDocuments || 0
      stats.totalChunks = res.data.totalChunks || 0
      stats.activeBases = res.data.activeBases || 0
    }
  } catch (error) {
    console.error('加载统计信息失败:', error)
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

function showCreateModal() {
  isEdit.value = false
  Object.assign(formData, {
    name: '',
    description: '',
    icon: 'KB',
    color: '#1890ff',
    isPublic: false,
  })
  modalVisible.value = true
}

function handleEdit(kb: KnowledgeBase) {
  isEdit.value = true
  Object.assign(formData, {
    id: kb.id,
    name: kb.name,
    description: kb.description,
    icon: kb.icon || 'KB',
    color: kb.color || '#1890ff',
    isPublic: kb.isPublic,
  })
  modalVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
    if (isEdit.value) {
      await updateKnowledgeBase(formData.id!, formData)
      message.success('更新成功')
    } else {
      await createKnowledgeBase(formData)
      message.success('创建成功')
    }
    modalVisible.value = false
    loadData()
    loadStats()
  } catch (error) {
    console.error('操作失败:', error)
  }
}

async function handleDelete(kb: KnowledgeBase) {
  try {
    await deleteKnowledgeBase(kb.id)
    message.success('删除成功')
    loadData()
    loadStats()
  } catch (error) {
    message.error('删除失败')
  }
}

function handleViewDetail(kb: KnowledgeBase) {
  router.push(`/knowledge/${kb.id}`)
}

function handleViewDocuments(kb: KnowledgeBase | null) {
  if (!kb) return
  router.push({
    path: '/knowledge',
    query: { knowledgeBaseId: kb.id },
  })
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
  loadStats()
})
</script>

<style scoped lang="scss">
.knowledge-base-page {
  height: calc(100vh - 64px - 48px);
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
    font-family: var(--font-display);
    font-size: 18px;
    font-weight: var(--font-weight-semibold);
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

// KB Grid
.kb-grid {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
  align-content: start;
}

.kb-card {
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

.kb-header {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
}

.kb-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;

  .icon-text {
    color: #fff;
    font-size: 16px;
    font-weight: 600;
  }
}

.kb-title-wrap {
  flex: 1;
  min-width: 0;
}

.kb-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.kb-title-wrap {
  flex: 1;
  min-width: 0;
}

.kb-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kb-desc {
  font-size: 13px;
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kb-stats {
  display: flex;
  gap: 24px;
  padding: 12px 0;
  border-top: 1px solid var(--border-light);
  border-bottom: 1px solid var(--border-light);
  margin-bottom: 12px;
}

.kb-stat-item {
  display: flex;
  align-items: center;
  gap: 6px;

  .stat-icon {
    font-size: 14px;
    color: var(--text-tertiary);
    width: auto;
    height: auto;
    background: transparent;
  }

  .stat-value {
    font-size: 16px;
    font-weight: 600;
    color: var(--text-primary);
  }

  .stat-label {
    font-size: 12px;
    color: var(--text-tertiary);
  }
}

.kb-footer {
  display: flex;
  align-items: center;
  gap: 8px;

  .kb-time {
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

// Color Picker
.color-picker {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.color-item {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  cursor: pointer;
  border: 2px solid transparent;
  transition: all 0.2s;

  &:hover {
    transform: scale(1.1);
  }

  &.active {
    border-color: var(--text-primary);
  }
}

.form-hint {
  margin-left: 12px;
  font-size: 12px;
  color: var(--text-tertiary);
}

// Drawer Actions
.drawer-actions {
  display: flex;
  gap: 12px;
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

  .kb-grid {
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

  .kb-grid {
    grid-template-columns: 1fr;
  }
}
</style>
