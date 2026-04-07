<template>
  <div class="chunk-list">
    <div class="search-bar">
      <a-input-search
        v-model:value="searchKeyword"
        placeholder="搜索分块内容"
        style="width: 300px"
        @search="loadChunks"
      />
    </div>

    <a-table
      :loading="loading"
      :data-source="chunks"
      :columns="columns"
      :pagination="false"
      row-key="id"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'content'">
          <a-tooltip :title="record.content">
            <span class="content-preview">{{ record.contentPreview || record.content?.slice(0, 100) + '...' }}</span>
          </a-tooltip>
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <a-button type="link" size="small" @click="showChunkDetail(record)">查看详情</a-button>
        </template>
      </template>
    </a-table>

    <div class="pagination">
      <a-pagination
        v-model:current="pagination.current"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        show-size-changer
        :show-total="(total: number) => `共 ${total} 条`"
        @change="loadChunks"
      />
    </div>

    <!-- 分块详情抽屉 -->
    <a-drawer
      v-model:open="detailVisible"
      title="分块详情"
      :width="500"
    >
      <div v-if="currentChunk" class="chunk-detail">
        <a-descriptions :column="1" bordered>
          <a-descriptions-item label="文档">{{ currentChunk.documentTitle }}</a-descriptions-item>
          <a-descriptions-item label="分块索引">{{ currentChunk.chunkIndex }}</a-descriptions-item>
          <a-descriptions-item label="分块大小">{{ currentChunk.chunkSize }} 字符</a-descriptions-item>
          <a-descriptions-item label="向量ID">{{ currentChunk.vectorId || '未生成' }}</a-descriptions-item>
          <a-descriptions-item label="创建时间">{{ currentChunk.createTime }}</a-descriptions-item>
        </a-descriptions>

        <a-divider />

        <div class="content-section">
          <div class="section-title">分块内容</div>
          <div class="content-box">{{ currentChunk.content }}</div>
        </div>
      </div>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { getKnowledgeBaseChunks, type KnowledgeChunk } from '@/api/knowledgeBase'

const props = defineProps<{
  knowledgeBaseId: number
}>()

const loading = ref(false)
const chunks = ref<KnowledgeChunk[]>([])
const searchKeyword = ref('')

const pagination = reactive({
  current: 1,
  pageSize: 20,
  total: 0,
})

const detailVisible = ref(false)
const currentChunk = ref<KnowledgeChunk | null>(null)

const columns = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '文档', dataIndex: 'documentTitle', width: 200, ellipsis: true },
  { title: '索引', dataIndex: 'chunkIndex', width: 80 },
  { title: '内容', dataIndex: 'content', ellipsis: true },
  { title: '大小', dataIndex: 'chunkSize', width: 100 },
  { title: '操作', dataIndex: 'action', width: 100 },
]

async function loadChunks() {
  loading.value = true
  try {
    const res = await getKnowledgeBaseChunks(props.knowledgeBaseId, {
      page: pagination.current,
      pageSize: pagination.pageSize,
    })
    if (res.code === 200) {
      chunks.value = res.data?.records || []
      pagination.total = res.data?.total || 0
    }
  } finally {
    loading.value = false
  }
}

function showChunkDetail(chunk: KnowledgeChunk) {
  currentChunk.value = chunk
  detailVisible.value = true
}

watch(() => props.knowledgeBaseId, loadChunks, { immediate: true })
</script>

<style scoped lang="scss">
.chunk-list {
  height: 100%;
  display: flex;
  flex-direction: column;

  .search-bar {
    margin-bottom: 16px;
    flex-shrink: 0;
  }

  .content-preview {
    color: var(--text-secondary);
    font-size: 13px;
  }

  .pagination {
    margin-top: 16px;
    display: flex;
    justify-content: flex-end;
  }

  .chunk-detail {
    .content-section {
      .section-title {
        font-weight: 500;
        margin-bottom: 8px;
        color: var(--text-primary);
      }

      .content-box {
        padding: 12px;
        background: var(--bg-page);
        border-radius: var(--radius-base);
        font-size: 14px;
        line-height: 1.7;
        max-height: 400px;
        overflow-y: auto;
        white-space: pre-wrap;
      }
    }
  }
}
</style>