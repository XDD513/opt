import request from './request'

/**
 * 体质测试相关API
 */

/**
 * 获取所有体质类型列表
 */
export function getConstitutionTypes() {
  return request({
    url: '/constitution/types',
    method: 'get'
  })
}

/**
 * 根据体质代码获取体质详情
 * @param {string} code - 体质代码
 */
export function getConstitutionDetail(code) {
  return request({
    url: `/constitution/type/${code}`,
    method: 'get'
  })
}

/**
 * 获取完整测试问卷
 */
/**
 * 提交测试答案
 * @param {Object} data - 测试答案数据
 * @param {Object} data.answers - 答案对象（问题ID -> 选项ID）
 */
export function submitTest(data) {
  return request({
    url: '/constitution/test/submit',
    method: 'post',
    data
  })
}

/**
 * 获取用户测试历史记录
 */
export function getTestHistory() {
  return request({
    url: '/constitution/test/history',
    method: 'get'
  })
}

/**
 * 获取用户最新测试结果
 */
export function getLatestTestResult(options = {}) {
  return request({
    url: '/constitution/test/latest',
    method: 'get',
    ...options
  })
}

/**
 * 根据测试ID获取测试报告
 * @param {string|number} testId - 测试ID（使用字符串避免精度丢失）
 */
export function getTestReport(testId) {
  return request({
    url: `/constitution/test/report/${String(testId)}`,
    method: 'get'
  })
}

export function generateAiSuggestion(testId, phase = 'analysis') {
  return request({
    url: `/constitution/test/ai-suggestion/generate/${String(testId)}`,
    method: 'post',
    params: { phase }
  })
}

/**
 * 根据用户ID获取最新测试结果（医生端使用）
 * @param {string|number} userId - 用户ID（使用字符串避免精度丢失）
 */
export function getUserLatestTestResult(userId) {
  return request({
    url: `/constitution/test/user/${String(userId)}/latest`,
    method: 'get'
  })
}

/**
 * 根据用户ID获取测试历史记录（医生端使用）
 * @param {string|number} userId - 用户ID（使用字符串避免精度丢失）
 */
export function getUserTestHistory(userId) {
  return request({
    url: `/constitution/test/user/${String(userId)}/history`,
    method: 'get'
  })
}

/**
 * 根据预约ID获取体质测试结果（医生端使用）
 * @param {string|number} appointmentId - 预约ID（使用字符串避免精度丢失）
 */
export function getTestResultByAppointment(appointmentId) {
  return request({
    url: `/constitution/test/appointment/${String(appointmentId)}`,
    method: 'get'
  })
}

/**
 * 检查预约是否已有体质测试记录
 * @param {string|number} appointmentId - 预约ID（使用字符串避免精度丢失）
 */
export function checkTestByAppointment(appointmentId) {
  return request({
    url: `/constitution/test/appointment/${String(appointmentId)}/exists`,
    method: 'get'
  })
}

