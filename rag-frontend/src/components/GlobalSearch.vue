<template>
  <a-input-search
    v-model:value="searchValue"
    :placeholder="placeholder"
    :loading="loading"
    allow-clear
    @search="handleSearch"
    @change="handleChange"
  >
    <template v-if="$slots.prefix" #prefix>
      <slot name="prefix"></slot>
    </template>
  </a-input-search>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

interface Props {
  modelValue?: string
  placeholder?: string
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  placeholder: '搜索...',
  loading: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  search: [value: string]
  change: [value: string]
}>()

const searchValue = ref(props.modelValue)

watch(
  () => props.modelValue,
  (val) => {
    searchValue.value = val
  }
)

function handleSearch(value: string) {
  emit('search', value)
}

function handleChange(e: Event) {
  const value = (e.target as HTMLInputElement).value
  emit('update:modelValue', value)
  emit('change', value)
}
</script>
