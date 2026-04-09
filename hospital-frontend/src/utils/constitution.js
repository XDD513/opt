/**
 * 体质类型映射常量
 */
export const CONSTITUTION_TYPE_MAP = {
  'PINGHE': '平和质',
  'QIXU': '气虚质',
  'YANGXU': '阳虚质',
  'YINXU': '阴虚质',
  'TANSHI': '痰湿质',
  'SHIRE': '湿热质',
  'XUEYU': '血瘀质',
  'QIYU': '气郁质',
  'TEBING': '特禀质'
}

/**
 * 体质类型颜色映射
 */
export const CONSTITUTION_COLOR_MAP = {
  'PINGHE': 'success',
  'QIXU': 'warning',
  'YANGXU': 'primary',
  'YINXU': 'danger',
  'TANSHI': 'warning',
  'SHIRE': 'danger',
  'XUEYU': 'info',
  'QIYU': 'primary',
  'TEBING': 'info'
}

/**
 * 获取体质中文名称
 * @param {string} type - 体质代码
 * @returns {string} 体质中文名称
 */
export const getConstitutionName = (type) => {
  return CONSTITUTION_TYPE_MAP[type] || type
}

/**
 * 获取体质对应的颜色类型
 * @param {string} type - 体质代码
 * @returns {string} Element Plus 颜色类型
 */
export const getConstitutionColorType = (type) => {
  return CONSTITUTION_COLOR_MAP[type] || 'primary'
}
