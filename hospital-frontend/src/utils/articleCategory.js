/** 与发布/列表页 el-option 保持一致 */
export const ARTICLE_CATEGORY_MAP = {
  CONSTITUTION: '体质养生',
  DIET: '饮食养生',
  EXERCISE: '运动养生',
  ACUPOINT: '穴位养生',
  SEASON: '时令养生',
  OTHER: '其他'
}

/** 下拉/快捷选择用（value 存库，与筛选条件一致） */
export const ARTICLE_CATEGORY_OPTIONS = [
  { label: '体质养生', value: 'CONSTITUTION' },
  { label: '饮食养生', value: 'DIET' },
  { label: '运动养生', value: 'EXERCISE' },
  { label: '穴位养生', value: 'ACUPOINT' },
  { label: '时令养生', value: 'SEASON' },
  { label: '其他', value: 'OTHER' }
]

/**
 * @param {string|null|undefined} code 后端存储的分类码
 * @returns {string} 中文名称；未知码时原样返回；空为 —
 */
export function formatArticleCategory(code) {
  if (code == null || String(code).trim() === '') return '—'
  const key = String(code).trim().toUpperCase()
  return ARTICLE_CATEGORY_MAP[key] ?? String(code)
}
