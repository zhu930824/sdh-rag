<template>
  <div class="memory-page">
    <a-card :bordered="false">
      <template #title>
        <div class="page-header">
          <div class="header-left">
            <h2>记忆中心</h2>
            <p class="subtitle">管理系统对您的记忆，提升对话体验</p>
          </div>
          <a-button danger @click="handleClearAll">
            <template #icon><DeleteOutlined /></template>
            清除所有记忆
          </a-button>
        </div>
      </template>

      <a-spin :spinning="memoryStore.loading">
        <a-tabs v-model:activeKey="activeTab">
          <!-- 偏好 -->
          <a-tab-pane key="preferences" tab="用户偏好">
            <div class="tab-header">
              <a-button type="primary" @click="showAddModal('preference')">
                <template #icon><PlusOutlined /></template>
                添加偏好
              </a-button>
            </div>
            <a-list
              :data-source="memoryStore.overview?.preferences || []"
              item-layout="horizontal"
            >
              <template #renderItem="{ item }">
                <a-list-item>
                  <a-list-item-meta :description="`重要性: ${item.importance}/10`">
                    <template #title>
                      <span>{{ item.content }}</span>
                    </template>
                  </a-list-item-meta>
                  <template #actions>
                    <a-button type="link" danger @click="handleDelete(item, 'SEMANTIC')">
                      删除
                    </a-button>
                  </template>
                </a-list-item>
              </template>
            </a-list>
          </a-tab-pane>

          <!-- 事实 -->
          <a-tab-pane key="facts" tab="知识事实">
            <a-list
              :data-source="memoryStore.overview?.facts || []"
              item-layout="horizontal"
            >
              <template #renderItem="{ item }">
                <a-list-item>
                  <a-list-item-meta :description="`重要性: ${item.importance}/10`">
                    <template #title>{{ item.content }}</template>
                  </a-list-item-meta>
                  <template #actions>
                    <a-button type="link" danger @click="handleDelete(item, 'SEMANTIC')">
                      删除
                    </a-button>
                  </template>
                </a-list-item>
              </template>
            </a-list>
          </a-tab-pane>

          <!-- 模式 -->
          <a-tab-pane key="patterns" tab="行为模式">
            <a-list
              :data-source="memoryStore.overview?.patterns || []"
              item-layout="horizontal"
            >
              <template #renderItem="{ item }">
                <a-list-item>
                  <a-list-item-meta :description="`置信度: ${item.importance * 10}%`">
                    <template #title>{{ item.content }}</template>
                  </a-list-item-meta>
                </a-list-item>
              </template>
            </a-list>
          </a-tab-pane>

          <!-- 摘要 -->
          <a-tab-pane key="summaries" tab="历史摘要">
            <a-list
              :data-source="memoryStore.overview?.recentSummaries || []"
              item-layout="vertical"
            >
              <template #renderItem="{ item }">
                <a-list-item>
                  <a-list-item-meta>
                    <template #title>{{ item.content }}</template>
                    <template #description>
                      {{ item.createdAt ? formatTime(item.createdAt) : '' }}
                    </template>
                  </a-list-item-meta>
                  <template #actions>
                    <a-button type="link" danger @click="handleDelete(item, 'ABSTRACT')">
                      删除
                    </a-button>
                  </template>
                </a-list-item>
              </template>
            </a-list>
          </a-tab-pane>
        </a-tabs>
      </a-spin>
    </a-card>

    <!-- 添加偏好弹窗 -->
    <a-modal
      v-model:open="addModalVisible"
      title="添加偏好"
      @ok="handleAddPreference"
      :confirm-loading="adding"
    >
      <a-form layout="vertical">
        <a-form-item label="偏好内容" required>
          <a-input v-model:value="newPreference.content" placeholder="例如：偏好中文回复" />
        </a-form-item>
        <a-form-item label="重要性">
          <a-slider v-model:value="newPreference.importance" :min="1" :max="10" :marks="{ 1: '低', 5: '中', 10: '高' }" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Modal, message } from 'ant-design-vue'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { useMemoryStore } from '@/stores/memory'
import type { MemoryItem } from '@/api/memory'

const memoryStore = useMemoryStore()

const activeTab = ref('preferences')
const addModalVisible = ref(false)
const adding = ref(false)

const newPreference = reactive({
  content: '',
  importance: 7,
})

onMounted(async () => {
  await memoryStore.fetchOverview()
})

function showAddModal(type: string) {
  newPreference.content = ''
  newPreference.importance = 7
  addModalVisible.value = true
}

async function handleAddPreference() {
  if (!newPreference.content.trim()) {
    message.warning('请输入偏好内容')
    return
  }

  adding.value = true
  try {
    await memoryStore.addPref(newPreference.content, newPreference.importance)
    addModalVisible.value = false
  } finally {
    adding.value = false
  }
}

async function handleDelete(item: MemoryItem, layer: string) {
  Modal.confirm({
    title: '确认删除',
    content: '确定要删除这条记忆吗？',
    async onOk() {
      await memoryStore.removeMemory(item.id, layer)
    },
  })
}

async function handleClearAll() {
  Modal.confirm({
    title: '确认清除',
    content: '确定要清除所有记忆吗？此操作不可恢复。',
    okType: 'danger',
    async onOk() {
      await memoryStore.clearAll()
    },
  })
}

function formatTime(time: string): string {
  return new Date(time).toLocaleString('zh-CN')
}
</script>

<style scoped lang="scss">
.memory-page {
  height: 100%;

  .ant-card {
    height: 100%;
  }

  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;

    .header-left {
      h2 {
        margin: 0 0 4px;
        font-size: 20px;
        font-weight: 600;
      }

      .subtitle {
        margin: 0;
        color: var(--text-secondary);
        font-size: 14px;
      }
    }
  }

  .tab-header {
    margin-bottom: 16px;
  }
}
</style>
