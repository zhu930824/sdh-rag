<template>
  <div class="model-page">
    <div class="page-sidebar">
      <div class="filter-tabs">
        <a-radio-group v-model:value="filterType" button-style="solid" size="small">
          <a-radio-button value="all">全部</a-radio-button>
          <a-radio-button value="public">公有</a-radio-button>
          <a-radio-button value="local">本地</a-radio-button>
        </a-radio-group>
        <a-button type="text" size="small" @click="showSettings = true">
          <template #icon><SettingOutlined /></template>
        </a-button>
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
        </div>
      </div>
    </div>

    <div class="page-content">
      <div class="page-header">
        <h2 class="page-title">模型</h2>
        <div class="header-actions">
          <a-input-search
            v-model:value="searchKeyword"
            placeholder="请输入关键词"
            style="width: 200px"
            @search="handleSearch"
          />
          <a-button type="primary" @click="handleAdd">
            <template #icon><PlusOutlined /></template>
            新增模型
          </a-button>
        </div>
      </div>

      <div class="model-grid">
        <div
          v-for="model in filteredModels"
          :key="model.id"
          class="model-card"
          :class="{ 'is-default': model.isDefault }"
        >
          <div class="card-header">
            <div class="model-icon">
              <img v-if="model.icon" :src="model.icon" :alt="model.name" />
              <RobotOutlined v-else />
            </div>
            <div class="model-info">
              <div class="model-name">
                {{ model.name }}
                <a-tag v-if="model.isBuiltIn" color="orange">内置</a-tag>
              </div>
              <div class="model-tags">
                <a-tag :color="getModelTypeColor(model.modelType)">{{ getModelTypeText(model.modelType) }}</a-tag>
                <a-tag v-if="model.isLocal">本地</a-tag>
                <a-tag v-else color="blue">公有</a-tag>
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

          <div class="card-actions">
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

        <div class="model-card add-card" @click="handleAdd">
          <PlusOutlined />
          <span>新增模型</span>
        </div>
      </div>
    </div>

    <a-modal
      v-model:open="dialogVisible"
      :title="dialogTitle"
      :width="600"
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
          <a-switch v-model:checked="formData.isLocal" />
        </a-form-item>
        <a-form-item label="是否内置">
          <a-switch v-model:checked="formData.isBuiltIn" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import {
  PlusOutlined,
  SettingOutlined,
  StarOutlined,
  StarFilled,
  RobotOutlined,
  CloudOutlined,
  CodeOutlined,
  ThunderboltOutlined,
  ApiOutlined,
  SafetyOutlined,
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
const showSettings = ref(false)
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
  isLocal: false,
  isBuiltIn: false,
})

const providers = [
  { key: 'all', name: 'Local (内置)', icon: CloudOutlined },
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
  loadData()
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
    isLocal: false,
    isBuiltIn: false,
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
    if (isEdit.value && formData.id) {
      await updateModel(formData.id, formData)
      message.success('更新成功')
    } else {
      await createModel(formData)
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
.model-page {
  display: flex;
  height: calc(100vh - 100px);
  background-color: var(--bg-page);

  .page-sidebar {
    width: 200px;
    background-color: var(--bg-color);
    border-right: 1px solid var(--border-lighter);
    padding: 16px;
    margin-right: 10px;

    .filter-tabs {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 16px;

      :deep(.ant-radio-group) {
        flex: 1;
      }
    }

    .provider-list {
      .provider-item {
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 10px 12px;
        border-radius: var(--border-radius-base);
        cursor: pointer;
        transition: all var(--transition-duration);

        &:hover {
          background-color: var(--bg-page);
        }

        &.active {
          background-color: var(--primary-light-9);
          color: var(--primary-color);
        }

        .provider-icon {
          width: 20px;
          height: 20px;
          color: var(--text-secondary);
        }

        .provider-name {
          font-size: 14px;
        }
      }
    }
  }

  .page-content {
    flex: 1;
    padding: 24px;
    overflow-y: auto;

    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 24px;

      .page-title {
        font-size: 18px;
        font-weight: 600;
        color: var(--text-primary);
        margin: 0;
      }

      .header-actions {
        display: flex;
        align-items: center;
        gap: 12px;
      }
    }

    .model-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
      gap: 10px;
    }

    .model-card {
      background-color: var(--bg-color);
      border: 1px solid var(--border-lighter);
      border-radius: var(--border-radius-large);
      padding: 16px;
      transition: all var(--transition-duration);

      &:hover {
        box-shadow: var(--box-shadow);
      }

      &.is-default {
        border-color: var(--primary-color);
      }

      .card-header {
        display: flex;
        align-items: flex-start;
        gap: 12px;
        margin-bottom: 16px;

        .model-icon {
          width: 48px;
          height: 48px;
          background-color: var(--bg-page);
          border-radius: var(--border-radius-base);
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: 24px;
          color: var(--primary-color);

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
            color: var(--text-primary);
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
          color: var(--text-secondary);

          &.active {
            color: var(--warning-color);
          }
        }
      }

      .card-body {
        margin-bottom: 16px;

        .model-detail {
          display: flex;
          justify-content: space-between;
          padding: 6px 0;
          font-size: 13px;

          .detail-label {
            color: var(--text-secondary);
          }

          .detail-value {
            color: var(--text-primary);

            &.status {
              display: flex;
              align-items: center;
              gap: 4px;

              .status-dot {
                width: 6px;
                height: 6px;
                border-radius: 50%;
                background-color: var(--text-secondary);
              }

              &.enabled {
                color: var(--success-color);

                .status-dot {
                  background-color: var(--success-color);
                }
              }
            }
          }
        }
      }

      .card-actions {
        display: flex;
        align-items: center;
        justify-content: flex-end;
        gap: 8px;
        padding-top: 12px;
        border-top: 1px solid var(--border-lighter);
      }

      &.add-card {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 8px;
        min-height: 180px;
        border-style: dashed;
        cursor: pointer;
        color: var(--text-secondary);

        &:hover {
          color: var(--primary-color);
          border-color: var(--primary-color);
        }

        :deep(.anticon) {
          font-size: 32px;
        }
      }
    }
  }
}

@media (max-width: 768px) {
  .model-page {
    flex-direction: column;

    .page-sidebar {
      width: 100%;
      border-right: none;
      border-bottom: 1px solid var(--border-lighter);
    }
  }
}
</style>