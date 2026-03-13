<template>
  <div class="document-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="card-title">文档管理</span>
          <el-button type="primary" :icon="Upload">上传文档</el-button>
        </div>
      </template>

      <!-- 搜索栏 -->
      <el-form :inline="true" class="search-form">
        <el-form-item label="文档名称">
          <el-input v-model="searchForm.keyword" placeholder="请输入文档名称" clearable />
        </el-form-item>
        <el-form-item label="知识库">
          <el-select v-model="searchForm.knowledgeId" placeholder="请选择知识库" clearable>
            <el-option label="产品文档库" :value="1" />
            <el-option label="技术文档库" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search">搜索</el-button>
          <el-button :icon="Refresh">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 表格 -->
      <el-table :data="tableData" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="文档名称" show-overflow-tooltip />
        <el-table-column prop="knowledgeName" label="所属知识库" width="150" />
        <el-table-column prop="size" label="文件大小" width="120" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === '已处理' ? 'success' : 'warning'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="上传时间" width="180" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link>查看</el-button>
            <el-button type="primary" link>下载</el-button>
            <el-button type="danger" link>删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive } from 'vue'
import { Upload, Search, Refresh } from '@element-plus/icons-vue'

// 搜索表单
const searchForm = reactive({
  keyword: '',
  knowledgeId: undefined as number | undefined,
})

// 分页
const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 200,
})

// 表格数据
const tableData = [
  { id: 1, name: '产品需求文档v1.0.pdf', knowledgeName: '产品文档库', size: '2.5MB', status: '已处理', createTime: '2024-01-15 10:30:00' },
  { id: 2, name: '系统架构设计.docx', knowledgeName: '技术文档库', size: '1.8MB', status: '处理中', createTime: '2024-01-16 14:20:00' },
  { id: 3, name: '用户操作手册.pdf', knowledgeName: '用户手册库', size: '3.2MB', status: '已处理', createTime: '2024-01-17 09:15:00' },
]
</script>

<style scoped lang="scss">
.document-container {
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
