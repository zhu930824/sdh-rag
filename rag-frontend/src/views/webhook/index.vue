<template>
  <div class="webhook-page">
    <a-card title="Webhook管理" :bordered="false">
      <template #extra>
        <a-button type="primary" @click="showCreateModal">新建Webhook</a-button>
      </template>

      <a-table :dataSource="webhooks" :columns="columns" :pagination="pagination" :loading="loading" rowKey="id">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="record.status === 1 ? 'success' : 'default'">{{ record.status === 1 ? '启用' : '禁用' }}</a-tag>
          </template>
          <template v-if="column.key === 'events'">
            <a-space wrap>
              <a-tag v-for="event in parseEvents(record.events)" :key="event" color="blue">{{ event }}</a-tag>
            </a-space>
          </template>
          <template v-if="column.key === 'failCount'">
            <a-tag :color="record.failCount > 0 ? 'error' : 'success'">
              {{ record.failCount }} 次失败
            </a-tag>
          </template>
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="handleTest(record)">测试</a-button>
              <a-button type="link" size="small" @click="handleToggle(record)">
                {{ record.status === 1 ? '禁用' : '启用' }}
              </a-button>
              <a-button type="link" size="small" @click="showEditModal(record)">编辑</a-button>
              <a-popconfirm title="确定删除?" @confirm="handleDelete(record.id)">
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="modalVisible" :title="isEdit ? '编辑Webhook' : '新建Webhook'" width="700px" ok-text="确认" cancel-text="取消" @ok="handleSubmit">
      <a-form :model="formData" :labelCol="{ span: 5 }">
        <a-form-item label="名称" required>
          <a-input v-model:value="formData.name" placeholder="Webhook名称" />
        </a-form-item>
        <a-form-item label="URL" required>
          <a-input v-model:value="formData.url" placeholder="回调URL，如: https://example.com/webhook" />
        </a-form-item>
        <a-form-item label="签名密钥">
          <a-input v-model:value="formData.secret" placeholder="可选，用于签名验证" />
        </a-form-item>
        <a-form-item label="订阅事件" required>
          <a-checkbox-group v-model:value="selectedEvents">
            <a-row>
              <a-col :span="8" v-for="event in availableEvents" :key="event.value">
                <a-checkbox :value="event.value">{{ event.label }}</a-checkbox>
              </a-col>
            </a-row>
          </a-checkbox-group>
        </a-form-item>
        <a-form-item label="自定义请求头">
          <a-textarea v-model:value="formData.headers" :rows="3" placeholder='JSON格式，如: {"Authorization": "Bearer xxx"}' />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import {
  getWebhooks, createWebhook, updateWebhook, toggleWebhookStatus,
  deleteWebhook, testWebhook, type WebhookConfig
} from '@/api/webhook'

const loading = ref(false)
const webhooks = ref<WebhookConfig[]>([])
const modalVisible = ref(false)
const isEdit = ref(false)
const selectedEvents = ref<string[]>([])

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  onChange: (page: number) => {
    pagination.current = page
    loadWebhooks()
  }
})

const formData = reactive({
  id: 0,
  name: '',
  url: '',
  secret: '',
  events: '',
  headers: ''
})

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: 'URL', dataIndex: 'url', key: 'url', ellipsis: true },
  { title: '订阅事件', key: 'events', width: 200 },
  { title: '状态', key: 'status', width: 80 },
  { title: '失败次数', key: 'failCount', width: 100 },
  { title: '最后触发', dataIndex: 'lastTriggerTime', key: 'lastTriggerTime', width: 180 },
  { title: '操作', key: 'action', width: 200 }
]

const availableEvents = [
  { value: 'document.upload', label: '文档上传' },
  { value: 'document.delete', label: '文档删除' },
  { value: 'chat.message', label: '聊天消息' },
  { value: 'user.register', label: '用户注册' },
  { value: 'user.login', label: '用户登录' },
  { value: 'task.complete', label: '任务完成' },
  { value: 'task.fail', label: '任务失败' },
  { value: 'system.alert', label: '系统告警' },
  { value: 'approval.submit', label: '审批提交' },
  { value: 'approval.approve', label: '审批通过' },
  { value: 'approval.reject', label: '审批拒绝' }
]

const loadWebhooks = async () => {
  loading.value = true
  try {
    const res = await getWebhooks(pagination.current, pagination.pageSize)
    if (res.data) {
      webhooks.value = res.data.records
      pagination.total = res.data.total
    }
  } finally {
    loading.value = false
  }
}

const parseEvents = (events: string) => {
  try {
    return JSON.parse(events)
  } catch {
    return events.split(',').filter(e => e.trim())
  }
}

const showCreateModal = () => {
  isEdit.value = false
  Object.assign(formData, {
    id: 0, name: '', url: '', secret: '', events: '', headers: ''
  })
  selectedEvents.value = []
  modalVisible.value = true
}

const showEditModal = (record: WebhookConfig) => {
  isEdit.value = true
  Object.assign(formData, {
    id: record.id,
    name: record.name,
    url: record.url,
    secret: record.secret || '',
    events: record.events,
    headers: record.headers || ''
  })
  selectedEvents.value = parseEvents(record.events)
  modalVisible.value = true
}

const handleSubmit = async () => {
  try {
    formData.events = JSON.stringify(selectedEvents.value)
    if (isEdit.value) {
      await updateWebhook(formData.id, formData)
      message.success('更新成功')
    } else {
      await createWebhook(formData)
      message.success('创建成功')
    }
    modalVisible.value = false
    loadWebhooks()
  } catch (error) {
    message.error('操作失败')
  }
}

const handleToggle = async (webhook: WebhookConfig) => {
  try {
    await toggleWebhookStatus(webhook.id)
    message.success(webhook.status === 1 ? '已禁用' : '已启用')
    loadWebhooks()
  } catch (error) {
    message.error('操作失败')
  }
}

const handleTest = async (webhook: WebhookConfig) => {
  try {
    await testWebhook(webhook.id)
    message.success('测试成功')
  } catch (error) {
    message.error('测试失败')
  }
}

const handleDelete = async (id: number) => {
  try {
    await deleteWebhook(id)
    message.success('删除成功')
    loadWebhooks()
  } catch (error) {
    message.error('删除失败')
  }
}

onMounted(() => {
  loadWebhooks()
})
</script>

<style scoped>
.webhook-page {
  padding: 24px;
}
</style>
