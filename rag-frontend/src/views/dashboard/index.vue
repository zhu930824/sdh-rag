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
              <el-icon><component :is="badge.icon" /></el-icon>
              <span>{{ badge.text }}</span>
            </div>
          </div>
        </div>
        <div class="welcome-illustration">
          <div class="illustration-wrapper">
            <el-icon :size="120" class="illustration-icon">
              <DataAnalysis />
            </el-icon>
            <div class="illustration-glow"></div>
            <div class="floating-particles">
              <div class="particle" v-for="i in 8" :key="i" :style="getParticleStyle(i)"></div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :xs="24" :sm="12" :md="6" v-for="(stat, index) in statsData" :key="stat.label">
        <el-card
          class="stat-card card-hover animate-slide-up"
          :style="{ animationDelay: `${index * 0.1}s` }"
        >
          <div class="stat-content">
            <div class="stat-icon" :style="{ background: stat.gradient }">
              <el-icon :size="28">
                <component :is="stat.icon" />
              </el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">
                <CountUp :end-val="stat.value" :duration="2" />
              </div>
              <div class="stat-label">{{ stat.label }}</div>
              <div class="stat-trend" :class="stat.trend > 0 ? 'up' : 'down'">
                <el-icon>
                  <component :is="stat.trend > 0 ? 'Top' : 'Bottom'" />
                </el-icon>
                <span>{{ Math.abs(stat.trend) }}%</span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 快捷操作 -->
    <el-card class="quick-actions-card animate-slide-up" style="animation-delay: 0.3s">
      <template #header>
        <div class="card-header">
          <span class="card-title">快捷操作</span>
          <el-tag size="small" type="info">常用功能</el-tag>
        </div>
      </template>
      <el-row :gutter="20">
        <el-col :xs="12" :sm="6" v-for="action in quickActions" :key="action.label">
          <div class="action-item interactive-transition" @click="handleAction(action)">
            <div class="action-icon" :style="{ background: action.gradient }">
              <el-icon :size="32">
                <component :is="action.icon" />
              </el-icon>
            </div>
            <span class="action-label">{{ action.label }}</span>
            <span class="action-desc">{{ action.desc }}</span>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 内容区域 -->
    <el-row :gutter="20" class="content-row">
      <!-- 最近文档 -->
      <el-col :xs="24" :lg="12">
        <el-card class="content-card animate-slide-left" style="animation-delay: 0.4s">
          <template #header>
            <div class="card-header">
              <span class="card-title">
                <el-icon><Document /></el-icon>
                最近文档
              </span>
              <el-link type="primary" :underline="false" @click="router.push('/document')">
                查看全部
                <el-icon><ArrowRight /></el-icon>
              </el-link>
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
                <el-icon :size="24" :color="getDocColor(doc.type)">
                  <component :is="getDocIcon(doc.type)" />
                </el-icon>
              </div>
              <div class="doc-info">
                <div class="doc-name text-ellipsis">{{ doc.name }}</div>
                <div class="doc-meta">
                  <span class="doc-size">{{ doc.size }}</span>
                  <span class="doc-time">{{ doc.time }}</span>
                </div>
              </div>
              <el-tag :type="doc.status === 'success' ? 'success' : 'info'" size="small">
                {{ doc.status === 'success' ? '已处理' : '处理中' }}
              </el-tag>
            </div>
            <el-empty v-if="recentDocuments.length === 0" description="暂无文档" />
          </div>
        </el-card>
      </el-col>

      <!-- 最近问答 -->
      <el-col :xs="24" :lg="12">
        <el-card class="content-card animate-slide-right" style="animation-delay: 0.4s">
          <template #header>
            <div class="card-header">
              <span class="card-title">
                <el-icon><ChatDotRound /></el-icon>
                最近问答
              </span>
              <el-link type="primary" :underline="false" @click="router.push('/chat')">
                查看全部
                <el-icon><ArrowRight /></el-icon>
              </el-link>
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
                <el-icon :size="20"><User /></el-icon>
              </div>
              <div class="chat-content">
                <div class="chat-question text-ellipsis">{{ chat.question }}</div>
                <div class="chat-answer text-ellipsis">{{ chat.answer }}</div>
              </div>
              <div class="chat-time">{{ chat.time }}</div>
            </div>
            <el-empty v-if="recentChats.length === 0" description="暂无问答记录" />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 系统信息 -->
    <el-card class="system-info-card animate-slide-up" style="animation-delay: 0.5s">
      <template #header>
        <div class="card-header">
          <span class="card-title">
            <el-icon><Monitor /></el-icon>
            系统信息
          </span>
        </div>
      </template>
      <el-descriptions :column="4" border>
        <el-descriptions-item label="系统版本">
          <el-tag size="small">v1.0.0</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="技术栈">
          <el-tag type="success" size="small">Vue3 + TypeScript</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="AI模型">
          <el-tag type="warning" size="small">GPT-4 / Claude</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="向量数据库">
          <el-tag type="info" size="small">Milvus</el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import {
  Folder,
  Document,
  ChatDotRound,
  User,
  FolderAdd,
  Upload,
  ChatLineRound,
  UserFilled,
  DataAnalysis,
  ArrowRight,
  Monitor,
  Top,
  Bottom,
  DocumentCopy,
  Tickets,
  Files,
} from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()

// 用户昵称
const nickname = computed(() => userStore.nickname)

// 数字递增组件
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

// 获取问候语
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

// 欢迎区域徽章
const welcomeBadges = [
  { icon: Document, text: '256 文档' },
  { icon: ChatDotRound, text: '1024 问答' },
  { icon: User, text: '48 用户' },
]

// 获取粒子样式
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

// 统计数据
const statsData = [
  {
    label: '知识库数量',
    value: 12,
    icon: Folder,
    gradient: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    trend: 12,
  },
  {
    label: '文档数量',
    value: 256,
    icon: Document,
    gradient: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
    trend: 8,
  },
  {
    label: '问答次数',
    value: 1024,
    icon: ChatDotRound,
    gradient: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
    trend: -5,
  },
  {
    label: '用户数量',
    value: 48,
    icon: User,
    gradient: 'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)',
    trend: 15,
  },
]

// 快捷操作
const quickActions = [
  {
    label: '创建知识库',
    desc: '新建知识库',
    icon: FolderAdd,
    gradient: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    route: '/knowledge',
  },
  {
    label: '上传文档',
    desc: '添加新文档',
    icon: Upload,
    gradient: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
    route: '/document',
  },
  {
    label: '开始问答',
    desc: '智能对话',
    icon: ChatLineRound,
    gradient: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
    route: '/chat',
  },
  {
    label: '用户管理',
    desc: '管理用户',
    icon: UserFilled,
    gradient: 'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)',
    route: '/user',
  },
]

// 最近文档
const recentDocuments = ref([
  { id: 1, name: '产品需求文档.pdf', type: 'pdf', size: '2.5 MB', time: '10分钟前', status: 'success' },
  { id: 2, name: '技术架构设计.docx', type: 'doc', size: '1.8 MB', time: '1小时前', status: 'success' },
  { id: 3, name: '用户手册.md', type: 'md', size: '856 KB', time: '2小时前', status: 'processing' },
  { id: 4, name: 'API接口文档.pdf', type: 'pdf', size: '3.2 MB', time: '昨天', status: 'success' },
  { id: 5, name: '测试报告.xlsx', type: 'excel', size: '1.2 MB', time: '昨天', status: 'success' },
])

// 最近问答
const recentChats = ref([
  { id: 1, question: '如何使用RAG技术优化问答系统？', answer: 'RAG技术通过检索增强生成，可以显著提升问答系统的准确性...', time: '5分钟前' },
  { id: 2, question: '向量数据库的选择建议？', answer: '推荐使用Milvus或Pinecone，它们在性能和易用性方面表现优秀...', time: '30分钟前' },
  { id: 3, question: '如何提高文档检索的准确率？', answer: '可以通过优化embedding模型、调整chunk大小等方式提升...', time: '1小时前' },
  { id: 4, question: '系统支持哪些文档格式？', answer: '目前支持PDF、Word、Markdown、TXT等常见格式...', time: '2小时前' },
])

// 获取文档图标
function getDocIcon(type: string) {
  const iconMap: Record<string, typeof Document> = {
    pdf: DocumentCopy,
    doc: Tickets,
    md: Document,
    excel: Files,
  }
  return iconMap[type] || Document
}

// 获取文档颜色
function getDocColor(type: string) {
  const colorMap: Record<string, string> = {
    pdf: '#f56c6c',
    doc: '#409eff',
    md: '#67c23a',
    excel: '#e6a23c',
  }
  return colorMap[type] || '#909399'
}

// 处理快捷操作
function handleAction(action: typeof quickActions[0]) {
  router.push(action.route)
}
</script>

<style scoped lang="scss">
.dashboard-container {
  padding: 20px;
  max-width: 1400px;
  margin: 0 auto;
}

// 欢迎区域
.welcome-section {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: var(--border-radius-large);
  padding: 32px 40px;
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

      .el-icon {
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

// 统计卡片
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

// 快捷操作
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

// 内容区域
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

// 文档列表
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

// 问答列表
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

// 系统信息
.system-info-card {
  .card-title {
    font-size: 16px;
    font-weight: 600;
    display: flex;
    align-items: center;
    gap: 8px;
  }
}

// 响应式设计
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
