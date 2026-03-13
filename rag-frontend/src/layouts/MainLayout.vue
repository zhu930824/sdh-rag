<template>
  <el-container class="main-layout">
    <!-- 侧边栏 -->
    <Sidebar />

    <!-- 主内容区 -->
    <el-container class="main-container">
      <!-- 顶部导航栏 -->
      <Header />

      <!-- 标签页 -->
      <Tabs />

      <!-- 内容区域 -->
      <el-main class="main-content">
        <router-view v-slot="{ Component, route }">
          <transition :name="transitionName" mode="out-in">
            <keep-alive :include="cachedViews">
              <component :is="Component" :key="route.path" />
            </keep-alive>
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores'
import Sidebar from './components/Sidebar.vue'
import Header from './components/Header.vue'
import Tabs from './components/Tabs.vue'

const route = useRoute()
const appStore = useAppStore()

// 页面过渡动画名称
const transitionName = ref('fade-transform')

// 缓存的视图（用于keep-alive）
const cachedViews = computed(() => {
  // 可以根据需要配置需要缓存的页面
  return []
})

// 监听路由变化，设置不同的过渡动画
watch(
  () => route.path,
  (to, from) => {
    // 根据路由层级决定动画方向
    const toDepth = to.split('/').length
    const fromDepth = from?.split('/').length || 0

    if (toDepth > fromDepth) {
      transitionName.value = 'slide-left'
    } else if (toDepth < fromDepth) {
      transitionName.value = 'slide-right'
    } else {
      transitionName.value = 'fade-transform'
    }
  }
)

// 初始化
onMounted(() => {
  // 初始化深色模式
  appStore.initDarkMode()
})
</script>

<style scoped lang="scss">
.main-layout {
  width: 100%;
  height: 100vh;
  overflow: hidden;
}

.main-container {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.main-content {
  flex: 1;
  overflow: auto;
  padding: 20px;
  background-color: var(--bg-page);
}

// 页面切换动画 - 淡入淡出 + 缩放
.fade-transform-enter-active,
.fade-transform-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.fade-transform-enter-from {
  opacity: 0;
  transform: translateX(-20px) scale(0.98);
}

.fade-transform-leave-to {
  opacity: 0;
  transform: translateX(20px) scale(0.98);
}

// 页面切换动画 - 左滑
.slide-left-enter-active,
.slide-left-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.slide-left-enter-from {
  opacity: 0;
  transform: translateX(30px);
}

.slide-left-leave-to {
  opacity: 0;
  transform: translateX(-30px);
}

// 页面切换动画 - 右滑
.slide-right-enter-active,
.slide-right-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.slide-right-enter-from {
  opacity: 0;
  transform: translateX(-30px);
}

.slide-right-leave-to {
  opacity: 0;
  transform: translateX(30px);
}

// 响应式设计
@media (max-width: 768px) {
  .main-content {
    padding: 12px;
  }

  // 移动端页面切换动画优化
  .fade-transform-enter-from,
  .fade-transform-leave-to {
    transform: translateX(0) scale(1);
  }

  .slide-left-enter-from,
  .slide-left-leave-to {
    transform: translateX(20px);
  }

  .slide-right-enter-from,
  .slide-right-leave-to {
    transform: translateX(-20px);
  }
}

@media (max-width: 480px) {
  .main-content {
    padding: 8px;
  }

  // 小屏幕设备优化
  .fade-transform-enter-active,
  .fade-transform-leave-active,
  .slide-left-enter-active,
  .slide-left-leave-active,
  .slide-right-enter-active,
  .slide-right-leave-active {
    transition-duration: 0.2s;
  }
}

// 平板设备
@media (min-width: 769px) and (max-width: 1024px) {
  .main-content {
    padding: 16px;
  }
}

// 横屏模式
@media (orientation: landscape) and (max-height: 600px) {
  .main-content {
    padding: 8px;
  }
}
</style>
