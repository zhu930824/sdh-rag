<template>
  <div class="settings-page">
    <a-card class="settings-card">
      <template #title>
        <div class="card-header">
          <span class="title">系统设置</span>
        </div>
      </template>

      <a-tabs v-model:activeKey="activeTab" class="settings-tabs">
        <a-tab-pane key="basic" tab="基础设置">
          <a-form :model="settings" :label-col="{ span: 4 }" :wrapper-col="{ span: 16 }" class="settings-form">
            <a-form-item label="系统名称">
              <a-input v-model:value="settings.systemName" placeholder="请输入系统名称" />
            </a-form-item>
            <a-form-item label="系统Logo">
              <a-upload
                class="logo-uploader"
                action="#"
                :show-upload-list="false"
                :auto-upload="false"
              >
                <div class="upload-trigger">
                  <PlusOutlined class="upload-icon" />
                </div>
              </a-upload>
            </a-form-item>
            <a-form-item label="默认主题">
              <a-radio-group v-model:value="settings.defaultTheme">
                <a-radio value="light">浅色</a-radio>
                <a-radio value="dark">深色</a-radio>
                <a-radio value="auto">跟随系统</a-radio>
              </a-radio-group>
            </a-form-item>
            <a-form-item :wrapper-col="{ offset: 4, span: 16 }">
              <a-button type="primary" @click="saveSettings">保存设置</a-button>
            </a-form-item>
          </a-form>
        </a-tab-pane>

        <a-tab-pane key="model" tab="模型配置">
          <a-form :model="settings" :label-col="{ span: 4 }" :wrapper-col="{ span: 16 }" class="settings-form">
            <a-form-item label="默认模型">
              <a-select v-model:value="settings.defaultModel" placeholder="请选择默认模型">
                <a-select-option value="gpt-4">GPT-4</a-select-option>
                <a-select-option value="gpt-3.5-turbo">GPT-3.5 Turbo</a-select-option>
                <a-select-option value="claude-3">Claude 3</a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item label="Temperature">
              <a-slider v-model:value="settings.temperature" :min="0" :max="2" :step="0.1" />
            </a-form-item>
            <a-form-item label="最大Token数">
              <a-input-number v-model:value="settings.maxTokens" :min="100" :max="4000" :step="100" />
            </a-form-item>
            <a-form-item :wrapper-col="{ offset: 4, span: 16 }">
              <a-button type="primary" @click="saveSettings">保存设置</a-button>
            </a-form-item>
          </a-form>
        </a-tab-pane>

        <a-tab-pane key="notification" tab="通知设置">
          <a-form :model="settings" :label-col="{ span: 4 }" :wrapper-col="{ span: 16 }" class="settings-form">
            <a-form-item label="系统通知">
              <a-switch v-model:checked="settings.enableNotification" />
            </a-form-item>
            <a-form-item label="邮件通知">
              <a-switch v-model:checked="settings.enableEmail" />
            </a-form-item>
            <a-form-item label="通知邮箱">
              <a-input v-model:value="settings.notificationEmail" placeholder="请输入通知邮箱" />
            </a-form-item>
            <a-form-item :wrapper-col="{ offset: 4, span: 16 }">
              <a-button type="primary" @click="saveSettings">保存设置</a-button>
            </a-form-item>
          </a-form>
        </a-tab-pane>
      </a-tabs>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { showSuccess } from '@/utils/message'

const activeTab = ref('basic')

const settings = reactive({
  systemName: '智能知识库',
  defaultTheme: 'auto',
  defaultModel: 'gpt-3.5-turbo',
  temperature: 0.7,
  maxTokens: 2000,
  enableNotification: true,
  enableEmail: false,
  notificationEmail: '',
})

function saveSettings(): void {
  showSuccess('设置保存成功')
}
</script>

<style scoped lang="scss">
.settings-page {
  height: 100%;
}

.settings-card {
  height: 100%;

  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;

    .title {
      font-size: 16px;
      font-weight: 600;
    }
  }
}

.settings-tabs {
  :deep(.ant-tabs-content) {
    padding: 20px 0;
  }
}

.settings-form {
  max-width: 600px;
}

.logo-uploader {
  :deep(.ant-upload) {
    width: 100px;
    height: 100px;
    border: 1px dashed var(--border-color);
    border-radius: var(--border-radius-base);
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    transition: border-color var(--transition-duration);

    &:hover {
      border-color: var(--primary-color);
    }
  }

  .upload-trigger {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 100%;
    height: 100%;
  }

  .upload-icon {
    font-size: 28px;
    color: var(--text-secondary);
  }
}
</style>
