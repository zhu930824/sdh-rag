<template>
  <div class="category-panel">
    <div class="panel-header">
      <span class="panel-title">文档分类</span>
      <a-button type="primary" size="small" @click="handleAddRoot">
        <template #icon><PlusOutlined /></template>
        新增
      </a-button>
    </div>

    <div class="category-tree">
      <a-empty v-if="treeData.length === 0" description="暂无分类数据" />
      <a-tree
        v-else
        :tree-data="treeData"
        :field-names="{ title: 'name', key: 'id', children: 'children' }"
        :selected-keys="selectedKeys"
        :show-icon="false"
        default-expand-all
        :block-node="true"
        @select="handleSelect"
      >
        <template #title="{ id, name, children, docCount }">
          <div class="tree-node" @mouseenter="hoveredNode = id" @mouseleave="hoveredNode = null">
            <div class="node-content">
              <FolderOutlined class="node-icon" />
              <span class="node-label">{{ name }}</span>
              <span class="node-count">{{ docCount || 0 }}</span>
            </div>
            <div v-show="hoveredNode === id" class="node-actions" @click.stop>
              <a-button type="link" size="small" @click="handleAddChild(id)">
                <PlusOutlined />
              </a-button>
              <a-button type="link" size="small" @click="handleEdit(id, name)">
                <EditOutlined />
              </a-button>
              <a-button
                type="link"
                size="small"
                danger
                :disabled="children && children.length > 0"
                @click="handleDelete(id, name)"
              >
                <DeleteOutlined />
              </a-button>
            </div>
          </div>
        </template>
      </a-tree>
    </div>

    <CategoryFormModal
      v-model:open="modalVisible"
      :mode="modalMode"
      :category="editingCategory"
      :parent-id="parentId"
      @success="handleSuccess"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { PlusOutlined, FolderOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { useDocumentStore } from '@/stores/document'
import { showSuccess, showError } from '@/utils/message'
import { showDeleteConfirm } from '@/utils/confirm'
import CategoryFormModal from './CategoryFormModal.vue'

const documentStore = useDocumentStore()

const hoveredNode = ref<number | null>(null)
const modalVisible = ref(false)
const modalMode = ref<'create' | 'edit'>('create')
const editingCategory = ref<{ id: number; name: string; description: string } | null>(null)
const parentId = ref<number | null>(null)

const selectedKeys = computed(() => {
  return documentStore.currentCategory ? [documentStore.currentCategory] : []
})

const treeData = computed(() => {
  console.log('categoryTree:', documentStore.categoryTree)
  console.log('categoryList:', documentStore.categoryList)
  return documentStore.categoryTree
})

onMounted(async () => {
  // 确保数据已加载
  if (documentStore.categoryList.length === 0) {
    await documentStore.fetchCategories()
  }
})

function handleSelect(keys: number[]) {
  if (keys.length > 0) {
    documentStore.setCurrentCategory(keys[0])
  } else {
    documentStore.setCurrentCategory(null)
  }
}

function handleAddRoot() {
  modalMode.value = 'create'
  parentId.value = null
  editingCategory.value = null
  modalVisible.value = true
}

function handleAddChild(id: number) {
  modalMode.value = 'create'
  parentId.value = id
  editingCategory.value = null
  modalVisible.value = true
}

function handleEdit(id: number, name: string) {
  const category = documentStore.categoryList.find(c => c.id === id)
  modalMode.value = 'edit'
  editingCategory.value = {
    id,
    name: category?.name || name,
    description: category?.description || '',
  }
  parentId.value = null
  modalVisible.value = true
}

async function handleDelete(id: number, name: string) {
  const confirmed = await showDeleteConfirm(`分类"${name}"`)
  if (!confirmed) return

  try {
    await documentStore.deleteCategory(id)
    showSuccess('删除成功')
  } catch (error: any) {
    showError(error.response?.data?.message || '删除失败')
  }
}

function handleSuccess() {
  modalVisible.value = false
}
</script>

<style scoped lang="scss">
.category-panel {
  height: 100%;
  display: flex;
  flex-direction: column;

  .panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
    flex-shrink: 0;

    .panel-title {
      font-family: var(--font-display);
      font-size: 16px;
      font-weight: var(--font-weight-semibold);
      color: var(--text-primary);
    }
  }

  .category-tree {
    flex: 1;
    min-height: 0;
    overflow-y: auto;

    .tree-node {
      display: flex;
      align-items: center;
      justify-content: space-between;
      width: 100%;
      padding-right: 8px;

      .node-content {
        display: flex;
        align-items: center;
        gap: 8px;
        flex: 1;
        min-width: 0;

        .node-icon {
          color: var(--warning-color);
          font-size: 14px;
        }

        .node-label {
          flex: 1;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .node-count {
          font-size: 12px;
          color: var(--text-tertiary);
          background: var(--bg-surface-secondary);
          padding: 2px 6px;
          border-radius: var(--radius-sm);
        }
      }

      .node-actions {
        display: flex;
        gap: 4px;

        .ant-btn-link {
          padding: 0 4px;
          height: auto;
          font-size: 12px;
        }
      }
    }
  }
}
</style>
