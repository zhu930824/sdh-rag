<template>
  <div class="link-document-dialog">
    <div class="search-bar">
      <a-input-search
        v-model:value="searchKeyword"
        placeholder="搜索文档名称"
        style="width: 300px"
        @search="loadDocuments"
      />
    </div>

    <a-table
      :loading="loading"
      :data-source="documents"
      :columns="columns"
      :pagination="false"
      :row-selection="rowSelection"
      row-key="id"
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

    <div class="selection-info">
      已选择 <span class="count">{{ selectedRowKeys.length }}</span> 个文档
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { getAvailableDocuments } from '@/api/knowledgeBase'

const props = defineProps<{
  knowledgeBaseId: number
  selectedIds: number[]
}>()

const emit = defineEmits<{
  (e: 'selection-change', ids: number[]): void
}>()

const loading = ref(false)
const documents = ref<any[]>([])
const searchKeyword = ref('')
const selectedRowKeys = ref<number[]>([])

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
})

const columns = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '文档名称', dataIndex: 'title', ellipsis: true },
  { title: '文件类型', dataIndex: 'fileType', width: 100 },
  { title: '文件大小', dataIndex: 'fileSize', width: 100 },
  { title: '上传时间', dataIndex: 'createTime', width: 180 },
]

const rowSelection = computed(() => ({
  selectedRowKeys: selectedRowKeys.value,
  onChange: (keys: number[]) => {
    selectedRowKeys.value = keys
    emit('selection-change', keys)
  },
}))

async function loadDocuments() {
  loading.value = true
  try {
    const res = await getAvailableDocuments({
      page: pagination.current,
      pageSize: pagination.pageSize,
      excludeKnowledgeId: props.knowledgeBaseId,
    })
    if (res.code === 200) {
      documents.value = res.data?.records || []
      pagination.total = res.data?.total || 0
    }
  } finally {
    loading.value = false
  }
}

function getFileTypeColor(fileType: string): string {
  const colors: Record<string, string> = {
    pdf: 'red',
    doc: 'blue',
    docx: 'blue',
    txt: 'green',
    xls: 'green',
    xlsx: 'green',
  }
  return colors[fileType?.toLowerCase()] || 'default'
}

function formatFileSize(bytes: number): string {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

watch(() => props.selectedIds, (ids) => {
  selectedRowKeys.value = ids || []
}, { immediate: true })

onMounted(loadDocuments)
</script>

<style scoped lang="scss">
.link-document-dialog {
  .search-bar {
    margin-bottom: 16px;
  }

  .pagination {
    margin-top: 16px;
    display: flex;
    justify-content: flex-end;
  }

  .selection-info {
    margin-top: 16px;
    padding: 8px 12px;
    background: var(--bg-page);
    border-radius: var(--radius-base);
    font-size: 13px;

    .count {
      color: var(--primary-color);
      font-weight: 600;
    }
  }
}
</style>