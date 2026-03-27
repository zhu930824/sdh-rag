<template>
  <div class="nlp-query-page">
    <div class="page-header">
      <h2>自然语言查询</h2>
      <p class="description">用自然语言描述您的查询需求，AI将自动转换为SQL查询</p>
    </div>

    <div class="query-container">
      <a-card :bordered="false" class="query-card">
        <a-form layout="vertical">
          <a-form-item label="查询需求">
            <a-textarea
              v-model:value="question"
              :rows="4"
              placeholder="例如：查询最近一周创建的用户列表"
              :maxlength="500"
              show-count
            />
          </a-form-item>
          <a-form-item>
            <a-button type="primary" :loading="loading" @click="handleQuery">
              <template #icon><SearchOutlined /></template>
              执行查询
            </a-button>
            <a-button @click="handleClear" style="margin-left: 8px">
              清空
            </a-button>
          </a-form-item>
        </a-form>

        <div v-if="sqlResult" class="result-section">
          <a-divider />
          <div class="sql-display">
            <div class="section-title">
              <DatabaseOutlined />
              <span>生成的SQL</span>
            </div>
            <pre class="sql-code">{{ sqlResult }}</pre>
          </div>

          <div v-if="queryResults.length > 0" class="results-display">
            <div class="section-title">
              <TableOutlined />
              <span>查询结果</span>
              <span class="result-count">({{ queryResults.length }} 条)</span>
            </div>
            <a-table
              :columns="resultColumns"
              :data-source="queryResults"
              :pagination="false"
              :scroll="{ x: 'max-content' }"
              size="small"
            />
          </div>
          <div v-else-if="sqlResult && !loading" class="no-result">
            <EmptyOutlined />
            <span>暂无查询结果</span>
          </div>
        </div>
      </a-card>
    </div>

    <div class="schema-info">
      <a-card title="数据表结构" :bordered="false">
        <a-tabs v-model:activeKey="activeTable">
          <a-tab-pane v-for="table in tables" :key="table.name" :tab="table.comment">
            <a-table
              :columns="schemaColumns"
              :data-source="tableSchemas[table.name] || []"
              :pagination="false"
              size="small"
              :scroll="{ x: 'max-content' }"
            />
          </a-tab-pane>
        </a-tabs>
      </a-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { SearchOutlined, DatabaseOutlined, TableOutlined, EmptyOutlined } from '@ant-design/icons-vue'
import { getTables, getTableSchema, executeQuery } from '@/api/nlp-query'

interface TableInfo {
  name: string
  comment: string
}

interface ColumnInfo {
  name: string
  type: string
  comment: string
}

const question = ref('')
const loading = ref(false)
const sqlResult = ref('')
const queryResults = ref<any[]>([])
const tables = ref<TableInfo[]>([])
const tableSchemas = ref<Record<string, ColumnInfo[]>>({})
const activeTable = ref('')

const schemaColumns = [
  { title: '字段名', dataIndex: 'name', width: 150 },
  { title: '类型', dataIndex: 'type', width: 150 },
  { title: '说明', dataIndex: 'comment', width: 200 },
]

const resultColumns = ref<any[]>([])

async function loadTables() {
  const res = await getTables()
  if (res.code === 200) {
    tables.value = res.data || []
    if (tables.value.length > 0) {
      activeTable.value = tables.value[0].name
      loadSchema(tables.value[0].name)
    }
  }
}

async function loadSchema(tableName: string) {
  if (tableSchemas.value[tableName]) return
  const res = await getTableSchema(tableName)
  if (res.code === 200) {
    tableSchemas.value[tableName] = res.data || []
  }
}

async function handleQuery() {
  if (!question.value.trim()) {
    message.warning('请输入查询需求')
    return
  }

  loading.value = true
  sqlResult.value = ''
  queryResults.value = []
  resultColumns.value = []

  try {
    const res = await executeQuery(question.value)
    if (res.code === 200) {
      sqlResult.value = res.data?.sql || ''
      queryResults.value = res.data?.results || []
      if (queryResults.value.length > 0) {
        resultColumns.value = Object.keys(queryResults.value[0]).map((key) => ({
          title: key,
          dataIndex: key,
          ellipsis: true,
        }))
      }
      message.success(res.data?.message || '查询成功')
    } else {
      message.error(res.message || '查询失败')
    }
  } catch (error) {
    message.error('查询失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function handleClear() {
  question.value = ''
  sqlResult.value = ''
  queryResults.value = []
  resultColumns.value = []
}

onMounted(() => {
  loadTables()
})
</script>

<style scoped lang="scss">
.nlp-query-page {
  padding: 24px;

  .page-header {
    margin-bottom: 24px;

    h2 {
      margin: 0 0 8px;
      font-size: 20px;
      font-weight: 600;
      color: var(--text-primary);
    }

    .description {
      margin: 0;
      color: var(--text-secondary);
      font-size: 14px;
    }
  }

  .query-container {
    margin-bottom: 24px;

    .query-card {
      :deep(.ant-card-body) {
        padding: 24px;
      }
    }
  }

  .result-section {
    .sql-display {
      margin-bottom: 24px;

      .section-title {
        display: flex;
        align-items: center;
        gap: 8px;
        margin-bottom: 12px;
        font-size: 14px;
        font-weight: 500;
        color: var(--text-primary);
      }

      .sql-code {
        padding: 16px;
        background-color: var(--bg-page);
        border-radius: var(--border-radius-base);
        font-family: 'Monaco', 'Menlo', monospace;
        font-size: 13px;
        line-height: 1.6;
        color: var(--text-primary);
        margin: 0;
        overflow-x: auto;
      }
    }

    .results-display {
      .section-title {
        display: flex;
        align-items: center;
        gap: 8px;
        margin-bottom: 12px;
        font-size: 14px;
        font-weight: 500;
        color: var(--text-primary);

        .result-count {
          color: var(--text-secondary);
          font-weight: normal;
        }
      }
    }

    .no-result {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      padding: 32px;
      color: var(--text-secondary);
      font-size: 14px;
    }
  }

  .schema-info {
    :deep(.ant-card) {
      border-radius: var(--border-radius-base);
    }
  }
}

@media (max-width: 768px) {
  .nlp-query-page {
    padding: 16px;
  }
}
</style>