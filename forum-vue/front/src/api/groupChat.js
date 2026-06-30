import request from './request'

// 我的群聊会话列表
export function getGroupChatSessions(params) {
  return request({ url: '/group-chat/sessions', method: 'get', params })
}

// 创建群聊
export function createGroupChat(data) {
  return request({ url: '/group-chat/create', method: 'post', data })
}

// 查询群聊消息
export function getGroupChatMessages(groupId, params) {
  return request({ url: `/group-chat/${groupId}/messages`, method: 'get', params })
}

// 发送群聊消息
export function sendGroupChatMessage(data) {
  return request({ url: '/group-chat/messages', method: 'post', data })
}

// 标记群聊已读
export function markGroupChatRead(groupId, messageId) {
  return request({
    url: `/group-chat/${groupId}/read`,
    method: 'put',
    params: { messageId },
  })
}

// 查询公开群聊
export function getPublicGroupChats(params) {
  return request({ url: '/group-chat/public', method: 'get', params })
}

// 加入公开群聊
export function joinPublicGroupChat(groupId) {
  return request({ url: `/group-chat/${groupId}/join`, method: 'post' })
}
