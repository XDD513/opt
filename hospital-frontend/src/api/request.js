import axios from 'axios'
import message from '@/plugins/message'
import { useUserStore } from '@/stores/user'
import router from '@/router'
import { retryRequest } from '@/utils/retryRequest'
import { refreshToken as refreshTokenApi } from '@/api/user'

// 创建axios实例，默认使用本地代理
const request = axios.create({
  baseURL: '/api',
  timeout: 30000 // 将全局超时时间从 10s 延长至 30s，以适应 AI 生成报告的需求
})

export const configureRequestClient = (config) => {
  if (!config) return
  if (config.apiBaseUrl) {
    request.defaults.baseURL = config.apiBaseUrl
  }
  if (config.requestTimeout) {
    const parsed = parseInt(config.requestTimeout, 10)
    if (!Number.isNaN(parsed)) {
      request.defaults.timeout = parsed
    }
  }
}

// 标记是否正在跳转登录页，防止多个请求同时失败时重复跳转和显示消息
let isRedirectingToLogin = false
// 标记token是否已过期，用于在请求拦截器中阻止后续请求
let tokenExpired = false

// ===== refresh token 相关：防并发刷新 =====
let isRefreshingToken = false
let refreshWaiters = []

const notifyRefreshWaiters = (err, newAccessToken) => {
  const q = refreshWaiters
  refreshWaiters = []
  q.forEach(({ resolve, reject }) => {
    if (err) reject(err)
    else resolve(newAccessToken)
  })
}

const ensureAccessToken = async () => {
  const userStore = useUserStore()
  // 没有 refreshToken，无法刷新
  if (!userStore.refreshToken) {
    throw new Error('No refresh token')
  }
  // 已在刷新：挂起等待
  if (isRefreshingToken) {
    return await new Promise((resolve, reject) => {
      refreshWaiters.push({ resolve, reject })
    })
  }
  isRefreshingToken = true
  try {
    const res = await refreshTokenApi({ refreshToken: userStore.refreshToken })
    if (res?.code !== 200 || !res?.data?.token) {
      throw new Error(res?.message || 'Refresh failed')
    }
    userStore.setToken(res.data.token)
    userStore.setRefreshToken(res.data.refreshToken || '')
    notifyRefreshWaiters(null, res.data.token)
    return res.data.token
  } catch (e) {
    notifyRefreshWaiters(e)
    throw e
  } finally {
    isRefreshingToken = false
  }
}

// 处理token失效的统一方法
const handleTokenExpired = () => {
  // 如果已经在跳转中，直接返回（不显示错误消息，不执行任何操作）
  if (isRedirectingToLogin) {
    return
  }
  
  // 立即设置标记，阻止所有后续的错误消息显示和请求（必须在最前面）
  isRedirectingToLogin = true
  tokenExpired = true
  
  // 立即关闭所有现有的消息提示，避免显示多个错误消息
  message.closeAll()
  
  const userStore = useUserStore()
  
  // 清除用户信息和token
  userStore.clearUserInfo()
  
  // 使用nextTick确保在关闭所有消息后再显示新消息
  setTimeout(() => {
    // 显示统一的登录过期提示（只显示一次）
    message.warning('登录已过期，请重新登录')
  }, 100)
  
  // 跳转到登录页
  // 优先使用 SPA 路由跳转；若路由实例异常/跳转失败，则兜底使用 location 强制跳转
  router
    .push('/login')
    .catch(() => {
      try {
        window.location.replace('/login')
      } catch {
        window.location.href = '/login'
      }
    })
    .finally(() => {
      // 再做一次兜底：如果短时间内仍未进入登录页，强制跳转（避免“只提示不跳转”）
      setTimeout(() => {
        try {
          if (router.currentRoute?.value?.path !== '/login') {
            window.location.replace('/login')
          }
        } catch {
          // ignore
        }
      }, 120)

      // 延迟重置标记，允许后续的错误处理
      setTimeout(() => {
        isRedirectingToLogin = false
        tokenExpired = false
      }, 1000)
    })
}

// 判断是否为token失效错误（需要跳转登录页的错误）
// 仅针对明确的认证错误：HTTP 401 或业务状态码 401
// HTTP 403 可能是权限不足，不是token失效，所以不在这里处理
const isTokenExpired = (status, code) => {
  // HTTP状态码 401（未授权）或业务状态码 401（token失效/未登录）
  // 注意：HTTP 403 可能是权限不足，不是token失效，所以不跳转登录页
  return status === 401 || code === 401
}

/** 未携带访问令牌时仍应放行的匿名接口（避免会话过期跳转登录后验证码/登录请求被误拦） */
const isAnonymousAllowedRequest = (config) => {
  const url = String(config?.url || '')
  return (
    url.includes('/captcha/image') ||
    url.includes('/user/login') ||
    url.includes('/user/register') ||
    url.includes('/user/check/username') ||
    url.includes('/user/check/phone')
  )
}

// 请求拦截器
request.interceptors.request.use(
  config => {
    const userStore = useUserStore()
    
    // 如果token已过期，直接阻止请求，避免发起无效请求
    if (tokenExpired) {
      // 但如果用户已经重新登录（有新的token），重置过期标记
      if (userStore.token) {
        tokenExpired = false
        isRedirectingToLogin = false
      } else if (isAnonymousAllowedRequest(config)) {
        // 登录页拉验证码、提交登录/注册等不应被“过期闸门”挡住（否则需刷新页面才出现图）
        return config
      } else {
        // 没有token，确实已过期，阻止请求
        return Promise.reject(new Error('Token expired, please login again'))
      }
    }
    
    // 如果存在token，添加到请求头
    if (userStore.token) {
      config.headers['Authorization'] = `Bearer ${userStore.token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  response => {
    // 如果正在跳转登录页，直接返回响应，不处理错误消息
    if (isRedirectingToLogin) {
      return response.data
    }
    
    // 文件下载（如导出Excel）直接返回二进制数据
    if (response.config?.responseType === 'blob') {
      return response.data
    }

    const res = response.data
    
    // 如果返回的状态码不是200，说明接口请求有问题
    if (res.code !== 200) {
      // 判断是否为token失效错误
      if (isTokenExpired(null, res.code)) {
        // refresh 接口自身失败不再尝试刷新，直接过期处理
        if (response.config?._isRefreshRequest) {
          handleTokenExpired()
          return Promise.reject(new Error('Token refresh failed'))
        }
        // 尝试刷新一次，并重试原请求
        return ensureAccessToken()
          .then(() => {
            const cfg = { ...response.config }
            // 重新发起请求（header 会由请求拦截器带上新token）
            return request(cfg)
          })
          .catch(() => {
            handleTokenExpired()
            return Promise.reject(new Error('Token expired'))
          })
      } else {
        // 其他业务错误（权限不足、参数错误等），但如果在跳转登录页中，则不显示错误消息
        // 如果配置了 silentError，也不显示错误消息（用于静默处理某些API错误）
        if (!isRedirectingToLogin && !response.config?.silentError) {
          message.error(res.message || '请求失败')
        }
      }
      
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    
    return res
  },
  error => {
    // 如果正在跳转登录页，不处理任何错误消息
    if (isRedirectingToLogin) {
      return Promise.reject(error)
    }
    
    let errorMessage = '网络错误'
    
    if (error.response) {
      const status = error.response.status
      const data = error.response.data
      
      // 判断是否为token失效错误
      if (isTokenExpired(status, data?.code)) {
        // refresh 接口自身失败不再尝试刷新，直接过期处理
        if (error.config?._isRefreshRequest) {
          handleTokenExpired()
          return Promise.reject(error)
        }
        // 尝试刷新一次，并重试原请求
        return ensureAccessToken()
          .then(() => {
            const cfg = { ...error.config }
            return request(cfg)
          })
          .catch(() => {
            handleTokenExpired()
            return Promise.reject(error)
          })
      } else {
        // 其他HTTP错误（403权限不足、404找不到、500服务器错误等）
        // 再次检查标记，因为handleTokenExpired可能是异步的
        // 如果配置了 silentError，也不显示错误消息（用于静默处理某些API错误）
        if (!isRedirectingToLogin && !error.config?.silentError) {
          switch (status) {
            case 403:
              errorMessage = data?.message || '没有权限访问该资源'
              break
            case 404:
              errorMessage = '请求地址不存在'
              break
            case 500:
              errorMessage = '服务器内部错误'
              break
            default:
              errorMessage = data?.message || '请求失败'
          }
          message.error(errorMessage)
        }
      }
    } else if (error.request) {
      // 请求已发出，但没有收到响应（网络错误）
      // 标记为可重试错误，让重试机制处理
      error._shouldRetry = true
      if (!isRedirectingToLogin) {
        errorMessage = '网络连接失败，请检查网络'
        // 网络错误不立即显示消息，让重试机制处理
      }
    } else {
      // 请求配置出错
      // 如果配置了 silentError，也不显示错误消息（用于静默处理某些API错误）
      if (!isRedirectingToLogin && !error.config?.silentError) {
        errorMessage = '请求配置错误'
        message.error(errorMessage)
      }
    }
    
    return Promise.reject(error)
  }
)

// 保存原始的 request 方法
const originalRequest = request.request.bind(request)

// 重写 request 方法，添加自动重试功能
request.request = function(config) {
  // 如果配置中明确禁用重试，直接使用原始请求
  if (config?.retry === false) {
    return originalRequest(config)
  }
  
  // 默认启用重试（最多3次）
  const maxRetries = config?.maxRetries ?? 3
  const baseDelay = config?.retryDelay ?? 500
  const silentRetry = config?.silentRetry ?? false
  
  return retryRequest(
    () => originalRequest(config),
    {
      maxRetries,
      baseDelay,
      silent: silentRetry,
      shouldRetry: (error) => {
        // 401/403 认证/授权错误不应该重试
        if (error.response) {
          const status = error.response.status
          if (status === 401 || status === 403 || status === 400) {
            return false
          }
          // 5xx 服务器错误应该重试
          if (status >= 500 && status < 600) {
            return true
          }
          // 429 请求过多应该重试
          if (status === 429) {
            return true
          }
        }
        
        // 网络错误（没有响应）应该重试
        if (error.request && !error.response) {
          return true
        }
        
        // 超时错误应该重试
        if (error.code === 'ECONNABORTED' || error.message?.includes('timeout')) {
          return true
        }
        
        // 标记为可重试的错误
        if (error._shouldRetry) {
          return true
        }
        
        return false
      },
      onRetry: (attempt, error, delayMs) => {
        // 保留空回调，便于后续按需扩展埋点
      }
    }
  )
}

export default request


