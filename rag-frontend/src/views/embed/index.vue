<template>
  <div class="embed-container">
    <a-card>
      <template #title>
        <div class="card-header">
          <span class="card-title">嵌入组件配置</span>
          <a-button type="primary" @click="handleAdd">
            <template #icon><PlusOutlined /></template>
            添加配置
          </a-button>
        </div>
      </template>

      <a-table :columns="columns" :data-source="tableData" :loading="loading" :pagination="false">
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'theme'">
            <a-tag>{{ record.theme === 'light' ? '浅色' : '深色' }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'position'">
            {{ getPositionText(record.position) }}
          </template>
          <template v-else-if="column.dataIndex === 'status'">
            <a-tag :color="record.status === 1 ? 'success' : 'default'">
              {{ record.status === 1 ? '启用' : '禁用' }}
            </a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="handleEdit(record)">编辑</a-button>
              <a-button type="link" size="small" @click="handleToggle(record)">
                {{ record.status === 1 ? '禁用' : '启用' }}
              </a-button>
              <a-button type="link" size="small" @click="handlePreview(record)">预览</a-button>
              <a-popconfirm title="确定删除？" @confirm="handleDelete(record)">
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>

      <div class="pagination">
        <a-pagination v-model:current="pagination.page" v-model:page-size="pagination.pageSize" :total="pagination.total" show-size-changer :show-total="(total: number) => `共 ${total} 条`" @change="loadData" />
      </div>
    </a-card>

    <a-modal v-model:open="dialogVisible" :title="dialogTitle" :width="650" @ok="handleSubmit">
      <a-form :model="formData" :label-col="{ span: 5 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="配置名称" required>
          <a-input v-model:value="formData.name" placeholder="请输入配置名称" />
        </a-form-item>
        <a-form-item label="主题">
          <a-radio-group v-model:value="formData.theme">
            <a-radio value="light">浅色</a-radio>
            <a-radio value="dark">深色</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="显示位置">
          <a-select v-model:value="formData.position">
            <a-select-option value="right-bottom">右下角</a-select-option>
            <a-select-option value="left-bottom">左下角</a-select-option>
            <a-select-option value="right-top">右上角</a-select-option>
            <a-select-option value="left-top">左上角</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="窗口宽度">
          <a-input-number v-model:value="formData.width" :min="300" :max="800" /> px
        </a-form-item>
        <a-form-item label="窗口高度">
          <a-input-number v-model:value="formData.height" :min="400" :max="800" /> px
        </a-form-item>
        <a-form-item label="主题色">
          <a-input v-model:value="formData.primaryColor" type="color" style="width: 60px" />
        </a-form-item>
        <a-form-item label="标题">
          <a-input v-model:value="formData.title" placeholder="智能助手" />
        </a-form-item>
        <a-form-item label="欢迎语">
          <a-input v-model:value="formData.welcomeMsg" placeholder="您好，有什么可以帮您？" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { getEmbedList, createEmbed, updateEmbed, deleteEmbed, toggleEmbedStatus } from '@/api/embed'

const loading = ref(false)
const pagination = reactive({ page: 1, pageSize: 10, total: 0 })
const tableData = ref<any[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('添加配置')
const formData = reactive({ name: '', theme: 'light', position: 'right-bottom', width: 400, height: 600, primaryColor: '#1890ff', title: '智能助手', welcomeMsg: '您好，有什么可以帮您？' })

const columns = [
  { title: 'ID', dataIndex: 'id', width: 60 },
  { title: '配置名称', dataIndex: 'name', width: 120 },
  { title: '主题', dataIndex: 'theme', width: 80 },
  { title: '位置', dataIndex: 'position', width: 100 },
  { title: '尺寸', dataIndex: 'size', width: 100 },
  { title: '标题', dataIndex: 'title', width: 120 },
  { title: '状态', dataIndex: 'status', width: 80 },
  { title: '操作', dataIndex: 'action', width: 280, fixed: 'right' },
]

function getPositionText(pos: string) {
  const map: Record<string, string> = { 'right-bottom': '右下角', 'left-bottom': '左下角', 'right-top': '右上角', 'left-top': '左上角' }
  return map[pos] || pos
}

async function loadData() {
  loading.value = true
  try {
    const { data } = await getEmbedList({ page: pagination.page, pageSize: pagination.pageSize })
    tableData.value = data.data.records?.map((r: any) => ({ ...r, size: `${r.width}x${r.height}` })) || []
    pagination.total = data.data.total
  } catch { tableData.value = [] }
  loading.value = false
}

function handleAdd() { dialogTitle.value = '添加配置'; dialogVisible.value = true }
function handleEdit(record: any) { dialogTitle.value = '编辑配置'; Object.assign(formData, record); dialogVisible.value = true }

async function handleSubmit() {
  try {
    if (formData.id) { await updateEmbed(formData.id, formData) } else { await createEmbed(formData) }
    message.success('保存成功')
    dialogVisible.value = false
    loadData()
  } catch { message.error('保存失败') }
}

async function handleDelete(record: any) { try { await deleteEmbed(record.id); message.success('删除成功'); loadData() } catch { message.error('删除失败') } }
async function handleToggle(record: any) { try { await toggleEmbedStatus(record.id); message.success('操作成功'); loadData() } catch { message.error('操作失败') } }
function handlePreview(record: any) { message.info(`预览功能开发中 - ${record.name}`) }

onMounted(() => { loadData() })
</script>

<style scoped lang="scss">
.embed-container {
  .card-header { display: flex; justify-content: space-between; align-items: center; .card-title { font-size: 16px; font-weight: 500; } }
  .pagination { margin-top: 20px; display: flex; justify-content: flex-end; }
}
</style>
