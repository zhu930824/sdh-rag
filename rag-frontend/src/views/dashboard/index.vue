<template>
  <div class="dashboard">
    <!-- Welcome Banner - Nature Style -->
    <div class="welcome-banner">
      <div class="banner-bg">
        <div class="leaf leaf-1"></div>
        <div class="leaf leaf-2"></div>
        <div class="leaf leaf-3"></div>
      </div>
      <div class="banner-content">
        <div class="welcome-text">
          <h1 class="welcome-title">
            {{ getGreeting() }}，<span class="username">{{ nickname || '用户' }}</span>
          </h1>
          <p class="welcome-subtitle">欢迎使用智能知识库，开启您的高效工作之旅</p>
          <div class="welcome-tags">
            <span class="tag" v-for="(badge, index) in welcomeBadges" :key="index">
              <FileTextOutlined v-if="badge.icon === FileTextOutlined" />
              <CommentOutlined v-else-if="badge.icon === CommentOutlined" />
              <UserOutlined v-else-if="badge.icon === UserOutlined" />
              {{ badge.text }}
            </span>
          </div>
        </div>
        <div class="welcome-illustration">
          <div class="illustration-circle">
            <svg viewBox="0 0 120 120" fill="none" xmlns="http://www.w3.org/2000/svg">
              <circle cx="60" cy="60" r="50" fill="rgba(255,255,255,0.1)"/>
              <path d="M35 60L50 75L85 42" stroke="white" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </div>
        </div>
      </div>
    </div>

    <!-- Stats Cards -->
    <div class="stats-grid">
      <div
        v-for="(stat, index) in statsData"
        :key="stat.label"
        class="stat-card"
        :style="{ animationDelay: `${index * 0.1}s` }"
      >
        <div class="stat-icon" :style="{ backgroundColor: stat.color }">
          <FolderOutlined v-if="stat.icon === FolderOutlined" />
          <FileTextOutlined v-else-if="stat.icon === FileTextOutlined" />
          <CommentOutlined v-else-if="stat.icon === CommentOutlined" />
          <UserOutlined v-else-if="stat.icon === UserOutlined" />
        </div>
        <div class="stat-info">
          <div class="stat-value">
            <CountUp :end-val="stat.value" :duration="2" />
          </div>
          <div class="stat-label">{{ stat.label }}</div>
        </div>
        <div class="stat-trend" :class="stat.trend > 0 ? 'up' : 'down'">
          <ArrowUpOutlined v-if="stat.trend > 0" />
          <ArrowDownOutlined v-else />
          <span>{{ Math.abs(stat.trend) }}%</span>
        </div>
      </div>
    </div>

    <!-- Quick Actions -->
    <div class="section-card">
      <div class="section-header">
        <h2 class="section-title">
          <AppstoreOutlined />
          快捷操作
        </h2>
        <span class="section-badge">常用</span>
      </div>
      <div class="actions-grid">
        <div
          v-for="action in quickActions"
          :key="action.label"
          class="action-card"
          @click="handleAction(action)"
        >
          <div class="action-icon" :style="{ backgroundColor: action.color }">
            <FolderAddOutlined v-if="action.icon === FolderAddOutlined" />
            <UploadOutlined v-else-if="action.icon === UploadOutlined" />
            <MessageOutlined v-else-if="action.icon === MessageOutlined" />
            <TeamOutlined v-else-if="action.icon === TeamOutlined" />
          </div>
          <div class="action-text">
            <span class="action-label">{{ action.label }}</span>
            <span class="action-desc">{{ action.desc }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Content Grid - Equal Height -->
    <div class="content-grid">
      <!-- Recent Documents -->
      <div class="section-card content-card">
        <div class="section-header">
          <h2 class="section-title">
            <FileTextOutlined />
            最近文档
          </h2>
          <a class="section-link" @click="router.push('/knowledge')">
            查看全部 <RightOutlined />
          </a>
        </div>
        <div class="document-list">
          <div
            v-for="(doc, index) in recentDocuments"
            :key="doc.id"
            class="document-item"
            :style="{ animationDelay: `${index * 0.05}s` }"
          >
            <div class="doc-icon" :style="{ color: getDocColor(doc.type) }">
              <FilePdfOutlined v-if="doc.type === 'pdf'" />
              <FileWordOutlined v-else-if="doc.type === 'doc'" />
              <FileMarkdownOutlined v-else-if="doc.type === 'md'" />
              <FileExcelOutlined v-else-if="doc.type === 'excel'" />
              <FileOutlined v-else />
            </div>
            <div class="doc-info">
              <div class="doc-name">{{ doc.name }}</div>
              <div class="doc-meta">
                <span>{{ doc.size }}</span>
                <span>{{ doc.time }}</span>
              </div>
            </div>
            <span class="doc-status" :class="doc.status">
              {{ doc.status === 'success' ? '已完成' : '处理中' }}
            </span>
          </div>
          <div v-if="recentDocuments.length === 0" class="empty-state">
            <InboxOutlined />
            <span>暂无文档</span>
          </div>
        </div>
      </div>

      <!-- Recent Chats -->
      <div class="section-card content-card">
        <div class="section-header">
          <h2 class="section-title">
            <CommentOutlined />
            最近问答
          </h2>
          <a class="section-link" @click="router.push('/chat')">
            查看全部 <RightOutlined />
          </a>
        </div>
        <div class="chat-list">
          <div
            v-for="(chat, index) in recentChats"
            :key="chat.id"
            class="chat-item"
            :style="{ animationDelay: `${index * 0.05}s` }"
          >
            <div class="chat-avatar">
              <UserOutlined />
            </div>
            <div class="chat-content">
              <div class="chat-question">{{ chat.question }}</div>
              <div class="chat-answer">{{ chat.answer }}</div>
            </div>
            <span class="chat-time">{{ chat.time }}</span>
          </div>
          <div v-if="recentChats.length === 0" class="empty-state">
            <MessageOutlined />
            <span>暂无问答记录</span>
          </div>
        </div>
      </div>
    </div>

    <!-- System Info -->
    <div class="section-card">
      <div class="section-header">
        <h2 class="section-title">
          <InfoCircleOutlined />
          系统信息
        </h2>
      </div>
      <div class="info-grid">
        <div class="info-item">
          <span class="info-label">系统版本</span>
          <span class="info-value">v1.0.0</span>
        </div>
        <div class="info-item">
          <span class="info-label">技术栈</span>
          <span class="info-value">Vue3 + TypeScript</span>
        </div>
        <div class="info-item">
          <span class="info-label">AI模型</span>
          <span class="info-value">GPT-4 / Claude</span>
        </div>
        <div class="info-item">
          <span class="info-label">向量数据库</span>
          <span class="info-value">Milvus</span>
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
  FolderOutlined,
  FileTextOutlined,
  CommentOutlined,
  UserOutlined,
  FolderAddOutlined,
  UploadOutlined,
  MessageOutlined,
  TeamOutlined,
  RightOutlined,
  ArrowUpOutlined,
  ArrowDownOutlined,
  FilePdfOutlined,
  FileWordOutlined,
  FileMarkdownOutlined,
  FileExcelOutlined,
  FileOutlined,
  AppstoreOutlined,
  InfoCircleOutlined,
  InboxOutlined,
} from '@ant-design/icons-vue'
import { getDashboardStats } from '@/api/dashboard'

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
const dashboardStats = reactive({
  knowledgeCount: 0,
  documentCount: 0,
  chatCount: 0,
  userCount: 0,
})

const welcomeBadges = computed(() => [
  { icon: FileTextOutlined, text: `${dashboardStats.documentCount} 文档` },
  { icon: CommentOutlined, text: `${dashboardStats.chatCount} 问答` },
  { icon: UserOutlined, text: `${dashboardStats.userCount} 用户` },
])

const statsData = computed(() => [
  {
    label: '知识库数量',
    value: dashboardStats.knowledgeCount,
    icon: FolderOutlined,
    color: '#059669',
    trend: 12,
  },
  {
    label: '文档数量',
    value: dashboardStats.documentCount,
    icon: FileTextOutlined,
    color: '#C67B5C',
    trend: 8,
  },
  {
    label: '问答次数',
    value: dashboardStats.chatCount,
    icon: CommentOutlined,
    color: '#5B9EA6',
    trend: -5,
  },
  {
    label: '用户数量',
    value: dashboardStats.userCount,
    icon: UserOutlined,
    color: '#6B7B3C',
    trend: 15,
  },
])

const quickActions = [
  {
    label: '创建知识库',
    desc: '新建知识库',
    icon: FolderAddOutlined,
    color: '#059669',
    route: '/knowledge',
  },
  {
    label: '上传文档',
    desc: '添加新文档',
    icon: UploadOutlined,
    color: '#C67B5C',
    route: '/knowledge',
  },
  {
    label: '开始问答',
    desc: '智能对话',
    icon: MessageOutlined,
    color: '#5B9EA6',
    route: '/chat',
  },
  {
    label: '用户管理',
    desc: '管理用户',
    icon: TeamOutlined,
    color: '#6B7B3C',
    route: '/user',
  },
]

const recentDocuments = ref([
  { id: 1, name: '产品需求文档.pdf', type: 'pdf', size: '2.5 MB', time: '10分钟前', status: 'success' },
  { id: 2, name: '技术架构设计.docx', type: 'doc', size: '1.8 MB', time: '1小时前', status: 'success' },
  { id: 3, name: '用户手册.md', type: 'md', size: '856 KB', time: '2小时前', status: 'processing' },
  { id: 4, name: 'API接口文档.pdf', type: 'pdf', size: '3.2 MB', time: '昨天', status: 'success' },
])

const recentChats = ref([
  { id: 1, question: '如何使用RAG技术优化问答系统？', answer: 'RAG技术通过检索增强生成，可以显著提升问答系统的准确性...', time: '5分钟前' },
  { id: 2, question: '向量数据库的选择建议？', answer: '推荐使用Milvus或Pinecone，它们在性能和易用性方面表现优秀...', time: '30分钟前' },
  { id: 3, question: '如何提高文档检索的准确率？', answer: '可以通过优化embedding模型、调整chunk大小等方式提升...', time: '1小时前' },
])

function getDocColor(type: string) {
  const colorMap: Record<string, string> = {
    pdf: '#C67B5C',
    doc: '#5B9EA6',
    md: '#059669',
    excel: '#D97706',
  }
  return colorMap[type] || '#78746C'
}

function handleAction(action: typeof quickActions[0]) {
  router.push(action.route)
}

async function loadStats() {
  statsLoading.value = true
  try {
    const { data } = await getDashboardStats()
    if (data.data) {
      dashboardStats.knowledgeCount = data.data.knowledgeCount || 0
      dashboardStats.documentCount = data.data.documentCount || 0
      dashboardStats.chatCount = data.data.chatCount || 0
      dashboardStats.userCount = data.data.userCount || 0
    }
  } catch (error) {
    console.error('加载统计数据失败:', error)
  } finally {
    statsLoading.value = false
  }
}

onMounted(() => {
  loadStats()
})
</script>

<style scoped lang="scss">
.dashboard {
  display: flex;
  flex-direction: column;
  gap: var(--card-gap);
}

// Welcome Banner - Nature Style
.welcome-banner {
  background: linear-gradient(135deg, #059669 0%, #228B22 50%, #6B8E4E 100%);
  border-radius: var(--radius-xl);
  padding: 24px 32px;
  position: relative;
  overflow: hidden;

  html.dark & {
    background: linear-gradient(135deg, #065F46 0%, #064E3B 50%, #022C22 100%);
  }
}

.banner-bg {
  position: absolute;
  inset: 0;
  overflow: hidden;

  .leaf {
    position: absolute;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.08);

    &.leaf-1 {
      width: 200px;
      height: 200px;
      top: -50px;
      right: 10%;
    }

    &.leaf-2 {
      width: 150px;
      height: 150px;
      bottom: -30px;
      left: 20%;
    }

    &.leaf-3 {
      width: 100px;
      height: 100px;
      top: 50%;
      right: 30%;
    }
  }
}

.banner-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  position: relative;
  z-index: 1;
}

.welcome-text {
  color: #fff;
}

.welcome-title {
  font-family: var(--font-serif);
  font-size: 28px;
  font-weight: 600;
  margin-bottom: 8px;

  .username {
    color: #FBBF24;
  }
}

.welcome-subtitle {
  font-size: 15px;
  opacity: 0.9;
  margin-bottom: 20px;
}

.welcome-tags {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;

  .tag {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 8px 16px;
    background: rgba(255, 255, 255, 0.15);
    border-radius: var(--radius-full);
    font-size: 13px;
    font-weight: 500;
    backdrop-filter: blur(8px);
    transition: all var(--duration-fast) var(--ease-nature);

    &:hover {
      background: rgba(255, 255, 255, 0.25);
      transform: translateY(-2px);
    }
  }
}

.welcome-illustration {
  .illustration-circle {
    width: 100px;
    height: 100px;

    svg {
      width: 100%;
      height: 100%;
      filter: drop-shadow(0 4px 12px rgba(0, 0, 0, 0.15));
    }
  }
}

// Stats Grid
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--card-gap);
}

.stat-card {
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-xl);
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  transition: all var(--duration-normal) var(--ease-nature);
  animation: slideInUp 0.4s var(--ease-out) both;

  &:hover {
    transform: translateY(-2px);
    box-shadow: var(--shadow-card-hover);
  }
}

.stat-icon {
  width: 52px;
  height: 52px;
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 24px;
  flex-shrink: 0;
}

.stat-info {
  flex: 1;
  min-width: 0;
}

.stat-value {
  font-family: var(--font-serif);
  font-size: 28px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: var(--text-secondary);
  margin-top: 2px;
}

.stat-trend {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 12px;
  font-weight: 500;
  padding: 4px 8px;
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

// Section Card
.section-card {
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-xl);
  padding: 20px;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.section-title {
  font-family: var(--font-serif);
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  display: flex;
  align-items: center;
  gap: 10px;

  :deep(.anticon) {
    color: var(--primary-color);
  }
}

.section-badge {
  font-size: 12px;
  font-weight: 500;
  color: var(--primary-color);
  background: var(--primary-lighter);
  padding: 4px 12px;
  border-radius: var(--radius-full);
}

.section-link {
  font-size: 13px;
  color: var(--primary-color);
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
  transition: opacity var(--duration-fast);

  &:hover {
    opacity: 0.8;
  }
}

// Actions Grid
.actions-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--card-gap);
}

.action-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 24px 16px;
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: all var(--duration-normal) var(--ease-nature);
  text-align: center;

  &:hover {
    background: var(--bg-surface-secondary);
    transform: translateY(-2px);

    .action-icon {
      transform: scale(1.05);
    }
  }
}

.action-icon {
  width: 56px;
  height: 56px;
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 24px;
  transition: transform var(--duration-normal) var(--ease-nature);
}

.action-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.action-label {
  font-size: 15px;
  font-weight: 500;
  color: var(--text-primary);
}

.action-desc {
  font-size: 12px;
  color: var(--text-secondary);
}

// Content Grid - Equal Height
.content-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--card-gap);
}

.content-card {
  display: flex;
  flex-direction: column;

  .section-header {
    flex-shrink: 0;
  }

  .document-list,
  .chat-list {
    flex: 1;
    min-height: 200px;
  }
}

// Document List
.document-list {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.document-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--border-light);
  animation: slideInUp 0.3s var(--ease-out) both;
  transition: background var(--duration-fast);

  &:last-child {
    border-bottom: none;
  }

  &:hover {
    .doc-name {
      color: var(--primary-color);
    }
  }
}

.doc-icon {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  flex-shrink: 0;
}

.doc-info {
  flex: 1;
  min-width: 0;
}

.doc-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  transition: color var(--duration-fast);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: var(--text-tertiary);
  margin-top: 4px;
}

.doc-status {
  font-size: 12px;
  font-weight: 500;
  padding: 4px 10px;
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

// Chat List
.chat-list {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.chat-item {
  display: flex;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--border-light);
  animation: slideInUp 0.3s var(--ease-out) both;

  &:last-child {
    border-bottom: none;
  }
}

.chat-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--primary-lighter);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--primary-color);
  font-size: 16px;
  flex-shrink: 0;
}

.chat-content {
  flex: 1;
  min-width: 0;
}

.chat-question {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-answer {
  font-size: 13px;
  color: var(--text-secondary);
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-time {
  font-size: 12px;
  color: var(--text-tertiary);
  white-space: nowrap;
  flex-shrink: 0;
}

// Empty State
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
    font-size: 14px;
  }
}

// Info Grid
.info-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-label {
  font-size: 12px;
  color: var(--text-secondary);
}

.info-value {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
}

// Animations
@keyframes slideInUp {
  from {
    opacity: 0;
    transform: translateY(16px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

// Responsive
@media (max-width: 1200px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .actions-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .info-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .dashboard {
    padding: 16px;
    gap: 16px;
  }

  .welcome-banner {
    padding: 24px;
  }

  .welcome-title {
    font-size: 22px;
  }

  .welcome-illustration {
    display: none;
  }

  .stats-grid {
    grid-template-columns: 1fr;
  }

  .actions-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .content-grid {
    grid-template-columns: 1fr;
  }

  .info-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>