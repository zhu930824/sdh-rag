<template>
  <el-dialog
    v-model="visible"
    title="全局搜索"
    width="600px"
    :close-on-click-modal="true"
    class="global-search-dialog"
    @close="handleClose"
  >
    <el-input
      ref="searchInputRef"
      v-model="searchKeyword"
      placeholder="搜索用户、知识库、文档..."
      :prefix-icon="Search"
      clearable
      size="large"
      @keyup.enter="handleSearch"
    >
      <template #append>
        <el-button :icon="Search" @click="handleSearch" />
      </template>
    </el-input>

    <!-- 搜索结果 -->
    <div v-if="searchResults.length > 0" class="search-results">
      <div class="result-category" v-for="category in searchResults" :key="category.type">
        <div class="category-title">{{ category.label }}</div>
        <div
          v-for="item in category.items"
          :key="item.id"
          class="result-item"
          @click="handleSelectResult(item)"
        >
          <el-icon class="item-icon"><component :is="item.icon" /></el-icon>
          <div class="item-content">
            <div class="item-title">{{ item.title }}</div>
            <div class="item-desc">{{ item.description }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else-if="searchKeyword && !loading" class="empty-result">
      <EmptyState type="search" text="未找到相关内容" />
    </div>

    <!-- 快捷键提示 -->
    <div class="shortcut-tips">
      <span>按 <kbd>Ctrl</kbd> + <kbd>K</kbd> 打开搜索</span>
      <span>按 <kbd>Enter</kbd> 执行搜索</span>
      <span>按 <kbd>Esc</kbd> 关闭</span>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { Search, User, Document, FolderOpened } from '@element-plus/icons-vue'
import EmptyState from '@/components/EmptyState.vue'

/**
 * 搜索结果项
 */
interface SearchResultItem {
  id: string | number
  type: string
  title: string
  description: string
  icon: typeof User
  path?: string
}

/**
 * 搜索结果分类
 */
interface SearchResultCategory {
  type: string
  label: string
  items: SearchResultItem[]
}

interface Props {
  modelValue: boolean
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
  (e: 'select', item: SearchResultItem): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const visible = ref(props.modelValue)
const searchKeyword = ref('')
const searchInputRef = ref()
const loading = ref(false)
const searchResults = ref<SearchResultCategory[]>([])

// 同步 visible
watch(
  () => props.modelValue,
  val => {
    visible.value = val
    if (val) {
      nextTick(() => {
        searchInputRef.value?.focus()
      })
    }
  }
)

watch(visible, val => {
  emit('update:modelValue', val)
})

// 执行搜索
async function handleSearch() {
  if (!searchKeyword.value.trim()) return

  loading.value = true
  try {
    // 模拟搜索结果
    await new Promise(resolve => setTimeout(resolve, 500))
    searchResults.value = [
      {
        type: 'user',
        label: '用户',
        items: [
          { id: 1, type: 'user', title: '管理员', description: 'admin@example.com', icon: User, path: '/user' },
          { id: 2, type: 'user', title: '用户一', description: 'user01@example.com', icon: User, path: '/user' },
        ],
      },
      {
        type: 'knowledge',
        label: '知识库',
        items: [
          { id: 1, type: 'knowledge', title: '产品文档', description: '产品相关文档集合', icon: FolderOpened, path: '/knowledge' },
          { id: 2, type: 'knowledge', title: '技术文档', description: '技术相关文档集合', icon: FolderOpened, path: '/knowledge' },
        ],
      },
      {
        type: 'document',
        label: '文档',
        items: [
          { id: 1, type: 'document', title: '用户手册', description: '系统使用说明', icon: Document, path: '/document' },
        ],
      },
    ]
  } finally {
    loading.value = false
  }
}

// 选择结果
function handleSelectResult(item: SearchResultItem) {
  emit('select', item)
  handleClose()
}

// 关闭对话框
function handleClose() {
  visible.value = false
  searchKeyword.value = ''
  searchResults.value = []
}
</script>

<style scoped lang="scss">
.global-search-dialog {
  :deep(.el-dialog__body) {
    padding: 16px 20px;
  }
}

.search-results {
  margin-top: 16px;
  max-height: 400px;
  overflow-y: auto;

  .result-category {
    margin-bottom: 16px;

    .category-title {
      font-size: 12px;
      color: var(--el-text-color-secondary);
      margin-bottom: 8px;
      padding-left: 4px;
    }

    .result-item {
      display: flex;
      align-items: center;
      padding: 12px;
      border-radius: 8px;
      cursor: pointer;
      transition: background-color 0.2s;

      &:hover {
        background-color: var(--el-fill-color-light);
      }

      .item-icon {
        font-size: 20px;
        color: var(--el-color-primary);
        margin-right: 12px;
      }

      .item-content {
        flex: 1;

        .item-title {
          font-size: 14px;
          color: var(--el-text-color-primary);
          margin-bottom: 4px;
        }

        .item-desc {
          font-size: 12px;
          color: var(--el-text-color-secondary);
        }
      }
    }
  }
}

.empty-result {
  margin-top: 40px;
}

.shortcut-tips {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);

  kbd {
    padding: 2px 6px;
    background-color: var(--el-fill-color);
    border: 1px solid var(--el-border-color);
    border-radius: 4px;
    font-family: inherit;
    font-size: 11px;
  }
}
</style>
