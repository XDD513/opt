/**
 * 去掉 VITE_API_URL 末尾的 /api，得到后端站点 origin（用于拼接静态资源路径）
 */
export function normalizeApiOrigin() {
  let b = String(import.meta.env.VITE_API_URL || '').trim().replace(/\/+$/, '')
  if (!b || b.startsWith('/')) return ''
  if (b.endsWith('/api')) b = b.slice(0, -4)
  return b.replace(/\/+$/, '')
}

/**
 * 将库内存储的相对路径转为浏览器可请求的 URL（与药膳图逻辑对齐）
 */
export function resolvePublicMediaUrl(path) {
  if (path == null || String(path).trim() === '') return ''
  const p = String(path).trim()
  if (/^https?:\/\//i.test(p)) return p
  if (p.startsWith('//')) {
    const proto = typeof window !== 'undefined' ? window.location.protocol : 'https:'
    return `${proto}${p}`
  }
  const origin = normalizeApiOrigin()
  if (p.startsWith('/')) {
    if (origin) return `${origin}${p}`
    return p
  }
  return `/uploads/${p.replace(/^\/+/, '')}`
}
