<template>
  <div class="tag-container">
    <a-card>
      <template #title>
        <div class="card-header">
          <span class="card-title">标签管理</span>
          <a-button type="primary" @click="showCreateModal">
            <template #icon><PlusOutlined /></template>
            新建标签
          </a-button>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div class="search-toolbar">
        <a-form layout="inline" :model="filters" class="search-form">
          <a-form-item label="关键词">
            <a-input v-model:value="filters.keyword" placeholder="搜索标签" allow-clear style="width: 200px" @pressEnter="handleSearch" />
          </a-form-item>
          <a-form-item label="分类">
            <a-select v-model:value="filters.category" placeholder="全部" allow-clear style="width: 150px">
              <a-select-option value="文档类型">文档类型</a-select-option>
              <a-select-option value="业务领域">业务领域</a-select-option>
              <a-select-option value="技术栈">技术栈</a-select-option>
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
          <a-button @click="loadTags">
            <template #icon><SyncOutlined /></template>
            刷新
          </a-button>
        </div>
      </div>

      <!-- 标签云 -->
      <div class="tag-cloud-section">
        <div class="section-title">标签云</div>
        <div class="tag-cloud">
          <a-tag
            v-for="tag in tags"
            :key="tag.id"
            :color="tag.color || 'blue'"
            style="margin: 4px; cursor: pointer"
            @click="handleTagClick(tag)"
          >
            {{ tag.name }}
          </a-tag>
          <span v-if="tags.length === 0" class="empty-hint">暂无标签</span>
        </div>
      </div>

      <!-- 表格 -->
      <a-table
        class="tag-table"
        :columns="columns"
        :data-source="tableData"
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 800 }"
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
              <a-popconfirm title="确定删除该标签？" ok-text="确定" cancel-text="取消" @confirm="handleDelete(record)">
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
      :title="isEdit ? '编辑标签' : '新建标签'"
      ok-text="确认"
      cancel-text="取消"
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
import { PlusOutlined, SearchOutlined, ReloadOutlined, SyncOutlined } from '@ant-design/icons-vue'
import { getTagList, getAllTags, createTag, updateTag, deleteTag, type Tag } from '@/api/tag'

const loading = ref(false)
const tableData = ref<Tag[]>([])
const tags = ref<Tag[]>([])
const modalVisible = ref(false)
const isEdit = ref(false)

const pagination = reactive({ current: 1, pageSize: 10, total: 0 })
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
  { title: '操作', dataIndex: 'action', width: 150, fixed: 'right' },
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

function handleSearch() {
  pagination.current = 1
  loadTags()
}

function handleReset() {
  filters.keyword = ''
  filters.category = ''
  handleSearch()
}

function handlePageChange(page: number) {
  pagination.current = page
  loadTags()
}

function handleSizeChange(_current: number, size: number) {
  pagination.current = 1
  pagination.pageSize = size
  loadTags()
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
  handleSearch()
}

onMounted(() => {
  loadTags()
  loadAllTags()
})
</script>

<style scoped lang="scss">
.tag-container {
  height: calc(100vh - 64px - 48px);
  overflow-y: auto;

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
    flex-wrap: wrap;
    gap: 12px;
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

  .tag-cloud-section {
    margin-bottom: 16px;
    flex-shrink: 0;

    .section-title {
      font-size: 14px;
      font-weight: var(--font-weight-medium);
      color: var(--text-primary);
      margin-bottom: 12px;
    }

    .tag-cloud {
      padding: 16px;
      background: var(--bg-surface-secondary);
      border-radius: var(--radius-lg);
      min-height: 50px;

      .empty-hint {
        color: var(--text-tertiary);
        font-size: 13px;
      }
    }
  }

  .tag-table {
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

  .color-preview {
    width: 24px;
    height: 24px;
    border-radius: var(--radius-sm);
    border: 1px solid var(--border-color);
  }
}
</style>
