import request from './request'

/**
 * 刷新预约列表缓存（管理员端）
 */
export function refreshAppointmentCache() {
  return request({
    url: '/cache/refresh/appointment',
    method: 'post'
  })
}

/**
 * 刷新科室列表缓存（管理员端）
 */
export function refreshDepartmentCache() {
  return request({
    url: '/cache/refresh/department',
    method: 'post'
  })
}

/**
 * 刷新排班列表缓存（管理员端）
 */
export function refreshScheduleCache() {
  return request({
    url: '/cache/refresh/schedule',
    method: 'post'
  })
}

/**
 * 刷新用户列表缓存（管理员端）
 */
export function refreshUserCache() {
  return request({
    url: '/cache/refresh/user',
    method: 'post'
  })
}

/**
 * 刷新所有缓存（管理员端）
 */
export function refreshAllCache() {
  return request({
    url: '/cache/refresh/all',
    method: 'post'
  })
}

