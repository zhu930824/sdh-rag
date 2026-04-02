<template>
  <BaseEdge
    :id="id"
    :source-x="sourceX"
    :source-y="sourceY"
    :target-x="targetX"
    :target-y="targetY"
    :source-position="sourcePosition"
    :target-position="targetPosition"
    :style="style"
    :marker-end="markerEnd"
    :path="path"
  />
  <EdgeLabelRenderer>
    <div
      class="edge-button-wrapper"
      :style="{
        pointerEvents: 'all',
        position: 'absolute',
        transform: `translate(-50%, -50%) translate(${centerX}px, ${centerY}px)`,
      }"
    >
      <button class="edge-button" @click="$emit('delete', id)">
        <span>×</span>
      </button>
    </div>
  </EdgeLabelRenderer>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { BaseEdge, EdgeLabelRenderer, getBezierPath, type Position } from '@vue-flow/core'

const props = defineProps<{
  id: string
  sourceX: number
  sourceY: number
  targetX: number
  targetY: number
  sourcePosition: Position
  targetPosition: Position
  style?: any
  markerEnd?: string
  data?: any
}>()

defineEmits<{
  (e: 'delete', id: string): void
}>()

const path = computed(() => {
  const [result] = getBezierPath({
    sourceX: props.sourceX,
    sourceY: props.sourceY,
    targetX: props.targetX,
    targetY: props.targetY,
    sourcePosition: props.sourcePosition,
    targetPosition: props.targetPosition,
  })
  return result
})

const centerX = computed(() => (props.sourceX + props.targetX) / 2)
const centerY = computed(() => (props.sourceY + props.targetY) / 2)
</script>

<style scoped lang="scss">
.edge-button-wrapper {
  z-index: 10;
}

.edge-button {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #fff;
  border: 1px solid #d9d9d9;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  color: #8c8c8c;
  transition: all 0.2s ease;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);

  &:hover {
    background: #ff4d4f;
    border-color: #ff4d4f;
    color: #fff;
  }

  span {
    line-height: 1;
  }
}
</style>
