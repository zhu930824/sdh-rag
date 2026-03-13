<template>
  <div class="skeleton-wrapper">
    <!-- 骨架屏类型：卡片 -->
    <div v-if="type === 'card'" class="skeleton-card">
      <div class="skeleton-header">
        <skeleton-item variant="circle" width="40px" height="40px" />
        <div class="skeleton-title">
          <skeleton-item width="60%" height="16px" />
          <skeleton-item width="40%" height="14px" />
        </div>
      </div>
      <div class="skeleton-content">
        <skeleton-item width="100%" height="14px" />
        <skeleton-item width="90%" height="14px" />
        <skeleton-item width="75%" height="14px" />
      </div>
    </div>

    <!-- 骨架屏类型：列表 -->
    <div v-else-if="type === 'list'" class="skeleton-list">
      <div v-for="i in rows" :key="i" class="skeleton-list-item">
        <skeleton-item variant="circle" width="40px" height="40px" />
        <div class="skeleton-list-content">
          <skeleton-item width="70%" height="14px" />
          <skeleton-item width="50%" height="12px" />
        </div>
      </div>
    </div>

    <!-- 骨架屏类型：表格 -->
    <div v-else-if="type === 'table'" class="skeleton-table">
      <div class="skeleton-table-header">
        <skeleton-item v-for="i in columns" :key="i" width="100%" height="40px" />
      </div>
      <div v-for="i in rows" :key="i" class="skeleton-table-row">
        <skeleton-item v-for="j in columns" :key="j" width="100%" height="40px" />
      </div>
    </div>

    <!-- 骨架屏类型：详情 -->
    <div v-else-if="type === 'detail'" class="skeleton-detail">
      <skeleton-item width="200px" height="24px" style="margin-bottom: 20px" />
      <div class="skeleton-detail-row" v-for="i in rows" :key="i">
        <skeleton-item width="120px" height="14px" />
        <skeleton-item width="200px" height="14px" />
      </div>
    </div>

    <!-- 骨架屏类型：表单 -->
    <div v-else-if="type === 'form'" class="skeleton-form">
      <div v-for="i in rows" :key="i" class="skeleton-form-item">
        <skeleton-item width="80px" height="14px" />
        <skeleton-item width="100%" height="40px" />
      </div>
    </div>

    <!-- 默认：简单骨架屏 -->
    <div v-else class="skeleton-simple">
      <skeleton-item v-for="i in rows" :key="i" :width="getWidth(i)" height="14px" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { h, defineComponent } from 'vue'

interface Props {
  type?: 'card' | 'list' | 'table' | 'detail' | 'form' | 'simple'
  rows?: number
  columns?: number
  animated?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  type: 'simple',
  rows: 3,
  columns: 4,
  animated: true,
})

// 骨架屏子项组件
const SkeletonItem = defineComponent({
  name: 'SkeletonItem',
  props: {
    variant: {
      type: String as () => 'text' | 'circle' | 'rect',
      default: 'text',
    },
    width: {
      type: String,
      default: '100%',
    },
    height: {
      type: String,
      default: '14px',
    },
  },
  setup(props) {
    return () =>
      h('div', {
        class: ['skeleton-item', `skeleton-${props.variant}`, { 'skeleton-animated': props.animated }],
        style: {
          width: props.width,
          height: props.height,
          borderRadius: props.variant === 'circle' ? '50%' : '4px',
        },
      })
  },
})

// 获取宽度
function getWidth(index: number): string {
  const widths = ['100%', '90%', '80%', '95%', '85%', '75%']
  return widths[index % widths.length]
}
</script>

<style scoped lang="scss">
.skeleton-wrapper {
  width: 100%;
}

// 骨架屏项
.skeleton-item {
  background: linear-gradient(
    90deg,
    var(--border-lighter) 25%,
    var(--border-light) 50%,
    var(--border-lighter) 75%
  );
  background-size: 200% 100%;

  &.skeleton-animated {
    animation: skeleton-loading 1.5s ease-in-out infinite;
  }
}

@keyframes skeleton-loading {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

// 卡片骨架屏
.skeleton-card {
  padding: 20px;
  background: var(--bg-overlay);
  border-radius: var(--border-radius-base);
  border: 1px solid var(--border-lighter);

  .skeleton-header {
    display: flex;
    gap: 12px;
    margin-bottom: 20px;

    .skeleton-title {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
  }

  .skeleton-content {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }
}

// 列表骨架屏
.skeleton-list {
  .skeleton-list-item {
    display: flex;
    gap: 12px;
    padding: 12px 0;
    border-bottom: 1px solid var(--border-lighter);

    &:last-child {
      border-bottom: none;
    }

    .skeleton-list-content {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
  }
}

// 表格骨架屏
.skeleton-table {
  .skeleton-table-header,
  .skeleton-table-row {
    display: grid;
    gap: 12px;
    padding: 12px 0;
  }

  .skeleton-table-header {
    border-bottom: 2px solid var(--border-lighter);
  }

  .skeleton-table-row {
    border-bottom: 1px solid var(--border-lighter);

    &:last-child {
      border-bottom: none;
    }
  }
}

// 详情骨架屏
.skeleton-detail {
  .skeleton-detail-row {
    display: flex;
    gap: 20px;
    margin-bottom: 16px;
  }
}

// 表单骨架屏
.skeleton-form {
  .skeleton-form-item {
    margin-bottom: 20px;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
}

// 简单骨架屏
.skeleton-simple {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
</style>
