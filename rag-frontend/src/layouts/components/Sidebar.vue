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
          <rect width="32" height="32" rx="10" fill="url(#sidebar-logo-gradient)"/>
          <path d="M10 16L14 20L22 12" stroke="white" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
          <defs>
            <linearGradient id="sidebar-logo-gradient" x1="0" y1="0" x2="32" y2="32" gradientUnits="userSpaceOnUse">
              <stop stop-color="#0EA5E9"/>
              <stop offset="1" stop-color="#14B8A6"/>
            </linearGradient>
          </defs>
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
        <template v-for="item in filteredMenu" :key="item.key">
          <!-- 子菜单 -->
          <a-sub-menu v-if="item.children && item.children.length > 0" :key="item.key" class="nav-submenu">
            <template #icon>
              <component :is="item.icon" class="nav-icon" />
            </template>
            <template #title>{{ item.label }}</template>
            <a-menu-item v-for="child in item.children" :key="child.key">
              <component :is="child.icon" class="nav-icon-sm" />
              <span>{{ child.label }}</span>
            </a-menu-item>
          </a-sub-menu>

          <!-- 普通菜单项 -->
          <a-menu-item v-else :key="item.key" class="nav-item">
            <template #icon>
              <component :is="item.icon" class="nav-icon" />
            </template>
            <span>{{ item.label }}</span>
          </a-menu-item>

          <!-- 分隔线（在特定菜单后） -->
          <div v-if="['/chat', 'knowledge', '/model', '/tag'].includes(item.key)" class="nav-divider" />
        </template>
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
import { useUserStore } from '@/stores/user'
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
  ApartmentOutlined,
  BarChartOutlined,
  LikeOutlined,
  NotificationOutlined,
  TagsOutlined,
  TeamOutlined,
  DatabaseOutlined,
} from '@ant-design/icons-vue'

interface MenuItem {
  key: string
  label: string
  icon?: any
  permission?: string
  children?: MenuItem[]
}

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

const openKeys = ref<string[]>([])

// 菜单配置
const menuConfig: MenuItem[] = [
  {
    key: '/dashboard',
    label: '首页',
    icon: HomeOutlined,
    permission: '/dashboard',
  },
  {
    key: '/chat',
    label: '智能问答',
    icon: MessageOutlined,
    permission: '/chat',
  },
  {
    key: 'knowledge',
    label: '知识管理',
    icon: FolderOutlined,
    children: [
      { key: '/knowledge', label: '知识库', icon: DatabaseOutlined, permission: '/knowledge' },
      { key: '/document', label: '文档管理', icon: FileTextOutlined, permission: '/document' },
      { key: '/graph', label: '知识图谱', icon: ApartmentOutlined, permission: '/graph' },
    ],
  },
  {
    key: '/workflow',
    label: '工作流编排',
    icon: ForkOutlined,
    permission: '/workflow',
  },
  {
    key: '/model',
    label: '大模型管理',
    icon: ApiOutlined,
    permission: '/model',
  },
  {
    key: '/stats',
    label: '数据统计',
    icon: BarChartOutlined,
    permission: '/stats',
  },
  {
    key: '/hotwords',
    label: '热点词分析',
    icon: LineChartOutlined,
    permission: '/hotwords',
  },
  {
    key: '/feedback',
    label: '问答评价',
    icon: LikeOutlined,
    permission: '/feedback',
  },
  {
    key: '/tag',
    label: '标签管理',
    icon: TagsOutlined,
    permission: '/tag',
  },
  {
    key: 'system',
    label: '系统管理',
    icon: SettingOutlined,
    children: [
      { key: '/user', label: '用户管理', icon: UserOutlined, permission: '/user' },
      { key: '/role', label: '角色管理', icon: TeamOutlined, permission: '/role' },
      { key: '/log', label: '日志管理', icon: FileTextOutlined, permission: '/log' },
      { key: '/sensitive', label: '敏感词管理', icon: WarningOutlined, permission: '/sensitive' },
      { key: '/announcement', label: '公告管理', icon: NotificationOutlined, permission: '/announcement' },
      { key: '/settings', label: '系统设置', icon: SettingOutlined, permission: '/settings' },
    ],
  },
]

// 检查权限
function hasPermission(permission?: string): boolean {
  if (!permission) return true
  const userPermissions = userStore.permissions
  console.log('Checking permission:', permission, 'userPermissions:', userPermissions)
  if (!userPermissions || userPermissions.length === 0) return false
  return userPermissions.includes(permission)
}

// 过滤菜单
function filterMenu(items: MenuItem[]): MenuItem[] {
  return items
    .filter(item => {
      if (item.children) {
        return item.children.some(child => hasPermission(child.permission))
      }
      return hasPermission(item.permission)
    })
    .map(item => {
      if (item.children) {
        return {
          ...item,
          children: item.children.filter(child => hasPermission(child.permission)),
        }
      }
      return item
    })
}

// 过滤后的菜单
const filteredMenu = computed(() => filterMenu(menuConfig))

const subMenuMap: Record<string, string[]> = {
  'knowledge': ['/knowledge', '/document', '/graph'],
  'system': ['/user', '/role', '/log', '/sensitive', '/announcement', '/settings']
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
  transition: background var(--duration-fast) var(--ease-default);

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
  font-family: var(--font-display);
  font-size: 17px;
  font-weight: var(--font-weight-semibold);
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
}

.logo-text-enter-active,
.logo-text-leave-active {
  transition: opacity var(--duration-normal) var(--ease-default);
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
    font-weight: var(--font-weight-medium);
    color: var(--text-secondary);
    transition: all var(--duration-fast) var(--ease-default);

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
  transition: all var(--duration-fast) var(--ease-default);

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