<template>
  <el-dialog
    :model-value="modelValue"
    title="分类管理"
    width="600px"
    :close-on-click-modal="false"
    @update:model-value="handleClose"
  >
    <div class="category-manager">
      <!-- 添加分类 -->
      <div class="add-category">
        <el-form :inline="true" :model="newCategory" :rules="rules" @submit.prevent="handleAddCategory">
          <el-form-item prop="name">
            <el-input
              v-model="newCategory.name"
              placeholder="分类名称"
              style="width: 200px"
            />
          </el-form-item>
          <el-form-item>
            <el-tree-select
              v-model="newCategory.parentId"
              :data="categoryTreeData"
              :props="{ label: 'name', children: 'children', value: 'id' }"
              placeholder="父级分类"
              check-strictly
              clearable
              style="width: 150px"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="adding" @click="handleAddCategory">
              添加
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 分类树形列表 -->
      <div class="category-tree">
        <el-tree
          :data="knowledgeStore.categoryTree"
          :props="{ label: 'name', children: 'children' }"
          node-key="id"
          default-expand-all
          draggable
          :allow-drop="allowDrop"
          :allow-drag="allowDrag"
          @node-drop="handleDrop"
        >
          <template #default="{ node, data }">
            <div class="tree-node">
              <span class="node-label">{{ data.name }}</span>
              <span class="node-desc">{{ data.description }}</span>
              <div class="node-actions">
                <el-button type="primary" link size="small" @click.stop="handleEdit(data)">
                  编辑
                </el-button>
                <el-button
                  type="danger"
                  link
                  size="small"
                  :disabled="data.children && data.children.length > 0"
                  @click.stop="handleDelete(data)"
                >
                  删除
                </el-button>
              </div>
            </div>
          </template>
        </el-tree>
      </div>
    </div>

    <!-- 编辑对话框 -->
    <el-dialog
      v-model="showEditDialog"
      title="编辑分类"
      width="400px"
      append-to-body
      :close-on-click-modal="false"
    >
      <el-form ref="editFormRef" :model="editForm" :rules="rules" label-width="80px">
        <el-form-item label="分类名称" prop="name">
          <el-input v-model="editForm.name" placeholder="请输入分类名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="editForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入分类描述"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditDialog = false">取消</el-button>
        <el-button type="primary" :loading="updating" @click="handleUpdate">
          确定
        </el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useKnowledgeStore } from '@/stores/knowledge'
import { createCategory, updateCategory, deleteCategory } from '@/api/knowledge'
import type { DocumentCategory } from '@/api/knowledge'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
}>()

const knowledgeStore = useKnowledgeStore()

// 添加中状态
const adding = ref(false)
// 更新中状态
const updating = ref(false)
// 显示编辑对话框
const showEditDialog = ref(false)
// 编辑表单引用
const editFormRef = ref<FormInstance>()

// 新分类数据
const newCategory = ref({
  name: '',
  parentId: null as number | null,
})

// 编辑表单数据
const editForm = ref({
  id: 0,
  name: '',
  description: '',
})

// 表单验证规则
const rules: FormRules = {
  name: [
    { required: true, message: '请输入分类名称', trigger: 'blur' },
    { min: 2, max: 20, message: '长度在 2 到 20 个字符', trigger: 'blur' },
  ],
}

// 分类树数据（包含顶级选项）
const categoryTreeData = computed(() => {
  return [
    { id: 0, name: '顶级分类', children: knowledgeStore.categoryTree },
  ]
})

// 添加分类
async function handleAddCategory() {
  if (!newCategory.value.name.trim()) {
    ElMessage.warning('请输入分类名称')
    return
  }

  adding.value = true
  try {
    await createCategory({
      name: newCategory.value.name,
      parentId: newCategory.value.parentId || undefined,
    })
    ElMessage.success('添加成功')
    newCategory.value.name = ''
    newCategory.value.parentId = null
    await knowledgeStore.fetchCategories()
    emit('success')
  } catch (error) {
    ElMessage.error('添加失败')
  } finally {
    adding.value = false
  }
}

// 编辑分类
function handleEdit(data: DocumentCategory) {
  editForm.value = {
    id: data.id,
    name: data.name,
    description: data.description || '',
  }
  showEditDialog.value = true
}

// 更新分类
async function handleUpdate() {
  if (!editFormRef.value) return

  await editFormRef.value.validate(async (valid) => {
    if (!valid) return

    updating.value = true
    try {
      await updateCategory(editForm.value.id, {
        name: editForm.value.name,
        description: editForm.value.description,
      })
      ElMessage.success('更新成功')
      showEditDialog.value = false
      await knowledgeStore.fetchCategories()
      emit('success')
    } catch (error) {
      ElMessage.error('更新失败')
    } finally {
      updating.value = false
    }
  })
}

// 删除分类
async function handleDelete(data: DocumentCategory) {
  try {
    await ElMessageBox.confirm(
      `确定要删除分类"${data.name}"吗？删除后无法恢复`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )

    await deleteCategory(data.id)
    ElMessage.success('删除成功')
    await knowledgeStore.fetchCategories()
    emit('success')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// 拖拽判断
function allowDrop(draggingNode: any, dropNode: any, type: 'prev' | 'inner' | 'next'): boolean {
  return type !== 'inner' || dropNode.data.id !== 0
}

function allowDrag(draggingNode: any): boolean {
  return draggingNode.data.id !== 0
}

// 拖拽完成
async function handleDrop(draggingNode: any, dropNode: any, dropType: string) {
  // 这里可以调用API更新排序
  console.log('拖拽完成:', draggingNode.data, dropNode.data, dropType)
}

// 关闭对话框
function handleClose() {
  emit('update:modelValue', false)
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

    :deep(.el-tree-node__content):hover .node-actions {
      display: flex;
    }
  }
}
</style>
