<template>
  <a-space direction="vertical" style="width: 100%" :size="16">
    <a-alert type="info" show-icon message="所有者管理成员与公开范围；编辑者维护文档与配置；阅读者仅查看和检索。" />
    <a-alert v-if="isPublic" type="warning" show-icon message="当前知识库公开：移除成员后，该用户仍可阅读。若需限制访问，请由所有者将知识库设为私有。" />
    <a-form layout="inline" @finish="save">
      <a-form-item label="已有用户">
        <a-input v-model:value="username" placeholder="输入完整登录用户名" :maxlength="100" />
      </a-form-item>
      <a-form-item label="权限">
        <a-select v-model:value="permission" style="width: 120px" :options="roles" />
      </a-form-item>
      <a-form-item><a-button type="primary" html-type="submit" :loading="busy" :disabled="!username.trim()">保存成员权限</a-button></a-form-item>
    </a-form>
    <a-table :data-source="members" :columns="columns" row-key="userId" :loading="loading" :pagination="false">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'permission'">
          <a-tag :color="record.permission === 'editor' ? 'blue' : 'default'">{{ record.permission === 'editor' ? '编辑者' : '阅读者' }}</a-tag>
        </template>
        <template v-if="column.key === 'actions'">
          <a-space>
            <a-button type="link" :disabled="busy" @click="edit(record.username, record.permission)">修改权限</a-button>
            <a-popconfirm title="确定移除此成员的显式授权？" @confirm="revoke(record.userId)">
              <a-button type="link" danger :disabled="busy">移除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>
  </a-space>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { getKnowledgeMembers, grantKnowledgeMember, revokeKnowledgeMember, type KnowledgeMember } from '@/api/knowledgeBase'

const props = defineProps<{ knowledgeBaseId: number; isPublic: boolean }>()
const members = ref<KnowledgeMember[]>([])
const username = ref('')
const permission = ref<'reader' | 'editor'>('reader')
const busy = ref(false)
const loading = ref(false)
const roles = [{ label: '阅读者', value: 'reader' }, { label: '编辑者', value: 'editor' }]
const columns = [
  { title: '用户名', dataIndex: 'username', key: 'username' },
  { title: '权限', key: 'permission' },
  { title: '操作', key: 'actions' },
]
async function load() {
  loading.value = true
  members.value = []
  try {
    const res = await getKnowledgeMembers(props.knowledgeBaseId)
    if (res.code === 200) members.value = res.data
  } catch { message.error('加载成员失败') }
  finally { loading.value = false }
}
function edit(memberUsername: string, memberPermission: 'reader' | 'editor') {
  username.value = memberUsername
  permission.value = memberPermission
}
async function save() {
  if (busy.value || !username.value.trim()) return
  busy.value = true
  try {
    const res = await grantKnowledgeMember(props.knowledgeBaseId, username.value.trim(), permission.value)
    if (res.code === 200) { username.value = ''; message.success('权限已保存'); await load() }
  } catch { message.error('保存权限失败') }
  finally { busy.value = false }
}
async function revoke(userId: number) {
  if (busy.value) return
  busy.value = true
  try {
    const res = await revokeKnowledgeMember(props.knowledgeBaseId, userId)
    if (res.code === 200) { message.success('成员已移除'); await load() }
  } catch { message.error('移除成员失败') }
  finally { busy.value = false }
}
watch(() => props.knowledgeBaseId, load, { immediate: true })
</script>
