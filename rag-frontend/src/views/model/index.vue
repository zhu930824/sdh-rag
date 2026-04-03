<template>
  <div class="model-container">
    <div class="provider-panel">
      <a-card :bordered="false">
        <template #title>
          <div class="panel-header">
            <span class="panel-title">模型提供商</span>
          </div>
        </template>
        <div class="filter-tabs">
          <a-radio-group v-model:value="filterType" button-style="solid" size="small">
            <a-radio-button value="all">全部</a-radio-button>
            <a-radio-button value="public">公有</a-radio-button>
            <a-radio-button value="local">本地</a-radio-button>
          </a-radio-group>
        </div>
        <div class="provider-list">
          <div
            v-for="provider in providers"
            :key="provider.key"
            class="provider-item"
            :class="{ active: selectedProvider === provider.key }"
            @click="selectedProvider = provider.key"
          >
            <component :is="provider.icon" class="provider-icon" />
            <span class="provider-name">{{ provider.name }}</span>
            <span class="provider-count">{{ getProviderCount(provider.key) }}</span>
          </div>
        </div>
      </a-card>
    </div>

    <div class="content-panel">
      <a-card :bordered="false">
        <template #title>
          <div class="card-header">
            <span class="card-title">模型列表</span>
            <a-button type="primary" @click="handleAdd">
              <template #icon><PlusOutlined /></template>
              新增模型
            </a-button>
          </div>
        </template>

        <div class="search-form">
          <a-input-search
            v-model:value="searchKeyword"
            placeholder="搜索模型名称或ID"
            allow-clear
            style="width: 300px"
            @search="handleSearch"
            @press-enter="handleSearch"
          />
          <a-button style="margin-left: 8px" @click="handleReset">
            <template #icon><ReloadOutlined /></template>
            重置
          </a-button>
        </div>

        <a-spin :spinning="loading">
          <div class="model-list">
            <a-empty v-if="!loading && filteredModels.length === 0" description="暂无模型数据">
              <a-button type="primary" @click="handleAdd">新增模型</a-button>
            </a-empty>

            <div v-else class="model-grid">
              <div
                v-for="model in filteredModels"
                :key="model.id"
                class="model-card"
                :class="{ 'is-default': model.isDefault }"
              >
                <div class="card-header-inner">
                  <div class="model-icon">
                    <img v-if="model.icon" :src="model.icon" :alt="model.name" />
                    <RobotOutlined v-else />
                  </div>
                  <div class="model-info">
                    <div class="model-name">
                      {{ model.name }}
                      <a-tag v-if="model.isBuiltIn" color="orange" size="small">内置</a-tag>
                    </div>
                    <div class="model-tags">
                      <a-tag :color="getModelTypeColor(model.modelType)" size="small">
                        {{ getModelTypeText(model.modelType) }}
                      </a-tag>
                      <a-tag v-if="model.isLocal" size="small">本地</a-tag>
                      <a-tag v-else color="blue" size="small">公有</a-tag>
                    </div>
                  </div>
                  <a-button
                    type="text"
                    class="star-btn"
                    :class="{ active: model.isDefault }"
                    @click="handleSetDefault(model)"
                  >
                    <template #icon><StarFilled v-if="model.isDefault" /><StarOutlined v-else /></template>
                  </a-button>
                </div>

                <div class="card-body">
                  <div class="model-detail">
                    <span class="detail-label">基础模型</span>
                    <span class="detail-value">{{ model.modelId }}</span>
                  </div>
                  <div class="model-detail">
                    <span class="detail-label">状态</span>
                    <span class="detail-value status" :class="{ enabled: model.status === 1 }">
                      <span class="status-dot"></span>
                      {{ model.status === 1 ? '启用' : '禁用' }}
                    </span>
                  </div>
                </div>

                <div class="card-footer">
                  <span class="time">{{ formatDate(model.createTime) }}</span>
                  <div class="actions" @click.stop>
                    <a-button type="link" size="small" @click="handleEdit(model)">编辑</a-button>
                    <a-switch
                      :checked="model.status === 1"
                      size="small"
                      @change="(checked: boolean) => handleStatusChange(model, checked)"
                    />
                    <a-popconfirm title="确定要删除该模型吗？" @confirm="handleDelete(model)">
                      <a-button type="link" size="small" danger>删除</a-button>
                    </a-popconfirm>
                  </div>
                </div>
              </div>

              <div class="model-card add-card" @click="handleAdd">
                <PlusOutlined />
                <span>新增模型</span>
              </div>
            </div>
          </div>
        </a-spin>
      </a-card>
    </div>

    <a-modal
      v-model:open="dialogVisible"
      :title="dialogTitle"
      :width="600"
      ok-text="确认"
      cancel-text="取消"
      @ok="handleSubmit"
      @cancel="dialogVisible = false"
    >
      <a-form :model="formData" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="模型名称" required>
          <a-input v-model:value="formData.name" placeholder="请输入模型名称" />
        </a-form-item>
        <a-form-item label="提供商" required>
          <a-select v-model:value="formData.provider" placeholder="请选择提供商">
            <a-select-option value="local">Local (内置)</a-select-option>
            <a-select-option value="openai">OpenAI</a-select-option>
            <a-select-option value="gemini">Gemini</a-select-option>
            <a-select-option value="ollama">Ollama</a-select-option>
            <a-select-option value="deepseek">DeepSeek</a-select-option>
            <a-select-option value="vllm">vLLM</a-select-option>
            <a-select-option value="azure">Azure OpenAI</a-select-option>
            <a-select-option value="alibaba">阿里云百炼</a-select-option>
            <a-select-option value="zhipu">智谱AI</a-select-option>
            <a-select-option value="volcano">火山引擎(豆包)</a-select-option>
            <a-select-option value="moonshot">Moonshot</a-select-option>
            <a-select-option value="silicon">SiliconFlow (硅基流动)</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="模型类型" required>
          <a-select v-model:value="formData.modelType" placeholder="请选择模型类型">
            <a-select-option value="chat">语言模型</a-select-option>
            <a-select-option value="embedding">向量模型</a-select-option>
            <a-select-option value="reranker">多路召回</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="模型ID" required>
          <a-input v-model:value="formData.modelId" placeholder="如: gpt-4, qwen-turbo" />
        </a-form-item>
        <a-form-item label="API Key">
          <a-input-password v-model:value="formData.apiKey" placeholder="可选填，用于调用API" />
        </a-form-item>
        <a-form-item label="自定义地址">
          <a-input v-model:value="formData.baseUrl" placeholder="可选，自定义API地址" />
        </a-form-item>
        <a-form-item label="Temperature">
          <a-slider v-model:value="formData.temperature" :min="0" :max="2" :step="0.1" />
        </a-form-item>
        <a-form-item label="最大Token">
          <a-input-number v-model:value="formData.maxTokens" :min="100" :max="4000" :step="100" style="width: 100%" />
        </a-form-item>
        <a-form-item label="是否本地">
          <a-switch :checked="formData.isLocal === 1" @change="(checked: boolean) => formData.isLocal = checked ? 1 : 0" />
        </a-form-item>
        <a-form-item label="是否内置">
          <a-switch :checked="formData.isBuiltIn === 1" @change="(checked: boolean) => formData.isBuiltIn = checked ? 1 : 0" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import {
  PlusOutlined,
  StarOutlined,
  StarFilled,
  RobotOutlined,
  CloudOutlined,
  CodeOutlined,
  ThunderboltOutlined,
  ApiOutlined,
  SafetyOutlined,
  ReloadOutlined,
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { getModelList, createModel, updateModel, deleteModel, setModelDefault } from '@/api/model'
import type { ModelConfig } from '@/api/model'

interface ExtendedModelConfig extends ModelConfig {
  icon?: string
  isLocal?: boolean
  isBuiltIn?: boolean
}

const loading = ref(false)
const searchKeyword = ref('')
const filterType = ref('all')
const selectedProvider = ref('all')
const tableData = ref<ExtendedModelConfig[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('添加模型')
const isEdit = ref(false)

const formData = reactive<Partial<ExtendedModelConfig>>({
  name: '',
  provider: '',
  modelType: 'chat',
  modelId: '',
  apiKey: '',
  baseUrl: '',
  temperature: 0.7,
  maxTokens: 2000,
  status: 1,
  isLocal: 0,
  isBuiltIn: 0,
})

const providers = [
  { key: 'all', name: '全部', icon: CloudOutlined },
  { key: 'openai', name: 'OpenAI', icon: ApiOutlined },
  { key: 'gemini', name: 'Gemini', icon: ThunderboltOutlined },
  { key: 'ollama', name: 'Ollama', icon: CodeOutlined },
  { key: 'deepseek', name: 'DeepSeek', icon: SafetyOutlined },
  { key: 'vllm', name: 'vLLM', icon: ApiOutlined },
  { key: 'azure', name: 'Azure OpenAI', icon: CloudOutlined },
  { key: 'alibaba', name: '阿里云百炼', icon: CloudOutlined },
  { key: 'zhipu', name: '智谱AI', icon: ApiOutlined },
  { key: 'volcano', name: '火山引擎(豆包)', icon: ThunderboltOutlined },
  { key: 'moonshot', name: 'Moonshot', icon: RobotOutlined },
  { key: 'silicon', name: 'SiliconFlow (硅基流动)', icon: CloudOutlined },
]

const filteredModels = computed(() => {
  let models = tableData.value

  if (selectedProvider.value !== 'all') {
    models = models.filter(m => m.provider === selectedProvider.value)
  }

  if (filterType.value === 'public') {
    models = models.filter(m => !m.isLocal)
  } else if (filterType.value === 'local') {
    models = models.filter(m => m.isLocal)
  }

  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase()
    models = models.filter(m =>
      m.name.toLowerCase().includes(keyword) ||
      m.modelId.toLowerCase().includes(keyword)
    )
  }

  return models
})

function getProviderCount(providerKey: string): number {
  if (providerKey === 'all') return tableData.value.length
  return tableData.value.filter(m => m.provider === providerKey).length
}

function getModelTypeColor(type: string): string {
  const map: Record<string, string> = {
    chat: 'blue',
    embedding: 'green',
    reranker: 'orange',
  }
  return map[type] || 'default'
}

function getModelTypeText(type: string): string {
  const map: Record<string, string> = {
    chat: '语言模型',
    embedding: '向量模型',
    reranker: '多路召回',
  }
  return map[type] || type
}

function formatDate(dateStr: string): string {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  })
}

async function loadData() {
  loading.value = true
  try {
    const res = await getModelList({ page: 1, pageSize: 100, keyword: undefined })
    tableData.value = (res.data?.records || []).map((item: any) => ({
      ...item,
      isLocal: item.provider === 'local' || item.provider === 'ollama' || item.provider === 'vllm',
      isBuiltIn: item.name.includes('内置') || item.provider === 'local',
    }))
  } catch (error) {
    tableData.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  // 搜索已通过 computed 实现
}

function handleReset() {
  searchKeyword.value = ''
  filterType.value = 'all'
  selectedProvider.value = 'all'
}

function handleAdd() {
  isEdit.value = false
  dialogTitle.value = '新增模型'
  Object.assign(formData, {
    name: '',
    provider: '',
    modelType: 'chat',
    modelId: '',
    apiKey: '',
    baseUrl: '',
    temperature: 0.7,
    maxTokens: 2000,
    status: 1,
    isLocal: 0,
    isBuiltIn: 0,
  })
  dialogVisible.value = true
}

function handleEdit(record: ExtendedModelConfig) {
  isEdit.value = true
  dialogTitle.value = '编辑模型'
  Object.assign(formData, { ...record })
  dialogVisible.value = true
}

async function handleSubmit() {
  try {
    // 确保 isLocal 和 isBuiltIn 是数字类型
    const submitData = {
      ...formData,
      isLocal: formData.isLocal ? 1 : 0,
      isBuiltIn: formData.isBuiltIn ? 1 : 0,
    }

    if (isEdit.value && formData.id) {
      await updateModel(formData.id, submitData)
      message.success('更新成功')
    } else {
      await createModel(submitData)
      message.success('添加成功')
    }
    dialogVisible.value = false
    loadData()
  } catch (error) {
    message.error('操作失败')
  }
}

async function handleDelete(record: ExtendedModelConfig) {
  try {
    await deleteModel(record.id)
    message.success('删除成功')
    loadData()
  } catch (error) {
    message.error('删除失败')
  }
}

async function handleSetDefault(record: ExtendedModelConfig) {
  try {
    await setModelDefault(record.id)
    message.success('设置成功')
    loadData()
  } catch (error) {
    message.error('设置失败')
  }
}

async function handleStatusChange(record: ExtendedModelConfig, checked: boolean) {
  record.status = checked ? 1 : 0
  await updateModel(record.id, record)
  loadData()
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.model-container {
  display: flex;
  gap: 16px;
  height: calc(100vh - 64px - 48px);
  overflow: hidden;

  .provider-panel {
    width: 260px;
    flex-shrink: 0;
    height: 100%;

    :deep(.ant-card) {
      height: 100%;
      display: flex;
      flex-direction: column;

      .ant-card-head {
        flex-shrink: 0;
      }

      .ant-card-body {
        flex: 1;
        min-height: 0;
        display: flex;
        flex-direction: column;
        overflow: hidden;
      }
    }

    .panel-header {
      display: flex;
      justify-content: space-between;
      align-items: center;

      .panel-title {
        font-family: var(--font-display);
        font-size: 18px;
        font-weight: var(--font-weight-semibold);
      }
    }

    .filter-tabs {
      margin-bottom: 16px;
      flex-shrink: 0;

      :deep(.ant-radio-group) {
        width: 100%;
        display: flex;
      }

      :deep(.ant-radio-button-wrapper) {
        flex: 1;
        text-align: center;
      }
    }

    .provider-list {
      flex: 1;
      min-height: 0;
      overflow-y: auto;

      .provider-item {
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 10px 12px;
        border-radius: 8px;
        cursor: pointer;
        transition: all 0.3s;

        &:hover {
          background-color: #f5f7fa;
        }

        &.active {
          background-color: #e6f4ff;
          color: #1890ff;
        }

        .provider-icon {
          width: 20px;
          height: 20px;
          color: inherit;
        }

        .provider-name {
          flex: 1;
          font-size: 14px;
        }

        .provider-count {
          font-size: 12px;
          color: #909399;
        }
      }
    }
  }

  .content-panel {
    flex: 1;
    min-width: 0;
    height: 100%;

    :deep(.ant-card) {
      height: 100%;
      display: flex;
      flex-direction: column;

      .ant-card-head {
        flex-shrink: 0;
      }

      .ant-card-body {
        flex: 1;
        min-height: 0;
        display: flex;
        flex-direction: column;
        overflow: hidden;
      }
    }

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;

      .card-title {
        font-family: var(--font-display);
        font-size: 18px;
        font-weight: var(--font-weight-semibold);
      }
    }

    .search-form {
      margin-bottom: 16px;
      display: flex;
      align-items: center;
      flex-shrink: 0;
    }

    .model-list {
      flex: 1;
      min-height: 0;
      overflow-y: auto;
    }

    .model-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
      gap: 16px;
    }

    .model-card {
      background: #fff;
      border: 1px solid #e4e7ed;
      border-radius: 8px;
      padding: 16px;
      transition: all 0.3s;

      &:hover {
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
      }

      &.is-default {
        border-color: #1890ff;
      }

      .card-header-inner {
        display: flex;
        align-items: flex-start;
        gap: 12px;
        margin-bottom: 12px;

        .model-icon {
          width: 48px;
          height: 48px;
          background-color: #f5f7fa;
          border-radius: 8px;
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: 24px;
          color: #1890ff;

          img {
            width: 32px;
            height: 32px;
            object-fit: contain;
          }
        }

        .model-info {
          flex: 1;

          .model-name {
            font-size: 15px;
            font-weight: 500;
            margin-bottom: 8px;
            display: flex;
            align-items: center;
            gap: 8px;
          }

          .model-tags {
            display: flex;
            flex-wrap: wrap;
            gap: 4px;
          }
        }

        .star-btn {
          color: #909399;

          &.active {
            color: #faad14;
          }
        }
      }

      .card-body {
        margin-bottom: 12px;

        .model-detail {
          display: flex;
          justify-content: space-between;
          padding: 6px 0;
          font-size: 13px;

          .detail-label {
            color: #909399;
          }

          .detail-value {
            color: #303133;

            &.status {
              display: flex;
              align-items: center;
              gap: 4px;

              .status-dot {
                width: 6px;
                height: 6px;
                border-radius: 50%;
                background-color: #909399;
              }

              &.enabled {
                color: #67c23a;

                .status-dot {
                  background-color: #67c23a;
                }
              }
            }
          }
        }
      }

      .card-footer {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding-top: 12px;
        border-top: 1px solid #f0f0f0;

        .time {
          font-size: 12px;
          color: #909399;
        }

        .actions {
          display: flex;
          align-items: center;
          gap: 8px;
        }
      }

      &.add-card {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 8px;
        min-height: 160px;
        border-style: dashed;
        cursor: pointer;
        color: #909399;

        &:hover {
          color: #1890ff;
          border-color: #1890ff;
        }

        :deep(.anticon) {
          font-size: 32px;
        }
      }
    }
  }
}

@media (max-width: 1200px) {
  .model-container {
    flex-direction: column;
    height: calc(100vh - 64px - 48px);
    overflow-y: auto;

    .provider-panel {
      width: 100%;
      height: auto;

      :deep(.ant-card) {
        height: auto;
      }

      .provider-list {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
        overflow: visible;

        .provider-item {
          padding: 8px 12px;

          .provider-count {
            display: none;
          }
        }
      }
    }

    .content-panel {
      height: auto;

      :deep(.ant-card) {
        height: auto;
      }

      .model-list {
        overflow: visible;
      }

      .model-grid {
        grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
      }
    }
  }
}
</style>
