import { reactive } from 'vue'
import { resolveOssPreviewUrl, sanitizeStoredMediaUrl } from '@/utils/ossPreview'

/**
 * 文章列表封面预签名 URL（与养生社区列表一致）
 */
export function useArticleCoverUrls() {
  const coverUrlMap = reactive({})

  async function loadCoverUrls(rows) {
    Object.keys(coverUrlMap).forEach((k) => delete coverUrlMap[k])
    const entries = await Promise.all(
      (rows || []).map(async (row) => {
        if (!row.coverImage) return [row.id, '']
        try {
          const url = await resolveOssPreviewUrl(sanitizeStoredMediaUrl(row.coverImage), 60)
          return [row.id, url || '']
        } catch {
          return [row.id, '']
        }
      })
    )
    entries.forEach(([id, url]) => {
      coverUrlMap[id] = url
    })
  }

  return { coverUrlMap, loadCoverUrls }
}
