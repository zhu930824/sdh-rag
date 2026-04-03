<template>
  <div class="role-container">
    <a-card>
      <template #title>
        <div class="card-header">
          <span class="card-title">角色管理</span>
          <a-button type="primary" @click="handleAdd">
            <template #icon><PlusOutlined /></template>
            新增角色
          </a-button>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div class="search-toolbar">
        <a-form layout="inline" :model="searchForm" class="search-form">
          <a-form-item label="关键词">
            <a-input
              v-model:value="searchForm.keyword"
              placeholder="请输入角色名称或编码"
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
        class="role-table"
        :loading="loading"
        :data-source="tableData"
        :columns="tableColumnsConfig"
        :size="tableDensity"
        :row-selection="rowSelection"
        :pagination="false"
        :scroll="{ x: 900 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'code'">
            <a-tag color="blue">{{ record.code }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'permissions'">
            <a-tooltip v-if="record.permissions">
              <template #title>
                <div v-for="perm in record.permissions.split(',')" :key="perm">{{ getMenuName(perm) }}</div>
              </template>
              <a-tag>{{ record.permissions.split(',').length }} 项权限</a-tag>
            </a-tooltip>
            <span v-else class="text-gray">未配置</span>
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

    <!-- 角色编辑对话框 -->
    <a-modal
      v-model:open="dialogVisible"
      :title="dialogTitle"
      :width="600"
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
        <a-form-item label="角色名称" name="name">
          <a-input v-model:value="formData.name" placeholder="请输入角色名称" :maxlength="20" />
        </a-form-item>
        <a-form-item label="角色编码" name="code">
          <a-input
            v-model:value="formData.code"
            placeholder="请输入角色编码"
            :maxlength="20"
            :disabled="isEdit"
          />
        </a-form-item>
        <a-form-item label="描述" name="description">
          <a-textarea
            v-model:value="formData.description"
            placeholder="请输入角色描述"
            :maxlength="200"
            :rows="2"
          />
        </a-form-item>
        <a-form-item label="菜单权限" name="permissions">
          <div class="permission-tree-wrapper">
            <div class="permission-toolbar">
              <a-checkbox :checked="isAllChecked" :indeterminate="isIndeterminate" @change="handleCheckAll">
                全选
              </a-checkbox>
              <a-button type="link" size="small" @click="handleExpandAll">展开全部</a-button>
              <a-button type="link" size="small" @click="handleCollapseAll">收起全部</a-button>
            </div>
            <a-tree
              ref="treeRef"
              v-model:checkedKeys="checkedKeys"
              :tree-data="menuTreeData"
              :field-names="{ title: 'label', key: 'value', children: 'children' }"
              checkable
              :expanded-keys="expandedKeys"
              @expand="handleExpand"
            />
          </div>
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
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { PlusOutlined, SearchOutlined, ReloadOutlined, DeleteOutlined, SyncOutlined } from '@ant-design/icons-vue'
import type { FormInstance, Rule } from 'ant-design-vue/es/form'
import type { TableColumnType, TableProps, TreeProps } from 'ant-design-vue'
import TableToolbar from '@/components/TableToolbar.vue'
import { showSuccess, showError, showSaveSuccess, showDeleteSuccess, showOperationError } from '@/utils/message'
import { showDeleteConfirm, showBatchDeleteConfirm } from '@/utils/confirm'
import { useLoading } from '@/composables'
import type { RoleItem, RoleQueryParams, RoleFormData } from '@/api/roleManage'
import { getRoleList, createRole, updateRole, deleteRole, batchDeleteRoles, toggleRoleStatus } from '@/api/roleManage'
import type { ColumnConfig, TableDensity } from '@/components/TableToolbar.vue'

interface RoleTableRow extends RoleItem {
  statusLoading?: boolean
}

// 菜单树数据
const menuTreeData = ref<TreeProps['treeData']>([
  {
    label: '首页',
    value: '/dashboard',
    children: [],
  },
  {
    label: '智能问答',
    value: '/chat',
    children: [],
  },
  {
    label: '知识管理',
    value: 'knowledge-group',
    children: [
      { label: '知识库', value: '/knowledge-base' },
      { label: '文档管理', value: '/knowledge' },
      { label: '知识图谱', value: '/graph' },
    ],
  },
  {
    label: '工作流编排',
    value: '/workflow',
    children: [],
  },
  {
    label: '大模型管理',
    value: '/model',
    children: [],
  },
  {
    label: '数据分析',
    value: 'analysis-group',
    children: [
      { label: '数据统计', value: '/stats' },
      { label: '热点词分析', value: '/hotwords' },
      { label: '问答评价', value: '/feedback' },
      { label: '审核中心', value: '/approval' },
    ],
  },
  {
    label: '内容管理',
    value: 'content-group',
    children: [
      { label: '文档预处理', value: '/process-task' },
      { label: '标签管理', value: '/tag' },
    ],
  },
  {
    label: '系统管理',
    value: 'system-group',
    children: [
      { label: '用户管理', value: '/user' },
      { label: '角色管理', value: '/role' },
      { label: '日志管理', value: '/log' },
      { label: '敏感词管理', value: '/sensitive' },
      { label: '公告管理', value: '/announcement' },
      { label: '系统设置', value: '/settings' },
    ],
  },
])

// 获取所有菜单路径（用于全选）
const allMenuPaths = computed(() => {
  const paths: string[] = []
  function collect(items: any[]) {
    items.forEach(item => {
      if (item.value.startsWith('/')) {
        paths.push(item.value)
      }
      if (item.children) {
        collect(item.children)
      }
    })
  }
  collect(menuTreeData.value || [])
  return paths
})

// 菜单名称映射
const menuNameMap = computed(() => {
  const map: Record<string, string> = {}
  function collect(items: any[]) {
    items.forEach(item => {
      if (item.value.startsWith('/')) {
        map[item.value] = item.label
      }
      if (item.children) {
        collect(item.children)
      }
    })
  }
  collect(menuTreeData.value || [])
  return map
})

function getMenuName(path: string): string {
  return menuNameMap.value[path] || path
}

const { loading, withLoading } = useLoading()

const searchForm = reactive<RoleQueryParams>({
  page: 1,
  pageSize: 10,
  keyword: '',
  status: undefined,
})

const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0,
})

const tableData = ref<RoleTableRow[]>([])
const selectedRowKeys = ref<number[]>([])

const tableColumns = ref<ColumnConfig[]>([
  { prop: 'id', label: 'ID', visible: true },
  { prop: 'name', label: '角色名称', visible: true },
  { prop: 'code', label: '角色编码', visible: true },
  { prop: 'permissions', label: '菜单权限', visible: true },
  { prop: 'status', label: '状态', visible: true },
  { prop: 'createTime', label: '创建时间', visible: true },
])

const tableDensity = ref<TableDensity>('default')

const tableColumnsConfig = computed<TableColumnType[]>(() => {
  const columns: TableColumnType[] = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    { title: '角色名称', dataIndex: 'name', key: 'name', width: 120 },
    { title: '角色编码', dataIndex: 'code', key: 'code', width: 120 },
    { title: '菜单权限', dataIndex: 'permissions', key: 'permissions', width: 120 },
    { title: '状态', dataIndex: 'status', key: 'status', width: 80 },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180 },
    { title: '操作', dataIndex: 'action', key: 'action', width: 150, fixed: 'right' },
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
const dialogTitle = ref('新增角色')
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const treeRef = ref()

const formData = reactive<RoleFormData>({
  name: '',
  code: '',
  description: '',
  permissions: [],
  status: 1,
})

const checkedKeys = ref<string[]>([])
const expandedKeys = ref<string[]>([])

// 计算全选状态
const isAllChecked = computed(() => {
  return allMenuPaths.value.length > 0 && allMenuPaths.value.every(p => checkedKeys.value.includes(p))
})

const isIndeterminate = computed(() => {
  const checked = allMenuPaths.value.filter(p => checkedKeys.value.includes(p))
  return checked.length > 0 && checked.length < allMenuPaths.value.length
})

function handleCheckAll(e: any) {
  if (e.target.checked) {
    checkedKeys.value = [...allMenuPaths.value]
  } else {
    checkedKeys.value = []
  }
}

function handleExpandAll() {
  const keys: string[] = []
  function collect(items: any[]) {
    items.forEach(item => {
      if (item.children && item.children.length > 0) {
        keys.push(item.value)
        collect(item.children)
      }
    })
  }
  collect(menuTreeData.value || [])
  expandedKeys.value = keys
}

function handleCollapseAll() {
  expandedKeys.value = []
}

function handleExpand(keys: string[]) {
  expandedKeys.value = keys
}

const formRules: Record<string, Rule[]> = {
  name: [
    { required: true, message: '请输入角色名称', trigger: 'blur' },
    { min: 2, max: 20, message: '角色名称长度在 2 到 20 个字符', trigger: 'blur' },
  ],
  code: [
    { required: true, message: '请输入角色编码', trigger: 'blur' },
    { min: 2, max: 20, message: '角色编码长度在 2 到 20 个字符', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]+$/, message: '角色编码只能包含字母、数字和下划线', trigger: 'blur' },
  ],
}

async function loadData() {
  await withLoading(async () => {
    try {
      const params: RoleQueryParams = {
        ...searchForm,
        page: pagination.page,
        pageSize: pagination.pageSize,
      }
      const res = await getRoleList(params)
      tableData.value = res.data.records || []
      pagination.total = res.data.total || 0
    } catch (error) {
      showError('加载角色列表失败')
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
  dialogTitle.value = '新增角色'
  checkedKeys.value = []
  expandedKeys.value = []
  dialogVisible.value = true
}

function handleEdit(row: RoleTableRow) {
  isEdit.value = true
  dialogTitle.value = '编辑角色'
  Object.assign(formData, {
    id: row.id,
    name: row.name,
    code: row.code,
    description: row.description,
    status: row.status,
  })
  // 解析权限字符串为数组
  checkedKeys.value = row.permissions ? row.permissions.split(',').filter(p => p.startsWith('/')) : []
  handleExpandAll()
  dialogVisible.value = true
}

async function handleDelete(row: RoleTableRow) {
  const confirmed = await showDeleteConfirm(`角色 ${row.name}`)
  if (!confirmed) return

  try {
    await deleteRole(row.id)
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
    const res = await batchDeleteRoles(selectedRowKeys.value)
    showSuccess(`成功删除 ${res.data.success} 个角色`)
    if (res.data.fail > 0) {
      showError(`${res.data.fail} 个角色删除失败（可能已被用户使用）`)
    }
    selectedRowKeys.value = []
    loadData()
  } catch (error) {
    showOperationError('批量删除')
  }
}

async function handleStatusChange(row: RoleTableRow) {
  const statusText = row.status === 1 ? '禁用' : '启用'
  row.statusLoading = true
  try {
    await toggleRoleStatus(row.id)
    row.status = row.status === 1 ? 0 : 1
    showSuccess(`已${statusText}角色 ${row.name}`)
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
      // 只保存以 / 开头的权限路径
      const permissions = checkedKeys.value.filter(k => k.startsWith('/'))
      const submitData = {
        ...formData,
        permissions,
      }

      if (isEdit.value) {
        await updateRole(formData.id!, submitData)
        showSaveSuccess()
      } else {
        await createRole(submitData)
        showSuccess('创建角色成功')
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
    name: '',
    code: '',
    description: '',
    permissions: [],
    status: 1,
  })
  checkedKeys.value = []
  expandedKeys.value = []
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.role-container {
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

  .role-table {
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

.permission-tree-wrapper {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  padding: 12px;
  max-height: 300px;
  overflow-y: auto;
}

.permission-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-light);
}

.text-gray {
  color: var(--text-tertiary);
}
</style>
