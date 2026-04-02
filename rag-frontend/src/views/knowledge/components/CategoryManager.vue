<template>
  <a-modal
    :open="open"
    title="分类管理"
    :width="600"
    :mask-closable="false"
    @update:open="handleClose"
  >
    <div class="category-manager">
      <div class="add-category">
        <a-form layout="inline" :model="newCategory" @submit.prevent="handleAddCategory">
          <a-form-item>
            <a-input
              v-model:value="newCategory.name"
              placeholder="分类名称"
              style="width: 200px"
            />
          </a-form-item>
          <a-form-item>
            <a-tree-select
              v-model:value="newCategory.parentId"
              :tree-data="categoryTreeData"
              :field-names="{ label: 'name', value: 'id', children: 'children' }"
              placeholder="父级分类"
              allow-clear
              tree-default-expand-all
              style="width: 150px"
            />
          </a-form-item>
          <a-form-item>
            <a-button type="primary" :loading="adding" @click="handleAddCategory">
              添加
            </a-button>
          </a-form-item>
        </a-form>
      </div>

      <div class="category-tree">
        <a-tree
          :tree-data="knowledgeStore.categoryTree"
          :field-names="{ title: 'name', key: 'id', children: 'children' }"
          default-expand-all
          draggable
          :block-node="true"
          @drop="handleDrop"
        >
          <template #title="{ name, description, id, children }">
            <div class="tree-node">
              <span class="node-label">{{ name }}</span>
              <span class="node-desc">{{ description }}</span>
              <div class="node-actions">
                <a-button type="link" size="small" @click.stop="handleEdit({ id, name, description })">
                  编辑
                </a-button>
                <a-button
                  type="link"
                  danger
                  size="small"
                  :disabled="children && children.length > 0"
                  @click.stop="handleDelete({ id, name })"
                >
                  删除
                </a-button>
              </div>
            </div>
          </template>
        </a-tree>
      </div>
    </div>

    <a-modal
      v-model:open="showEditDialog"
      title="编辑分类"
      :width="400"
      :mask-closable="false"
      ok-text="确认"
      cancel-text="取消"
      @ok="handleUpdate"
    >
      <a-form ref="editFormRef" :model="editForm" :rules="rules" :label-col="{ span: 4 }">
        <a-form-item label="分类名称" name="name">
          <a-input v-model:value="editForm.name" placeholder="请输入分类名称" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea
            v-model:value="editForm.description"
            :rows="3"
            placeholder="请输入分类描述"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { FormInstance, Rule } from 'ant-design-vue/es/form'
import type { TreeProps } from 'ant-design-vue'
import { useKnowledgeStore } from '@/stores/knowledge'
import { createCategory, updateCategory, deleteCategory } from '@/api/knowledge'
import { showSuccess, showError, showWarning } from '@/utils/message'
import { showDeleteConfirm } from '@/utils/confirm'
import type { DocumentCategory } from '@/api/knowledge'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  success: []
}>()

const knowledgeStore = useKnowledgeStore()

const adding = ref(false)
const updating = ref(false)
const showEditDialog = ref(false)
const editFormRef = ref<FormInstance>()

const newCategory = ref({
  name: '',
  parentId: null as number | null,
})

const editForm = ref({
  id: 0,
  name: '',
  description: '',
})

const rules: Record<string, Rule[]> = {
  name: [
    { required: true, message: '请输入分类名称', trigger: 'blur' },
    { min: 2, max: 20, message: '长度在 2 到 20 个字符', trigger: 'blur' },
  ],
}

const categoryTreeData = computed(() => {
  return [
    { id: 0, name: '顶级分类', children: knowledgeStore.categoryTree },
  ]
})

async function handleAddCategory() {
  if (!newCategory.value.name.trim()) {
    showWarning('请输入分类名称')
    return
  }

  adding.value = true
  try {
    await createCategory({
      name: newCategory.value.name,
      parentId: newCategory.value.parentId || undefined,
    })
    showSuccess('添加成功')
    newCategory.value.name = ''
    newCategory.value.parentId = null
    await knowledgeStore.fetchCategories()
    emit('success')
  } catch (error) {
    showError('添加失败')
  } finally {
    adding.value = false
  }
}

function handleEdit(data: { id: number; name: string; description?: string }) {
  editForm.value = {
    id: data.id,
    name: data.name,
    description: data.description || '',
  }
  showEditDialog.value = true
}

async function handleUpdate() {
  if (!editFormRef.value) return

  try {
    await editFormRef.value.validate()
  } catch {
    return
  }

  updating.value = true
  try {
    await updateCategory(editForm.value.id, {
      name: editForm.value.name,
      description: editForm.value.description,
    })
    showSuccess('更新成功')
    showEditDialog.value = false
    await knowledgeStore.fetchCategories()
    emit('success')
  } catch (error) {
    showError('更新失败')
  } finally {
    updating.value = false
  }
}

async function handleDelete(data: { id: number; name: string }) {
  const confirmed = await showDeleteConfirm(`分类"${data.name}"`)
  if (!confirmed) return

  try {
    await deleteCategory(data.id)
    showSuccess('删除成功')
    await knowledgeStore.fetchCategories()
    emit('success')
  } catch (error) {
    showError('删除失败')
  }
}

const handleDrop: TreeProps['onDrop'] = (info) => {
  console.log('拖拽完成:', info)
}

function handleClose() {
  emit('update:open', false)
}
</script>

<style scoped lang="scss">
.category-manager {
  .add-category {
    margin-bottom: 20px;
    padding-bottom: 20px;
    border-bottom: 1px solid #e4e7ed;
  }

  .category-tree {
    max-height: 400px;
    overflow-y: auto;

    .tree-node {
      display: flex;
      align-items: center;
      width: 100%;
      padding-right: 8px;

      .node-label {
        flex: 1;
        font-size: 14px;
      }

      .node-desc {
        margin: 0 12px;
        font-size: 12px;
        color: #909399;
        max-width: 150px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .node-actions {
        display: none;
        gap: 4px;
      }
    }

    :deep(.ant-tree-node-content-wrapper):hover .node-actions {
      display: flex;
    }
  }
}
</style>
