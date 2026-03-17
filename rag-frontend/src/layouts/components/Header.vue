<template>
  <a-layout-header class="layout-header">
    <div class="header-left">
      <MenuFoldOutlined
        v-if="!isMobile && !sidebarCollapsed"
        class="action-icon"
        @click="toggleSidebar"
      />
      <MenuUnfoldOutlined
        v-if="!isMobile && sidebarCollapsed"
        class="action-icon"
        @click="toggleSidebar"
      />
      <MenuOutlined v-if="isMobile" class="action-icon menu-trigger" @click="openMobileMenu" />
      <Breadcrumb />
    </div>

    <div class="header-right">
      <a-input-search
        v-if="!isMobile"
        v-model:value="searchKeyword"
        placeholder="搜索功能..."
        class="search-input"
        @search="handleSearch"
      />

      <a-tooltip :title="isFullscreen ? '退出全屏' : '全屏'">
        <FullscreenOutlined v-if="!isFullscreen" class="action-icon" @click="toggleFullscreen" />
        <FullscreenExitOutlined v-else class="action-icon" @click="toggleFullscreen" />
      </a-tooltip>

      <a-tooltip :title="isDark ? '切换浅色模式' : '切换深色模式'">
        <MoonOutlined v-if="!isDark" class="action-icon" @click="toggleDark" />
        <SunOutlined v-else class="action-icon" @click="toggleDark" />
      </a-tooltip>

      <a-dropdown>
        <div class="user-info">
          <a-avatar :size="32" :src="userAvatar">
            <template #icon><UserOutlined /></template>
          </a-avatar>
          <span v-if="!isMobile" class="username">{{ nickname || '用户' }}</span>
          <DownOutlined v-if="!isMobile" class="arrow-icon" />
        </div>
        <template #overlay>
          <a-menu @click="handleCommand">
            <a-menu-item key="profile">
              <UserOutlined />
              <span>个人中心</span>
            </a-menu-item>
            <a-menu-item key="settings">
              <SettingOutlined />
              <span>系统设置</span>
            </a-menu-item>
            <a-menu-divider />
            <a-menu-item key="logout">
              <LogoutOutlined />
              <span>退出登录</span>
            </a-menu-item>
          </a-menu>
        </template>
      </a-dropdown>
    </div>
  </a-layout-header>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { Modal, message } from 'ant-design-vue'
import {
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  MenuOutlined,
  FullscreenOutlined,
  FullscreenExitOutlined,
  BulbOutlined,
  UserOutlined,
  SettingOutlined,
  LogoutOutlined,
  DownOutlined,
} from '@ant-design/icons-vue'
import { useAppStore, useUserStore } from '@/stores'
import Breadcrumb from './Breadcrumb.vue'

const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

const searchKeyword = ref('')
const isFullscreen = ref(false)
const isMobile = ref(false)

function checkMobile(): void {
  isMobile.value = window.innerWidth < 768
}

const sidebarCollapsed = computed(() => appStore.sidebarCollapsed)
const isDark = computed(() => appStore.isDark)
const nickname = computed(() => userStore.nickname)
const userAvatar = computed(() => userStore.userInfo?.avatar)

function toggleSidebar(): void {
  appStore.toggleSidebar()
}

function openMobileMenu(): void {
  appStore.setMobileMenuOpen(true)
}

function toggleDark(): void {
  appStore.toggleDark()
}

function toggleFullscreen(): void {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen()
    isFullscreen.value = true
  } else {
    document.exitFullscreen()
    isFullscreen.value = false
  }
}

function handleFullscreenChange(): void {
  isFullscreen.value = !!document.fullscreenElement
}

function handleSearch(): void {
  if (!searchKeyword.value.trim()) {
    return
  }
  message.info(`搜索功能开发中: ${searchKeyword.value}`)
}

async function handleCommand({ key }: { key: string }): Promise<void> {
  switch (key) {
    case 'profile':
      router.push('/profile')
      break
    case 'settings':
      router.push('/settings')
      break
    case 'logout':
      Modal.confirm({
        title: '提示',
        content: '确定要退出登录吗？',
        okText: '确定',
        cancelText: '取消',
        onOk() {
          userStore.logout()
          router.push('/login')
        },
      })
      break
  }
}

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
  document.addEventListener('fullscreenchange', handleFullscreenChange)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
  document.removeEventListener('fullscreenchange', handleFullscreenChange)
})
</script>

<style scoped lang="scss">
.layout-header {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  background-color: var(--bg-overlay);
  border-bottom: 1px solid var(--border-lighter);
  box-shadow: var(--box-shadow-lighter);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.search-input {
  width: 200px;
}

.action-icon {
  font-size: 20px;
  color: var(--text-regular);
  cursor: pointer;
  transition: all var(--transition-duration);

  &:hover {
    color: var(--primary-color);
    transform: scale(1.1);
  }
}

.menu-trigger {
  font-size: 22px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  border-radius: var(--border-radius-base);
  cursor: pointer;
  transition: background-color var(--transition-duration);

  &:hover {
    background-color: var(--bg-page);
  }

  .username {
    font-size: 14px;
    color: var(--text-regular);
    max-width: 100px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .arrow-icon {
    font-size: 12px;
    color: var(--text-secondary);
    transition: transform var(--transition-duration);
  }
}

@media (max-width: 768px) {
  .layout-header {
    padding: 0 12px;
  }

  .header-left {
    gap: 12px;
  }

  .header-right {
    gap: 12px;
  }
}
</style>
