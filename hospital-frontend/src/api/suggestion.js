import request from './request'

/**
 * 提词器相关API
 */

/**
 * 症状关键词提词器
 * @param {string} prefix - 输入前缀
 * @param {number} size - 返回数量，默认10
 */
export function suggestSymptoms(prefix, size = 10) {
  return request({
    url: '/suggestion/symptoms',
    method: 'get',
    params: { prefix, size }
  })
}

/**
 * 科室关键词提词器
 * @param {string} prefix - 输入前缀
 * @param {number} size - 返回数量，默认10
 */
export function suggestDepartments(prefix, size = 10) {
  return request({
    url: '/suggestion/departments',
    method: 'get',
    params: { prefix, size }
  })
}

/**
 * 题目关键词提词器
 * @param {string} prefix - 输入前缀
 * @param {number} size - 返回数量，默认10
 */
export function suggestQuestions(prefix, size = 10) {
  return request({
    url: '/suggestion/questions',
    method: 'get',
    params: { prefix, size }
  })
}

/**
 * 初始化提词器数据
 */
export function initSuggestionData() {
  return request({
    url: '/suggestion/init',
    method: 'post'
  })
}

