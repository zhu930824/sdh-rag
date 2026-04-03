<template>
  <div class="hotwords-container">
    <!-- 时间筛选 -->
    <a-card class="filter-card animate-slide-down">
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
          class="stat-card card-hover animate-slide-up"
          :style="{ animationDelay: `${index * 0.1}s` }"
        >
          <div class="stat-content">
            <div class="stat-icon" :style="{ background: stat.gradient }">
              <SearchOutlined v-if="stat.label === '总查询次数'" class="stat-icon-component" />
              <TagsOutlined v-else-if="stat.label === '独立词汇数'" class="stat-icon-component" />
              <LineChartOutlined v-else-if="stat.label === '平均查询次数'" class="stat-icon-component" />
              <BarChartOutlined v-else class="stat-icon-component" />
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
        <a-card title="热点词排行" class="chart-card animate-slide-left" style="animation-delay: 0.3s">
          <template #extra>
            <a-radio-group v-model:value="chartType" size="small">
              <a-radio-button value="bar">柱状图</a-radio-button>
              <a-radio-button value="word">词云</a-radio-button>
            </a-radio-group>
          </template>
          <!-- 柱状图 -->
          <div v-if="chartType === 'bar'" class="bar-chart">
            <div v-for="(item, index) in topWords" :key="item.word" class="bar-item">
              <span class="bar-rank" :class="{ 'top-three': index < 3 }">{{ index + 1 }}</span>
              <span class="bar-label">{{ item.word }}</span>
              <div class="bar-wrapper">
                <div
                  class="bar-fill"
                  :style="{
                    width: `${item.percent}%`,
                    background: getBarGradient(index)
                  }"
                >
                  <span class="bar-count">{{ item.count }}</span>
                </div>
              </div>
            </div>
          </div>
          <!-- 词云效果 -->
          <div v-else class="word-cloud">
            <span
              v-for="(item, index) in topWords"
              :key="item.word"
              class="word-item"
              :style="getWordStyle(item, index)"
            >
              {{ item.word }}
            </span>
          </div>
        </a-card>
      </a-col>
      <a-col :xs="24" :lg="12">
        <a-card title="查询趋势" class="chart-card animate-slide-right" style="animation-delay: 0.3s">
          <template #extra>
            <a-select v-model:value="trendWord" style="width: 150px" size="small">
              <a-select-option v-for="word in topWords.slice(0, 5)" :key="word.word" :value="word.word">
                {{ word.word }}
              </a-select-option>
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
                      <stop offset="0%" style="stop-color: #1890ff; stop-opacity: 0.3" />
                      <stop offset="100%" style="stop-color: #1890ff; stop-opacity: 0" />
                    </linearGradient>
                  </defs>
                  <!-- 面积图 -->
                  <path
                    :d="trendAreaPath"
                    fill="url(#trendGradient)"
                  />
                  <!-- 折线 -->
                  <path
                    :d="trendLinePath"
                    fill="none"
                    stroke="#1890ff"
                    stroke-width="2"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                  <!-- 数据点 -->
                  <template v-for="(point, index) in trendPoints" :key="index">
                    <circle
                      :cx="point.x"
                      :cy="point.y"
                      r="4"
                      fill="#1890ff"
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
    </a-row>

    <!-- 热点词列表 -->
    <a-card title="热点词详情" class="table-card animate-slide-up" style="animation-delay: 0.4s">
      <template #extra>
        <a-input-search
          v-model:value="searchKeyword"
          placeholder="搜索词汇"
          style="width: 200px"
          @search="handleSearch"
        />
        <a-button style="margin-left: 8px" @click="handleExport">
          <template #icon><ExportOutlined /></template>
          导出数据
        </a-button>
      </template>
      <a-table
        :columns="columns"
        :data-source="filteredWordList"
        :loading="loading"
        :pagination="pagination"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record, index }">
          <template v-if="column.key === 'rank'">
            <div class="rank-cell" :class="{ 'top-rank': index < 3 }">
              <span class="rank-number">{{ index + 1 }}</span>
              <a-tag v-if="index < 3" :color="getRankColor(index)" size="small">
                {{ getRankLabel(index) }}
              </a-tag>
            </div>
          </template>
          <template v-if="column.key === 'word'">
            <div class="word-cell">
              <span class="word-text">{{ record.word }}</span>
              <a-tag v-if="record.isNew" color="red" size="small">新</a-tag>
            </div>
          </template>
          <template v-if="column.key === 'count'">
            <div class="count-cell">
              <a-progress
                :percent="record.percent"
                :show-info="false"
                :stroke-color="getProgressColor(record.percent)"
                size="small"
              />
              <span class="count-value">{{ record.count }}</span>
            </div>
          </template>
          <template v-if="column.key === 'trend'">
            <span :class="record.trend > 0 ? 'trend-up' : 'trend-down'" class="trend-cell">
              <ArrowUpOutlined v-if="record.trend > 0" />
              <ArrowDownOutlined v-else />
              {{ Math.abs(record.trend) }}%
            </span>
          </template>
          <template v-if="column.key === 'action'">
            <a-button type="link" size="small" @click="handleViewDetail(record)">
              查看详情
            </a-button>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import dayjs, { type Dayjs } from 'dayjs'
import {
  SearchOutlined,
  TagsOutlined,
  LineChartOutlined,
  ArrowUpOutlined,
  ArrowDownOutlined,
  ReloadOutlined,
  ExportOutlined,
  FireOutlined,
  ThunderboltOutlined,
  BarChartOutlined,
} from '@ant-design/icons-vue'
import { getHotwordStats, getHotwordRanking, getHotwordTrend, getHotwordList } from '@/api/hotwords'
import type { WordRankItem, TrendItem } from '@/api/hotwords'

const timeRange = ref('week')
const customRange = ref<[Dayjs, Dayjs] | null>(null)
const loading = ref(false)
const searchKeyword = ref('')
const chartType = ref('bar')
const trendWord = ref('')

const statsData = reactive([
  { label: '总查询次数', value: 0, gradient: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)', trend: 0 },
  { label: '独立词汇数', value: 0, gradient: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)', trend: 0 },
  { label: '平均查询次数', value: 0, gradient: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)', trend: 0 },
  { label: '环比增长', value: 0, gradient: 'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)', trend: 0 },
])

const topWords = ref<WordRankItem[]>([])
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

const columns = [
  { title: '排名', key: 'rank', width: 100 },
  { title: '词汇', key: 'word', width: 200 },
  { title: '查询次数', key: 'count', width: 250, sorter: (a: WordRankItem, b: WordRankItem) => a.count - b.count },
  { title: '占比', dataIndex: 'percent', key: 'percent', width: 100, customRender: ({ text }: { text: number }) => `${text.toFixed(1)}%` },
  { title: '趋势', key: 'trend', width: 120, sorter: (a: WordRankItem, b: WordRankItem) => (a.trend || 0) - (b.trend || 0) },
  { title: '操作', key: 'action', width: 100, fixed: 'right' as const },
]

const wordList = ref<WordRankItem[]>([])

const filteredWordList = computed(() => {
  if (!searchKeyword.value) return wordList.value
  return wordList.value.filter(item => item.word.toLowerCase().includes(searchKeyword.value.toLowerCase()))
})

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: computed(() => filteredWordList.value.length),
  showSizeChanger: true,
  showQuickJumper: true,
  showTotal: (total: number) => `共 ${total} 条`,
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

async function loadData() {
  loading.value = true
  const { startDate, endDate } = getDateRange()
  
  try {
    const [statsRes, rankingRes, listRes] = await Promise.all([
      getHotwordStats(startDate, endDate),
      getHotwordRanking(10, startDate, endDate),
      getHotwordList({ page: 1, pageSize: 50, startDate, endDate }),
    ])
    
    if (statsRes.data.data) {
      statsData[0].value = statsRes.data.data.totalQueries || 0
      statsData[1].value = statsRes.data.data.uniqueWords || 0
      statsData[2].value = statsRes.data.data.avgQueries || 0
    }
    
    topWords.value = rankingRes.data.data || []
    wordList.value = listRes.data.data || []
    
    if (topWords.value.length > 0 && !trendWord.value) {
      trendWord.value = topWords.value[0].word
      loadTrendData()
    }
  } catch (error) {
    console.error('加载热点词数据失败:', error)
  } finally {
    loading.value = false
  }
}

async function loadTrendData() {
  if (!trendWord.value) return
  
  const { startDate, endDate } = getDateRange()
  try {
    const { data } = await getHotwordTrend(trendWord.value, startDate, endDate)
    const trendItems = data.data || []
    trendData.value = trendItems.map((item: TrendItem) => item.count)
    trendXAxis.value = trendItems.map((item: TrendItem) => item.date.slice(5))
  } catch (error) {
    console.error('加载趋势数据失败:', error)
  }
}

function formatNumber(num: number): string {
  if (num >= 10000) return (num / 10000).toFixed(1) + 'w'
  return num.toLocaleString()
}

function getBarGradient(index: number): string {
  const gradients = [
    'linear-gradient(90deg, #667eea 0%, #764ba2 100%)',
    'linear-gradient(90deg, #f093fb 0%, #f5576c 100%)',
    'linear-gradient(90deg, #4facfe 0%, #00f2fe 100%)',
    'linear-gradient(90deg, #43e97b 0%, #38f9d7 100%)',
    'linear-gradient(90deg, #fa8c16 0%, #fadb14 100%)',
  ]
  return gradients[index % gradients.length]
}

function getWordStyle(item: WordRankItem, index: number): Record<string, string> {
  const baseSize = 14
  const maxSize = 36
  const size = baseSize + ((item.percent || 0) / 100) * (maxSize - baseSize)
  const colors = ['#667eea', '#f5576c', '#4facfe', '#43e97b', '#fa8c16', '#1890ff', '#722ed1']
  const color = colors[index % colors.length]
  return { fontSize: `${size}px`, color: color, fontWeight: index < 3 ? '600' : '400' }
}

function getRankColor(index: number): string {
  const colors = ['gold', 'silver', '#cd7f32']
  return colors[index] || 'default'
}

function getRankLabel(index: number): string {
  const labels = ['TOP 1', 'TOP 2', 'TOP 3']
  return labels[index] || ''
}

function getProgressColor(percent: number): string {
  if (percent >= 5) return '#52c41a'
  if (percent >= 2) return '#1890ff'
  return '#faad14'
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

function handleSearch() {
  pagination.current = 1
}

function handleExport() {
  message.success('数据导出成功')
}

function handleTableChange(pag: any) {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
}

function handleViewDetail(record: WordRankItem) {
  trendWord.value = record.word
  loadTrendData()
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.hotwords-container {
  height: calc(100vh - 64px - 48px);
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding-bottom: 20px;
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
  flex-shrink: 0;
  margin-bottom: 0;
}

.chart-card {
  height: 100%;
  min-height: 400px;
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

// 柱状图
.bar-chart {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-between;

  .bar-item {
    display: flex;
    align-items: center;
    gap: 12px;
    animation: slideInUp 0.3s var(--ease-out) both;
  }

  .bar-rank {
    width: 24px;
    height: 24px;
    border-radius: 50%;
    background: var(--bg-surface-secondary);
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 12px;
    font-weight: 600;
    color: var(--text-secondary);
    flex-shrink: 0;

    &.top-three {
      background: var(--primary-color);
      color: #fff;
    }
  }

  .bar-label {
    width: 100px;
    font-size: 14px;
    color: var(--text-primary);
    font-weight: 500;
    flex-shrink: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .bar-wrapper {
    flex: 1;
    height: 28px;
    background: var(--bg-surface-secondary);
    border-radius: var(--radius-md);
    overflow: hidden;
  }

  .bar-fill {
    height: 100%;
    border-radius: var(--radius-md);
    display: flex;
    align-items: center;
    justify-content: flex-end;
    padding-right: 8px;
    transition: width 0.6s cubic-bezier(0.4, 0, 0.2, 1);
  }

  .bar-count {
    font-size: 12px;
    color: #fff;
    font-weight: 600;
  }
}

// 词云
.word-cloud {
  flex: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  padding: 20px;
  justify-content: center;
  align-items: center;

  .word-item {
    display: inline-block;
    padding: 8px 16px;
    border-radius: var(--radius-full);
    background: var(--bg-surface-secondary);
    transition: all var(--duration-normal) var(--ease-nature);
    cursor: default;

    &:hover {
      transform: scale(1.1);
      background: var(--primary-lighter);
      color: var(--primary-color);
    }
  }
}

// 趋势图
.trend-chart {
  display: flex;
  gap: 12px;
  padding: 20px 0;
  min-height: 280px;

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

// 表格卡片
.table-card {
  flex-shrink: 0;

  :deep(.ant-card-head) {
    border-bottom: 1px solid var(--border-color);
    padding: 0 24px;
    min-height: auto;

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
  }
}

.rank-cell {
  display: flex;
  align-items: center;
  gap: 8px;

  .rank-number {
    font-weight: 600;
    color: var(--text-primary);
  }

  &.top-rank .rank-number {
    color: var(--primary-color);
  }
}

.word-cell {
  display: flex;
  align-items: center;
  gap: 8px;

  .word-text {
    font-weight: 500;
    color: var(--text-primary);
  }
}

.count-cell {
  display: flex;
  align-items: center;
  gap: 12px;

  .ant-progress {
    flex: 1;
  }

  .count-value {
    min-width: 50px;
    text-align: right;
    font-weight: 500;
    color: var(--text-primary);
  }
}

.trend-cell {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  border-radius: var(--radius-full);
  font-weight: 500;
  font-size: 13px;

  &.trend-up {
    color: var(--success-color);
    background: var(--success-light);
  }

  &.trend-down {
    color: var(--danger-color);
    background: var(--danger-light);
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

  .bar-chart {
    .bar-label {
      width: 80px;
      font-size: 13px;
    }
  }

  .trend-chart {
    .trend-y-axis {
      width: 30px;
      font-size: 11px;
    }
  }
}
</style>
