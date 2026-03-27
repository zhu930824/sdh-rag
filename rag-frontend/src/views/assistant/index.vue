<template>
  <div class="assistant-page">
    <div class="page-header">
      <h2>AI助手市场</h2>
      <p class="description">选择或创建适合您业务场景的AI助手</p>
    </div>

    <div class="category-tabs">
      <a-radio-group v-model:value="selectedCategory" button-style="solid" @change="loadAssistants">
        <a-radio-button value="">全部</a-radio-button>
        <a-radio-button value="customer_service">客服</a-radio-button>
        <a-radio-button value="hr">HR</a-radio-button>
        <a-radio-button value="it_support">IT支持</a-radio-button>
        <a-radio-button value="legal">法律</a-radio-button>
        <a-radio-button value="general">通用</a-radio-button>
      </a-radio-group>

      <a-input-search
        v-model:value="searchKeyword"
        placeholder="搜索助手"
        style="width: 200px; margin-left: 16px"
        @search="loadAssistants"
      />
    </div>

    <div class="assistant-grid">
      <div
        v-for="assistant in assistants"
        :key="assistant.id"
        class="assistant-card"
        @click="showDetail(assistant)"
      >
        <div class="card-icon">
          <img v-if="assistant.icon" :src="assistant.icon" :alt="assistant.name" />
          <RobotOutlined v-else />
        </div>
        <div class="card-content">
          <div class="card-title">
            {{ assistant.name }}
            <a-tag v-if="assistant.category" size="small">{{ getCategoryText(assistant.category) }}</a-tag>
          </div>
          <div class="card-desc">{{ assistant.description || '暂无描述' }}</div>
          <div class="card-stats">
            <span><EyeOutlined /> {{ assistant.useCount }}</span>
            <span><StarFilled /> {{ assistant.ratingAvg?.toFixed(1) || '暂无评分' }}</span>
          </div>
        </div>
        <div class="card-action">
          <a-button type="primary" size="small" @click.stop="useAssistant(assistant)">立即使用</a-button>
        </div>
      </div>

      <div class="assistant-card add-card" @click="showCreateModal">
        <PlusOutlined />
        <span>创建助手</span>
      </div>
    </div>

    <div class="pagination-wrapper" v-if="pagination.total > pagination.pageSize">
      <a-pagination
        v-model:current="pagination.current"
        :page-size="pagination.pageSize"
        :total="pagination.total"
        show-less-items
        @change="loadAssistants"
      />
    </div>

    <a-modal
      v-model:open="detailVisible"
      :title="currentAssistant?.name"
      :width="700"
      :footer="null"
    >
      <a-descriptions :column="2" v-if="currentAssistant">
        <a-descriptions-item label="分类">{{ getCategoryText(currentAssistant.category) }}</a-descriptions-item>
        <a-descriptions-item label="使用次数">{{ currentAssistant.useCount }}</a-descriptions-item>
        <a-descriptions-item label="平均评分">{{ currentAssistant.ratingAvg?.toFixed(1) || '暂无' }}</a-descriptions-item>
        <a-descriptions-item label="评分人数">{{ currentAssistant.ratingCount }}</a-descriptions-item>
        <a-descriptions-item label="欢迎语" :span="2">{{ currentAssistant.welcomeMessage }}</a-descriptions-item>
        <a-descriptions-item label="推荐问题" :span="2">
          <a-tag v-for="(q, i) in getSuggestedQuestions(currentAssistant.suggestedQuestions)" :key="i" style="margin: 4px">{{ q }}</a-tag>
        </a-descriptions-item>
      </a-descriptions>
      
      <div class="modal-footer">
        <a-button type="primary" @click="useAssistant(currentAssistant)">立即使用</a-button>
      </div>
    </a-modal>

    <a-modal
      v-model:open="createVisible"
      title="创建AI助手"
      :width="600"
      @ok="handleCreate"
    >
      <a-form :model="formData" :label-col="{ span: 4 }" :wrapper-col="{ span: 18 }">
        <a-form-item label="名称" required>
          <a-input v-model:value="formData.name" placeholder="请输入助手名称" />
        </a-form-item>
        <a-form-item label="分类" required>
          <a-select v-model:value="formData.category" placeholder="请选择分类">
            <a-select-option value="customer_service">客服</a-select-option>
            <a-select-option value="hr">HR</a-select-option>
            <a-select-option value="it_support">IT支持</a-select-option>
            <a-select-option value="legal">法律</a-select-option>
            <a-select-option value="general">通用</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="formData.description" :rows="2" placeholder="描述助手的功能" />
        </a-form-item>
        <a-form-item label="系统提示词">
          <a-textarea v-model:value="formData.systemPrompt" :rows="3" placeholder="设定助手的角色和行为规则" />
        </a-form-item>
        <a-form-item label="欢迎语">
          <a-input v-model:value="formData.welcomeMessage" placeholder="助手首次打招呼的内容" />
        </a-form-item>
        <a-form-item label="是否公开">
          <a-switch v-model:checked="formData.isPublic" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { RobotOutlined, PlusOutlined, EyeOutlined, StarFilled } from '@ant-design/icons-vue'
import { getPublicAssistants, getAssistantDetail, createAssistant, type AiAssistant } from '@/api/assistant'

const loading = ref(false)
const assistants = ref<AiAssistant[]>([])
const selectedCategory = ref('')
const searchKeyword = ref('')
const detailVisible = ref(false)
const createVisible = ref(false)
const currentAssistant = ref<AiAssistant | null>(null)

const pagination = reactive({
  current: 1,
  pageSize: 12,
  total: 0,
})

const formData = reactive({
  name: '',
  category: '',
  description: '',
  systemPrompt: '',
  welcomeMessage: '',
  isPublic: true,
})

function getCategoryText(category: string): string {
  const map: Record<string, string> = {
    customer_service: '客服',
    hr: 'HR',
    it_support: 'IT支持',
    legal: '法律',
    general: '通用',
  }
  return map[category] || category
}

function getSuggestedQuestions(json: string | undefined): string[] {
  if (!json) return []
  try {
    return JSON.parse(json)
  } catch {
    return []
  }
}

async function loadAssistants() {
  loading.value = true
  try {
    const res = await getPublicAssistants({
      page: pagination.current,
      pageSize: pagination.pageSize,
      category: selectedCategory.value || undefined,
    })
    if (res.code === 200) {
      assistants.value = res.data?.records || []
      pagination.total = res.data?.total || 0
    }
  } catch (error) {
    message.error('加载失败')
  } finally {
    loading.value = false
  }
}

function showDetail(assistant: AiAssistant) {
  currentAssistant.value = assistant
  detailVisible.value = true
}

function showCreateModal() {
  Object.assign(formData, {
    name: '',
    category: '',
    description: '',
    systemPrompt: '',
    welcomeMessage: '',
    isPublic: true,
  })
  createVisible.value = true
}

async function handleCreate() {
  if (!formData.name || !formData.category) {
    message.warning('请填写必要信息')
    return
  }
  try {
    await createAssistant({
      name: formData.name,
      category: formData.category,
      description: formData.description,
      systemPrompt: formData.systemPrompt,
      welcomeMessage: formData.welcomeMessage,
      isPublic: formData.isPublic ? 1 : 0,
    })
    message.success('创建成功')
    createVisible.value = false
    loadAssistants()
  } catch (error) {
    message.error('创建失败')
  }
}

function useAssistant(assistant: AiAssistant | null) {
  if (!assistant) return
  // TODO: 跳转到聊天页面并使用该助手
  message.info(`即将使用助手: ${assistant.name}`)
}

onMounted(() => {
  loadAssistants()
})
</script>

<style scoped lang="scss">
.assistant-page {
  padding: 24px;

  .page-header {
    margin-bottom: 24px;
    h2 { margin: 0 0 8px; font-size: 20px; font-weight: 600; }
    .description { color: var(--text-secondary); font-size: 14px; }
  }

  .category-tabs {
    display: flex;
    align-items: center;
    margin-bottom: 24px;
    padding: 16px;
    background-color: var(--bg-color);
    border-radius: var(--border-radius-base);
  }

  .assistant-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
    gap: 16px;
  }

  .assistant-card {
    background-color: var(--bg-color);
    border: 1px solid var(--border-lighter);
    border-radius: var(--border-radius-large);
    padding: 20px;
    cursor: pointer;
    transition: all 0.3s;

    &:hover {
      box-shadow: var(--box-shadow);
      transform: translateY(-2px);
    }

    .card-icon {
      width: 48px;
      height: 48px;
      background-color: var(--primary-light-9);
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 16px;
      font-size: 24px;
      color: var(--primary-color);

      img { width: 32px; height: 32px; object-fit: contain; }
    }

    .card-content {
      .card-title {
        display: flex;
        align-items: center;
        gap: 8px;
        margin-bottom: 8px;
        font-size: 16px;
        font-weight: 500;
      }
      .card-desc {
        color: var(--text-secondary);
        font-size: 13px;
        margin-bottom: 12px;
        line-height: 1.5;
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
        overflow: hidden;
      }
      .card-stats {
        display: flex;
        gap: 16px;
        color: var(--text-secondary);
        font-size: 12px;
        span { display: flex; align-items: center; gap: 4px; }
      }
    }

    .card-action {
      margin-top: 16px;
      padding-top: 16px;
      border-top: 1px solid var(--border-lighter);
    }

    &.add-card {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      min-height: 200px;
      border-style: dashed;
      color: var(--text-secondary);
      gap: 8px;

      &:hover { color: var(--primary-color); border-color: var(--primary-color); }
    }
  }

  .pagination-wrapper {
    display: flex;
    justify-content: flex-end;
    margin-top: 24px;
  }

  .modal-footer {
    margin-top: 24px;
    text-align: right;
  }
}
</style>