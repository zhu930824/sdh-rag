<template>
  <div class="document-detail">
    <template v-if="loading">
      <a-skeleton active :paragraph="{ rows: 5 }" />
    </template>

    <template v-else-if="document">
      <a-card :bordered="false" class="info-card">
        <template #title>
          <div class="card-header">
            <span class="title">{{ document.title }}</span>
            <a-tag :color="getStatusColor(document.status)">
              {{ getStatusText(document.status) }}
            </a-tag>
          </div>
        </template>

        <a-descriptions :column="2" bordered>
          <a-descriptions-item label="文件类型">
            <a-tag>{{ document.fileType.toUpperCase() }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="文件大小">
            {{ formatFileSize(document.fileSize) }}
          </a-descriptions-item>
          <a-descriptions-item label="所属分类">
            {{ getCategoryName(document.categoryId) }}
          </a-descriptions-item>
          <a-descriptions-item label="创建时间">
            {{ formatDate(document.createTime) }}
          </a-descriptions-item>
          <a-descriptions-item label="更新时间">
            {{ formatDate(document.updateTime) }}
          </a-descriptions-item>
          <a-descriptions-item label="文件路径">
            <span class="file-path">{{ document.filePath }}</span>
          </a-descriptions-item>
        </a-descriptions>
      </a-card>

      <a-card :bordered="false" class="content-card">
        <template #title>
          <div class="section-title">
            <FileTextOutlined />
            <span>文档内容</span>
          </div>
        </template>
        <div class="content-preview">
          <a-scrollbar style="height: 400px">
            <div class="content-text">{{ document.content || '暂无内容预览' }}</div>
          </a-scrollbar>
        </div>
      </a-card>

      <a-card v-if="relatedDocuments.length > 0" :bordered="false" class="related-card">
        <template #title>
          <div class="section-title">
            <LinkOutlined />
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
            <FileTextOutlined :style="{ color: getFileIconColor(doc.fileType) }" />
            <div class="item-info">
              <div class="item-title">{{ doc.title }}</div>
              <div class="item-meta">
                <span>{{ doc.fileType.toUpperCase() }}</span>
                <span>{{ formatFileSize(doc.fileSize) }}</span>
              </div>
            </div>
          </div>
        </div>
      </a-card>
    </template>

    <a-empty v-else description="文档不存在或已被删除" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { FileTextOutlined, LinkOutlined } from '@ant-design/icons-vue'
import { getDocumentDetail, getRelatedDocuments } from '@/api/knowledge'
import type { KnowledgeDocument } from '@/api/knowledge'
import { useKnowledgeStore } from '@/stores/knowledge'
import { showError } from '@/utils/message'

const props = defineProps<{
  id: number
}>()

const emit = defineEmits<{
  viewRelated: [id: number]
}>()

const knowledgeStore = useKnowledgeStore()

const loading = ref(false)
const document = ref<KnowledgeDocument | null>(null)
const relatedDocuments = ref<KnowledgeDocument[]>([])

watch(
  () => props.id,
  () => {
    fetchDocumentDetail()
  }
)

onMounted(() => {
  fetchDocumentDetail()
})

async function fetchDocumentDetail() {
  loading.value = true
  try {
    const result = await getDocumentDetail(props.id)
    document.value = result.data

    const relatedResult = await getRelatedDocuments(props.id)
    relatedDocuments.value = relatedResult.data
  } catch (error) {
    showError('获取文档详情失败')
  } finally {
    loading.value = false
  }
}

function handleViewRelated(id: number) {
  emit('viewRelated', id)
}

function getCategoryName(categoryId: number): string {
  const category = knowledgeStore.categoryList.find((c) => c.id === categoryId)
  return category?.name || '未分类'
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

    .file-path {
      font-size: 12px;
      color: #909399;
      word-break: break-all;
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
