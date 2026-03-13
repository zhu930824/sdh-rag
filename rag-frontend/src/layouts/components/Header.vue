<template>
  <el-header class="layout-header">
    <!-- 左侧区域 -->
    <div class="header-left">
      <!-- 移动端菜单按钮 -->
      <el-icon v-if="isMobile" class="action-icon menu-trigger" @click="openMobileMenu">
        <Expand />
      </el-icon>

      <!-- 桌面端折叠按钮 -->
      <el-icon v-else class="action-icon" @click="toggleSidebar">
        <Fold v-if="!sidebarCollapsed" />
        <Expand v-else />
      </el-icon>

      <!-- 面包屑导航 -->
      <Breadcrumb />
    </div>

    <!-- 右侧区域 -->
    <div class="header-right">
      <!-- 全局搜索 -->
      <el-input
        v-if="!isMobile"
        v-model="searchKeyword"
        class="search-input"
        placeholder="搜索功能..."
        prefix-icon="Search"
        clearable
        @keyup.enter="handleSearch"
      />

      <!-- 全屏按钮 -->
      <el-tooltip :content="isFullscreen ? '退出全屏' : '全屏'" placement="bottom">
        <el-icon class="action-icon" @click="toggleFullscreen">
          <FullScreen v-if="!isFullscreen" />
          <Aim v-else />
        </el-icon>
      </el-tooltip>

      <!-- 主题切换 -->
      <el-tooltip :content="isDark ? '切换浅色模式' : '切换深色模式'" placement="bottom">
        <el-icon class="action-icon" @click="toggleDark">
          <Moon v-if="!isDark" />
          <Sunny v-else />
        </el-icon>
      </el-tooltip>

      <!-- 用户下拉菜单 -->
      <el-dropdown class="user-dropdown" @command="handleCommand">
        <div class="user-info">
          <el-avatar :size="32" :src="userAvatar">
            <el-icon><UserFilled /></el-icon>
          </el-avatar>
          <span v-if="!isMobile" class="username">{{ nickname || '用户' }}</span>
          <el-icon v-if="!isMobile" class="arrow-icon"><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">
              <el-icon><User /></el-icon>
              <span>个人中心</span>
            </el-dropdown-item>
            <el-dropdown-item command="settings">
              <el-icon><Setting /></el-icon>
              <span>系统设置</span>
            </el-dropdown-item>
            <el-dropdown-item divided command="logout">
              <el-icon><SwitchButton /></el-icon>
              <span>退出登录</span>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </el-header>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { useAppStore, useUserStore } from '@/stores'
import Breadcrumb from './Breadcrumb.vue'

const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

// 搜索关键词
const searchKeyword = ref('')

// 全屏状态
const isFullscreen = ref(false)

// 响应式检测
const isMobile = ref(false)

// 检测是否为移动端
function checkMobile(): void {
  isMobile.value = window.innerWidth < 768
}

// 计算属性
const sidebarCollapsed = computed(() => appStore.sidebarCollapsed)
const isDark = computed(() => appStore.isDark)
const nickname = computed(() => userStore.nickname)
const userAvatar = computed(() => userStore.userInfo?.avatar)

// 切换侧边栏
function toggleSidebar(): void {
  appStore.toggleSidebar()
}

// 打开移动端菜单
function openMobileMenu(): void {
  appStore.setMobileMenuOpen(true)
}

// 切换深色模式
function toggleDark(): void {
  appStore.toggleDark()
}

// 切换全屏
function toggleFullscreen(): void {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen()
    isFullscreen.value = true
  } else {
    document.exitFullscreen()
    isFullscreen.value = false
  }
}

// 监听全屏变化
function handleFullscreenChange(): void {
  isFullscreen.value = !!document.fullscreenElement
}

// 搜索处理
function handleSearch(): void {
  if (!searchKeyword.value.trim()) {
    return
  }
  // TODO: 实现全局搜索功能
  ElMessage.info(`搜索功能开发中: ${searchKeyword.value}`)
}

// 处理下拉菜单命令
async function handleCommand(command: string): Promise<void> {
  switch (command) {
    case 'profile':
      router.push('/profile')
      break
    case 'settings':
      router.push('/settings')
      break
    case 'logout':
      try {
        await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning',
        })
        userStore.logout()
        router.push('/login')
      } catch {
        // 用户取消操作
      }
      break
  }
}

// 生命周期
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

  :deep(.el-input__wrapper) {
    background-color: var(--bg-page);
    border-radius: var(--border-radius-base);
  }
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

.user-dropdown {
  cursor: pointer;

  .user-info {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 4px 8px;
    border-radius: var(--border-radius-base);
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
}

// 响应式设计
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
