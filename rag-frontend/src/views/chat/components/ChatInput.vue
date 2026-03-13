<template>
  <div class="chat-input">
    <div class="input-wrapper">
      <textarea
        ref="textareaRef"
        v-model="inputText"
        class="input-textarea"
        :placeholder="placeholder"
        :disabled="disabled"
        @input="adjustHeight"
        @keydown="handleKeydown"
      ></textarea>

      <div class="input-actions">
        <div class="input-tips">
          <el-icon><InfoFilled /></el-icon>
          <span>Enter 发送，Shift + Enter 换行</span>
        </div>

        <div class="input-buttons">
          <!-- 停止生成按钮 -->
          <el-button v-if="isGenerating" type="danger" :icon="VideoPause" @click="handleStop">
            停止生成
          </el-button>

          <!-- 发送按钮 -->
          <el-button
            v-else
            type="primary"
            :icon="Promotion"
            :disabled="!canSend"
            :loading="sending"
            @click="handleSend"
          >
            发送
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick } from 'vue'
import { Promotion, VideoPause, InfoFilled } from '@element-plus/icons-vue'

const props = withDefaults(
  defineProps<{
    disabled?: boolean
    placeholder?: string
    sending?: boolean
    isGenerating?: boolean
  }>(),
  {
    disabled: false,
    placeholder: '输入您的问题...',
    sending: false,
    isGenerating: false,
  }
)

const emit = defineEmits<{
  (e: 'send', message: string): void
  (e: 'stop'): void
}>()

const textareaRef = ref<HTMLTextAreaElement>()
const inputText = ref('')

// 是否可以发送
const canSend = computed(() => {
  return inputText.value.trim().length > 0 && !props.disabled && !props.sending
})

// 调整文本框高度
function adjustHeight(): void {
  nextTick(() => {
    if (textareaRef.value) {
      // 重置高度
      textareaRef.value.style.height = 'auto'
      // 设置新高度（最小60px，最大200px）
      const newHeight = Math.min(Math.max(textareaRef.value.scrollHeight, 60), 200)
      textareaRef.value.style.height = `${newHeight}px`
    }
  })
}

// 处理键盘事件
function handleKeydown(event: KeyboardEvent): void {
  // Enter 发送（非 Shift+Enter）
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    handleSend()
  }
  // Shift+Enter 换行（默认行为）
}

// 发送消息
function handleSend(): void {
  const message = inputText.value.trim()
  if (!message || props.disabled || props.sending) return

  emit('send', message)
  inputText.value = ''

  // 重置文本框高度
  nextTick(() => {
    if (textareaRef.value) {
      textareaRef.value.style.height = '60px'
    }
  })
}

// 停止生成
function handleStop(): void {
  emit('stop')
}

// 聚焦输入框
function focus(): void {
  textareaRef.value?.focus()
}

// 暴露方法
defineExpose({
  focus,
})
</script>

<style scoped lang="scss">
.chat-input {
  padding: 16px 20px;
  background-color: var(--bg-color);
  border-top: 1px solid var(--border-lighter);

  .input-wrapper {
    display: flex;
    flex-direction: column;
    background-color: var(--bg-page);
    border-radius: var(--border-radius-large);
    border: 1px solid var(--border-light);
    transition: border-color var(--transition-duration);

    &:focus-within {
      border-color: var(--primary-color);
    }

    .input-textarea {
      width: 100%;
      min-height: 60px;
      max-height: 200px;
      padding: 12px 16px;
      border: none;
      background: transparent;
      font-size: 14px;
      line-height: 1.6;
      color: var(--text-primary);
      resize: none;
      outline: none;

      &::placeholder {
        color: var(--text-placeholder);
      }

      &:disabled {
        cursor: not-allowed;
        opacity: 0.6;
      }
    }

    .input-actions {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 8px 12px;
      border-top: 1px solid var(--border-lighter);

      .input-tips {
        display: flex;
        align-items: center;
        gap: 4px;
        font-size: 12px;
        color: var(--text-placeholder);

        .el-icon {
          font-size: 14px;
        }
      }

      .input-buttons {
        display: flex;
        gap: 8px;
      }
    }
  }
}
</style>
