<template>
  <div class="login-page">
    <!-- Left Panel - Brand -->
    <div class="brand-panel">
      <div class="brand-bg">
        <div class="leaf-shape leaf-1"></div>
        <div class="leaf-shape leaf-2"></div>
        <div class="leaf-shape leaf-3"></div>
      </div>
      <div class="brand-content">
        <div class="logo">
          <svg viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect width="48" height="48" rx="14" fill="#059669"/>
            <path d="M15 24L20 29L33 17" stroke="white" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
        <h1 class="brand-title">智能知识库</h1>
        <p class="brand-subtitle">基于 RAG 技术的智能问答平台</p>

        <div class="features">
          <div class="feature">
            <div class="feature-icon">
              <FileTextOutlined />
            </div>
            <div class="feature-info">
              <h3>智能文档管理</h3>
              <p>支持多种格式，自动解析</p>
            </div>
          </div>
          <div class="feature">
            <div class="feature-icon">
              <MessageOutlined />
            </div>
            <div class="feature-info">
              <h3>精准问答检索</h3>
              <p>AI 驱动，秒级响应</p>
            </div>
          </div>
          <div class="feature">
            <div class="feature-icon">
              <ApartmentOutlined />
            </div>
            <div class="feature-info">
              <h3>知识图谱分析</h3>
              <p>可视化展示知识关联</p>
            </div>
          </div>
        </div>
      </div>

      <div class="brand-footer">
        <span>© 2026 智能知识库. All rights reserved.</span>
      </div>
    </div>

    <!-- Right Panel - Form -->
    <div class="form-panel">
      <div class="form-container">
        <!-- Theme Toggle -->
        <div class="theme-toggle">
          <button class="icon-btn" @click="toggleDark" :title="isDark ? '浅色模式' : '深色模式'">
            <BulbFilled v-if="isDark" class="bulb-icon" />
            <BulbOutlined v-else class="bulb-icon" />
          </button>
        </div>

        <!-- Form Header -->
        <div class="form-header">
          <h2 class="form-title">欢迎回来</h2>
          <p class="form-subtitle">请登录您的账号继续使用</p>
        </div>

        <!-- Login Form -->
        <a-form
          ref="formRef"
          :model="loginForm"
          :rules="rules"
          class="login-form"
          layout="vertical"
          @finish="handleLogin"
        >
          <a-form-item label="用户名" name="username">
            <a-input
              v-model:value="loginForm.username"
              placeholder="请输入用户名"
              size="large"
              allow-clear
            >
              <template #prefix><UserOutlined /></template>
            </a-input>
          </a-form-item>

          <a-form-item label="密码" name="password">
            <a-input-password
              v-model:value="loginForm.password"
              placeholder="请输入密码"
              size="large"
            >
              <template #prefix><LockOutlined /></template>
            </a-input-password>
          </a-form-item>

          <a-form-item>
            <div class="form-options">
              <a-checkbox v-model:checked="loginForm.remember">记住密码</a-checkbox>
              <a class="forgot-link">忘记密码？</a>
            </div>
          </a-form-item>

          <a-form-item>
            <a-button
              type="primary"
              html-type="submit"
              :loading="loading"
              class="submit-btn"
              block
              size="large"
            >
              {{ loading ? '登录中...' : '登录' }}
            </a-button>
          </a-form-item>
        </a-form>

        <!-- Register Link -->
        <div class="form-footer">
          <span>还没有账号？</span>
          <a class="register-link" @click="goToRegister">立即注册</a>
        </div>

        <!-- Social Login -->
        <div class="social-login">
          <a-divider>
            <span class="divider-text">其他登录方式</span>
          </a-divider>
          <div class="social-buttons">
            <button class="social-btn" title="微信登录">
              <WechatOutlined />
            </button>
            <button class="social-btn" title="GitHub登录">
              <GithubOutlined />
            </button>
            <button class="social-btn" title="邮箱登录">
              <MailOutlined />
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import type { FormInstance, Rule } from 'ant-design-vue/es/form'
import {
  UserOutlined,
  LockOutlined,
  FileTextOutlined,
  MessageOutlined,
  ApartmentOutlined,
  WechatOutlined,
  GithubOutlined,
  MailOutlined,
  BulbOutlined,
  BulbFilled,
} from '@ant-design/icons-vue'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const appStore = useAppStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const isDark = ref(false)

const loginForm = reactive({
  username: '',
  password: '',
  remember: false,
})

const rules: Record<string, Rule[]> = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度为3-20个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度为6-20个字符', trigger: 'blur' },
  ],
}

onMounted(() => {
  isDark.value = appStore.isDark
  const savedUsername = localStorage.getItem('rememberedUsername')
  if (savedUsername) {
    loginForm.username = savedUsername
    loginForm.remember = true
  }
})

function toggleDark(): void {
  appStore.toggleDark()
  isDark.value = !isDark.value
}

async function handleLogin(): Promise<void> {
  loading.value = true
  try {
    const success = await userStore.login(loginForm)
    if (success) {
      if (loginForm.remember) {
        localStorage.setItem('rememberedUsername', loginForm.username)
      } else {
        localStorage.removeItem('rememberedUsername')
      }
      const redirect = (route.query.redirect as string) || '/'
      router.push(redirect)
    }
  } finally {
    loading.value = false
  }
}

function goToRegister(): void {
  router.push('/register')
}
</script>

<style scoped lang="scss">
.login-page {
  display: flex;
  min-height: 100vh;
  background: var(--bg-body);
}

// Brand Panel (Left) - Nature Style
.brand-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 60px;
  background: linear-gradient(135deg, #059669 0%, #228B22 50%, #6B8E4E 100%);
  color: white;
  position: relative;
  overflow: hidden;

  html.dark & {
    background: linear-gradient(135deg, #065F46 0%, #064E3B 50%, #022C22 100%);
  }
}

.brand-bg {
  position: absolute;
  inset: 0;
  overflow: hidden;

  .leaf-shape {
    position: absolute;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.06);

    &.leaf-1 {
      width: 300px;
      height: 300px;
      top: -100px;
      right: -50px;
    }

    &.leaf-2 {
      width: 200px;
      height: 200px;
      bottom: -50px;
      left: -50px;
    }

    &.leaf-3 {
      width: 150px;
      height: 150px;
      top: 40%;
      right: 20%;
    }
  }
}

.brand-content {
  position: relative;
  z-index: 1;
  max-width: 480px;
}

.logo {
  width: 56px;
  height: 56px;
  margin-bottom: 32px;

  svg {
    width: 100%;
    height: 100%;
    filter: drop-shadow(0 4px 12px rgba(0, 0, 0, 0.15));
  }
}

.brand-title {
  font-family: var(--font-serif);
  font-size: 36px;
  font-weight: 600;
  margin-bottom: 12px;
  letter-spacing: -0.02em;
}

.brand-subtitle {
  font-size: 18px;
  opacity: 0.9;
  margin-bottom: 48px;
  font-weight: 400;
}

.features {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.feature {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.feature-icon {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.15);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
  backdrop-filter: blur(8px);
}

.feature-info {
  h3 {
    font-family: var(--font-serif);
    font-size: 16px;
    font-weight: 500;
    margin-bottom: 4px;
  }

  p {
    font-size: 14px;
    opacity: 0.8;
    margin: 0;
  }
}

.brand-footer {
  position: absolute;
  bottom: 24px;
  left: 60px;
  font-size: 13px;
  opacity: 0.7;
}

// Form Panel (Right)
.form-panel {
  width: 520px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
  background: var(--bg-surface);
}

.form-container {
  width: 100%;
  max-width: 360px;
  position: relative;
}

.theme-toggle {
  position: absolute;
  top: -20px;
  right: -20px;
}

.icon-btn {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-surface-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  color: var(--text-secondary);
  font-size: 18px;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-nature);

  &:hover {
    background: var(--bg-surface-tertiary);
    color: var(--text-primary);
  }
}

// Use BulbOutlined for light mode, BulbFilled for dark mode indicator
.theme-toggle {
  :deep(.bulb-icon) {
    font-size: 18px;
  }
}

.form-header {
  margin-bottom: 32px;
}

.form-title {
  font-family: var(--font-serif);
  font-size: 28px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 8px;
  letter-spacing: -0.02em;
}

.form-subtitle {
  font-size: 15px;
  color: var(--text-secondary);
}

.login-form {
  :deep(.ant-form-item-label > label) {
    font-weight: 500;
    color: var(--text-primary);
  }

  :deep(.ant-input-affix-wrapper) {
    padding: 12px 16px;
    border-radius: var(--radius-lg);
    background: var(--bg-surface-secondary);
    border: 1px solid transparent;

    &:hover {
      background: var(--bg-surface);
      border-color: var(--border-color);
    }

    &.ant-input-affix-wrapper-focused {
      background: var(--bg-surface);
      border-color: var(--primary-color);
      box-shadow: 0 0 0 3px rgba(5, 150, 105, 0.1);
    }
  }

  :deep(.ant-input-prefix) {
    color: var(--text-tertiary);
    margin-right: 12px;
  }

  :deep(.ant-input-affix-wrapper-focused .ant-input-prefix) {
    color: var(--primary-color);
  }
}

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.forgot-link {
  color: var(--primary-color);
  font-size: var(--font-size-sm);
  cursor: pointer;

  &:hover {
    text-decoration: underline;
  }
}

.submit-btn {
  height: 48px;
  font-size: 16px;
  font-weight: 600;
  border-radius: var(--radius-lg);
  background: var(--primary-color);
  border: none;
  transition: all var(--duration-fast) var(--ease-nature);

  &:hover {
    background: var(--primary-hover);
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgba(5, 150, 105, 0.3);
  }

  &:active {
    transform: translateY(0);
  }
}

.form-footer {
  text-align: center;
  margin-top: 24px;
  font-size: var(--font-size-sm);
  color: var(--text-secondary);

  a {
    color: var(--primary-color);
    font-weight: 500;
    cursor: pointer;
    margin-left: 4px;

    &:hover {
      text-decoration: underline;
    }
  }
}

.social-login {
  margin-top: 32px;

  .divider-text {
    font-size: var(--font-size-sm);
    color: var(--text-tertiary);
    padding: 0 16px;
  }
}

.social-buttons {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-top: 20px;
}

.social-btn {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-surface-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  color: var(--text-secondary);
  font-size: 20px;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-nature);

  &:hover {
    background: var(--bg-surface-tertiary);
    border-color: var(--primary-color);
    color: var(--primary-color);
    transform: translateY(-2px);
  }
}

// Dark Mode
html.dark {
  .form-panel {
    background: var(--bg-page);
  }
}

// Responsive
@media (max-width: 1024px) {
  .brand-panel {
    display: none;
  }

  .form-panel {
    width: 100%;
  }
}

@media (max-width: 480px) {
  .form-panel {
    padding: 24px;
  }

  .form-title {
    font-size: 24px;
  }
}
</style>