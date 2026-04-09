/**
 * 表单验证工具函数
 * 用于处理表单字段失去焦点时的验证提示清除
 * 
 * 当字段失去焦点时，如果字段值有效，自动清除验证提示
 */

/**
 * 创建字段失焦处理函数（返回一个绑定好的函数）
 * 用于在模板中直接使用
 * 
 * @param {Object} formRef - 表单引用
 * @param {Function} additionalHandler - 额外的处理函数（可选，接收fieldName参数）
 * @returns {Function} 处理函数，接收fieldName参数
 */
export function createFieldBlurHandler(formRef, additionalHandler = null) {
  return async (fieldName) => {
    if (!formRef || !formRef.value || !fieldName) return
    
    // 执行额外的处理函数（如检查用户名是否存在）
    if (additionalHandler && typeof additionalHandler === 'function') {
      try {
        await additionalHandler(fieldName)
      } catch (error) {
        // 额外处理失败不影响验证清除
      }
    }
    
    // 等待 Element Plus 的 blur 验证先执行完成
    // Element Plus 的验证是异步的，需要等待一定时间让验证执行
    await new Promise(resolve => setTimeout(resolve, 300))
    
    // 验证该字段
    // validateField 可以使用回调函数或返回 Promise
    return new Promise((resolve) => {
      // 先尝试使用回调方式
      try {
        formRef.value.validateField(fieldName, (isValid, message) => {
          // 如果验证通过，清除验证提示
          if (isValid) {
            // 使用 setTimeout 确保在下一个事件循环清除
            setTimeout(() => {
              if (formRef.value) {
                formRef.value.clearValidate(fieldName)
              }
            }, 50)
          }
          // 无论验证是否通过，都 resolve
          resolve()
        })
      } catch (error) {
        // 如果回调方式失败，尝试 Promise 方式
        formRef.value.validateField(fieldName)
          .then(() => {
            // 验证通过，清除验证提示
            setTimeout(() => {
              if (formRef.value) {
                formRef.value.clearValidate(fieldName)
              }
            }, 50)
            resolve()
          })
          .catch(() => {
            // 验证失败，保留错误提示
            resolve()
          })
      }
    })
  }
}

