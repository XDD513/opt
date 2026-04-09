import request from './request'

/**
 * 养生文章API接口
 */

/**
 * 统一的分页查询文章列表接口（支持所有查询场景）
 * @param {Object} params - 查询参数
 * @param {string} params.category - 分类（可选）
 * @param {string} params.constitutionType - 体质类型（可选）
 * @param {string} params.tags - 标签（可选，支持多个标签，逗号分隔）
 * @param {number} params.isFeatured - 是否精选（可选）
 * @param {number} params.status - 状态（可选，0-草稿 1-已发布 2-已下架，null表示已发布）
 * @param {string} params.keyword - 搜索关键词（可选，按标题、摘要、标签搜索）
 * @param {number} params.userId - 用户ID（可选，用于查询我的文章或收藏文章）
 * @param {string} params.type - 查询类型（可选，my-我的文章，favorites-收藏文章，recommended-精选，popular-热门）
 * @param {boolean} params.isAdmin - 是否管理员查询（可选，true表示管理员查询，即使status为null也查询所有状态）
 * @param {number} params.pageNum - 页码（默认1）
 * @param {number} params.pageSize - 每页数量（默认10）
 */
export function getArticleList(params) {
  return request({
    url: '/article/list',
    method: 'get',
    params
  })
}

/**
 * 获取文章详情
 * @param {string|number} id - 文章ID（使用字符串避免精度丢失）
 */
export function getArticleDetail(id) {
  return request({
    url: `/article/${String(id)}`,
    method: 'get'
  })
}


/**
 * 发布文章
 * @param {Object} data - 文章数据
 */
export function publishArticle(data) {
  return request({
    url: '/article/publish',
    method: 'post',
    data
  })
}

/**
 * 更新文章
 * @param {string|number} id - 文章ID（使用字符串避免精度丢失）
 * @param {Object} data - 文章数据
 */
export function updateArticle(id, data) {
  return request({
    url: `/article/${String(id)}`,
    method: 'put',
    data
  })
}

/**
 * 删除文章
 * @param {string|number} id - 文章ID（使用字符串避免精度丢失）
 * @param {string|number} userId - 用户ID（使用字符串避免精度丢失）
 */
export function deleteArticle(id, userId) {
  return request({
    url: `/article/${String(id)}`,
    method: 'delete'
  })
}

/**
 * 点赞文章
 * @param {string|number} id - 文章ID（使用字符串避免精度丢失）
 * @param {string|number} userId - 用户ID（使用字符串避免精度丢失）
 */
export function likeArticle(id, userId) {
  return request({
    url: `/article/like/${String(id)}`,
    method: 'post'
  })
}

/**
 * 取消点赞文章
 * @param {string|number} id - 文章ID（使用字符串避免精度丢失）
 * @param {string|number} userId - 用户ID（使用字符串避免精度丢失）
 */
export function unlikeArticle(id, userId) {
  return request({
    url: `/article/like/${String(id)}`,
    method: 'delete'
  })
}

/**
 * 收藏文章
 * @param {string|number} id - 文章ID（使用字符串避免精度丢失）
 * @param {string|number} userId - 用户ID（使用字符串避免精度丢失）
 * @param {string} remark - 备注（可选）
 */
export function favoriteArticle(id, userId, remark = '') {
  return request({
    url: `/article/favorite/${String(id)}`,
    method: 'post',
    params: { remark }
  })
}

/**
 * 取消收藏文章
 * @param {string|number} id - 文章ID（使用字符串避免精度丢失）
 * @param {string|number} userId - 用户ID（使用字符串避免精度丢失）
 */
export function unfavoriteArticle(id, userId) {
  return request({
    url: `/article/favorite/${String(id)}`,
    method: 'delete'
  })
}

/**
 * 获取文章状态（点赞和收藏状态）
 * @param {string|number} id - 文章ID（使用字符串避免精度丢失）
 * @param {string|number} userId - 用户ID（使用字符串避免精度丢失）
 * @returns {Promise} 返回 {liked: boolean, favorited: boolean}
 */
export function getArticleStatus(id, userId) {
  return request({
    url: `/article/status/${String(id)}`,
    method: 'get'
  })
}

/**
 * 获取所有标签列表
 */
export function getAllTags() {
  return request({
    url: '/article/tags',
    method: 'get'
  })
}


/**
 * 下架文章（管理员操作，软删除）
 * @param {string|number} id - 文章ID（使用字符串避免精度丢失）
 */
export function offlineArticle(id, reason = '') {
  return request({
    url: `/article/admin/offline/${String(id)}`,
    method: 'put',
    params: { reason }
  })
}

/**
 * 上架文章（管理员操作，将已下架状态改为已发布）
 * @param {string|number} id - 文章ID（使用字符串避免精度丢失）
 */
export function onlineArticle(id) {
  return request({
    url: `/article/admin/online/${String(id)}`,
    method: 'put'
  })
}

export function getMyArticles(params) {
  return request({
    url: '/article/my',
    method: 'get',
    params
  })
}

export function getMyFavorites(params) {
  return request({
    url: '/article/favorites',
    method: 'get',
    params
  })
}

export function getAdminArticleList(params) {
  return request({
    url: '/article/admin/list',
    method: 'get',
    params
  })
}

export function reviewArticle(id, approved, reason = '') {
  return request({
    url: `/article/admin/review/${String(id)}`,
    method: 'put',
    params: { approved, reason }
  })
}

export function recommendArticle(id) {
  return request({
    url: `/article/admin/recommend/${String(id)}`,
    method: 'put'
  })
}

export function unrecommendArticle(id) {
  return request({
    url: `/article/admin/unrecommend/${String(id)}`,
    method: 'put'
  })
}

export function getMyArticleNotifications(params) {
  return request({
    url: '/article/notification/my',
    method: 'get',
    params
  })
}

export function getAdminArticleNotifications(params) {
  return request({
    url: '/article/notification/admin',
    method: 'get',
    params
  })
}

export function getArticleUnreadCount() {
  return request({
    url: '/article/notification/unread-count',
    method: 'get'
  })
}

export function markArticleNotificationRead(id) {
  return request({
    url: `/article/notification/read/${String(id)}`,
    method: 'put'
  })
}

export function markAllArticleNotificationRead() {
  return request({
    url: '/article/notification/read-all',
    method: 'put'
  })
}

export function markBatchArticleNotificationRead(ids) {
  return request({
    url: '/article/notification/read-batch',
    method: 'put',
    data: ids
  })
}

export function deleteBatchArticleNotification(ids) {
  return request({
    url: '/article/notification/batch',
    method: 'delete',
    data: ids
  })
}

