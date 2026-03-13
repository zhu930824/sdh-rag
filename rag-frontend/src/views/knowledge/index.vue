<template>
  <div class="knowledge-container">
    <!-- 左侧分类面板 -->
    <div class="category-panel">
      <el-card shadow="never">
        <template #header>
          <div class="panel-header">
            <span class="panel-title">文档分类</span>
            <el-button type="primary" link :icon="Plus" @click="showCategoryManager = true">
              管理
            </el-button>
          </div>
        </template>
        <el-tree
          :data="knowledgeStore.categoryTree"
          :props="{ label: 'name', children: 'children' }"
          node-key="id"
          highlight-current
          default-expand-all
          @node-click="handleCategoryClick"
        >
          <template #default="{ data }">
            <span class="tree-node">
              <el-icon><Folder /></el-icon>
              <span>{{ data.name }}</span>
              <span class="count">{{ getCategoryCount(data.id) }}</span>
            </span>
          </template>
        </el-tree>
      </el-card>
    </div>

    <!-- 右侧内容区 -->
    <div class="content-panel">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span class="card-title">文档列表</span>
            <el-button type="primary" :icon="Upload" @click="showUploadDialog = true">
              上传文档
            </el-button>
          </div>
        </template>

        <!-- 搜索栏 -->
        <el-form :inline="true" class="search-form">
          <el-form-item>
            <el-input
              v-model="searchKeyword"
              placeholder="搜索文档标题或内容"
              clearable
              :prefix-icon="Search"
              style="width: 300px"
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
            <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>

        <!-- 文档列表 -->
        <div v-loading="knowledgeStore.loading" class="document-list">
          <!-- 空状态 -->
          <el-empty v-if="!knowledgeStore.loading && knowledgeStore.documentList.length === 0" description="暂无文档数据">
            <el-button type="primary" @click="showUploadDialog = true">上传文档</el-button>
          </el-empty>

          <!-- 文档卡片列表 -->
          <div v-else class="document-grid">
            <div
              v-for="doc in knowledgeStore.documentList"
              :key="doc.id"
              class="document-card"
              @click="handleViewDetail(doc.id)"
            >
              <div class="card-icon">
                <el-icon :size="40" :color="getFileIconColor(doc.fileType)">
                  <component :is="getFileIcon(doc.fileType)" />
                </el-icon>
              </div>
              <div class="card-content">
                <div class="card-title">{{ doc.title }}</div>
                <div class="card-meta">
                  <span class="file-type">{{ doc.fileType.toUpperCase() }}</span>
                  <span class="file-size">{{ formatFileSize(doc.fileSize) }}</span>
                </div>
                <div class="card-status">
                  <el-tag :type="getStatusType(doc.status)" size="small">
                    {{ getStatusText(doc.status) }}
                  </el-tag>
                </div>
                <div class="card-footer">
                  <span class="time">{{ formatDate(doc.createTime) }}</span>
                  <div class="actions" @click.stop>
                    <el-button type="primary" link size="small" @click="handleViewDetail(doc.id)">
                      查看
                    </el-button>
                    <el-button type="danger" link size="small" @click="handleDelete(doc.id)">
                      删除
                    </el-button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 分页 -->
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="knowledgeStore.pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          class="pagination"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </el-card>
    </div>

    <!-- 上传对话框 -->
    <UploadDialog v-model="showUploadDialog" @success="handleUploadSuccess" />

    <!-- 分类管理对话框 -->
    <CategoryManager v-model="showCategoryManager" @success="handleCategoryUpdate" />

    <!-- 文档详情抽屉 -->
    <el-drawer
      v-model="showDetailDrawer"
      title="文档详情"
      direction="rtl"
      size="50%"
      :before-close="handleCloseDetail"
    >
      <DocumentDetail v-if="currentDocId" :id="currentDocId" />
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus,
  Upload,
  Search,
  Refresh,
  Folder,
  Document,
  Tickets,
} from '@element-plus/icons-vue'
import { useKnowledgeStore } from '@/stores/knowledge'
import UploadDialog from './components/UploadDialog.vue'
import CategoryManager from './components/CategoryManager.vue'
import DocumentDetail from './detail.vue'

const knowledgeStore = useKnowledgeStore()

// 搜索关键词
const searchKeyword = ref('')
// 当前页
const currentPage = ref(1)
// 每页大小
const pageSize = ref(10)
// 显示上传对话框
const showUploadDialog = ref(false)
// 显示分类管理
const showCategoryManager = ref(false)
// 显示详情抽屉
const showDetailDrawer = ref(false)
// 当前文档ID
const currentDocId = ref<number | null>(null)

// 初始化
onMounted(async () => {
  await knowledgeStore.fetchCategories()
  await knowledgeStore.fetchDocuments()
})

// 搜索
function handleSearch() {
  knowledgeStore.setSearchKeyword(searchKeyword.value)
}

// 重置
function handleReset() {
  searchKeyword.value = ''
  knowledgeStore.resetSearch()
}

// 分类点击
function handleCategoryClick(data: { id: number }) {
  knowledgeStore.setCurrentCategory(data.id)
}

// 分页大小改变
function handleSizeChange(size: number) {
  pageSize.value = size
  knowledgeStore.setPagination(1, size)
}

// 页码改变
function handlePageChange(page: number) {
  knowledgeStore.setPagination(page, pageSize.value)
}

// 查看详情
function handleViewDetail(id: number) {
  currentDocId.value = id
  showDetailDrawer.value = true
}

// 关闭详情
function handleCloseDetail() {
  showDetailDrawer.value = false
  currentDocId.value = null
}

// 删除文档
async function handleDelete(id: number) {
  try {
    await ElMessageBox.confirm('确定要删除该文档吗？删除后无法恢复', '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await knowledgeStore.removeDocument(id)
    ElMessage.success('删除成功')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// 上传成功
function handleUploadSuccess() {
  showUploadDialog.value = false
  knowledgeStore.fetchDocuments()
}

// 分类更新
function handleCategoryUpdate() {
  knowledgeStore.fetchCategories()
}

// 获取分类文档数量
function getCategoryCount(categoryId: number): number {
  return knowledgeStore.documentList.filter((doc) => doc.categoryId === categoryId).length
}

// 获取文件图标
function getFileIcon(fileType: string) {
  const iconMap: Record<string, typeof Document> = {
    pdf: Tickets,
    doc: Document,
    docx: Document,
    txt: Document,
  }
  return iconMap[fileType.toLowerCase()] || Document
}

// 获取文件图标颜色
function getFileIconColor(fileType: string): string {
  const colorMap: Record<string, string> = {
    pdf: '#F56C6C',
    doc: '#409EFF',
    docx: '#409EFF',
    txt: '#67C23A',
  }
  return colorMap[fileType.toLowerCase()] || '#909399'
}

// 获取状态类型
function getStatusType(status: number): '' | 'success' | 'warning' | 'danger' | 'info' {
  const typeMap: Record<number, '' | 'success' | 'warning' | 'danger' | 'info'> = {
    0: 'warning',
    1: 'success',
    2: 'danger',
  }
  return typeMap[status] || 'info'
}

// 获取状态文本
function getStatusText(status: number): string {
  const textMap: Record<number, string> = {
    0: '处理中',
    1: '成功',
    2: '失败',
  }
  return textMap[status] || '未知'
}

// 格式化文件大小
function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

// 格式化日期
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
  gap: 16px;
  height: calc(100vh - 120px);
  padding: 16px;

  .category-panel {
    width: 280px;
    flex-shrink: 0;

    .panel-header {
      display: flex;
      justify-content: space-between;
      align-items: center;

      .panel-title {
        font-size: 16px;
        font-weight: 500;
      }
    }

    .tree-node {
      display: flex;
      align-items: center;
      gap: 8px;
      width: 100%;

      .count {
        margin-left: auto;
        font-size: 12px;
        color: #909399;
      }
    }
  }

  .content-panel {
    flex: 1;
    overflow: hidden;

    .el-card {
      height: 100%;
      display: flex;
      flex-direction: column;

      :deep(.el-card__body) {
        flex: 1;
        overflow: auto;
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

    .search-form {
      margin-bottom: 16px;
    }

    .document-list {
      min-height: 400px;
    }

    .document-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
      gap: 16px;
    }

    .document-card {
      background: #fff;
      border: 1px solid #e4e7ed;
      border-radius: 8px;
      padding: 16px;
      cursor: pointer;
      transition: all 0.3s;

      &:hover {
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
        border-color: #409eff;
      }

      .card-icon {
        text-align: center;
        margin-bottom: 12px;
      }

      .card-content {
        .card-title {
          font-size: 14px;
          font-weight: 500;
          margin-bottom: 8px;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .card-meta {
          display: flex;
          gap: 12px;
          margin-bottom: 8px;
          font-size: 12px;
          color: #909399;

          .file-type {
            padding: 2px 6px;
            background: #f4f4f5;
            border-radius: 4px;
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
          border-top: 1px solid #f0f0f0;

          .time {
            font-size: 12px;
            color: #909399;
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
      justify-content: flex-end;
    }
  }
}

// 响应式设计
@media (max-width: 1200px) {
  .knowledge-container {
    flex-direction: column;
    height: auto;

    .category-panel {
      width: 100%;
    }

    .content-panel {
      .document-grid {
        grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
      }
    }
  }
}
</style>
