import request from './request'

/**
 * 首页相关API
 */

/**
 * 获取首页统计数据
 */
export function getHomeStats() {
  return request({
    url: '/home/stats',
    method: 'get'
  })
}

/**
 * 获取首页推荐内容
 * @param {number} limit - 每类内容的数量限制
 */
export function getHomeRecommendations(limit = 3) {
  return request({
    url: '/home/recommendations',
    method: 'get',
    params: { limit }
  })
}

