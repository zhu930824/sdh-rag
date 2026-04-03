<template>
  <div v-if="announcements.length > 0" class="announcement-marquee">
    <div class="marquee-icon">
      <SoundOutlined />
    </div>
    <div class="marquee-wrapper" ref="wrapperRef">
      <div
        class="marquee-content"
        ref="contentRef"
        :style="{ transform: `translateX(${-offset}px)` }"
      >
        <!-- 渲染两份内容实现无缝滚动 -->
        <template v-for="dup in 2" :key="dup">
          <span
            v-for="(item, index) in announcements"
            :key="`${dup}-${item.id}`"
            class="marquee-item"
            @click="handleClick(item)"
          >
            <a-tag :color="getTypeColor(item.type)" size="small">{{ getTypeText(item.type) }}</a-tag>
            <a-tag v-if="item.isTop" color="red" size="small">置顶</a-tag>
            <span class="item-title">{{ item.title }}</span>
            <span v-if="index < announcements.length - 1" class="item-divider">|</span>
          </span>
          <span v-if="dup === 1" class="item-divider" style="padding: 0 40px;">|</span>
        </template>
      </div>
    </div>
    <div class="marquee-actions">
      <a-button type="text" size="small" class="action-btn" @click="togglePause">
        <PauseCircleOutlined v-if="!isPaused" />
        <PlayCircleOutlined v-else />
      </a-button>
      <a-button type="text" size="small" class="action-btn close-btn" @click="handleClose">
        <CloseOutlined />
      </a-button>
    </div>

    <!-- 详情弹窗 -->
    <a-modal
      v-model:open="detailVisible"
      :title="null"
      :footer="null"
      :width="520"
      class="announcement-modal"
    >
      <template v-if="currentAnnouncement">
        <div class="modal-header">
          <div class="modal-tags">
            <a-tag :color="getTypeColor(currentAnnouncement.type)">
              {{ getTypeText(currentAnnouncement.type) }}
            </a-tag>
            <a-tag v-if="currentAnnouncement.isTop" color="red">置顶</a-tag>
          </div>
          <h3 class="modal-title">{{ currentAnnouncement.title }}</h3>
          <div class="modal-time">
            <ClockCircleOutlined />
            {{ currentAnnouncement.publishTime }}
          </div>
        </div>
        <a-divider style="margin: 16px 0" />
        <div class="modal-content">
          {{ currentAnnouncement.content }}
        </div>
        <div class="modal-footer">
          <a-button type="primary" @click="handleMarkRead">我知道了</a-button>
        </div>
      </template>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import {
  SoundOutlined,
  CloseOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  ClockCircleOutlined,
} from '@ant-design/icons-vue'
import { getActiveAnnouncements, markAnnouncementRead, type Announcement } from '@/api/announcement'

const announcements = ref<Announcement[]>([])
const offset = ref(0)
const isPaused = ref(false)
const detailVisible = ref(false)
const currentAnnouncement = ref<Announcement | null>(null)
const wrapperRef = ref<HTMLElement | null>(null)
const contentRef = ref<HTMLElement | null>(null)

let animationFrame: number | null = null
let lastTime: number = 0
const speed = 50 // pixels per second
let contentWidth = 0

function getTypeColor(type: string): string {
  const map: Record<string, string> = {
    notice: 'blue',
    update: 'green',
    warning: 'orange',
  }
  return map[type] || 'default'
}

function getTypeText(type: string): string {
  const map: Record<string, string> = {
    notice: '通知',
    update: '更新',
    warning: '警告',
  }
  return map[type] || type
}

async function loadAnnouncements() {
  try {
    const res = await getActiveAnnouncements()
    if (res.code === 200 && res.data) {
      announcements.value = res.data
      if (announcements.value.length > 0) {
        nextTick(() => {
          // 计算单份内容的宽度
          if (contentRef.value) {
            contentWidth = contentRef.value.scrollWidth / 2
          }
          startAnimation()
        })
      }
    }
  } catch (error) {
    console.error('加载公告失败:', error)
  }
}

function animate(currentTime: number) {
  if (isPaused.value) {
    lastTime = currentTime
    animationFrame = requestAnimationFrame(animate)
    return
  }

  if (!lastTime) lastTime = currentTime
  const deltaTime = currentTime - lastTime
  lastTime = currentTime

  offset.value += (speed * deltaTime) / 1000

  // 当滚动超过单份内容宽度时重置
  if (offset.value >= contentWidth) {
    offset.value = 0
  }

  animationFrame = requestAnimationFrame(animate)
}

function startAnimation() {
  if (animationFrame) {
    cancelAnimationFrame(animationFrame)
  }
  lastTime = 0
  offset.value = 0
  animationFrame = requestAnimationFrame(animate)
}

function stopAnimation() {
  if (animationFrame) {
    cancelAnimationFrame(animationFrame)
    animationFrame = null
  }
}

function togglePause() {
  isPaused.value = !isPaused.value
}

function handleClose() {
  stopAnimation()
  announcements.value = []
}

function handleClick(item: Announcement) {
  currentAnnouncement.value = item
  detailVisible.value = true
}

async function handleMarkRead() {
  if (currentAnnouncement.value) {
    try {
      await markAnnouncementRead(currentAnnouncement.value.id)
    } catch (error) {
      // ignore
    }
  }
  detailVisible.value = false
}

onMounted(() => {
  loadAnnouncements()
})

onUnmounted(() => {
  stopAnimation()
})
</script>

<style scoped lang="scss">
.announcement-marquee {
  display: flex;
  align-items: center;
  height: 40px;
  background: linear-gradient(135deg, #0EA5E9 0%, #06B6D4 100%);
  color: #fff;
  position: relative;
  overflow: hidden;

  html.dark & {
    background: linear-gradient(135deg, #0C4A6E 0%, #0E7490 100%);
  }
}

.marquee-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 100%;
  background: rgba(0, 0, 0, 0.1);
  font-size: 18px;
  flex-shrink: 0;
  animation: pulse-icon 2s ease-in-out infinite;
}

@keyframes pulse-icon {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.6;
  }
}

.marquee-wrapper {
  flex: 1;
  overflow: hidden;
  position: relative;
  height: 100%;
}

.marquee-content {
  display: flex;
  align-items: center;
  height: 100%;
  white-space: nowrap;
  will-change: transform;

  // 复制一份内容实现无缝滚动
  &::after {
    content: '';
    display: inline-block;
    width: 100px;
  }
}

.marquee-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0 16px;
  cursor: pointer;
  transition: opacity var(--duration-fast);

  &:hover {
    opacity: 0.85;
  }

  .item-title {
    font-size: var(--font-size-sm);
    font-weight: var(--font-weight-medium);
  }

  .item-divider {
    opacity: 0.4;
    margin-left: 8px;
  }

  :deep(.ant-tag) {
    margin-right: 0;
  }
}

.marquee-actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  padding-right: 8px;
}

.action-btn {
  color: #fff;
  opacity: 0.8;

  &:hover {
    opacity: 1;
    color: #fff;
    background: rgba(255, 255, 255, 0.1);
  }

  .anticon {
    font-size: 16px;
  }
}

.close-btn {
  margin-left: 4px;
}

// 公告弹窗样式
.announcement-modal {
  .modal-header {
    text-align: center;
  }

  .modal-tags {
    display: flex;
    justify-content: center;
    gap: 8px;
    margin-bottom: 12px;
  }

  .modal-title {
    font-family: var(--font-display);
    font-size: 20px;
    font-weight: var(--font-weight-semibold);
    color: var(--text-primary);
    margin-bottom: 8px;
  }

  .modal-time {
    font-size: var(--font-size-sm);
    color: var(--text-tertiary);
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
  }

  .modal-content {
    font-size: 15px;
    line-height: 1.8;
    color: var(--text-primary);
    white-space: pre-wrap;
    max-height: 300px;
    overflow-y: auto;
  }

  .modal-footer {
    margin-top: 24px;
    text-align: center;
  }
}
</style>
