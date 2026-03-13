<template>
  <el-breadcrumb class="layout-breadcrumb" separator="/">
    <transition-group name="breadcrumb">
      <el-breadcrumb-item v-for="(item, index) in breadcrumbs" :key="item.path">
        <!-- 最后一项不可点击 -->
        <span v-if="index === breadcrumbs.length - 1" class="breadcrumb-current">
          {{ item.title }}
        </span>
        <!-- 其他项可点击跳转 -->
        <a v-else class="breadcrumb-link" @click="handleNavigate(item.path)">
          {{ item.title }}
        </a>
      </el-breadcrumb-item>
    </transition-group>
  </el-breadcrumb>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter, type RouteLocationMatched } from 'vue-router'

interface BreadcrumbItem {
  path: string
  title: string
}

const route = useRoute()
const router = useRouter()

// 根据当前路由生成面包屑
const breadcrumbs = computed<BreadcrumbItem[]>(() => {
  const matched = route.matched.filter(
    (item: RouteLocationMatched) => item.meta && item.meta.title && !item.meta.hidden
  )

  // 添加首页
  const result: BreadcrumbItem[] = [
    {
      path: '/dashboard',
      title: '首页',
    },
  ]

  // 添加匹配的路由
  matched.forEach((item: RouteLocationMatched) => {
    if (item.meta?.title) {
      result.push({
        path: item.path,
        title: item.meta.title as string,
      })
    }
  })

  return result
})

// 导航处理
function handleNavigate(path: string): void {
  router.push(path)
}
</script>

<style scoped lang="scss">
.layout-breadcrumb {
  font-size: 14px;

  :deep(.el-breadcrumb__inner) {
    color: var(--text-regular);
  }

  :deep(.el-breadcrumb__separator) {
    color: var(--text-secondary);
  }
}

.breadcrumb-link {
  color: var(--text-regular);
  cursor: pointer;
  transition: color var(--transition-duration);

  &:hover {
    color: var(--primary-color);
  }
}

.breadcrumb-current {
  color: var(--text-primary);
  font-weight: 500;
}

// 面包屑动画
.breadcrumb-enter-active,
.breadcrumb-leave-active {
  transition: all 0.3s ease;
}

.breadcrumb-enter-from,
.breadcrumb-leave-to {
  opacity: 0;
  transform: translateX(10px);
}
</style>
