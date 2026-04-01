<template>
  <a-layout-sider
    :collapsed="collapsed"
    :width="260"
    :collapsedWidth="72"
    class="app-sidebar"
    :trigger="null"
  >
    <!-- Logo -->
    <div class="sidebar-header" @click="navigateToDashboard">
      <div class="logo-mark">
        <svg viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
          <rect width="32" height="32" rx="10" fill="#059669"/>
          <path d="M10 16L14 20L22 12" stroke="white" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </div>
      <transition name="logo-text">
        <span v-show="!collapsed" class="logo-text">智能知识库</span>
      </transition>
    </div>

    <!-- Navigation -->
    <nav class="sidebar-nav">
      <a-menu
        v-model:selectedKeys="selectedKeys"
        v-model:openKeys="openKeys"
        mode="inline"
        :inline-collapsed="collapsed"
        class="nav-menu"
        @click="handleMenuClick"
      >
        <!-- Core -->
        <a-menu-item key="/dashboard" class="nav-item">
          <template #icon>
            <HomeOutlined class="nav-icon" />
          </template>
          <span>首页</span>
        </a-menu-item>

        <a-menu-item key="/knowledge" class="nav-item">
          <template #icon>
            <FolderOutlined class="nav-icon" />
          </template>
          <span>知识库</span>
        </a-menu-item>

        <a-menu-item key="/chat" class="nav-item">
          <template #icon>
            <MessageOutlined class="nav-icon" />
          </template>
          <span>智能问答</span>
        </a-menu-item>

        <a-menu-item key="/graph" class="nav-item">
          <template #icon>
            <ApartmentOutlined class="nav-icon" />
          </template>
          <span>知识图谱</span>
        </a-menu-item>

        <!-- Divider -->
        <div class="nav-divider" />

        <!-- Tools -->
        <a-menu-item key="/workflow" class="nav-item">
          <template #icon>
            <ForkOutlined class="nav-icon" />
          </template>
          <span>工作流编排</span>
        </a-menu-item>

        <a-menu-item key="/model" class="nav-item">
          <template #icon>
            <ApiOutlined class="nav-icon" />
          </template>
          <span>大模型管理</span>
        </a-menu-item>

        <a-menu-item key="/embed" class="nav-item">
          <template #icon>
            <CodeOutlined class="nav-icon" />
          </template>
          <span>嵌入配置</span>
        </a-menu-item>

        <!-- Divider -->
        <div class="nav-divider" />

        <!-- Analysis -->
        <a-menu-item key="/stats" class="nav-item">
          <template #icon>
            <BarChartOutlined class="nav-icon" />
          </template>
          <span>数据统计</span>
        </a-menu-item>

        <a-menu-item key="/hotwords" class="nav-item">
          <template #icon>
            <LineChartOutlined class="nav-icon" />
          </template>
          <span>热点词分析</span>
        </a-menu-item>

        <a-menu-item key="/feedback" class="nav-item">
          <template #icon>
            <LikeOutlined class="nav-icon" />
          </template>
          <span>问答评价</span>
        </a-menu-item>

        <a-menu-item key="/approval" class="nav-item">
          <template #icon>
            <AuditOutlined class="nav-icon" />
          </template>
          <span>审核中心</span>
        </a-menu-item>

        <!-- Divider -->
        <div class="nav-divider" />

        <!-- More -->
        <a-menu-item key="/process-task" class="nav-item">
          <template #icon>
            <CloudUploadOutlined class="nav-icon" />
          </template>
          <span>文档预处理</span>
        </a-menu-item>

        <a-menu-item key="/tag" class="nav-item">
          <template #icon>
            <TagsOutlined class="nav-icon" />
          </template>
          <span>标签管理</span>
        </a-menu-item>


        <!-- Divider -->
        <div class="nav-divider" />

        <!-- System -->
        <a-sub-menu key="system" class="nav-submenu">
          <template #icon>
            <SettingOutlined class="nav-icon" />
          </template>
          <template #title>系统管理</template>
          <a-menu-item key="/user">
            <UserOutlined class="nav-icon-sm" />
            <span>用户管理</span>
          </a-menu-item>
          <a-menu-item key="/log">
            <FileTextOutlined class="nav-icon-sm" />
            <span>日志管理</span>
          </a-menu-item>
          <a-menu-item key="/sensitive">
            <WarningOutlined class="nav-icon-sm" />
            <span>敏感词管理</span>
          </a-menu-item>
          <a-menu-item key="/announcement">
            <NotificationOutlined class="nav-icon-sm" />
            <span>公告管理</span>
          </a-menu-item>
          <a-menu-item key="/settings">
            <SettingOutlined class="nav-icon-sm" />
            <span>系统设置</span>
          </a-menu-item>
        </a-sub-menu>

        <a-menu-item key="/points" class="nav-item">
          <template #icon>
            <GiftOutlined class="nav-icon" />
          </template>
          <span>积分商城</span>
        </a-menu-item>

        <a-menu-item key="/channel" class="nav-item">
          <template #icon>
            <ApiOutlined class="nav-icon" />
          </template>
          <span>渠道管理</span>
        </a-menu-item>
      </a-menu>
    </nav>

    <!-- Collapse Toggle -->
    <div class="sidebar-footer">
      <button class="collapse-btn" @click="toggleCollapsed">
        <MenuFoldOutlined v-if="!collapsed" />
        <MenuUnfoldOutlined v-else />
      </button>
    </div>
  </a-layout-sider>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/stores'
import {
  HomeOutlined,
  FolderOutlined,
  MessageOutlined,
  UserOutlined,
  SettingOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  FileTextOutlined,
  WarningOutlined,
  LineChartOutlined,
  ApiOutlined,
  ForkOutlined,
  CodeOutlined,
  ApartmentOutlined,
  BarChartOutlined,
  LikeOutlined,
  NotificationOutlined,
  AuditOutlined,
  GiftOutlined,
  CloudUploadOutlined,
  TagsOutlined,
  AudioOutlined,
} from '@ant-design/icons-vue'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()

const openKeys = ref<string[]>([])

const subMenuMap: Record<string, string[]> = {
  'system': ['/user', '/log', '/sensitive', '/announcement', '/settings']
}

function updateOpenKeys(path: string): void {
  for (const [key, paths] of Object.entries(subMenuMap)) {
    if (paths.includes(path)) {
      if (!openKeys.value.includes(key)) {
        openKeys.value = [key]
      }
      return
    }
  }
}

const collapsed = computed(() => appStore.sidebarCollapsed)
const selectedKeys = computed({
  get: () => [route.path],
  set: () => {},
})

function toggleCollapsed(): void {
  appStore.toggleSidebar()
}

function navigateToDashboard(): void {
  router.push('/dashboard')
}

function handleMenuClick({ key }: { key: string }): void {
  if (key.startsWith('/')) {
    router.push(key)
  }
}

watch(
  () => route.path,
  (path) => {
    updateOpenKeys(path)
  },
  { immediate: true }
)
</script>

<style scoped lang="scss">
.app-sidebar {
  display: flex;
  flex-direction: column;
  background: var(--bg-surface);
  border-right: 1px solid var(--border-color);
  z-index: var(--z-fixed);

  :deep(.ant-layout-sider-children) {
    display: flex;
    flex-direction: column;
    height: 100%;
  }
}

// Header
.sidebar-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 16px;
  cursor: pointer;
  transition: background var(--duration-fast) var(--ease-nature);

  &:hover {
    background: var(--bg-surface-secondary);
  }
}

.logo-mark {
  width: 32px;
  height: 32px;
  flex-shrink: 0;

  svg {
    width: 100%;
    height: 100%;
  }
}

.logo-text {
  font-family: var(--font-serif);
  font-size: 17px;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
}

.logo-text-enter-active,
.logo-text-leave-active {
  transition: opacity var(--duration-normal) var(--ease-nature);
}

.logo-text-enter-from,
.logo-text-leave-to {
  opacity: 0;
}

// Navigation
.sidebar-nav {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 8px 12px;
}

.nav-menu {
  background: transparent;
  border: none;

  :deep(.ant-menu-item),
  :deep(.ant-menu-submenu-title) {
    height: 44px;
    line-height: 44px;
    margin: 2px 0;
    padding: 0 14px !important;
    border-radius: var(--radius-lg);
    font-weight: 500;
    color: var(--text-secondary);
    transition: all var(--duration-fast) var(--ease-nature);

    &:hover {
      background: var(--bg-surface-secondary);
      color: var(--text-primary);
    }

    .nav-icon {
      font-size: 18px;
      margin-right: 12px;
      color: var(--text-tertiary);
    }
  }

  :deep(.ant-menu-item-selected) {
    background: var(--primary-lighter) !important;
    color: var(--primary-color) !important;

    .nav-icon {
      color: var(--primary-color);
    }
  }

  :deep(.ant-menu-submenu-open) > .ant-menu-submenu-title {
    color: var(--text-primary);

    .nav-icon {
      color: var(--text-secondary);
    }
  }

  :deep(.ant-menu-sub) {
    background: transparent;

    .ant-menu-item {
      padding-left: 46px !important;
      height: 40px;
      line-height: 40px;

      .nav-icon-sm {
        font-size: 16px;
        margin-right: 10px;
      }
    }
  }

  &.ant-menu-inline-collapsed {
    :deep(.ant-menu-item),
    :deep(.ant-menu-submenu-title) {
      padding: 0 calc(50% - 18px) !important;
      justify-content: center;

      .nav-icon {
        margin-right: 0;
      }
    }
  }
}

// Divider
.nav-divider {
  height: 1px;
  background: var(--border-color);
  margin: 10px 12px;
}

// Footer
.sidebar-footer {
  padding: 12px;
  border-top: 1px solid var(--border-color);
}

.collapse-btn {
  width: 100%;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  color: var(--text-secondary);
  font-size: 16px;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-nature);

  &:hover {
    background: var(--bg-surface-secondary);
    border-color: var(--primary-color);
    color: var(--primary-color);
  }
}

// Dark Mode
html.dark {
  .app-sidebar {
    background: var(--bg-page);
  }
}
</style>