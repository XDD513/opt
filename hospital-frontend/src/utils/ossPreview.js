import { generatePresignedUrl } from '@/api/oss'
import { resolvePublicMediaUrl } from './mediaUrl'

/**
 * 去掉 URL 中 ? 及查询串（如历史 OSS 签名参数），与资料页头像入库逻辑一致。
 */
export function sanitizeStoredMediaUrl(url) {
  if (!url) return ''
  const s = String(url)
  const q = s.indexOf('?')
  if (q > 0) return s.substring(0, q)
  return s
}

/**
 * 与头像/图标预览一致：OSS 绝对地址走预签名；相对路径走 resolvePublicMediaUrl。
 */
export async function resolveOssPreviewUrl(storedUrl, expirationMinutes = 60) {
  const raw = sanitizeStoredMediaUrl(storedUrl)
  if (!raw) return ''
  if (!/^https?:\/\//i.test(raw)) {
    return resolvePublicMediaUrl(raw)
  }
  try {
    const signedUrlResponse = await generatePresignedUrl(raw, expirationMinutes)
    if (signedUrlResponse && signedUrlResponse.code === 200) {
      const signedUrl = signedUrlResponse.data || signedUrlResponse.message
      if (signedUrl && signedUrl.startsWith('http')) return signedUrl
    }
  } catch {
    // ignore
  }
  return raw
}
