<template>
  <div class="sensitive-container">
    <a-card>
      <template #title>
        <div class="card-header">
          <span class="card-title">敏感词管理</span>
          <a-button type="primary" @click="handleAdd">
            <template #icon><PlusOutlined /></template>
            添加敏感词
          </a-button>
        </div>
      </template>

      <!-- 搜索栏 -->
      <a-form layout="inline" :model="searchForm" class="search-form">
        <a-form-item label="敏感词">
          <a-input
            v-model:value="searchForm.keyword"
            placeholder="请输入敏感词"
            allow-clear
            @pressEnter="handleSearch"
          />
        </a-form-item>
        <a-form-item label="分类">
          <a-select
            v-model:value="searchForm.category"
            placeholder="请选择分类"
            allow-clear
            style="width: 120px"
          >
            <a-select-option value="politics">政治</a-select-option>
            <a-select-option value="porn">色情</a-select-option>
            <a-select-option value="violence">暴力</a-select-option>
            <a-select-option value="ad">广告</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item>
          <a-space>
            <a-button type="primary" @click="handleSearch">
              <template #icon><SearchOutlined /></template>
              搜索
            </a-button>
            <a-button @click="handleReset">
              <template #icon><ReloadOutlined /></template>
              重置
            </a-button>
          </a-space>
        </a-form-item>
      </a-form>

      <!-- 表格工具栏 -->
      <TableToolbar
        :columns="tableColumns"
        :density="tableDensity"
        @refresh="loadData"
        @column-change="handleColumnChange"
        @density-change="handleDensityChange"
      >
        <template #left>
          <a-button
            v-if="selectedRowKeys.length > 0"
            danger
            @click="handleBatchDelete"
          >
            <template #icon><DeleteOutlined /></template>
            批量删除 ({{ selectedRowKeys.length }})
          </a-button>
        </template>
      </TableToolbar>

      <!-- 表格 -->
      <a-table
        :loading="loading"
        :data-source="tableData"
        :columns="tableColumnsConfig"
        :size="tableDensity"
        :row-selection="rowSelection"
        :pagination="false"
        :scroll="{ x: 900 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'category'">
            <a-tag :color="getCategoryColor(record.category)">
              {{ getCategoryText(record.category) }}
            </a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'status'">
            <a-switch
              :checked="record.status === 'enabled'"
              :loading="record.statusLoading"
              @change="(checked: boolean) => handleStatusChange(record, checked)"
            />
          </template>
          <template v-else-if="column.dataIndex === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="handleEdit(record)">编辑</a-button>
              <a-popconfirm
                title="确定要删除该敏感词吗？"
                ok-text="确定"
                cancel-text="取消"
                @confirm="handleDelete(record)"
              >
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
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

    <!-- 添加/编辑对话框 -->
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
        <a-form-item label="词汇" name="word">
          <a-input
            v-model:value="formData.word"
            placeholder="请输入敏感词"
            :maxlength="50"
          />
        </a-form-item>
        <a-form-item label="分类" name="category">
          <a-select v-model:value="formData.category" placeholder="请选择分类">
            <a-select-option value="politics">政治</a-select-option>
            <a-select-option value="porn">色情</a-select-option>
            <a-select-option value="violence">暴力</a-select-option>
            <a-select-option value="ad">广告</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="状态" name="status">
          <a-radio-group v-model:value="formData.status">
            <a-radio value="enabled">启用</a-radio>
            <a-radio value="disabled">禁用</a-radio>
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
import { PlusOutlined, SearchOutlined, ReloadOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import type { FormInstance, Rule } from 'ant-design-vue/es/form'
import type { TableColumnType, TableProps } from 'ant-design-vue'
import TableToolbar from '@/components/TableToolbar.vue'
import { showSuccess, showSaveSuccess, showDeleteSuccess, showOperationError } from '@/utils/message'
import { showBatchDeleteConfirm } from '@/utils/confirm'
import { useLoading } from '@/composables'
import type { ColumnConfig, TableDensity } from '@/components/TableToolbar.vue'

// 敏感词数据类型
interface SensitiveWord {
  id: number
  word: string
  category: string
  status: string
  createTime: string
  statusLoading?: boolean
}

// 搜索表单类型
interface SearchForm {
  keyword: string
  category: string | undefined
}

// 表单数据类型
interface FormData {
  id?: number
  word: string
  category: string
  status: string
}

const { loading, withLoading } = useLoading()

// 搜索表单
const searchForm = reactive<SearchForm>({
  keyword: '',
  category: undefined,
})

// 分页配置
const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0,
})

// 表格数据
const tableData = ref<SensitiveWord[]>([])
const selectedRowKeys = ref<number[]>([])

// 表格列配置
const tableColumns = ref<ColumnConfig[]>([
  { prop: 'id', label: 'ID', visible: true },
  { prop: 'word', label: '词汇', visible: true },
  { prop: 'category', label: '分类', visible: true },
  { prop: 'status', label: '状态', visible: true },
  { prop: 'createTime', label: '创建时间', visible: true },
])

// 表格密度
const tableDensity = ref<TableDensity>('default')

// 表格列配置计算属性
const tableColumnsConfig = computed<TableColumnType[]>(() => {
  const columns: TableColumnType[] = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    { title: '词汇', dataIndex: 'word', key: 'word', width: 200 },
    { title: '分类', dataIndex: 'category', key: 'category', width: 100 },
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

// 行选择配置
const rowSelection: TableProps['rowSelection'] = computed(() => ({
  selectedRowKeys: selectedRowKeys.value,
  onChange: (keys: number[]) => {
    selectedRowKeys.value = keys
  },
}))

// 对话框相关
const dialogVisible = ref(false)
const dialogTitle = ref('添加敏感词')
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()

// 表单数据
const formData = reactive<FormData>({
  word: '',
  category: '',
  status: 'enabled',
})

// 表单验证规则
const formRules: Record<string, Rule[]> = {
  word: [
    { required: true, message: '请输入敏感词', trigger: 'blur' },
    { min: 1, max: 50, message: '词汇长度在 1 到 50 个字符', trigger: 'blur' },
  ],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
}

// 分类颜色映射
const categoryColorMap: Record<string, string> = {
  politics: 'red',
  porn: 'orange',
  violence: 'purple',
  ad: 'blue',
}

// 分类文本映射
const categoryTextMap: Record<string, string> = {
  politics: '政治',
  porn: '色情',
  violence: '暴力',
  ad: '广告',
}

// 获取分类颜色
function getCategoryColor(category: string): string {
  return categoryColorMap[category] || 'default'
}

// 获取分类文本
function getCategoryText(category: string): string {
  return categoryTextMap[category] || category
}

// 加载数据
async function loadData() {
  await withLoading(async () => {
    // 模拟 API 调用
    await new Promise(resolve => setTimeout(resolve, 500))

    // 模拟数据
    const mockData: SensitiveWord[] = [
      { id: 1, word: '敏感词1', category: 'politics', status: 'enabled', createTime: '2024-01-01 10:00:00' },
      { id: 2, word: '敏感词2', category: 'porn', status: 'enabled', createTime: '2024-01-02 11:30:00' },
      { id: 3, word: '敏感词3', category: 'violence', status: 'disabled', createTime: '2024-01-03 14:20:00' },
      { id: 4, word: '广告词1', category: 'ad', status: 'enabled', createTime: '2024-01-04 09:15:00' },
      { id: 5, word: '敏感词4', category: 'politics', status: 'enabled', createTime: '2024-01-05 16:45:00' },
    ]

    // 根据搜索条件过滤
    let filteredData = mockData
    if (searchForm.keyword) {
      filteredData = filteredData.filter(item => item.word.includes(searchForm.keyword))
    }
    if (searchForm.category) {
      filteredData = filteredData.filter(item => item.category === searchForm.category)
    }

    tableData.value = filteredData
    pagination.total = filteredData.length
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
  searchForm.category = undefined
  handleSearch()
}

// 列显示切换
function handleColumnChange(prop: string, visible: boolean) {
  const col = tableColumns.value.find(c => c.prop === prop)
  if (col) {
    col.visible = visible
  }
}

// 表格密度切换
function handleDensityChange(density: TableDensity) {
  tableDensity.value = density
}

// 添加敏感词
function handleAdd() {
  isEdit.value = false
  dialogTitle.value = '添加敏感词'
  dialogVisible.value = true
}

// 编辑敏感词
function handleEdit(row: SensitiveWord) {
  isEdit.value = true
  dialogTitle.value = '编辑敏感词'
  Object.assign(formData, {
    id: row.id,
    word: row.word,
    category: row.category,
    status: row.status,
  })
  dialogVisible.value = true
}

// 删除敏感词
async function handleDelete(row: SensitiveWord) {
  try {
    // 模拟 API 调用
    await new Promise(resolve => setTimeout(resolve, 300))
    tableData.value = tableData.value.filter(item => item.id !== row.id)
    pagination.total -= 1
    showDeleteSuccess()
  } catch (error) {
    showOperationError('删除')
  }
}

// 批量删除
async function handleBatchDelete() {
  const confirmed = await showBatchDeleteConfirm(selectedRowKeys.value.length)
  if (!confirmed) return

  try {
    // 模拟 API 调用
    await new Promise(resolve => setTimeout(resolve, 300))
    tableData.value = tableData.value.filter(item => !selectedRowKeys.value.includes(item.id))
    pagination.total -= selectedRowKeys.value.length
    selectedRowKeys.value = []
    showDeleteSuccess()
  } catch (error) {
    showOperationError('批量删除')
  }
}

// 状态切换
async function handleStatusChange(row: SensitiveWord, checked: boolean) {
  const statusText = checked ? '启用' : '禁用'
  row.statusLoading = true
  try {
    // 模拟 API 调用
    await new Promise(resolve => setTimeout(resolve, 300))
    row.status = checked ? 'enabled' : 'disabled'
    showSuccess(`已${statusText}敏感词 ${row.word}`)
  } catch (error) {
    showOperationError(statusText)
  } finally {
    row.statusLoading = false
  }
}

// 提交表单
async function handleSubmit() {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
    submitLoading.value = true
    try {
      // 模拟 API 调用
      await new Promise(resolve => setTimeout(resolve, 500))

      if (isEdit.value) {
        // 编辑模式：更新数据
        const index = tableData.value.findIndex(item => item.id === formData.id)
        if (index !== -1) {
          tableData.value[index] = {
            ...tableData.value[index],
            word: formData.word,
            category: formData.category,
            status: formData.status,
          }
        }
        showSaveSuccess()
      } else {
        // 添加模式：新增数据
        const newItem: SensitiveWord = {
          id: Date.now(),
          word: formData.word,
          category: formData.category,
          status: formData.status,
          createTime: new Date().toLocaleString('zh-CN'),
        }
        tableData.value.unshift(newItem)
        pagination.total += 1
        showSuccess('添加成功')
      }
      dialogVisible.value = false
    } catch (error) {
      showOperationError(isEdit.value ? '保存' : '添加')
    } finally {
      submitLoading.value = false
    }
  } catch {
    // 表单验证失败
  }
}

// 对话框关闭
function handleDialogClosed() {
  formRef.value?.resetFields()
  Object.assign(formData, {
    id: undefined,
    word: '',
    category: '',
    status: 'enabled',
  })
}

// 初始化
onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.sensitive-container {
  :deep(.ant-card-head-title) {
    width: 100%;
  }

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
    display: flex;
    justify-content: flex-end;
  }
}
</style>
