<template>
  <div class="points-page">
    <div class="page-header">
      <h2>积分商城</h2>
    </div>

    <div class="points-info">
      <a-card :bordered="false">
        <a-row :gutter="24">
          <a-col :span="6">
            <a-statistic title="当前积分" :value="profile.pointsBalance" suffix="分">
              <template #prefix><DollarOutlined /></template>
            </a-statistic>
          </a-col>
          <a-col :span="6">
            <a-statistic title="累计积分" :value="profile.totalPoints" suffix="分" />
          </a-col>
          <a-col :span="6">
            <a-statistic title="用户等级" :value="profile.level" suffix="级" />
          </a-col>
          <a-col :span="6">
            <a-statistic title="经验值" :value="profile.experience" />
          </a-col>
        </a-row>
      </a-card>
    </div>

    <a-tabs v-model:activeKey="activeTab">
      <a-tab-pane key="goods" tab="兑换商品">
        <div class="goods-grid">
          <div v-for="goods in goodsList" :key="goods.id" class="goods-card">
            <div class="goods-image">
              <img v-if="goods.image" :src="goods.image" :alt="goods.name" />
              <GiftOutlined v-else />
            </div>
            <div class="goods-info">
              <div class="goods-name">{{ goods.name }}</div>
              <div class="goods-desc">{{ goods.description }}</div>
              <div class="goods-bottom">
                <span class="goods-points"><DollarOutlined /> {{ goods.points }}积分</span>
                <span class="goods-stock">库存: {{ goods.stock }}</span>
              </div>
            </div>
            <a-button
              type="primary"
              size="small"
              :disabled="profile.pointsBalance < goods.points || goods.stock <= 0"
              @click="handleExchange(goods)"
            >
              兑换
            </a-button>
          </div>
        </div>
      </a-tab-pane>

      <a-tab-pane key="records" tab="积分记录">
        <a-table :columns="recordColumns" :data-source="records" :loading="recordsLoading" :pagination="recordsPagination" row-key="id">
          <template #bodyCell="{ column, record }">
            <template v-if="column.dataIndex === 'points'">
              <span :class="{ 'points-add': record.points > 0, 'points-minus': record.points < 0 }">
                {{ record.points > 0 ? '+' : '' }}{{ record.points }}
              </span>
            </template>
          </template>
        </a-table>
      </a-tab-pane>

      <a-tab-pane key="exchanges" tab="兑换记录">
        <a-table :columns="exchangeColumns" :data-source="exchanges" :loading="exchangesLoading" :pagination="exchangesPagination" row-key="id">
          <template #bodyCell="{ column, record }">
            <template v-if="column.dataIndex === 'status'">
              <a-tag :color="getExchangeStatusColor(record.status)">{{ getExchangeStatusText(record.status) }}</a-tag>
            </template>
          </template>
        </a-table>
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { message } from 'ant-design-vue'
import { DollarOutlined, GiftOutlined } from '@ant-design/icons-vue'
import {
  getPointsProfile,
  getPointsRecords,
  getPointsGoods,
  exchangeGoods,
  getPointsExchanges,
  type UserProfile,
  type PointsRecord,
  type PointsGoods,
  type PointsExchange,
} from '@/api/points'

const activeTab = ref('goods')
const profile = reactive<UserProfile>({ id: 0, userId: 0, pointsBalance: 0, totalPoints: 0, level: 1, experience: 0 })
const goodsList = ref<PointsGoods[]>([])
const records = ref<PointsRecord[]>([])
const exchanges = ref<PointsExchange[]>([])
const recordsLoading = ref(false)
const exchangesLoading = ref(false)

const recordsPagination = reactive({ current: 1, pageSize: 10, total: 0 })
const exchangesPagination = reactive({ current: 1, pageSize: 10, total: 0 })

const recordColumns = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '类型', dataIndex: 'type', width: 100 },
  { title: '积分变动', dataIndex: 'points', width: 100 },
  { title: '余额', dataIndex: 'balance', width: 100 },
  { title: '描述', dataIndex: 'description', ellipsis: true },
  { title: '时间', dataIndex: 'createTime', width: 160 },
]

const exchangeColumns = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '商品ID', dataIndex: 'goodsId', width: 80 },
  { title: '消耗积分', dataIndex: 'points', width: 100 },
  { title: '状态', dataIndex: 'status', width: 80 },
  { title: '时间', dataIndex: 'createTime', width: 160 },
]

function getExchangeStatusColor(status: number): string {
  const map: Record<number, string> = { 0: 'orange', 1: 'blue', 2: 'green', 3: 'red' }
  return map[status] || 'default'
}

function getExchangeStatusText(status: number): string {
  const map: Record<number, string> = { 0: '待处理', 1: '已发货', 2: '已完成', 3: '已取消' }
  return map[status] || String(status)
}

async function loadProfile() {
  try {
    const res = await getPointsProfile()
    if (res.code === 200 && res.data) {
      Object.assign(profile, res.data)
    }
  } catch (error) {
    console.error('加载积分信息失败')
  }
}

async function loadGoods() {
  try {
    const res = await getPointsGoods({ page: 1, pageSize: 100 })
    if (res.code === 200) {
      goodsList.value = res.data?.records || []
    }
  } catch (error) {
    message.error('加载商品失败')
  }
}

async function loadRecords() {
  recordsLoading.value = true
  try {
    const res = await getPointsRecords({ page: recordsPagination.current, pageSize: recordsPagination.pageSize })
    if (res.code === 200) {
      records.value = res.data?.records || []
      recordsPagination.total = res.data?.total || 0
    }
  } finally {
    recordsLoading.value = false
  }
}

async function loadExchanges() {
  exchangesLoading.value = true
  try {
    const res = await getPointsExchanges({ page: exchangesPagination.current, pageSize: exchangesPagination.pageSize })
    if (res.code === 200) {
      exchanges.value = res.data?.records || []
      exchangesPagination.total = res.data?.total || 0
    }
  } finally {
    exchangesLoading.value = false
  }
}

async function handleExchange(goods: PointsGoods) {
  try {
    await exchangeGoods(goods.id)
    message.success('兑换成功')
    loadProfile()
    loadGoods()
  } catch (error: any) {
    message.error(error?.response?.data?.message || '兑换失败')
  }
}

watch(activeTab, (val) => {
  if (val === 'records') loadRecords()
  if (val === 'exchanges') loadExchanges()
})

onMounted(() => {
  loadProfile()
  loadGoods()
})
</script>

<style scoped lang="scss">
.points-page {
  padding: 24px;

  .page-header {
    margin-bottom: 24px;
    h2 { margin: 0; font-size: 20px; font-weight: 600; }
  }

  .points-info {
    margin-bottom: 24px;
  }

  .goods-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
    gap: 16px;
    margin-top: 16px;
  }

  .goods-card {
    background-color: var(--bg-color);
    border: 1px solid var(--border-lighter);
    border-radius: var(--border-radius-base);
    padding: 16px;
    display: flex;
    flex-direction: column;
    align-items: center;

    .goods-image {
      width: 80px;
      height: 80px;
      background-color: var(--bg-page);
      border-radius: var(--border-radius-base);
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 12px;
      font-size: 32px;
      color: var(--primary-color);
      img { width: 100%; height: 100%; object-fit: contain; }
    }

    .goods-info {
      text-align: center;
      flex: 1;

      .goods-name { font-weight: 500; margin-bottom: 4px; }
      .goods-desc { font-size: 12px; color: var(--text-secondary); margin-bottom: 8px; }
      .goods-bottom {
        display: flex;
        justify-content: space-between;
        font-size: 12px;
        .goods-points { color: var(--primary-color); font-weight: 500; }
        .goods-stock { color: var(--text-secondary); }
      }
    }
  }

  .points-add { color: var(--success-color); }
  .points-minus { color: var(--danger-color); }
}
</style>