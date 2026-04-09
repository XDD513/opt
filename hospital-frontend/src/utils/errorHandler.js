/**
 * 统一错误处理工具函数
 */

/**
 * 处理API错误
 * @param {Error} error - 错误对象
 * @param {Object} options - 配置选项
 * @param {string} options.defaultMessage - 默认错误消息
 * @param {Function} options.onError - 错误回调函数
 * @returns {string} 友好的错误消息
 */
export function handleApiError(error, options = {}) {
  const { defaultMessage = '操作失败，请稍后重试', onError } = options
  
  let errorMessage = defaultMessage
  
  // 从不同来源提取错误消息
  if (error?.response?.data?.message) {
    errorMessage = error.response.data.message
  } else if (error?.message) {
    errorMessage = error.message
  } else if (typeof error === 'string') {
    errorMessage = error
  }
  
  // 处理特定错误码
  if (error?.response?.status === 403) {
    errorMessage = '无权访问，请确认权限'
  } else if (error?.response?.status === 404) {
    errorMessage = '资源不存在或已被删除'
  } else if (error?.response?.status === 500) {
    errorMessage = '服务器错误，请稍后重试'
  } else if (error?.response?.status >= 500) {
    errorMessage = '服务器异常，请稍后重试'
  }
  
  // 执行回调
  if (onError && typeof onError === 'function') {
    onError(errorMessage, error)
  }
  
  return errorMessage
}

/**
 * 处理验证错误
 * @param {Error|Object} error - 错误对象
 * @param {Object} options - 配置选项
 * @returns {string} 友好的错误消息
 */
export function handleValidationError(error, options = {}) {
  const { defaultMessage = '输入验证失败' } = options
  
  if (error?.response?.data?.errors) {
    // 处理字段验证错误
    const errors = error.response.data.errors
    const firstError = Object.values(errors)[0]
    return Array.isArray(firstError) ? firstError[0] : firstError
  }
  
  return error?.message || defaultMessage
}

/**
 * 获取友好的错误消息
 * @param {Error|string|Object} error - 错误对象或消息
 * @param {Object} options - 配置选项
 * @returns {string} 友好的错误消息
 */
export function getErrorMessage(error, options = {}) {
  if (!error) {
    return options.defaultMessage || '未知错误'
  }
  
  // 字符串直接返回
  if (typeof error === 'string') {
    return error
  }
  
  // API错误
  if (error?.response) {
    return handleApiError(error, options)
  }
  
  // 验证错误
  if (error?.errors || error?.response?.data?.errors) {
    return handleValidationError(error, options)
  }
  
  // 普通错误对象
  if (error?.message) {
    return error.message
  }
  
  return options.defaultMessage || '操作失败，请稍后重试'
}

/**
 * 判断是否为临时错误（可重试）
 * @param {Error} error - 错误对象
 * @returns {boolean} 是否为临时错误
 */
export function isTemporaryError(error) {
  if (!error?.response) {
    return false
  }
  
  const status = error.response.status
  // 5xx错误和429（限流）通常是临时错误
  return status >= 500 || status === 429 || status === 408
}

/**
 * 判断是否为永久错误（不可重试）
 * @param {Error} error - 错误对象
 * @returns {boolean} 是否为永久错误
 */
export function isPermanentError(error) {
  if (!error?.response) {
    return false
  }
  
  const status = error.response.status
  // 4xx错误（除429和408外）通常是永久错误
  return status >= 400 && status < 500 && status !== 429 && status !== 408
}

