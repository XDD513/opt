import request from './request'

/**
 * 获取热门搜索词
 */
export function getHotKeywords(limit = 10) {
  return request({
    url: '/search/hot-keywords',
    method: 'get',
    params: { limit }
  })
}

/**
 * 记录搜索关键词（用于统计）
 */
export function recordSearchKeyword(keyword) {
  return request({
    url: '/search/record',
    method: 'post',
    params: { keyword }
  })
}

