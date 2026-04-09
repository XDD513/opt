import request from './request'

/**
 * 健康档案API接口
 */

/**
 * 获取用户健康档案
 * @param {number} userId - 用户ID
 */
export function getHealthProfile(userId) {
  return request({
    url: '/health/profile',
    method: 'get',
    params: { userId }
  })
}

/**
 * 更新用户健康档案
 * @param {Object} data - 健康档案数据
 */
export function updateHealthProfile(data) {
  return request({
    url: '/health/profile',
    method: 'put',
    data
  })
}

/**
 * 创建健康计划
 * @param {Object} data - 健康计划数据
 */
export function createHealthPlan(data) {
  return request({
    url: '/health/plan',
    method: 'post',
    data
  })
}

/**
 * 更新健康计划
 * @param {number} id - 计划ID
 * @param {Object} data - 健康计划数据
 */
export function updateHealthPlan(id, data) {
  return request({
    url: `/health/plan/${id}`,
    method: 'put',
    data
  })
}

/**
 * 删除健康计划
 * @param {number} id - 计划ID
 * @param {number} userId - 用户ID
 */
export function deleteHealthPlan(id, userId) {
  return request({
    url: `/health/plan/${id}`,
    method: 'delete',
    params: { userId }
  })
}

/**
 * 获取健康计划列表
 * @param {Object} params - 查询参数
 * @param {number} params.userId - 用户ID
 * @param {number} params.status - 状态（可选）
 * @param {number} params.pageNum - 页码
 * @param {number} params.pageSize - 每页数量
 */
export function getHealthPlanList(params) {
  return request({
    url: '/health/plan/list',
    method: 'get',
    params
  })
}

/**
 * 健康打卡
 * @param {Object} data - 打卡数据
 */
export function healthCheckin(data) {
  return request({
    url: '/health/checkin',
    method: 'post',
    data
  })
}

/**
 * 获取打卡记录列表
 * @param {Object} params - 查询参数
 * @param {number} params.userId - 用户ID
 * @param {string} params.checkinType - 打卡类型（可选）
 * @param {string} params.startDate - 开始日期（可选，格式：YYYY-MM-DD）
 * @param {string} params.endDate - 结束日期（可选，格式：YYYY-MM-DD）
 * @param {number} params.pageNum - 页码
 * @param {number} params.pageSize - 每页数量
 */
export function getCheckinList(params) {
  return request({
    url: '/health/checkin/list',
    method: 'get',
    params
  })
}

/**
 * 查询指定日期的打卡记录
 * @param {number} userId - 用户ID
 * @param {string} date - 日期（YYYY-MM-DD）
 */
export function getCheckinByDate(userId, date) {
  return request({
    url: '/health/checkin/date',
    method: 'get',
    params: { userId, date }
  })
}

/**
 * 获取健康统计数据
 * @param {Object} params - 查询参数
 * @param {number} params.userId - 用户ID
 * @param {string} params.startDate - 开始日期（可选）
 * @param {string} params.endDate - 结束日期（可选）
 */
export function getHealthStatistics(params) {
  return request({
    url: '/health/statistics',
    method: 'get',
    params
  })
}

/**
 * 生成健康报告
 * @param {number} userId - 用户ID
 */
/**
 * 获取健康计划历史
 * @param {number} userId - 用户ID
 */
export function getHealthPlanHistory(userId) {
  return request({
    url: '/health/plan/history',
    method: 'get',
    params: { userId }
  })
}

/**
 * 根据体质测试ID查询健康计划
 * @param {number} userId - 用户ID
 * @param {number} testId - 体质测试ID
 */
export function getHealthPlansByTestId(userId, testId) {
  return request({
    url: '/health/plan/by-test',
    method: 'get',
    params: { userId, testId }
  })
}

/**
 * 根据 AI 建议创建计划
 * @param {Object} data
 */
export function createHealthPlansFromAiResult(data) {
  return request({
    url: '/health/plan/create-from-ai',
    method: 'post',
    data
  })
}

