import request from './request'

/**
 * 用户登录
 */
export function login(data) {
  return request({
    url: '/user/login',
    method: 'post',
    data: {
      username: data.username,
      password: data.password,
      captchaId: data.captchaId,
      captchaCode: data.captchaCode
    },
    silentError: true
  })
}

/**
 * 获取登录图形验证码
 */
export function getCaptchaImage() {
  return request({
    url: '/captcha/image',
    method: 'get',
    silentError: true
  })
}

/**
 * 用户注册
 */
export function register(data) {
  return request({
    url: '/user/register',
    method: 'post',
    data
  })
}

/**
 * 获取用户信息
 */
export function getUserInfo() {
  return request({
    url: '/user/info',
    method: 'get'
  })
}

/**
 * 检查用户名是否存在
 */
export function checkUsername(username) {
  return request({
    url: '/user/check/username',
    method: 'get',
    params: { username }
  })
}

/**
 * 检查手机号是否存在
 */
export function checkPhone(phone) {
  return request({
    url: '/user/check/phone',
    method: 'get',
    params: { phone }
  })
}

/**
 * 退出登录
 */
export function logout() {
  return request({
    url: '/user/logout',
    method: 'post'
  })
}

/**
 * 刷新访问令牌
 * @param {{ refreshToken: string }} payload
 */
export function refreshToken(payload) {
  return request({
    url: '/user/refresh',
    method: 'post',
    data: payload,
    // 刷新令牌失败不应该弹业务错误消息，由统一鉴权逻辑接管
    silentError: true,
    // 刷新接口本身不应被“刷新逻辑”再次拦截重试，否则可能死循环
    _isRefreshRequest: true
  })
}

/**
 * 更新用户信息
 */
export function updateUserInfo(data) {
  return request({
    url: '/user/info',
    method: 'put',
    data
  })
}

/**
 * 获取用户信息（会返回带签名的头像URL）
 */
export function getUserInfoWithSignedAvatar() {
  return request({
    url: '/user/info',
    method: 'get'
  })
}

/**
 * 修改密码
 */
export function changePassword(data) {
  return request({
    url: '/user/password',
    method: 'put',
    data
  })
}

/**
 * 获取用户设置
 */
export function getUserSettings() {
  return request({
    url: '/user/settings',
    method: 'get'
  })
}

/**
 * 更新用户设置
 */
export function updateUserSettings(data) {
  return request({
    url: '/user/settings',
    method: 'put',
    data
  })
}

/**
 * 判断用户是否为新用户
 */
export function checkIsNewUser() {
  return request({
    url: '/user/check-new-user',
    method: 'get'
  })
}


