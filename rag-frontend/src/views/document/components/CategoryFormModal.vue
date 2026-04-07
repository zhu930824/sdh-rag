<template>
  <a-modal
    :open="open"
    :title="modalTitle"
    :width="480"
    :mask-closable="false"
    :confirm-loading="loading"
    @update:open="handleClose"
    @ok="handleSubmit"
  >
    <a-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      :label-col="{ span: 4 }"
      :wrapper-col="{ span: 20 }"
    >
      <a-form-item label="分类名称" name="name">
        <a-input
          v-model:value="formData.name"
          placeholder="请输入分类名称"
          :maxlength="50"
        />
      </a-form-item>
      <a-form-item label="父级分类" name="parentId">
        <a-tree-select
          v-model:value="formData.parentId"
          :tree-data="parentTreeData"
          :field-names="{ label: 'name', value: 'id', children: 'children' }"
          placeholder="请选择父级分类（可选）"
          allow-clear
          tree-default-expand-all
        />
      </a-form-item>
      <a-form-item label="描述" name="description">
        <a-textarea
          v-model:value="formData.description"
          :rows="3"
          placeholder="请输入分类描述（可选）"
          :maxlength="200"
          show-count
        />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import type { FormInstance, Rule } from 'ant-design-vue/es/form'
import { useDocumentStore } from '@/stores/document'
import { showSuccess, showError } from '@/utils/message'

const props = defineProps<{
  open: boolean
  mode: 'create' | 'edit'
  category: { id: number; name: string; description: string } | null
  parentId: number | null
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  success: []
}>()

const documentStore = useDocumentStore()
const formRef = ref<FormInstance>()
const loading = ref(false)

const modalTitle = computed(() => props.mode === 'create' ? '新增分类' : '编辑分类')

const formData = ref({
  name: '',
  parentId: null as number | null,
  description: '',
})

const rules: Record<string, Rule[]> = {
  name: [
    { required: true, message: '请输入分类名称', trigger: 'blur' },
    { min: 2, max: 50, message: '名称长度在 2 到 50 个字符', trigger: 'blur' },
  ],
}

// 父级分类树（排除当前编辑的分类及其子分类）
const parentTreeData = computed(() => {
  if (props.mode === 'edit' && props.category) {
    // 编辑模式下，需要排除当前分类及其子分类
    const excludeIds = new Set<number>()
    collectChildIds(documentStore.categoryTree, props.category.id, excludeIds)
    return filterTree(documentStore.categoryTree, excludeIds)
  }
  return documentStore.categoryTree
})

// 收集所有子分类ID
function collectChildIds(tree: any[], id: number, ids: Set<number>) {
  ids.add(id)
  for (const node of tree) {
    if (node.id === id || ids.has(node.id)) {
      if (node.children) {
        for (const child of node.children) {
          collectChildIds([child], child.id, ids)
        }
      }
    } else if (node.children) {
      collectChildIds(node.children, id, ids)
    }
  }
}

// 过滤树，排除指定ID
function filterTree(tree: any[], excludeIds: Set<number>): any[] {
  return tree
    .filter(node => !excludeIds.has(node.id))
    .map(node => ({
      ...node,
      children: node.children ? filterTree(node.children, excludeIds) : undefined,
    }))
}

// 监听弹窗打开，初始化表单
watch(() => props.open, (val) => {
  if (val) {
    if (props.mode === 'edit' && props.category) {
      const category = documentStore.categoryList.find(c => c.id === props.category!.id)
      formData.value = {
        name: props.category.name,
        parentId: category?.parentId || null,
        description: props.category.description || '',
      }
    } else {
      formData.value = {
        name: '',
        parentId: props.parentId,
        description: '',
      }
    }
  }
})

async function handleSubmit() {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
  } catch {
    return
  }

  loading.value = true
  try {
    if (props.mode === 'edit' && props.category) {
      await documentStore.updateCategory(props.category.id, formData.value)
    } else {
      await documentStore.createCategory(formData.value)
    }
    // 先关闭弹窗，再显示成功消息
    emit('update:open', false)
    emit('success')
    showSuccess(props.mode === 'edit' ? '更新成功' : '创建成功')
  } catch (error: any) {
    showError(error.response?.data?.message || '操作失败')
  } finally {
    loading.value = false
  }
}

function handleClose() {
  emit('update:open', false)
}
</script>
