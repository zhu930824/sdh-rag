<template>
  <!-- 全屏加载 -->
  <Teleport to="body" v-if="fullscreen">
    <Transition name="fade">
      <div v-if="modelValue" class="loading-fullscreen" :style="{ zIndex }">
        <div class="loading-content">
          <!-- 加载动画 -->
          <div class="loading-spinner">
            <div class="spinner-ring" v-for="i in 4" :key="i" :style="{ animationDelay: `${i * 0.15}s` }"></div>
            <div class="spinner-core"></div>
          </div>
          <!-- 加载文字 -->
          <div v-if="text" class="loading-text">{{ text }}</div>
          <!-- 进度条 -->
          <div v-if="showProgress" class="loading-progress">
            <div class="progress-bar" :style="{ width: `${progress}%` }">
              <div class="progress-glow"></div>
            </div>
            <div class="progress-text">{{ progress }}%</div>
          </div>
        </div>
        <!-- 背景装饰 -->
        <div class="loading-decoration">
          <div class="decoration-circle" v-for="i in 3" :key="i" :class="`circle-${i}`"></div>
        </div>
      </div>
    </Transition>
  </Teleport>

  <!-- 局部加载 -->
  <div v-else class="loading-local" :class="{ 'is-loading': modelValue }">
    <Transition name="fade">
      <div v-if="modelValue" class="loading-mask" :style="{ background: background }">
        <div class="loading-content">
          <!-- 加载动画 -->
          <div class="loading-spinner small">
            <div class="spinner-ring" v-for="i in 4" :key="i" :style="{ animationDelay: `${i * 0.15}s` }"></div>
            <div class="spinner-core"></div>
          </div>
          <!-- 加载文字 -->
          <div v-if="text" class="loading-text">{{ text }}</div>
        </div>
      </div>
    </Transition>
    <slot></slot>
  </div>
</template>

<script setup lang="ts">
import { withDefaults } from 'vue'

interface Props {
  modelValue: boolean
  text?: string
  fullscreen?: boolean
  background?: string
  zIndex?: number
  showProgress?: boolean
  progress?: number
}

withDefaults(defineProps<Props>(), {
  text: '',
  fullscreen: false,
  background: 'rgba(255, 255, 255, 0.9)',
  zIndex: 4000,
  showProgress: false,
  progress: 0,
})
</script>

<style scoped lang="scss">
// 全屏加载
.loading-fullscreen {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);

  :global(html.dark) & {
    background: rgba(0, 0, 0, 0.95);
  }
}

// 局部加载
.loading-local {
  position: relative;

  &.is-loading {
    min-height: 100px;
  }

  .loading-mask {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: inherit;
    z-index: 10;
  }
}

// 加载内容
.loading-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}

// 加载动画
.loading-spinner {
  position: relative;
  width: 60px;
  height: 60px;

  &.small {
    width: 40px;
    height: 40px;
  }

  .spinner-ring {
    position: absolute;
    width: 100%;
    height: 100%;
    border-radius: 50%;
    border: 3px solid transparent;
    border-top-color: var(--primary-color);
    animation: spin 1.2s cubic-bezier(0.5, 0, 0.5, 1) infinite;

    &:nth-child(1) {
      border-top-color: #667eea;
      width: 100%;
      height: 100%;
    }

    &:nth-child(2) {
      border-top-color: #764ba2;
      width: 80%;
      height: 80%;
      top: 10%;
      left: 10%;
      animation-duration: 1.4s;
    }

    &:nth-child(3) {
      border-top-color: #f093fb;
      width: 60%;
      height: 60%;
      top: 20%;
      left: 20%;
      animation-duration: 1.6s;
    }

    &:nth-child(4) {
      border-top-color: #f5576c;
      width: 40%;
      height: 40%;
      top: 30%;
      left: 30%;
      animation-duration: 1.8s;
    }
  }

  .spinner-core {
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    width: 20%;
    height: 20%;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    border-radius: 50%;
    animation: pulse 1.5s ease-in-out infinite;
  }
}

@keyframes spin {
  0% {
    transform: rotate(0deg);
  }
  100% {
    transform: rotate(360deg);
  }
}

@keyframes pulse {
  0%, 100% {
    transform: translate(-50%, -50%) scale(1);
    opacity: 1;
  }
  50% {
    transform: translate(-50%, -50%) scale(1.2);
    opacity: 0.8;
  }
}

// 加载文字
.loading-text {
  font-size: 16px;
  font-weight: 500;
  color: var(--text-primary);
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
}

// 进度条
.loading-progress {
  width: 200px;
  height: 4px;
  background: var(--border-lighter);
  border-radius: 2px;
  overflow: hidden;
  position: relative;

  .progress-bar {
    height: 100%;
    background: linear-gradient(90deg, #667eea 0%, #764ba2 100%);
    border-radius: 2px;
    transition: width 0.3s ease;
    position: relative;
    overflow: hidden;

    &::after {
      content: '';
      position: absolute;
      top: 0;
      left: -100%;
      width: 100%;
      height: 100%;
      background: linear-gradient(
        90deg,
        transparent 0%,
        rgba(255, 255, 255, 0.5) 50%,
        transparent 100%
      );
      animation: shimmer 1.5s infinite;
    }
  }

  .progress-glow {
    position: absolute;
    right: 0;
    top: 50%;
    transform: translateY(-50%);
    width: 20px;
    height: 20px;
    background: radial-gradient(circle, rgba(102, 126, 234, 0.8) 0%, transparent 70%);
    border-radius: 50%;
    animation: glowPulse 1s ease-in-out infinite;
  }

  .progress-text {
    position: absolute;
    right: -30px;
    top: 50%;
    transform: translateY(-50%);
    font-size: 12px;
    font-weight: 600;
    color: var(--text-primary);
    white-space: nowrap;
  }
}

@keyframes shimmer {
  0% {
    left: -100%;
  }
  100% {
    left: 100%;
  }
}

@keyframes glowPulse {
  0%, 100% {
    opacity: 0.5;
    transform: translateY(-50%) scale(1);
  }
  50% {
    opacity: 1;
    transform: translateY(-50%) scale(1.2);
  }
}

// 背景装饰
.loading-decoration {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  overflow: hidden;

  .decoration-circle {
    position: absolute;
    border-radius: 50%;
    background: radial-gradient(circle, rgba(102, 126, 234, 0.1) 0%, transparent 70%);
    animation: floatCircle 4s ease-in-out infinite;

    &.circle-1 {
      width: 200px;
      height: 200px;
      top: -50px;
      right: -50px;
      animation-delay: 0s;
    }

    &.circle-2 {
      width: 150px;
      height: 150px;
      bottom: -30px;
      left: -30px;
      animation-delay: -1s;
    }

    &.circle-3 {
      width: 100px;
      height: 100px;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      animation-delay: -2s;
    }
  }
}

@keyframes floatCircle {
  0%, 100% {
    transform: translate(0, 0) scale(1);
  }
  50% {
    transform: translate(20px, -20px) scale(1.1);
  }
}

// 过渡动画
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
