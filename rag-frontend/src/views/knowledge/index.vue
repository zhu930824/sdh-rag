<template>
  <div class="knowledge-container">
    <div class="category-panel">
      <a-card :bordered="false">
        <template #title>
          <div class="panel-header">
            <span class="panel-title">文档分类</span>
            <a-button type="link" @click="showCategoryManager = true">
              <template #icon><SettingOutlined /></template>
              管理
            </a-button>
          </div>
        </template>
        <a-tree
          :tree-data="knowledgeStore.categoryTree"
          :field-names="{ title: 'name', key: 'id', children: 'children' }"
          :show-icon="false"
          default-expand-all
          @select="handleCategoryClick"
        >
          <template #title="{ name, id }">
            <span class="tree-node">
              <FolderOutlined />
              <span>{{ name }}</span>
              <span class="count">{{ getCategoryCount(id) }}</span>
            </span>
          </template>
        </a-tree>
      </a-card>
    </div>

    <div class="content-panel">
      <a-card :bordered="false">
        <template #title>
          <div class="card-header">
            <span class="card-title">文档列表</span>
            <a-button type="primary" @click="showUploadDialog = true">
              <template #icon><UploadOutlined /></template>
              上传文档
            </a-button>
          </div>
        </template>

        <div class="search-form">
          <a-input-search
            v-model:value="searchKeyword"
            placeholder="搜索文档标题或内容"
            allow-clear
            style="width: 300px"
            @search="handleSearch"
            @press-enter="handleSearch"
          />
          <a-button style="margin-left: 8px" @click="handleReset">
            <template #icon><ReloadOutlined /></template>
            重置
          </a-button>
        </div>

        <a-spin :spinning="knowledgeStore.loading">
          <div class="document-list">
            <a-empty v-if="!knowledgeStore.loading && (!knowledgeStore.documentList || knowledgeStore.documentList.length === 0)" description="暂无文档数据">
              <a-button type="primary" @click="showUploadDialog = true">上传文档</a-button>
            </a-empty>

            <div v-else class="document-grid">
              <div
                v-for="doc in knowledgeStore.documentList"
                :key="doc.id"
                class="document-card"
                @click="handleViewDetail(doc.id)"
              >
                <div class="card-icon">
                  <FileTextOutlined :style="{ fontSize: '40px', color: getFileIconColor(doc.fileType) }" />
                </div>
                <div class="card-content">
                  <div class="card-title">{{ doc.title }}</div>
                  <div class="card-meta">
                    <span class="file-type">{{ doc.fileType.toUpperCase() }}</span>
                    <span class="file-size">{{ formatFileSize(doc.fileSize) }}</span>
                  </div>
                  <div class="card-status">
                    <a-tag :color="getStatusColor(doc.status)">
                      {{ getStatusText(doc.status) }}
                    </a-tag>
                  </div>
                  <div class="card-footer">
                    <span class="time">{{ formatDate(doc.createTime) }}</span>
                    <div class="actions" @click.stop>
                      <a-button type="link" size="small" @click="handleViewDetail(doc.id)">
                        查看
                      </a-button>
                      <a-button type="link" danger size="small" @click="handleDelete(doc.id)">
                        删除
                      </a-button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </a-spin>

        <a-pagination
          v-model:current="currentPage"
          v-model:page-size="pageSize"
          :total="knowledgeStore.pagination.total"
          :page-size-options="['10', '20', '50', '100']"
          show-size-changer
          show-quick-jumper
          :show-total="(total: number) => `共 ${total} 条`"
          class="pagination"
          @change="handlePageChange"
          @show-size-change="handleSizeChange"
        />
      </a-card>
    </div>

    <UploadDialog v-model:open="showUploadDialog" @success="handleUploadSuccess" />

    <CategoryManager v-model:open="showCategoryManager" @success="handleCategoryUpdate" />

    <a-drawer
      v-model:open="showDetailDrawer"
      title="文档详情"
      placement="right"
      :width="'50%'"
      @close="handleCloseDetail"
    >
      <DocumentDetail v-if="currentDocId" :id="currentDocId" />
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  SettingOutlined,
  UploadOutlined,
  ReloadOutlined,
  FolderOutlined,
  FileTextOutlined,
  FilePdfOutlined,
  FileWordOutlined,
} from '@ant-design/icons-vue'
import { useKnowledgeStore } from '@/stores/knowledge'
import { showSuccess, showError } from '@/utils/message'
import { showDeleteConfirm } from '@/utils/confirm'
import UploadDialog from './components/UploadDialog.vue'
import CategoryManager from './components/CategoryManager.vue'
import DocumentDetail from './detail.vue'

const knowledgeStore = useKnowledgeStore()

const searchKeyword = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const showUploadDialog = ref(false)
const showCategoryManager = ref(false)
const showDetailDrawer = ref(false)
const currentDocId = ref<number | null>(null)

onMounted(async () => {
  await knowledgeStore.fetchCategories()
  await knowledgeStore.fetchDocuments()
})

function handleSearch() {
  knowledgeStore.setSearchKeyword(searchKeyword.value)
}

function handleReset() {
  searchKeyword.value = ''
  knowledgeStore.resetSearch()
}

function handleCategoryClick(selectedKeys: number[]) {
  if (selectedKeys.length > 0) {
    knowledgeStore.setCurrentCategory(selectedKeys[0])
  }
}

function handleSizeChange(_current: number, size: number) {
  pageSize.value = size
  knowledgeStore.setPagination(1, size)
}

function handlePageChange(page: number) {
  knowledgeStore.setPagination(page, pageSize.value)
}

function handleViewDetail(id: number) {
  currentDocId.value = id
  showDetailDrawer.value = true
}

function handleCloseDetail() {
  showDetailDrawer.value = false
  currentDocId.value = null
}

async function handleDelete(id: number) {
  const confirmed = await showDeleteConfirm('该文档')
  if (!confirmed) return

  try {
    await knowledgeStore.removeDocument(id)
    showSuccess('删除成功')
  } catch (error) {
    showError('删除失败')
  }
}

function handleUploadSuccess() {
  showUploadDialog.value = false
  knowledgeStore.fetchDocuments()
}

function handleCategoryUpdate() {
  knowledgeStore.fetchCategories()
}

function getCategoryCount(categoryId: number): number {
  return knowledgeStore.documentList.filter((doc) => doc.categoryId === categoryId).length
}

function getFileIconColor(fileType: string): string {
  const colorMap: Record<string, string> = {
    pdf: '#F56C6C',
    doc: '#409EFF',
    docx: '#409EFF',
    txt: '#67C23A',
  }
  return colorMap[fileType.toLowerCase()] || '#909399'
}

function getStatusColor(status: number): string {
  const colorMap: Record<number, string> = {
    0: 'warning',
    1: 'success',
    2: 'error',
  }
  return colorMap[status] || 'default'
}

function getStatusText(status: number): string {
  const textMap: Record<number, string> = {
    0: '处理中',
    1: '成功',
    2: '失败',
  }
  return textMap[status] || '未知'
}

function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}
</script>

<style scoped lang="scss">
.knowledge-container {
  display: flex;
  gap: 20px;
  height: calc(100vh - 64px - 48px);
  overflow: hidden;

  .category-panel {
    width: 280px;
    flex-shrink: 0;
    height: 100%;

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

    .panel-header {
      display: flex;
      justify-content: space-between;
      align-items: center;

      .panel-title {
        font-family: var(--font-display);
        font-size: 16px;
        font-weight: var(--font-weight-semibold);
      }
    }

    :deep(.ant-tree) {
      flex: 1;
      min-height: 0;
      overflow-y: auto;
    }

    .tree-node {
      display: flex;
      align-items: center;
      gap: 8px;
      width: 100%;

      .count {
        margin-left: auto;
        font-size: 12px;
        color: var(--text-tertiary);
      }
    }
  }

  .content-panel {
    flex: 1;
    min-width: 0;
    height: 100%;

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

    .search-form {
      margin-bottom: 16px;
      display: flex;
      align-items: center;
      flex-shrink: 0;
    }

    .document-list {
      flex: 1;
      min-height: 0;
      overflow-y: auto;
    }

    .document-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
      gap: 16px;
    }

    .document-card {
      background: var(--bg-surface);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-xl);
      padding: 16px;
      cursor: pointer;
      transition: all var(--duration-normal) var(--ease-default);

      &:hover {
        box-shadow: var(--shadow-card-hover);
        border-color: var(--primary-color);
        transform: translateY(-2px);
      }

      .card-icon {
        text-align: center;
        margin-bottom: 12px;
      }

      .card-content {
        .card-title {
          font-size: 14px;
          font-weight: var(--font-weight-medium);
          margin-bottom: 8px;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
          color: var(--text-primary);
        }

        .card-meta {
          display: flex;
          gap: 12px;
          margin-bottom: 8px;
          font-size: 12px;
          color: var(--text-tertiary);

          .file-type {
            padding: 2px 6px;
            background: var(--bg-surface-secondary);
            border-radius: var(--radius-sm);
          }
        }

        .card-status {
          margin-bottom: 8px;
        }

        .card-footer {
          display: flex;
          justify-content: space-between;
          align-items: center;
          padding-top: 8px;
          border-top: 1px solid var(--border-light);

          .time {
            font-size: 12px;
            color: var(--text-tertiary);
          }

          .actions {
            display: flex;
            gap: 8px;
          }
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

@media (max-width: 1200px) {
  .knowledge-container {
    flex-direction: column;
    height: calc(100vh - 64px - 48px);
    overflow-y: auto;

    .category-panel {
      width: 100%;
      height: auto;

      :deep(.ant-card) {
        height: auto;
      }

      :deep(.ant-tree) {
        overflow: visible;
      }
    }

    .content-panel {
      height: auto;

      :deep(.ant-card) {
        height: auto;
      }

      .document-list {
        overflow: visible;
      }

      .document-grid {
        grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
      }
    }
  }
}
</style>
