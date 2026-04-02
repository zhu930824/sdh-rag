<template>
  <div class="workflow-container">
    <a-card>
      <template #title>
        <div class="card-header">
          <span class="card-title">工作流编排</span>
          <a-button type="primary" @click="handleAdd">
            <template #icon><PlusOutlined /></template>
            创建工作流
          </a-button>
        </div>
      </template>

      <a-form layout="inline" class="search-form">
        <a-form-item label="工作流名称">
          <a-input v-model:value="searchForm.keyword" placeholder="搜索工作流" allow-clear style="width: 200px" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" @click="handleSearch">搜索</a-button>
          <a-button style="margin-left: 8px" @click="handleReset">重置</a-button>
        </a-form-item>
      </a-form>

      <a-table :columns="columns" :data-source="tableData" :loading="loading" :pagination="false">
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'status'">
            <a-tag :color="record.status === 1 ? 'success' : 'default'">
              {{ record.status === 1 ? '启用' : '禁用' }}
            </a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="handleEdit(record)">编辑</a-button>
              <a-button type="link" size="small" @click="handleToggle(record)">
                {{ record.status === 1 ? '禁用' : '启用' }}
              </a-button>
              <a-popconfirm title="确定删除？" @confirm="handleDelete(record)">
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>

      <div class="pagination">
        <a-pagination
          v-model:current="pagination.page"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          show-size-changer
          :show-total="(total: number) => `共 ${total} 条`"
          @change="loadData"
        />
      </div>
    </a-card>

    <a-modal v-model:open="dialogVisible" :title="dialogTitle" :width="700" ok-text="确认" cancel-text="取消" @ok="handleSubmit">
      <a-form :model="formData" :label-col="{ span: 4 }" :wrapper-col="{ span: 18 }">
        <a-form-item label="工作流名称" required>
          <a-input v-model:value="formData.name" placeholder="请输入工作流名称" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="formData.description" placeholder="请输入描述" :rows="3" />
        </a-form-item>
        <a-form-item label="流程配置">
          <div class="flow-builder-preview">
            <div class="node-list">
              <div class="node-item"><DatabaseOutlined /> 输入节点</div>
              <div class="node-item"><SearchOutlined /> 检索节点</div>
              <div class="node-item"><RobotOutlined /> LLM节点</div>
              <div class="node-item"><OutputOutlined /> 输出节点</div>
            </div>
            <a-alert message="拖拽式工作流配置开发中，敬请期待" type="info" show-icon />
          </div>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { PlusOutlined, DatabaseOutlined, SearchOutlined, RobotOutlined, OutputOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { getWorkflowList, createWorkflow, updateWorkflow, deleteWorkflow, toggleWorkflowStatus } from '@/api/workflow'

const loading = ref(false)
const searchForm = reactive({ keyword: '' })
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const tableData = ref<any[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('创建工作流')
const formData = reactive({ name: '', description: '' })

const columns = [
  { title: 'ID', dataIndex: 'id', width: 60 },
  { title: '名称', dataIndex: 'name', width: 150 },
  { title: '描述', dataIndex: 'description', width: 200 },
  { title: '状态', dataIndex: 'status', width: 80 },
  { title: '创建时间', dataIndex: 'createTime', width: 180 },
  { title: '操作', dataIndex: 'action', width: 200, fixed: 'right' },
]

async function loadData() {
  loading.value = true
  try {
    const { data } = await getWorkflowList({ page: pagination.page, pageSize: pagination.pageSize, keyword: searchForm.keyword || undefined })
    tableData.value = data.data.records || []
    pagination.total = data.data.total
  } catch { tableData.value = [] }
  loading.value = false
}

function handleSearch() { pagination.page = 1; loadData() }
function handleReset() { searchForm.keyword = ''; handleSearch() }
function handleAdd() { dialogTitle.value = '创建工作流'; dialogVisible.value = true }
function handleEdit(record: any) { dialogTitle.value = '编辑工作流'; Object.assign(formData, record); dialogVisible.value = true }

async function handleSubmit() {
  try {
    await createWorkflow(formData)
    message.success('创建成功')
    dialogVisible.value = false
    loadData()
  } catch { message.error('创建失败') }
}

async function handleDelete(record: any) {
  try { await deleteWorkflow(record.id); message.success('删除成功'); loadData() } catch { message.error('删除失败') }
}

async function handleToggle(record: any) {
  try { await toggleWorkflowStatus(record.id); message.success('操作成功'); loadData() } catch { message.error('操作失败') }
}

onMounted(() => { loadData() })
</script>

<style scoped lang="scss">
.workflow-container {
  .card-header { display: flex; justify-content: space-between; align-items: center; .card-title { font-size: 16px; font-weight: 500; } }
  .search-form { margin-bottom: 20px; }
  .pagination { margin-top: 20px; display: flex; justify-content: flex-end; }
  .flow-builder-preview { border: 1px dashed #d9d9d9; border-radius: 8px; padding: 16px; .node-list { display: flex; gap: 12px; margin-bottom: 16px; .node-item { padding: 8px 16px; background: #f5f5f5; border-radius: 4px; cursor: move; } } }
}
</style>
