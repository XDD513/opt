/**
 * 图标上传组合式函数
 * 用于统一处理图标上传的前置验证、成功回调和错误处理
 * 
 * 使用方式：
 * import { useIconUpload } from '@/composables/useIconUpload'
 * const { beforeIconUpload, handleIconSuccess, handleIconError } = useIconUpload(iconPreview, editForm)
 */

import message from '@/plugins/message'
import { resolveOssPreviewUrl, sanitizeStoredMediaUrl } from '@/utils/ossPreview'

/**
 * 图标上传组合式函数
 * 
 * @param {import('vue').Ref} iconPreview - 图标预览URL的ref
 * @param {import('vue').Ref|Object} editForm - 编辑表单对象，需要包含icon字段
 * @returns {Object} 包含beforeIconUpload、handleIconSuccess、handleIconError函数的对象
 */
export function useIconUpload(iconPreview, editForm) {
  /**
   * 图标上传前验证
   */
  const beforeIconUpload = (file) => {
    const isImage = file.type.startsWith('image/')
    const isLt5M = file.size / 1024 / 1024 < 5

    if (!isImage) {
      message.error('只能上传图片文件!')
      return false
    }
    if (!isLt5M) {
      message.error('图片大小不能超过 5MB!')
      return false
    }
    return true
  }

  /**
   * 图标上传成功回调
   */
  const handleIconSuccess = async (response) => {
    // Element Plus的el-upload使用原生XMLHttpRequest，不会经过axios拦截器
    // response可能是对象或字符串，需要处理
    let result = response
    if (typeof response === 'string') {
      try {
        result = JSON.parse(response)
      } catch (e) {
        message.error('响应格式错误')
        return
      }
    }

    if (result && result.code === 200 && result.data) {
      const iconUrl = sanitizeStoredMediaUrl(result.data)
      iconPreview.value = await resolveOssPreviewUrl(iconUrl, 60)

      if (editForm && typeof editForm === 'object') {
        if (editForm.value) {
          editForm.value.icon = iconUrl
        } else {
          editForm.icon = iconUrl
        }
      }
      
      message.success('图标上传成功，请点击保存按钮保存到数据库')
    } else {
      message.error(result?.message || '图标上传失败')
    }
  }

  /**
   * 图标上传失败回调
   */
  const handleIconError = (error) => {
    message.error('图标上传失败，请重试')
  }

  return {
    beforeIconUpload,
    handleIconSuccess,
    handleIconError
  }
}

