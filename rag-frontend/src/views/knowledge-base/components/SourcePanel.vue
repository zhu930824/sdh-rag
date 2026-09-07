<template>
  <a-space direction="vertical" style="width: 100%" :size="16">
    <a-alert type="info" show-icon message="支持 API Push 与 REST 定时拉取；稳定 externalId 用于增量去重，deleted=true 会使内容失效。" />
    <div><a-button v-if="isOwner" type="primary" @click="createOpen = true">新建业务数据源</a-button></div>
    <a-table :data-source="sources" :columns="columns" row-key="id" :loading="loading" :pagination="false">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <a-tag :color="record.status === 1 ? 'green' : 'default'">{{ record.status === 1 ? '启用' : '停用' }}</a-tag>
        </template>
        <template v-else-if="column.key === 'lastSyncStatus'">
          <a-tag v-if="record.lastSyncStatus" :color="record.lastSyncStatus === 'COMPLETED' ? 'green' : 'red'">{{ record.lastSyncStatus }}</a-tag>
          <span v-else>-</span>
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a-button v-if="record.status === 1 && record.sourceType === 'api_push'" type="link" size="small" @click="openIngest(record.id)">推送数据</a-button>
            <a-button v-if="record.status === 1 && record.sourceType === 'rest_json'" type="link" size="small" @click="pullNow(record.id)">立即拉取</a-button>
            <a-button type="link" size="small" @click="openHistory(record.id)">同步记录</a-button>
            <a-button v-if="isOwner && record.sourceType === 'api_push'" type="link" size="small" @click="openKeys(record.id)">API Key</a-button>
            <a-popconfirm v-if="isOwner && record.status === 1" title="停用后将拒绝新的推送，历史内容不会删除。" @confirm="disable(record.id)">
              <a-button type="link" size="small" danger>停用</a-button>
            </a-popconfirm>
            <a-button v-if="isOwner && record.status === 0" type="link" size="small" @click="enable(record.id, record.name, record.sourceType, record.configJson, record.credentialRef, record.syncEnabled, record.syncIntervalMinutes)">重新启用</a-button>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal v-model:open="createOpen" title="新建业务数据源" :confirm-loading="saving" width="680px" @ok="create">
      <a-form layout="vertical">
        <a-form-item label="名称"><a-input v-model:value="sourceName" placeholder="例如：客服工单知识" :maxlength="120" /></a-form-item>
        <a-form-item label="接入方式"><a-select v-model:value="sourceType"><a-select-option value="api_push">业务系统主动推送</a-select-option><a-select-option value="rest_json">平台定时拉取 REST JSON</a-select-option></a-select></a-form-item>
        <template v-if="sourceType === 'rest_json'">
          <a-form-item label="接口地址"><a-input v-model:value="restEndpoint" placeholder="https://business.example/api/knowledge" /></a-form-item>
          <a-form-item label="认证方式"><a-select v-model:value="restAuthType"><a-select-option value="none">无认证</a-select-option><a-select-option value="bearer">Bearer Token</a-select-option><a-select-option value="header">自定义请求头</a-select-option></a-select></a-form-item>
          <a-form-item v-if="restAuthType === 'header'" label="认证请求头"><a-input v-model:value="restAuthHeader" placeholder="X-API-Key" /></a-form-item>
          <a-form-item v-if="restAuthType !== 'none'" label="凭据引用"><a-input v-model:value="credentialRef" placeholder="env:BUSINESS_KNOWLEDGE_TOKEN" /></a-form-item>
          <a-space style="margin-bottom: 16px">
            <a-checkbox v-model:checked="allowPrivateNetwork">允许访问企业内网</a-checkbox>
            <a-checkbox v-model:checked="allowInsecureHttp">允许 HTTP（仅限可信测试环境）</a-checkbox>
          </a-space>
          <a-form-item label="分页大小"><a-input-number v-model:value="pageSize" :min="1" :max="500" style="width: 100%" /></a-form-item>
          <a-form-item label="自动同步"><a-switch v-model:checked="syncEnabled" /></a-form-item>
          <a-form-item v-if="syncEnabled" label="同步周期（分钟）"><a-input-number v-model:value="syncIntervalMinutes" :min="5" :max="10080" style="width: 100%" /></a-form-item>
        </template>
      </a-form>
    </a-modal>

    <a-modal v-model:open="ingestOpen" title="推送 JSON 数据" width="820px" :confirm-loading="saving" @ok="ingest">
      <a-alert type="warning" show-icon message="这里只用于联调或手动补数据；生产环境应由业务系统调用同一接口。" style="margin-bottom: 12px" />
      <a-textarea v-model:value="payload" :rows="14" />
    </a-modal>

    <a-drawer v-model:open="historyOpen" title="最近同步记录" width="760">
      <a-table :data-source="tasks" :columns="taskColumns" row-key="id" :loading="historyLoading" size="small" :pagination="false">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'taskStatus'"><a-tag :color="record.taskStatus === 'COMPLETED' ? 'green' : 'red'">{{ record.taskStatus }}</a-tag></template>
        </template>
      </a-table>
    </a-drawer>

    <a-drawer v-model:open="keysOpen" title="生产接入与调用审计" width="980">
      <a-alert type="warning" show-icon message="密钥明文仅在创建或轮换后展示一次；业务系统请通过 X-SDH-API-Key 请求头调用。" />
      <a-descriptions :column="1" size="small" bordered style="margin: 12px 0">
        <a-descriptions-item label="推送地址"><a-typography-text copyable>{{ integrationEndpoint }}</a-typography-text></a-descriptions-item>
      </a-descriptions>
      <a-button type="primary" @click="keyCreateOpen = true">创建 API Key</a-button>
      <a-table :data-source="apiKeys" :columns="keyColumns" row-key="id" :loading="keyLoading" size="small" :pagination="false" style="margin-top: 12px">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'keyStatus'"><a-tag :color="record.status === 1 ? 'green' : 'default'">{{ record.status === 1 ? '有效' : '已吊销' }}</a-tag></template>
          <template v-else-if="column.key === 'keyActions'">
            <a-space v-if="record.status === 1">
              <a-popconfirm title="轮换后旧密钥会立即失效，确定继续吗？" @confirm="rotateKey(record.id)"><a-button type="link" size="small">轮换</a-button></a-popconfirm>
              <a-popconfirm title="吊销后无法恢复，确定继续吗？" @confirm="revokeKey(record.id)"><a-button type="link" size="small" danger>吊销</a-button></a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
      <a-divider>最近调用</a-divider>
      <a-table :data-source="apiAudits" :columns="auditColumns" row-key="id" :loading="keyLoading" size="small" :pagination="false">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'auditStatus'"><a-tag :color="record.resultStatus === 'SUCCESS' ? 'green' : 'red'">{{ record.responseCode }}</a-tag></template>
        </template>
      </a-table>
    </a-drawer>

    <a-modal v-model:open="keyCreateOpen" title="创建 API Key" :confirm-loading="saving" @ok="createKey">
      <a-form layout="vertical">
        <a-form-item label="用途名称"><a-input v-model:value="keyName" :maxlength="80" placeholder="例如：工单生产环境" /></a-form-item>
        <a-form-item label="每分钟请求上限"><a-input-number v-model:value="keyRateLimit" :min="1" :max="10000" style="width: 100%" /></a-form-item>
        <a-form-item label="过期时间（可选）"><a-date-picker v-model:value="keyExpiresAt" show-time value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" /></a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="keyRevealOpen" title="请立即保存 API Key" :footer="null">
      <a-alert type="error" show-icon message="关闭后将无法再次查看该密钥，只能重新轮换。" style="margin-bottom: 12px" />
      <a-typography-paragraph copyable :content="revealedApiKey"><code>{{ revealedApiKey }}</code></a-typography-paragraph>
    </a-modal>
  </a-space>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import {
  createKnowledgeSource, disableKnowledgeSource, getKnowledgeSources, getKnowledgeSyncTasks,
  ingestKnowledgeSource, updateKnowledgeSource, getSourceApiKeys, createSourceApiKey,
  rotateSourceApiKey, revokeSourceApiKey, getSourceApiAudits,
  pullKnowledgeSource,
  type KnowledgeSource, type SyncTask, type SourceApiKey, type SourceApiAudit,
} from '@/api/knowledgeSource'

const props = defineProps<{ knowledgeBaseId: number; role: string }>()
const isOwner = ref(false)
const sources = ref<KnowledgeSource[]>([])
const tasks = ref<SyncTask[]>([])
const loading = ref(false)
const historyLoading = ref(false)
const saving = ref(false)
const createOpen = ref(false)
const ingestOpen = ref(false)
const historyOpen = ref(false)
const keysOpen = ref(false)
const keyCreateOpen = ref(false)
const keyRevealOpen = ref(false)
const sourceName = ref('')
const credentialRef = ref('')
const sourceType = ref<'api_push' | 'rest_json'>('api_push')
const restEndpoint = ref('')
const restAuthType = ref<'none' | 'bearer' | 'header'>('none')
const restAuthHeader = ref('X-API-Key')
const allowPrivateNetwork = ref(false)
const allowInsecureHttp = ref(false)
const pageSize = ref(100)
const syncEnabled = ref(true)
const syncIntervalMinutes = ref(60)
const currentSourceId = ref<number>()
const apiKeys = ref<SourceApiKey[]>([])
const apiAudits = ref<SourceApiAudit[]>([])
const keyLoading = ref(false)
const keyName = ref('')
const keyRateLimit = ref(60)
const keyExpiresAt = ref<string>()
const revealedApiKey = ref('')
const integrationEndpoint = ref('')
const payload = ref(JSON.stringify({ cursor: 'cursor-001', items: [{ externalId: 'article-001', title: '示例业务知识', content: '这里填写可检索的业务内容', sourceUrl: 'https://business.example/items/001', sourceUpdatedAt: new Date().toISOString().slice(0, 19), metadata: { department: '客服部', docType: 'faq', docDate: new Date().toISOString().slice(0, 10) }, deleted: false }] }, null, 2))
const columns = [
  { title: '名称', dataIndex: 'name', key: 'name' }, { title: '类型', dataIndex: 'sourceType', key: 'sourceType', width: 110 },
  { title: '状态', key: 'status', width: 80 }, { title: '游标', dataIndex: 'syncCursor', key: 'syncCursor', ellipsis: true },
  { title: '下次同步', dataIndex: 'nextSyncTime', key: 'nextSyncTime', width: 170 }, { title: '最近同步', dataIndex: 'lastSyncTime', key: 'lastSyncTime', width: 170 }, { title: '结果', key: 'lastSyncStatus', width: 110 },
  { title: '操作', key: 'actions', width: 320 },
]
const keyColumns = [
  { title: '用途', dataIndex: 'name', key: 'name' }, { title: '前缀', dataIndex: 'keyPrefix', key: 'keyPrefix' },
  { title: '限流/分钟', dataIndex: 'rateLimitPerMinute', key: 'rateLimitPerMinute', width: 100 },
  { title: '状态', key: 'keyStatus', width: 80 }, { title: '过期时间', dataIndex: 'expiresAt', key: 'expiresAt', width: 170 },
  { title: '最后使用', dataIndex: 'lastUsedAt', key: 'lastUsedAt', width: 170 }, { title: '操作', key: 'keyActions', width: 130 },
]
const auditColumns = [
  { title: '时间', dataIndex: 'createTime', key: 'createTime', width: 170 }, { title: '状态码', key: 'auditStatus', width: 80 },
  { title: '条目', dataIndex: 'itemCount', key: 'itemCount', width: 70 }, { title: '耗时(ms)', dataIndex: 'durationMs', key: 'durationMs', width: 90 },
  { title: '请求ID', dataIndex: 'requestId', key: 'requestId', ellipsis: true }, { title: '错误', dataIndex: 'errorMessage', key: 'errorMessage', ellipsis: true },
]
const taskColumns = [
  { title: '开始时间', dataIndex: 'startTime', key: 'startTime', width: 170 }, { title: '状态', key: 'taskStatus', width: 100 },
  { title: '扫描', dataIndex: 'scannedCount', key: 'scannedCount' }, { title: '新增', dataIndex: 'createdCount', key: 'createdCount' },
  { title: '更新', dataIndex: 'updatedCount', key: 'updatedCount' }, { title: '未变', dataIndex: 'unchangedCount', key: 'unchangedCount' },
  { title: '删除', dataIndex: 'deletedCount', key: 'deletedCount' }, { title: '错误', dataIndex: 'errorMessage', key: 'errorMessage', ellipsis: true },
]
async function load() {
  isOwner.value = props.role === 'owner'
  loading.value = true
  try { const res = await getKnowledgeSources(props.knowledgeBaseId); if (res.code === 200) sources.value = res.data }
  catch { message.error('加载知识源失败') } finally { loading.value = false }
}
async function create() {
  if (!sourceName.value.trim()) return message.warning('请输入数据源名称')
  if (sourceType.value === 'rest_json' && !restEndpoint.value.trim()) return message.warning('请输入 REST 接口地址')
  saving.value = true
  try {
    const configuration = sourceType.value === 'rest_json' ? {
      endpoint: restEndpoint.value.trim(), authType: restAuthType.value,
      ...(restAuthType.value === 'header' ? { authHeader: restAuthHeader.value.trim() } : {}),
      allowPrivateNetwork: allowPrivateNetwork.value, allowInsecureHttp: allowInsecureHttp.value,
      pageSize: pageSize.value, maxPagesPerRun: 20, maxRunSeconds: 600,
    } : {}
    const res = await createKnowledgeSource(props.knowledgeBaseId, {
      name: sourceName.value.trim(), sourceType: sourceType.value, configuration,
      credentialRef: credentialRef.value.trim() || undefined,
      syncEnabled: sourceType.value === 'rest_json' && syncEnabled.value,
      syncIntervalMinutes: syncIntervalMinutes.value,
    })
    if (res.code === 200) { createOpen.value = false; sourceName.value = ''; credentialRef.value = ''; restEndpoint.value = ''; message.success('数据源已创建'); await load() }
  } catch { message.error('创建数据源失败') } finally { saving.value = false }
}
function openIngest(sourceId: number) { currentSourceId.value = sourceId; ingestOpen.value = true }
async function ingest() {
  if (!currentSourceId.value) return
  let data: unknown
  try { data = JSON.parse(payload.value) } catch { return message.error('JSON 格式不正确') }
  saving.value = true
  try {
    const res = await ingestKnowledgeSource(props.knowledgeBaseId, currentSourceId.value, data)
    if (res.code === 200) { ingestOpen.value = false; message.success('同步已接收'); await load() }
  } catch { message.error('同步失败，请查看同步记录后使用相同 externalId 重试') } finally { saving.value = false }
}
async function disable(sourceId: number) {
  const res = await disableKnowledgeSource(props.knowledgeBaseId, sourceId)
  if (res.code === 200) { message.success('数据源已停用'); await load() }
}
async function enable(sourceId: number, name: string, type: 'api_push' | 'rest_json', configJson: string, sourceCredentialRef: string | null, sourceSyncEnabled: number, interval: number) {
  const configuration = configJson ? JSON.parse(configJson) : {}
  const res = await updateKnowledgeSource(props.knowledgeBaseId, sourceId, { name, sourceType: type, configuration, credentialRef: sourceCredentialRef || undefined, status: 1, syncEnabled: sourceSyncEnabled === 1, syncIntervalMinutes: interval })
  if (res.code === 200) { message.success('数据源已启用'); await load() }
}
async function pullNow(sourceId: number) {
  saving.value = true
  try {
    const res = await pullKnowledgeSource(props.knowledgeBaseId, sourceId)
    if (res.code === 200) { message.success('拉取同步已完成'); await load() }
  } catch { message.error('拉取失败，请查看同步记录；开启自动同步后系统会按退避策略重试') } finally { saving.value = false }
}
async function openHistory(sourceId: number) {
  historyOpen.value = true; historyLoading.value = true
  try { const res = await getKnowledgeSyncTasks(props.knowledgeBaseId, sourceId); if (res.code === 200) tasks.value = res.data }
  finally { historyLoading.value = false }
}
async function openKeys(sourceId: number) {
  currentSourceId.value = sourceId
  integrationEndpoint.value = `${window.location.origin}/api/integration/knowledge-sources/${sourceId}/ingest`
  keysOpen.value = true
  await loadKeys()
}
async function loadKeys() {
  if (!currentSourceId.value) return
  keyLoading.value = true
  try {
    const [keys, audits] = await Promise.all([
      getSourceApiKeys(props.knowledgeBaseId, currentSourceId.value),
      getSourceApiAudits(props.knowledgeBaseId, currentSourceId.value),
    ])
    if (keys.code === 200) apiKeys.value = keys.data
    if (audits.code === 200) apiAudits.value = audits.data
  } catch { message.error('加载 API Key 信息失败') } finally { keyLoading.value = false }
}
async function createKey() {
  if (!currentSourceId.value || !keyName.value.trim()) return message.warning('请输入用途名称')
  saving.value = true
  try {
    const res = await createSourceApiKey(props.knowledgeBaseId, currentSourceId.value, {
      name: keyName.value.trim(), rateLimitPerMinute: keyRateLimit.value, expiresAt: keyExpiresAt.value,
    })
    if (res.code === 200) {
      keyCreateOpen.value = false; keyName.value = ''; keyExpiresAt.value = undefined
      revealedApiKey.value = res.data.apiKey; keyRevealOpen.value = true; await loadKeys()
    }
  } catch { message.error('创建 API Key 失败') } finally { saving.value = false }
}
async function rotateKey(keyId: number) {
  if (!currentSourceId.value) return
  const res = await rotateSourceApiKey(props.knowledgeBaseId, currentSourceId.value, keyId)
  if (res.code === 200) { revealedApiKey.value = res.data.apiKey; keyRevealOpen.value = true; await loadKeys() }
}
async function revokeKey(keyId: number) {
  if (!currentSourceId.value) return
  const res = await revokeSourceApiKey(props.knowledgeBaseId, currentSourceId.value, keyId)
  if (res.code === 200) { message.success('API Key 已吊销'); await loadKeys() }
}
watch(() => [props.knowledgeBaseId, props.role], load, { immediate: true })
</script>
