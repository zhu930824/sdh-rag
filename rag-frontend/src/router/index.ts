import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import type { RouteMeta } from '@/types'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', hidden: true } as RouteMeta,
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/register/index.vue'),
    meta: { title: '注册', hidden: true } as RouteMeta,
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
        meta: { title: '首页', icon: 'Odometer', requiresAuth: true, permission: '/dashboard' } as RouteMeta,
      },
      {
        path: 'knowledge',
        name: 'KnowledgeBase',
        component: () => import('@/views/knowledge-base/index.vue'),
        meta: { title: '知识库', icon: 'Folder', requiresAuth: true, permission: '/knowledge' } as RouteMeta,
      },
      {
        path: 'knowledge/:id',
        name: 'KnowledgeBaseDetail',
        component: () => import('@/views/knowledge-base/detail.vue'),
        meta: { title: '知识库详情', icon: 'Folder', requiresAuth: true, hidden: true } as RouteMeta,
      },
      {
        path: 'document',
        name: 'Document',
        component: () => import('@/views/document/index.vue'),
        meta: { title: '文档管理', icon: 'Document', requiresAuth: true, permission: '/knowledge' } as RouteMeta,
      },
      {
        path: 'chat',
        name: 'Chat',
        component: () => import('@/views/chat/index.vue'),
        meta: { title: '智能问答', icon: 'ChatDotRound', requiresAuth: true, permission: '/chat' } as RouteMeta,
      },
      {
        path: 'user',
        name: 'User',
        component: () => import('@/views/user/index.vue'),
        meta: { title: '用户管理', icon: 'User', requiresAuth: true, permission: '/user' } as RouteMeta,
      },
      {
        path: 'role',
        name: 'Role',
        component: () => import('@/views/role/index.vue'),
        meta: { title: '角色管理', icon: 'UsergroupSwitch', requiresAuth: true, permission: '/role' } as RouteMeta,
      },
      {
        path: 'log',
        name: 'Log',
        component: () => import('@/views/log/index.vue'),
        meta: { title: '日志管理', icon: 'Document', requiresAuth: true, permission: '/log' } as RouteMeta,
      },
      {
        path: 'sensitive',
        name: 'Sensitive',
        component: () => import('@/views/sensitive/index.vue'),
        meta: { title: '敏感词管理', icon: 'Warning', requiresAuth: true, permission: '/sensitive' } as RouteMeta,
      },
      {
        path: 'hotwords',
        name: 'Hotwords',
        component: () => import('@/views/hotwords/index.vue'),
        meta: { title: '热点词分析', icon: 'TrendCharts', requiresAuth: true, permission: '/hotwords' } as RouteMeta,
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/views/settings/index.vue'),
        meta: { title: '系统设置', icon: 'Setting', requiresAuth: true, permission: '/settings' } as RouteMeta,
      },
      {
        path: 'model',
        name: 'Model',
        component: () => import('@/views/model/index.vue'),
        meta: { title: '大模型管理', icon: 'Cpu', requiresAuth: true, permission: '/model' } as RouteMeta,
      },
      {
        path: 'workflow',
        name: 'WorkflowList',
        component: () => import('@/views/workflow-list/index.vue'),
        meta: { title: '工作流编排', icon: 'ShareAlt', requiresAuth: true, permission: '/workflow' } as RouteMeta,
      },
      {
        path: 'workflow/create',
        name: 'WorkflowCreate',
        component: () => import('@/views/workflow/index.vue'),
        meta: { title: '新建工作流', icon: 'ShareAlt', requiresAuth: true, hidden: true } as RouteMeta,
      },
      {
        path: 'workflow/:id',
        name: 'WorkflowEdit',
        component: () => import('@/views/workflow/index.vue'),
        meta: { title: '编辑工作流', icon: 'ShareAlt', requiresAuth: true, hidden: true } as RouteMeta,
      },
      {
        path: 'nlp-query',
        name: 'NlpQuery',
        component: () => import('@/views/nlp-query/index.vue'),
        meta: { title: '自然语言查询', icon: 'Search', requiresAuth: true, permission: '/nlp-query' } as RouteMeta,
      },
      {
        path: 'graph',
        name: 'Graph',
        component: () => import('@/views/graph/index.vue'),
        meta: { title: '知识图谱', icon: 'Apartment', requiresAuth: true, permission: '/graph' } as RouteMeta,
      },
      {
        path: 'feedback',
        name: 'Feedback',
        component: () => import('@/views/feedback/index.vue'),
        meta: { title: '问答评价', icon: 'Like', requiresAuth: true, permission: '/feedback' } as RouteMeta,
      },
      {
        path: 'stats',
        name: 'Stats',
        component: () => import('@/views/stats/index.vue'),
        meta: { title: '数据统计', icon: 'BarChart', requiresAuth: true, permission: '/stats' } as RouteMeta,
      },
      {
        path: 'announcement',
        name: 'Announcement',
        component: () => import('@/views/announcement/index.vue'),
        meta: { title: '公告管理', icon: 'Notification', requiresAuth: true, permission: '/announcement' } as RouteMeta,
      },
      {
        path: 'tag',
        name: 'Tag',
        component: () => import('@/views/tag/index.vue'),
        meta: { title: '标签管理', icon: 'Tags', requiresAuth: true, permission: '/tag' } as RouteMeta,
      },
      {
        path: 'voice',
        name: 'Voice',
        component: () => import('@/views/voice/index.vue'),
        meta: { title: '语音问答', icon: 'Microphone', requiresAuth: true, permission: '/voice' } as RouteMeta,
      },
      {
        path: 'prompt',
        name: 'Prompt',
        component: () => import('@/views/prompt/index.vue'),
        meta: { title: 'Prompt模板', icon: 'Edit', requiresAuth: true, permission: '/prompt' } as RouteMeta,
      },
      {
        path: 'session',
        name: 'Session',
        component: () => import('@/views/session/index.vue'),
        meta: { title: '会话管理', icon: 'Message', requiresAuth: true, permission: '/session' } as RouteMeta,
      },
      {
        path: 'scheduled-task',
        name: 'ScheduledTask',
        component: () => import('@/views/scheduled-task/index.vue'),
        meta: { title: '定时任务', icon: 'ClockCircle', requiresAuth: true, permission: '/scheduled-task' } as RouteMeta,
      },
      {
        path: 'webhook',
        name: 'Webhook',
        component: () => import('@/views/webhook/index.vue'),
        meta: { title: 'Webhook管理', icon: 'Api', requiresAuth: true, permission: '/webhook' } as RouteMeta,
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/profile/index.vue'),
        meta: { title: '个人中心', icon: 'User', requiresAuth: true, hidden: true } as RouteMeta,
      },
      {
        path: 'evaluation',
        name: 'Evaluation',
        component: () => import('@/views/evaluation/index.vue'),
        meta: { title: '检索评估', icon: 'DataAnalysis', requiresAuth: true, permission: '/knowledge' } as RouteMeta,
      },
      {
        path: 'memory',
        name: 'Memory',
        component: () => import('@/views/memory/index.vue'),
        meta: { title: '记忆中心', icon: 'Bulb', requiresAuth: true, permission: '/memory' } as RouteMeta,
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '页面不存在', hidden: true } as RouteMeta,
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

const whiteList = ['/login', '/register', '/404']

router.beforeEach(async (to, from, next) => {
  const title = (to.meta as RouteMeta).title
  document.title = title ? `${title} - ${import.meta.env.VITE_APP_TITLE}` : import.meta.env.VITE_APP_TITLE

  const { useUserStore } = await import('@/stores/user')
  const userStore = useUserStore()
  const hasToken = !!userStore.token

  if (hasToken) {
    if (to.path === '/login' || to.path === '/register') {
      next({ path: '/' })
      return
    }
    if (!userStore.userInfo) {
      try {
        const success = await userStore.restoreLoginState()
        if (!success) {
          await userStore.clearAuth()
          next({ path: '/login', query: { redirect: to.fullPath } })
          return
        }
      } catch (error) {
        console.error('获取用户信息失败:', error)
        await userStore.clearAuth()
        next({ path: '/login', query: { redirect: to.fullPath } })
        return
      }
    }

    // 检查权限
    const permission = (to.meta as RouteMeta).permission
    if (permission && !checkPermission(userStore.permissions, permission)) {
      // 没有权限，尝试跳转到有权限的第一个菜单
      const firstPermittedRoute = getFirstPermittedRoute(userStore.permissions)
      if (firstPermittedRoute && firstPermittedRoute !== to.path) {
        next({ path: firstPermittedRoute })
        return
      }
      // 如果没有任何权限，放行让页面正常显示（可能是公共页面）
    }

    next()
  } else {
    if (whiteList.includes(to.path)) {
      next()
    } else {
      next({ path: '/login', query: { redirect: to.fullPath } })
    }
  }
})

/**
 * 检查用户是否有指定权限
 */
function checkPermission(userPermissions: string[], requiredPermission: string): boolean {
  if (!userPermissions || userPermissions.length === 0) {
    return false
  }
  return userPermissions.includes(requiredPermission)
}

/**
 * 获取用户有权限的第一个路由
 */
function getFirstPermittedRoute(userPermissions: string[]): string | null {
  if (!userPermissions || userPermissions.length === 0) {
    return null
  }
  // 返回第一个权限对应的路由
  return userPermissions[0]
}

export default router
