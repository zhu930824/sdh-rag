<template>
  <div class="settings-page">
    <el-card class="settings-card">
      <template #header>
        <div class="card-header">
          <span class="title">系统设置</span>
        </div>
      </template>

      <el-tabs v-model="activeTab" class="settings-tabs">
        <!-- 基础设置 -->
        <el-tab-pane label="基础设置" name="basic">
          <el-form label-width="120px" class="settings-form">
            <el-form-item label="系统名称">
              <el-input v-model="settings.systemName" placeholder="请输入系统名称" />
            </el-form-item>
            <el-form-item label="系统Logo">
              <el-upload
                class="logo-uploader"
                action="#"
                :show-file-list="false"
                :auto-upload="false"
              >
                <el-icon class="upload-icon"><Plus /></el-icon>
              </el-upload>
            </el-form-item>
            <el-form-item label="默认主题">
              <el-radio-group v-model="settings.defaultTheme">
                <el-radio value="light">浅色</el-radio>
                <el-radio value="dark">深色</el-radio>
                <el-radio value="auto">跟随系统</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveSettings">保存设置</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- 模型配置 -->
        <el-tab-pane label="模型配置" name="model">
          <el-form label-width="120px" class="settings-form">
            <el-form-item label="默认模型">
              <el-select v-model="settings.defaultModel" placeholder="请选择默认模型">
                <el-option label="GPT-4" value="gpt-4" />
                <el-option label="GPT-3.5 Turbo" value="gpt-3.5-turbo" />
                <el-option label="Claude 3" value="claude-3" />
              </el-select>
            </el-form-item>
            <el-form-item label="Temperature">
              <el-slider v-model="settings.temperature" :min="0" :max="2" :step="0.1" show-input />
            </el-form-item>
            <el-form-item label="最大Token数">
              <el-input-number v-model="settings.maxTokens" :min="100" :max="4000" :step="100" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveSettings">保存设置</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- 通知设置 -->
        <el-tab-pane label="通知设置" name="notification">
          <el-form label-width="120px" class="settings-form">
            <el-form-item label="系统通知">
              <el-switch v-model="settings.enableNotification" />
            </el-form-item>
            <el-form-item label="邮件通知">
              <el-switch v-model="settings.enableEmail" />
            </el-form-item>
            <el-form-item label="通知邮箱">
              <el-input v-model="settings.notificationEmail" placeholder="请输入通知邮箱" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveSettings">保存设置</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'

const activeTab = ref('basic')

// 设置数据
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

// 保存设置
function saveSettings(): void {
  // TODO: 调用API保存设置
  ElMessage.success('设置保存成功')
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
  :deep(.el-tabs__content) {
    padding: 20px 0;
  }
}

.settings-form {
  max-width: 600px;
}

.logo-uploader {
  :deep(.el-upload) {
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

  .upload-icon {
    font-size: 28px;
    color: var(--text-secondary);
  }
}
</style>
