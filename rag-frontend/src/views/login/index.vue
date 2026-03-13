<template>
  <div class="login-container">
    <!-- 粒子背景 -->
    <canvas ref="particleCanvas" class="particle-canvas"></canvas>

    <!-- 动态背景装饰 -->
    <div class="bg-decoration">
      <div class="bg-shape shape-1"></div>
      <div class="bg-shape shape-2"></div>
      <div class="bg-shape shape-3"></div>
      <div class="bg-shape shape-4"></div>
    </div>

    <!-- 登录卡片 -->
    <div class="login-card">
      <!-- 左侧品牌区域 -->
      <div class="brand-section">
        <div class="brand-content">
          <!-- Logo -->
          <div class="brand-logo">
            <div class="logo-icon-wrapper">
              <el-icon :size="48"><Reading /></el-icon>
            </div>
            <div class="logo-glow"></div>
            <div class="logo-ring"></div>
          </div>

          <h1 class="brand-title">智能知识库</h1>
          <p class="brand-subtitle">基于 RAG 技术的智能问答平台</p>

          <!-- 动态数据展示 -->
          <div class="brand-stats">
            <div class="stat-item" v-for="(stat, index) in brandStats" :key="index">
              <div class="stat-number">
                <CountUp :end-val="stat.value" :duration="2" />
              </div>
              <div class="stat-label">{{ stat.label }}</div>
            </div>
          </div>

          <div class="brand-features">
            <div class="feature-item" v-for="(feature, index) in features" :key="index">
              <div class="feature-icon">
                <el-icon><component :is="feature.icon" /></el-icon>
              </div>
              <div class="feature-text">
                <span class="feature-title">{{ feature.title }}</span>
                <span class="feature-desc">{{ feature.desc }}</span>
              </div>
            </div>
          </div>

          <!-- 装饰性元素 -->
          <div class="brand-decoration">
            <div class="decoration-line"></div>
            <div class="decoration-dot"></div>
          </div>
        </div>
      </div>

      <!-- 右侧登录表单 -->
      <div class="form-section">
        <div class="form-content">
          <!-- 深色模式切换 -->
          <div class="theme-switch">
            <el-tooltip :content="isDark ? '切换浅色模式' : '切换深色模式'" placement="left">
              <el-switch
                v-model="isDark"
                :active-icon="Moon"
                :inactive-icon="Sunny"
                @change="toggleDark"
              />
            </el-tooltip>
          </div>

          <div class="form-header">
            <h2 class="form-title">欢迎回来</h2>
            <p class="form-subtitle">请登录您的账号</p>
          </div>

          <el-form
            ref="formRef"
            :model="loginForm"
            :rules="rules"
            class="login-form"
            size="large"
            @keyup.enter="handleLogin"
          >
            <el-form-item prop="username">
              <el-input
                v-model="loginForm.username"
                placeholder="请输入用户名"
                :prefix-icon="User"
                clearable
              />
            </el-form-item>

            <el-form-item prop="password">
              <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="请输入密码"
                :prefix-icon="Lock"
                show-password
              />
            </el-form-item>

            <el-form-item>
              <div class="form-options">
                <el-checkbox v-model="loginForm.remember">记住密码</el-checkbox>
                <el-link type="primary" :underline="false">忘记密码？</el-link>
              </div>
            </el-form-item>

            <el-form-item>
              <el-button
                type="primary"
                :loading="loading"
                class="login-btn"
                @click="handleLogin"
              >
                <span v-if="!loading">登 录</span>
                <span v-else>登录中...</span>
              </el-button>
            </el-form-item>
          </el-form>

          <div class="form-footer">
            <span class="footer-text">还没有账号？</span>
            <el-link type="primary" :underline="false" @click="goToRegister">
              立即注册
            </el-link>
          </div>

          <!-- 其他登录方式 -->
          <div class="other-login">
            <el-divider>
              <span class="divider-text">其他登录方式</span>
            </el-divider>
            <div class="login-methods">
              <div class="method-item" title="微信登录">
                <el-icon :size="24"><ChatDotRound /></el-icon>
              </div>
              <div class="method-item" title="GitHub登录">
                <el-icon :size="24"><Link /></el-icon>
              </div>
              <div class="method-item" title="邮箱登录">
                <el-icon :size="24"><Message /></el-icon>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部版权信息 -->
    <div class="footer-info">
      <span>Copyright 2024 智能知识库系统. All rights reserved.</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, onUnmounted, defineComponent, h } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { type FormInstance, type FormRules } from 'element-plus'
import {
  User,
  Lock,
  Reading,
  Document,
  ChatDotRound,
  DataAnalysis,
  Moon,
  Sunny,
  Link,
  Message,
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const appStore = useAppStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const isDark = ref(false)
const particleCanvas = ref<HTMLCanvasElement>()

// 数字递增组件
const CountUp = defineComponent({
  props: {
    endVal: { type: Number, default: 0 },
    duration: { type: Number, default: 2 },
  },
  setup(props) {
    const currentVal = ref(0)
    let startTime: number | null = null
    let animationFrame: number | null = null

    const animate = (timestamp: number) => {
      if (!startTime) startTime = timestamp
      const progress = Math.min((timestamp - startTime) / (props.duration * 1000), 1)
      currentVal.value = Math.floor(progress * props.endVal)
      
      if (progress < 1) {
        animationFrame = requestAnimationFrame(animate)
      }
    }

    onMounted(() => {
      animationFrame = requestAnimationFrame(animate)
    })

    onUnmounted(() => {
      if (animationFrame) {
        cancelAnimationFrame(animationFrame)
      }
    })

    return () => h('span', currentVal.value.toLocaleString())
  },
})

// 粒子系统
interface Particle {
  x: number
  y: number
  vx: number
  vy: number
  radius: number
  opacity: number
}

let particles: Particle[] = []
let animationFrameId: number | null = null

// 功能特性
const features = [
  { icon: Document, title: '智能文档管理', desc: '支持多种格式' },
  { icon: ChatDotRound, title: '精准问答检索', desc: 'AI驱动' },
  { icon: DataAnalysis, title: '知识图谱分析', desc: '可视化展示' },
]

// 品牌统计数据
const brandStats = [
  { value: 256, label: '文档数量' },
  { value: 1024, label: '问答次数' },
  { value: 48, label: '用户数量' },
]

// 登录表单
const loginForm = reactive({
  username: '',
  password: '',
  remember: false,
})

// 表单验证规则
const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度为3-20个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度为6-20个字符', trigger: 'blur' },
  ],
}

// 初始化粒子系统
function initParticles(): void {
  const canvas = particleCanvas.value
  if (!canvas) return

  const ctx = canvas.getContext('2d')
  if (!ctx) return

  // 设置画布尺寸
  const resizeCanvas = () => {
    canvas.width = window.innerWidth
    canvas.height = window.innerHeight
  }
  resizeCanvas()
  window.addEventListener('resize', resizeCanvas)

  // 创建粒子
  const particleCount = 80
  particles = []
  for (let i = 0; i < particleCount; i++) {
    particles.push({
      x: Math.random() * canvas.width,
      y: Math.random() * canvas.height,
      vx: (Math.random() - 0.5) * 0.5,
      vy: (Math.random() - 0.5) * 0.5,
      radius: Math.random() * 2 + 1,
      opacity: Math.random() * 0.5 + 0.2,
    })
  }

  // 动画循环
  const animate = () => {
    ctx.clearRect(0, 0, canvas.width, canvas.height)

    // 更新和绘制粒子
    particles.forEach((particle, i) => {
      // 更新位置
      particle.x += particle.vx
      particle.y += particle.vy

      // 边界检测
      if (particle.x < 0 || particle.x > canvas.width) particle.vx *= -1
      if (particle.y < 0 || particle.y > canvas.height) particle.vy *= -1

      // 绘制粒子
      ctx.beginPath()
      ctx.arc(particle.x, particle.y, particle.radius, 0, Math.PI * 2)
      ctx.fillStyle = `rgba(255, 255, 255, ${particle.opacity})`
      ctx.fill()

      // 绘制连线
      particles.slice(i + 1).forEach((other) => {
        const dx = particle.x - other.x
        const dy = particle.y - other.y
        const distance = Math.sqrt(dx * dx + dy * dy)

        if (distance < 150) {
          ctx.beginPath()
          ctx.moveTo(particle.x, particle.y)
          ctx.lineTo(other.x, other.y)
          ctx.strokeStyle = `rgba(255, 255, 255, ${0.1 * (1 - distance / 150)})`
          ctx.stroke()
        }
      })
    })

    animationFrameId = requestAnimationFrame(animate)
  }

  animate()
}

// 初始化
onMounted(() => {
  // 恢复深色模式状态
  isDark.value = appStore.isDark
  // 恢复记住的用户名
  const savedUsername = localStorage.getItem('rememberedUsername')
  if (savedUsername) {
    loginForm.username = savedUsername
    loginForm.remember = true
  }
  // 初始化粒子系统
  initParticles()
})

// 清理
onUnmounted(() => {
  if (animationFrameId) {
    cancelAnimationFrame(animationFrameId)
  }
  window.removeEventListener('resize', () => {})
})

// 切换深色模式
function toggleDark(): void {
  appStore.toggleDark()
}

// 处理登录
async function handleLogin(): Promise<void> {
  const valid = await formRef.value?.validate()
  if (!valid) return

  loading.value = true
  try {
    const success = await userStore.login(loginForm)
    if (success) {
      // 记住用户名
      if (loginForm.remember) {
        localStorage.setItem('rememberedUsername', loginForm.username)
      } else {
        localStorage.removeItem('rememberedUsername')
      }
      // 跳转到目标页面或首页
      const redirect = (route.query.redirect as string) || '/'
      router.push(redirect)
    }
  } finally {
    loading.value = false
  }
}

// 跳转到注册页
function goToRegister(): void {
  router.push('/register')
}
</script>

<style scoped lang="scss">
.login-container {
  width: 100%;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);

  // 深色模式背景
  :global(html.dark) & {
    background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
  }
}

// 粒子画布
.particle-canvas {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 1;
  pointer-events: none;
}

// 动态背景装饰
.bg-decoration {
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
      width: 500px;
      height: 500px;
      background: radial-gradient(circle, #fff 0%, transparent 70%);
      top: -150px;
      left: -150px;
      animation-delay: 0s;
    }

    &.shape-2 {
      width: 400px;
      height: 400px;
      background: radial-gradient(circle, #fff 0%, transparent 70%);
      bottom: -100px;
      right: -100px;
      animation-delay: -5s;
    }

    &.shape-3 {
      width: 300px;
      height: 300px;
      background: radial-gradient(circle, #fff 0%, transparent 70%);
      top: 40%;
      left: 60%;
      animation-delay: -10s;
    }

    &.shape-4 {
      width: 250px;
      height: 250px;
      background: radial-gradient(circle, #fff 0%, transparent 70%);
      top: 20%;
      right: 20%;
      animation-delay: -15s;
    }
  }
}

@keyframes float {
  0%, 100% {
    transform: translate(0, 0) rotate(0deg) scale(1);
  }
  25% {
    transform: translate(50px, 50px) rotate(90deg) scale(1.1);
  }
  50% {
    transform: translate(0, 100px) rotate(180deg) scale(1);
  }
  75% {
    transform: translate(-50px, 50px) rotate(270deg) scale(0.9);
  }
}

// 登录卡片
.login-card {
  display: flex;
  width: 950px;
  max-width: 95%;
  min-height: 600px;
  background: var(--bg-overlay);
  border-radius: 24px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  overflow: hidden;
  position: relative;
  z-index: 2;
  animation: slideUp 0.6s ease-out;
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
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

// 左侧品牌区域
.brand-section {
  flex: 1;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 50px 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;

  // 深色模式
  :global(html.dark) & {
    background: linear-gradient(135deg, #2d3748 0%, #1a202c 100%);
  }

  // 装饰性背景
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

  // 底部装饰
  &::after {
    content: '';
    position: absolute;
    bottom: 0;
    left: 0;
    right: 0;
    height: 200px;
    background: linear-gradient(to top, rgba(0,0,0,0.1), transparent);
    pointer-events: none;
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
  width: 100%;
}

// Logo
.brand-logo {
  position: relative;
  display: inline-block;
  margin-bottom: 24px;

  .logo-icon-wrapper {
    width: 80px;
    height: 80px;
    border-radius: 20px;
    background: rgba(255, 255, 255, 0.2);
    backdrop-filter: blur(10px);
    display: flex;
    align-items: center;
    justify-content: center;
    position: relative;
    z-index: 1;
    animation: pulse 2s ease-in-out infinite;
  }

  .logo-glow {
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    width: 100px;
    height: 100px;
    border-radius: 50%;
    background: radial-gradient(circle, rgba(255, 255, 255, 0.3) 0%, transparent 70%);
    animation: glow 2s ease-in-out infinite;
  }

  .logo-ring {
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    width: 120px;
    height: 120px;
    border: 2px solid rgba(255, 255, 255, 0.3);
    border-radius: 50%;
    animation: ringPulse 3s ease-in-out infinite;
  }
}

@keyframes pulse {
  0%, 100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.05);
  }
}

@keyframes glow {
  0%, 100% {
    opacity: 0.5;
    transform: translate(-50%, -50%) scale(1);
  }
  50% {
    opacity: 1;
    transform: translate(-50%, -50%) scale(1.2);
  }
}

@keyframes ringPulse {
  0%, 100% {
    transform: translate(-50%, -50%) scale(1);
    opacity: 0.3;
  }
  50% {
    transform: translate(-50%, -50%) scale(1.1);
    opacity: 0.6;
  }
}

.brand-title {
  font-size: 32px;
  font-weight: 700;
  margin-bottom: 12px;
  letter-spacing: 2px;
  text-shadow: 0 2px 10px rgba(0, 0, 0, 0.2);
}

.brand-subtitle {
  font-size: 16px;
  opacity: 0.9;
  margin-bottom: 40px;
}

// 品牌统计数据
.brand-stats {
  display: flex;
  justify-content: space-around;
  margin-bottom: 40px;
  padding: 20px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 16px;
  backdrop-filter: blur(10px);

  .stat-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;

    .stat-number {
      font-size: 28px;
      font-weight: 700;
      color: #fff;
      text-shadow: 0 2px 10px rgba(0, 0, 0, 0.2);
    }

    .stat-label {
      font-size: 12px;
      opacity: 0.8;
    }
  }
}

// 功能特性
.brand-features {
  display: flex;
  flex-direction: column;
  gap: 20px;

  .feature-item {
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 16px;
    background: rgba(255, 255, 255, 0.1);
    border-radius: 12px;
    backdrop-filter: blur(10px);
    transition: all 0.3s ease;
    text-align: left;

    &:hover {
      background: rgba(255, 255, 255, 0.15);
      transform: translateX(5px);
    }

    .feature-icon {
      width: 40px;
      height: 40px;
      border-radius: 10px;
      background: rgba(255, 255, 255, 0.2);
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;

      .el-icon {
        font-size: 20px;
      }
    }

    .feature-text {
      display: flex;
      flex-direction: column;
      gap: 4px;

      .feature-title {
        font-size: 15px;
        font-weight: 600;
      }

      .feature-desc {
        font-size: 12px;
        opacity: 0.8;
      }
    }
  }
}

// 装饰性元素
.brand-decoration {
  margin-top: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;

  .decoration-line {
    width: 60px;
    height: 2px;
    background: linear-gradient(to right, transparent, rgba(255, 255, 255, 0.5), transparent);
  }

  .decoration-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.5);
    animation: blink 2s ease-in-out infinite;
  }
}

@keyframes blink {
  0%, 100% {
    opacity: 0.5;
  }
  50% {
    opacity: 1;
  }
}

// 右侧表单区域
.form-section {
  flex: 1;
  padding: 50px 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-overlay);
  position: relative;
}

.form-content {
  width: 100%;
  max-width: 360px;
}

.theme-switch {
  position: absolute;
  top: 20px;
  right: 20px;
}

.form-header {
  text-align: center;
  margin-bottom: 32px;
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

.login-form {
  .el-form-item {
    margin-bottom: 24px;
    position: relative;

    &:focus-within {
      .el-input__wrapper {
        border-color: var(--primary-color);
        box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.1);
      }
    }
  }

  .el-input {
    --el-input-border-radius: 12px;
    --el-input-height: 48px;

    :deep(.el-input__wrapper) {
      padding: 12px 16px;
      transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);

      &:hover {
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
      }

      &.is-focus {
        transform: translateY(-1px);
      }
    }

    :deep(.el-input__prefix) {
      color: var(--text-secondary);
      transition: color 0.3s ease;
    }

    &:focus-within {
      :deep(.el-input__prefix) {
        color: var(--primary-color);
      }
    }
  }

  .form-options {
    width: 100%;
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .login-btn {
    width: 100%;
    height: 48px;
    font-size: 16px;
    font-weight: 500;
    border-radius: 12px;
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    border: none;
    position: relative;
    overflow: hidden;

    &::before {
      content: '';
      position: absolute;
      top: 0;
      left: -100%;
      width: 100%;
      height: 100%;
      background: linear-gradient(
        90deg,
        transparent 0%,
        rgba(255, 255, 255, 0.3) 50%,
        transparent 100%
      );
      transition: left 0.5s ease;
    }

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 8px 24px rgba(102, 126, 234, 0.4);

      &::before {
        left: 100%;
      }
    }

    &:active {
      transform: translateY(0);
    }
  }
}

.form-footer {
  text-align: center;
  margin-top: 24px;
  font-size: 14px;
  color: var(--text-secondary);

  .footer-text {
    margin-right: 4px;
  }
}

// 其他登录方式
.other-login {
  margin-top: 32px;

  .divider-text {
    font-size: 12px;
    color: var(--text-secondary);
    padding: 0 16px;
  }

  .login-methods {
    display: flex;
    justify-content: center;
    gap: 24px;
    margin-top: 20px;

    .method-item {
      width: 48px;
      height: 48px;
      border-radius: 12px;
      border: 1px solid var(--border-color);
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      transition: all 0.3s ease;
      color: var(--text-secondary);

      &:hover {
        border-color: var(--primary-color);
        color: var(--primary-color);
        transform: translateY(-2px);
        box-shadow: 0 4px 12px rgba(64, 158, 255, 0.2);
      }
    }
  }
}

// 底部版权信息
.footer-info {
  position: absolute;
  bottom: 20px;
  left: 0;
  right: 0;
  text-align: center;
  color: rgba(255, 255, 255, 0.6);
  font-size: 12px;
  z-index: 2;
}

// 响应式设计
@media (max-width: 768px) {
  .login-card {
    flex-direction: column;
    width: 100%;
    max-width: 420px;
    min-height: auto;
    margin: 20px;
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

  .brand-decoration {
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

  .footer-info {
    display: none;
  }
}
</style>
