import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { KnowledgeDocument, DocumentCategory } from '@/api/knowledge'
import {
  getDocumentList,
  searchDocuments,
  getCategories,
  deleteDocument as deleteDocumentApi,
} from '@/api/knowledge'

export const useKnowledgeStore = defineStore('knowledge', () => {
  // 文档列表
  const documentList = ref<KnowledgeDocument[]>([])
  // 分类列表
  const categoryList = ref<DocumentCategory[]>([])
  // 当前选中分类
  const currentCategory = ref<number | null>(null)
  // 分页信息
  const pagination = ref({
    page: 1,
    size: 10,
    total: 0,
  })
  // 加载状态
  const loading = ref(false)
  // 搜索关键词
  const searchKeyword = ref('')

  // 获取分类树形结构
  const categoryTree = computed(() => {
    return buildTree(categoryList.value)
  })

  // 构建树形结构
  function buildTree(categories: DocumentCategory[], parentId = 0): DocumentCategory[] {
    return categories
      .filter((item) => item.parentId === parentId)
      .map((item) => ({
        ...item,
        children: buildTree(categories, item.id),
      }))
      .sort((a, b) => a.sort - b.sort)
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

      let result
      if (searchKeyword.value) {
        result = await searchDocuments({
          keyword: searchKeyword.value,
          page: pagination.value.page,
          size: pagination.value.size,
        })
      } else {
        result = await getDocumentList(params)
      }

      documentList.value = result.data.list
      pagination.value.total = result.data.total
    } finally {
      loading.value = false
    }
  }

  // 获取分类列表
  async function fetchCategories() {
    const result = await getCategories()
    categoryList.value = result.data
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

  // 设置搜索关键词
  function setSearchKeyword(keyword: string) {
    searchKeyword.value = keyword
    pagination.value.page = 1
    fetchDocuments()
  }

  // 重置搜索
  function resetSearch() {
    searchKeyword.value = ''
    currentCategory.value = null
    pagination.value.page = 1
    fetchDocuments()
  }

  // 设置分页
  function setPagination(page: number, size: number) {
    pagination.value.page = page
    pagination.value.size = size
    fetchDocuments()
  }

  return {
    documentList,
    categoryList,
    categoryTree,
    currentCategory,
    pagination,
    loading,
    searchKeyword,
    fetchDocuments,
    fetchCategories,
    removeDocument,
    setCurrentCategory,
    setSearchKeyword,
    resetSearch,
    setPagination,
  }
})
