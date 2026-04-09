import { defineStore } from 'pinia'
import { ref } from 'vue'
import { logout as apiLogout } from '@/api/user'

export const useUserStore = defineStore('user', () => {
  // 状态
  const token = ref(localStorage.getItem('token') || '')
  const refreshToken = ref(localStorage.getItem('refreshToken') || '')
  const userInfo = ref(JSON.parse(localStorage.getItem('userInfo') || '{}'))

  // 设置Token
  const setToken = (newToken) => {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  // 设置RefreshToken
  const setRefreshToken = (newToken) => {
    refreshToken.value = newToken
    localStorage.setItem('refreshToken', newToken)
  }

  // 设置用户信息
  const setUserInfo = (info) => {
    userInfo.value = info
    localStorage.setItem('userInfo', JSON.stringify(info))
  }

  // 清除用户信息
  const clearUserInfo = () => {
    token.value = ''
    refreshToken.value = ''
    userInfo.value = {}
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('userInfo')
  }

  // 登出：调用后端接口并清理本地信息
  const logout = async () => {
    try {
      await apiLogout()
    } catch (e) {
      // 后端失败不阻塞本地登出

    } finally {
      clearUserInfo()
    }
  }

  return {
    token,
    refreshToken,
    userInfo,
    setToken,
    setRefreshToken,
    setUserInfo,
    clearUserInfo,
    logout
  }
})


