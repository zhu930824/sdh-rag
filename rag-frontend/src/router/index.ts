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
        meta: { title: '首页', icon: 'Odometer', requiresAuth: true } as RouteMeta,
      },
      {
        path: 'knowledge-base',
        name: 'KnowledgeBase',
        component: () => import('@/views/knowledge-base/index.vue'),
        meta: { title: '知识库', icon: 'Folder', requiresAuth: true } as RouteMeta,
      },
      {
        path: 'knowledge',
        name: 'Knowledge',
        component: () => import('@/views/knowledge/index.vue'),
        meta: { title: '文档管理', icon: 'Document', requiresAuth: true } as RouteMeta,
      },
      {
        path: 'document',
        name: 'Document',
        component: () => import('@/views/document/index.vue'),
        meta: { title: '文档详情', icon: 'Document', requiresAuth: true, hidden: true } as RouteMeta,
      },
      {
        path: 'chat',
        name: 'Chat',
        component: () => import('@/views/chat/index.vue'),
        meta: { title: '智能问答', icon: 'ChatDotRound', requiresAuth: true } as RouteMeta,
      },
      {
        path: 'user',
        name: 'User',
        component: () => import('@/views/user/index.vue'),
        meta: { title: '用户管理', icon: 'User', requiresAuth: true } as RouteMeta,
      },
      {
        path: 'role',
        name: 'Role',
        component: () => import('@/views/role/index.vue'),
        meta: { title: '角色管理', icon: 'UsergroupSwitch', requiresAuth: true } as RouteMeta,
      },
      {
        path: 'log',
        name: 'Log',
        component: () => import('@/views/log/index.vue'),
        meta: { title: '日志管理', icon: 'Document', requiresAuth: true } as RouteMeta,
      },
      {
        path: 'sensitive',
        name: 'Sensitive',
        component: () => import('@/views/sensitive/index.vue'),
        meta: { title: '敏感词管理', icon: 'Warning', requiresAuth: true } as RouteMeta,
      },
      {
        path: 'hotwords',
        name: 'Hotwords',
        component: () => import('@/views/hotwords/index.vue'),
        meta: { title: '热点词分析', icon: 'TrendCharts', requiresAuth: true } as RouteMeta,
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/views/settings/index.vue'),
        meta: { title: '系统设置', icon: 'Setting', requiresAuth: true } as RouteMeta,
      },
      {
        path: 'model',
        name: 'Model',
        component: () => import('@/views/model/index.vue'),
        meta: { title: '大模型管理', icon: 'Cpu', requiresAuth: true } as RouteMeta,
      },
      {
        path: 'workflow',
        name: 'WorkflowList',
        component: () => import('@/views/workflow-list/index.vue'),
        meta: { title: '工作流编排', icon: 'ShareAlt', requiresAuth: true } as RouteMeta,
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
        meta: { title: '自然语言查询', icon: 'Search', requiresAuth: true } as RouteMeta,
      },
      {
        path: 'graph',
        name: 'Graph',
        component: () => import('@/views/graph/index.vue'),
        meta: { title: '知识图谱', icon: 'Apartment', requiresAuth: true } as RouteMeta,
      },
      {
        path: 'feedback',
        name: 'Feedback',
        component: () => import('@/views/feedback/index.vue'),
        meta: { title: '问答评价', icon: 'Like', requiresAuth: true } as RouteMeta,
      },
      {
        path: 'stats',
        name: 'Stats',
        component: () => import('@/views/stats/index.vue'),
        meta: { title: '数据统计', icon: 'BarChart', requiresAuth: true } as RouteMeta,
      },
      {
        path: 'announcement',
        name: 'Announcement',
        component: () => import('@/views/announcement/index.vue'),
        meta: { title: '公告管理', icon: 'Notification', requiresAuth: true } as RouteMeta,
      },
      {
        path: 'approval',
        name: 'Approval',
        component: () => import('@/views/approval/index.vue'),
        meta: { title: '审核中心', icon: 'Audit', requiresAuth: true } as RouteMeta,
      },
      {
        path: 'process-task',
        name: 'ProcessTask',
        component: () => import('@/views/process-task/index.vue'),
        meta: { title: '文档预处理', icon: 'CloudUpload', requiresAuth: true } as RouteMeta,
      },
      {
        path: 'tag',
        name: 'Tag',
        component: () => import('@/views/tag/index.vue'),
        meta: { title: '标签管理', icon: 'Tags', requiresAuth: true } as RouteMeta,
      },
      {
        path: 'voice',
        name: 'Voice',
        component: () => import('@/views/voice/index.vue'),
        meta: { title: '语音问答', icon: 'Microphone', requiresAuth: true } as RouteMeta,
      },
      {
        path: 'prompt',
        name: 'Prompt',
        component: () => import('@/views/prompt/index.vue'),
        meta: { title: 'Prompt模板', icon: 'Edit', requiresAuth: true } as RouteMeta,
      },
      {
        path: 'session',
        name: 'Session',
        component: () => import('@/views/session/index.vue'),
        meta: { title: '会话管理', icon: 'Message', requiresAuth: true } as RouteMeta,
      },
      {
        path: 'scheduled-task',
        name: 'ScheduledTask',
        component: () => import('@/views/scheduled-task/index.vue'),
        meta: { title: '定时任务', icon: 'ClockCircle', requiresAuth: true } as RouteMeta,
      },
      {
        path: 'webhook',
        name: 'Webhook',
        component: () => import('@/views/webhook/index.vue'),
        meta: { title: 'Webhook管理', icon: 'Api', requiresAuth: true } as RouteMeta,
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/profile/index.vue'),
        meta: { title: '个人中心', icon: 'User', requiresAuth: true, hidden: true } as RouteMeta,
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
    next()
  } else {
    if (whiteList.includes(to.path)) {
      next()
    } else {
      next({ path: '/login', query: { redirect: to.fullPath } })
    }
  }
})

export default router
