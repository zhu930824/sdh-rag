<template>
  <div class="not-found-container">
    <!-- 动态背景 -->
    <div class="bg-animation">
      <div class="floating-shape" v-for="i in 6" :key="i" :class="`shape-${i}`"></div>
    </div>

    <!-- 主要内容 -->
    <div class="not-found-content">
      <!-- 404数字动画 -->
      <div class="error-code-wrapper">
        <div class="error-code">
          <span class="digit" v-for="(digit, index) in '404'" :key="index" :style="{ animationDelay: `${index * 0.1}s` }">
            {{ digit }}
          </span>
        </div>
        <div class="error-shadow">404</div>
        <div class="error-glow"></div>
      </div>

      <!-- 错误信息 -->
      <div class="error-info animate-slide-up">
        <h2 class="error-title">哎呀！页面走丢了</h2>
        <p class="error-desc">
          您访问的页面不存在或已被移除，请检查URL是否正确
        </p>
        <div class="error-suggestions">
          <div class="suggestion-item" v-for="(item, index) in suggestions" :key="index" @click="handleSuggestion(index)">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.text }}</span>
          </div>
        </div>
      </div>

      <!-- 动画图标 -->
      <div class="error-illustration">
        <div class="astronaut">
          <div class="astronaut-body">
            <div class="helmet">
              <div class="visor"></div>
            </div>
            <div class="body"></div>
            <div class="arm left"></div>
            <div class="arm right"></div>
            <div class="leg left"></div>
            <div class="leg right"></div>
          </div>
          <div class="planet"></div>
        </div>
      </div>

      <!-- 操作按钮 -->
      <div class="action-buttons animate-slide-up" style="animation-delay: 0.2s">
        <el-button type="primary" size="large" @click="goHome" class="home-btn">
          <el-icon><HomeFilled /></el-icon>
          <span>返回首页</span>
        </el-button>
        <el-button size="large" @click="goBack" class="back-btn">
          <el-icon><Back /></el-icon>
          <span>返回上页</span>
        </el-button>
      </div>

      <!-- 倒计时自动跳转 -->
      <div class="auto-redirect animate-fade-in" style="animation-delay: 0.4s">
        <span>{{ countdown }}秒后自动返回首页</span>
        <el-progress 
          :percentage="progress" 
          :show-text="false"
          :stroke-width="4"
          class="progress-bar"
        />
      </div>
    </div>

    <!-- 装饰性元素 -->
    <div class="decoration-elements">
      <div class="star" v-for="i in 20" :key="i" :style="getStarStyle(i)"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { HomeFilled, Back, RefreshRight, Document, ChatDotRound } from '@element-plus/icons-vue'

const router = useRouter()

// 倒计时
const countdown = ref(10)
const progress = ref(100)
let timer: number | null = null

// 建议操作
const suggestions = [
  { icon: RefreshRight, text: '刷新页面' },
  { icon: Document, text: '查看文档' },
  { icon: ChatDotRound, text: '开始问答' },
]

// 获取星星样式
function getStarStyle(index: number): Record<string, string> {
  const size = Math.random() * 3 + 1
  return {
    left: `${Math.random() * 100}%`,
    top: `${Math.random() * 100}%`,
    width: `${size}px`,
    height: `${size}px`,
    animationDelay: `${Math.random() * 3}s`,
    animationDuration: `${Math.random() * 2 + 2}s`,
  }
}

// 返回首页
function goHome(): void {
  if (timer) {
    clearInterval(timer)
  }
  router.push('/')
}

// 返回上一页
function goBack(): void {
  if (timer) {
    clearInterval(timer)
  }
  router.go(-1)
}

// 处理建议操作
function handleSuggestion(index: number): void {
  if (timer) {
    clearInterval(timer)
  }
  switch (index) {
    case 0:
      window.location.reload()
      break
    case 1:
      router.push('/document')
      break
    case 2:
      router.push('/chat')
      break
  }
}

// 开始倒计时
function startCountdown(): void {
  timer = window.setInterval(() => {
    countdown.value--
    progress.value = (countdown.value / 10) * 100

    if (countdown.value <= 0) {
      if (timer) {
        clearInterval(timer)
      }
      router.push('/')
    }
  }, 1000)
}

onMounted(() => {
  startCountdown()
})

onUnmounted(() => {
  if (timer) {
    clearInterval(timer)
  }
})
</script>

<style scoped lang="scss">
.not-found-container {
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

// 动态背景
.bg-animation {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  overflow: hidden;
  z-index: 0;

  .floating-shape {
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

    &.shape-4 {
      width: 150px;
      height: 150px;
      background: #fff;
      top: 20%;
      right: 20%;
      animation-delay: -15s;
    }

    &.shape-5 {
      width: 100px;
      height: 100px;
      background: #fff;
      bottom: 30%;
      left: 20%;
      animation-delay: -8s;
    }

    &.shape-6 {
      width: 120px;
      height: 120px;
      background: #fff;
      top: 30%;
      right: 30%;
      animation-delay: -12s;
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

// 主要内容
.not-found-content {
  position: relative;
  z-index: 1;
  text-align: center;
  padding: 20px;
}

// 404数字
.error-code-wrapper {
  position: relative;
  margin-bottom: 20px;

  .error-code {
    display: flex;
    justify-content: center;
    gap: 10px;
    position: relative;
    z-index: 2;

    .digit {
      font-size: 150px;
      font-weight: 900;
      color: #fff;
      text-shadow: 0 10px 30px rgba(0, 0, 0, 0.3);
      animation: bounce 0.6s ease-out both;
      display: inline-block;
      position: relative;

      &::after {
        content: attr(data-digit);
        position: absolute;
        left: 0;
        top: 0;
        width: 100%;
        height: 100%;
        background: linear-gradient(135deg, #fff 0%, rgba(255, 255, 255, 0.8) 100%);
        -webkit-background-clip: text;
        -webkit-text-fill-color: transparent;
        background-clip: text;
        opacity: 0;
        animation: shimmerText 2s ease-in-out infinite;
      }

      &:nth-child(2) {
        animation-name: bounceIn;
      }
    }
  }

  .error-shadow {
    position: absolute;
    top: 10px;
    left: 50%;
    transform: translateX(-50%);
    font-size: 150px;
    font-weight: 900;
    color: rgba(0, 0, 0, 0.1);
    z-index: 1;
    letter-spacing: 10px;
  }

  .error-glow {
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    width: 300px;
    height: 300px;
    background: radial-gradient(circle, rgba(255, 255, 255, 0.2) 0%, transparent 70%);
    border-radius: 50%;
    z-index: 0;
    animation: glowPulse 3s ease-in-out infinite;
  }
}

@keyframes bounce {
  0% {
    opacity: 0;
    transform: translateY(-50px);
  }
  60% {
    transform: translateY(10px);
  }
  100% {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes bounceIn {
  0% {
    opacity: 0;
    transform: translateY(-50px) scale(0.8);
  }
  50% {
    transform: translateY(10px) scale(1.1);
  }
  100% {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes shimmerText {
  0%, 100% {
    opacity: 0;
  }
  50% {
    opacity: 1;
  }
}

@keyframes glowPulse {
  0%, 100% {
    opacity: 0.3;
    transform: translate(-50%, -50%) scale(1);
  }
  50% {
    opacity: 0.6;
    transform: translate(-50%, -50%) scale(1.1);
  }
}

// 错误信息
.error-info {
  margin-bottom: 30px;

  .error-title {
    font-size: 32px;
    font-weight: 600;
    color: #fff;
    margin-bottom: 12px;
    text-shadow: 0 2px 10px rgba(0, 0, 0, 0.2);
  }

  .error-desc {
    font-size: 16px;
    color: rgba(255, 255, 255, 0.8);
    max-width: 500px;
    margin: 0 auto;
    line-height: 1.6;
  }

  .error-suggestions {
    display: flex;
    justify-content: center;
    gap: 16px;
    margin-top: 24px;

    .suggestion-item {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 12px 20px;
      background: rgba(255, 255, 255, 0.1);
      border-radius: 24px;
      backdrop-filter: blur(10px);
      cursor: pointer;
      transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
      border: 1px solid rgba(255, 255, 255, 0.2);

      &:hover {
        background: rgba(255, 255, 255, 0.2);
        transform: translateY(-2px);
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
        border-color: rgba(255, 255, 255, 0.4);
      }

      &:active {
        transform: translateY(0);
      }

      .el-icon {
        font-size: 18px;
      }

      span {
        font-size: 14px;
        font-weight: 500;
      }
    }
  }
}

// 宇航员动画
.error-illustration {
  margin: 40px 0;

  .astronaut {
    position: relative;
    display: inline-block;
    animation: floatAstronaut 3s ease-in-out infinite;
  }

  .astronaut-body {
    position: relative;
    width: 100px;
    height: 120px;

    .helmet {
      width: 60px;
      height: 60px;
      background: #fff;
      border-radius: 50%;
      position: absolute;
      top: 0;
      left: 50%;
      transform: translateX(-50%);
      box-shadow: 0 5px 15px rgba(0, 0, 0, 0.2);

      .visor {
        width: 40px;
        height: 30px;
        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
        border-radius: 50%;
        position: absolute;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%);
      }
    }

    .body {
      width: 50px;
      height: 50px;
      background: #fff;
      border-radius: 25px;
      position: absolute;
      top: 50px;
      left: 50%;
      transform: translateX(-50%);
      box-shadow: 0 5px 15px rgba(0, 0, 0, 0.2);
    }

    .arm {
      width: 15px;
      height: 40px;
      background: #fff;
      border-radius: 10px;
      position: absolute;
      top: 55px;
      box-shadow: 0 5px 15px rgba(0, 0, 0, 0.2);

      &.left {
        left: 10px;
        transform: rotate(-20deg);
        animation: waveArm 2s ease-in-out infinite;
      }

      &.right {
        right: 10px;
        transform: rotate(20deg);
        animation: waveArm 2s ease-in-out infinite 1s;
      }
    }

    .leg {
      width: 15px;
      height: 35px;
      background: #fff;
      border-radius: 10px;
      position: absolute;
      top: 95px;
      box-shadow: 0 5px 15px rgba(0, 0, 0, 0.2);

      &.left {
        left: 30px;
      }

      &.right {
        right: 30px;
      }
    }
  }

  .planet {
    width: 80px;
    height: 80px;
    background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
    border-radius: 50%;
    position: absolute;
    bottom: -20px;
    left: 50%;
    transform: translateX(-50%);
    box-shadow: 0 10px 30px rgba(0, 0, 0, 0.3);

    &::before {
      content: '';
      position: absolute;
      width: 120px;
      height: 20px;
      background: rgba(255, 255, 255, 0.3);
      border-radius: 50%;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%) rotate(-20deg);
    }
  }
}

@keyframes floatAstronaut {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-20px);
  }
}

@keyframes waveArm {
  0%, 100% {
    transform: rotate(-20deg);
  }
  50% {
    transform: rotate(-40deg);
  }
}

// 操作按钮
.action-buttons {
  display: flex;
  gap: 16px;
  justify-content: center;
  margin-bottom: 30px;

  .home-btn,
  .back-btn {
    height: 48px;
    padding: 0 32px;
    font-size: 16px;
    font-weight: 500;
    border-radius: 24px;
    transition: all 0.3s ease;

    .el-icon {
      margin-right: 8px;
    }
  }

  .home-btn {
    background: #fff;
    color: #667eea;
    border: none;

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 6px 20px rgba(255, 255, 255, 0.4);
    }
  }

  .back-btn {
    background: transparent;
    color: #fff;
    border: 2px solid rgba(255, 255, 255, 0.5);

    &:hover {
      background: rgba(255, 255, 255, 0.1);
      border-color: #fff;
      transform: translateY(-2px);
    }
  }
}

// 自动跳转
.auto-redirect {
  color: rgba(255, 255, 255, 0.7);
  font-size: 14px;

  .progress-bar {
    width: 200px;
    margin: 10px auto 0;

    :deep(.el-progress-bar__outer) {
      background: rgba(255, 255, 255, 0.2);
      border-radius: 2px;
    }

    :deep(.el-progress-bar__inner) {
      background: #fff;
      border-radius: 2px;
    }
  }
}

// 装饰性星星
.decoration-elements {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 0;

  .star {
    position: absolute;
    background: #fff;
    border-radius: 50%;
    animation: twinkle 2s ease-in-out infinite;
  }
}

@keyframes twinkle {
  0%, 100% {
    opacity: 0.3;
    transform: scale(1);
  }
  50% {
    opacity: 1;
    transform: scale(1.2);
  }
}

// 响应式设计
@media (max-width: 768px) {
  .error-code-wrapper {
    .error-code .digit {
      font-size: 100px;
    }

    .error-shadow {
      font-size: 100px;
    }
  }

  .error-info {
    .error-title {
      font-size: 24px;
    }

    .error-desc {
      font-size: 14px;
      padding: 0 20px;
    }
  }

  .action-buttons {
    flex-direction: column;
    align-items: center;

    .home-btn,
    .back-btn {
      width: 200px;
    }
  }

  .error-illustration {
    transform: scale(0.8);
  }
}
</style>
