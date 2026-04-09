<template>
  <div class="dashboard">
    <!-- Hero Section -->
    <div class="hero-section">
      <div class="hero-bg">
        <div class="hero-orb hero-orb-1"></div>
        <div class="hero-orb hero-orb-2"></div>
        <div class="hero-orb hero-orb-3"></div>
        <div class="hero-grid"></div>
      </div>
      <div class="hero-content">
        <div class="hero-text">
          <div class="hero-badge">
            <StarOutlined />
            <span>AI 驱动的智能平台</span>
          </div>
          <h1 class="hero-title">
            {{ getGreeting() }}，<span class="gradient-text">{{ nickname || '用户' }}</span>
          </h1>
          <p class="hero-subtitle">探索知识图谱，开启智能问答新体验</p>
          <div class="hero-actions">
            <a-button type="primary" size="large" class="hero-btn" @click="router.push('/chat')">
              <MessageOutlined />
              开始对话
            </a-button>
            <a-button size="large" class="hero-btn-secondary" @click="router.push('/knowledge')">
              <FolderOutlined />
              知识库
            </a-button>
          </div>
        </div>
        <div class="hero-visual">
          <div class="visual-card visual-card-main">
            <div class="visual-header">
              <div class="visual-dots">
                <span></span><span></span><span></span>
              </div>
              <span class="visual-title">实时数据</span>
            </div>
            <div class="visual-content">
              <div class="visual-stat">
                <span class="visual-stat-value">{{ animatedStats.chatCount }}</span>
                <span class="visual-stat-label">今日问答</span>
              </div>
              <div class="visual-chart">
                <div class="chart-bar" v-for="(h, i) in chartData" :key="i" :style="{ height: h + '%' }"></div>
              </div>
            </div>
          </div>
          <div class="visual-card visual-card-float">
            <ThunderboltOutlined />
            <span>响应时间 <strong>{{ avgResponseTime }}s</strong></span>
          </div>
          <div class="visual-card visual-card-float-2">
            <SafetyCertificateOutlined />
            <span>准确率 <strong>{{ accuracyRate }}%</strong></span>
          </div>
        </div>
      </div>
    </div>

    <!-- Stats Cards -->
    <div class="stats-section">
      <div
        v-for="(stat, index) in statsData"
        :key="stat.label"
        class="stat-card animate-slide-up"
        :style="{ animationDelay: `${index * 0.1}s` }"
      >
        <div class="stat-header">
          <div class="stat-icon" :style="{ background: stat.gradient }">
            <component :is="stat.icon" />
          </div>
          <div class="stat-trend" :class="stat.trend > 0 ? 'up' : 'down'">
            <ArrowUpOutlined v-if="stat.trend > 0" />
            <ArrowDownOutlined v-else />
            <span>{{ Math.abs(stat.trend) }}%</span>
          </div>
        </div>
        <div class="stat-body">
          <div class="stat-value">
            <CountUp :end-val="stat.value" :duration="2" />
          </div>
          <div class="stat-label">{{ stat.label }}</div>
        </div>
        <div class="stat-sparkline">
          <svg viewBox="0 0 100 30" preserveAspectRatio="none">
            <path :d="stat.sparkline" fill="none" :stroke="stat.color" stroke-width="2" />
          </svg>
        </div>
      </div>
    </div>

    <!-- Feature Highlights -->
    <div class="section-card">
      <div class="section-header">
        <h2 class="section-title">
          <CompassOutlined />
          核心功能
        </h2>
        <span class="section-badge">FEATURES</span>
      </div>
      <div class="features-grid">
        <div
          v-for="(feature, index) in features"
          :key="feature.title"
          class="feature-card"
          :style="{ animationDelay: `${index * 0.1}s` }"
          @click="router.push(feature.route)"
        >
          <div class="feature-icon" :style="{ background: feature.gradient }">
            <component :is="feature.icon" />
          </div>
          <div class="feature-content">
            <h3 class="feature-title">{{ feature.title }}</h3>
            <p class="feature-desc">{{ feature.desc }}</p>
          </div>
          <div class="feature-arrow">
            <RightOutlined />
          </div>
        </div>
      </div>
    </div>

    <!-- Content Grid -->
    <div class="content-grid">
      <!-- Recent Activity -->
      <div class="section-card content-card">
        <div class="section-header">
          <h2 class="section-title">
            <ClockCircleOutlined />
            最近活动
          </h2>
          <a class="section-link" @click="router.push('/chat')">
            查看全部 <RightOutlined />
          </a>
        </div>
        <div class="activity-list">
          <div
            v-for="(activity, index) in recentActivities"
            :key="activity.id"
            class="activity-item"
            :style="{ animationDelay: `${index * 0.05}s` }"
          >
            <div class="activity-icon" :style="{ background: getActivityColor(activity.type) }">
              <MessageOutlined />
            </div>
            <div class="activity-content">
              <div class="activity-title">{{ activity.title }}</div>
              <div class="activity-meta">
                <span>{{ activity.time }}</span>
                <span class="activity-dot"></span>
                <span>{{ activity.type }}</span>
              </div>
            </div>
            <div class="activity-status" :class="activity.status">
              {{ activity.statusText }}
            </div>
          </div>
          <div v-if="recentActivities.length === 0" class="empty-state">
            <InboxOutlined />
            <span>暂无活动记录</span>
          </div>
        </div>
      </div>

      <!-- Announcements -->
      <div class="section-card content-card">
        <div class="section-header">
          <h2 class="section-title">
            <NotificationOutlined />
            系统公告
          </h2>
          <a class="section-link" @click="router.push('/announcement')">
            查看全部 <RightOutlined />
          </a>
        </div>
        <div class="announcement-list">
          <div
            v-for="(announcement, index) in announcements"
            :key="announcement.id"
            class="announcement-item"
            :style="{ animationDelay: `${index * 0.05}s` }"
            @click="showAnnouncementDetail(announcement)"
          >
            <div class="announcement-tag" :class="announcement.type">
              {{ getAnnouncementTypeText(announcement.type) }}
            </div>
            <div class="announcement-content">
              <div class="announcement-title">
                <span v-if="announcement.isTop" class="top-badge">置顶</span>
                {{ announcement.title }}
              </div>
              <div class="announcement-meta">
                <span>{{ announcement.publishTime }}</span>
              </div>
            </div>
            <RightOutlined class="announcement-arrow" />
          </div>
          <div v-if="announcements.length === 0" class="empty-state">
            <NotificationOutlined />
            <span>暂无公告</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Trust Badges -->
    <div class="trust-section">
      <div class="trust-badges">
        <div class="trust-badge">
          <SecurityScanOutlined />
          <span>数据安全加密</span>
        </div>
        <div class="trust-badge">
          <CloudServerOutlined />
          <span>云端高可用</span>
        </div>
        <div class="trust-badge">
          <RocketOutlined />
          <span>毫秒级响应</span>
        </div>
        <div class="trust-badge">
          <TeamOutlined />
          <span>企业级支持</span>
        </div>
      </div>
    </div>

    <!-- System Status -->
    <div class="section-card system-card">
      <div class="section-header">
        <h2 class="section-title">
          <DashboardOutlined />
          系统状态
        </h2>
      </div>
      <div class="system-grid">
        <div class="system-item">
          <div class="system-icon online">
            <CheckCircleOutlined />
          </div>
          <div class="system-info">
            <span class="system-label">API 服务</span>
            <span class="system-value status-online">运行正常</span>
          </div>
        </div>
        <div class="system-item">
          <div class="system-icon online">
            <CheckCircleOutlined />
          </div>
          <div class="system-info">
            <span class="system-label">向量数据库</span>
            <span class="system-value status-online">已连接</span>
          </div>
        </div>
        <div class="system-item">
          <div class="system-icon online">
            <CheckCircleOutlined />
          </div>
          <div class="system-info">
            <span class="system-label">AI 模型</span>
            <span class="system-value status-online">就绪</span>
          </div>
        </div>
        <div class="system-item">
          <div class="system-icon">
            <ClockCircleOutlined />
          </div>
          <div class="system-info">
            <span class="system-label">系统版本</span>
            <span class="system-value">v1.0.0</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, ref, watch, onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import {
  MessageOutlined,
  FolderOutlined,
  FileTextOutlined,
  UserOutlined,
  ApartmentOutlined,
  ClockCircleOutlined,
  RightOutlined,
  ArrowUpOutlined,
  ArrowDownOutlined,
  InboxOutlined,
  StarOutlined,
  CompassOutlined,
  SecurityScanOutlined,
  CloudServerOutlined,
  RocketOutlined,
  TeamOutlined,
  DashboardOutlined,
  CheckCircleOutlined,
  SafetyCertificateOutlined,
  DatabaseOutlined,
  NotificationOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons-vue'
import { getDashboardStats, getRecentActivities, type DashboardStats, type ActivityRecord } from '@/api/dashboard'
import { getActiveAnnouncements, type Announcement } from '@/api/announcement'

const router = useRouter()
const userStore = useUserStore()

const nickname = computed(() => userStore.nickname)

const CountUp = defineComponent({
  props: {
    endVal: { type: Number, default: 0 },
    duration: { type: Number, default: 2 },
  },
  setup(props) {
    const currentVal = ref(0)
    let startTime: number | null = null
    let animationFrame: number | null = null

    const animate = (timestamp: number) => {
      if (!startTime) startTime = timestamp
      const progress = Math.min((timestamp - startTime) / (props.duration * 1000), 1)
      currentVal.value = Math.floor(progress * props.endVal)

      if (progress < 1) {
        animationFrame = requestAnimationFrame(animate)
      }
    }

    watch(() => props.endVal, () => {
      startTime = null
      if (animationFrame) {
        cancelAnimationFrame(animationFrame)
      }
      animationFrame = requestAnimationFrame(animate)
    }, { immediate: true })

    return () => h('span', currentVal.value.toLocaleString())
  },
})

function getGreeting(): string {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 9) return '早上好'
  if (hour < 12) return '上午好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  if (hour < 22) return '晚上好'
  return '夜深了'
}

const statsLoading = ref(false)
const dashboardStats = reactive<Partial<DashboardStats>>({
  knowledgeCount: 0,
  documentCount: 0,
  chatCount: 0,
  userCount: 0,
  todayChatCount: 0,
  avgResponseTime: 0.3,
  accuracyRate: 98.5,
  hourlyStats: [],
})

const animatedStats = reactive({
  chatCount: 0,
})

const chartData = ref([40, 65, 45, 80, 55, 70, 60, 85, 50, 75, 65, 90])
const avgResponseTime = ref('0.3')
const accuracyRate = ref('98.5')

const statsData = computed(() => [
  {
    label: '知识库',
    value: dashboardStats.knowledgeCount,
    icon: FolderOutlined,
    color: '#6366F1',
    gradient: 'linear-gradient(135deg, #6366F1 0%, #8B5CF6 100%)',
    trend: 0, // 知识库暂无同比数据
    sparkline: 'M0,25 L10,20 L20,22 L30,15 L40,18 L50,10 L60,12 L70,8 L80,5 L90,7 L100,3',
  },
  {
    label: '文档数量',
    value: dashboardStats.documentCount,
    icon: FileTextOutlined,
    color: '#10B981',
    gradient: 'linear-gradient(135deg, #10B981 0%, #34D399 100%)',
    trend: dashboardStats.documentTrend || 0,
    sparkline: 'M0,20 L10,18 L20,22 L30,15 L40,17 L50,12 L60,14 L70,10 L80,8 L90,12 L100,5',
  },
  {
    label: '问答次数',
    value: dashboardStats.chatCount,
    icon: MessageOutlined,
    color: '#F59E0B',
    gradient: 'linear-gradient(135deg, #F59E0B 0%, #FBBF24 100%)',
    trend: dashboardStats.chatTrend || 0,
    sparkline: 'M0,15 L10,18 L20,12 L30,20 L40,15 L50,18 L60,12 L70,15 L80,10 L90,14 L100,8',
  },
  {
    label: '用户数量',
    value: dashboardStats.userCount,
    icon: UserOutlined,
    color: '#EC4899',
    gradient: 'linear-gradient(135deg, #EC4899 0%, #F472B6 100%)',
    trend: dashboardStats.userTrend || 0,
    sparkline: 'M0,22 L10,20 L20,18 L30,15 L40,17 L50,12 L60,10 L70,8 L80,6 L90,8 L100,4',
  },
])

const features = [
  {
    title: '智能问答',
    desc: '基于 RAG 的精准问答系统',
    icon: MessageOutlined,
    gradient: 'linear-gradient(135deg, #6366F1 0%, #8B5CF6 100%)',
    route: '/chat',
  },
  {
    title: '知识图谱',
    desc: '可视化展示知识关联关系',
    icon: ApartmentOutlined,
    gradient: 'linear-gradient(135deg, #10B981 0%, #34D399 100%)',
    route: '/graph',
  },
  {
    title: '文档管理',
    desc: '智能解析与向量存储',
    icon: FileTextOutlined,
    gradient: 'linear-gradient(135deg, #F59E0B 0%, #FBBF24 100%)',
    route: '/knowledge',
  },
  {
    title: '工作流编排',
    desc: '可视化流程设计',
    icon: CompassOutlined,
    gradient: 'linear-gradient(135deg, #EC4899 0%, #F472B6 100%)',
    route: '/workflow',
  },
]

const announcements = ref<Announcement[]>([])

const recentActivities = ref<ActivityRecord[]>([])

async function loadStats() {
  statsLoading.value = true
  try {
    const res = await getDashboardStats()
    const data = res.data
    if (data) {
      dashboardStats.knowledgeCount = data.knowledgeCount || 0
      dashboardStats.documentCount = data.documentCount || 0
      dashboardStats.chatCount = data.chatCount || 0
      dashboardStats.userCount = data.userCount || 0
      dashboardStats.todayChatCount = data.todayChatCount || 0
      dashboardStats.avgResponseTime = data.avgResponseTime || 0.3
      dashboardStats.accuracyRate = data.accuracyRate || 98.5
      dashboardStats.hourlyStats = data.hourlyStats || []

      // Animate today chat count
      animatedStats.chatCount = data.todayChatCount || 0

      // Update chart data from hourly stats
      if (data.hourlyStats && data.hourlyStats.length > 0) {
        const maxCount = Math.max(...data.hourlyStats.map((h: any) => h.count), 1)
        chartData.value = data.hourlyStats.map((h: any) => (h.count / maxCount) * 100)
      }

      // Update response time and accuracy
      avgResponseTime.value = (data.avgResponseTime || 0.3).toFixed(1)
      accuracyRate.value = (data.accuracyRate || 98.5).toFixed(1)
    }
  } catch (error) {
    console.error('加载统计数据失败:', error)
  } finally {
    statsLoading.value = false
  }
}

async function loadRecentActivities() {
  try {
    const res = await getRecentActivities()
    if (res.data) {
      recentActivities.value = res.data
    }
  } catch (error) {
    console.error('加载最近活动失败:', error)
  }
}

async function loadAnnouncements() {
  try {
    const res = await getActiveAnnouncements()
    if (res.data) {
      announcements.value = res.data.slice(0, 5)
    }
  } catch (error) {
    console.error('加载公告失败:', error)
  }
}

function getAnnouncementTypeText(type: string): string {
  const map: Record<string, string> = {
    system: '系统',
    update: '更新',
    notice: '通知',
    maintenance: '维护',
  }
  return map[type] || '公告'
}

function getActivityColor(type: string): string {
  const map: Record<string, string> = {
    '问答': 'linear-gradient(135deg, #6366F1 0%, #8B5CF6 100%)',
    '文档': 'linear-gradient(135deg, #10B981 0%, #34D399 100%)',
    '知识库': 'linear-gradient(135deg, #F59E0B 0%, #FBBF24 100%)',
    '图谱': 'linear-gradient(135deg, #EC4899 0%, #F472B6 100%)',
  }
  return map[type] || 'linear-gradient(135deg, #6366F1 0%, #8B5CF6 100%)'
}

function showAnnouncementDetail(announcement: Announcement) {
  // 可以跳转到公告详情或弹窗显示
  console.log('查看公告:', announcement)
}

onMounted(() => {
  loadStats()
  loadAnnouncements()
  loadRecentActivities()
})
</script>

<style scoped lang="scss">
.dashboard {
  height: calc(100vh - 64px - 48px);
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: var(--card-gap);
}

// ============================================
// Hero Section
// ============================================
.hero-section {
  position: relative;
  background: linear-gradient(135deg, #059669 0%, #10B981 50%, #34D399 100%);
  border-radius: var(--radius-2xl);
  padding: 40px 48px;
  overflow: hidden;
  min-height: 280px;

  html.dark & {
    background: linear-gradient(135deg, #064E3B 0%, #065F46 50%, #047857 100%);
  }
}

.hero-bg {
  position: absolute;
  inset: 0;
  overflow: hidden;

  .hero-orb {
    position: absolute;
    border-radius: 50%;
    filter: blur(60px);
    opacity: 0.35;

    &.hero-orb-1 {
      width: 300px;
      height: 300px;
      background: #6EE7B7;
      top: -100px;
      right: 10%;
      animation: float 8s ease-in-out infinite;
    }

    &.hero-orb-2 {
      width: 200px;
      height: 200px;
      background: #A7F3D0;
      bottom: -50px;
      left: 20%;
      animation: float 6s ease-in-out infinite reverse;
    }

    &.hero-orb-3 {
      width: 150px;
      height: 150px;
      background: #D1FAE5;
      top: 30%;
      right: 30%;
      animation: float 10s ease-in-out infinite;
    }
  }

  .hero-grid {
    position: absolute;
    inset: 0;
    background-image:
      linear-gradient(rgba(255,255,255,0.05) 1px, transparent 1px),
      linear-gradient(90deg, rgba(255,255,255,0.05) 1px, transparent 1px);
    background-size: 40px 40px;
  }
}

.hero-content {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 40px;
}

.hero-text {
  flex: 1;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: rgba(255, 255, 255, 0.15);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: var(--radius-full);
  color: rgba(255, 255, 255, 0.95);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  margin-bottom: 20px;
  backdrop-filter: blur(8px);

  .anticon {
    color: #FEF08A;
  }
}

.hero-title {
  font-family: var(--font-display);
  font-size: 36px;
  font-weight: var(--font-weight-bold);
  color: #fff;
  margin-bottom: 12px;
  line-height: 1.2;
}

.gradient-text {
  background: linear-gradient(135deg, #FFFFFF 0%, #D1FAE5 50%, #A7F3D0 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.hero-subtitle {
  font-size: 16px;
  color: rgba(255, 255, 255, 0.8);
  margin-bottom: 28px;
}

.hero-actions {
  display: flex;
  gap: 12px;
}

.hero-btn {
  height: 44px;
  padding: 0 24px;
  font-weight: var(--font-weight-semibold);
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  gap: 8px;

  &.ant-btn-primary {
    background: #fff;
    border: none;
    color: #059669;
    box-shadow: 0 4px 14px rgba(0, 0, 0, 0.1);

    &:hover {
      background: #ECFDF5;
      transform: translateY(-2px);
      box-shadow: 0 6px 20px rgba(0, 0, 0, 0.15);
    }
  }
}

.hero-btn-secondary {
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  color: #fff;

  &:hover {
    background: rgba(255, 255, 255, 0.15);
    border-color: rgba(255, 255, 255, 0.3);
    color: #fff;
  }
}

// Hero Visual
.hero-visual {
  position: relative;
  width: 320px;
  height: 220px;
}

.visual-card {
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: var(--radius-xl);

  &.visual-card-main {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 40px;
    padding: 16px;
  }

  &.visual-card-float,
  &.visual-card-float-2 {
    position: absolute;
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 12px 16px;
    font-size: var(--font-size-sm);
    color: rgba(255, 255, 255, 0.9);
    animation: float 4s ease-in-out infinite;

    .anticon {
      font-size: 18px;
      color: #6EE7B7;
    }

    strong {
      color: #fff;
      font-weight: var(--font-weight-semibold);
    }
  }

  &.visual-card-float {
    bottom: 0;
    left: 0;
    animation-delay: 0s;
  }

  &.visual-card-float-2 {
    bottom: 10px;
    right: 0;
    animation-delay: 1s;
  }
}

.visual-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.visual-dots {
  display: flex;
  gap: 6px;

  span {
    width: 10px;
    height: 10px;
    border-radius: 50%;

    &:nth-child(1) { background: #EF4444; }
    &:nth-child(2) { background: #FBBF24; }
    &:nth-child(3) { background: #10B981; }
  }
}

.visual-title {
  font-size: var(--font-size-sm);
  color: rgba(255, 255, 255, 0.6);
}

.visual-content {
  display: flex;
  gap: 20px;
}

.visual-stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.visual-stat-value {
  font-family: var(--font-display);
  font-size: 32px;
  font-weight: var(--font-weight-bold);
  color: #fff;
  line-height: 1;
}

.visual-stat-label {
  font-size: var(--font-size-sm);
  color: rgba(255, 255, 255, 0.5);
}

.visual-chart {
  flex: 1;
  display: flex;
  align-items: flex-end;
  gap: 4px;
  padding-bottom: 4px;
}

.chart-bar {
  flex: 1;
  background: linear-gradient(180deg, rgba(110, 231, 183, 0.8) 0%, rgba(167, 243, 208, 0.3) 100%);
  border-radius: 2px 2px 0 0;
  min-height: 4px;
  transition: height 0.5s ease;
}

// ============================================
// Stats Section
// ============================================
.stats-section {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--card-gap);
}

.stat-card {
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-xl);
  padding: 24px;
  position: relative;
  overflow: hidden;
  transition: all var(--duration-normal) var(--ease-default);

  &:hover {
    transform: translateY(-4px);
    box-shadow: var(--shadow-card-hover);
  }
}

.stat-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

.stat-icon {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-lg);
  color: #fff;
  font-size: 22px;
}

.stat-trend {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  padding: 4px 10px;
  border-radius: var(--radius-full);

  &.up {
    color: var(--success-color);
    background: var(--success-light);
  }

  &.down {
    color: var(--danger-color);
    background: var(--danger-light);
  }
}

.stat-body {
  position: relative;
  z-index: 1;
}

.stat-value {
  font-family: var(--font-display);
  font-size: 32px;
  font-weight: var(--font-weight-bold);
  color: var(--text-primary);
  line-height: 1;
  margin-bottom: 4px;
}

.stat-label {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
}

.stat-sparkline {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 40px;
  opacity: 0.3;

  svg {
    width: 100%;
    height: 100%;
  }
}

// ============================================
// Section Card
// ============================================
.section-card {
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-xl);
  padding: 24px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.section-title {
  font-family: var(--font-display);
  font-size: 18px;
  font-weight: var(--font-weight-semibold);
  color: var(--text-primary);
  display: flex;
  align-items: center;
  gap: 10px;

  :deep(.anticon) {
    color: var(--primary-color);
  }
}

.section-badge {
  font-size: 11px;
  font-weight: var(--font-weight-bold);
  color: var(--primary-color);
  background: var(--primary-lighter);
  padding: 4px 12px;
  border-radius: var(--radius-full);
  letter-spacing: 0.05em;
}

.section-link {
  font-size: var(--font-size-sm);
  color: var(--primary-color);
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
  font-weight: var(--font-weight-medium);
  transition: opacity var(--duration-fast);

  &:hover {
    opacity: 0.8;
  }
}

// ============================================
// Features Grid
// ============================================
.features-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--card-gap);
}

.feature-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 16px;
  padding: 24px;
  border-radius: var(--radius-xl);
  cursor: pointer;
  transition: all var(--duration-normal) var(--ease-default);
  border: 1px solid transparent;

  &:hover {
    background: var(--bg-surface-secondary);
    border-color: var(--border-color);

    .feature-arrow {
      opacity: 1;
      transform: translateX(0);
    }
  }
}

.feature-icon {
  width: 52px;
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-lg);
  color: #fff;
  font-size: 24px;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.25);
}

.feature-content {
  flex: 1;
}

.feature-title {
  font-family: var(--font-display);
  font-size: 16px;
  font-weight: var(--font-weight-semibold);
  color: var(--text-primary);
  margin-bottom: 4px;
}

.feature-desc {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  line-height: var(--line-height-relaxed);
}

.feature-arrow {
  opacity: 0;
  transform: translateX(-8px);
  transition: all var(--duration-normal) var(--ease-default);
  color: var(--primary-color);
  font-size: 16px;
}

// ============================================
// Content Grid
// ============================================
.content-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--card-gap);
}

.content-card {
  display: flex;
  flex-direction: column;
  min-height: 320px;
}

// ============================================
// Activity List
// ============================================
.activity-list {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.activity-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 0;
  border-bottom: 1px solid var(--border-light);
  transition: background var(--duration-fast);

  &:last-child {
    border-bottom: none;
  }

  &:hover {
    .activity-title {
      color: var(--primary-color);
    }
  }
}

.activity-icon {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-lg);
  color: #fff;
  font-size: 18px;
  flex-shrink: 0;
}

.activity-content {
  flex: 1;
  min-width: 0;
}

.activity-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
  color: var(--text-primary);
  transition: color var(--duration-fast);
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.activity-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--font-size-sm);
  color: var(--text-tertiary);
}

.activity-dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--text-quaternary);
}

.activity-status {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  padding: 4px 12px;
  border-radius: var(--radius-full);

  &.success {
    color: var(--success-color);
    background: var(--success-light);
  }

  &.processing {
    color: var(--warning-color);
    background: var(--warning-light);
  }
}

// ============================================
// Announcement List
// ============================================
.announcement-list {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.announcement-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 0;
  border-bottom: 1px solid var(--border-light);
  cursor: pointer;
  transition: background var(--duration-fast);

  &:last-child {
    border-bottom: none;
  }

  &:hover {
    .announcement-title {
      color: var(--primary-color);
    }

    .announcement-arrow {
      opacity: 1;
      transform: translateX(4px);
    }
  }
}

.announcement-tag {
  padding: 4px 10px;
  border-radius: var(--radius-full);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  flex-shrink: 0;

  &.system {
    background: var(--primary-lighter);
    color: var(--primary-color);
  }

  &.update {
    background: var(--success-light);
    color: var(--success-color);
  }

  &.notice {
    background: var(--warning-light);
    color: var(--warning-color);
  }

  &.maintenance {
    background: var(--danger-light);
    color: var(--danger-color);
  }
}

.announcement-content {
  flex: 1;
  min-width: 0;
}

.announcement-title {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
  color: var(--text-primary);
  transition: color var(--duration-fast);
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: flex;
  align-items: center;
  gap: 8px;

  .top-badge {
    background: #EF4444;
    color: #fff;
    font-size: 10px;
    padding: 2px 6px;
    border-radius: var(--radius-full);
    font-weight: var(--font-weight-medium);
  }
}

.announcement-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--font-size-sm);
  color: var(--text-tertiary);
}

.announcement-arrow {
  opacity: 0;
  transform: translateX(0);
  transition: all var(--duration-normal) var(--ease-default);
  color: var(--primary-color);
  font-size: 14px;
  flex-shrink: 0;
}

// ============================================
// Empty State
// ============================================
.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: var(--text-tertiary);
  padding: 40px 0;

  :deep(.anticon) {
    font-size: 48px;
    opacity: 0.5;
  }

  span {
    font-size: var(--font-size-base);
  }
}

// ============================================
// Trust Section
// ============================================
.trust-section {
  padding: 8px 0;
}

.trust-badges {
  display: flex;
  justify-content: center;
  gap: 16px;
  flex-wrap: wrap;
}

.trust-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-full);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--text-secondary);
  transition: all var(--duration-normal) var(--ease-default);

  &:hover {
    border-color: var(--primary-color);
    color: var(--primary-color);

    .anticon {
      color: var(--primary-color);
    }
  }

  .anticon {
    font-size: 16px;
    color: var(--text-tertiary);
  }
}

// ============================================
// System Card
// ============================================
.system-card {
  background: var(--bg-surface);
}

.system-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

.system-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  background: var(--bg-surface-secondary);
  border-radius: var(--radius-xl);
  transition: all var(--duration-normal) var(--ease-default);

  &:hover {
    background: var(--bg-surface-tertiary);
  }
}

.system-icon {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-lg);
  background: var(--bg-surface);
  color: var(--text-tertiary);
  font-size: 18px;

  &.online {
    background: var(--success-light);
    color: var(--success-color);
  }
}

.system-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.system-label {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
}

.system-value {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
  color: var(--text-primary);

  &.status-online {
    color: var(--success-color);
  }
}

// ============================================
// Animations
// ============================================
@keyframes float {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-10px);
  }
}

.animate-slide-up {
  animation: slideInUp 0.4s var(--ease-out) both;
}

// ============================================
// Responsive
// ============================================
@media (max-width: 1400px) {
  .features-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 1200px) {
  .stats-section {
    grid-template-columns: repeat(2, 1fr);
  }

  .system-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .hero-section {
    padding: 32px 24px;
    min-height: auto;
  }

  .hero-content {
    flex-direction: column;
    text-align: center;
  }

  .hero-title {
    font-size: 28px;
  }

  .hero-actions {
    justify-content: center;
  }

  .hero-visual {
    display: none;
  }

  .stats-section {
    grid-template-columns: 1fr;
  }

  .features-grid {
    grid-template-columns: 1fr;
  }

  .content-grid {
    grid-template-columns: 1fr;
  }

  .system-grid {
    grid-template-columns: 1fr;
  }

  .trust-badges {
    flex-direction: column;
    align-items: center;
  }
}
</style>
