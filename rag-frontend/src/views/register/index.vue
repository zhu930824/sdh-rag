<template>
  <div class="register-container">
    <div class="bg-animation">
      <div class="bg-shape shape-1"></div>
      <div class="bg-shape shape-2"></div>
      <div class="bg-shape shape-3"></div>
    </div>

    <div class="register-card">
      <div class="brand-section">
        <div class="brand-content">
          <div class="brand-icon">
            <ReadOutlined />
          </div>
          <h1 class="brand-title">智能知识库</h1>
          <p class="brand-subtitle">开启您的智能知识管理之旅</p>
          <div class="brand-features">
            <div class="feature-item">
              <UserOutlined />
              <span>快速注册</span>
            </div>
            <div class="feature-item">
              <LockOutlined />
              <span>安全可靠</span>
            </div>
            <div class="feature-item">
              <ApiOutlined />
              <span>知识共享</span>
            </div>
          </div>
        </div>
      </div>

      <div class="form-section">
        <div class="form-content">
          <div class="form-header">
            <h2 class="form-title">创建账号</h2>
            <p class="form-subtitle">填写以下信息完成注册</p>
          </div>

          <a-form
            ref="formRef"
            :model="registerForm"
            :rules="rules"
            class="register-form"
            @finish="handleRegister"
            @finishFailed="onFinishFailed"
          >
            <a-form-item name="username">
              <a-input
                v-model:value="registerForm.username"
                placeholder="请输入用户名"
                size="large"
                allow-clear
              >
                <template #prefix><UserOutlined /></template>
              </a-input>
            </a-form-item>

            <a-form-item name="nickname">
              <a-input
                v-model:value="registerForm.nickname"
                placeholder="请输入昵称（选填）"
                size="large"
                allow-clear
              >
                <template #prefix><SmileOutlined /></template>
              </a-input>
            </a-form-item>

            <a-form-item name="password">
              <a-input-password
                v-model:value="registerForm.password"
                placeholder="请输入密码"
                size="large"
              >
                <template #prefix><LockOutlined /></template>
              </a-input-password>
            </a-form-item>

            <a-form-item name="confirmPassword">
              <a-input-password
                v-model:value="registerForm.confirmPassword"
                placeholder="请确认密码"
                size="large"
              >
                <template #prefix><LockOutlined /></template>
              </a-input-password>
            </a-form-item>

            <a-form-item>
              <a-button
                type="primary"
                html-type="submit"
                :loading="loading"
                class="register-btn"
                block
              >
                {{ loading ? '注册中...' : '注 册' }}
              </a-button>
            </a-form-item>
          </a-form>

          <div class="form-footer">
            <span class="footer-text">已有账号？</span>
            <a class="login-link" @click="goToLogin">立即登录</a>
          </div>

          <div class="theme-switch">
            <a-tooltip :title="isDark ? '切换浅色模式' : '切换深色模式'">
              <a-switch v-model:checked="isDark" @change="toggleDark">
                <template #checkedChildren><MoonOutlined /></template>
                <template #unCheckedChildren><SunOutlined /></template>
              </a-switch>
            </a-tooltip>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, Rule } from 'ant-design-vue/es/form'
import {
  UserOutlined,
  SmileOutlined,
  LockOutlined,
  ReadOutlined,
  ApiOutlined,
  BulbOutlined,
  SunOutlined,
} from '@ant-design/icons-vue'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'

const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const isDark = ref(false)

const registerForm = reactive({
  username: '',
  nickname: '',
  password: '',
  confirmPassword: '',
})

const validateConfirmPassword = async (_rule: any, value: string) => {
  if (value === '') {
    return Promise.reject('请再次输入密码')
  } else if (value !== registerForm.password) {
    return Promise.reject('两次输入密码不一致')
  } else {
    return Promise.resolve()
  }
}

const rules: Record<string, Rule[]> = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度为3-20个字符', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]+$/, message: '用户名只能包含字母、数字和下划线', trigger: 'blur' },
  ],
  nickname: [
    { max: 20, message: '昵称最长为20个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度为6-20个字符', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, validator: validateConfirmPassword, trigger: 'blur' },
  ],
}

onMounted(() => {
  isDark.value = appStore.isDark
})

function toggleDark(): void {
  appStore.toggleDark()
}

function onFinishFailed(): void {
  // 表单验证失败时的处理
}

async function handleRegister(): Promise<void> {
  loading.value = true
  try {
    const success = await userStore.register({
      username: registerForm.username,
      password: registerForm.password,
      nickname: registerForm.nickname || undefined,
    })
    if (success) {
      router.push('/login')
    }
  } finally {
    loading.value = false
  }
}

function goToLogin(): void {
  router.push('/login')
}
</script>

<style scoped lang="scss">
.register-container {
  width: 100%;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);

  :global(html.dark) & {
    background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
  }
}

.bg-animation {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  overflow: hidden;
  z-index: 0;

  .bg-shape {
    position: absolute;
    border-radius: 50%;
    opacity: 0.1;
    animation: float 20s infinite ease-in-out;

    &.shape-1 {
      width: 400px;
      height: 400px;
      background: #fff;
      top: -100px;
      left: -100px;
      animation-delay: 0s;
    }

    &.shape-2 {
      width: 300px;
      height: 300px;
      background: #fff;
      bottom: -50px;
      right: -50px;
      animation-delay: -5s;
    }

    &.shape-3 {
      width: 200px;
      height: 200px;
      background: #fff;
      top: 50%;
      left: 50%;
      animation-delay: -10s;
    }
  }
}

@keyframes float {
  0%, 100% {
    transform: translate(0, 0) rotate(0deg);
  }
  25% {
    transform: translate(50px, 50px) rotate(90deg);
  }
  50% {
    transform: translate(0, 100px) rotate(180deg);
  }
  75% {
    transform: translate(-50px, 50px) rotate(270deg);
  }
}

.register-card {
  display: flex;
  width: 900px;
  max-width: 95%;
  min-height: 600px;
  background: var(--bg-overlay);
  border-radius: 20px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  overflow: hidden;
  position: relative;
  z-index: 1;
  animation: slideUp 0.6s ease-out;
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(30px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.brand-section {
  flex: 1;
  background: linear-gradient(135deg, #764ba2 0%, #667eea 100%);
  padding: 60px 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;

  :global(html.dark) & {
    background: linear-gradient(135deg, #1a202c 0%, #2d3748 100%);
  }

  &::before {
    content: '';
    position: absolute;
    top: -50%;
    left: -50%;
    width: 200%;
    height: 200%;
    background: radial-gradient(circle, rgba(255,255,255,0.1) 0%, transparent 60%);
    animation: rotate 30s linear infinite;
  }
}

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.brand-content {
  position: relative;
  z-index: 1;
  text-align: center;
  color: #fff;
}

.brand-icon {
  margin-bottom: 24px;
  font-size: 64px;
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.05);
  }
}

.brand-title {
  font-size: 32px;
  font-weight: 700;
  margin-bottom: 12px;
  letter-spacing: 2px;
}

.brand-subtitle {
  font-size: 16px;
  opacity: 0.9;
  margin-bottom: 40px;
}

.brand-features {
  display: flex;
  flex-direction: column;
  gap: 16px;

  .feature-item {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 12px;
    font-size: 14px;
    opacity: 0.9;
    transition: transform 0.3s ease;

    &:hover {
      transform: translateX(5px);
    }

    :deep(.anticon) {
      font-size: 20px;
    }
  }
}

.form-section {
  flex: 1;
  padding: 50px 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-overlay);
}

.form-content {
  width: 100%;
  max-width: 340px;
}

.form-header {
  text-align: center;
  margin-bottom: 28px;
}

.form-title {
  font-size: 28px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.form-subtitle {
  font-size: 14px;
  color: var(--text-secondary);
}

.register-form {
  :deep(.ant-form-item) {
    margin-bottom: 20px;
  }

  :deep(.ant-input-affix-wrapper) {
    border-radius: 8px;
    transition: all 0.3s ease;

    &:hover {
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
    }

    &.ant-input-affix-wrapper-focused {
      box-shadow: 0 4px 12px rgba(102, 126, 234, 0.15);
    }
  }

  .register-btn {
    width: 100%;
    height: 44px;
    font-size: 16px;
    font-weight: 500;
    border-radius: 8px;
    margin-top: 8px;
    transition: all 0.3s ease;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    border: none;

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
    }

    &:active {
      transform: translateY(0);
    }
  }
}

.form-footer {
  text-align: center;
  margin-top: 20px;
  font-size: 14px;
  color: var(--text-secondary);

  .footer-text {
    margin-right: 4px;
  }

  .login-link {
    color: var(--primary-color);
    cursor: pointer;

    &:hover {
      opacity: 0.8;
    }
  }
}

.theme-switch {
  position: absolute;
  top: 20px;
  right: 20px;
}

@media (max-width: 768px) {
  .register-card {
    flex-direction: column;
    width: 100%;
    max-width: 420px;
    min-height: auto;
  }

  .brand-section {
    padding: 40px 30px;
  }

  .brand-title {
    font-size: 24px;
  }

  .brand-features {
    display: none;
  }

  .form-section {
    padding: 40px 30px;
  }

  .form-title {
    font-size: 24px;
  }

  .theme-switch {
    top: 15px;
    right: 15px;
  }
}
</style>
