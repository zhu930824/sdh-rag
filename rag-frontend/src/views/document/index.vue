<template>
  <div class="document-container">
    <div class="category-panel-wrapper">
      <a-card :bordered="false" class="category-card">
        <CategoryPanel />
      </a-card>
    </div>

    <div class="content-panel">
      <a-card :bordered="false" class="content-card">
        <template #title>
          <div class="card-header">
            <div class="header-left">
              <span class="card-title">文档列表</span>
              <a-tag v-if="documentStore.currentCategory" color="blue" closable @close="handleClearCategory">
                {{ getCategoryName(documentStore.currentCategory) }}
              </a-tag>
            </div>
            <a-button type="primary" @click="showUploadDialog = true">
              <template #icon><UploadOutlined /></template>
              上传文档
            </a-button>
          </div>
        </template>

        <!-- 搜索栏 -->
        <div class="search-toolbar">
          <a-form layout="inline" :model="searchForm" class="search-form">
            <a-form-item>
              <a-input
                v-model:value="searchForm.keyword"
                placeholder="请输入文档名称"
                allow-clear
                style="width: 200px"
                @press-enter="handleSearch"
              />
            </a-form-item>
            <a-form-item>
              <a-button type="primary" @click="handleSearch">
                <template #icon><SearchOutlined /></template>
                搜索
              </a-button>
            </a-form-item>
          </a-form>
          <div class="toolbar-actions">
            <a-button @click="handleReset">
              <template #icon><ReloadOutlined /></template>
              重置
            </a-button>
          </div>
        </div>

        <!-- 表格 -->
        <a-table
          class="document-table"
          :loading="documentStore.loading"
          :data-source="documentStore.documentList"
          :columns="columns"
          :pagination="false"
          :scroll="{ x: 1000 }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.dataIndex === 'fileType'">
              <a-tag :color="getFileTypeColor(record.fileType)">
                {{ record.fileType?.toUpperCase() || '未知' }}
              </a-tag>
            </template>
            <template v-else-if="column.dataIndex === 'fileSize'">
              {{ formatFileSize(record.fileSize) }}
            </template>
            <template v-else-if="column.dataIndex === 'tags'">
              <div class="document-tags">
                <template v-if="record.tags?.length">
                  <a-tag
                    v-for="(tag, index) in record.tags.slice(0, 3)"
                    :key="tag.id"
                    :color="tag.color || 'blue'"
                    closable
                    @close="handleRemoveTag(record.id, tag.id)"
                  >
                    {{ tag.name }}
                  </a-tag>
                  <a-tooltip v-if="record.tags.length > 3" :title="record.tags.slice(3).map(t => t.name).join(', ')">
                    <span class="more-tags">+{{ record.tags.length - 3 }}</span>
                  </a-tooltip>
                </template>
                <span v-else class="no-tags">暂无标签</span>
              </div>
            </template>
            <template v-else-if="column.dataIndex === 'action'">
              <a-space>
                <a-button type="link" size="small" @click="openTagModal(record)">
                  <TagOutlined />
                  添加标签
                </a-button>
                <a-popconfirm title="确定要删除该文档吗？" @confirm="handleDelete(record)">
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
            v-model:page-size="pagination.size"
            :total="documentStore.pagination.total"
            :page-size-options="['10', '20', '50', '100']"
            show-size-changer
            show-quick-jumper
            :show-total="(total: number) => `共 ${total} 条`"
            @change="handlePageChange"
            @showSizeChange="handleSizeChange"
          />
        </div>
      </a-card>
    </div>

    <UploadDialog v-model:open="showUploadDialog" @success="handleUploadSuccess" />

    <!-- 添加标签弹窗 -->
    <a-modal
      v-model:open="tagModalVisible"
      title="添加标签"
      :width="450"
      @ok="handleAddTags"
      @cancel="tagModalVisible = false"
    >
      <a-form :label-col="{ span: 6 }">
        <a-form-item label="选择标签">
          <a-select
            v-model:value="selectedTagIds"
            mode="multiple"
            placeholder="请选择标签（可多选）"
            style="width: 100%"
            :loading="tagsLoading"
            :options="availableTagOptions"
            :filter-option="filterTagOption"
            show-search
          />
        </a-form-item>
        <a-form-item label="当前标签">
          <div class="current-tags">
            <a-tag
              v-for="tag in currentDocument?.tags"
              :key="tag.id"
              :color="tag.color || 'blue'"
            >
              {{ tag.name }}
            </a-tag>
            <span v-if="!currentDocument?.tags?.length" class="no-tags-text">暂无标签</span>
          </div>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { UploadOutlined, SearchOutlined, ReloadOutlined, TagOutlined } from '@ant-design/icons-vue'
import { useDocumentStore } from '@/stores/document'
import { showSuccess, showError } from '@/utils/message'
import { getAllTags, addDocumentTag, removeDocumentTag, type Tag } from '@/api/tag'
import CategoryPanel from './components/CategoryPanel.vue'
import UploadDialog from './components/UploadDialog.vue'
import type { Document } from '@/api/document'

const documentStore = useDocumentStore()

const showUploadDialog = ref(false)
const tagModalVisible = ref(false)
const selectedTagIds = ref<number[]>([])
const currentDocument = ref<Document | null>(null)
const allTags = ref<Tag[]>([])
const tagsLoading = ref(false)

const searchForm = reactive({
  keyword: '',
})

const pagination = computed(() => documentStore.pagination)

// 可选标签选项（排除已选标签）
const availableTagOptions = computed(() => {
  const currentTagIds = currentDocument.value?.tags?.map(t => t.id) || []
  return allTags.value
    .filter(tag => !currentTagIds.includes(tag.id))
    .map(tag => ({
      label: tag.name,
      value: tag.id,
    }))
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
    dataIndex: 'title',
    key: 'title',
    ellipsis: true,
  },
  {
    title: '文件类型',
    dataIndex: 'fileType',
    key: 'fileType',
    width: 100,
  },
  {
    title: '文件大小',
    dataIndex: 'fileSize',
    key: 'fileSize',
    width: 100,
  },
  {
    title: '标签',
    dataIndex: 'tags',
    key: 'tags',
    width: 200,
  },
  {
    title: '上传时间',
    dataIndex: 'createTime',
    key: 'createTime',
    width: 180,
  },
  {
    title: '操作',
    dataIndex: 'action',
    key: 'action',
    width: 160,
    fixed: 'right' as const,
  },
]

onMounted(async () => {
  await documentStore.fetchCategories()
  await documentStore.fetchDocuments()
  await fetchAllTags()
})

async function fetchAllTags() {
  tagsLoading.value = true
  try {
    const res = await getAllTags()
    if (res.code === 200 || res.code === 0) {
      allTags.value = res.data || []
    }
  } finally {
    tagsLoading.value = false
  }
}

function handleSearch() {
  // TODO: 实现搜索功能
  message.info('搜索功能开发中')
}

function handleReset() {
  searchForm.keyword = ''
  documentStore.reset()
}

function handleClearCategory() {
  documentStore.setCurrentCategory(null)
}

function handlePageChange(page: number) {
  documentStore.setPagination(page, pagination.value.size)
}

function handleSizeChange(_current: number, size: number) {
  documentStore.setPagination(1, size)
}

async function handleDelete(record: Document) {
  try {
    await documentStore.removeDocument(record.id)
    showSuccess('删除成功')
  } catch (error) {
    showError('删除失败')
  }
}

function handleUploadSuccess() {
  showUploadDialog.value = false
  documentStore.fetchDocuments()
}

function openTagModal(record: Document) {
  currentDocument.value = record
  selectedTagIds.value = []
  tagModalVisible.value = true
}

async function handleAddTags() {
  if (!currentDocument.value || selectedTagIds.value.length === 0) {
    message.warning('请选择标签')
    return
  }

  try {
    // 批量添加标签
    const promises = selectedTagIds.value.map(tagId => addDocumentTag(currentDocument.value!.id, tagId))
    await Promise.all(promises)
    showSuccess('添加标签成功')
    tagModalVisible.value = false
    await documentStore.fetchDocuments()
  } catch (error) {
    showError('添加标签失败')
  }
}

async function handleRemoveTag(documentId: number, tagId: number) {
  try {
    const res = await removeDocumentTag(documentId, tagId)
    if (res.code === 200 || res.code === 0) {
      showSuccess('移除标签成功')
      await documentStore.fetchDocuments()
    }
  } catch (error) {
    showError('移除标签失败')
  }
}

function filterTagOption(input: string, option: { label: string }) {
  return option.label.toLowerCase().includes(input.toLowerCase())
}

function getCategoryName(categoryId: number): string {
  const category = documentStore.categoryList.find(c => c.id === categoryId)
  return category?.name || '未知分类'
}

function getFileTypeColor(fileType: string): string {
  const colorMap: Record<string, string> = {
    pdf: 'red',
    doc: 'blue',
    docx: 'blue',
    txt: 'green',
    xls: 'green',
    xlsx: 'green',
    ppt: 'orange',
    pptx: 'orange',
  }
  return colorMap[fileType?.toLowerCase()] || 'default'
}

function formatFileSize(bytes: number): string {
  if (!bytes || bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}
</script>

<style scoped lang="scss">
.document-container {
  display: flex;
  gap: 16px;
  height: calc(100vh - 64px - 48px);
  overflow: hidden;

  .category-panel-wrapper {
    width: 280px;
    flex-shrink: 0;
    height: 100%;

    .category-card {
      height: 100%;

      :deep(.ant-card-body) {
        height: 100%;
        display: flex;
        flex-direction: column;
        overflow: hidden;
      }
    }
  }

  .content-panel {
    flex: 1;
    min-width: 0;
    height: 100%;

    .content-card {
      height: 100%;

      :deep(.ant-card) {
        height: 100%;
        display: flex;
        flex-direction: column;
      }

      :deep(.ant-card-head) {
        flex-shrink: 0;
      }

      :deep(.ant-card-body) {
        flex: 1;
        min-height: 0;
        display: flex;
        flex-direction: column;
        overflow: hidden;
      }
    }

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;

      .header-left {
        display: flex;
        align-items: center;
        gap: 12px;

        .card-title {
          font-family: var(--font-display);
          font-size: 18px;
          font-weight: var(--font-weight-semibold);
        }
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

    .document-table {
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

    .document-tags {
      display: flex;
      flex-wrap: nowrap;
      gap: 4px;
      align-items: center;
      overflow: hidden;
      white-space: nowrap;

      .no-tags {
        color: var(--text-tertiary);
        font-size: 12px;
      }

      .more-tags {
        display: inline-flex;
        align-items: center;
        padding: 0 7px;
        font-size: 12px;
        line-height: 20px;
        border-radius: 4px;
        background: #f0f0f0;
        color: #666;
        cursor: pointer;
        flex-shrink: 0;

        &:hover {
          background: #e0e0e0;
        }
      }
    }

    .pagination {
      margin-top: 16px;
      display: flex;
      justify-content: flex-end;
      flex-shrink: 0;
    }
  }
}

.current-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;

  .no-tags-text {
    color: var(--text-tertiary);
    font-size: 12px;
  }
}

@media (max-width: 1200px) {
  .document-container {
    flex-direction: column;
    height: calc(100vh - 64px - 48px);
    overflow-y: auto;

    .category-panel-wrapper {
      width: 100%;
      height: auto;

      .category-card {
        height: auto;

        :deep(.ant-card-body) {
          height: auto;
          max-height: 300px;
        }
      }
    }

    .content-panel {
      height: auto;

      .content-card {
        height: auto;
      }

      .document-table {
        overflow: visible;
      }
    }
  }
}
</style>
