import { onMounted, onUnmounted, ref, type Ref } from 'vue'

/**
 * 窗口尺寸变化监听
 */
export function useResizeObserver(target: Ref<HTMLElement | null>) {
  const width = ref(0)
  const height = ref(0)

  let observer: ResizeObserver | null = null

  onMounted(() => {
    if (!target.value) return
    observer = new ResizeObserver(entries => {
      const { width: w, height: h } = entries[0].contentRect
      width.value = w
      height.value = h
    })
    observer.observe(target.value)
  })

  onUnmounted(() => {
    observer?.disconnect()
  })

  return { width, height }
}

/**
 * 加载状态管理
 */
export function useLoading(initLoading = false) {
  const loading = ref(initLoading)

  const startLoading = () => {
    loading.value = true
  }

  const stopLoading = () => {
    loading.value = false
  }

  const withLoading = async <T>(fn: () => Promise<T>): Promise<T> => {
    startLoading()
    try {
      return await fn()
    } finally {
      stopLoading()
    }
  }

  return { loading, startLoading, stopLoading, withLoading }
}

/**
 * 本地存储
 */
export function useLocalStorage<T>(key: string, defaultValue: T) {
  const storedValue = localStorage.getItem(key)
  const value = ref<T>(storedValue ? JSON.parse(storedValue) : defaultValue)

  const setValue = (newValue: T) => {
    value.value = newValue as any
    localStorage.setItem(key, JSON.stringify(newValue))
  }

  const removeValue = () => {
    value.value = defaultValue as any
    localStorage.removeItem(key)
  }

  return { value, setValue, removeValue }
}

/**
 * 鼠标位置追踪
 */
export function useMouse() {
  const x = ref(0)
  const y = ref(0)

  const update = (e: MouseEvent) => {
    x.value = e.pageX
    y.value = e.pageY
  }

  onMounted(() => {
    window.addEventListener('mousemove', update)
  })

  onUnmounted(() => {
    window.removeEventListener('mousemove', update)
  })

  return { x, y }
}

/**
 * 快捷键配置接口
 */
export interface HotkeyConfig {
  key: string // 按键
  ctrl?: boolean // 是否需要 Ctrl/Cmd
  shift?: boolean // 是否需要 Shift
  alt?: boolean // 是否需要 Alt
  handler: (e: KeyboardEvent) => void // 处理函数
  preventDefault?: boolean // 是否阻止默认行为
}

/**
 * 全局快捷键监听
 * @param hotkeys 快捷键配置数组
 */
export function useHotkey(hotkeys: HotkeyConfig[]) {
  const handleKeydown = (e: KeyboardEvent) => {
    for (const config of hotkeys) {
      const ctrlMatch = config.ctrl ? e.ctrlKey || e.metaKey : true
      const shiftMatch = config.shift ? e.shiftKey : !e.shiftKey
      const altMatch = config.alt ? e.altKey : !e.altKey
      const keyMatch = e.key.toLowerCase() === config.key.toLowerCase()

      if (ctrlMatch && shiftMatch && altMatch && keyMatch) {
        if (config.preventDefault !== false) {
          e.preventDefault()
        }
        config.handler(e)
        break
      }
    }
  }

  onMounted(() => {
    window.addEventListener('keydown', handleKeydown)
  })

  onUnmounted(() => {
    window.removeEventListener('keydown', handleKeydown)
  })
}

/**
 * 常用快捷键预设
 */
export const HotkeyPresets = {
  // 全局搜索
  globalSearch: (handler: () => void): HotkeyConfig => ({
    key: 'k',
    ctrl: true,
    handler,
  }),
  // 刷新当前页
  refresh: (handler: () => void): HotkeyConfig => ({
    key: 'r',
    ctrl: true,
    handler,
  }),
  // 关闭当前标签页
  closeTab: (handler: () => void): HotkeyConfig => ({
    key: 'w',
    ctrl: true,
    handler,
  }),
  // 保存
  save: (handler: () => void): HotkeyConfig => ({
    key: 's',
    ctrl: true,
    handler,
  }),
  // 新建
  create: (handler: () => void): HotkeyConfig => ({
    key: 'n',
    ctrl: true,
    handler,
  }),
  // 查找
  find: (handler: () => void): HotkeyConfig => ({
    key: 'f',
    ctrl: true,
    handler,
  }),
}
