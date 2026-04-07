import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { DocumentCategory, Document, CategoryRequest, PageResult } from '@/api/document'
import {
  getCategoryList,
  createCategory as createCategoryApi,
  updateCategory as updateCategoryApi,
  deleteCategory as deleteCategoryApi,
  getDocumentList,
  deleteDocument as deleteDocumentApi,
} from '@/api/document'

export const useDocumentStore = defineStore('document', () => {
  // 分类列表
  const categoryList = ref<DocumentCategory[]>([])
  // 当前选中分类
  const currentCategory = ref<number | null>(null)
  // 文档列表
  const documentList = ref<Document[]>([])
  // 分页信息
  const pagination = ref({
    page: 1,
    size: 10,
    total: 0,
  })
  // 加载状态
  const loading = ref(false)

  // 获取分类树形结构
  const categoryTree = computed(() => {
    return buildTree(categoryList.value)
  })

  // 构建树形结构
  function buildTree(categories: DocumentCategory[], parentId: number | null = null): DocumentCategory[] {
    return categories
      .filter((item) => {
        // 兼容 parentId 为 0 或 null 的情况，都视为顶级分类
        if (parentId === null) {
          return !item.parentId || item.parentId === 0
        }
        return item.parentId === parentId
      })
      .map((item) => ({
        ...item,
        children: buildTree(categories, item.id),
      }))
      .sort((a, b) => a.sort - b.sort)
  }

  // 获取分类列表
  async function fetchCategories() {
    try {
      const res = await getCategoryList()
      console.log('分类列表完整响应:', res)
      // 响应拦截器已经返回了 data，所以 res 就是 { code, message, data }
      categoryList.value = res.data || []
      console.log('categoryList:', categoryList.value)
      console.log('categoryTree:', categoryTree.value)
    } catch (error) {
      console.error('获取分类列表失败:', error)
    }
  }

  // 创建分类
  async function createCategory(data: CategoryRequest) {
    await createCategoryApi(data)
    await fetchCategories()
  }

  // 更新分类
  async function updateCategory(id: number, data: CategoryRequest) {
    await updateCategoryApi(id, data)
    await fetchCategories()
  }

  // 删除分类
  async function deleteCategory(id: number) {
    await deleteCategoryApi(id)
    await fetchCategories()
  }

  // 获取文档列表
  async function fetchDocuments() {
    loading.value = true
    try {
      const params: { page: number; size: number; categoryId?: number } = {
        page: pagination.value.page,
        size: pagination.value.size,
      }
      if (currentCategory.value) {
        params.categoryId = currentCategory.value
      }
      const res = await getDocumentList(params)
      documentList.value = res.data?.records || []
      pagination.value.total = res.data?.total || 0
    } catch (error) {
      console.error('获取文档列表失败:', error)
    } finally {
      loading.value = false
    }
  }

  // 删除文档
  async function removeDocument(id: number) {
    await deleteDocumentApi(id)
    await fetchDocuments()
  }

  // 设置当前分类
  function setCurrentCategory(categoryId: number | null) {
    currentCategory.value = categoryId
    pagination.value.page = 1
    fetchDocuments()
  }

  // 设置分页
  function setPagination(page: number, size: number) {
    pagination.value.page = page
    pagination.value.size = size
    fetchDocuments()
  }

  // 重置
  function reset() {
    currentCategory.value = null
    pagination.value.page = 1
    fetchDocuments()
  }

  return {
    categoryList,
    categoryTree,
    currentCategory,
    documentList,
    pagination,
    loading,
    fetchCategories,
    createCategory,
    updateCategory,
    deleteCategory,
    fetchDocuments,
    removeDocument,
    setCurrentCategory,
    setPagination,
    reset,
  }
})
