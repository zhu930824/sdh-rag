<template>
  <div class="scheduled-task-page">
    <a-card title="定时任务管理" :bordered="false">
      <template #extra>
        <a-space>
          <a-select v-model:value="filterType" placeholder="任务类型" style="width: 150px" allowClear @change="handleSearch">
            <a-select-option value="sync_document">文档同步</a-select-option>
            <a-select-option value="reindex">重建索引</a-select-option>
            <a-select-option value="cleanup">数据清理</a-select-option>
            <a-select-option value="report">报表生成</a-select-option>
          </a-select>
          <a-button type="primary" @click="showCreateModal">新建任务</a-button>
        </a-space>
      </template>

      <a-table :dataSource="tasks" :columns="columns" :pagination="pagination" :loading="loading" rowKey="id">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="record.status === 1 ? 'success' : 'default'">{{ record.status === 1 ? '运行中' : '已暂停' }}</a-tag>
          </template>
          <template v-if="column.key === 'taskType'">
            {{ getTaskTypeName(record.taskType) }}
          </template>
          <template v-if="column.key === 'stats'">
            <span style="color: #52c41a">{{ record.successCount }}</span> / 
            <span style="color: #ff4d4f">{{ record.failCount }}</span>
          </template>
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="handleToggle(record)">
                {{ record.status === 1 ? '暂停' : '启用' }}
              </a-button>
              <a-button type="link" size="small" @click="handleExecute(record)" :disabled="record.status !== 1">立即执行</a-button>
              <a-button type="link" size="small" @click="showEditModal(record)">编辑</a-button>
              <a-button type="link" size="small" @click="showLogModal(record)">日志</a-button>
              <a-popconfirm title="确定删除?" @confirm="handleDelete(record.id)">
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="modalVisible" :title="isEdit ? '编辑任务' : '新建任务'" width="600px" @ok="handleSubmit">
      <a-form :model="formData" :labelCol="{ span: 6 }">
        <a-form-item label="任务名称" required>
          <a-input v-model:value="formData.name" placeholder="请输入任务名称" />
        </a-form-item>
        <a-form-item label="任务类型" required>
          <a-select v-model:value="formData.taskType" placeholder="选择任务类型">
            <a-select-option value="sync_document">文档同步</a-select-option>
            <a-select-option value="reindex">重建索引</a-select-option>
            <a-select-option value="cleanup">数据清理</a-select-option>
            <a-select-option value="report">报表生成</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="Cron表达式" required>
          <a-input v-model:value="formData.cronExpression" placeholder="如: 0 0 2 * * ? (每天凌晨2点)" />
          <div class="cron-hint">
            <a @click="showCronHelper = true">Cron表达式帮助</a>
          </div>
        </a-form-item>
        <a-form-item label="任务参数">
          <a-textarea v-model:value="formData.params" :rows="3" placeholder="JSON格式的任务参数" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="formData.description" :rows="2" placeholder="任务描述" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="logVisible" title="执行日志" width="800px" :footer="null">
      <a-timeline v-if="logs.length > 0">
        <a-timeline-item v-for="log in logs" :key="log.id" :color="log.status === 1 ? 'green' : 'red'">
          <p><strong>{{ log.startTime }}</strong></p>
          <p>状态: {{ log.status === 1 ? '成功' : '失败' }}</p>
          <p v-if="log.result">结果: {{ log.result }}</p>
          <p v-if="log.errorMsg">错误: {{ log.errorMsg }}</p>
        </a-timeline-item>
      </a-timeline>
      <a-empty v-else description="暂无执行日志" />
    </a-modal>

    <a-modal v-model:open="showCronHelper" title="Cron表达式说明" width="600px" :footer="null">
      <a-descriptions :column="1" bordered size="small">
        <a-descriptions-item label="格式">秒 分 时 日 月 周</a-descriptions-item>
        <a-descriptions-item label="示例: 每分钟">0 * * * * ?</a-descriptions-item>
        <a-descriptions-item label="示例: 每小时">0 0 * * * ?</a-descriptions-item>
        <a-descriptions-item label="示例: 每天凌晨2点">0 0 2 * * ?</a-descriptions-item>
        <a-descriptions-item label="示例: 每周一早上8点">0 0 8 ? * MON</a-descriptions-item>
        <a-descriptions-item label="示例: 每月1号凌晨">0 0 0 1 * ?</a-descriptions-item>
      </a-descriptions>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import {
  getScheduledTasks, createScheduledTask, updateScheduledTask,
  toggleTaskStatus, executeTask, deleteScheduledTask, type ScheduledTask
} from '@/api/scheduledTask'

const loading = ref(false)
const tasks = ref<ScheduledTask[]>([])
const filterType = ref<string>()
const modalVisible = ref(false)
const logVisible = ref(false)
const showCronHelper = ref(false)
const isEdit = ref(false)
const logs = ref<any[]>([])

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  onChange: (page: number) => {
    pagination.current = page
    loadTasks()
  }
})

const formData = reactive({
  id: 0,
  name: '',
  taskType: '',
  cronExpression: '',
  params: '',
  description: ''
})

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '任务名称', dataIndex: 'name', key: 'name' },
  { title: '任务类型', dataIndex: 'taskType', key: 'taskType', width: 120 },
  { title: 'Cron表达式', dataIndex: 'cronExpression', key: 'cronExpression', width: 150 },
  { title: '状态', key: 'status', width: 100 },
  { title: '成功/失败', key: 'stats', width: 100 },
  { title: '下次执行', dataIndex: 'nextExecuteTime', key: 'nextExecuteTime', width: 180 },
  { title: '操作', key: 'action', width: 250 }
]

const loadTasks = async () => {
  loading.value = true
  try {
    const res = await getScheduledTasks(pagination.current, pagination.pageSize, filterType.value)
    if (res.data) {
      tasks.value = res.data.records
      pagination.total = res.data.total
    }
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.current = 1
  loadTasks()
}

const getTaskTypeName = (type: string) => {
  const names: Record<string, string> = {
    'sync_document': '文档同步',
    'reindex': '重建索引',
    'cleanup': '数据清理',
    'report': '报表生成'
  }
  return names[type] || type
}

const showCreateModal = () => {
  isEdit.value = false
  Object.assign(formData, {
    id: 0, name: '', taskType: '', cronExpression: '', params: '', description: ''
  })
  modalVisible.value = true
}

const showEditModal = (record: ScheduledTask) => {
  isEdit.value = true
  Object.assign(formData, {
    id: record.id,
    name: record.name,
    taskType: record.taskType,
    cronExpression: record.cronExpression,
    params: record.params || '',
    description: record.description || ''
  })
  modalVisible.value = true
}

const handleSubmit = async () => {
  try {
    if (isEdit.value) {
      await updateScheduledTask(formData.id, formData)
      message.success('更新成功')
    } else {
      await createScheduledTask(formData)
      message.success('创建成功')
    }
    modalVisible.value = false
    loadTasks()
  } catch (error) {
    message.error('操作失败')
  }
}

const handleToggle = async (task: ScheduledTask) => {
  try {
    await toggleTaskStatus(task.id)
    message.success(task.status === 1 ? '已暂停' : '已启用')
    loadTasks()
  } catch (error) {
    message.error('操作失败')
  }
}

const handleExecute = async (task: ScheduledTask) => {
  try {
    await executeTask(task.id)
    message.success('任务已开始执行')
    loadTasks()
  } catch (error) {
    message.error('执行失败')
  }
}

const handleDelete = async (id: number) => {
  try {
    await deleteScheduledTask(id)
    message.success('删除成功')
    loadTasks()
  } catch (error) {
    message.error('删除失败')
  }
}

const showLogModal = (task: ScheduledTask) => {
  logs.value = []
  logVisible.value = true
}

onMounted(() => {
  loadTasks()
})
</script>

<style scoped>
.scheduled-task-page {
  padding: 24px;
}
.cron-hint {
  margin-top: 4px;
  font-size: 12px;
}
</style>
