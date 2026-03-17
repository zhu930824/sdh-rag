<template>
  <div class="dashboard-container">
    <!-- 欢迎区域 -->
    <div class="welcome-section animate-slide-down">
      <div class="welcome-content">
        <div class="welcome-text">
          <h1 class="welcome-title">
            {{ getGreeting() }}，<span class="username">{{ nickname || '用户' }}</span>
          </h1>
          <p class="welcome-subtitle">欢迎使用智能知识库管理系统，开始您的高效工作之旅</p>
          <div class="welcome-stats">
            <div class="stat-badge" v-for="(badge, index) in welcomeBadges" :key="index">
              <component :is="badge.icon" />
              <span>{{ badge.text }}</span>
            </div>
          </div>
        </div>
        <div class="welcome-illustration">
          <div class="illustration-wrapper">
            <LineChartOutlined class="illustration-icon" />
            <div class="illustration-glow"></div>
            <div class="floating-particles">
              <div class="particle" v-for="i in 8" :key="i" :style="getParticleStyle(i)"></div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 统计卡片 -->
    <a-row :gutter="20" class="stats-row">
      <a-col :xs="24" :sm="12" :md="6" v-for="(stat, index) in statsData" :key="stat.label">
        <a-card
          class="stat-card card-hover animate-slide-up"
          :style="{ animationDelay: `${index * 0.1}s` }"
        >
          <div class="stat-content">
            <div class="stat-icon" :style="{ background: stat.gradient }">
              <component :is="stat.icon" class="stat-icon-component" />
            </div>
            <div class="stat-info">
              <div class="stat-value">
                <CountUp :end-val="stat.value" :duration="2" />
              </div>
              <div class="stat-label">{{ stat.label }}</div>
              <div class="stat-trend" :class="stat.trend > 0 ? 'up' : 'down'">
                <component :is="stat.trend > 0 ? ArrowUpOutlined : ArrowDownOutlined" />
                <span>{{ Math.abs(stat.trend) }}%</span>
              </div>
            </div>
          </div>
        </a-card>
      </a-col>
    </a-row>

    <!-- 快捷操作 -->
    <a-card class="quick-actions-card animate-slide-up" style="animation-delay: 0.3s">
      <template #title>
        <div class="card-header">
          <span class="card-title">快捷操作</span>
          <a-tag color="blue">常用功能</a-tag>
        </div>
      </template>
      <a-row :gutter="20">
        <a-col :xs="12" :sm="6" v-for="action in quickActions" :key="action.label">
          <div class="action-item interactive-transition" @click="handleAction(action)">
            <div class="action-icon" :style="{ background: action.gradient }">
              <component :is="action.icon" class="action-icon-component" />
            </div>
            <span class="action-label">{{ action.label }}</span>
            <span class="action-desc">{{ action.desc }}</span>
          </div>
        </a-col>
      </a-row>
    </a-card>

    <!-- 内容区域 -->
    <a-row :gutter="20" class="content-row">
      <!-- 最近文档 -->
      <a-col :xs="24" :lg="12">
        <a-card class="content-card animate-slide-left" style="animation-delay: 0.4s">
          <template #title>
            <div class="card-header">
              <span class="card-title">
                <FileTextOutlined />
                最近文档
              </span>
              <a @click="router.push('/document')">
                查看全部
                <RightOutlined />
              </a>
            </div>
          </template>
          <div class="document-list">
            <div
              v-for="(doc, index) in recentDocuments"
              :key="doc.id"
              class="document-item"
              :style="{ animationDelay: `${0.5 + index * 0.05}s` }"
            >
              <div class="doc-icon">
                <component :is="getDocIcon(doc.type)" :style="{ color: getDocColor(doc.type) }" />
              </div>
              <div class="doc-info">
                <div class="doc-name text-ellipsis">{{ doc.name }}</div>
                <div class="doc-meta">
                  <span class="doc-size">{{ doc.size }}</span>
                  <span class="doc-time">{{ doc.time }}</span>
                </div>
              </div>
              <a-tag :color="doc.status === 'success' ? 'success' : 'processing'">
                {{ doc.status === 'success' ? '已处理' : '处理中' }}
              </a-tag>
            </div>
            <a-empty v-if="recentDocuments.length === 0" description="暂无文档" />
          </div>
        </a-card>
      </a-col>

      <!-- 最近问答 -->
      <a-col :xs="24" :lg="12">
        <a-card class="content-card animate-slide-right" style="animation-delay: 0.4s">
          <template #title>
            <div class="card-header">
              <span class="card-title">
                <CommentOutlined />
                最近问答
              </span>
              <a @click="router.push('/chat')">
                查看全部
                <RightOutlined />
              </a>
            </div>
          </template>
          <div class="chat-list">
            <div
              v-for="(chat, index) in recentChats"
              :key="chat.id"
              class="chat-item"
              :style="{ animationDelay: `${0.5 + index * 0.05}s` }"
            >
              <div class="chat-avatar">
                <UserOutlined />
              </div>
              <div class="chat-content">
                <div class="chat-question text-ellipsis">{{ chat.question }}</div>
                <div class="chat-answer text-ellipsis">{{ chat.answer }}</div>
              </div>
              <div class="chat-time">{{ chat.time }}</div>
            </div>
            <a-empty v-if="recentChats.length === 0" description="暂无问答记录" />
          </div>
        </a-card>
      </a-col>
    </a-row>

    <!-- 系统信息 -->
    <a-card class="system-info-card animate-slide-up" style="animation-delay: 0.5s">
      <template #title>
        <div class="card-header">
          <span class="card-title">
            <DesktopOutlined />
            系统信息
          </span>
        </div>
      </template>
      <a-descriptions :column="{ xs: 1, sm: 2, md: 4 }" bordered>
        <a-descriptions-item label="系统版本">
          <a-tag color="blue">v1.0.0</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="技术栈">
          <a-tag color="green">Vue3 + TypeScript</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="AI模型">
          <a-tag color="orange">GPT-4 / Claude</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="向量数据库">
          <a-tag color="cyan">Milvus</a-tag>
        </a-descriptions-item>
      </a-descriptions>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, ref, watch } from 'vue'
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
  LineChartOutlined,
  RightOutlined,
  DesktopOutlined,
  ArrowUpOutlined,
  ArrowDownOutlined,
  FilePdfOutlined,
  FileWordOutlined,
  FileMarkdownOutlined,
  FileExcelOutlined,
  FileOutlined,
} from '@ant-design/icons-vue'

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

const welcomeBadges = [
  { icon: FileTextOutlined, text: '256 文档' },
  { icon: CommentOutlined, text: '1024 问答' },
  { icon: UserOutlined, text: '48 用户' },
]

function getParticleStyle(index: number): Record<string, string> {
  const angle = (index / 8) * 360
  const distance = 60 + Math.random() * 20
  const size = 4 + Math.random() * 4
  const delay = Math.random() * 2
  return {
    left: `calc(50% + ${Math.cos(angle * Math.PI / 180) * distance}px)`,
    top: `calc(50% + ${Math.sin(angle * Math.PI / 180) * distance}px)`,
    width: `${size}px`,
    height: `${size}px`,
    animationDelay: `${delay}s`,
  }
}

const statsData = [
  {
    label: '知识库数量',
    value: 12,
    icon: FolderOutlined,
    gradient: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    trend: 12,
  },
  {
    label: '文档数量',
    value: 256,
    icon: FileTextOutlined,
    gradient: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
    trend: 8,
  },
  {
    label: '问答次数',
    value: 1024,
    icon: CommentOutlined,
    gradient: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
    trend: -5,
  },
  {
    label: '用户数量',
    value: 48,
    icon: UserOutlined,
    gradient: 'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)',
    trend: 15,
  },
]

const quickActions = [
  {
    label: '创建知识库',
    desc: '新建知识库',
    icon: FolderAddOutlined,
    gradient: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    route: '/knowledge',
  },
  {
    label: '上传文档',
    desc: '添加新文档',
    icon: UploadOutlined,
    gradient: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
    route: '/document',
  },
  {
    label: '开始问答',
    desc: '智能对话',
    icon: MessageOutlined,
    gradient: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
    route: '/chat',
  },
  {
    label: '用户管理',
    desc: '管理用户',
    icon: TeamOutlined,
    gradient: 'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)',
    route: '/user',
  },
]

const recentDocuments = ref([
  { id: 1, name: '产品需求文档.pdf', type: 'pdf', size: '2.5 MB', time: '10分钟前', status: 'success' },
  { id: 2, name: '技术架构设计.docx', type: 'doc', size: '1.8 MB', time: '1小时前', status: 'success' },
  { id: 3, name: '用户手册.md', type: 'md', size: '856 KB', time: '2小时前', status: 'processing' },
  { id: 4, name: 'API接口文档.pdf', type: 'pdf', size: '3.2 MB', time: '昨天', status: 'success' },
  { id: 5, name: '测试报告.xlsx', type: 'excel', size: '1.2 MB', time: '昨天', status: 'success' },
])

const recentChats = ref([
  { id: 1, question: '如何使用RAG技术优化问答系统？', answer: 'RAG技术通过检索增强生成，可以显著提升问答系统的准确性...', time: '5分钟前' },
  { id: 2, question: '向量数据库的选择建议？', answer: '推荐使用Milvus或Pinecone，它们在性能和易用性方面表现优秀...', time: '30分钟前' },
  { id: 3, question: '如何提高文档检索的准确率？', answer: '可以通过优化embedding模型、调整chunk大小等方式提升...', time: '1小时前' },
  { id: 4, question: '系统支持哪些文档格式？', answer: '目前支持PDF、Word、Markdown、TXT等常见格式...', time: '2小时前' },
])

function getDocIcon(type: string) {
  const iconMap: Record<string, typeof FileTextOutlined> = {
    pdf: FilePdfOutlined,
    doc: FileWordOutlined,
    md: FileMarkdownOutlined,
    excel: FileExcelOutlined,
  }
  return iconMap[type] || FileOutlined
}

function getDocColor(type: string) {
  const colorMap: Record<string, string> = {
    pdf: '#f56c6c',
    doc: '#409eff',
    md: '#67c23a',
    excel: '#e6a23c',
  }
  return colorMap[type] || '#909399'
}

function handleAction(action: typeof quickActions[0]) {
  router.push(action.route)
}
</script>

<style scoped lang="scss">
.dashboard-container {
  padding: 16px;
}

.welcome-section {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: var(--border-radius-large);
  padding: 24px 32px;
  margin-bottom: 20px;
  position: relative;
  overflow: hidden;

  html.dark & {
    background: linear-gradient(135deg, #2d3748 0%, #1a202c 100%);
  }

  &::before {
    content: '';
    position: absolute;
    top: -50%;
    right: -10%;
    width: 300px;
    height: 300px;
    background: radial-gradient(circle, rgba(255, 255, 255, 0.1) 0%, transparent 70%);
    border-radius: 50%;
  }

  &::after {
    content: '';
    position: absolute;
    bottom: -30%;
    left: -10%;
    width: 200px;
    height: 200px;
    background: radial-gradient(circle, rgba(255, 255, 255, 0.08) 0%, transparent 70%);
    border-radius: 50%;
  }

  .welcome-content {
    display: flex;
    justify-content: space-between;
    align-items: center;
    position: relative;
    z-index: 1;
  }

  .welcome-text {
    flex: 1;
    color: #fff;
  }

  .welcome-title {
    font-size: 28px;
    font-weight: 600;
    margin-bottom: 8px;
    display: flex;
    align-items: center;
    gap: 8px;

    .username {
      background: linear-gradient(135deg, #ffd89b 0%, #19547b 100%);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      background-clip: text;
    }
  }

  .welcome-subtitle {
    font-size: 14px;
    opacity: 0.9;
    margin-bottom: 20px;
  }

  .welcome-stats {
    display: flex;
    gap: 12px;

    .stat-badge {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 8px 16px;
      background: rgba(255, 255, 255, 0.15);
      border-radius: 20px;
      backdrop-filter: blur(10px);
      font-size: 13px;
      font-weight: 500;
      transition: all 0.3s ease;

      &:hover {
        background: rgba(255, 255, 255, 0.25);
        transform: translateY(-2px);
      }

      :deep(.anticon) {
        font-size: 16px;
      }
    }
  }

  .welcome-illustration {
    .illustration-wrapper {
      position: relative;
      width: 160px;
      height: 160px;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .illustration-icon {
      font-size: 120px;
      color: rgba(255, 255, 255, 0.3);
      animation: float 3s ease-in-out infinite;
      z-index: 2;
    }

    .illustration-glow {
      position: absolute;
      width: 120px;
      height: 120px;
      background: radial-gradient(circle, rgba(255, 255, 255, 0.2) 0%, transparent 70%);
      border-radius: 50%;
      animation: glow 2s ease-in-out infinite;
      z-index: 1;
    }

    .floating-particles {
      position: absolute;
      width: 100%;
      height: 100%;

      .particle {
        position: absolute;
        background: rgba(255, 255, 255, 0.6);
        border-radius: 50%;
        animation: floatParticle 3s ease-in-out infinite;
      }
    }
  }
}

@keyframes float {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-10px);
  }
}

@keyframes glow {
  0%, 100% {
    opacity: 0.5;
    transform: scale(1);
  }
  50% {
    opacity: 1;
    transform: scale(1.1);
  }
}

@keyframes floatParticle {
  0%, 100% {
    transform: translateY(0) scale(1);
    opacity: 0.6;
  }
  50% {
    transform: translateY(-8px) scale(1.2);
    opacity: 1;
  }
}

.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  margin-bottom: 20px;
  position: relative;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 3px;
    background: var(--gradient-purple);
    opacity: 0;
    transition: opacity 0.3s ease;
  }

  &:hover::before {
    opacity: 1;
  }

  .stat-content {
    display: flex;
    align-items: center;
    gap: 16px;
  }

  .stat-icon {
    width: 60px;
    height: 60px;
    border-radius: var(--border-radius-base);
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    flex-shrink: 0;
    position: relative;
    overflow: hidden;

    &::after {
      content: '';
      position: absolute;
      top: -50%;
      left: -50%;
      width: 200%;
      height: 200%;
      background: linear-gradient(
        45deg,
        transparent 30%,
        rgba(255, 255, 255, 0.3) 50%,
        transparent 70%
      );
      transform: rotate(45deg);
      animation: shimmer 2s infinite;
    }

    .stat-icon-component {
      font-size: 28px;
    }
  }

  .stat-info {
    flex: 1;
    min-width: 0;
  }

  .stat-value {
    font-size: 32px;
    font-weight: 700;
    color: var(--text-primary);
    line-height: 1;
    margin-bottom: 4px;
    background: linear-gradient(135deg, var(--text-primary) 0%, var(--text-regular) 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    background-clip: text;
  }

  .stat-label {
    font-size: 14px;
    color: var(--text-secondary);
    margin-bottom: 4px;
  }

  .stat-trend {
    display: inline-flex;
    align-items: center;
    gap: 2px;
    font-size: 12px;
    padding: 2px 8px;
    border-radius: 12px;
    font-weight: 500;

    &.up {
      color: var(--success-color);
      background: var(--success-light-9);
    }

    &.down {
      color: var(--danger-color);
      background: var(--danger-light-9);
    }
  }
}

@keyframes shimmer {
  0% {
    transform: translateX(-100%) rotate(45deg);
  }
  100% {
    transform: translateX(100%) rotate(45deg);
  }
}

.quick-actions-card {
  margin-bottom: 20px;

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .card-title {
    font-size: 16px;
    font-weight: 600;
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .action-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 12px;
    padding: 24px 16px;
    border-radius: var(--border-radius-base);
    cursor: pointer;
    text-align: center;
    position: relative;
    overflow: hidden;
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);

    &::before {
      content: '';
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: linear-gradient(135deg, rgba(102, 126, 234, 0.05) 0%, rgba(118, 75, 162, 0.05) 100%);
      opacity: 0;
      transition: opacity 0.3s ease;
    }

    &:hover {
      background: var(--bg-hover);
      transform: translateY(-4px);
      box-shadow: 0 8px 20px rgba(0, 0, 0, 0.1);

      &::before {
        opacity: 1;
      }

      .action-icon {
        transform: scale(1.1) rotate(5deg);
      }
    }

    &:active {
      transform: translateY(-2px);
    }

    .action-icon {
      width: 64px;
      height: 64px;
      border-radius: var(--border-radius-large);
      display: flex;
      align-items: center;
      justify-content: center;
      color: #fff;
      position: relative;
      z-index: 1;
      transition: transform 0.3s cubic-bezier(0.68, -0.55, 0.265, 1.55);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);

      .action-icon-component {
        font-size: 32px;
      }
    }

    .action-label {
      font-size: 15px;
      font-weight: 500;
      color: var(--text-primary);
      position: relative;
      z-index: 1;
    }

    .action-desc {
      font-size: 12px;
      color: var(--text-secondary);
      position: relative;
      z-index: 1;
    }
  }
}

.content-row {
  margin-bottom: 20px;
}

.content-card {
  margin-bottom: 20px;

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .card-title {
    font-size: 16px;
    font-weight: 600;
    display: flex;
    align-items: center;
    gap: 8px;
  }
}

.document-list {
  .document-item {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px;
    border-radius: var(--border-radius-base);
    transition: all var(--transition-duration);
    animation: slideInUp 0.3s ease-out both;
    position: relative;
    overflow: hidden;

    &::before {
      content: '';
      position: absolute;
      left: 0;
      top: 0;
      bottom: 0;
      width: 3px;
      background: var(--gradient-purple);
      transform: scaleY(0);
      transition: transform 0.3s ease;
    }

    &:hover {
      background: var(--bg-hover);
      transform: translateX(4px);

      &::before {
        transform: scaleY(1);
      }

      .doc-icon {
        transform: scale(1.1) rotate(5deg);
      }
    }

    &:not(:last-child) {
      border-bottom: 1px solid var(--border-lighter);
    }

    .doc-icon {
      width: 40px;
      height: 40px;
      border-radius: var(--border-radius-small);
      background: var(--bg-page);
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      transition: transform 0.3s cubic-bezier(0.68, -0.55, 0.265, 1.55);
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);

      :deep(.anticon) {
        font-size: 24px;
      }
    }

    .doc-info {
      flex: 1;
      min-width: 0;
    }

    .doc-name {
      font-size: 14px;
      font-weight: 500;
      color: var(--text-primary);
      margin-bottom: 4px;
      transition: color 0.3s ease;

      .document-item:hover & {
        color: var(--primary-color);
      }
    }

    .doc-meta {
      display: flex;
      gap: 12px;
      font-size: 12px;
      color: var(--text-secondary);
    }
  }
}

.chat-list {
  .chat-item {
    display: flex;
    gap: 12px;
    padding: 12px;
    border-radius: var(--border-radius-base);
    transition: all var(--transition-duration);
    animation: slideInUp 0.3s ease-out both;

    &:hover {
      background: var(--bg-hover);
    }

    &:not(:last-child) {
      border-bottom: 1px solid var(--border-lighter);
    }

    .chat-avatar {
      width: 36px;
      height: 36px;
      border-radius: 50%;
      background: var(--primary-light-9);
      display: flex;
      align-items: center;
      justify-content: center;
      color: var(--primary-color);
      flex-shrink: 0;

      :deep(.anticon) {
        font-size: 20px;
      }
    }

    .chat-content {
      flex: 1;
      min-width: 0;
    }

    .chat-question {
      font-size: 14px;
      font-weight: 500;
      color: var(--text-primary);
      margin-bottom: 4px;
    }

    .chat-answer {
      font-size: 13px;
      color: var(--text-secondary);
    }

    .chat-time {
      font-size: 12px;
      color: var(--text-placeholder);
      white-space: nowrap;
    }
  }
}

.system-info-card {
  .card-title {
    font-size: 16px;
    font-weight: 600;
    display: flex;
    align-items: center;
    gap: 8px;
  }
}

@media (max-width: 768px) {
  .dashboard-container {
    padding: 12px;
  }

  .welcome-section {
    padding: 20px;

    .welcome-title {
      font-size: 22px;
    }

    .welcome-illustration {
      display: none;
    }
  }

  .stat-card .stat-value {
    font-size: 24px;
  }

  .action-item {
    padding: 16px 12px;

    .action-icon {
      width: 48px;
      height: 48px;
    }
  }
}
</style>
