<template>
  <div class="chat-session-page">
    <a-card title="会话管理" :bordered="false">
      <template #extra>
        <a-space>
          <a-button @click="showArchived = !showArchived">
            {{ showArchived ? '显示活跃会话' : '显示归档会话' }}
          </a-button>
          <a-button type="primary" @click="showCreateModal">新建会话</a-button>
        </a-space>
      </template>

      <a-row :gutter="16">
        <a-col :span="6">
          <a-card title="收藏的会话" size="small">
            <a-list :dataSource="starredSessions" size="small">
              <template #renderItem="{ item }">
                <a-list-item>
                  <a-list-item-meta :title="item.title" :description="`消息: ${item.messageCount}`" />
                  <template #actions>
                    <a-button type="link" size="small" @click="viewSession(item)">打开</a-button>
                  </template>
                </a-list-item>
              </template>
            </a-list>
          </a-card>
        </a-col>
        <a-col :span="18">
          <a-table :dataSource="sessions" :columns="columns" :pagination="pagination" :loading="loading" rowKey="id">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'title'">
                <a-space>
                  <span>{{ record.title }}</span>
                  <star-filled v-if="record.isStarred" style="color: #faad14" />
                </a-space>
              </template>
              <template v-if="column.key === 'status'">
                <a-tag v-if="record.isArchived" color="orange">已归档</a-tag>
                <a-tag v-else color="green">活跃</a-tag>
              </template>
              <template v-if="column.key === 'action'">
                <a-space>
                  <a-button type="link" size="small" @click="viewSession(record)">查看</a-button>
                  <a-button type="link" size="small" @click="handleStar(record)">
                    {{ record.isStarred ? '取消收藏' : '收藏' }}
                  </a-button>
                  <a-button type="link" size="small" @click="handleArchive(record)">
                    {{ record.isArchived ? '取消归档' : '归档' }}
                  </a-button>
                  <a-button type="link" size="small" @click="showShareModal(record)">分享</a-button>
                  <a-button type="link" size="small" @click="handleExport(record)">导出</a-button>
                  <a-popconfirm title="确定删除?" @confirm="handleDelete(record.sessionId)">
                    <a-button type="link" size="small" danger>删除</a-button>
                  </a-popconfirm>
                </a-space>
              </template>
            </template>
          </a-table>
        </a-col>
      </a-row>
    </a-card>

    <a-modal v-model:open="createVisible" title="新建会话" ok-text="确认" cancel-text="取消" @ok="handleCreate">
      <a-form :model="createForm" :labelCol="{ span: 6 }">
        <a-form-item label="会话标题">
          <a-input v-model:value="createForm.title" placeholder="可选，留空自动生成" />
        </a-form-item>
        <a-form-item label="选择模型">
          <a-select v-model:value="createForm.modelId" placeholder="选择模型">
            <a-select-option v-for="model in models" :key="model.id" :value="model.id">{{ model.name }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="Prompt模板">
          <a-select v-model:value="createForm.promptTemplateId" placeholder="选择模板">
            <a-select-option v-for="tpl in templates" :key="tpl.id" :value="tpl.id">{{ tpl.name }}</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="shareVisible" title="分享会话" ok-text="确认" cancel-text="取消" @ok="handleShare">
      <a-form :model="shareForm" :labelCol="{ span: 6 }">
        <a-form-item label="访问密码">
          <a-input v-model:value="shareForm.password" placeholder="可选，留空则公开访问" />
        </a-form-item>
        <a-form-item label="有效期">
          <a-select v-model:value="shareForm.expireHours" placeholder="选择有效期">
            <a-select-option :value="0">永久有效</a-select-option>
            <a-select-option :value="1">1小时</a-select-option>
            <a-select-option :value="24">1天</a-select-option>
            <a-select-option :value="168">1周</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
      <a-alert v-if="shareResult" type="success" style="margin-top: 16px">
        <template #message>
          <p>分享链接：<a :href="shareResult.url" target="_blank">{{ shareResult.url }}</a></p>
          <p v-if="shareResult.password">访问密码：{{ shareResult.password }}</p>
        </template>
      </a-alert>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { StarFilled } from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import {
  getChatSessions, getStarredSessions, getArchivedSessions, createChatSession,
  toggleSessionStar, toggleSessionArchive, deleteChatSession, createSessionShare,
  exportChatSession, type ChatSession, type SessionShare
} from '@/api/chatSession'
import { getActivePromptTemplates, type PromptTemplate } from '@/api/promptTemplate'
import { getActiveModels, type ModelConfig } from '@/api/model'

const router = useRouter()
const loading = ref(false)
const sessions = ref<ChatSession[]>([])
const starredSessions = ref<ChatSession[]>([])
const showArchived = ref(false)
const createVisible = ref(false)
const shareVisible = ref(false)
const currentSession = ref<ChatSession | null>(null)
const shareResult = ref<{ url: string; password?: string } | null>(null)
const models = ref<ModelConfig[]>([])
const templates = ref<PromptTemplate[]>([])

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  onChange: (page: number) => {
    pagination.current = page
    loadSessions()
  }
})

const createForm = reactive({
  title: '',
  modelId: undefined as number | undefined,
  promptTemplateId: undefined as number | undefined
})

const shareForm = reactive({
  password: '',
  expireHours: 24
})

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '标题', dataIndex: 'title', key: 'title' },
  { title: '消息数', dataIndex: 'messageCount', key: 'messageCount', width: 100 },
  { title: 'Token消耗', dataIndex: 'totalTokens', key: 'totalTokens', width: 120 },
  { title: '状态', key: 'status', width: 100 },
  { title: '最后消息', dataIndex: 'lastMessageTime', key: 'lastMessageTime', width: 180 },
  { title: '操作', key: 'action', width: 300 }
]

const loadSessions = async () => {
  loading.value = true
  try {
    const res = await getChatSessions(pagination.current, pagination.pageSize)
    if (res.data) {
      sessions.value = res.data.records
      pagination.total = res.data.total
    }
  } finally {
    loading.value = false
  }
}

const loadStarredSessions = async () => {
  const res = await getStarredSessions()
  if (res.data) starredSessions.value = res.data
}

const loadModels = async () => {
  const res = await getActiveModels()
  if (res.data) models.value = res.data
}

const loadTemplates = async () => {
  const res = await getActivePromptTemplates()
  if (res.data) templates.value = res.data
}

const viewSession = (session: ChatSession) => {
  router.push({ path: '/chat', query: { sessionId: session.sessionId } })
}

const showCreateModal = () => {
  createForm.title = ''
  createForm.modelId = undefined
  createForm.promptTemplateId = undefined
  createVisible.value = true
}

const handleCreate = async () => {
  try {
    const res = await createChatSession(createForm)
    if (res.data) {
      message.success('创建成功')
      createVisible.value = false
      loadSessions()
      router.push({ path: '/chat', query: { sessionId: res.data.sessionId } })
    }
  } catch (error) {
    message.error('创建失败')
  }
}

const handleStar = async (session: ChatSession) => {
  try {
    await toggleSessionStar(session.sessionId)
    message.success(session.isStarred ? '已取消收藏' : '已收藏')
    loadSessions()
    loadStarredSessions()
  } catch (error) {
    message.error('操作失败')
  }
}

const handleArchive = async (session: ChatSession) => {
  try {
    await toggleSessionArchive(session.sessionId)
    message.success(session.isArchived ? '已取消归档' : '已归档')
    loadSessions()
  } catch (error) {
    message.error('操作失败')
  }
}

const handleDelete = async (sessionId: string) => {
  try {
    await deleteChatSession(sessionId)
    message.success('删除成功')
    loadSessions()
    loadStarredSessions()
  } catch (error) {
    message.error('删除失败')
  }
}

const showShareModal = (session: ChatSession) => {
  currentSession.value = session
  shareForm.password = ''
  shareForm.expireHours = 24
  shareResult.value = null
  shareVisible.value = true
}

const handleShare = async () => {
  if (!currentSession.value) return
  try {
    const res = await createSessionShare(
      currentSession.value.sessionId,
      shareForm.password || undefined,
      shareForm.expireHours || undefined
    )
    if (res.data) {
      shareResult.value = {
        url: `${window.location.origin}/shared/${res.data.shareCode}`,
        password: shareForm.password || undefined
      }
    }
  } catch (error) {
    message.error('分享失败')
  }
}

const handleExport = async (session: ChatSession) => {
  try {
    const blob = await exportChatSession(session.sessionId, 'markdown')
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `session_${session.sessionId}.md`
    a.click()
    window.URL.revokeObjectURL(url)
    message.success('导出成功')
  } catch (error) {
    message.error('导出失败')
  }
}

onMounted(() => {
  loadSessions()
  loadStarredSessions()
  loadModels()
  loadTemplates()
})
</script>

<style scoped>
.chat-session-page {
  padding: 24px;
}
</style>
