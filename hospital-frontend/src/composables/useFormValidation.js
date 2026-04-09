/**
 * 表单验证组合式函数
 * 用于统一处理表单字段失去焦点时的验证提示清除
 * 
 * 使用方式：
 * import { useFormValidation } from '@/composables/useFormValidation'
 * const { handleFieldBlur } = useFormValidation(formRef)
 * 
 * 在模板中使用：
 * @blur="handleFieldBlur('fieldName')"
 */

import { createFieldBlurHandler } from '@/utils/formValidation'

/**
 * 表单验证组合式函数
 * 
 * @param {Object} formRef - 表单引用
 * @param {Function} additionalHandler - 额外的处理函数（可选，接收fieldName参数）
 * @returns {Object} 包含handleFieldBlur函数的对象
 */
export function useFormValidation(formRef, additionalHandler = null) {
  // 创建字段失焦处理函数
  const handleFieldBlur = createFieldBlurHandler(formRef, additionalHandler)
  
  return {
    handleFieldBlur
  }
}

