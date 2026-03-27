<template>
  <div class="stats-page">
    <div class="page-header">
      <h2>数据统计</h2>
      <p class="description">查看系统使用情况和API调用成本</p>
    </div>

    <div class="overview-cards">
      <a-row :gutter="16">
        <a-col :span="6">
          <a-card :bordered="false">
            <a-statistic title="总文档数" :value="overview.totalDocuments">
              <template #prefix><FileOutlined /></template>
            </a-statistic>
          </a-card>
        </a-col>
        <a-col :span="6">
          <a-card :bordered="false">
            <a-statistic title="总用户数" :value="overview.totalUsers">
              <template #prefix><UserOutlined /></template>
            </a-statistic>
          </a-card>
        </a-col>
        <a-col :span="6">
          <a-card :bordered="false">
            <a-statistic title="总对话数" :value="overview.totalChats">
              <template #prefix><MessageOutlined /></template>
            </a-statistic>
          </a-card>
        </a-col>
        <a-col :span="6">
          <a-card :bordered="false">
            <a-statistic title="今日对话" :value="overview.todayChats">
              <template #prefix><RiseOutlined /></template>
            </a-statistic>
          </a-card>
        </a-col>
      </a-row>
    </div>

    <div class="chart-section">
      <a-card title="对话趋势" :bordered="false">
        <template #extra>
          <a-range-picker
            v-model:value="dateRange"
            format="YYYY-MM-DD"
            @change="loadChatTrend"
          />
        </template>
        <div ref="chartRef" class="chart-container"></div>
      </a-card>
    </div>

    <div class="cost-section">
      <a-card title="API调用成本" :bordered="false">
        <a-descriptions :column="2">
          <a-descriptions-item label="总Token数">{{ apiCost.totalTokens?.toLocaleString() }}</a-descriptions-item>
          <a-descriptions-item label="总费用">¥{{ apiCost.totalCost?.toFixed(2) }}</a-descriptions-item>
        </a-descriptions>
      </a-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, nextTick } from 'vue'
import { FileOutlined, UserOutlined, MessageOutlined, RiseOutlined } from '@ant-design/icons-vue'
import { getStatsOverview, getChatTrend, getApiCostStats, type StatsOverview, type ApiCostStats } from '@/api/stats'
import dayjs from 'dayjs'

const chartRef = ref<HTMLElement>()
const dateRange = ref<[string, string]>(['', ''])
const overview = reactive<StatsOverview>({ totalDocuments: 0, totalUsers: 0, totalChats: 0, todayChats: 0, totalQuestions: 0, todayQuestions: 0 })
const apiCost = reactive<ApiCostStats>({ totalTokens: 0, totalCost: 0, todayTokens: 0, todayCost: 0, modelStats: [] })

function initDateRange() {
  const end = dayjs()
  const start = end.subtract(7, 'day')
  dateRange.value = [start.format('YYYY-MM-DD'), end.format('YYYY-MM-DD')]
}

async function loadOverview() {
  try {
    const res = await getStatsOverview()
    if (res.code === 200 && res.data) {
      Object.assign(overview, res.data)
    }
  } catch (error) {
    console.error('加载概览数据失败')
  }
}

async function loadChatTrend() {
  if (!dateRange.value[0] || !dateRange.value[1]) return
  
  try {
    const res = await getChatTrend(dateRange.value[0], dateRange.value[1])
    if (res.code === 200 && res.data) {
      // 简化实现，实际应使用图表库如 ECharts
      renderChart(res.data)
    }
  } catch (error) {
    console.error('加载趋势数据失败')
  }
}

async function loadApiCost() {
  if (!dateRange.value[0] || !dateRange.value[1]) return
  
  try {
    const res = await getApiCostStats(dateRange.value[0], dateRange.value[1])
    if (res.code === 200 && res.data) {
      Object.assign(apiCost, res.data)
    }
  } catch (error) {
    console.error('加载API成本失败')
  }
}

function renderChart(data: any[]) {
  if (!chartRef.value || !data.length) return
  
  // 清空容器
  chartRef.value.innerHTML = ''
  
  // 简化实现：显示数据表格
  const table = document.createElement('table')
  table.style.width = '100%'
  table.style.borderCollapse = 'collapse'
  
  data.forEach((item: any) => {
    const row = document.createElement('tr')
    row.innerHTML = `<td style="padding: 8px; border: 1px solid #eee;">${item.date}</td><td style="padding: 8px; border: 1px solid #eee;">${item.count}</td>`
    table.appendChild(row)
  })
  
  chartRef.value.appendChild(table)
}

onMounted(() => {
  initDateRange()
  loadOverview()
  loadChatTrend()
  loadApiCost()
})
</script>

<style scoped lang="scss">
.stats-page {
  padding: 24px;

  .page-header {
    margin-bottom: 24px;
    h2 { margin: 0 0 8px; font-size: 20px; font-weight: 600; }
    .description { color: var(--text-secondary); font-size: 14px; }
  }

  .overview-cards {
    margin-bottom: 24px;
  }

  .chart-section {
    margin-bottom: 24px;

    .chart-container {
      min-height: 300px;
    }
  }
}
</style>