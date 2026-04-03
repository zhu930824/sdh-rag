<template>
  <div class="user-container">
    <a-card>
      <template #title>
        <div class="card-header">
          <span class="card-title">用户管理</span>
          <a-button type="primary" @click="handleAdd">
            <template #icon><PlusOutlined /></template>
            新增用户
          </a-button>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div class="search-toolbar">
        <a-form layout="inline" :model="searchForm" class="search-form">
          <a-form-item label="用户名">
            <a-input
              v-model:value="searchForm.keyword"
              placeholder="请输入用户名或昵称"
              allow-clear
              @pressEnter="handleSearch"
            />
          </a-form-item>
          <a-form-item label="状态">
            <a-select
              v-model:value="searchForm.status"
              placeholder="请选择状态"
              allow-clear
              style="width: 120px"
            >
              <a-select-option :value="1">启用</a-select-option>
              <a-select-option :value="0">禁用</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="角色">
            <a-select
              v-model:value="searchForm.role"
              placeholder="请选择角色"
              allow-clear
              style="width: 120px"
            >
              <a-select-option v-for="role in roleList" :key="role.code" :value="role.code">
                {{ role.name }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item>
            <a-button type="primary" @click="handleSearch">
              <template #icon><SearchOutlined /></template>
              搜索
            </a-button>
          </a-form-item>
        </a-form>
        <div class="toolbar-actions">
          <a-space>
            <a-button v-if="selectedRowKeys.length > 0" danger @click="handleBatchDelete">
              <template #icon><DeleteOutlined /></template>
              批量删除 ({{ selectedRowKeys.length }})
            </a-button>
            <a-button @click="handleReset">
              <template #icon><ReloadOutlined /></template>
              重置
            </a-button>
            <a-button @click="loadData">
              <template #icon><SyncOutlined /></template>
              刷新
            </a-button>
            <TableToolbar
              :columns="tableColumns"
              :density="tableDensity"
              @column-change="handleColumnChange"
              @density-change="handleDensityChange"
            />
          </a-space>
        </div>
      </div>

      <!-- 表格 -->
      <a-table
        class="user-table"
        :loading="loading"
        :data-source="tableData"
        :columns="tableColumnsConfig"
        :size="tableDensity"
        :row-selection="rowSelection"
        :pagination="false"
        :scroll="{ x: 1000 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'role'">
            <a-tag :color="record.role === 'admin' ? 'red' : 'blue'">{{ getRoleName(record.role) }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'status'">
            <a-switch
              :checked="record.status === 1"
              :loading="record.statusLoading"
              @change="handleStatusChange(record)"
            />
          </template>
          <template v-else-if="column.dataIndex === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="handleEdit(record)">编辑</a-button>
              <a-button type="link" size="small" @click="handleResetPassword(record)">重置密码</a-button>
              <a-button type="link" size="small" danger @click="handleDelete(record)">删除</a-button>
            </a-space>
          </template>
        </template>
      </a-table>

      <!-- 分页 -->
      <div class="pagination">
        <a-pagination
          v-model:current="pagination.page"
          v-model:pageSize="pagination.pageSize"
          :total="pagination.total"
          :pageSizeOptions="['10', '20', '50', '100']"
          show-size-changer
          show-quick-jumper
          :show-total="(total: number) => `共 ${total} 条`"
          @change="loadData"
          @showSizeChange="loadData"
        />
      </div>
    </a-card>

    <!-- 用户编辑对话框 -->
    <a-modal
      v-model:open="dialogVisible"
      :title="dialogTitle"
      :width="500"
      :maskClosable="false"
      @cancel="handleDialogClosed"
    >
      <a-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        :label-col="{ span: 4 }"
        :wrapper-col="{ span: 20 }"
      >
        <a-form-item label="用户名" name="username">
          <a-input
            v-model:value="formData.username"
            placeholder="请输入用户名"
            :disabled="isEdit"
            :maxlength="20"
          />
        </a-form-item>
        <a-form-item label="昵称" name="nickname">
          <a-input v-model:value="formData.nickname" placeholder="请输入昵称" :maxlength="20" />
        </a-form-item>
        <a-form-item label="邮箱" name="email">
          <a-input v-model:value="formData.email" placeholder="请输入邮箱" />
        </a-form-item>
        <a-form-item v-if="!isEdit" label="密码" name="password">
          <a-input-password
            v-model:value="formData.password"
            placeholder="请输入密码"
            :maxlength="20"
          />
        </a-form-item>
        <a-form-item label="角色" name="role">
          <a-select v-model:value="formData.role" placeholder="请选择角色">
            <a-select-option v-for="role in roleList" :key="role.code" :value="role.code">
              {{ role.name }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="状态" name="status">
          <a-radio-group v-model:value="formData.status">
            <a-radio :value="1">启用</a-radio>
            <a-radio :value="0">禁用</a-radio>
          </a-radio-group>
        </a-form-item>
      </a-form>
      <template #footer>
        <a-button @click="dialogVisible = false">取消</a-button>
        <a-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</a-button>
      </template>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { PlusOutlined, SearchOutlined, ReloadOutlined, DeleteOutlined, SyncOutlined } from '@ant-design/icons-vue'
import type { FormInstance, Rule } from 'ant-design-vue/es/form'
import type { TableColumnType, TableProps } from 'ant-design-vue'
import TableToolbar from '@/components/TableToolbar.vue'
import { showSuccess, showError, showSaveSuccess, showDeleteSuccess, showOperationError } from '@/utils/message'
import { showDeleteConfirm, showBatchDeleteConfirm, showResetPasswordConfirm } from '@/utils/confirm'
import { useLoading } from '@/composables'
import type { UserItem, UserQueryParams, UserFormData } from '@/api/userManage'
import { getUserList, createUser, updateUser, deleteUser, batchDeleteUsers, resetUserPassword, toggleUserStatus } from '@/api/userManage'
import { getAllRoles } from '@/api/roleManage'
import type { ColumnConfig, TableDensity } from '@/components/TableToolbar.vue'

interface RoleItem {
  id: number
  name: string
  code: string
}

interface UserTableRow extends UserItem {
  statusLoading?: boolean
}

const { loading, withLoading } = useLoading()

// 角色列表
const roleList = ref<RoleItem[]>([])

// 获取角色列表
async function loadRoleList() {
  try {
    const res = await getAllRoles()
    roleList.value = res.data || []
  } catch (error) {
    console.error('获取角色列表失败:', error)
  }
}

// 根据角色code获取角色名称
function getRoleName(roleCode: string): string {
  const role = roleList.value.find(r => r.code === roleCode)
  return role?.name || roleCode
}

const searchForm = reactive<UserQueryParams>({
  page: 1,
  pageSize: 10,
  keyword: '',
  status: undefined,
  role: undefined,
})

const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0,
})

const tableData = ref<UserTableRow[]>([])
const selectedRowKeys = ref<number[]>([])

const tableColumns = ref<ColumnConfig[]>([
  { prop: 'id', label: 'ID', visible: true },
  { prop: 'username', label: '用户名', visible: true },
  { prop: 'nickname', label: '昵称', visible: true },
  { prop: 'email', label: '邮箱', visible: true },
  { prop: 'role', label: '角色', visible: true },
  { prop: 'status', label: '状态', visible: true },
  { prop: 'createTime', label: '创建时间', visible: true },
])

const tableDensity = ref<TableDensity>('default')

const tableColumnsConfig = computed<TableColumnType[]>(() => {
  const columns: TableColumnType[] = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    { title: '用户名', dataIndex: 'username', key: 'username', width: 120 },
    { title: '昵称', dataIndex: 'nickname', key: 'nickname', width: 120 },
    { title: '邮箱', dataIndex: 'email', key: 'email', width: 200 },
    { title: '角色', dataIndex: 'role', key: 'role', width: 100 },
    { title: '状态', dataIndex: 'status', key: 'status', width: 80 },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180 },
    { title: '操作', dataIndex: 'action', key: 'action', width: 200, fixed: 'right' },
  ]
  return columns.filter(col => {
    const prop = col.dataIndex as string
    const config = tableColumns.value.find(c => c.prop === prop)
    return config ? config.visible !== false : true
  })
})

const rowSelection: TableProps['rowSelection'] = computed(() => ({
  selectedRowKeys: selectedRowKeys.value,
  onChange: (keys: number[]) => {
    selectedRowKeys.value = keys
  },
}))

const dialogVisible = ref(false)
const dialogTitle = ref('新增用户')
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()

const formData = reactive<UserFormData>({
  username: '',
  nickname: '',
  email: '',
  role: '',
  status: 1,
  password: '',
})

const formRules: Record<string, Rule[]> = {
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

async function loadData() {
  await withLoading(async () => {
    try {
      const params: UserQueryParams = {
        ...searchForm,
        page: pagination.page,
        pageSize: pagination.pageSize,
      }
      const res = await getUserList(params)
      tableData.value = res.data.records || []
      pagination.total = res.data.total || 0
    } catch (error) {
      showError('加载用户列表失败')
      tableData.value = []
      pagination.total = 0
    }
  })
}

function handleSearch() {
  pagination.page = 1
  loadData()
}

function handleReset() {
  searchForm.keyword = ''
  searchForm.status = undefined
  searchForm.role = undefined
  handleSearch()
}

function handleColumnChange(prop: string, visible: boolean) {
  const col = tableColumns.value.find(c => c.prop === prop)
  if (col) {
    col.visible = visible
  }
}

function handleDensityChange(density: TableDensity) {
  tableDensity.value = density
}

function handleAdd() {
  isEdit.value = false
  dialogTitle.value = '新增用户'
  dialogVisible.value = true
}

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

async function handleBatchDelete() {
  const confirmed = await showBatchDeleteConfirm(selectedRowKeys.value.length)
  if (!confirmed) return

  try {
    await batchDeleteUsers(selectedRowKeys.value)
    showDeleteSuccess()
    selectedRowKeys.value = []
    loadData()
  } catch (error) {
    showOperationError('批量删除')
  }
}

async function handleResetPassword(row: UserTableRow) {
  const confirmed = await showResetPasswordConfirm()
  if (!confirmed) return

  try {
    const res = await resetUserPassword(row.id)
    showSuccess(`密码已重置为: ${res.data.password}`)
  } catch (error) {
    showOperationError('重置密码')
  }
}

async function handleStatusChange(row: UserTableRow) {
  const statusText = row.status === 1 ? '禁用' : '启用'
  row.statusLoading = true
  try {
    await toggleUserStatus(row.id)
    row.status = row.status === 1 ? 0 : 1
    showSuccess(`已${statusText}用户 ${row.username}`)
  } catch (error) {
    showOperationError(statusText)
  } finally {
    row.statusLoading = false
  }
}

async function handleSubmit() {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
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
  } catch {
    // 表单验证失败
  }
}

function handleDialogClosed() {
  formRef.value?.resetFields()
  Object.assign(formData, {
    id: undefined,
    username: '',
    nickname: '',
    email: '',
    role: '',
    status: 1,
    password: '',
  })
}

onMounted(() => {
  loadRoleList()
  loadData()
})
</script>

<style scoped lang="scss">
.user-container {
  height: calc(100vh - 64px - 48px);
  overflow-y: auto;

  :deep(.ant-card) {
    height: 100%;
    display: flex;
    flex-direction: column;

    .ant-card-head {
      flex-shrink: 0;
    }

    .ant-card-body {
      flex: 1;
      min-height: 0;
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }
  }

  :deep(.ant-card-head-title) {
    width: 100%;
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .card-title {
      font-family: var(--font-display);
      font-size: 18px;
      font-weight: var(--font-weight-semibold);
    }
  }

  .search-toolbar {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    margin-bottom: 16px;
    flex-shrink: 0;
    flex-wrap: wrap;
    gap: 12px;
  }

  .search-form {
    flex: 1;
  }

  .toolbar-actions {
    flex-shrink: 0;
    padding-top: 4px;
    display: flex;
    align-items: center;

    :deep(.table-toolbar) {
      display: inline-flex;
      margin-left: 8px;
    }
  }

  .user-table {
    flex: 1;
    min-height: 0;
    overflow: hidden;

    :deep(.ant-table-wrapper) {
      height: 100%;
    }

    :deep(.ant-spin-nested-loading) {
      height: 100%;
    }

    :deep(.ant-spin-container) {
      height: 100%;
      display: flex;
      flex-direction: column;
    }

    :deep(.ant-table) {
      flex: 1;
      display: flex;
      flex-direction: column;
    }

    :deep(.ant-table-container) {
      flex: 1;
      display: flex;
      flex-direction: column;
    }

    :deep(.ant-table-body) {
      flex: 1;
      overflow: auto !important;
    }
  }

  .pagination {
    margin-top: 16px;
    display: flex;
    justify-content: flex-end;
    flex-shrink: 0;
  }
}
</style>
