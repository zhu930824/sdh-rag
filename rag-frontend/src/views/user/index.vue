<template>
  <div class="user-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="card-title">用户管理</span>
          <el-button type="primary" :icon="Plus" @click="handleAdd">新增用户</el-button>
        </div>
      </template>

      <!-- 搜索栏 -->
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="用户名">
          <el-input
            v-model="searchForm.keyword"
            placeholder="请输入用户名或昵称"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择状态" clearable>
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="searchForm.role" placeholder="请选择角色" clearable>
            <el-option label="管理员" value="管理员" />
            <el-option label="普通用户" value="普通用户" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 表格工具栏 -->
      <TableToolbar
        :columns="tableColumns"
        :density="tableDensity"
        @refresh="loadData"
        @column-change="handleColumnChange"
        @density-change="handleDensityChange"
      >
        <template #left>
          <el-button
            v-if="selectedRows.length > 0"
            type="danger"
            :icon="Delete"
            @click="handleBatchDelete"
          >
            批量删除 ({{ selectedRows.length }})
          </el-button>
        </template>
      </TableToolbar>

      <!-- 表格 -->
      <el-table
        v-loading="loading"
        :data="tableData"
        :size="tableDensity"
        stripe
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" />
        <el-table-column
          v-for="col in visibleColumns"
          :key="col.prop"
          :prop="col.prop"
          :label="col.label"
          :width="col.width"
          :fixed="col.fixed"
        >
          <template v-if="col.prop === 'role'" #default="{ row }">
            <el-tag :type="row.role === '管理员' ? 'danger' : 'primary'">{{ row.role }}</el-tag>
          </template>
          <template v-else-if="col.prop === 'status'" #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="1"
              :inactive-value="0"
              :loading="row.statusLoading"
              @change="handleStatusChange(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="primary" link @click="handleResetPassword(row)">重置密码</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <EmptyState type="user" description="暂无用户数据，点击上方按钮新增用户">
            <template #action>
              <el-button type="primary" :icon="Plus" @click="handleAdd">新增用户</el-button>
            </template>
          </EmptyState>
        </template>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @size-change="loadData"
        @current-change="loadData"
      />
    </el-card>

    <!-- 用户编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="500px"
      :close-on-click-modal="false"
      @closed="handleDialogClosed"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="80px"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="formData.username"
            placeholder="请输入用户名"
            :disabled="isEdit"
            maxlength="20"
          />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="formData.nickname" placeholder="请输入昵称" maxlength="20" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="formData.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item v-if="!isEdit" label="密码" prop="password">
          <el-input
            v-model="formData.password"
            type="password"
            placeholder="请输入密码"
            show-password
            maxlength="20"
          />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="formData.role" placeholder="请选择角色">
            <el-option label="管理员" value="管理员" />
            <el-option label="普通用户" value="普通用户" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { Plus, Search, Refresh, Delete } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import TableToolbar from '@/components/TableToolbar.vue'
import EmptyState from '@/components/EmptyState.vue'
import { showSuccess, showError, showSaveSuccess, showDeleteSuccess, showOperationError } from '@/utils/message'
import { showDeleteConfirm, showBatchDeleteConfirm, showResetPasswordConfirm } from '@/utils/confirm'
import { useLoading } from '@/composables'
import type { UserItem, UserQueryParams, UserFormData } from '@/api/userManage'
import { getUserList, createUser, updateUser, deleteUser, batchDeleteUsers, resetUserPassword, toggleUserStatus } from '@/api/userManage'
import type { ColumnConfig, TableDensity } from '@/components/TableToolbar.vue'

// 扩展用户类型，添加状态加载属性
interface UserTableRow extends UserItem {
  statusLoading?: boolean
}

// 加载状态
const { loading, withLoading } = useLoading()

// 搜索表单
const searchForm = reactive<UserQueryParams>({
  page: 1,
  pageSize: 10,
  keyword: '',
  status: undefined,
  role: undefined,
})

// 分页
const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0,
})

// 表格数据
const tableData = ref<UserTableRow[]>([])
const selectedRows = ref<UserTableRow[]>([])

// 表格列配置
const tableColumns = ref<ColumnConfig[]>([
  { prop: 'id', label: 'ID', visible: true },
  { prop: 'username', label: '用户名', visible: true },
  { prop: 'nickname', label: '昵称', visible: true },
  { prop: 'email', label: '邮箱', visible: true },
  { prop: 'role', label: '角色', visible: true },
  { prop: 'status', label: '状态', visible: true },
  { prop: 'createTime', label: '创建时间', visible: true },
])

// 表格密度
const tableDensity = ref<TableDensity>('default')

// 可见列
const visibleColumns = computed(() => tableColumns.value.filter(col => col.visible !== false))

// 对话框
const dialogVisible = ref(false)
const dialogTitle = ref('新增用户')
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()

// 表单数据
const formData = reactive<UserFormData>({
  username: '',
  nickname: '',
  email: '',
  role: '普通用户',
  status: 1,
  password: '',
})

// 表单验证规则
const formRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度在 3 到 20 个字符', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]+$/, message: '用户名只能包含字母、数字和下划线', trigger: 'blur' },
  ],
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { min: 2, max: 20, message: '昵称长度在 2 到 20 个字符', trigger: 'blur' },
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度在 6 到 20 个字符', trigger: 'blur' },
  ],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
}

// 加载数据
async function loadData() {
  await withLoading(async () => {
    try {
      const params: UserQueryParams = {
        ...searchForm,
        page: pagination.page,
        pageSize: pagination.pageSize,
      }
      const { data } = await getUserList(params)
      tableData.value = data.data.list
      pagination.total = data.data.total
    } catch (error) {
      showError('加载用户列表失败')
      // 使用模拟数据
      tableData.value = [
        { id: 1, username: 'admin', nickname: '管理员', email: 'admin@example.com', role: '管理员', status: 1, createTime: '2024-01-01 00:00:00' },
        { id: 2, username: 'user01', nickname: '用户一', email: 'user01@example.com', role: '普通用户', status: 1, createTime: '2024-01-10 10:30:00' },
        { id: 3, username: 'user02', nickname: '用户二', email: 'user02@example.com', role: '普通用户', status: 0, createTime: '2024-01-15 14:20:00' },
      ]
      pagination.total = 3
    }
  })
}

// 搜索
function handleSearch() {
  pagination.page = 1
  loadData()
}

// 重置
function handleReset() {
  searchForm.keyword = ''
  searchForm.status = undefined
  searchForm.role = undefined
  handleSearch()
}

// 列显示切换
function handleColumnChange(prop: string, visible: boolean) {
  const col = tableColumns.value.find(c => c.prop === prop)
  if (col) {
    col.visible = visible
  }
}

// 密度切换
function handleDensityChange(density: TableDensity) {
  tableDensity.value = density
}

// 选择变化
function handleSelectionChange(rows: UserTableRow[]) {
  selectedRows.value = rows
}

// 新增用户
function handleAdd() {
  isEdit.value = false
  dialogTitle.value = '新增用户'
  dialogVisible.value = true
}

// 编辑用户
function handleEdit(row: UserTableRow) {
  isEdit.value = true
  dialogTitle.value = '编辑用户'
  Object.assign(formData, {
    id: row.id,
    username: row.username,
    nickname: row.nickname,
    email: row.email,
    role: row.role,
    status: row.status,
  })
  dialogVisible.value = true
}

// 删除用户
async function handleDelete(row: UserTableRow) {
  const confirmed = await showDeleteConfirm(`用户 ${row.username}`)
  if (!confirmed) return

  try {
    await deleteUser(row.id)
    showDeleteSuccess()
    loadData()
  } catch (error) {
    showOperationError('删除')
  }
}

// 批量删除
async function handleBatchDelete() {
  const confirmed = await showBatchDeleteConfirm(selectedRows.value.length)
  if (!confirmed) return

  try {
    await batchDeleteUsers(selectedRows.value.map(row => row.id))
    showDeleteSuccess()
    selectedRows.value = []
    loadData()
  } catch (error) {
    showOperationError('批量删除')
  }
}

// 重置密码
async function handleResetPassword(row: UserTableRow) {
  const confirmed = await showResetPasswordConfirm()
  if (!confirmed) return

  try {
    const { data } = await resetUserPassword(row.id)
    showSuccess(`密码已重置为: ${data.data.password}`)
  } catch (error) {
    showOperationError('重置密码')
  }
}

// 状态切换
async function handleStatusChange(row: UserTableRow) {
  const statusText = row.status === 1 ? '启用' : '禁用'
  row.statusLoading = true
  try {
    await toggleUserStatus(row.id)
    showSuccess(`已${statusText}用户 ${row.username}`)
  } catch (error) {
    row.status = row.status === 1 ? 0 : 1 // 恢复原状态
    showOperationError(statusText)
  } finally {
    row.statusLoading = false
  }
}

// 提交表单
async function handleSubmit() {
  if (!formRef.value) return

  await formRef.value.validate(async valid => {
    if (!valid) return

    submitLoading.value = true
    try {
      if (isEdit.value) {
        await updateUser(formData.id!, formData)
        showSaveSuccess()
      } else {
        await createUser(formData)
        showSuccess('创建用户成功')
      }
      dialogVisible.value = false
      loadData()
    } catch (error) {
      showOperationError(isEdit.value ? '保存' : '创建')
    } finally {
      submitLoading.value = false
    }
  })
}

// 对话框关闭
function handleDialogClosed() {
  formRef.value?.resetFields()
  Object.assign(formData, {
    id: undefined,
    username: '',
    nickname: '',
    email: '',
    role: '普通用户',
    status: 1,
    password: '',
  })
}

// 初始化
onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.user-container {
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .card-title {
      font-size: 16px;
      font-weight: 500;
    }
  }

  .search-form {
    margin-bottom: 20px;
  }

  .pagination {
    margin-top: 20px;
    justify-content: flex-end;
  }
}
</style>
