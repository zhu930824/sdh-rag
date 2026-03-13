<template>
  <div class="layout-tabs">
    <el-scrollbar class="tabs-scrollbar">
      <div class="tabs-container">
        <div
          v-for="tab in visitedTabs"
          :key="tab.path"
          class="tab-item"
          :class="{ active: isActive(tab.path) }"
          @click="handleTabClick(tab)"
          @contextmenu.prevent="openContextMenu($event, tab)"
        >
          <el-icon v-if="tab.icon" class="tab-icon">
            <component :is="tab.icon" />
          </el-icon>
          <span class="tab-title">{{ tab.title }}</span>
          <el-icon
            v-if="!tab.affix"
            class="tab-close"
            @click.stop="handleTabClose(tab)"
          >
            <Close />
          </el-icon>
        </div>
      </div>
    </el-scrollbar>

    <!-- 右键菜单 -->
    <ul
      v-show="contextMenuVisible"
      class="context-menu"
      :style="{ left: contextMenuLeft + 'px', top: contextMenuTop + 'px' }"
    >
      <li @click="refreshTab">
        <el-icon><Refresh /></el-icon>
        <span>刷新</span>
      </li>
      <li v-if="!selectedTab?.affix" @click="closeTab">
        <el-icon><Close /></el-icon>
        <span>关闭</span>
      </li>
      <li @click="closeOtherTabs">
        <el-icon><FolderRemove /></el-icon>
        <span>关闭其他</span>
      </li>
      <li @click="closeLeftTabs">
        <el-icon><Back /></el-icon>
        <span>关闭左侧</span>
      </li>
      <li @click="closeRightTabs">
        <el-icon><Right /></el-icon>
        <span>关闭右侧</span>
      </li>
      <li @click="closeAllTabs">
        <el-icon><CircleClose /></el-icon>
        <span>关闭所有</span>
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter, type RouteLocationMatched } from 'vue-router'
import { useAppStore } from '@/stores'

interface TabItem {
  path: string
  title: string
  icon?: string
  affix?: boolean // 是否固定
}

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()

// 右键菜单状态
const contextMenuVisible = ref(false)
const contextMenuLeft = ref(0)
const contextMenuTop = ref(0)
const selectedTab = ref<TabItem | null>(null)

// 固定的标签页
const affixTabs: TabItem[] = [
  {
    path: '/dashboard',
    title: '首页',
    icon: 'Odometer',
    affix: true,
  },
]

// 计算属性
const visitedTabs = computed(() => appStore.visitedTabs)

// 判断是否激活
function isActive(path: string): boolean {
  return route.path === path
}

// 添加标签页
function addTab(): void {
  const { path, meta, matched } = route

  // 过滤隐藏的路由
  if (meta.hidden) {
    return
  }

  const title = meta.title as string
  if (!title) {
    return
  }

  // 获取图标
  const matchedRoute = matched[matched.length - 1] as RouteLocationMatched | undefined
  const icon = (matchedRoute?.meta?.icon as string) || meta.icon as string

  appStore.addTab({
    path,
    title,
    icon,
  })
}

// 点击标签页
function handleTabClick(tab: TabItem): void {
  router.push(tab.path)
}

// 关闭标签页
function handleTabClose(tab: TabItem): void {
  appStore.closeTab(tab.path)
  // 如果关闭的是当前标签页，跳转到最后一个标签页
  if (isActive(tab.path) && visitedTabs.value.length > 0) {
    const lastTab = visitedTabs.value[visitedTabs.value.length - 1]
    router.push(lastTab.path)
  }
}

// 打开右键菜单
function openContextMenu(e: MouseEvent, tab: TabItem): void {
  selectedTab.value = tab
  contextMenuLeft.value = e.clientX
  contextMenuTop.value = e.clientY
  contextMenuVisible.value = true
}

// 关闭右键菜单
function closeContextMenu(): void {
  contextMenuVisible.value = false
  selectedTab.value = null
}

// 刷新当前标签页
function refreshTab(): void {
  closeContextMenu()
  if (selectedTab.value) {
    appStore.refreshTab(selectedTab.value.path)
    // 重新加载当前路由
    router.replace({ path: '/redirect' + selectedTab.value.path })
  }
}

// 关闭选中标签页
function closeTab(): void {
  closeContextMenu()
  if (selectedTab.value) {
    handleTabClose(selectedTab.value)
  }
}

// 关闭其他标签页
function closeOtherTabs(): void {
  closeContextMenu()
  if (selectedTab.value) {
    appStore.closeOtherTabs(selectedTab.value.path)
  }
}

// 关闭左侧标签页
function closeLeftTabs(): void {
  closeContextMenu()
  if (selectedTab.value) {
    appStore.closeLeftTabs(selectedTab.value.path)
  }
}

// 关闭右侧标签页
function closeRightTabs(): void {
  closeContextMenu()
  if (selectedTab.value) {
    appStore.closeRightTabs(selectedTab.value.path)
  }
}

// 关闭所有标签页
function closeAllTabs(): void {
  closeContextMenu()
  appStore.closeAllTabs()
  // 跳转到首页
  router.push('/dashboard')
}

// 初始化固定标签页
function initTabs(): void {
  affixTabs.forEach((tab) => {
    appStore.addTab(tab)
  })
}

// 监听路由变化
watch(
  () => route.path,
  () => {
    addTab()
  },
  { immediate: true }
)

// 点击其他地方关闭右键菜单
function handleClickOutside(): void {
  closeContextMenu()
}

onMounted(() => {
  initTabs()
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

<style scoped lang="scss">
.layout-tabs {
  height: 40px;
  background-color: var(--bg-overlay);
  border-bottom: 1px solid var(--border-lighter);
  position: relative;
}

.tabs-scrollbar {
  height: 100%;

  :deep(.el-scrollbar__wrap) {
    height: 100%;
    overflow-y: hidden;
  }
}

.tabs-container {
  display: flex;
  align-items: center;
  height: 100%;
  padding: 0 10px;
  gap: 4px;
  white-space: nowrap;
}

.tab-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 12px;
  font-size: 13px;
  color: var(--text-regular);
  background-color: var(--bg-page);
  border-radius: var(--border-radius-small);
  cursor: pointer;
  transition: all var(--transition-duration);
  user-select: none;

  &:hover {
    background-color: var(--primary-light-9);
    color: var(--text-primary);
  }

  &.active {
    background-color: var(--primary-color);
    color: #fff;

    .tab-close:hover {
      background-color: rgba(255, 255, 255, 0.2);
    }
  }

  .tab-icon {
    font-size: 14px;
  }

  .tab-title {
    max-width: 120px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .tab-close {
    font-size: 12px;
    border-radius: 50%;
    transition: background-color var(--transition-duration);

    &:hover {
      background-color: var(--border-lighter);
    }
  }
}

.context-menu {
  position: fixed;
  z-index: 3000;
  min-width: 120px;
  padding: 5px 0;
  background-color: var(--bg-overlay);
  border: 1px solid var(--border-lighter);
  border-radius: var(--border-radius-base);
  box-shadow: var(--box-shadow);
  list-style: none;

  li {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 16px;
    font-size: 13px;
    color: var(--text-regular);
    cursor: pointer;
    transition: all var(--transition-duration);

    &:hover {
      background-color: var(--bg-page);
      color: var(--primary-color);
    }

    .el-icon {
      font-size: 14px;
    }
  }
}

// 深色模式适配
html.dark {
  .tab-item {
    &.active {
      background-color: var(--primary-color);
    }
  }
}
</style>
