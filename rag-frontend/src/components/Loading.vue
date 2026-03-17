<template>
  <div class="loading-container" :class="{ 'full-screen': fullscreen }">
    <a-spin :size="size" :tip="tip" />
  </div>
</template>

<script setup lang="ts">
interface Props {
  fullscreen?: boolean
  size?: 'small' | 'default' | 'large'
  tip?: string
}

withDefaults(defineProps<Props>(), {
  fullscreen: false,
  size: 'default',
  tip: '',
})
</script>

<style scoped lang="scss">
.loading-container {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;

  &.full-screen {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: rgba(255, 255, 255, 0.8);
    z-index: 1000;

    html.dark & {
      background: rgba(0, 0, 0, 0.8);
    }
  }
}
</style>
