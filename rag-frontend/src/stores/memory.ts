import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { MemoryOverview, MemoryItem } from '@/api/memory'
import { getMemoryOverview, addPreference, deleteMemory, searchMemory, clearAllMemory } from '@/api/memory'
import { message } from 'ant-design-vue'

export const useMemoryStore = defineStore('memory', () => {
  const loading = ref(false)
  const overview = ref<MemoryOverview | null>(null)

  async function fetchOverview(): Promise<void> {
    loading.value = true
    try {
      const res = await getMemoryOverview()
      overview.value = res.data
    } catch (error) {
      console.error('Failed to fetch memory overview:', error)
    } finally {
      loading.value = false
    }
  }

  async function addPref(content: string, importance?: number): Promise<boolean> {
    try {
      await addPreference(content, importance)
      message.success('添加成功')
      await fetchOverview()
      return true
    } catch (error) {
      console.error('Failed to add preference:', error)
      message.error('添加失败')
      return false
    }
  }

  async function removeMemory(memoryId: string, layer: string): Promise<boolean> {
    try {
      await deleteMemory(memoryId, layer)
      message.success('删除成功')
      await fetchOverview()
      return true
    } catch (error) {
      console.error('Failed to delete memory:', error)
      message.error('删除失败')
      return false
    }
  }

  async function search(query: string, type?: string): Promise<MemoryItem[]> {
    try {
      const res = await searchMemory(query, type)
      return res.data || []
    } catch (error) {
      console.error('Failed to search memory:', error)
      return []
    }
  }

  async function clearAll(): Promise<boolean> {
    try {
      await clearAllMemory()
      message.success('已清除所有记忆')
      overview.value = null
      return true
    } catch (error) {
      console.error('Failed to clear memory:', error)
      message.error('清除失败')
      return false
    }
  }

  function reset(): void {
    overview.value = null
    loading.value = false
  }

  return {
    loading,
    overview,
    fetchOverview,
    addPref,
    removeMemory,
    search,
    clearAll,
    reset,
  }
})
