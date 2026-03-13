import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

// 标签页类型
export interface TabItem {
  path: string
  title: string
  icon?: string
  affix?: boolean // 是否固定
}

export const useAppStore = defineStore('app', () => {
  // 侧边栏折叠状态
  const sidebarCollapsed = ref<boolean>(false)

  // 深色模式
  const isDark = ref<boolean>(false)

  // 设备类型
  const device = ref<'desktop' | 'mobile'>('desktop')

  // 移动端菜单打开状态
  const mobileMenuOpen = ref<boolean>(false)

  // 访问过的标签页
  const visitedTabs = ref<TabItem[]>([])

  // 计算属性
  const sidebarWidth = computed(() => (sidebarCollapsed.value ? '64px' : '220px'))

  // 切换侧边栏
  function toggleSidebar(): void {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  // 切换深色模式
  function toggleDark(): void {
    isDark.value = !isDark.value
    // 更新HTML类名
    document.documentElement.classList.toggle('dark', isDark.value)
    // 持久化
    localStorage.setItem('darkMode', String(isDark.value))
  }

  // 初始化深色模式
  function initDarkMode(): void {
    const stored = localStorage.getItem('darkMode')
    if (stored !== null) {
      isDark.value = stored === 'true'
    } else {
      // 跟随系统
      isDark.value = window.matchMedia('(prefers-color-scheme: dark)').matches
    }
    document.documentElement.classList.toggle('dark', isDark.value)
  }

  // 设置设备类型
  function setDevice(newDevice: 'desktop' | 'mobile'): void {
    device.value = newDevice
    if (newDevice === 'mobile') {
      sidebarCollapsed.value = true
    }
  }

  // 设置移动端菜单状态
  function setMobileMenuOpen(open: boolean): void {
    mobileMenuOpen.value = open
  }

  // 添加标签页
  function addTab(tab: TabItem): void {
    // 检查是否已存在
    const exists = visitedTabs.value.some((item) => item.path === tab.path)
    if (!exists) {
      visitedTabs.value.push(tab)
    }
  }

  // 关闭标签页
  function closeTab(path: string): void {
    const index = visitedTabs.value.findIndex((tab) => tab.path === path)
    if (index > -1 && !visitedTabs.value[index].affix) {
      visitedTabs.value.splice(index, 1)
    }
  }

  // 刷新标签页（触发重新加载）
  function refreshTab(path: string): void {
    // 通过移除再添加来触发刷新
    const index = visitedTabs.value.findIndex((tab) => tab.path === path)
    if (index > -1) {
      const tab = visitedTabs.value[index]
      visitedTabs.value.splice(index, 1)
      setTimeout(() => {
        visitedTabs.value.splice(index, 0, tab)
      }, 0)
    }
  }

  // 关闭其他标签页
  function closeOtherTabs(path: string): void {
    visitedTabs.value = visitedTabs.value.filter(
      (tab) => tab.path === path || tab.affix
    )
  }

  // 关闭左侧标签页
  function closeLeftTabs(path: string): void {
    const index = visitedTabs.value.findIndex((tab) => tab.path === path)
    if (index > -1) {
      visitedTabs.value = visitedTabs.value.filter(
        (tab, i) => i >= index || tab.affix
      )
    }
  }

  // 关闭右侧标签页
  function closeRightTabs(path: string): void {
    const index = visitedTabs.value.findIndex((tab) => tab.path === path)
    if (index > -1) {
      visitedTabs.value = visitedTabs.value.filter(
        (tab, i) => i <= index || tab.affix
      )
    }
  }

  // 关闭所有标签页
  function closeAllTabs(): void {
    visitedTabs.value = visitedTabs.value.filter((tab) => tab.affix)
  }

  return {
    // 状态
    sidebarCollapsed,
    isDark,
    device,
    mobileMenuOpen,
    visitedTabs,
    // 计算属性
    sidebarWidth,
    // 方法
    toggleSidebar,
    toggleDark,
    initDarkMode,
    setDevice,
    setMobileMenuOpen,
    addTab,
    closeTab,
    refreshTab,
    closeOtherTabs,
    closeLeftTabs,
    closeRightTabs,
    closeAllTabs,
  }
})
