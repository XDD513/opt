import request from './request'

/**
 * 药膳推荐相关API
 */

/**
 * 获取全部药膳列表（分页，不包含AI推荐）
 * @param {Object} params - 查询参数
 * @param {number} params.pageNum - 页码
 * @param {number} params.pageSize - 每页数量
 */
export function getRecipeList(params) {
  return request({
    url: '/recipe/list',
    method: 'get',
    params: {
      pageNum: params.pageNum || params.page || 1,
      pageSize: params.pageSize || params.size || 10
    }
  })
}

/**
 * 根据提示词生成药膳（并保存）
 * @param {string} prompt 提示词
 */
export function generateRecipeByPrompt(prompt) {
  return request({
    url: '/recipe/generate',
    method: 'post',
    data: { prompt }
  })
}

export function generateRecipeJsonByPrompt(prompt) {
  return request({
    url: '/recipe/generate-json',
    method: 'post',
    data: { prompt }
  })
}

export function getRecipePromptStreamUrl(prompt) {
  const normalizeApiBase = (base) => {
    let b = String(base || '').trim()
    if (!b) return ''
    b = b.replace(/\/+$/, '')
    if (b.endsWith('/api')) b = b.slice(0, -4)
    return b
  }
  const apiBase = normalizeApiBase(import.meta.env.VITE_API_URL || '')
  const token = localStorage.getItem('token') || ''
  return `${apiBase || ''}/api/recipe/generate-json/stream?token=${encodeURIComponent(token)}&prompt=${encodeURIComponent(prompt || '')}`
}

/**
 * 保存 AI 生成完成后的 JSON
 * @param {Object|string} json
 * @param {string|number|null} testId
 */
export function saveRecipeFromJson(json, testId = null) {
  const data = { json }
  if (testId !== null && testId !== undefined && testId !== '') {
    data.test_id = testId
  }
  return request({
    url: '/recipe/save',
    method: 'post',
    data
  })
}

/**
 * 将“药膳建议”文本模板化并入库
 * @param {Object} payload
 * @param {string|number} payload.testId
 * @param {string} payload.text
 */
export function saveRecipeFromSuggestion({ testId, text }) {
  return request({
    url: '/recipe/suggestion/save',
    method: 'post',
    data: { testId, text }
  })
}

/**
 * 获取药膳详情
 * @param {string|number} id - 药膳ID（使用字符串避免精度丢失）
 */
export function getRecipeDetail(id) {
  return request({
    url: `/recipe/${String(id)}`,
    method: 'get'
  })
}

/**
 * 获取个性化推荐药膳（分页）
 * @param {Object} params - 查询参数
 * @param {number} params.pageNum - 页码，默认1
 * @param {number} params.pageSize - 每页数量，默认10
 * @param {string} params.season - 季节（可选）
 */
export function getRecommendedRecipes(params = {}, options = {}) {
  return request({
    url: '/recipe/recommend',
    method: 'get',
    params: {
      pageNum: params.pageNum || 1,
      pageSize: params.pageSize || 10,
      season: params.season
    },
    ...options
  })
}

/**
 * 获取时令药膳
 * @param {string} season - 季节（SPRING/SUMMER/AUTUMN/WINTER）
 * @param {number} limit - 数量限制
 */
export function getSeasonalRecipes(season, limit = 10) {
  return request({
    url: `/recipe/season/${season}`,
    method: 'get',
    params: { limit }
  })
}

/**
 * 按体质测试ID获取“本次生成的药膳”
 * @param {string|number} testId
 */
export function getRecipesByTestId(testId) {
  return request({
    url: `/recipe/by-test/${String(testId)}`,
    method: 'get'
  })
}

/**
 * 获取热门药膳
 * @param {number} limit - 数量限制
 */
export function getPopularRecipes(limit = 10) {
  return request({
    url: '/recipe/popular',
    method: 'get',
    params: { limit }
  })
}

/**
 * 收藏药膳
 * @param {number} recipeId - 药膳ID
 * @param {string} remark - 备注（可选）
 */
export function favoriteRecipe(recipeId, remark = '') {
  return request({
    url: `/recipe/favorite/${recipeId}`,
    method: 'post',
    params: { remark }
  })
}

/**
 * 取消收藏药膳
 * @param {number} recipeId - 药膳ID
 */
export function unfavoriteRecipe(recipeId) {
  return request({
    url: `/recipe/favorite/${recipeId}`,
    method: 'delete'
  })
}

/**
 * 获取用户收藏的药膳列表（分页）
 * @param {Object} params - 查询参数
 * @param {number} params.pageNum - 页码
 * @param {number} params.pageSize - 每页数量
 */
export function getFavoriteRecipes(params = {}) {
  return request({
    url: '/recipe/favorites',
    method: 'get',
    params: {
      pageNum: params.pageNum || 1,
      pageSize: params.pageSize || 10
    }
  })
}

/**
 * 查询适用食材
 * @param {string} constitutionType - 体质类型代码
 */
export function getSuitableIngredients(constitutionType) {
  return request({
    url: `/recipe/ingredients/${constitutionType}`,
    method: 'get'
  })
}

/**
 * 搜索药膳
 * @param {Object} params - 搜索参数
 * @param {string} params.keyword - 关键词
 * @param {string} params.season - 季节
 * @param {string} params.constitutionType - 体质类型
 * @param {number} params.page - 页码
 * @param {number} params.size - 每页数量
 */
export function searchRecipes(params) {
  return request({
    url: '/recipe/search',
    method: 'get',
    params
  })
}

