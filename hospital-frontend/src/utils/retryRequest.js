/**
 * 请求重试工具
 * 用于处理接口访问失败时的自动重试机制
 */

/**
 * 判断错误是否应该重试
 * @param {Error} error - 错误对象
 * @returns {boolean} - 是否应该重试
 */
const shouldRetry = (error) => {
  // 401/403 认证/授权错误不应该重试
  if (error.response) {
    const status = error.response.status
    const data = error.response.data
    
    // 认证错误（401）不应该重试
    if (status === 401 || data?.code === 401) {
      return false
    }
    
    // 权限不足（403）不应该重试
    if (status === 403) {
      return false
    }
    
    // 参数错误（400）不应该重试
    if (status === 400) {
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
  
  // 其他错误默认不重试
  return false
}

/**
 * 延迟函数
 * @param {number} ms - 延迟毫秒数
 * @returns {Promise}
 */
const delay = (ms) => {
  return new Promise(resolve => setTimeout(resolve, ms))
}

/**
 * 计算重试延迟时间（指数退避）
 * @param {number} attempt - 当前重试次数（从0开始）
 * @param {number} baseDelay - 基础延迟时间（毫秒），默认500ms
 * @returns {number} - 延迟时间（毫秒）
 */
const getRetryDelay = (attempt, baseDelay = 500) => {
  // 指数退避：500ms, 1000ms, 2000ms
  return baseDelay * Math.pow(2, attempt)
}

/**
 * 带重试机制的请求包装器
 * @param {Function} requestFn - 返回 Promise 的请求函数
 * @param {Object} options - 配置选项
 * @param {number} options.maxRetries - 最大重试次数，默认3次
 * @param {number} options.baseDelay - 基础延迟时间（毫秒），默认500ms
 * @param {Function} options.shouldRetry - 自定义重试判断函数
 * @param {Function} options.onRetry - 重试回调函数，接收 (attempt, error) 参数
 * @param {boolean} options.silent - 是否静默模式（不输出日志），默认false
 * @returns {Promise} - 请求结果
 */
export const retryRequest = async (requestFn, options = {}) => {
  const {
    maxRetries = 3,
    baseDelay = 500,
    shouldRetry: customShouldRetry,
    onRetry,
    silent = false
  } = options

  let lastError = null

  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    try {
      const result = await requestFn()
      
      // 如果成功，返回结果
      if (result && result.code === 200) {
        return result
      }
      
      // 如果业务状态码不是200，检查是否需要重试
      if (result && result.code !== 200) {
        // 401 认证错误不应该重试
        if (result.code === 401) {
          throw new Error(result.message || '认证失败')
        }
        
        // 其他业务错误默认不重试
        return result
      }
      
      return result
    } catch (error) {
      lastError = error
      
      // 判断是否应该重试
      const shouldRetryError = customShouldRetry 
        ? customShouldRetry(error, attempt) 
        : shouldRetry(error)
      
      // 如果不需要重试或已达到最大重试次数，直接抛出错误
      if (!shouldRetryError || attempt >= maxRetries) {
        if (!silent && attempt > 0) {
          console.warn(`请求失败，已重试 ${attempt} 次，不再重试:`, error)
        }
        throw error
      }
      
      // 计算延迟时间
      const delayMs = getRetryDelay(attempt, baseDelay)
      
      // 触发重试回调
      if (onRetry) {
        onRetry(attempt + 1, error, delayMs)
      }
      
      if (!silent) {
        console.warn(`请求失败，${delayMs}ms 后进行第 ${attempt + 1} 次重试:`, error.message || error)
      }
      
      // 等待后重试
      await delay(delayMs)
    }
  }
  
  // 理论上不会执行到这里，但为了安全起见
  throw lastError || new Error('请求失败')
}

/**
 * 创建带重试功能的 axios 请求包装器
 * @param {Function} requestFn - axios 请求函数
 * @param {Object} options - 重试配置选项
 * @returns {Function} - 包装后的请求函数
 */
export const withRetry = (requestFn, options = {}) => {
  return async (...args) => {
    return retryRequest(() => requestFn(...args), options)
  }
}

/**
 * 默认的重试配置（适用于大多数场景）
 */
export const defaultRetryOptions = {
  maxRetries: 3,
  baseDelay: 500,
  silent: false
}

/**
 * 静默重试配置（不输出日志）
 */
export const silentRetryOptions = {
  maxRetries: 3,
  baseDelay: 500,
  silent: true
}

/**
 * 快速重试配置（延迟时间较短）
 */
export const fastRetryOptions = {
  maxRetries: 3,
  baseDelay: 300,
  silent: false
}

