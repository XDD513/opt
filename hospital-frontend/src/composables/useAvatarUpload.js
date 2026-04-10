/**
 * 头像上传组合式函数
 * 用于统一处理头像上传的前置验证、成功回调和错误处理
 * 
 * 使用方式：
 * import { useAvatarUpload } from '@/composables/useAvatarUpload'
 * const { beforeAvatarUpload, handleAvatarSuccess, handleAvatarError } = useAvatarUpload(avatarPreview, editForm)
 */

import message from '@/plugins/message'
import { resolveOssPreviewUrl, sanitizeStoredMediaUrl } from '@/utils/ossPreview'

/**
 * 头像上传组合式函数
 * 
 * @param {import('vue').Ref} avatarPreview - 头像预览URL的ref
 * @param {import('vue').Ref|Object} editForm - 编辑表单对象，需要包含avatar字段
 * @returns {Object} 包含beforeAvatarUpload、handleAvatarSuccess、handleAvatarError函数的对象
 */
export function useAvatarUpload(avatarPreview, editForm) {
  /**
   * 头像上传前验证
   */
  const beforeAvatarUpload = (file) => {
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
   * 头像上传成功回调
   */
  const handleAvatarSuccess = async (response) => {
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
      const avatarUrl = sanitizeStoredMediaUrl(result.data)
      avatarPreview.value = await resolveOssPreviewUrl(avatarUrl, 60)

      // 保存去掉签名参数的持久化 URL
      if (editForm && typeof editForm === 'object') {
        if (editForm.value) {
          editForm.value.avatar = avatarUrl
        } else {
          editForm.avatar = avatarUrl
        }
      }
      
      message.success('头像上传成功')
    } else {
      message.error(result?.message || '头像上传失败')
    }
  }

  /**
   * 头像上传失败回调
   */
  const handleAvatarError = (error) => {
    message.error('头像上传失败，请重试')
  }

  return {
    beforeAvatarUpload,
    handleAvatarSuccess,
    handleAvatarError
  }
}

