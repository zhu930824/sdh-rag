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
              <component :is="stat.icon" class="stat-icon-component" />
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ formatNumber(stat.value) }}</div>
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
                  <circle
                    v-for="(point, index) in trendPoints"
                    :key="index"
                    :cx="point.x"
                    :cy="point.y"
                    r="4"
                    fill="#1890ff"
                    stroke="#fff"
                    stroke-width="2"
                    class="trend-point"
                  />
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
import { ref, computed, reactive } from 'vue'
import { message } from 'ant-design-vue'
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

// 时间范围
const timeRange = ref('week')
const customRange = ref<[Date, Date] | null>(null)
const loading = ref(false)
const searchKeyword = ref('')
const chartType = ref('bar')
const trendWord = ref('RAG技术')

// 统计数据
const statsData = reactive([
  {
    label: '总查询次数',
    value: 15680,
    icon: SearchOutlined,
    gradient: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    trend: 12.5,
  },
  {
    label: '独立词汇数',
    value: 2456,
    icon: TagsOutlined,
    gradient: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
    trend: 8.3,
  },
  {
    label: '平均查询次数',
    value: 6.4,
    icon: LineChartOutlined,
    gradient: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
    trend: -2.1,
  },
  {
    label: '环比增长',
    value: 15.8,
    icon: BarChartOutlined,
    gradient: 'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)',
    trend: 15.8,
  },
])

// 热点词排行数据
const topWords = ref([
  { word: 'RAG技术', count: 1256, percent: 100, trend: 15 },
  { word: '向量数据库', count: 986, percent: 78.5, trend: 12 },
  { word: '知识图谱', count: 856, percent: 68.2, trend: -5 },
  { word: '大语言模型', count: 724, percent: 57.6, trend: 8 },
  { word: 'Embedding', count: 658, percent: 52.4, trend: 20 },
  { word: '文档检索', count: 542, percent: 43.2, trend: -3 },
  { word: '语义理解', count: 486, percent: 38.7, trend: 6 },
  { word: '文本分块', count: 425, percent: 33.8, trend: 10 },
  { word: '提示工程', count: 398, percent: 31.7, trend: 25 },
  { word: 'Milvus', count: 356, percent: 28.3, trend: 18 },
])

// 趋势数据
const trendData = ref([120, 180, 150, 220, 280, 240, 320])
const trendXAxis = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
const trendYAxis = computed(() => {
  const max = Math.max(...trendData.value)
  return [max, Math.round(max * 0.75), Math.round(max * 0.5), Math.round(max * 0.25), 0]
})

// 计算趋势图路径
const trendPoints = computed(() => {
  const max = Math.max(...trendData.value)
  const width = 600
  const height = 200
  const padding = 20
  
  return trendData.value.map((val, index) => ({
    x: padding + (index * (width - 2 * padding)) / (trendData.value.length - 1),
    y: height - padding - ((val / max) * (height - 2 * padding)),
  }))
})

const trendLinePath = computed(() => {
  return trendPoints.value.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x} ${p.y}`).join(' ')
})

const trendAreaPath = computed(() => {
  const height = 200
  const padding = 20
  const linePath = trendLinePath.value
  const lastPoint = trendPoints.value[trendPoints.value.length - 1]
  const firstPoint = trendPoints.value[0]
  return `${linePath} L ${lastPoint.x} ${height - padding} L ${firstPoint.x} ${height - padding} Z`
})

const maxTrendValue = computed(() => Math.max(...trendData.value))
const minTrendValue = computed(() => Math.min(...trendData.value))
const avgTrendValue = computed(() => Math.round(trendData.value.reduce((a, b) => a + b, 0) / trendData.value.length))

// 表格列定义
const columns = [
  {
    title: '排名',
    key: 'rank',
    width: 100,
  },
  {
    title: '词汇',
    key: 'word',
    width: 200,
  },
  {
    title: '查询次数',
    key: 'count',
    width: 250,
    sorter: (a: WordItem, b: WordItem) => a.count - b.count,
  },
  {
    title: '占比',
    dataIndex: 'percent',
    key: 'percent',
    width: 100,
    customRender: ({ text }: { text: number }) => `${text.toFixed(1)}%`,
  },
  {
    title: '趋势',
    key: 'trend',
    width: 120,
    sorter: (a: WordItem, b: WordItem) => a.trend - b.trend,
  },
  {
    title: '操作',
    key: 'action',
    width: 100,
    fixed: 'right' as const,
  },
]

// 热点词列表数据
const wordList = ref<WordItem[]>([
  { id: 1, word: 'RAG技术', count: 1256, percent: 8.0, trend: 15, isNew: false },
  { id: 2, word: '向量数据库', count: 986, percent: 6.3, trend: 12, isNew: false },
  { id: 3, word: '知识图谱', count: 856, percent: 5.5, trend: -5, isNew: false },
  { id: 4, word: '大语言模型', count: 724, percent: 4.6, trend: 8, isNew: false },
  { id: 5, word: 'Embedding', count: 658, percent: 4.2, trend: 20, isNew: true },
  { id: 6, word: '文档检索', count: 542, percent: 3.5, trend: -3, isNew: false },
  { id: 7, word: '语义理解', count: 486, percent: 3.1, trend: 6, isNew: false },
  { id: 8, word: '文本分块', count: 425, percent: 2.7, trend: 10, isNew: false },
  { id: 9, word: '提示工程', count: 398, percent: 2.5, trend: 25, isNew: true },
  { id: 10, word: 'Milvus', count: 356, percent: 2.3, trend: 18, isNew: false },
  { id: 11, word: 'LangChain', count: 324, percent: 2.1, trend: 22, isNew: true },
  { id: 12, word: 'OpenAI', count: 298, percent: 1.9, trend: 5, isNew: false },
  { id: 13, word: 'Claude', count: 276, percent: 1.8, trend: 30, isNew: true },
  { id: 14, word: 'GPT-4', count: 258, percent: 1.6, trend: -8, isNew: false },
  { id: 15, word: 'Transformer', count: 234, percent: 1.5, trend: 3, isNew: false },
])

// 过滤后的列表
const filteredWordList = computed(() => {
  if (!searchKeyword.value) return wordList.value
  return wordList.value.filter(item =>
    item.word.toLowerCase().includes(searchKeyword.value.toLowerCase())
  )
})

// 分页配置
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: computed(() => filteredWordList.value.length),
  showSizeChanger: true,
  showQuickJumper: true,
  showTotal: (total: number) => `共 ${total} 条`,
})

interface WordItem {
  id: number
  word: string
  count: number
  percent: number
  trend: number
  isNew: boolean
}

// 格式化数字
function formatNumber(num: number): string {
  if (num >= 10000) {
    return (num / 10000).toFixed(1) + 'w'
  }
  return num.toLocaleString()
}

// 获取柱状图渐变色
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

// 获取词云样式
function getWordStyle(item: typeof topWords.value[0], index: number): Record<string, string> {
  const baseSize = 14
  const maxSize = 36
  const size = baseSize + (item.percent / 100) * (maxSize - baseSize)
  const colors = ['#667eea', '#f5576c', '#4facfe', '#43e97b', '#fa8c16', '#1890ff', '#722ed1']
  const color = colors[index % colors.length]
  
  return {
    fontSize: `${size}px`,
    color: color,
    fontWeight: index < 3 ? '600' : '400',
  }
}

// 获取排名颜色
function getRankColor(index: number): string {
  const colors = ['gold', 'silver', '#cd7f32']
  return colors[index] || 'default'
}

// 获取排名标签
function getRankLabel(index: number): string {
  const labels = ['TOP 1', 'TOP 2', 'TOP 3']
  return labels[index] || ''
}

// 获取进度条颜色
function getProgressColor(percent: number): string {
  if (percent >= 5) return '#52c41a'
  if (percent >= 2) return '#1890ff'
  return '#faad14'
}

// 时间范围变化
function handleTimeChange() {
  loading.value = true
  setTimeout(() => {
    loading.value = false
    message.success('数据已更新')
  }, 500)
}

// 自定义时间范围变化
function handleCustomRangeChange() {
  if (customRange.value) {
    handleTimeChange()
  }
}

// 刷新数据
function handleRefresh() {
  loading.value = true
  setTimeout(() => {
    loading.value = false
    message.success('数据刷新成功')
  }, 800)
}

// 搜索
function handleSearch() {
  pagination.current = 1
}

// 导出数据
function handleExport() {
  message.success('数据导出成功')
}

// 表格变化
function handleTableChange(pag: any) {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
}

// 查看详情
function handleViewDetail(record: WordItem) {
  message.info(`查看 ${record.word} 的详细数据`)
}
</script>

<style scoped lang="scss">
.hotwords-container {
  padding: 16px;
}

// 筛选卡片
.filter-card {
  margin-bottom: 20px;

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
    color: var(--text-primary);
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
      background: linear-gradient(45deg, transparent 30%, rgba(255, 255, 255, 0.3) 50%, transparent 70%);
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

// 图表卡片
.chart-row {
  margin-bottom: 20px;
}

.chart-card {
  margin-bottom: 20px;
  min-height: 400px;
}

// 柱状图
.bar-chart {
  .bar-item {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 16px;
    animation: slideInUp 0.3s ease-out both;

    &:last-child {
      margin-bottom: 0;
    }
  }

  .bar-rank {
    width: 24px;
    height: 24px;
    border-radius: 50%;
    background: var(--bg-page);
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 12px;
    font-weight: 600;
    color: var(--text-secondary);
    flex-shrink: 0;

    &.top-three {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
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
    background: var(--bg-page);
    border-radius: var(--border-radius-base);
    overflow: hidden;
    position: relative;
  }

  .bar-fill {
    height: 100%;
    border-radius: var(--border-radius-base);
    display: flex;
    align-items: center;
    justify-content: flex-end;
    padding-right: 8px;
    transition: width 0.6s cubic-bezier(0.4, 0, 0.2, 1);
    position: relative;

    &::after {
      content: '';
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: linear-gradient(90deg, transparent 0%, rgba(255, 255, 255, 0.2) 50%, transparent 100%);
      animation: shimmer 2s infinite;
    }
  }

  .bar-count {
    font-size: 12px;
    color: #fff;
    font-weight: 600;
    position: relative;
    z-index: 1;
  }
}

// 词云
.word-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  padding: 20px;
  justify-content: center;
  align-items: center;
  min-height: 300px;

  .word-item {
    display: inline-block;
    padding: 8px 16px;
    border-radius: var(--border-radius-round);
    background: var(--bg-page);
    transition: all 0.3s ease;
    cursor: default;

    &:hover {
      transform: scale(1.1);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
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
    color: var(--text-secondary);
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
      background: var(--border-lighter);
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
    color: var(--text-secondary);
    padding-top: 8px;
  }
}

.trend-stats {
  display: flex;
  justify-content: space-around;
  padding: 16px;
  background: var(--bg-page);
  border-radius: var(--border-radius-base);
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
  margin-bottom: 20px;
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
  border-radius: 12px;
  font-weight: 500;
  font-size: 13px;

  &.trend-up {
    color: var(--success-color);
    background: var(--success-light-9);
  }

  &.trend-down {
    color: var(--danger-color);
    background: var(--danger-light-9);
  }
}

// 响应式
@media (max-width: 768px) {
  .hotwords-container {
    padding: 12px;
  }

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
