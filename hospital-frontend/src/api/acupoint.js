import request from './request'

/**
 * 穴位按摩API接口
 */

/**
 * 获取穴位列表（分页）
 * @param {Object} params - 查询参数
 * @param {string} params.meridian - 经络（可选）
 * @param {string} params.constitutionType - 体质类型（可选）
 * @param {number} params.pageNum - 页码
 * @param {number} params.pageSize - 每页数量
 */
export function getAcupointList(params) {
  return request({
    url: '/acupoint/list',
    method: 'get',
    params
  })
}

/**
 * 获取穴位详情
 * @param {string|number} id - 穴位ID（使用字符串避免精度丢失）
 */
export function getAcupointDetail(id) {
  return request({
    url: `/acupoint/detail/${String(id)}`,
    method: 'get'
  })
}

/**
 * 搜索穴位
 * @param {Object} params - 查询参数
 * @param {string} params.keyword - 关键词
 * @param {number} params.pageNum - 页码
 * @param {number} params.pageSize - 每页数量
 */
export function searchAcupoints(params) {
  return request({
    url: '/acupoint/search',
    method: 'get',
    params
  })
}

/**
 * 按经络查询穴位
 * @param {string} meridian - 经络名称
 */
export function getAcupointsByMeridian(meridian) {
  return request({
    url: `/acupoint/meridian/${meridian}`,
    method: 'get'
  })
}

/**
 * 获取所有经络列表
 */
export function getAllMeridians() {
  return request({
    url: '/acupoint/meridians',
    method: 'get'
  })
}

/**
 * 获取热门穴位
 * @param {number} limit - 数量限制
 */
export function getPopularAcupoints(limit = 10) {
  return request({
    url: '/acupoint/popular',
    method: 'get',
    params: { limit }
  })
}

/**
 * 获取穴位组合列表（分页）
 * @param {Object} params - 查询参数
 * @param {string} params.constitutionType - 体质类型（可选）
 * @param {string} params.symptom - 症状（可选）
 * @param {number} params.pageNum - 页码
 * @param {number} params.pageSize - 每页数量
 */
export function getCombinationList(params) {
  return request({
    url: '/acupoint/combination/list',
    method: 'get',
    params
  })
}

/**
 * 获取穴位组合详情
 * @param {string|number} id - 组合ID（使用字符串避免精度丢失）
 */
export function getCombinationDetail(id) {
  return request({
    url: `/acupoint/combination/${String(id)}`,
    method: 'get'
  })
}

/**
 * 推荐穴位组合（基于用户体质）
 * @param {number} userId - 用户ID
 * @param {number} limit - 数量限制
 */
export function getRecommendedCombinations(userId, limit = 10) {
  return request({
    url: '/acupoint/combination/recommend',
    method: 'get',
    params: { userId, limit }
  })
}

/**
 * 搜索穴位组合
 * @param {Object} params - 查询参数
 * @param {string} params.keyword - 关键词
 * @param {number} params.pageNum - 页码
 * @param {number} params.pageSize - 每页数量
 */
export function searchCombinations(params) {
  return request({
    url: '/acupoint/combination/search',
    method: 'get',
    params
  })
}

/**
 * 按症状查询穴位组合
 * @param {string} symptom - 症状
 */
export function getCombinationsBySymptom(symptom) {
  return request({
    url: `/acupoint/combination/symptom/${symptom}`,
    method: 'get'
  })
}

/**
 * 获取热门穴位组合
 * @param {number} limit - 数量限制
 */
export function getPopularCombinations(limit = 10) {
  return request({
    url: '/acupoint/combination/popular',
    method: 'get',
    params: { limit }
  })
}

