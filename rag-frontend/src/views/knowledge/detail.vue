<template>
  <div v-loading="loading" class="document-detail">
    <!-- 加载骨架屏 -->
    <template v-if="loading">
      <el-skeleton :rows="5" animated />
    </template>

    <!-- 文档内容 -->
    <template v-else-if="document">
      <!-- 基本信息 -->
      <el-card shadow="never" class="info-card">
        <template #header>
          <div class="card-header">
            <span class="title">{{ document.title }}</span>
            <el-tag :type="getStatusType(document.status)" size="large">
              {{ getStatusText(document.status) }}
            </el-tag>
          </div>
        </template>

        <el-descriptions :column="2" border>
          <el-descriptions-item label="文件类型">
            <el-tag size="small">{{ document.fileType.toUpperCase() }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="文件大小">
            {{ formatFileSize(document.fileSize) }}
          </el-descriptions-item>
          <el-descriptions-item label="所属分类">
            {{ getCategoryName(document.categoryId) }}
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">
            {{ formatDate(document.createTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="更新时间">
            {{ formatDate(document.updateTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="文件路径">
            <el-text type="info" size="small">{{ document.filePath }}</el-text>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 文档内容预览 -->
      <el-card shadow="never" class="content-card">
        <template #header>
          <div class="section-title">
          <el-icon><Document /></el-icon>
          <span>文档内容</span>
        </div>
        </template>
        <div class="content-preview">
          <el-scrollbar height="400px">
            <div class="content-text">{{ document.content || '暂无内容预览' }}</div>
          </el-scrollbar>
        </div>
      </el-card>

      <!-- 相关文档推荐 -->
      <el-card v-if="relatedDocuments.length > 0" shadow="never" class="related-card">
        <template #header>
          <div class="section-title">
            <el-icon><Connection /></el-icon>
            <span>相关文档</span>
          </div>
        </template>
        <div class="related-list">
          <div
            v-for="doc in relatedDocuments"
            :key="doc.id"
            class="related-item"
            @click="handleViewRelated(doc.id)"
          >
            <el-icon :color="getFileIconColor(doc.fileType)">
              <component :is="getFileIcon(doc.fileType)" />
            </el-icon>
            <div class="item-info">
              <div class="item-title">{{ doc.title }}</div>
              <div class="item-meta">
                <span>{{ doc.fileType.toUpperCase() }}</span>
                <span>{{ formatFileSize(doc.fileSize) }}</span>
              </div>
            </div>
          </div>
        </div>
      </el-card>
    </template>

    <!-- 错误状态 -->
    <el-empty v-else description="文档不存在或已被删除" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, Connection, Tickets } from '@element-plus/icons-vue'
import { getDocumentDetail, getRelatedDocuments } from '@/api/knowledge'
import type { KnowledgeDocument } from '@/api/knowledge'
import { useKnowledgeStore } from '@/stores/knowledge'

const props = defineProps<{
  id: number
}>()

const emit = defineEmits<{
  viewRelated: [id: number]
}>()

const knowledgeStore = useKnowledgeStore()

// 加载状态
const loading = ref(false)
// 文档详情
const document = ref<KnowledgeDocument | null>(null)
// 相关文档
const relatedDocuments = ref<KnowledgeDocument[]>([])

// 监听ID变化
watch(
  () => props.id,
  () => {
    fetchDocumentDetail()
  }
)

// 初始化
onMounted(() => {
  fetchDocumentDetail()
})

// 获取文档详情
async function fetchDocumentDetail() {
  loading.value = true
  try {
    const result = await getDocumentDetail(props.id)
    document.value = result.data

    // 获取相关文档
    const relatedResult = await getRelatedDocuments(props.id)
    relatedDocuments.value = relatedResult.data
  } catch (error) {
    ElMessage.error('获取文档详情失败')
  } finally {
    loading.value = false
  }
}

// 查看相关文档
function handleViewRelated(id: number) {
  emit('viewRelated', id)
}

// 获取分类名称
function getCategoryName(categoryId: number): string {
  const category = knowledgeStore.categoryList.find((c) => c.id === categoryId)
  return category?.name || '未分类'
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
.document-detail {
  .info-card {
    margin-bottom: 16px;

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;

      .title {
        font-size: 18px;
        font-weight: 600;
      }
    }
  }

  .content-card,
  .related-card {
    margin-bottom: 16px;
  }

  .section-title {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 16px;
    font-weight: 500;
  }

  .content-preview {
    background: #f5f7fa;
    border-radius: 4px;
    padding: 16px;

    .content-text {
      line-height: 1.8;
      white-space: pre-wrap;
      word-break: break-word;
    }
  }

  .related-list {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 12px;

    .related-item {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px;
      background: #f5f7fa;
      border-radius: 4px;
      cursor: pointer;
      transition: all 0.3s;

      &:hover {
        background: #e6f7ff;
      }

      .item-info {
        flex: 1;
        min-width: 0;

        .item-title {
          font-size: 14px;
          font-weight: 500;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .item-meta {
          display: flex;
          gap: 8px;
          margin-top: 4px;
          font-size: 12px;
          color: #909399;
        }
      }
    }
  }
}
</style>
