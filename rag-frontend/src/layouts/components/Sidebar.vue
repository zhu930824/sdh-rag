<template>
  <!-- 移动端抽屉模式 -->
  <el-drawer
    v-if="isMobile"
    v-model="drawerVisible"
    direction="ltr"
    :size="220"
    :with-header="false"
    :z-index="1000"
    class="mobile-drawer"
    @close="handleClose"
  >
    <div class="drawer-content">
      <!-- Logo区域 -->
      <div class="logo-container" @click="handleLogoClick">
        <el-icon class="logo-icon" :size="28">
          <Reading />
        </el-icon>
        <span class="logo-text">智能知识库</span>
      </div>

      <!-- 导航菜单 -->
      <el-scrollbar class="menu-scrollbar">
        <el-menu
          :default-active="activeMenu"
          :unique-opened="true"
          router
          class="sidebar-menu"
          @select="handleMenuSelect"
        >
          <el-menu-item index="/dashboard">
            <el-icon><Odometer /></el-icon>
            <template #title>
              <span>首页</span>
            </template>
          </el-menu-item>

          <el-menu-item index="/knowledge">
            <el-icon><Folder /></el-icon>
            <template #title>
              <span>知识库</span>
            </template>
          </el-menu-item>

          <el-menu-item index="/chat">
            <el-icon><ChatDotRound /></el-icon>
            <template #title>
              <span>智能问答</span>
            </template>
          </el-menu-item>

          <el-menu-item index="/user">
            <el-icon><User /></el-icon>
            <template #title>
              <span>用户管理</span>
            </template>
          </el-menu-item>

          <el-menu-item index="/settings">
            <el-icon><Setting /></el-icon>
            <template #title>
              <span>系统设置</span>
            </template>
          </el-menu-item>
        </el-menu>
      </el-scrollbar>
    </div>
  </el-drawer>

  <!-- 桌面端侧边栏 -->
  <el-aside v-else :width="sidebarWidth" class="layout-sidebar">
    <!-- Logo区域 -->
    <div class="logo-container" @click="handleLogoClick">
      <el-icon class="logo-icon" :size="28">
        <Reading />
      </el-icon>
      <transition name="logo-text">
        <span v-show="!sidebarCollapsed" class="logo-text">智能知识库</span>
      </transition>
    </div>

    <!-- 导航菜单 -->
    <el-scrollbar class="menu-scrollbar">
      <el-menu
        :default-active="activeMenu"
        :collapse="sidebarCollapsed"
        :collapse-transition="false"
        :unique-opened="true"
        router
        class="sidebar-menu"
      >
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <template #title>
            <span>首页</span>
          </template>
        </el-menu-item>

        <el-menu-item index="/knowledge">
          <el-icon><Folder /></el-icon>
          <template #title>
            <span>知识库</span>
          </template>
        </el-menu-item>

        <el-menu-item index="/chat">
          <el-icon><ChatDotRound /></el-icon>
          <template #title>
            <span>智能问答</span>
          </template>
        </el-menu-item>

        <el-menu-item index="/user">
          <el-icon><User /></el-icon>
          <template #title>
            <span>用户管理</span>
          </template>
        </el-menu-item>

        <el-menu-item index="/settings">
          <el-icon><Setting /></el-icon>
          <template #title>
            <span>系统设置</span>
          </template>
        </el-menu-item>
      </el-menu>
    </el-scrollbar>

    <!-- 折叠按钮 -->
    <div class="collapse-trigger" @click="toggleSidebar">
      <el-icon :size="18">
        <ArrowLeft v-if="!sidebarCollapsed" />
        <ArrowRight v-else />
      </el-icon>
    </div>
  </el-aside>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/stores'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()

// 响应式检测
const isMobile = ref(false)
const windowWidth = ref(window.innerWidth)

// 检测是否为移动端
function checkMobile(): void {
  windowWidth.value = window.innerWidth
  isMobile.value = windowWidth.value < 768
}

// 计算属性
const sidebarCollapsed = computed(() => appStore.sidebarCollapsed)
const sidebarWidth = computed(() => appStore.sidebarWidth)
const activeMenu = computed(() => route.path)
const drawerVisible = computed({
  get: () => appStore.mobileMenuOpen,
  set: (value: boolean) => appStore.setMobileMenuOpen(value),
})

// 切换侧边栏
function toggleSidebar(): void {
  appStore.toggleSidebar()
}

// Logo点击处理
function handleLogoClick(): void {
  router.push('/dashboard')
  if (isMobile.value) {
    drawerVisible.value = false
  }
}

// 菜单选择处理
function handleMenuSelect(): void {
  drawerVisible.value = false
}

// 关闭抽屉
function handleClose(): void {
  drawerVisible.value = false
}

// 监听窗口大小变化
onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
})
</script>

<style scoped lang="scss">
// 移动端抽屉
.mobile-drawer {
  :deep(.el-drawer__body) {
    padding: 0;
  }

  .drawer-content {
    height: 100%;
    display: flex;
    flex-direction: column;
    background: var(--bg-overlay);
  }
}

// Logo区域
.logo-container {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 0 16px;
  border-bottom: 1px solid var(--border-lighter);
  cursor: pointer;
  transition: all var(--transition-duration);

  &:hover {
    background-color: var(--bg-page);
  }

  .logo-icon {
    color: var(--primary-color);
    flex-shrink: 0;
  }

  .logo-text {
    font-size: 16px;
    font-weight: 600;
    color: var(--text-primary);
    white-space: nowrap;
    overflow: hidden;
  }
}

// Logo文字动画
.logo-text-enter-active,
.logo-text-leave-active {
  transition: all 0.3s ease;
}

.logo-text-enter-from,
.logo-text-leave-to {
  opacity: 0;
  transform: translateX(-10px);
}

// 滚动区域
.menu-scrollbar {
  flex: 1;
  overflow: hidden;

  :deep(.el-scrollbar__wrap) {
    overflow-x: hidden;
  }
}

// 侧边栏菜单
.layout-sidebar {
  display: flex;
  flex-direction: column;
  background-color: var(--bg-overlay);
  border-right: 1px solid var(--border-lighter);
  transition: width var(--transition-duration);
  overflow: hidden;
}

.sidebar-menu {
  border-right: none;
  background-color: transparent;

  :deep(.el-menu-item) {
    height: 50px;
    line-height: 50px;
    margin: 4px 8px;
    border-radius: var(--border-radius-base);
    transition: all var(--transition-duration);

    &:hover {
      background-color: var(--bg-page);
    }

    &.is-active {
      background-color: var(--primary-light-9);
      color: var(--primary-color);
      font-weight: 500;

      .el-icon {
        color: var(--primary-color);
      }
    }
  }

  // 折叠状态样式
  &.el-menu--collapse {
    :deep(.el-menu-item) {
      margin: 4px;
      justify-content: center;
    }
  }
}

// 折叠按钮
.collapse-trigger {
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-top: 1px solid var(--border-lighter);
  cursor: pointer;
  color: var(--text-regular);
  transition: all var(--transition-duration);

  &:hover {
    background-color: var(--bg-page);
    color: var(--primary-color);
  }
}

// 响应式设计
@media (max-width: 768px) {
  .mobile-drawer {
    :deep(.el-drawer) {
      border-radius: 0;
    }

    :deep(.el-drawer__body) {
      padding: 0;
    }

    .drawer-content {
      height: 100%;
      display: flex;
      flex-direction: column;
      background: var(--bg-overlay);
    }

    .logo-container {
      padding: 16px;
      border-bottom: 1px solid var(--border-lighter);

      .logo-icon {
        font-size: 24px;
      }

      .logo-text {
        font-size: 15px;
      }
    }

    .menu-scrollbar {
      flex: 1;
      overflow: hidden;

      :deep(.el-scrollbar__wrap) {
        overflow-x: hidden;
      }
    }

    .sidebar-menu {
      border-right: none;
      background-color: transparent;

      :deep(.el-menu-item) {
        height: 56px;
        line-height: 56px;
        margin: 4px 12px;
        border-radius: var(--border-radius-base);
        transition: all var(--transition-duration);

        &:hover {
          background-color: var(--bg-page);
        }

        &.is-active {
          background-color: var(--primary-light-9);
          color: var(--primary-color);
          font-weight: 500;

          .el-icon {
            color: var(--primary-color);
          }
        }
      }
    }
  }
}

@media (max-width: 480px) {
  .mobile-drawer {
    :deep(.el-drawer) {
      width: 100% !important;
    }

    .logo-container {
      padding: 12px;

      .logo-icon {
        font-size: 22px;
      }

      .logo-text {
        font-size: 14px;
      }
    }

    .sidebar-menu {
      :deep(.el-menu-item) {
        height: 52px;
        line-height: 52px;
        margin: 4px 8px;
      }
    }
  }
}

// 平板设备
@media (min-width: 769px) and (max-width: 1024px) {
  .layout-sidebar {
    width: 200px !important;

    .logo-container {
      padding: 0 12px;

      .logo-text {
        font-size: 15px;
      }
    }

    .sidebar-menu {
      :deep(.el-menu-item) {
        height: 48px;
        line-height: 48px;
        margin: 4px 6px;
      }
    }
  }
}

// 深色模式适配
html.dark {
  .sidebar-menu {
    :deep(.el-menu-item.is-active) {
      background-color: rgba(64, 158, 255, 0.1);
    }
  }
}
</style>
