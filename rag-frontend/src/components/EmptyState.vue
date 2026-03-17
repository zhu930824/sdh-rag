<template>
  <a-empty :image="imageType" :description="description">
    <template v-if="$slots.action || showAction" #default>
      <slot name="action">
        <a-button v-if="actionText" type="primary" @click="$emit('action')">
          {{ actionText }}
        </a-button>
      </slot>
    </template>
  </a-empty>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Empty } from 'ant-design-vue'

type EmptyType = 'default' | 'search' | 'data' | 'error' | 'user' | 'document'

interface Props {
  type?: EmptyType
  description?: string
  actionText?: string
  showAction?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  type: 'default',
  description: '',
  actionText: '',
  showAction: false,
})

defineEmits<{
  action: []
}>()

const imageType = computed(() => {
  const typeMap: Record<EmptyType, typeof Empty.PRESENTED_IMAGE_SIMPLE> = {
    default: Empty.PRESENTED_IMAGE_SIMPLE,
    search: Empty.PRESENTED_IMAGE_SIMPLE,
    data: Empty.PRESENTED_IMAGE_SIMPLE,
    error: Empty.PRESENTED_IMAGE_SIMPLE,
    user: Empty.PRESENTED_IMAGE_SIMPLE,
    document: Empty.PRESENTED_IMAGE_SIMPLE,
  }
  return typeMap[props.type] || Empty.PRESENTED_IMAGE_SIMPLE
})
</script>

<style scoped lang="scss">
.ant-empty {
  padding: 32px 0;
}
</style>
