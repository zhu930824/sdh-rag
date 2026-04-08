<template>
  <div class="knowledge-base-detail-container">
    <!-- 上部：基本信息卡片 -->
    <a-card class="info-card">
      <template #title>
        <div class="card-header">
          <div class="header-left">
            <a-button type="text" @click="handleBack">
              <template #icon><ArrowLeftOutlined /></template>
            </a-button>
            <span class="card-title">{{ knowledgeBase?.name || '知识库详情' }}</span>
            <a-tag :color="knowledgeBase?.status === 1 ? 'green' : 'red'" style="margin-left: 8px">
              {{ knowledgeBase?.status === 1 ? '启用' : '禁用' }}
            </a-tag>
          </div>
          <a-space>
            <a-button @click="handleEdit">
              <template #icon><EditOutlined /></template>
              编辑
            </a-button>
            <a-button type="primary" @click="showLinkDocumentModal = true">
              <template #icon><LinkOutlined /></template>
              关联文档
            </a-button>
          </a-space>
        </div>
      </template>

      <a-row :gutter="24">
        <a-col :span="16">
          <a-descriptions :column="2" bordered size="small">
            <a-descriptions-item label="描述" :span="2">{{ knowledgeBase?.description || '暂无描述' }}</a-descriptions-item>
            <a-descriptions-item label="分块大小">{{ knowledgeBase?.chunkSize || 500 }} 字符</a-descriptions-item>
            <a-descriptions-item label="分块重叠">{{ knowledgeBase?.chunkOverlap || 50 }} 字符</a-descriptions-item>
            <a-descriptions-item label="嵌入模型">{{ knowledgeBase?.embeddingModel || '未设置' }}</a-descriptions-item>
            <a-descriptions-item label="是否公开">{{ knowledgeBase?.isPublic ? '公开' : '私有' }}</a-descriptions-item>
            <a-descriptions-item label="创建时间">{{ knowledgeBase?.createTime }}</a-descriptions-item>
            <a-descriptions-item label="更新时间">{{ knowledgeBase?.updateTime }}</a-descriptions-item>
          </a-descriptions>
        </a-col>
        <a-col :span="8">
          <div class="stats-panel">
            <div class="stats-title">统计概览</div>
            <div class="stats-grid">
              <div class="stat-item">
                <div class="stat-value">{{ detail?.documentCount || 0 }}</div>
                <div class="stat-label">文档数量</div>
              </div>
              <div class="stat-item">
                <div class="stat-value">{{ detail?.chunkCount || 0 }}</div>
                <div class="stat-label">分块数量</div>
              </div>
            </div>
            <div class="tags-section">
              <div class="section-label">标签</div>
              <div class="tags-content">
                <template v-if="tags.length > 0">
                  <a-tag
                    v-for="tag in tags"
                    :key="tag.id"
                    :color="tag.color || 'blue'"
                    closable
                    @close="handleRemoveTag(tag.id)"
                  >
                    {{ tag.name }}
                  </a-tag>
                </template>
                <span v-else class="no-tags">暂无标签</span>
                <a-button type="link" size="small" @click="showAddTagModal = true">
                  <PlusOutlined /> 添加
                </a-button>
              </div>
            </div>
          </div>
        </a-col>
      </a-row>
    </a-card>

    <!-- 下部：内容卡片 -->
    <a-card class="content-card">
      <a-tabs v-model:activeKey="activeTab">
        <a-tab-pane key="documents" tab="关联文档">
          <DocumentList
            ref="documentListRef"
            :knowledge-base-id="knowledgeBaseId"
            @unlink="handleUnlinkDocument"
            @reprocess="handleReprocessDocument"
          />
        </a-tab-pane>
        <a-tab-pane key="chunks" tab="分块管理">
          <ChunkList :knowledge-base-id="knowledgeBaseId" />
        </a-tab-pane>
        <a-tab-pane key="config" tab="配置设置">
          <ConfigPanel
            :knowledge-base="knowledgeBase"
            @save="handleSaveConfig"
          />
        </a-tab-pane>
      </a-tabs>
    </a-card>

    <!-- 关联文档对话框 -->
    <a-modal
      v-model:open="showLinkDocumentModal"
      title="关联文档"
      :width="800"
      ok-text="确认关联"
      cancel-text="取消"
      @ok="handleLinkDocuments"
    >
      <LinkDocumentDialog
        ref="linkDocumentRef"
        :knowledge-base-id="knowledgeBaseId"
        :selected-ids="selectedDocumentIds"
        @selection-change="selectedDocumentIds = $event"
      />
    </a-modal>

    <!-- 添加标签对话框 -->
    <a-modal
      v-model:open="showAddTagModal"
      title="添加标签"
      ok-text="确认"
      cancel-text="取消"
      @ok="handleAddTag"
    >
      <a-select
        v-model:value="selectedTagId"
        placeholder="选择标签"
        style="width: 100%"
        :options="availableTags.map(t => ({ value: t.id, label: t.name }))"
      />
    </a-modal>

    <!-- 编辑知识库对话框 -->
    <a-modal
      v-model:open="showEditModal"
      title="编辑知识库"
      :width="520"
      ok-text="保存"
      cancel-text="取消"
      @ok="handleEditSubmit"
    >
      <a-form :model="editForm" :label-col="{ span: 5 }" :wrapper-col="{ span: 18 }">
        <a-form-item label="名称" required>
          <a-input v-model:value="editForm.name" placeholder="请输入知识库名称" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="editForm.description" placeholder="请输入描述" :rows="3" />
        </a-form-item>
        <a-form-item label="是否公开">
          <a-switch v-model:checked="editForm.isPublic" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  ArrowLeftOutlined,
  EditOutlined,
  LinkOutlined,
  PlusOutlined,
} from '@ant-design/icons-vue'
import {
  getKnowledgeBaseFullDetail,
  updateKnowledgeBase,
  updateKnowledgeBaseConfig,
  linkDocumentsToKnowledgeBase,
  unlinkDocumentFromKnowledgeBase,
  reprocessDocument,
  addKnowledgeBaseTag,
  removeKnowledgeBaseTag,
  type KnowledgeBase,
  type KnowledgeBaseDetail,
  type Tag,
} from '@/api/knowledgeBase'
import { getAllTags } from '@/api/tag'
import DocumentList from './components/DocumentList.vue'
import ChunkList from './components/ChunkList.vue'
import ConfigPanel from './components/ConfigPanel.vue'
import LinkDocumentDialog from './components/LinkDocumentDialog.vue'

const route = useRoute()
const router = useRouter()

const knowledgeBaseId = computed(() => Number(route.params.id))

const loading = ref(false)
const knowledgeBase = ref<KnowledgeBase | null>(null)
const detail = ref<KnowledgeBaseDetail | null>(null)
const tags = ref<Tag[]>([])
const allTags = ref<Tag[]>([])

const activeTab = ref('documents')
const showLinkDocumentModal = ref(false)
const showAddTagModal = ref(false)
const showEditModal = ref(false)

const selectedDocumentIds = ref<number[]>([])
const selectedTagId = ref<number | undefined>(undefined)

const editForm = reactive({
  name: '',
  description: '',
  isPublic: false,
})

const documentListRef = ref()
const linkDocumentRef = ref()

const availableTags = computed(() => {
  const tagIds = tags.value.map(t => t.id)
  return allTags.value.filter(t => !tagIds.includes(t.id))
})

async function loadDetail() {
  loading.value = true
  try {
    const res = await getKnowledgeBaseFullDetail(knowledgeBaseId.value)
    if (res.code === 200 && res.data) {
      knowledgeBase.value = res.data.knowledgeBase
      detail.value = res.data
      tags.value = res.data.tags || []
    }
  } catch (error) {
    message.error('加载知识库详情失败')
  } finally {
    loading.value = false
  }
}

async function loadAllTags() {
  try {
    const res = await getAllTags()
    if (res.code === 200) {
      allTags.value = res.data || []
    }
  } catch (error) {
    console.error('加载标签列表失败', error)
  }
}

function handleBack() {
  router.push('/knowledge-base')
}

function handleEdit() {
  if (knowledgeBase.value) {
    editForm.name = knowledgeBase.value.name
    editForm.description = knowledgeBase.value.description || ''
    editForm.isPublic = knowledgeBase.value.isPublic || false
  }
  showEditModal.value = true
}

async function handleEditSubmit() {
  if (!editForm.name) {
    message.warning('请输入知识库名称')
    return
  }
  try {
    await updateKnowledgeBase(knowledgeBaseId.value, {
      name: editForm.name,
      description: editForm.description,
      isPublic: editForm.isPublic,
      icon: knowledgeBase.value?.icon || 'KB',
      color: knowledgeBase.value?.color || '#1890ff',
      embeddingModel: knowledgeBase.value?.embeddingModel || '',
    })
    message.success('更新成功')
    showEditModal.value = false
    loadDetail()
  } catch (error) {
    message.error('更新失败')
  }
}

async function handleLinkDocuments() {
  if (selectedDocumentIds.value.length === 0) {
    message.warning('请选择要关联的文档')
    return
  }
  try {
    // 获取配置
    const configs = linkDocumentRef.value?.getConfigs()

    if (configs && configs.length > 0) {
      await linkDocumentsToKnowledgeBase(knowledgeBaseId.value, { configs })
    } else {
      await linkDocumentsToKnowledgeBase(knowledgeBaseId.value, { documentIds: selectedDocumentIds.value })
    }
    message.success('关联成功')
    showLinkDocumentModal.value = false
    selectedDocumentIds.value = []
    loadDetail()
    documentListRef.value?.loadDocuments()
  } catch (error) {
    message.error('关联失败')
  }
}

async function handleUnlinkDocument(documentId: number) {
  try {
    await unlinkDocumentFromKnowledgeBase(knowledgeBaseId.value, documentId)
    message.success('移除成功')
    loadDetail()
  } catch (error) {
    message.error('移除失败')
  }
}

async function handleReprocessDocument(documentId: number) {
  try {
    await reprocessDocument(knowledgeBaseId.value, documentId)
    message.success('已提交重新处理')
    documentListRef.value?.loadDocuments()
  } catch (error) {
    message.error('重新处理失败')
  }
}

async function handleAddTag() {
  if (!selectedTagId.value) {
    message.warning('请选择标签')
    return
  }
  try {
    await addKnowledgeBaseTag(knowledgeBaseId.value, selectedTagId.value)
    message.success('添加成功')
    showAddTagModal.value = false
    selectedTagId.value = undefined
    loadDetail()
  } catch (error) {
    message.error('添加失败')
  }
}

async function handleRemoveTag(tagId: number) {
  try {
    await removeKnowledgeBaseTag(knowledgeBaseId.value, tagId)
    message.success('移除成功')
    loadDetail()
  } catch (error) {
    message.error('移除失败')
  }
}

async function handleSaveConfig(config: {
  chunkSize: number
  chunkOverlap: number
  embeddingModel: string
  rankModel?: string
  enableRewrite?: boolean
  similarityThreshold?: number
  keywordTopK?: number
  vectorTopK?: number
  keywordWeight?: number
  vectorWeight?: number
}) {
  try {
    await updateKnowledgeBaseConfig(knowledgeBaseId.value, config)
    message.success('配置保存成功')
    loadDetail()
  } catch (error) {
    message.error('保存失败')
  }
}

onMounted(() => {
  loadDetail()
  loadAllTags()
})
</script>

<style scoped lang="scss">
.knowledge-base-detail-container {
  height: calc(100vh - 64px - 48px);
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 16px;

  .info-card {
    flex-shrink: 0;

    :deep(.ant-card-head-title) {
      width: 100%;
    }

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;

      .header-left {
        display: flex;
        align-items: center;
        gap: 8px;
      }

      .card-title {
        font-family: var(--font-display);
        font-size: 18px;
        font-weight: var(--font-weight-semibold);
      }
    }

    .stats-panel {
      padding: 16px;
      background: var(--bg-page);
      border-radius: var(--radius-lg);
      height: 100%;

      .stats-title {
        font-weight: 600;
        font-size: 15px;
        color: var(--text-primary);
        margin-bottom: 16px;
      }

      .stats-grid {
        display: grid;
        grid-template-columns: repeat(2, 1fr);
        gap: 16px;
        margin-bottom: 16px;

        .stat-item {
          text-align: center;
          padding: 12px;
          background: var(--bg-surface);
          border-radius: var(--radius-base);

          .stat-value {
            font-size: 24px;
            font-weight: 600;
            color: var(--primary-color);
            line-height: 1.2;
          }

          .stat-label {
            font-size: 12px;
            color: var(--text-secondary);
            margin-top: 4px;
          }
        }
      }

      .tags-section {
        .section-label {
          font-size: 13px;
          color: var(--text-secondary);
          margin-bottom: 8px;
        }

        .tags-content {
          display: flex;
          align-items: center;
          gap: 6px;
          flex-wrap: wrap;

          .no-tags {
            color: var(--text-tertiary);
            font-size: 13px;
          }
        }
      }
    }
  }

  .content-card {
    flex: none;
    min-height: 500px;

    :deep(.ant-card-body) {
      padding-top: 12px;
    }
  }
}
</style>
