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

function hashSeed(s) {
  const str = String(s ?? '')
  let h = 2166136261
  for (let i = 0; i < str.length; i++) {
    h ^= str.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return Math.abs(h)
}

/** SVG <text> 节点内转义 */
function escapeXmlText(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

/** 无封面时占位主文案：优先用药膳名，过长拆两行 */
function buildPlaceholderTitleLines(recipeName) {
  const raw = recipeName != null ? String(recipeName).replace(/\s+/g, ' ').trim() : ''
  const title = raw || '未命名药膳'
  const max1 = 10
  const max2 = 12
  if (title.length <= max1) {
    return { line1: title, line2: null, font1: 40 }
  }
  if (title.length <= max1 + max2) {
    return {
      line1: title.slice(0, max1),
      line2: title.slice(max1),
      font1: 32
    }
  }
  return {
    line1: title.slice(0, max1),
    line2: `${title.slice(max1, max1 + max2 - 1)}…`,
    font1: 32
  }
}

/** 无封面时的渐变占位（按 id 稳定变色；主文案为药膳名称） */
function recipeCoverPlaceholderDataUrl(recipeId, recipeName) {
  const seed = hashSeed(recipeId != null && String(recipeId).trim() !== '' ? recipeId : recipeName || 'default')
  const palettes = [
    ['#1f4d3a', '#7fb069'],
    ['#3d2914', '#c9a66b'],
    ['#1e3a5f', '#6fa8dc'],
    ['#4a1f2e', '#d4899b'],
    ['#2b2b40', '#9b8fd9'],
    ['#1a3a3a', '#5fb3b3']
  ]
  const [c1, c2] = palettes[seed % palettes.length]
  const { line1, line2, font1 } = buildPlaceholderTitleLines(recipeName)
  const e1 = escapeXmlText(line1)
  const e2 = line2 != null ? escapeXmlText(line2) : ''
  const nameBlock =
    line2 != null
      ? `<text x="400" y="300" text-anchor="middle" fill="#ffffff" fill-opacity="0.92" font-family="system-ui,&quot;Microsoft YaHei&quot;,sans-serif" font-size="${font1}" font-weight="600"><tspan x="400" dy="0">${e1}</tspan><tspan x="400" dy="${Math.round(font1 * 1.25)}">${e2}</tspan></text>`
      : `<text x="400" y="310" text-anchor="middle" fill="#ffffff" fill-opacity="0.92" font-family="system-ui,&quot;Microsoft YaHei&quot;,sans-serif" font-size="${font1}" font-weight="600">${e1}</text>`
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="800" height="600" viewBox="0 0 800 600">
<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
<stop offset="0%" stop-color="${c1}"/><stop offset="100%" stop-color="${c2}"/></linearGradient></defs>
<rect width="800" height="600" fill="url(#g)"/>
<circle cx="620" cy="120" r="180" fill="#ffffff" fill-opacity="0.06"/>
<circle cx="120" cy="480" r="220" fill="#ffffff" fill-opacity="0.05"/>
${nameBlock}
</svg>`
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
}

/**
 * 药膳列表/详情封面：有路径则走 API 域名拼接；无路径则返回稳定渐变占位图。
 * @param {string|null|undefined} imagePath 库内 image 或 imageUrl
 * @param {string|number|null|undefined} recipeId 用于占位图色系稳定
 * @param {string|null|undefined} recipeName 无封面时占位图主文案（药膳名称）
 */
export function resolveRecipeCoverUrl(imagePath, recipeId, recipeName) {
  const raw = imagePath != null ? String(imagePath).trim() : ''
  if (!raw) {
    return recipeCoverPlaceholderDataUrl(recipeId, recipeName)
  }
  return resolvePublicMediaUrl(raw)
}
