<template>
  <div class="stats-container">
    <!-- 时间筛选 -->
    <a-card class="filter-card">
      <div class="filter-content">
        <div class="filter-left">
          <span class="filter-label">时间范围：</span>
          <a-radio-group v-model:value="timeRange" button-style="solid" @change="handleTimeChange">
            <a-radio-button value="today">今日</a-radio-button>
            <a-radio-button value="week">近7天</a-radio-button>
            <a-radio-button value="month">近30天</a-radio-button>
            <a-radio-button value="custom">自定义</a-radio-button>
          </a-radio-group>
          <a-range-picker
            v-if="timeRange === 'custom'"
            v-model:value="customRange"
            style="margin-left: 16px"
            @change="handleCustomRangeChange"
          />
        </div>
        <div class="filter-right">
          <a-button type="primary" @click="handleRefresh">
            <template #icon><ReloadOutlined /></template>
            刷新数据
          </a-button>
        </div>
      </div>
    </a-card>

    <!-- 统计概览 -->
    <a-row :gutter="20" class="stats-row">
      <a-col :xs="24" :sm="12" :md="6" v-for="(stat, index) in statsData" :key="stat.label">
        <a-card
          class="stat-card card-hover"
          :style="{ animationDelay: `${index * 0.1}s` }"
        >
          <div class="stat-content">
            <div class="stat-icon" :style="{ background: stat.gradient }">
              <FileOutlined v-if="stat.label === '总文档数'" class="stat-icon-component" />
              <UserOutlined v-else-if="stat.label === '总用户数'" class="stat-icon-component" />
              <MessageOutlined v-else-if="stat.label === '总对话数'" class="stat-icon-component" />
              <RiseOutlined v-else class="stat-icon-component" />
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ formatNumber(stat.value) }}</div>
              <div class="stat-label">{{ stat.label }}</div>
              <div class="stat-trend" :class="stat.trend > 0 ? 'up' : 'down'">
                <ArrowUpOutlined v-if="stat.trend > 0" />
                <ArrowDownOutlined v-else />
                <span>{{ Math.abs(stat.trend) }}%</span>
              </div>
            </div>
          </div>
        </a-card>
      </a-col>
    </a-row>

    <!-- 图表区域 -->
    <a-row :gutter="20" class="chart-row">
      <a-col :xs="24" :lg="12">
        <a-card title="对话趋势" class="chart-card">
          <template #extra>
            <a-select v-model:value="trendType" style="width: 120px" size="small">
              <a-select-option value="count">对话数</a-select-option>
              <a-select-option value="user">用户数</a-select-option>
            </a-select>
          </template>
          <!-- 趋势图 -->
          <div class="trend-chart">
            <div class="trend-y-axis">
              <span v-for="val in trendYAxis" :key="val">{{ val }}</span>
            </div>
            <div class="trend-content">
              <div class="trend-grid">
                <div v-for="i in 7" :key="i" class="grid-line"></div>
              </div>
              <div class="trend-line-wrapper">
                <svg class="trend-svg" viewBox="0 0 600 200" preserveAspectRatio="none">
                  <defs>
                    <linearGradient id="trendGradient" x1="0%" y1="0%" x2="0%" y2="100%">
                      <stop offset="0%" style="stop-color: #059669; stop-opacity: 0.3" />
                      <stop offset="100%" style="stop-color: #059669; stop-opacity: 0" />
                    </linearGradient>
                  </defs>
                  <path :d="trendAreaPath" fill="url(#trendGradient)" />
                  <path
                    :d="trendLinePath"
                    fill="none"
                    stroke="#059669"
                    stroke-width="2"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                  <template v-for="(point, index) in trendPoints" :key="index">
                    <circle
                      :cx="point.x"
                      :cy="point.y"
                      r="4"
                      fill="#059669"
                      stroke="#fff"
                      stroke-width="2"
                      class="trend-point"
                    />
                  </template>
                </svg>
              </div>
              <div class="trend-x-axis">
                <span v-for="label in trendXAxis" :key="label">{{ label }}</span>
              </div>
            </div>
          </div>
          <!-- 趋势统计 -->
          <div class="trend-stats">
            <div class="trend-stat-item">
              <span class="trend-stat-label">最高值</span>
              <span class="trend-stat-value">{{ maxTrendValue }}</span>
            </div>
            <div class="trend-stat-item">
              <span class="trend-stat-label">最低值</span>
              <span class="trend-stat-value">{{ minTrendValue }}</span>
            </div>
            <div class="trend-stat-item">
              <span class="trend-stat-label">平均值</span>
              <span class="trend-stat-value">{{ avgTrendValue }}</span>
            </div>
          </div>
        </a-card>
      </a-col>
      <a-col :xs="24" :lg="12">
        <a-card title="API调用成本" class="chart-card">
          <template #extra>
            <a-select v-model:value="costType" style="width: 120px" size="small">
              <a-select-option value="token">Token</a-select-option>
              <a-select-option value="cost">费用</a-select-option>
            </a-select>
          </template>
          <!-- 成本概览 -->
          <div class="cost-overview">
            <div class="cost-item">
              <div class="cost-label">总Token数</div>
              <div class="cost-value">{{ apiCost.totalTokens?.toLocaleString() }}</div>
            </div>
            <div class="cost-item">
              <div class="cost-label">总费用</div>
              <div class="cost-value highlight">¥{{ apiCost.totalCost?.toFixed(2) }}</div>
            </div>
          </div>
          <!-- 成本分布 -->
          <div class="cost-chart">
            <div class="cost-bar-item">
              <span class="cost-bar-label">今日Token</span>
              <div class="cost-bar-wrapper">
                <div class="cost-bar-fill" :style="{ width: todayTokenPercent + '%' }"></div>
              </div>
              <span class="cost-bar-value">{{ apiCost.todayTokens?.toLocaleString() }}</span>
            </div>
            <div class="cost-bar-item">
              <span class="cost-bar-label">今日费用</span>
              <div class="cost-bar-wrapper">
                <div class="cost-bar-fill cost" :style="{ width: todayCostPercent + '%' }"></div>
              </div>
              <span class="cost-bar-value">¥{{ apiCost.todayCost?.toFixed(2) }}</span>
            </div>
          </div>
          <!-- 模型统计 -->
          <div v-if="apiCost.modelStats && apiCost.modelStats.length > 0" class="model-stats">
            <div class="model-stat-title">模型调用统计</div>
            <div v-for="model in apiCost.modelStats" :key="model.name" class="model-stat-item">
              <span class="model-name">{{ model.name }}</span>
              <span class="model-tokens">{{ model.tokens?.toLocaleString() }} tokens</span>
              <span class="model-cost">¥{{ model.cost?.toFixed(2) }}</span>
            </div>
          </div>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import {
  FileOutlined,
  UserOutlined,
  MessageOutlined,
  RiseOutlined,
  ArrowUpOutlined,
  ArrowDownOutlined,
  ReloadOutlined,
} from '@ant-design/icons-vue'
import { getStatsOverview, getChatTrend, getApiCostStats, type StatsOverview, type ApiCostStats } from '@/api/stats'
import dayjs, { type Dayjs } from 'dayjs'

const timeRange = ref('week')
const customRange = ref<[Dayjs, Dayjs] | null>(null)
const trendType = ref('count')
const costType = ref('token')

const statsData = reactive([
  { label: '总文档数', value: 0, gradient: 'linear-gradient(135deg, #059669 0%, #228B22 100%)', trend: 12 },
  { label: '总用户数', value: 0, gradient: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)', trend: 8 },
  { label: '总对话数', value: 0, gradient: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)', trend: -5 },
  { label: '今日对话', value: 0, gradient: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)', trend: 15 },
])

const overview = reactive<StatsOverview>({ totalDocuments: 0, totalUsers: 0, totalChats: 0, todayChats: 0, totalQuestions: 0, todayQuestions: 0 })
const apiCost = reactive<ApiCostStats>({ totalTokens: 0, totalCost: 0, todayTokens: 0, todayCost: 0, modelStats: [] })

const trendData = ref<number[]>([])
const trendXAxis = ref<string[]>([])

const trendYAxis = computed(() => {
  if (trendData.value.length === 0) return [0, 0, 0, 0, 0]
  const max = Math.max(...trendData.value)
  return [max, Math.round(max * 0.75), Math.round(max * 0.5), Math.round(max * 0.25), 0]
})

const trendPoints = computed(() => {
  if (trendData.value.length === 0) return []
  const max = Math.max(...trendData.value) || 1
  const width = 600
  const height = 200
  const padding = 20
  return trendData.value.map((val, index) => ({
    x: padding + (index * (width - 2 * padding)) / (trendData.value.length - 1 || 1),
    y: height - padding - ((val / max) * (height - 2 * padding)),
  }))
})

const trendLinePath = computed(() => {
  return trendPoints.value.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x} ${p.y}`).join(' ')
})

const trendAreaPath = computed(() => {
  if (trendPoints.value.length === 0) return ''
  const height = 200
  const padding = 20
  const linePath = trendLinePath.value
  const lastPoint = trendPoints.value[trendPoints.value.length - 1]
  const firstPoint = trendPoints.value[0]
  return `${linePath} L ${lastPoint.x} ${height - padding} L ${firstPoint.x} ${height - padding} Z`
})

const maxTrendValue = computed(() => trendData.value.length > 0 ? Math.max(...trendData.value) : 0)
const minTrendValue = computed(() => trendData.value.length > 0 ? Math.min(...trendData.value) : 0)
const avgTrendValue = computed(() => trendData.value.length > 0 ? Math.round(trendData.value.reduce((a, b) => a + b, 0) / trendData.value.length) : 0)

const todayTokenPercent = computed(() => {
  if (!apiCost.totalTokens) return 0
  return Math.min((apiCost.todayTokens / apiCost.totalTokens) * 100, 100)
})

const todayCostPercent = computed(() => {
  if (!apiCost.totalCost) return 0
  return Math.min((apiCost.todayCost / apiCost.totalCost) * 100, 100)
})

function getDateRange(): { startDate: string; endDate: string } {
  const now = dayjs()
  const endDate = now.format('YYYY-MM-DD')
  let startDate = endDate

  if (timeRange.value === 'today') {
    startDate = endDate
  } else if (timeRange.value === 'week') {
    startDate = now.subtract(7, 'day').format('YYYY-MM-DD')
  } else if (timeRange.value === 'month') {
    startDate = now.subtract(30, 'day').format('YYYY-MM-DD')
  } else if (timeRange.value === 'custom' && customRange.value) {
    startDate = customRange.value[0].format('YYYY-MM-DD')
  }

  return { startDate, endDate }
}

function formatNumber(num: number): string {
  if (num >= 10000) return (num / 10000).toFixed(1) + 'w'
  return num.toLocaleString()
}

async function loadData() {
  const { startDate, endDate } = getDateRange()

  try {
    const [overviewRes, trendRes, costRes] = await Promise.all([
      getStatsOverview(),
      getChatTrend(startDate, endDate),
      getApiCostStats(startDate, endDate),
    ])

    if (overviewRes.data.data) {
      overview.totalDocuments = overviewRes.data.data.totalDocuments || 0
      overview.totalUsers = overviewRes.data.data.totalUsers || 0
      overview.totalChats = overviewRes.data.data.totalChats || 0
      overview.todayChats = overviewRes.data.data.todayChats || 0

      statsData[0].value = overview.totalDocuments
      statsData[1].value = overview.totalUsers
      statsData[2].value = overview.totalChats
      statsData[3].value = overview.todayChats
    }

    if (trendRes.data.data) {
      trendData.value = trendRes.data.data.map((item: any) => item.count)
      trendXAxis.value = trendRes.data.data.map((item: any) => item.date.slice(5))
    }

    if (costRes.data.data) {
      Object.assign(apiCost, costRes.data.data)
    }
  } catch (error) {
    console.error('加载数据失败:', error)
  }
}

function handleTimeChange() {
  loadData()
}

function handleCustomRangeChange() {
  if (customRange.value) handleTimeChange()
}

function handleRefresh() {
  loadData()
  message.success('数据刷新成功')
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.stats-container {
  height: calc(100vh - 64px - 48px);
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

// 筛选卡片
.filter-card {
  flex-shrink: 0;

  :deep(.ant-card-body) {
    padding: 16px 24px;
  }

  .filter-content {
    display: flex;
    justify-content: space-between;
    align-items: center;
    flex-wrap: wrap;
    gap: 16px;
  }

  .filter-left {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 12px;
  }

  .filter-label {
    font-weight: 500;
    color: var(--text-secondary);
    font-size: 14px;
  }
}

// 统计卡片
.stats-row {
  flex-shrink: 0;
  margin-bottom: 0;
}

.stat-card {
  transition: all var(--duration-normal) var(--ease-nature);
  animation: slideInUp 0.4s var(--ease-out) both;

  &:hover {
    transform: translateY(-2px);
    box-shadow: var(--shadow-card-hover);
  }

  :deep(.ant-card-body) {
    padding: 20px;
  }

  .stat-content {
    display: flex;
    align-items: center;
    gap: 16px;
  }

  .stat-icon {
    width: 52px;
    height: 52px;
    border-radius: var(--radius-lg);
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    flex-shrink: 0;

    .stat-icon-component {
      font-size: 24px;
    }
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
    margin-top: 4px;

    &.up {
      color: var(--success-color);
      background: var(--success-light);
    }

    &.down {
      color: var(--danger-color);
      background: var(--danger-light);
    }
  }
}

// 图表卡片
.chart-row {
  flex: 1;
  min-height: 0;
  margin-bottom: 0;
}

.chart-card {
  height: 100%;
  display: flex;
  flex-direction: column;

  :deep(.ant-card-head) {
    border-bottom: 1px solid var(--border-color);
    padding: 0 24px;
    min-height: auto;
    flex-shrink: 0;

    .ant-card-head-title {
      font-family: var(--font-serif);
      font-size: 16px;
      font-weight: 600;
      color: var(--text-primary);
      padding: 16px 0;
    }

    .ant-card-extra {
      padding: 16px 0;
    }
  }

  :deep(.ant-card-body) {
    padding: 16px 24px 24px;
    flex: 1;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }
}

// 趋势图
.trend-chart {
  display: flex;
  gap: 12px;
  padding: 20px 0;
  flex: 1;
  min-height: 200px;

  .trend-y-axis {
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    font-size: 12px;
    color: var(--text-tertiary);
    padding: 20px 0;
    width: 40px;
    text-align: right;
  }

  .trend-content {
    flex: 1;
    position: relative;
  }

  .trend-grid {
    position: absolute;
    top: 20px;
    left: 0;
    right: 0;
    bottom: 20px;
    display: flex;
    flex-direction: column;
    justify-content: space-between;

    .grid-line {
      height: 1px;
      background: var(--border-light);
    }
  }

  .trend-line-wrapper {
    position: relative;
    height: 200px;
    margin: 20px 0;
  }

  .trend-svg {
    width: 100%;
    height: 100%;
    display: block;
  }

  .trend-point {
    cursor: pointer;
    transition: r 0.2s ease;

    &:hover {
      r: 6;
    }
  }

  .trend-x-axis {
    display: flex;
    justify-content: space-between;
    font-size: 12px;
    color: var(--text-tertiary);
    padding-top: 8px;
  }
}

.trend-stats {
  display: flex;
  justify-content: space-around;
  padding: 16px;
  background: var(--bg-surface-secondary);
  border-radius: var(--radius-lg);
  margin-top: 16px;

  .trend-stat-item {
    text-align: center;
  }

  .trend-stat-label {
    display: block;
    font-size: 12px;
    color: var(--text-secondary);
    margin-bottom: 4px;
  }

  .trend-stat-value {
    display: block;
    font-size: 20px;
    font-weight: 600;
    color: var(--primary-color);
  }
}

// 成本概览
.cost-overview {
  display: flex;
  justify-content: space-around;
  padding: 16px;
  background: var(--bg-surface-secondary);
  border-radius: var(--radius-lg);
  margin-bottom: 16px;

  .cost-item {
    text-align: center;
  }

  .cost-label {
    font-size: 13px;
    color: var(--text-secondary);
    margin-bottom: 8px;
  }

  .cost-value {
    font-size: 24px;
    font-weight: 600;
    color: var(--text-primary);

    &.highlight {
      color: var(--primary-color);
    }
  }
}

// 成本图表
.cost-chart {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 16px;

  .cost-bar-item {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .cost-bar-label {
    width: 80px;
    font-size: 14px;
    color: var(--text-secondary);
    flex-shrink: 0;
  }

  .cost-bar-wrapper {
    flex: 1;
    height: 24px;
    background: var(--bg-surface-secondary);
    border-radius: var(--radius-md);
    overflow: hidden;
  }

  .cost-bar-fill {
    height: 100%;
    background: linear-gradient(90deg, #059669 0%, #228B22 100%);
    border-radius: var(--radius-md);
    transition: width 0.6s cubic-bezier(0.4, 0, 0.2, 1);

    &.cost {
      background: linear-gradient(90deg, #667eea 0%, #764ba2 100%);
    }
  }

  .cost-bar-value {
    width: 80px;
    text-align: right;
    font-size: 14px;
    font-weight: 500;
    color: var(--text-primary);
    flex-shrink: 0;
  }
}

// 模型统计
.model-stats {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--border-light);

  .model-stat-title {
    font-size: 14px;
    font-weight: 500;
    color: var(--text-primary);
    margin-bottom: 12px;
  }

  .model-stat-item {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 8px 0;

    .model-name {
      flex: 1;
      font-size: 13px;
      color: var(--text-primary);
    }

    .model-tokens {
      font-size: 12px;
      color: var(--text-secondary);
    }

    .model-cost {
      font-size: 13px;
      font-weight: 500;
      color: var(--primary-color);
    }
  }
}

// 动画
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

// 响应式
@media (max-width: 1200px) {
  .stats-row {
    :deep(.ant-col) {
      margin-bottom: 16px;
    }
  }
}

@media (max-width: 768px) {
  .filter-card {
    .filter-content {
      flex-direction: column;
      align-items: stretch;
    }

    .filter-left {
      flex-direction: column;
      align-items: stretch;
    }

    .filter-label {
      margin-bottom: 8px;
    }
  }

  .stat-card .stat-value {
    font-size: 24px;
  }

  .trend-chart {
    .trend-y-axis {
      width: 30px;
      font-size: 11px;
    }
  }
}
</style>
