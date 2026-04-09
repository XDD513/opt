import message from '@/plugins/message'
import { ref } from 'vue'
import { defineStore } from 'pinia'

import { getAdminStats } from '@/api/statistics'

/**
 * 管理员端专用Store
 */
export const useAdminStore = defineStore('admin', () => {
  // 系统统计数据
  const systemStats = ref({
    users: 0,
  })
  
  // 加载状态
  const loading = ref(false)

  /**
   * 获取系统统计数据
   */
  const fetchSystemStats = async () => {
    try {
      loading.value = true
      const res = await getAdminStats()
      if (res.code === 200) {
        systemStats.value = res.data || systemStats.value
        return res.data
      } else {
        message.error(res.message || '获取系统统计失败')
        return null
      }
    } catch (error) {

      message.error('获取系统统计失败')
      return null
    } finally {
      loading.value = false
    }
  }

  /**
   * 刷新所有数据
   */
  const refreshAll = async () => {
    await fetchSystemStats()
  }

  /**
   * 清除数据
   */
  const clearData = () => {
    systemStats.value = {
      users: 0,
    }
  }

  return {
    systemStats,
    loading,
    fetchSystemStats,
    refreshAll,
    clearData
  }
})

