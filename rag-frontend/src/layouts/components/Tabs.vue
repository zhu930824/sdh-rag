<template>
  <div class="layout-tabs">
    <div class="tabs-scroll-container">
      <div class="tabs-container">
        <div
          v-for="tab in visitedTabs"
          :key="tab.path"
          class="tab-item"
          :class="{ active: isActive(tab.path) }"
          @click="handleTabClick(tab)"
          @contextmenu.prevent="openContextMenu($event, tab)"
        >
          <span class="tab-title">{{ tab.title }}</span>
          <CloseOutlined
            v-if="!tab.affix"
            class="tab-close"
            @click.stop="handleTabClose(tab)"
          />
        </div>
      </div>
    </div>

    <ul
      v-show="contextMenuVisible"
      class="context-menu"
      :style="{ left: contextMenuLeft + 'px', top: contextMenuTop + 'px' }"
    >
      <li @click="refreshTab">
        <ReloadOutlined />
        <span>刷新</span>
      </li>
      <li v-if="!selectedTab?.affix" @click="closeTab">
        <CloseOutlined />
        <span>关闭</span>
      </li>
      <li @click="closeOtherTabs">
        <FolderOutlined />
        <span>关闭其他</span>
      </li>
      <li @click="closeLeftTabs">
        <LeftOutlined />
        <span>关闭左侧</span>
      </li>
      <li @click="closeRightTabs">
        <RightOutlined />
        <span>关闭右侧</span>
      </li>
      <li @click="closeAllTabs">
        <CloseCircleOutlined />
        <span>关闭所有</span>
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter, type RouteLocationMatched } from 'vue-router'
import {
  CloseOutlined,
  ReloadOutlined,
  FolderOutlined,
  LeftOutlined,
  RightOutlined,
  CloseCircleOutlined,
} from '@ant-design/icons-vue'
import { useAppStore } from '@/stores'

interface TabItem {
  path: string
  title: string
  icon?: string
  affix?: boolean
}

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()

const contextMenuVisible = ref(false)
const contextMenuLeft = ref(0)
const contextMenuTop = ref(0)
const selectedTab = ref<TabItem | null>(null)

const affixTabs: TabItem[] = [
  {
    path: '/dashboard',
    title: '首页',
    icon: 'HomeOutlined',
    affix: true,
  },
]

const visitedTabs = computed(() => appStore.visitedTabs)

function isActive(path: string): boolean {
  return route.path === path
}

function addTab(): void {
  const { path, meta, matched } = route

  if (meta.hidden) {
    return
  }

  const title = meta.title as string
  if (!title) {
    return
  }

  const matchedRoute = matched[matched.length - 1] as RouteLocationMatched | undefined
  const icon = (matchedRoute?.meta?.icon as string) || (meta.icon as string)

  appStore.addTab({
    path,
    title,
    icon,
  })
}

function handleTabClick(tab: TabItem): void {
  router.push(tab.path)
}

function handleTabClose(tab: TabItem): void {
  appStore.closeTab(tab.path)
  if (isActive(tab.path) && visitedTabs.value.length > 0) {
    const lastTab = visitedTabs.value[visitedTabs.value.length - 1]
    router.push(lastTab.path)
  }
}

function openContextMenu(e: MouseEvent, tab: TabItem): void {
  selectedTab.value = tab
  contextMenuLeft.value = e.clientX
  contextMenuTop.value = e.clientY
  contextMenuVisible.value = true
}

function closeContextMenu(): void {
  contextMenuVisible.value = false
  selectedTab.value = null
}

function refreshTab(): void {
  closeContextMenu()
  if (selectedTab.value) {
    appStore.refreshTab(selectedTab.value.path)
    router.replace({ path: '/redirect' + selectedTab.value.path })
  }
}

function closeTab(): void {
  closeContextMenu()
  if (selectedTab.value) {
    handleTabClose(selectedTab.value)
  }
}

function closeOtherTabs(): void {
  closeContextMenu()
  if (selectedTab.value) {
    appStore.closeOtherTabs(selectedTab.value.path)
  }
}

function closeLeftTabs(): void {
  closeContextMenu()
  if (selectedTab.value) {
    appStore.closeLeftTabs(selectedTab.value.path)
  }
}

function closeRightTabs(): void {
  closeContextMenu()
  if (selectedTab.value) {
    appStore.closeRightTabs(selectedTab.value.path)
  }
}

function closeAllTabs(): void {
  closeContextMenu()
  appStore.closeAllTabs()
  router.push('/dashboard')
}

function initTabs(): void {
  affixTabs.forEach((tab) => {
    appStore.addTab(tab)
  })
}

watch(
  () => route.path,
  () => {
    addTab()
  },
  { immediate: true }
)

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

.tabs-scroll-container {
  height: 100%;
  overflow-x: auto;
  overflow-y: hidden;

  &::-webkit-scrollbar {
    height: 4px;
  }

  &::-webkit-scrollbar-thumb {
    background-color: var(--border-lighter);
    border-radius: 2px;
  }

  &::-webkit-scrollbar-track {
    background-color: transparent;
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
  }
}

html.dark {
  .tab-item {
    &.active {
      background-color: var(--primary-color);
    }
  }
}
</style>
