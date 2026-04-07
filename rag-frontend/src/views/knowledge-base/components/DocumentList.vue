<template>
  <div class="document-list">
    <a-table
      :loading="loading"
      :data-source="documents"
      :columns="columns"
      :pagination="false"
      row-key="id"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="getStatusColor(record.processStatus)">
            {{ getStatusText(record.processStatus) }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <a-popconfirm
            title="确定要移除该文档吗？"
            @confirm="handleUnlink(record.id)"
          >
            <a-button type="link" size="small" danger>移除</a-button>
          </a-popconfirm>
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
        @change="loadDocuments"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { getKnowledgeBaseDocuments } from '@/api/knowledgeBase'

const props = defineProps<{
  knowledgeBaseId: number
}>()

const emit = defineEmits<{
  (e: 'unlink', documentId: number): void
}>()

const loading = ref(false)
const documents = ref<any[]>([])

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
})

const columns = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '文档名称', dataIndex: 'title', ellipsis: true },
  { title: '文件类型', dataIndex: 'fileType', width: 100 },
  { title: '处理状态', dataIndex: 'processStatus', width: 100 },
  { title: '分块数', dataIndex: 'chunkCount', width: 100 },
  { title: '操作', dataIndex: 'action', width: 100 },
]

async function loadDocuments() {
  loading.value = true
  try {
    const res = await getKnowledgeBaseDocuments(props.knowledgeBaseId, {
      page: pagination.current,
      pageSize: pagination.pageSize,
    })
    if (res.code === 200) {
      documents.value = res.data?.records || []
      pagination.total = res.data?.total || 0
    }
  } finally {
    loading.value = false
  }
}

function getStatusColor(status: number): string {
  const colors: Record<number, string> = {
    0: 'warning',
    1: 'processing',
    2: 'success',
    3: 'error',
  }
  return colors[status] || 'default'
}

function getStatusText(status: number): string {
  const texts: Record<number, string> = {
    0: '待处理',
    1: '处理中',
    2: '成功',
    3: '失败',
  }
  return texts[status] || '未知'
}

function handleUnlink(documentId: number) {
  emit('unlink', documentId)
}

watch(() => props.knowledgeBaseId, loadDocuments, { immediate: true })
</script>

<style scoped lang="scss">
.document-list {
  height: 100%;
  display: flex;
  flex-direction: column;

  .pagination {
    margin-top: 16px;
    display: flex;
    justify-content: flex-end;
  }
}
</style>
