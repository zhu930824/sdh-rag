<template>
  <header class="app-header">
    <!-- Left Section -->
    <div class="header-left">
      <button class="icon-btn" @click="toggleSidebar" v-if="!isMobile">
        <MenuFoldOutlined v-if="!sidebarCollapsed" />
        <MenuUnfoldOutlined v-else />
      </button>
      <button class="icon-btn" @click="openMobileMenu" v-else>
        <MenuOutlined />
      </button>
      <Breadcrumb />
    </div>

    <!-- Right Section -->
    <div class="header-right">
      <!-- Search -->
      <div class="search-wrapper" v-if="!isMobile">
        <a-input
          v-model:value="searchKeyword"
          placeholder="搜索..."
          class="search-input"
          @pressEnter="handleSearch"
        >
          <template #prefix>
            <SearchOutlined class="search-icon" />
          </template>
        </a-input>
      </div>

      <!-- Actions -->
      <div class="header-actions">
        <!-- Fullscreen -->
        <button class="icon-btn" @click="toggleFullscreen" :title="isFullscreen ? '退出全屏' : '全屏'">
          <FullscreenExitOutlined v-if="isFullscreen" />
          <FullscreenOutlined v-else />
        </button>

        <!-- Theme Toggle -->
        <button class="icon-btn" @click="toggleDark" :title="isDark ? '浅色模式' : '深色模式'">
          <BulbFilled v-if="isDark" />
          <BulbOutlined v-else />
        </button>

        <!-- User Menu -->
        <a-dropdown :trigger="['click']" placement="bottomRight">
          <div class="user-menu">
            <a-avatar :size="34" :src="userAvatar" class="user-avatar">
              <template #icon><UserOutlined /></template>
            </a-avatar>
            <span class="user-name" v-if="!isMobile">{{ nickname || '用户' }}</span>
            <DownOutlined class="user-arrow" v-if="!isMobile" />
          </div>
          <template #overlay>
            <a-menu class="user-dropdown" @click="handleUserAction">
              <a-menu-item key="profile">
                <UserOutlined />
                <span>个人中心</span>
              </a-menu-item>
              <a-menu-item key="settings">
                <SettingOutlined />
                <span>系统设置</span>
              </a-menu-item>
              <a-menu-divider />
              <a-menu-item key="logout" class="logout-item">
                <LogoutOutlined />
                <span>退出登录</span>
              </a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { Modal } from 'ant-design-vue'
import {
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  MenuOutlined,
  FullscreenOutlined,
  FullscreenExitOutlined,
  UserOutlined,
  SettingOutlined,
  LogoutOutlined,
  DownOutlined,
  SearchOutlined,
  BulbOutlined,
  BulbFilled,
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
  if (searchKeyword.value.trim()) {
    console.log('Search:', searchKeyword.value)
  }
}

function handleUserAction({ key }: { key: string }): void {
  switch (key) {
    case 'profile':
      router.push('/profile')
      break
    case 'settings':
      router.push('/settings')
      break
    case 'logout':
      Modal.confirm({
        title: '确认退出',
        content: '确定要退出登录吗？',
        okText: '退出',
        cancelText: '取消',
        centered: true,
        async onOk() {
          await userStore.logout()
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
.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--header-height);
  padding: 0 24px;
  background: var(--bg-surface);
  border-bottom: 1px solid var(--border-color);
  position: sticky;
  top: 0;
  z-index: var(--z-sticky);
}

// Left Section
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

// Right Section
.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

// Icon Button
.icon-btn {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  border-radius: var(--radius-lg);
  color: var(--text-secondary);
  font-size: 18px;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-default);

  &:hover {
    background: var(--bg-surface-secondary);
    color: var(--text-primary);
  }

  &:active {
    transform: scale(0.95);
  }
}

// Search
.search-wrapper {
  width: 260px;
}

.search-input {
  background: var(--bg-surface-secondary);
  border: 1px solid transparent;
  border-radius: var(--radius-lg);
  font-size: var(--font-size-sm);

  :deep(.ant-input) {
    background: transparent;
    font-size: var(--font-size-sm);

    &::placeholder {
      color: var(--text-placeholder);
    }
  }

  &:hover,
  &:focus-within {
    background: var(--bg-surface);
    border-color: var(--border-color);
  }

  &:focus-within {
    border-color: var(--primary-color);
    box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
  }

  .search-icon {
    color: var(--text-tertiary);
    font-size: 14px;
  }
}

// Header Actions
.header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

// User Menu
.user-menu {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 8px 4px 4px;
  border-radius: var(--radius-full);
  cursor: pointer;
  transition: background var(--duration-fast) var(--ease-default);

  &:hover {
    background: var(--bg-surface-secondary);
  }
}

.user-avatar {
  background: var(--primary-gradient);
}

.user-name {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--text-primary);
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-arrow {
  font-size: 10px;
  color: var(--text-tertiary);
  transition: transform var(--duration-fast) var(--ease-default);
}

.user-menu:hover .user-arrow {
  transform: rotate(180deg);
}

// User Dropdown
.user-dropdown {
  min-width: 180px;
  padding: 8px;

  :deep(.ant-dropdown-menu-item) {
    display: flex;
    align-items: center;
    gap: 10px;
    border-radius: var(--radius-md);
    padding: 10px 14px;
    font-size: var(--font-size-sm);

    .anticon {
      font-size: 16px;
      color: var(--text-tertiary);
    }

    &:hover {
      background: var(--bg-surface-secondary);

      .anticon {
        color: var(--text-primary);
      }
    }
  }

  .logout-item {
    color: var(--danger-color);

    .anticon {
      color: var(--danger-color);
    }

    &:hover {
      background: var(--danger-light);
    }
  }
}

// Dark Mode
html.dark {
  .search-input {
    background: var(--bg-surface-secondary);

    &:hover,
    &:focus-within {
      background: var(--bg-surface-tertiary);
    }
  }
}

// Mobile
@media (max-width: 768px) {
  .app-header {
    padding: 0 16px;
  }

  .search-wrapper {
    display: none;
  }
}
</style>