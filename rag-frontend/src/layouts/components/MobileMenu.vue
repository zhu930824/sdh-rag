<template>
  <nav class="mobile-nav">
    <a-menu
      v-model:selectedKeys="selectedKeys"
      v-model:openKeys="openKeys"
      mode="inline"
      class="mobile-menu"
      @click="handleClick"
    >
      <!-- Core -->
      <a-menu-item key="/dashboard">
        <template #icon><HomeOutlined /></template>
        <span>首页</span>
      </a-menu-item>

      <a-menu-item key="/knowledge-base">
        <template #icon><FolderOutlined /></template>
        <span>知识库</span>
      </a-menu-item>

      <a-menu-item key="/knowledge">
        <template #icon><FileTextOutlined /></template>
        <span>文档管理</span>
      </a-menu-item>

      <a-menu-item key="/chat">
        <template #icon><MessageOutlined /></template>
        <span>智能问答</span>
      </a-menu-item>

      <a-menu-item key="/graph">
        <template #icon><ApartmentOutlined /></template>
        <span>知识图谱</span>
      </a-menu-item>

      <div class="menu-divider" />

      <!-- Tools -->
      <a-menu-item key="/workflow">
        <template #icon><ForkOutlined /></template>
        <span>工作流编排</span>
      </a-menu-item>

      <a-menu-item key="/model">
        <template #icon><ApiOutlined /></template>
        <span>大模型管理</span>
      </a-menu-item>

      <div class="menu-divider" />

      <!-- Analysis -->
      <a-menu-item key="/stats">
        <template #icon><BarChartOutlined /></template>
        <span>数据统计</span>
      </a-menu-item>

      <a-menu-item key="/hotwords">
        <template #icon><LineChartOutlined /></template>
        <span>热点词分析</span>
      </a-menu-item>

      <a-menu-item key="/feedback">
        <template #icon><LikeOutlined /></template>
        <span>问答评价</span>
      </a-menu-item>

      <div class="menu-divider" />

      <!-- More -->
      <a-menu-item key="/process-task">
        <template #icon><CloudUploadOutlined /></template>
        <span>文档预处理</span>
      </a-menu-item>

      <a-menu-item key="/tag">
        <template #icon><TagsOutlined /></template>
        <span>标签管理</span>
      </a-menu-item>

      <a-menu-item key="/voice">
        <template #icon><AudioOutlined /></template>
        <span>语音问答</span>
      </a-menu-item>

      <div class="menu-divider" />

      <!-- System -->
      <a-sub-menu key="system">
        <template #icon><SettingOutlined /></template>
        <template #title>系统管理</template>
        <a-menu-item key="/user">
          <UserOutlined />
          <span>用户管理</span>
        </a-menu-item>
        <a-menu-item key="/log">
          <FileTextOutlined />
          <span>日志管理</span>
        </a-menu-item>
        <a-menu-item key="/sensitive">
          <WarningOutlined />
          <span>敏感词管理</span>
        </a-menu-item>
        <a-menu-item key="/announcement">
          <NotificationOutlined />
          <span>公告管理</span>
        </a-menu-item>
        <a-menu-item key="/settings">
          <SettingOutlined />
          <span>系统设置</span>
        </a-menu-item>
      </a-sub-menu>
    </a-menu>
  </nav>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  HomeOutlined,
  FolderOutlined,
  MessageOutlined,
  UserOutlined,
  SettingOutlined,
  FileTextOutlined,
  WarningOutlined,
  LineChartOutlined,
  ApiOutlined,
  ForkOutlined,
  ApartmentOutlined,
  BarChartOutlined,
  LikeOutlined,
  NotificationOutlined,
  CloudUploadOutlined,
  TagsOutlined,
  AudioOutlined,
} from '@ant-design/icons-vue'

const emit = defineEmits<{
  navigate: []
}>()

const route = useRoute()
const router = useRouter()

const openKeys = ref<string[]>([])

const subMenuMap: Record<string, string[]> = {
  'system': ['/user', '/log', '/sensitive', '/announcement', '/settings']
}

function updateOpenKeys(path: string): void {
  for (const [key, paths] of Object.entries(subMenuMap)) {
    if (paths.includes(path)) {
      if (!openKeys.value.includes(key)) {
        openKeys.value = [key]
      }
      return
    }
  }
}

const selectedKeys = computed({
  get: () => [route.path],
  set: () => {},
})

function handleClick({ key }: { key: string }): void {
  router.push(key)
  emit('navigate')
}

watch(
  () => route.path,
  (path) => {
    updateOpenKeys(path)
  },
  { immediate: true }
)
</script>

<style scoped lang="scss">
.mobile-nav {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.mobile-menu {
  background: transparent;
  border: none;

  :deep(.ant-menu-item),
  :deep(.ant-menu-submenu-title) {
    height: 48px;
    line-height: 48px;
    margin: 2px 12px;
    padding: 0 16px !important;
    border-radius: var(--radius-lg);
    font-weight: 500;
    color: var(--text-primary);

    &:hover {
      background: var(--bg-surface-secondary);
    }

    .anticon {
      font-size: 18px;
      margin-right: 12px;
    }
  }

  :deep(.ant-menu-item-selected) {
    background: var(--primary-lighter) !important;
    color: var(--primary-color) !important;
  }

  :deep(.ant-menu-sub) {
    background: transparent;

    .ant-menu-item {
      padding-left: 48px !important;
      height: 44px;
      line-height: 44px;
    }
  }
}

.menu-divider {
  height: 1px;
  background: var(--border-color);
  margin: 8px 16px;
}
</style>