import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getLatestTestResult } from '@/api/constitution'
import { getHealthProfile, getHealthStatistics } from '@/api/health'
import { getRecommendedRecipes } from '@/api/recipe'

/**
 * 患者端 Pinia Store
 * 管理患者的体质信息、健康档案、推荐药膳等数据
 */
export const usePatientStore = defineStore('patient', () => {
  // ==================== 状态定义 ====================
  
  // 最新体质测试结果
  const latestConstitution = ref(null)
  
  // 健康档案
  const healthProfile = ref(null)
  
  // 健康统计数据
  const healthStats = ref({
    totalCheckins: 0,
    currentStreak: 0,
    avgWeight: 0,
    avgSleep: 0,
    avgExercise: 0,
    avgMood: 0
  })
  
  // 推荐药膳列表
  const recommendedRecipes = ref([])
  
  // 加载状态
  const loading = ref(false)

  // ==================== 方法定义 ====================

  /**
   * 获取患者ID
   * 从用户信息中提取患者ID
   */
  const getPatientId = () => {
    const userStore = useUserStore()
    return userStore.userInfo?.id || null
  }

  /**
   * 获取最新体质测试结果
   */
  const fetchLatestConstitution = async () => {
    const patientId = getPatientId()
    if (!patientId) {

      return
    }

    try {
      loading.value = true
      const res = await getLatestTestResult(patientId)
      if (res.code === 200) {
        latestConstitution.value = res.data
      }
    } catch (error) {

    } finally {
      loading.value = false
    }
  }

  /**
   * 获取健康档案
   */
  const fetchHealthProfile = async () => {
    const patientId = getPatientId()
    if (!patientId) {

      return
    }

    try {
      loading.value = true
      const res = await getHealthProfile(patientId)
      if (res.code === 200) {
        healthProfile.value = res.data
      }
    } catch (error) {

    } finally {
      loading.value = false
    }
  }

  /**
   * 获取健康统计数据
   * @param {Object} params - 查询参数（startDate, endDate）
   */
  const fetchHealthStats = async (params = {}) => {
    const patientId = getPatientId()
    if (!patientId) {

      return
    }

    try {
      loading.value = true
      const queryParams = {
        userId: patientId,
        ...params
      }
      const res = await getHealthStatistics(queryParams)
      if (res.code === 200) {
        healthStats.value = res.data || {
          totalCheckins: 0,
          currentStreak: 0,
          avgWeight: 0,
          avgSleep: 0,
          avgExercise: 0,
          avgMood: 0
        }
      }
    } catch (error) {

    } finally {
      loading.value = false
    }
  }

  /**
   * 获取推荐药膳
   * @param {Object} params - 查询参数
   */
  const fetchRecommendedRecipes = async (params = { pageNum: 1, pageSize: 6 }) => {
    try {
      loading.value = true
      const res = await getRecommendedRecipes(params)
      if (res.code === 200 && res.data) {
        // 后端返回分页数据，需要从 records 中获取
        const records = res.data.records || res.data.list || res.data
        recommendedRecipes.value = records.map(recipe => ({
          id: recipe.id,
          name: recipe.name || recipe.recipeName,
          effect: recipe.effect || recipe.efficacy || '',
          season: recipe.season || recipe.suitableSeason || '四季',
          constitution: recipe.constitutionType || recipe.suitableConstitution || '',
          image: recipe.image || recipe.imageUrl || ''
        }))
      }
    } catch (error) {

      recommendedRecipes.value = []
    } finally {
      loading.value = false
    }
  }

  /**
   * 刷新所有数据
   */
  const refreshAll = async () => {
    await Promise.all([
      fetchLatestConstitution(),
      fetchHealthProfile(),
      fetchHealthStats(),
      fetchRecommendedRecipes()
    ])
  }

  /**
   * 清空数据
   */
  const clearData = () => {
    latestConstitution.value = null
    healthProfile.value = null
    healthStats.value = {
      totalCheckins: 0,
      currentStreak: 0,
      avgWeight: 0,
      avgSleep: 0,
      avgExercise: 0,
      avgMood: 0
    }
    recommendedRecipes.value = []
  }

  /**
   * 更新体质测试结果
   * @param {Object} result - 新的体质测试结果
   */
  const updateConstitution = (result) => {
    latestConstitution.value = result
  }

  /**
   * 更新健康档案
   * @param {Object} profile - 新的健康档案
   */
  const updateHealthProfile = (profile) => {
    healthProfile.value = profile
  }

  // ==================== 导出 ====================
  return {
    // 状态
    latestConstitution,
    healthProfile,
    healthStats,
    recommendedRecipes,
    loading,

    // 方法
    getPatientId,
    fetchLatestConstitution,
    fetchHealthProfile,
    fetchHealthStats,
    fetchRecommendedRecipes,
    refreshAll,
    clearData,
    updateConstitution,
    updateHealthProfile
  }
})

// 导入 useUserStore（避免循环依赖）
import { useUserStore } from './user'

