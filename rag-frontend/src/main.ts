import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Antd from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'

import App from './App.vue'
import router from './router'
import './styles/index.scss'

// 导入 highlight.js 代码高亮样式（使用暗色主题，适配聊天界面代码块）
import 'highlight.js/styles/atom-one-dark.css'

// 创建Vue应用实例
const app = createApp(App)

// 创建Pinia实例
const pinia = createPinia()

// 注册Pinia状态管理
app.use(pinia)

// 注册路由
app.use(router)

// 注册Ant Design Vue
app.use(Antd)

// 应用初始化
async function initializeApp(): Promise<void> {
  // 初始化深色模式
  const { useAppStore } = await import('@/stores/app')
  const appStore = useAppStore()
  appStore.initDarkMode()

  // 挂载应用
  app.mount('#app')
}

// 启动应用
initializeApp().catch((error) => {
  console.error('应用初始化失败:', error)
})
