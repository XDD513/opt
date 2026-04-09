import request from './request'

/**
 * 系统管理API
 */

// 获取操作日志列表
export function getOperationLogs(params) {
  return request({
    url: '/system/logs',
    method: 'get',
    params
  })
}

// 导出操作日志
export function exportOperationLogs(params) {
  return request({
    url: '/system/logs/export',
    method: 'get',
    params,
    responseType: 'blob'
  })
}

// 获取系统设置
export function getSystemSettings() {
  return request({
    url: '/system/settings',
    method: 'get'
  })
}

// 更新系统设置
export function updateSystemSettings(data) {
  return request({
    url: '/system/settings',
    method: 'put',
    data
  })
}

// 获取运行时配置（用于测试页面）
export function getRuntimeConfig() {
  return request({
    url: '/config',
    method: 'get'
  })
}

// 获取数据字典列表
export function getDictionaryList(params) {
  return request({
    url: '/system/dictionary',
    method: 'get',
    params
  })
}

// 添加数据字典
export function addDictionary(data) {
  return request({
    url: '/system/dictionary',
    method: 'post',
    data
  })
}

// 更新数据字典
export function updateDictionary(data) {
  return request({
    url: '/system/dictionary',
    method: 'put',
    data
  })
}

// 删除数据字典
export function deleteDictionary(id) {
  return request({
    url: `/system/dictionary/${id}`,
    method: 'delete'
  })
}

// 获取用户列表
export function getUserList(params) {
  return request({
    url: '/system/users',
    method: 'get',
    params
  })
}

// 添加用户
export function addUser(data) {
  return request({
    url: '/system/user',
    method: 'post',
    data
  })
}

// 更新用户
export function updateUser(data) {
  return request({
    url: '/system/user',
    method: 'put',
    data
  })
}

// 删除用户
export function deleteUser(id) {
  return request({
    url: `/system/user/${id}`,
    method: 'delete'
  })
}

// 重置用户密码
export function resetUserPassword(userId) {
  return request({
    url: `/system/user/${userId}/reset-password`,
    method: 'post'
  })
}

// 更新用户状态
export function updateUserStatus(userId, status) {
  return request({
    url: `/system/user/${userId}/status`,
    method: 'put',
    data: { status }
  })
}

// ==================== 系统设置相关 API ====================
// 注意：getSystemSettings 和 updateSystemSettings 已在文件开头定义

// ==================== 数据字典相关 API ====================
// 注意：字典相关API已在文件开头定义

// 根据类型获取字典
export function getDictionaryByType(type) {
  return request({
    url: `/system/dictionary`,
    method: 'get',
    params: { type }
  })
}

// 管理员强制下线用户
export function forceLogout(userId) {
  return request({
    url: `/system/user/${userId}/force-logout`,
    method: 'post'
  })
}

// 刷新缓存功能已迁移到 /api/cache.js
// 请使用: import { refreshUserCache } from '@/api/cache'