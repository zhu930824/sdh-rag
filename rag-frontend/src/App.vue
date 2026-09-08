<template>
  <a-config-provider :locale="zhCN">
    <router-view v-slot="{ Component, route }">
      <transition name="fade" mode="out-in">
        <!-- key 取顶级路由记录而非完整路径：保证 /dashboard -> /chat 这类子路由切换时
             只替换 MainLayout 内部的内容区，而不会卸载重建整个布局（避免整页闪烁刷新） -->
        <component :is="Component" :key="route.matched[0]?.path" />
      </transition>
    </router-view>
  </a-config-provider>
</template>

<script setup lang="ts">
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'

// 设置 dayjs 为中文
dayjs.locale('zh-cn')
</script>

<style>
#app {
  width: 100%;
  height: 100%;
}

/* 页面切换动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
