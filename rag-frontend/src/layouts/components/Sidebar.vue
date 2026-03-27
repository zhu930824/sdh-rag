<template>
  <a-drawer
    v-if="isMobile"
    v-model:open="drawerVisible"
    placement="left"
    :width="220"
    :closable="false"
    class="mobile-drawer"
    @close="handleClose"
  >
    <div class="drawer-content">
      <div class="logo-container" @click="handleLogoClick">
        <ReadOutlined class="logo-icon" />
        <span class="logo-text">智能知识库</span>
      </div>

      <a-menu
        v-model:selectedKeys="selectedKeys"
        mode="inline"
        class="sidebar-menu"
        @click="handleMenuClick"
      >
        <a-menu-item key="/dashboard">
          <template #icon><HomeOutlined /></template>
          <span>首页</span>
        </a-menu-item>

        <a-menu-item key="/knowledge">
          <template #icon><FolderOutlined /></template>
          <span>知识库</span>
        </a-menu-item>

        <a-menu-item key="/chat">
          <template #icon><MessageOutlined /></template>
          <span>智能问答</span>
        </a-menu-item>

        <a-menu-item key="/user">
          <template #icon><UserOutlined /></template>
          <span>用户管理</span>
        </a-menu-item>

        <a-menu-item key="/log">
          <template #icon><FileTextOutlined /></template>
          <span>日志管理</span>
        </a-menu-item>

        <a-menu-item key="/sensitive">
          <template #icon><WarningOutlined /></template>
          <span>敏感词管理</span>
        </a-menu-item>

        <a-menu-item key="/hotwords">
          <template #icon><LineChartOutlined /></template>
          <span>热点词分析</span>
        </a-menu-item>

        <a-menu-item key="/settings">
          <template #icon><SettingOutlined /></template>
          <span>系统设置</span>
        </a-menu-item>

        <a-menu-item key="/model">
          <template #icon><ApiOutlined /></template>
          <span>大模型管理</span>
        </a-menu-item>

        <a-menu-item key="/workflow">
          <template #icon><BranchOutlined /></template>
          <span>工作流编排</span>
        </a-menu-item>

        <a-menu-item key="/nlp-query">
          <template #icon><SearchOutlined /></template>
          <span>自然语言查询</span>
        </a-menu-item>

        <a-menu-item key="/embed">
          <template #icon><CodeOutlined /></template>
          <span>嵌入聊天配置</span>
        </a-menu-item>

        <a-menu-item key="/graph">
          <template #icon><ApartmentOutlined /></template>
          <span>知识图谱</span>
        </a-menu-item>

        <a-menu-item key="/assistant">
          <template #icon><RobotOutlined /></template>
          <span>助手市场</span>
        </a-menu-item>

        <a-menu-item key="/stats">
          <template #icon><BarChartOutlined /></template>
          <span>数据统计</span>
        </a-menu-item>

        <a-menu-item key="/feedback">
          <template #icon><LikeOutlined /></template>
          <span>问答评价</span>
        </a-menu-item>

        <a-menu-item key="/announcement">
          <template #icon><NotificationOutlined /></template>
          <span>公告管理</span>
        </a-menu-item>

        <a-menu-item key="/approval">
          <template #icon><AuditOutlined /></template>
          <span>审核中心</span>
        </a-menu-item>

        <a-menu-item key="/points">
          <template #icon><GiftOutlined /></template>
          <span>积分商城</span>
        </a-menu-item>

        <a-menu-item key="/channel">
          <template #icon><ApiOutlined /></template>
          <span>渠道管理</span>
        </a-menu-item>

        <a-menu-item key="/process-task">
          <template #icon><CloudUploadOutlined /></template>
          <span>文档预处理</span>
        </a-menu-item>

        <a-menu-item key="/tag">
          <template #icon><TagsOutlined /></template>
          <span>标签管理</span>
        </a-menu-item>

        <a-menu-item key="/voice">
          <template #icon><AudioOutlined /></template>
          <span>语音问答</span>
        </a-menu-item>
      </a-menu>
    </div>
  </a-drawer>

  <a-layout-sider
    v-else
    :collapsed="sidebarCollapsed"
    :width="sidebarWidth"
    :collapsedWidth="64"
    class="layout-sidebar"
    :trigger="null"
  >
    <div class="logo-container" @click="handleLogoClick">
      <ReadOutlined class="logo-icon" />
      <transition name="logo-text">
        <span v-show="!sidebarCollapsed" class="logo-text">智能知识库</span>
      </transition>
    </div>

    <a-menu
      v-model:selectedKeys="selectedKeys"
      mode="inline"
      :inline-collapsed="sidebarCollapsed"
      class="sidebar-menu"
      @click="handleMenuClick"
    >
      <a-menu-item key="/dashboard">
        <template #icon><HomeOutlined /></template>
        <span>首页</span>
      </a-menu-item>

      <a-menu-item key="/knowledge">
        <template #icon><FolderOutlined /></template>
        <span>知识库</span>
      </a-menu-item>

      <a-menu-item key="/chat">
        <template #icon><MessageOutlined /></template>
        <span>智能问答</span>
      </a-menu-item>

      <a-menu-item key="/user">
        <template #icon><UserOutlined /></template>
        <span>用户管理</span>
      </a-menu-item>

      <a-menu-item key="/log">
        <template #icon><FileTextOutlined /></template>
        <span>日志管理</span>
      </a-menu-item>

      <a-menu-item key="/sensitive">
        <template #icon><WarningOutlined /></template>
        <span>敏感词管理</span>
      </a-menu-item>

      <a-menu-item key="/hotwords">
        <template #icon><LineChartOutlined /></template>
        <span>热点词分析</span>
      </a-menu-item>

        <a-menu-item key="/settings">
          <template #icon><SettingOutlined /></template>
          <span>系统设置</span>
        </a-menu-item>

        <a-menu-item key="/model">
          <template #icon><ApiOutlined /></template>
          <span>大模型管理</span>
        </a-menu-item>

        <a-menu-item key="/workflow">
          <template #icon><BranchOutlined /></template>
          <span>工作流编排</span>
        </a-menu-item>

        <a-menu-item key="/nlp-query">
          <template #icon><SearchOutlined /></template>
          <span>自然语言查询</span>
        </a-menu-item>

        <a-menu-item key="/embed">
          <template #icon><CodeOutlined /></template>
          <span>嵌入聊天配置</span>
        </a-menu-item>

        <a-menu-item key="/graph">
          <template #icon><ApartmentOutlined /></template>
          <span>知识图谱</span>
        </a-menu-item>

        <a-menu-item key="/assistant">
          <template #icon><RobotOutlined /></template>
          <span>助手市场</span>
        </a-menu-item>

        <a-menu-item key="/stats">
          <template #icon><BarChartOutlined /></template>
          <span>数据统计</span>
        </a-menu-item>

        <a-menu-item key="/feedback">
          <template #icon><LikeOutlined /></template>
          <span>问答评价</span>
        </a-menu-item>

        <a-menu-item key="/announcement">
          <template #icon><NotificationOutlined /></template>
          <span>公告管理</span>
        </a-menu-item>

        <a-menu-item key="/approval">
          <template #icon><AuditOutlined /></template>
          <span>审核中心</span>
        </a-menu-item>

        <a-menu-item key="/points">
          <template #icon><GiftOutlined /></template>
          <span>积分商城</span>
        </a-menu-item>

        <a-menu-item key="/channel">
          <template #icon><ApiOutlined /></template>
          <span>渠道管理</span>
        </a-menu-item>

        <a-menu-item key="/process-task">
          <template #icon><CloudUploadOutlined /></template>
          <span>文档预处理</span>
        </a-menu-item>

        <a-menu-item key="/tag">
          <template #icon><TagsOutlined /></template>
          <span>标签管理</span>
        </a-menu-item>

        <a-menu-item key="/voice">
          <template #icon><AudioOutlined /></template>
          <span>语音问答</span>
        </a-menu-item>
    </a-menu>

    <div class="collapse-trigger" @click="toggleSidebar">
      <MenuFoldOutlined v-if="!sidebarCollapsed" />
      <MenuUnfoldOutlined v-else />
    </div>
  </a-layout-sider>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
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
  ReadOutlined,
  FileTextOutlined,
  WarningOutlined,
  LineChartOutlined,
  ApiOutlined,
  BranchOutlined,
  SearchOutlined,
  CodeOutlined,
  ApartmentOutlined,
  RobotOutlined,
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

const isMobile = ref(false)
const windowWidth = ref(window.innerWidth)

function checkMobile(): void {
  windowWidth.value = window.innerWidth
  isMobile.value = windowWidth.value < 768
}

const sidebarCollapsed = computed(() => appStore.sidebarCollapsed)
const sidebarWidth = computed(() => appStore.sidebarWidth)
const selectedKeys = computed({
  get: () => [route.path],
  set: () => {},
})
const drawerVisible = computed({
  get: () => appStore.mobileMenuOpen,
  set: (value: boolean) => appStore.setMobileMenuOpen(value),
})

function toggleSidebar(): void {
  appStore.toggleSidebar()
}

function handleLogoClick(): void {
  router.push('/dashboard')
  if (isMobile.value) {
    drawerVisible.value = false
  }
}

function handleMenuClick({ key }: { key: string }): void {
  router.push(key)
  drawerVisible.value = false
}

function handleClose(): void {
  drawerVisible.value = false
}

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
})
</script>

<style scoped lang="scss">
.mobile-drawer {
  :deep(.ant-drawer-body) {
    padding: 0;
  }

  .drawer-content {
    height: 100%;
    display: flex;
    flex-direction: column;
    background: var(--bg-overlay);
  }
}

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
    font-size: 28px;
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

.logo-text-enter-active,
.logo-text-leave-active {
  transition: all 0.3s ease;
}

.logo-text-enter-from,
.logo-text-leave-to {
  opacity: 0;
  transform: translateX(-10px);
}

.layout-sidebar {
  display: flex;
  flex-direction: column;
  background-color: var(--bg-overlay) !important;
  border-right: 1px solid var(--border-lighter);
  overflow: hidden;

  :deep(.ant-layout-sider-children) {
    display: flex;
    flex-direction: column;
    height: 100%;
  }
}

.sidebar-menu {
  flex: 1;
  border-right: none !important;
  background-color: transparent !important;

  :deep(.ant-menu-item) {
    height: 50px;
    line-height: 50px;
    margin: 4px 8px;
    border-radius: var(--border-radius-base);
    transition: all var(--transition-duration);

    &:hover {
      background-color: var(--bg-page);
    }

    &.ant-menu-item-selected {
      background-color: var(--primary-light-9);
      color: var(--primary-color);
      font-weight: 500;

      .anticon {
        color: var(--primary-color);
      }
    }
  }

  &.ant-menu-inline-collapsed {
    :deep(.ant-menu-item) {
      margin: 4px;
      justify-content: center;
      padding: 0 calc(50% - 16px) !important;
    }
  }
}

.collapse-trigger {
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-top: 1px solid var(--border-lighter);
  cursor: pointer;
  color: var(--text-regular);
  font-size: 18px;
  transition: all var(--transition-duration);

  &:hover {
    background-color: var(--bg-page);
    color: var(--primary-color);
  }
}

@media (max-width: 768px) {
  .mobile-drawer {
    :deep(.ant-drawer-body) {
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

    .sidebar-menu {
      border-right: none;
      background-color: transparent;

      :deep(.ant-menu-item) {
        height: 56px;
        line-height: 56px;
        margin: 4px 12px;
        border-radius: var(--border-radius-base);
        transition: all var(--transition-duration);

        &:hover {
          background-color: var(--bg-page);
        }

        &.ant-menu-item-selected {
          background-color: var(--primary-light-9);
          color: var(--primary-color);
          font-weight: 500;

          .anticon {
            color: var(--primary-color);
          }
        }
      }
    }
  }
}

@media (max-width: 480px) {
  .mobile-drawer {
    :deep(.ant-drawer-content) {
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
      :deep(.ant-menu-item) {
        height: 52px;
        line-height: 52px;
        margin: 4px 8px;
      }
    }
  }
}

@media (min-width: 769px) and (max-width: 1024px) {
  .layout-sidebar {
    .logo-container {
      padding: 0 12px;

      .logo-text {
        font-size: 15px;
      }
    }

    .sidebar-menu {
      :deep(.ant-menu-item) {
        height: 48px;
        line-height: 48px;
        margin: 4px 6px;
      }
    }
  }
}

html.dark {
  .sidebar-menu {
    :deep(.ant-menu-item.ant-menu-item-selected) {
      background-color: rgba(64, 158, 255, 0.1);
    }
  }
}
</style>
