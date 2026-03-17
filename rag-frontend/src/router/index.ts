import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import type { RouteMeta } from '@/types'

// 路由配置
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: {
      title: '登录',
      hidden: true,
    } as RouteMeta,
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/register/index.vue'),
    meta: {
      title: '注册',
      hidden: true,
    } as RouteMeta,
  },
  {
    path: '/redirect',
    name: 'Redirect',
    component: {
      template: '<div></div>',
      beforeRouteEnter(to, from, next) {
        next((vm) => {
          // 重定向到目标路由，用于刷新页面
          const path = to.path.replace('/redirect', '')
          vm.$router.replace(path)
        })
      },
    },
    meta: {
      hidden: true,
    } as RouteMeta,
  },
  {
    path: '/',
    name: 'Layout',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: {
          title: '首页',
          icon: 'Odometer',
          requiresAuth: true,
        } as RouteMeta,
      },
      {
        path: 'knowledge',
        name: 'Knowledge',
        component: () => import('@/views/knowledge/index.vue'),
        meta: {
          title: '知识库',
          icon: 'Folder',
          requiresAuth: true,
        } as RouteMeta,
      },
      {
        path: 'document',
        name: 'Document',
        component: () => import('@/views/document/index.vue'),
        meta: {
          title: '文档管理',
          icon: 'Document',
          requiresAuth: true,
          hidden: true, // 不在侧边栏显示
        } as RouteMeta,
      },
      {
        path: 'chat',
        name: 'Chat',
        component: () => import('@/views/chat/index.vue'),
        meta: {
          title: '智能问答',
          icon: 'ChatDotRound',
          requiresAuth: true,
        } as RouteMeta,
      },
      {
        path: 'user',
        name: 'User',
        component: () => import('@/views/user/index.vue'),
        meta: {
          title: '用户管理',
          icon: 'User',
          requiresAuth: true,
        } as RouteMeta,
      },
      {
        path: 'sensitive',
        name: 'Sensitive',
        component: () => import('@/views/sensitive/index.vue'),
        meta: {
          title: '敏感词管理',
          icon: 'Warning',
          requiresAuth: true,
        } as RouteMeta,
      },
      {
        path: 'log',
        name: 'Log',
        component: () => import('@/views/log/index.vue'),
        meta: {
          title: '日志管理',
          icon: 'Document',
          requiresAuth: true,
        } as RouteMeta,
      },
      {
        path: 'hotwords',
        name: 'Hotwords',
        component: () => import('@/views/hotwords/index.vue'),
        meta: {
          title: '热点词分析',
          icon: 'TrendCharts',
          requiresAuth: true,
        } as RouteMeta,
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/views/settings/index.vue'),
        meta: {
          title: '系统设置',
          icon: 'Setting',
          requiresAuth: true,
        } as RouteMeta,
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/profile/index.vue'),
        meta: {
          title: '个人中心',
          icon: 'User',
          requiresAuth: true,
          hidden: true, // 不在侧边栏显示
        } as RouteMeta,
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: {
      title: '页面不存在',
      hidden: true,
    } as RouteMeta,
  },
]

// 创建路由实例
const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

// 白名单路由 - 不需要登录即可访问
const whiteList = ['/login', '/register', '/404']

// 路由守卫 - 检查登录状态
router.beforeEach(async (to, from, next) => {
  // 设置页面标题
  const title = (to.meta as RouteMeta).title
  document.title = title ? `${title} - ${import.meta.env.VITE_APP_TITLE}` : import.meta.env.VITE_APP_TITLE

  // 动态导入 userStore 以避免循环依赖
  const { useUserStore } = await import('@/stores/user')
  const userStore = useUserStore()

  // 判断是否有 token
  const hasToken = !!userStore.token

  if (hasToken) {
    // 已登录状态
    if (to.path === '/login' || to.path === '/register') {
      // 已登录时访问登录/注册页，重定向到首页
      next({ path: '/' })
      return
    }

    // 检查是否已获取用户信息
    if (!userStore.userInfo) {
      try {
        // 尝试获取用户信息
        const success = await userStore.restoreLoginState()
        if (!success) {
          // Token 无效，清除登录状态并跳转登录页
          await userStore.clearAuth()
          next({
            path: '/login',
            query: { redirect: to.fullPath },
          })
          return
        }
      } catch (error) {
        console.error('获取用户信息失败:', error)
        await userStore.clearAuth()
        next({
          path: '/login',
          query: { redirect: to.fullPath },
        })
        return
      }
    }

    next()
  } else {
    // 未登录状态
    if (whiteList.includes(to.path)) {
      // 在白名单中，直接进入
      next()
    } else {
      // 不在白名单中，跳转登录页
      next({
        path: '/login',
        query: { redirect: to.fullPath },
      })
    }
  }
})

export default router
