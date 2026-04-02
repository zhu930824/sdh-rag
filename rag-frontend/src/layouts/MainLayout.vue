<template>
  <a-layout class="app-layout">
    <Sidebar />
    <a-layout class="app-main">
      <Header />
      <a-layout-content class="app-content">
        <router-view />
      </a-layout-content>
    </a-layout>

    <!-- Mobile Drawer -->
    <a-drawer
      v-if="isMobile"
      :open="mobileMenuOpen"
      placement="left"
      :width="280"
      :closable="false"
      class="mobile-drawer"
      @close="closeMobileMenu"
    >
      <div class="mobile-drawer-content">
        <div class="mobile-header">
          <div class="logo-mark">
            <svg viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
              <rect width="32" height="32" rx="8" fill="url(#mobile-logo-gradient)"/>
              <path d="M10 16L14 20L22 12" stroke="white" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
              <defs>
                <linearGradient id="mobile-logo-gradient" x1="0" y1="0" x2="32" y2="32" gradientUnits="userSpaceOnUse">
                  <stop stop-color="#2563EB"/>
                  <stop offset="1" stop-color="#7C3AED"/>
                </linearGradient>
              </defs>
            </svg>
          </div>
          <span class="logo-text">智能知识库</span>
          <button class="close-btn" @click="closeMobileMenu">
            <CloseOutlined />
          </button>
        </div>
        <MobileMenu @navigate="closeMobileMenu" />
      </div>
    </a-drawer>
  </a-layout>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores'
import { CloseOutlined } from '@ant-design/icons-vue'
import Sidebar from './components/Sidebar.vue'
import Header from './components/Header.vue'
import MobileMenu from './components/MobileMenu.vue'

const route = useRoute()
const appStore = useAppStore()

const isMobile = ref(false)
const windowWidth = ref(window.innerWidth)

function checkMobile(): void {
  windowWidth.value = window.innerWidth
  isMobile.value = windowWidth.value < 768
}

const cachedViews = computed(() => [])
const mobileMenuOpen = computed(() => appStore.mobileMenuOpen)

function closeMobileMenu(): void {
  appStore.setMobileMenuOpen(false)
}

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
  appStore.initDarkMode()
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
})
</script>

<style scoped lang="scss">
.app-layout {
  height: 100vh;
  overflow: hidden;
  background: var(--bg-body);
}

.app-main {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
  background: var(--bg-body);
}

.app-content {
  flex: 1;
  padding: 16px;
  overflow: hidden;
  background: var(--bg-body);
}

// Mobile Drawer
.mobile-drawer {
  :deep(.ant-drawer-body) {
    padding: 0;
  }
}

.mobile-drawer-content {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--bg-surface);
}

.mobile-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 16px;
  border-bottom: 1px solid var(--border-color);
}

.logo-mark {
  width: 32px;
  height: 32px;

  svg {
    width: 100%;
    height: 100%;
  }
}

.logo-text {
  flex: 1;
  font-size: 17px;
  font-weight: var(--font-weight-semibold);
  color: var(--text-primary);
}

.close-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  font-size: 16px;
  cursor: pointer;

  &:hover {
    background: var(--bg-surface-secondary);
    color: var(--text-primary);
  }
}

// Responsive
@media (max-width: 768px) {
  .app-content {
    padding: 12px;
  }
}

@media (max-width: 480px) {
  .app-content {
    padding: 8px;
  }
}

// Dark Mode
html.dark {
  .app-layout,
  .app-main,
  .app-content {
    background: var(--bg-body);
  }
}
</style>