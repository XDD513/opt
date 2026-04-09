import request from './request'

/**
 * 管理员统计数据API
 */

// 获取管理员统计数据
export function getAdminStats() {
  return request({
    url: '/statistics/admin',
    method: 'get'
  })
}

/**
 * 管理员：用户体质测试统计
 */
export function getAdminUserTestStats() {
  return request({
    url: '/statistics/admin/user-test',
    method: 'get'
  })
}

// 获取最近预约列表
export function getRecentAppointments() {
  return request({
    url: '/statistics/recent-appointments',
    method: 'get'
  })
}

// 获取月度统计
export function getMonthlyStats() {
  return request({
    url: '/statistics/monthly',
    method: 'get'
  })
}

// 获取科室统计
export function getDepartmentStats() {
  return request({
    url: '/statistics/department',
    method: 'get'
  })
}

/**
 * 患者统计数据API
 */

// 获取患者统计数据
export function getPatientStats(options = {}) {
  return request({
    url: '/statistics/patient',
    method: 'get',
    ...options
  })
}

// 获取患者最近预约
export function getPatientRecentAppointments() {
  return request({
    url: '/statistics/patient/recent-appointments',
    method: 'get'
  })
}
