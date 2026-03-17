<template>
  <a-breadcrumb class="layout-breadcrumb">
    <transition-group name="breadcrumb">
      <a-breadcrumb-item v-for="(item, index) in breadcrumbs" :key="item.path">
        <span v-if="index === breadcrumbs.length - 1" class="breadcrumb-current">
          {{ item.title }}
        </span>
        <a v-else class="breadcrumb-link" @click="handleNavigate(item.path)">
          {{ item.title }}
        </a>
      </a-breadcrumb-item>
    </transition-group>
  </a-breadcrumb>
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

const breadcrumbs = computed<BreadcrumbItem[]>(() => {
  const matched = route.matched.filter(
    (item: RouteLocationMatched) => item.meta && item.meta.title && !item.meta.hidden
  )

  const result: BreadcrumbItem[] = [
    {
      path: '/dashboard',
      title: '首页',
    },
  ]

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

function handleNavigate(path: string): void {
  router.push(path)
}
</script>

<style scoped lang="scss">
.layout-breadcrumb {
  font-size: 14px;

  :deep(.ant-breadcrumb-link) {
    color: var(--text-regular);
  }

  :deep(.ant-breadcrumb-separator) {
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
