<template>
  <a-skeleton :active="active" :loading="loading" :avatar="avatar" :paragraph="paragraph" :title="title">
    <template v-if="$slots.default">
      <slot></slot>
    </template>
  </a-skeleton>
</template>

<script setup lang="ts">
interface Props {
  loading?: boolean
  active?: boolean
  avatar?: boolean
  title?: boolean
  paragraph?: boolean | { rows: number }
}

withDefaults(defineProps<Props>(), {
  loading: true,
  active: true,
  avatar: false,
  title: true,
  paragraph: true,
})
</script>
