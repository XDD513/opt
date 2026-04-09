import request from './request'

/**
 * 获取会话列表
 */
export function fetchConversations(params) {
  return request({
    url: '/conversations',
    method: 'get',
    params
  })
}

/**
 * 创建会话
 */
export function createConversation(data) {
  return request({
    url: '/conversations',
    method: 'post',
    data
  })
}

/**
 * 清空会话消息
 */
export function clearConversation(conversationId) {
  return request({
    url: `/conversations/${conversationId}/messages`,
    method: 'delete'
  })
}

/**
 * 删除会话（单方面删除，根据角色标记删除）
 */
export function deleteConversation(conversationId, role) {
  return request({
    url: `/conversations/${conversationId}`,
    method: 'delete',
    params: {
      role
    }
  })
}

/**
 * 批量删除所有会话
 */
export function deleteAllConversations(participantId, role) {
  return request({
    url: '/conversations',
    method: 'delete',
    params: {
      participantId,
      role
    }
  })
}

/**
 * 获取会话消息
 */
export function fetchMessages(conversationId, params) {
  return request({
    url: `/conversations/${conversationId}/messages`,
    method: 'get',
    params
  })
}

/**
 * 发送消息
 */
export function sendMessage(conversationId, data) {
  return request({
    url: `/conversations/${conversationId}/messages`,
    method: 'post',
    data
  })
}

/**
 * 获取最近三次发送消息的用户信息
 */
export function getRecentSenders(conversationId) {
  return request({
    url: `/conversations/${conversationId}/recent-senders`,
    method: 'get'
  })
}

/**
 * 获取或创建与管理员对话的会话（多对一：多个发送者对一个管理员）
 */
export function getOrCreateAdminConversation(senderId) {
  return request({
    url: '/conversations/admin-conversation',
    method: 'get',
    params: {
      senderId
    }
  })
}

/**
 * 将会话标记为已读
 */
export function markConversationAsRead(conversationId, params) {
  return request({
    url: `/conversations/${conversationId}/read`,
    method: 'put',
    params
  })
}

/**
 * 批量标记所有会话为已读
 */
export function markAllConversationsAsRead(params) {
  return request({
    url: '/conversations/read-all',
    method: 'put',
    params
  })
}


