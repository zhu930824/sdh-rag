<template>
  <a-layout class="main-layout">
    <Sidebar />

    <a-layout class="main-container">
      <Header />

      <a-layout-content class="main-content">
        <router-view v-slot="{ Component, route }">
          <transition :name="transitionName" mode="out-in">
            <keep-alive :include="cachedViews">
              <component :is="Component" :key="route.path" />
            </keep-alive>
          </transition>
        </router-view>
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores'
import Sidebar from './components/Sidebar.vue'
import Header from './components/Header.vue'

const route = useRoute()
const appStore = useAppStore()

const transitionName = ref('fade-transform')

const cachedViews = computed(() => {
  return []
})

watch(
  () => route.path,
  (to, from) => {
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

onMounted(() => {
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

@media (max-width: 768px) {
  .main-content {
    padding: 12px;
  }

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

  .fade-transform-enter-active,
  .fade-transform-leave-active,
  .slide-left-enter-active,
  .slide-left-leave-active,
  .slide-right-enter-active,
  .slide-right-leave-active {
    transition-duration: 0.2s;
  }
}

@media (min-width: 769px) and (max-width: 1024px) {
  .main-content {
    padding: 16px;
  }
}

@media (orientation: landscape) and (max-height: 600px) {
  .main-content {
    padding: 8px;
  }
}
</style>
