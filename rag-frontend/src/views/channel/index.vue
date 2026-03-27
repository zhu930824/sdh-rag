<template>
  <div class="channel-page">
    <div class="page-header">
      <h2>渠道管理</h2>
      <p class="description">配置多渠道接入，支持企业微信、钉钉、微信小程序等平台</p>
    </div>

    <div class="channel-grid">
      <div class="channel-card" v-for="channel in channelList" :key="channel.id">
        <div class="card-header">
          <div class="channel-icon" :class="channel.channelType">
            <WechatOutlined v-if="channel.channelType === 'wechat_work'" />
            <DingtalkOutlined v-else-if="channel.channelType === 'dingtalk'" />
            <ApiOutlined v-else />
          </div>
          <div class="channel-info">
            <div class="channel-name">{{ channel.name }}</div>
            <a-tag :color="getChannelTypeColor(channel.channelType)">
              {{ getChannelTypeText(channel.channelType) }}
            </a-tag>
          </div>
          <a-switch :checked="channel.status === 1" @change="(checked: boolean) => handleToggle(channel, checked)" />
        </div>
        
        <div class="card-body">
          <div class="info-item">
            <span class="label">回调地址</span>
            <span class="value">{{ channel.callbackUrl || '未配置' }}</span>
          </div>
          <div class="info-item">
            <span class="label">创建时间</span>
            <span class="value">{{ channel.createTime }}</span>
          </div>
        </div>

        <div class="card-footer">
          <a-button type="link" size="small" @click="handleEdit(channel)">编辑</a-button>
          <a-button type="link" size="small" @click="handleTest(channel)">测试连接</a-button>
          <a-popconfirm title="确定删除该渠道？" @confirm="handleDelete(channel)">
            <a-button type="link" size="small" danger>删除</a-button>
          </a-popconfirm>
        </div>
      </div>

      <div class="channel-card add-card" @click="showCreateModal">
        <PlusOutlined />
        <span>添加渠道</span>
      </div>
    </div>

    <a-modal
      v-model:open="modalVisible"
      :title="isEdit ? '编辑渠道' : '添加渠道'"
      :width="550"
      @ok="handleSubmit"
    >
      <a-form :model="formData" :label-col="{ span: 5 }" :wrapper-col="{ span: 18 }">
        <a-form-item label="渠道名称" required>
          <a-input v-model:value="formData.name" placeholder="请输入渠道名称" />
        </a-form-item>
        <a-form-item label="渠道类型" required>
          <a-select v-model:value="formData.channelType" placeholder="请选择渠道类型">
            <a-select-option value="wechat_work">企业微信</a-select-option>
            <a-select-option value="dingtalk">钉钉</a-select-option>
            <a-select-option value="wechat_mp">微信公众号</a-select-option>
            <a-select-option value="api">开放API</a-select-option>
            <a-select-option value="web">Web组件</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="关联助手">
          <a-select v-model:value="formData.assistantId" placeholder="选择关联的AI助手" allow-clear>
            <a-select-option v-for="a in assistantList" :key="a.id" :value="a.id">{{ a.name }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="回调地址">
          <a-input v-model:value="formData.callbackUrl" placeholder="系统自动生成" disabled />
        </a-form-item>
        <a-form-item label="密钥">
          <a-input-password v-model:value="formData.secretKey" placeholder="用于验证消息来源" />
        </a-form-item>
        
        <template v-if="formData.channelType === 'wechat_work'">
          <a-form-item label="CorpID">
            <a-input v-model:value="wechatConfig.corpId" placeholder="企业ID" />
          </a-form-item>
          <a-form-item label="AgentID">
            <a-input v-model:value="wechatConfig.agentId" placeholder="应用AgentId" />
          </a-form-item>
          <a-form-item label="Secret">
            <a-input-password v-model:value="wechatConfig.secret" placeholder="应用Secret" />
          </a-form-item>
        </template>
        
        <template v-if="formData.channelType === 'dingtalk'">
          <a-form-item label="AppKey">
            <a-input v-model:value="dingConfig.appKey" placeholder="应用AppKey" />
          </a-form-item>
          <a-form-item label="AppSecret">
            <a-input-password v-model:value="dingConfig.appSecret" placeholder="应用AppSecret" />
          </a-form-item>
        </template>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined, WechatOutlined, ApiOutlined } from '@ant-design/icons-vue'
import { getChannelList, createChannel, updateChannel, deleteChannel, toggleChannelStatus, type ChannelConfig } from '@/api/channel'
import { getHotAssistants, type AiAssistant } from '@/api/assistant'

const channelList = ref<ChannelConfig[]>([])
const assistantList = ref<AiAssistant[]>([])
const modalVisible = ref(false)
const isEdit = ref(false)

const formData = reactive({
  id: 0,
  name: '',
  channelType: 'api',
  assistantId: undefined as number | undefined,
  callbackUrl: '',
  secretKey: '',
})

const wechatConfig = reactive({ corpId: '', agentId: '', secret: '' })
const dingConfig = reactive({ appKey: '', appSecret: '' })

function getChannelTypeText(type: string): string {
  const map: Record<string, string> = {
    wechat_work: '企业微信',
    dingtalk: '钉钉',
    wechat_mp: '微信公众号',
    api: '开放API',
    web: 'Web组件',
  }
  return map[type] || type
}

function getChannelTypeColor(type: string): string {
  const map: Record<string, string> = {
    wechat_work: 'green',
    dingtalk: 'blue',
    wechat_mp: 'green',
    api: 'purple',
    web: 'orange',
  }
  return map[type] || 'default'
}

async function loadData() {
  try {
    const res = await getChannelList({ page: 1, pageSize: 100 })
    if (res.code === 200) {
      channelList.value = res.data?.records || []
    }
  } catch (error) {
    message.error('加载失败')
  }
}

async function loadAssistants() {
  try {
    const res = await getHotAssistants(50)
    if (res.code === 200) {
      assistantList.value = res.data || []
    }
  } catch (error) {
    console.error('加载助手失败')
  }
}

function showCreateModal() {
  isEdit.value = false
  Object.assign(formData, { id: 0, name: '', channelType: 'api', assistantId: undefined, callbackUrl: '', secretKey: '' })
  Object.assign(wechatConfig, { corpId: '', agentId: '', secret: '' })
  Object.assign(dingConfig, { appKey: '', appSecret: '' })
  modalVisible.value = true
}

function handleEdit(channel: ChannelConfig) {
  isEdit.value = true
  Object.assign(formData, channel)
  modalVisible.value = true
}

async function handleSubmit() {
  if (!formData.name || !formData.channelType) {
    message.warning('请填写必要信息')
    return
  }
  
  const config: Record<string, any> = {}
  if (formData.channelType === 'wechat_work') {
    Object.assign(config, wechatConfig)
  } else if (formData.channelType === 'dingtalk') {
    Object.assign(config, dingConfig)
  }
  
  const data = { ...formData, config: JSON.stringify(config) }
  
  try {
    if (isEdit.value) {
      await updateChannel(formData.id, data)
    } else {
      await createChannel(data)
    }
    message.success('操作成功')
    modalVisible.value = false
    loadData()
  } catch (error) {
    message.error('操作失败')
  }
}

async function handleToggle(channel: ChannelConfig, checked: boolean) {
  try {
    await toggleChannelStatus(channel.id)
    channel.status = checked ? 1 : 0
    message.success('状态已更新')
  } catch (error) {
    message.error('操作失败')
  }
}

function handleTest(channel: ChannelConfig) {
  message.info('测试连接功能开发中...')
}

async function handleDelete(channel: ChannelConfig) {
  try {
    await deleteChannel(channel.id)
    message.success('删除成功')
    loadData()
  } catch (error) {
    message.error('删除失败')
  }
}

onMounted(() => {
  loadData()
  loadAssistants()
})
</script>

<style scoped lang="scss">
.channel-page {
  padding: 24px;

  .page-header {
    margin-bottom: 24px;
    h2 { margin: 0 0 8px; font-size: 20px; font-weight: 600; }
    .description { color: var(--text-secondary); font-size: 14px; }
  }

  .channel-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
    gap: 16px;
  }

  .channel-card {
    background-color: var(--bg-color);
    border: 1px solid var(--border-lighter);
    border-radius: var(--border-radius-large);
    padding: 20px;
    transition: all 0.3s;

    &:hover { box-shadow: var(--box-shadow); }

    .card-header {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 16px;

      .channel-icon {
        width: 40px;
        height: 40px;
        border-radius: 8px;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 20px;
        color: white;

        &.wechat_work { background-color: #07c160; }
        &.dingtalk { background-color: #0082ef; }
        &.wechat_mp { background-color: #07c160; }
        &.api { background-color: #722ed1; }
        &.web { background-color: #fa8c16; }
      }

      .channel-info {
        flex: 1;
        .channel-name { font-weight: 500; margin-bottom: 4px; }
      }
    }

    .card-body {
      .info-item {
        display: flex;
        justify-content: space-between;
        padding: 6px 0;
        font-size: 13px;
        .label { color: var(--text-secondary); }
        .value { color: var(--text-primary); }
      }
    }

    .card-footer {
      margin-top: 16px;
      padding-top: 16px;
      border-top: 1px solid var(--border-lighter);
      display: flex;
      justify-content: flex-end;
      gap: 8px;
    }

    &.add-card {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      min-height: 160px;
      border-style: dashed;
      color: var(--text-secondary);
      cursor: pointer;
      gap: 8px;

      &:hover { color: var(--primary-color); border-color: var(--primary-color); }
    }
  }
}
</style>